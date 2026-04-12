(ns discord-bot.clients.discord.api
  (:require [discord-bot.clients.discord.rate-limit :as rl]))

(defn mk-headers [{:keys [token project-name project-version]}]
  {"Authorization" (str "Bot " token)
   "User-Agent"    (str project-name " " project-version)})

(defn api-request [conn {:keys [method path body query-params]}]
  (let [config (:config conn)
        response (rl/discord-request
                  (:rate-limit-state conn)
                  (cond-> {:method  method
                           :url     (str (:url config) path)
                           :headers (mk-headers config)
                           :as      :json}
                    body         (assoc :content-type :json :form-params body)
                    query-params (assoc :query-params query-params)))]
    (or (:body response) :ok)))
