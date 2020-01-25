(ns discord-bot.core
  (:require [discord-bot.clients.discord.http :as http]
            [discord-bot.clients.discord.ws :as ws]))

; WEBSOCKETS AND INITIALIZATION
(def init ws/initialize)

; UTILITY
(defn get-presences
  []
  (ws/get-presences))

(def get-user-by-id ws/get-user-by-id)
(def started-new-game? ws/started-new-game?)
(defn server-state
  []
  ws/server-state)
;(get-presences)
; CHANNELS
(def get-channels http/get-channels)
(def send-channel-message http/send-channel-message)

(comment
  (init {:on-presence-update (fn [data]
                               (println "HOLY SHIT IT WORKED")
                               (println data)
                               (println "and it's still working"))}))
