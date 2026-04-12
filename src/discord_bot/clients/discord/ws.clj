(ns discord-bot.clients.discord.ws
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [discord-bot.config :refer [get-config]]
            [gniazdo.core :as ws]))

(declare init)
(def ws-connection (atom nil))
(def session (atom nil))
(def server-state (atom nil))
(def heartbeat-timer (atom nil))
(def heartbeat-semafor (atom 0))
(def the-opts (atom nil))
;; Controls what on-close does when the WS terminates.
;; :auto     - reconnect with resume if close code allows (default)
;; :resume   - reconnect with resume (explicit)
;; :identify - reconnect with a fresh identify (explicit)
;; :stop     - do not reconnect
(def reconnect-intent (atom :auto))

(defn ws-send
  [payload]
  (log/info "SENDING WS PAYLOAD: " (cond-> payload (get-in payload [:d :token]) (assoc-in [:d :token] "REDACTED")))
  (ws/send-msg @ws-connection (json/generate-string payload)))

(defn get-presences
  "Returns the presences for the connected guild as a vector of presence maps,
  each containing :user, :status, :activities, etc. Returns nil if no guild
  data has been received yet."
  []
  (:presences @server-state))

(defn build-identify-payload
  [intents]
  {:op 2
   :d {:token      (get-config [:token])
       :intents    intents
       :properties {"os"      "linux"
                    "browser" (get-config [:project-name])
                    "device"  "remote-discord-bot-server"}}})

(defn close-connection
  "Closes the WebSocket connection to Discord. Takes no arguments. The on-close
  handler drives reconnection based on reconnect-intent."
  []
  (when-let [conn @ws-connection]
    (try (ws/close conn) (catch Exception _))))

(defn trigger-reconnect
  "Signal that on-close should reconnect in a specific mode, then close the
  current connection. mode is :resume or :identify."
  [mode]
  (reset! reconnect-intent mode)
  (close-connection))


; After the connection is closed, your app should open a new connection using resume_gateway_url rather than the URL used to initially connect, with the same query parameters from the initial Connection. If your app doesn't use the resume_gateway_url when reconnecting, it will experience disconnects at a higher rate than normal.

;Once the new connection is opened, your app should send a Gateway Resume event using the session_id and sequence number mentioned above. When sending the event, session_id will have the same field name, but the last sequence number will be passed as seq in the data object (d).

;When Resuming, you do not need to send an Identify event after opening the connection.

;If successful, the Gateway will send the missed events in order, finishing with a Resumed event to signal event replay has finished and that all subsequent events will be new.
(defn resume-session
  []
  (let [{:keys [session_id last-event-index]} @session]
    (log/info "Sending resume payload" session_id last-event-index)
    (ws-send {:op 6
              :d {:token (get-config [:token])
                  :session_id session_id
                  :seq last-event-index}})))

(defn identify
  [intents]
  (ws-send (build-identify-payload intents)))

(defn on-connect  [resume? {:keys [intents]} & _]
  (log/info "**** CALLED ON-CONNECT ****")
  (log/info (if resume?
             "Connected to WS, and attempting to resume session"
             "Connected to WS and attempting to identify"))

  (if resume?
    (resume-session)
    (identify intents)))

(defn on-error [e]
  (log/info "on-error handler called")
  (log/error "ERROR: " e)
  (trigger-reconnect :resume))

(defn call-heartbeat
  [& [override]]
  (if (or override (= @heartbeat-semafor 0))
    (let [payload {:op 1
                   :d (or (:last-event-index @session) 0)}]
      (log/info "Calling Discord heartbeat")
      (reset! heartbeat-semafor 1)
      (ws-send payload))
    (do
      (log/info "Heartbeat concurrency issue detected. Disconnecting.")
      (trigger-reconnect :resume))))

(defn get-ws-connection
  []
  ws-connection)

(defn start-heartbeat
  [interval]
  (when (future? @heartbeat-timer)
    (future-cancel @heartbeat-timer))
  (reset! heartbeat-semafor 0)
  (reset! heartbeat-timer
          (future
            (loop []
              (Thread/sleep interval)
              (when @(get-ws-connection)
                (call-heartbeat)
                (recur))))))

(defn get-user-by-id
  "Looks up a guild member by their user ID. user-id is a string snowflake.
  Returns the member map (containing :user, :roles, :nick, etc.) or nil if
  not found."
  [user-id]
  (some (fn [member]
          (when (= (:id (:user member)) user-id)
            member))
        (:members @server-state)))

(defn handle-presence-update
  [data & [callback]]
  (when callback (callback data))
  (let [user-id (get-in data [:user :id])]
    (swap! server-state (fn [s]
                          (update s :presences (fn [presences]
                                                 (mapv (fn [presence]
                                                        (if (= user-id (get-in presence [:user :id]))
                                                          data
                                                          presence)) presences)))))))

(defn handle-message-create
  [data & [callback]]
  (log/info "Message created: " data)
  (when callback (callback data)))

(defn handle-typing-start
  [data & [callback]]
  (log/info "Typing started: " data)
  (when callback (callback data)))

(defn handle-default
  [data & [callback]]
  (log/info "default event handler: " data)
  (when callback (callback data)))

(defn started-new-game?
  "Returns true if the game changed between two presence activity entries.
  current-game and new-game are maps with a :name key (or nil). Returns boolean."
  [current-game new-game]
  (not= (:name current-game) (:name new-game)))

(defn handle-channel-create
  [data & [callback]]
  (swap! server-state (fn [s] (update s :channels (fn [c] (conj c data)))))
  (when callback (callback data)))

(defn handle-channel-delete
  [data & [callback]]
  (swap! server-state (fn [s] (update s :channels (fn [c] (filterv #(not= (:id %) (:id data)) c)))))
  (when callback (callback data)))

(defn handle-message-update
  [data & [callback]]
  (log/info "Message updated: " data)
  (when callback (callback data)))

(defn handle-session-resumed [data]
  (log/info "*** DISCORD WS SESSION RESUMED SUCCESSFULLY *** data: " data))

(defn receive-event
  [{data :d event-name :t event-index :s}
   {:keys [on-message-create
           on-presence-update
           on-typing-start
           on-channel-create
           on-channel-delete
           on-voice-state-update
           on-message-update
           on-message-reaction-add
           on-guild-member-update
           on-guild-member-remove
           on-guild-role-delete
           on-interaction-create]}]
  (log/info "Received event: " event-name)
  (case event-name
    "READY" (reset! session data)
    "RESUMED" (handle-session-resumed data)
    "GUILD_CREATE" (reset! server-state data)
    "MESSAGE_CREATE" (handle-message-create data on-message-create)
    "MESSAGE_UPDATE" (handle-message-update data on-message-update)
    "PRESENCE_UPDATE" (handle-presence-update data on-presence-update)
    "TYPING_START" (handle-typing-start data on-typing-start)
    "CHANNEL_CREATE" (handle-channel-create data on-channel-create)
    "CHANNEL_DELETE" (handle-channel-delete data on-channel-delete)
    "GUILD_MEMBER_UPDATE" (handle-default data on-guild-member-update)
    "GUILD_MEMBER_REMOVE" (handle-default data on-guild-member-remove)
    "GUILD_ROLE_DELETE" (handle-default data on-guild-role-delete)
    "VOICE_STATE_UPDATE" (handle-default data on-voice-state-update)
    "MESSAGE_REACTION_ADD" (handle-default data on-message-reaction-add)
    "INTERACTION_CREATE" (handle-default data on-interaction-create)
    (log/warn "Received an unknown event name " event-name ". Full event data: " data))
  ; should only be resetting when handling messages with opcode 0, which is when an "s" value would be present
  ; this function is only being called when opcode 0, so should always be present
  ; from the docs:
  ; Before your app can send a Resume (opcode 6) event, it will need three values: the session_id and the resume_gateway_url from the Ready event, and the sequence number (s) from the last Dispatch (opcode 0) event it received before the disconnect.
  (when event-index
    (swap! session (fn [{:keys [last-event-index] :as s}]
                     (assoc s :last-event-index (if (and last-event-index (> last-event-index event-index))
                                                  last-event-index
                                                  event-index))))))

(defn handle-message  [msg opts]
  (let [{code :op data :d :as full-msg} (json/parse-string msg true)]
    (log/info "Received WS message from Discord: " full-msg)
    (case code
      0 (receive-event full-msg opts)
      1 (do (log/info "received heartbeat request from discord")
            (call-heartbeat true))
      7 (do (log/info "was asked to reconnect!")
            (trigger-reconnect :resume))
      9 (do (log/info "was told the session is invalid! Resumable: " data)
            (trigger-reconnect (if (boolean data) :resume :identify)))
      10 (start-heartbeat (:heartbeat_interval data))
      11 (reset! heartbeat-semafor 0) ; heartbeat ack from discord
      (log/warn "Received unrecognized WS message code from Discord: " code))))

(defn reset-state!
  []
  (log/info "Checking if heartbeat-timer is a future")
  (when (future? @heartbeat-timer)
    (log/info "Heartbeat timer is a future")
    (future-cancel @heartbeat-timer)
    (log/info "Heartbeat timer future cancelled"))

  (log/info "Resetting heartbeat timer to nil")
  (reset! heartbeat-timer nil)
  (log/info "Heartbeat timer reset")
  (reset! heartbeat-semafor 0))

(defn reconnect?
  [close-code]
  (not (#{4004 4010 4011 4012 4013 4014} close-code)))

(defn init
  "Opens a WebSocket connection to Discord and begins handling gateway events.
  Both arguments are optional.

  opts - a map of event callback functions, keyed by:
    :on-message-create, :on-presence-update, :on-typing-start,
    :on-channel-create, :on-channel-delete, :on-voice-state-update,
    :on-message-update, :on-message-reaction-add, :on-guild-member-update,
    :on-guild-member-remove, :on-guild-role-delete, :on-interaction-create
    Each callback receives the event data map as its sole argument.
    Also accepts :intents (integer bitfield for gateway intents).

  resume? - boolean, when true resumes an existing session instead of
    sending a fresh Identify.

  Returns the WebSocket connection object."
  [& [opts resume?]]
  (log/info "Intializing WS session with Discord servers")
  (reset-state!)
  (reset! the-opts opts)
  (reset! reconnect-intent :auto)
  (log/info "reset all the state, and about to reset the ws-connection state with a new ws connection object")
  (reset!
    ws-connection
    (ws/connect
      ; need to update the url with resume_gateway_url for resuming sessions
      (get-config [:ws-url])
      :on-connect (partial on-connect resume? opts)
      :on-close
      (fn [code reason]
        (log/info "WS session terminated with code: " code " For reason: " reason)
        (let [intent @reconnect-intent]
          (log/info "on-close reconnect-intent: " intent)
          (reset-state!)
          (case intent
            :stop     nil
            :resume   (init opts true)
            :identify (init opts false)
            :auto     (when (reconnect? code) (init opts true)))))
      :on-error on-error
      :on-receive #(handle-message % opts))))

(defn stop
  "Gracefully disconnects from Discord without triggering auto-reconnect.
  Takes no arguments. Returns nil."
  []
  (reset! reconnect-intent :stop)
  (close-connection))


(comment
  (ws/close @ws-connection)
  (println @ws-connection)
  (init @the-opts)
  (init @the-opts true))
