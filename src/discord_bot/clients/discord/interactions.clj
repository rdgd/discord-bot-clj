(ns discord-bot.clients.discord.interactions
  (:require [discord-bot.clients.discord.api :as api]))

(def callback-types
  {:pong                                 1
   :channel-message-with-source          4
   :deferred-channel-message-with-source 5
   :deferred-update-message              6
   :update-message                       7
   :autocomplete-result                  8
   :modal                                9})

; Application command registration
(defn register-global-command [app-id command-data]
  (api/api-request {:method :post
                    :path   (str "applications/" app-id "/commands")
                    :body   command-data}))

(defn register-guild-command [app-id guild-id command-data]
  (api/api-request {:method :post
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")
                    :body   command-data}))

(defn list-global-commands [app-id]
  (api/api-request {:method :get
                    :path   (str "applications/" app-id "/commands")}))

(defn list-guild-commands [app-id guild-id]
  (api/api-request {:method :get
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")}))

(defn delete-global-command [app-id command-id]
  (api/api-request {:method :delete
                    :path   (str "applications/" app-id "/commands/" command-id)}))

(defn delete-guild-command [app-id guild-id command-id]
  (api/api-request {:method :delete
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands/" command-id)}))

(defn bulk-overwrite-global-commands [app-id commands]
  (api/api-request {:method :put
                    :path   (str "applications/" app-id "/commands")
                    :body   commands}))

(defn bulk-overwrite-guild-commands [app-id guild-id commands]
  (api/api-request {:method :put
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")
                    :body   commands}))

; Interaction responses
(defn respond-to-interaction [interaction-id interaction-token callback-type & [data]]
  (let [type-val (if (keyword? callback-type)
                   (get callback-types callback-type)
                   callback-type)]
    (api/api-request {:method :post
                      :path   (str "interactions/" interaction-id "/" interaction-token "/callback")
                      :body   (cond-> {:type type-val}
                                data (assoc :data data))})))

(defn create-followup-message [app-id interaction-token message-data]
  (api/api-request {:method :post
                    :path   (str "webhooks/" app-id "/" interaction-token)
                    :body   message-data}))

(defn edit-original-response [app-id interaction-token message-data]
  (api/api-request {:method :patch
                    :path   (str "webhooks/" app-id "/" interaction-token "/messages/@original")
                    :body   message-data}))

(defn delete-original-response [app-id interaction-token]
  (api/api-request {:method :delete
                    :path   (str "webhooks/" app-id "/" interaction-token "/messages/@original")}))
