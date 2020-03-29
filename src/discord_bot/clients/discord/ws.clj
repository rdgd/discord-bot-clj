(ns discord-bot.clients.discord.ws
  (:require [gniazdo.core :as ws]
            [cheshire.core :as json]
            [discord-bot.config :refer [get-config] :as config]
            [discord-bot.clients.discord.http :as dc]))

(def ws-connection (atom nil))
(def session (atom nil))
(def server-state (atom nil))
(def heartbeat-timer (atom nil))
(def the-opts (atom nil))
(def already-resumed? (atom false))

(defn ws-send
  [payload]
  (println "SENDING WS PAYLOAD: " payload)
  (ws/send-msg @ws-connection (json/generate-string payload)))

(defn get-presences
  []
  (:presences @server-state))

(def identify-payload
  {:op 2
   :d {:token (get-config [:token])
       :properties {"$os" "linux"
                    "$browser" (get-config [:project-name])
                    "$device" "remote-discord-bot-server"}}})

(defn resume-session
  [{:keys [session_id last-event-index]}]
  (println "Sending resume payload" session_id last-event-index)
  (reset! already-resumed? true)
  (ws-send {:op "6"
            :d {:token (get-config [:token])
                :session_id session_id
                :seq (or last-event-index 0)}}))

(defn identify
  []
  (ws-send identify-payload))

(defn kill-ws
  [reason & [callback]]
  (println "kill-ws - Killing and cleaning up WS connection for reason")
  (println "kill-ws - Reason for stopping: " reason)
  (do
    (println "Cancelling the heartbeat-timer future, stored in an atom")
    (println "Current value: " @heartbeat-timer)
    (when (future? @heartbeat-timer)
      (future-cancel @heartbeat-timer))
    (println "Value after cancel: " @heartbeat-timer)
    ;(reset! ws-connection (ws/close @ws-connection))
    (when callback
      (println "kill-ws - Kill WS callback detected, invoking callback")
      (callback))))

(defn on-connect  [resume? & [ws]]
  (println "**** CALLED ON-CONNECT ****")
  (println "Resume value: " resume?)
  (println "WS (arg) value: " ws)
  (println "WS Connection atom (issue): " @ws-connection)
  (println (if resume?
             "Connected to WS, and attempting to resume session"
             "Connected to WS and attempting to identify"))
  (if resume?
    (when-not @already-resumed? (resume-session @session))
    (identify)))

(defn on-error  [e]
  (println "ERROR: " e))

(defn call-heartbeat
  []
  (let [payload {:op 1
                 :d (or (:last-event-index @session) 0)}]
    (println "Calling Discord heartbeat")
    (ws-send payload)))

(defn get-ws-connection
  []
  ws-connection)

(defn start-heartbeat
  [interval]
  (when (future? @heartbeat-timer)
    (future-cancel @heartbeat-timer))
  (reset! heartbeat-timer
          (future
            (loop []
              (Thread/sleep interval)
              (when @(get-ws-connection)
                (call-heartbeat)
                (recur))))))

(defn get-user-by-id
  [user-id]
  (some (fn [member]
          (when (= (:id (:user member)) user-id)
            member))
        (:members @server-state)))

(defn handle-presence-update
  [{:keys [presences] :as data} & [callback]]
  (when callback (callback data))
  (let [user-id (get-in data [:user :id])]
    (swap! server-state (fn [s]
                          (update s :presences (fn [presences] (map (fn [presence]
                                                                      (if (= user-id (get-in presence [:user :id]))
                                                                        data
                                                                        presence)) presences)))))))

(defn handle-message-create
  [data & [callback]]
  (println "Message created: " data)
  (when callback (callback data)))

(defn handle-typing-start
  [data & [callback]]
  (println "Typing started: " data)
  (when callback (callback data)))

(defn handle-default
  [data & [callback]]
  (println "default event handler: " data)
  (when callback (callback data)))

(defn started-new-game?
  [current-game new-game]
  (not= (:name current-game) (:name new-game)))

(defn handle-channel-create
  [data & [callback]]
  (swap! server-state (fn [s] (update s :channels (fn [c] (conj c data)))))
  (when callback (callback data)))

(defn handle-message-update
  [data & [callback]]
  (println "Message updated: " data)
  (when callback (callback data)))

(defn handle-session-resumed [data]
  (println "*** DISCORD WS SESSION RESUMED SUCCESSFULLY *** data: " data))

(defn receive-event
  [{data :d event-name :t event-index :s}
   {:keys [on-message-create
           on-presence-update
           on-typing-start
           on-channel-create
           on-message-update
           on-guild-member-update
           on-guild-member-remove
           on-guild-role-delete]}]
  (println "Received event: " event-name)
  (case event-name
    "READY" (reset! session data)
    "RESUMED" (handle-session-resumed data)
    "GUILD_CREATE" (reset! server-state data)
    "MESSAGE_CREATE" (handle-message-create data on-message-create)
    "MESSAGE_UPDATE" (handle-message-update data on-message-update)
    "PRESENCE_UPDATE" (handle-presence-update data on-presence-update)
    "TYPING_START" (handle-typing-start data on-typing-start)
    "CHANNEL_CREATE" (handle-channel-create data on-channel-create)
    "GUILD_MEMBER_UPDATE" (handle-default data on-guild-member-update)
    "GUILD_MEMBER_REMOVE" (handle-default data on-guild-member-remove)
    "GUILD_ROLE_DELETE" (handle-default data on-guild-role-delete)
    (println "Received an unknown event name " event-name ". Full event data: " data))
  (swap! session (fn [{:keys [last-event-index] :as s}]
                   (assoc s :last-event-index (if last-event-index
                                                (if (> event-index last-event-index)
                                                  event-index
                                                  last-event-index)
                                                0)))))

(defn handle-message  [msg opts]
  (let [{code :op data :d :as full-msg} (json/parse-string msg true)]
    (println "Received WS message from Discord: " full-msg)
    (case code
      0 (receive-event full-msg opts)
      1 (call-heartbeat)
      10 (start-heartbeat (:heartbeat_interval data))
      11 true
      (println "Received unrecognized WS message code from Discord: " code))))

(defn initialize [& [{:keys [resume?] :as opts}]]
  (println "Intializing WS session with Discord servers")
  (reset! the-opts opts)
  (when-not @already-resumed?
    (reset! ws-connection (ws/connect (get-config [:ws-url])
                                      :on-connect  (partial on-connect resume?)
                                      :on-close (fn [code reason]
                                                  (kill-ws (str "beacuse of on-close " code reason)
                                                           (fn []
                                                             (when (> code 1001)
                                                               (initialize (merge opts {:resume? true})))))) ;used to be (kill-ws (str "beacuse of on-close " code reason) (partial initialize opts))
                                      :on-error on-error
                                      :on-receive #(handle-message % opts)))))

(comment
  (ws/close @ws-connection)
  (println (:last-event-index @session))
  (reset! ws-connection (ws/close @ws-connection))
  (println @ws-connection)
  (def session-id (:session_id @session))
  (println session-id)
  (kill-ws "manually triggered")
  (initialize @the-opts)
  (initialize {:resume? true})
  (identify)
  (ws/send-msg @ws-connection identify-payload))

