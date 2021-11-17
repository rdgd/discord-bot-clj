(ns discord-bot.clients.discord.ws
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [discord-bot.config :refer [get-config]]
            [gniazdo.core :as ws]))

(declare initialize)
(def ws-connection (atom nil))
(def session (atom nil))
(def server-state (atom nil))
(def heartbeat-timer (atom nil))
(def heartbeat-semafor (atom 0))
(def the-opts (atom nil))
(def intentionally-disconnected (atom false))

(defn ws-send
  [payload]
  (log/info "SENDING WS PAYLOAD: " payload)
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

(defn close-connection
  []
  (ws/close @ws-connection))


(defn resume-session
  []
  (let [{:keys [session_id last-event-index]} @session]
    (log/info "Sending resume payload" session_id last-event-index)
    (ws-send {:op "6"
              :d {:token (get-config [:token])
                  :session_id session_id
                  :seq (or last-event-index 0)}})))

(defn identify
  []
  (ws-send identify-payload))

(defn on-connect  [resume? & _]
  (log/info "**** CALLED ON-CONNECT ****")
  (log/info (if resume?
             "Connected to WS, and attempting to resume session"
             "Connected to WS and attempting to identify"))
  (identify)
  (when resume? (resume-session)))

(defn on-error  [e]
  (log/info "on-error handler called")
  (log/error "ERROR: " e)
  (initialize @the-opts true))

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
      (close-connection)
      (initialize @the-opts true))))

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
                                                 (map (fn [presence]
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
  [current-game new-game]
  (not= (:name current-game) (:name new-game)))

(defn handle-channel-create
  [data & [callback]]
  (swap! server-state (fn [s] (update s :channels (fn [c] (conj c data)))))
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
           on-voice-state-update
           on-message-update
           on-message-reaction-add
           on-guild-member-update
           on-guild-member-remove
           on-guild-role-delete]}]
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
    "GUILD_MEMBER_UPDATE" (handle-default data on-guild-member-update)
    "GUILD_MEMBER_REMOVE" (handle-default data on-guild-member-remove)
    "GUILD_ROLE_DELETE" (handle-default data on-guild-role-delete)
    "VOICE_STATE_UPDATE" (handle-default data on-voice-state-update)
    "MESSAGE_REACTION_ADD" (handle-default data on-message-reaction-add)
    (log/warn "Received an unknown event name " event-name ". Full event data: " data))
  (swap! session (fn [{:keys [last-event-index] :as s}]
                   (assoc s :last-event-index (if last-event-index
                                                (if (> event-index last-event-index)
                                                  event-index
                                                  last-event-index)
                                                0)))))

(defn handle-message  [msg opts]
  (let [{code :op data :d :as full-msg} (json/parse-string msg true)]
    (log/info "Received WS message from Discord: " full-msg)
    (case code
      0 (receive-event full-msg opts)
      1 (do (log/info "received heartbeat request from discord")
            (call-heartbeat true))
      7 (do (log/info "was asked to reconnect!")
            (reset! intentionally-disconnected true)
            (close-connection)) ;;reconnect
      9 (do (log/info "was told the session is invalid!") (close-connection)) ;;invalid session
      10 (start-heartbeat (:heartbeat_interval data))
      11 (reset! heartbeat-semafor 0) ;; heartbeat ack from discord
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
  (#{1000 4004 4010 4011 4012 4013 4014} close-code))

(defn initialize [& [opts resume?]]
  (log/info "Intializing WS session with Discord servers")
  (reset-state!)
  (reset! the-opts opts)
  (reset!
    ws-connection
    (ws/connect
      (get-config [:ws-url])
      :on-connect (partial on-connect resume?)
      :on-close
      (fn [code reason]
        (log/info "WS session terminated with code: " code " For reason: " reason)
        (log/info "Intentionally disconnected? " @intentionally-disconnected)
        (reset-state!)
        (when (and reconnect? (not intentionally-disconnected))
          (initialize opts true))) 
      :on-error on-error
      :on-receive #(handle-message % opts))))

(defn stop
  []
  (reset! intentionally-disconnected true)
  (close-connection))


(comment
  (ws/close @ws-connection)
  (println @ws-connection)
  (initialize @the-opts)
  (initialize @the-opts true))
