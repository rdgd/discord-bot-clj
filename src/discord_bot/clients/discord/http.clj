(ns discord-bot.clients.discord.http
  (:require [discord-bot.clients.discord.api :as api]))

; Channels
(defn parse-channels
  [channels]
  (reduce
   (fn [acc channel]
     (let [c (select-keys channel [:name :id :type])
           conj-channel #(conj % c)]
       (case (:type c)
         0 (update acc :text conj-channel)
         2 (update acc :voice conj-channel)
         4 (update acc :parents conj-channel)
         acc)))
   {:text []
    :voice []
    :parents []}
   channels))

(defn get-channels [server-id]
  (-> (api/api-request {:method :get
                        :path   (str "guilds/" server-id "/channels")})
      parse-channels))

(defn get-channel [channel-id]
  (api/api-request {:method :get
                    :path   (str "channels/" channel-id)}))

(defn modify-channel [channel-id params]
  (api/api-request {:method :patch
                    :path   (str "channels/" channel-id)
                    :body   params}))

(defn delete-channel [channel-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id)}))

(defn create-channel [guild-id params]
  (api/api-request {:method :post
                    :path   (str "guilds/" guild-id "/channels")
                    :body   params}))

; Messages
(defn send-channel-message [channel-id params]
  (api/api-request {:method :post
                    :path   (str "channels/" channel-id "/messages")
                    :body   params}))

(defn get-channel-message [channel-id message-id]
  (api/api-request {:method :get
                    :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn get-channel-messages [channel-id & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "channels/" channel-id "/messages")
                    :query-params query-params}))

(defn edit-message [channel-id message-id params]
  (api/api-request {:method :patch
                    :path   (str "channels/" channel-id "/messages/" message-id)
                    :body   params}))

(defn delete-message [channel-id message-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn bulk-delete-messages [channel-id message-ids]
  (api/api-request {:method :post
                    :path   (str "channels/" channel-id "/messages/bulk-delete")
                    :body   {:messages message-ids}}))

; Reactions
(defn create-reaction [channel-id message-id emoji]
  (api/api-request {:method :put
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-own-reaction [channel-id message-id emoji]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-user-reaction [channel-id message-id emoji user-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/" user-id)}))

(defn get-reactions [channel-id message-id emoji & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji)
                    :query-params query-params}))

; Guilds
(defn get-guild [guild-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id)}))

(defn modify-guild [guild-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id)
                    :body   params}))

; Members
(defn get-guild-member [guild-id user-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn list-guild-members [guild-id & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "guilds/" guild-id "/members")
                    :query-params query-params}))

(defn modify-guild-member [guild-id user-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id "/members/" user-id)
                    :body   params}))

(defn remove-guild-member [guild-id user-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn add-guild-member-role [guild-id user-id role-id]
  (api/api-request {:method :put
                    :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

(defn remove-guild-member-role [guild-id user-id role-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

; Roles
(defn get-guild-roles [guild-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id "/roles")}))

(defn create-guild-role [guild-id params]
  (api/api-request {:method :post
                    :path   (str "guilds/" guild-id "/roles")
                    :body   params}))

(defn modify-guild-role [guild-id role-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id "/roles/" role-id)
                    :body   params}))

(defn delete-guild-role [guild-id role-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/roles/" role-id)}))


