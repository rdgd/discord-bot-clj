(ns discord-bot.config
  (:require [clojure.core.memoize :as memo]
            [clojure.edn :as edn]))

(defonce url "https://discordapp.com/api/")
(defonce ws-url "wss://gateway.discord.gg/?v=6&encoding=json")

(defn bot-config*
  []
  (edn/read-string (slurp "./bot.edn")))

(def bot-config (memo/ttl bot-config* :ttl/threshold (* 10 1000)))

(defn get-config
  [path]
  (get-in (merge (bot-config) {:url url
                               :ws-url ws-url}) path))
