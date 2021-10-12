(ns discord-bot.clients.discord.http
  (:require [cheshire.core] ;; Cheshire needs to be on the classpath for clj-http auto coercion
            [clj-http.client :as http]
            [discord-bot.config :refer [get-config]]))

(defn mk-headers
  []
  {"Authorization" (str "Bot " (get-config [:token]))
   "User-Agent"    (str (get-config [:project-name]) " " (get-config [:project-version]))})

(defn parse-channels
  [channels]
  (reduce (fn [acc channel]
            (let [c (select-keys channel [:name :id :type])
                  conj-channel #(conj % c)]
              (case (:type c)
                0 (update acc :text conj-channel)
                2 (update acc :voice conj-channel)
                4 (update acc :parents conj-channel))))
          {:text []
           :voice []
           :parents []}
          channels))

(defn send-channel-message
  [channel-id params]
  (http/post (str (get-config [:url]) "channels/" channel-id "/messages")
             {:headers      (mk-headers)
              :content-type :json
              :form-params  params}))

(defn get-channels
  [server-id]
  (-> (http/get (str (get-config [:url]) "guilds/" server-id "/channels")
                {:headers (mk-headers)
                 :as :json})
      :body
      parse-channels))
