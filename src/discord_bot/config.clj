(ns discord-bot.config
  "Minimal helpers for loading bot configuration from disk.
  Callers pass their config map to `core/connect`."
  (:require [clojure.edn :as edn]))

(defn load-edn
  "Reads and returns the edn file at `path` as a Clojure data structure.
  Returns nil if the file doesn't exist or can't be read."
  [path]
  (try (edn/read-string (slurp path))
       (catch Exception _ nil)))

(defn load-bot-config
  "Edn reads ./bot.edn"
  ([] (load-bot-config "./bot.edn"))
  ([path] (load-edn path)))
