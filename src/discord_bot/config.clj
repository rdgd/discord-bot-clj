(ns discord-bot.config
  (:require [clojure.core.memoize :as memo]
            [clojure.edn :as edn]))

(defonce url "https://discord.com/api/v10/")
(defonce ws-url "wss://gateway.discord.gg/?v=10&encoding=json")

(defn bot-config*
  []
  (edn/read-string (slurp "./bot.edn")))

(def bot-config (memo/ttl bot-config* :ttl/threshold (* 10 1000)))

(defn get-config
  [path]
  (get-in (merge (bot-config) {:url url
                               :ws-url ws-url}) path))
