(ns discord-bot.core
  (:require [discord-bot.clients.discord.http :as http]
            [discord-bot.clients.discord.ws :as ws])
  (:gen-class))

; WEBSOCKETS AND INITIALIZATION
(def init ws/initialize)
(def stop ws/stop)

; UTILITY
(defn get-presences
  []
  (ws/get-presences))

(def get-user-by-id ws/get-user-by-id)
(def started-new-game? ws/started-new-game?)
(defn server-state
  []
  ws/server-state)

; CHANNELS
(def get-channels http/get-channels)
(def send-channel-message http/send-channel-message)
(def close-connection ws/close-connection)
