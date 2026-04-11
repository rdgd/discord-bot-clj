(ns discord-bot.clients.discord.api
  (:require [discord-bot.config :refer [get-config]]
            [discord-bot.clients.discord.rate-limit :as rl]))

(defn mk-headers []
  {"Authorization" (str "Bot " (get-config [:token]))
   "User-Agent"    (str (get-config [:project-name]) " " (get-config [:project-version]))})

(defn api-request [{:keys [method path body query-params]}]
  (let [response (rl/discord-request
                   (cond-> {:method  method
                            :url     (str (get-config [:url]) path)
                            :headers (mk-headers)
                            :as      :json}
                     body         (assoc :content-type :json :form-params body)
                     query-params (assoc :query-params query-params)))]
    (or (:body response) :ok)))
