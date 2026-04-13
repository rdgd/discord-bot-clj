(ns discord-bot.clients.discord.ws
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [discord-bot.clients.discord.rate-limit :as rl]
            [gniazdo.core :as ws])
  (:import [java.util.concurrent.locks ReentrantLock]))

(declare open!)

(defn new-connection
  "Creates a fresh connection map holding all mutable state for one bot.
  config is a map with :token :ws-url :project-name (and anything else the
  caller wants to carry around, like :project-version or :url for HTTP).
  handlers is a map of event callbacks; each is (fn [conn data]).
  intents is the Discord gateway intents bitfield."
  [{:keys [config handlers intents]}]
  {:config            config
   :handlers          handlers
   :intents           intents
   :ws-conn           (atom nil)
   :session           (atom nil)
   :server-state      (atom {})
   :heartbeat-timer   (atom nil)
   :heartbeat-semafor (atom 0)
   :reconnect-intent  (atom :auto)
   :generation        (atom 0)
   :last-message-at   (atom nil)
   :watchdog-timer    (atom nil)
   :rate-limit-state  (rl/new-state)
   :lifecycle-lock    (ReentrantLock.)})

(defmacro with-lifecycle-lock [conn & body]
  `(let [^ReentrantLock lock# (:lifecycle-lock ~conn)]
     (.lock lock#)
     (try ~@body
          (finally (.unlock lock#)))))

(defn ws-send
  [conn payload]
  (log/info "SENDING WS PAYLOAD: " (cond-> payload (get-in payload [:d :token]) (assoc-in [:d :token] "REDACTED")))
  (ws/send-msg @(:ws-conn conn) (json/generate-string payload)))

(defn- only-guild
  "When the bot is connected to a single guild, returns its state map. When
  connected to none or many, returns nil — callers should pass guild-id."
  [conn]
  (let [guilds (vals @(:server-state conn))]
    (when (= 1 (count guilds)) (first guilds))))

(defn get-presences
  "Returns the presences for a guild as a vector of presence maps. With no
  guild-id, works only if the bot is connected to exactly one guild. Returns
  nil if the guild is unknown or the bot is in multiple guilds and guild-id
  was not supplied."
  ([conn]
   (:presences (only-guild conn)))
  ([conn guild-id]
   (get-in @(:server-state conn) [guild-id :presences])))

(defn get-user-by-id
  "Looks up a guild member by their user ID across all known guilds. Returns
  the first match. Pass guild-id to restrict the search to a single guild."
  ([conn user-id]
   (some (fn [guild]
           (some (fn [member] (when (= (:id (:user member)) user-id) member))
                 (:members guild)))
         (vals @(:server-state conn))))
  ([conn guild-id user-id]
   (some (fn [member] (when (= (:id (:user member)) user-id) member))
         (get-in @(:server-state conn) [guild-id :members]))))

(defn started-new-game?
  "Returns true if the game changed between two presence activity entries."
  [current-game new-game]
  (not= (:name current-game) (:name new-game)))

(defn update-presence
  "Sends an Update Presence (op 3) gateway command. presence is a map with keys
  :since, :activities, :status, :afk — Discord requires all four, so nil fields
  are filled in with sensible defaults (status \"online\", afk false, activities
  []). activities is a vector of activity maps, e.g.
    [{:name \"with Clojure\" :type 0}]
  Type codes: 0 playing, 1 streaming, 2 listening, 3 watching, 4 custom, 5 competing."
  [conn {:keys [since activities status afk] :as _presence}]
  (ws-send conn {:op 3
                 :d  {:since      since
                      :activities (or activities [])
                      :status     (or status "online")
                      :afk        (boolean afk)}}))

(defn build-identify-payload
  [conn]
  (let [{:keys [token project-name]} (:config conn)]
    {:op 2
     :d {:token      token
         :intents    (:intents conn)
         :properties {"os"      "linux"
                      "browser" project-name
                      "device"  "remote-discord-bot-server"}}}))

(defn close-connection
  "Closes the WebSocket connection to Discord. The on-close handler drives
  reconnection based on reconnect-intent. Errors from ws/close are logged but
  not rethrown, since close is commonly called on a connection that is already
  half-dead."
  [conn]
  (when-let [c @(:ws-conn conn)]
    (try (ws/close c)
         (catch Exception e
           (log/warn e "ws/close threw (connection likely already closed)")))))

(defn trigger-reconnect
  "Signal that on-close should reconnect in a specific mode, then close the
  current connection. mode is :resume or :identify. The actual close runs on
  a separate thread so callers invoked from Jetty's WebSocket callback
  threads don't interrupt themselves when Jetty tears down its own thread
  pool during close."
  [conn mode]
  (with-lifecycle-lock conn
    (reset! (:reconnect-intent conn) mode))
  (future (close-connection conn))
  nil)

(defn resume-session
  [conn]
  (let [{:keys [session_id last-event-index]} @(:session conn)
        token (get-in conn [:config :token])]
    (log/info "Sending resume payload" session_id last-event-index)
    (ws-send conn {:op 6
                   :d {:token token
                       :session_id session_id
                       :seq last-event-index}})))

(defn identify
  [conn]
  (ws-send conn (build-identify-payload conn)))

(defn on-connect [conn resume? & _]
  (log/info "**** CALLED ON-CONNECT ****")
  (let [session @(:session conn)
        can-resume? (and resume? (:session_id session))]
    (log/info (if can-resume?
                "Connected to WS, and attempting to resume session"
                "Connected to WS and attempting to identify"))
    (if can-resume?
      (resume-session conn)
      (identify conn))))

(defn on-error [conn e]
  (log/info "on-error handler called")
  (log/error "ERROR: " e)
  (trigger-reconnect conn :resume))

(defn call-heartbeat
  [conn & [override]]
  (if (or override (= @(:heartbeat-semafor conn) 0))
    (let [payload {:op 1
                   :d (or (:last-event-index @(:session conn)) 0)}]
      (log/info "Calling Discord heartbeat")
      (reset! (:heartbeat-semafor conn) 1)
      (ws-send conn payload))
    (do
      (log/info "Heartbeat concurrency issue detected. Disconnecting.")
      (trigger-reconnect conn :resume))))

(defn start-heartbeat
  [conn interval]
  (when (future? @(:heartbeat-timer conn))
    (future-cancel @(:heartbeat-timer conn)))
  (reset! (:heartbeat-semafor conn) 0)
  (reset! (:heartbeat-timer conn)
          (future
            (loop []
              (Thread/sleep interval)
              (when @(:ws-conn conn)
                (call-heartbeat conn)
                (recur))))))

(defn handle-presence-update
  [conn data]
  (when-let [cb (get-in conn [:handlers :on-presence-update])]
    (cb conn data))
  (let [user-id  (get-in data [:user :id])
        guild-id (:guild_id data)]
    (when guild-id
      (swap!
       (:server-state conn)
       update-in [guild-id :presences]
       (fn [presences]
         (mapv (fn [p] (if (= user-id (get-in p [:user :id])) data p))
               (or presences [])))))))

(defn- dispatch-handler
  [conn handler-key data]
  (when-let [cb (get-in conn [:handlers handler-key])]
    (cb conn data)))

(defn handle-channel-create
  [conn data]
  (when-let [guild-id (:guild_id data)]
    (swap! (:server-state conn) update-in [guild-id :channels]
           (fn [cs] (conj (or cs []) data))))
  (dispatch-handler conn :on-channel-create data))

(defn handle-channel-delete
  [conn data]
  (when-let [guild-id (:guild_id data)]
    (swap! (:server-state conn) update-in [guild-id :channels]
           (fn [cs] (filterv #(not= (:id %) (:id data)) (or cs [])))))
  (dispatch-handler conn :on-channel-delete data))

(defn handle-session-resumed [data]
  (log/info "*** DISCORD WS SESSION RESUMED SUCCESSFULLY *** data:" data))

(defn receive-event
  [conn {data :d event-name :t event-index :s}]
  (log/info "Received event: " event-name)
  (case event-name
    "READY"                (reset! (:session conn) data)
    "RESUMED"              (handle-session-resumed data)
    "GUILD_CREATE"         (do (swap! (:server-state conn) assoc (:id data) data)
                               (dispatch-handler conn :on-guild-create data))
    "GUILD_UPDATE"         (do (swap! (:server-state conn)
                                      (fn [s] (update s (:id data) merge data)))
                               (dispatch-handler conn :on-guild-update data))
    "GUILD_DELETE"         (do (swap! (:server-state conn) dissoc (:id data))
                               (dispatch-handler conn :on-guild-delete data))
    "MESSAGE_CREATE"                    (dispatch-handler conn :on-message-create data)
    "MESSAGE_UPDATE"                    (dispatch-handler conn :on-message-update data)
    "MESSAGE_DELETE"                    (dispatch-handler conn :on-message-delete data)
    "MESSAGE_DELETE_BULK"               (dispatch-handler conn :on-message-delete-bulk data)
    "PRESENCE_UPDATE"                   (handle-presence-update conn data)
    "TYPING_START"                      (dispatch-handler conn :on-typing-start data)
    "CHANNEL_CREATE"                    (handle-channel-create conn data)
    "CHANNEL_UPDATE"                    (dispatch-handler conn :on-channel-update data)
    "CHANNEL_DELETE"                    (handle-channel-delete conn data)
    "CHANNEL_PINS_UPDATE"               (dispatch-handler conn :on-channel-pins-update data)
    "THREAD_CREATE"                     (dispatch-handler conn :on-thread-create data)
    "THREAD_UPDATE"                     (dispatch-handler conn :on-thread-update data)
    "THREAD_DELETE"                     (dispatch-handler conn :on-thread-delete data)
    "THREAD_LIST_SYNC"                  (dispatch-handler conn :on-thread-list-sync data)
    "THREAD_MEMBER_UPDATE"              (dispatch-handler conn :on-thread-member-update data)
    "THREAD_MEMBERS_UPDATE"             (dispatch-handler conn :on-thread-members-update data)
    "GUILD_MEMBER_ADD"                  (dispatch-handler conn :on-guild-member-add data)
    "GUILD_MEMBER_UPDATE"               (dispatch-handler conn :on-guild-member-update data)
    "GUILD_MEMBER_REMOVE"               (dispatch-handler conn :on-guild-member-remove data)
    "GUILD_ROLE_CREATE"                 (dispatch-handler conn :on-guild-role-create data)
    "GUILD_ROLE_UPDATE"                 (dispatch-handler conn :on-guild-role-update data)
    "GUILD_ROLE_DELETE"                 (dispatch-handler conn :on-guild-role-delete data)
    "VOICE_STATE_UPDATE"                (dispatch-handler conn :on-voice-state-update data)
    "MESSAGE_REACTION_ADD"              (dispatch-handler conn :on-message-reaction-add data)
    "MESSAGE_REACTION_REMOVE"           (dispatch-handler conn :on-message-reaction-remove data)
    "MESSAGE_REACTION_REMOVE_ALL"       (dispatch-handler conn :on-message-reaction-remove-all data)
    "MESSAGE_REACTION_REMOVE_EMOJI"     (dispatch-handler conn :on-message-reaction-remove-emoji data)
    "INTERACTION_CREATE"                (dispatch-handler conn :on-interaction-create data)
    (log/warn "Received an unknown event name" event-name ". Full event data:" data))
  (when event-index
    (swap! (:session conn)
           (fn [{:keys [last-event-index] :as s}]
             (assoc s :last-event-index
                    (if (and last-event-index (> last-event-index event-index))
                      last-event-index
                      event-index))))))

(defn handle-message [conn msg]
  (reset! (:last-message-at conn) (System/currentTimeMillis))
  (let [{code :op data :d :as full-msg} (json/parse-string msg true)]
    (log/info "Received WS message from Discord:" full-msg)
    (case code
      0  (receive-event conn full-msg)
      1  (do (log/info "received heartbeat request from discord")
             (call-heartbeat conn true))
      7  (do (log/info "was asked to reconnect!")
             (trigger-reconnect conn :resume))
      9  (do (log/info "was told the session is invalid! Resumable:" data)
             (trigger-reconnect conn (if (boolean data) :resume :identify)))
      10 (start-heartbeat conn (:heartbeat_interval data))
      11 (reset! (:heartbeat-semafor conn) 0)
      (log/warn "Received unrecognized WS message code from Discord:" code))))

(defn reset-state!
  [conn]
  (when (future? @(:heartbeat-timer conn))
    (future-cancel @(:heartbeat-timer conn)))
  (reset! (:heartbeat-timer conn) nil)
  (reset! (:heartbeat-semafor conn) 0))

(defn reconnect?
  [close-code]
  (not (#{4004 4010 4011 4012 4013 4014} close-code)))

(def ^:private watchdog-interval-ms 15000)
(def ^:private watchdog-stale-threshold-ms 90000)

(defn- start-watchdog! [conn]
  (when-not @(:watchdog-timer conn)
    (reset! (:watchdog-timer conn)
            (future
              (loop []
                (try
                  (Thread/sleep watchdog-interval-ms)
                  (when-let [last @(:last-message-at conn)]
                    (let [elapsed (- (System/currentTimeMillis) last)]
                      (when (> elapsed watchdog-stale-threshold-ms)
                        (log/warn "Watchdog: no Discord traffic in" elapsed "ms, forcing reconnect")
                        (reset! (:last-message-at conn) (System/currentTimeMillis))
                        (trigger-reconnect conn :resume))))
                  (catch InterruptedException _ (throw (InterruptedException.)))
                  (catch Throwable t (log/error t "Watchdog loop error")))
                (recur))))))

(defn- open!
  "Opens a new WS connection. Caller must hold lifecycle-lock."
  [conn resume?]
  (let [my-gen (swap! (:generation conn) inc)
        ws-url (get-in conn [:config :ws-url])
        ;; Gniazdo may fire on-connect from a Jetty thread before ws/connect
        ;; returns and the outer reset! populates (:ws-conn conn). Gate the
        ;; callback on this promise so it never runs before the atom is set.
        ready (promise)]
    (log/info "Opening WS session (generation" my-gen ")")
    (reset-state! conn)
    (reset! (:reconnect-intent conn) :auto)
    (reset! (:last-message-at conn) (System/currentTimeMillis))
    (reset!
     (:ws-conn conn)
     (ws/connect
       ws-url
       :on-connect (fn [& _]
                     @ready
                     (on-connect conn resume?))
       :on-close
       (fn [code reason]
         (with-lifecycle-lock conn
           (log/info "WS session terminated code:" code "reason:" reason "generation:" my-gen)
           (if (not= my-gen @(:generation conn))
             (log/info "on-close for superseded connection (gen" my-gen
                       "current" @(:generation conn) "); ignoring")
             (let [intent @(:reconnect-intent conn)]
               (log/info "on-close reconnect-intent:" intent)
               (reset-state! conn)
               (case intent
                 :stop     nil
                 :resume   (open! conn true)
                 :identify (open! conn false)
                 :auto     (when (reconnect? code) (open! conn true)))))))
       :on-error #(on-error conn %)
       :on-receive #(handle-message conn %)))
    (deliver ready true)))

(defn start!
  "Opens a WebSocket connection to Discord for the given connection map.
  If a connection is already open for this conn, it is closed first.
  Returns the connection map."
  [conn]
  (with-lifecycle-lock conn
    (when-let [existing @(:ws-conn conn)]
      (log/info "start! called with existing connection; closing it first")
      (try (ws/close existing) (catch Exception _)))
    (start-watchdog! conn)
    (open! conn false)
    conn))

(defn stop!
  "Gracefully disconnects from Discord without triggering auto-reconnect.
  Cancels the watchdog. Returns nil."
  [conn]
  ;; Set the intent under the lock so on-close (which also takes the lock)
  ;; sees :stop when it fires. Do NOT hold the lock while closing — on-close
  ;; needs to acquire it on a different thread, and Jetty's .stop can block
  ;; waiting for on-close to return. Holding the lock here would deadlock.
  (with-lifecycle-lock conn
    (reset! (:reconnect-intent conn) :stop))
  (when-let [wd @(:watchdog-timer conn)]
    (future-cancel wd)
    (reset! (:watchdog-timer conn) nil))
  (close-connection conn))
