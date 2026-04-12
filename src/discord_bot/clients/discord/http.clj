(ns discord-bot.clients.discord.http
  (:require [discord-bot.clients.discord.api :as api]))

; Channels
(def channel-types
  {0 :text
   2 :voice
   4 :parents})

(defn parse-channels
  [channels]
  (reduce
   (fn [acc channel]
     (let [c (select-keys channel [:name :id :type])]
       (if-let [channel-type (get channel-types (:type c))]
         (update acc channel-type conj c)
         acc)))
   {:text []
    :voice []
    :parents []}
   channels))

(defn get-channels [conn server-id]
  (-> (api/api-request conn {:method :get
                             :path   (str "guilds/" server-id "/channels")})
      parse-channels))

(defn get-channel [conn channel-id]
  (api/api-request conn {:method :get
                         :path   (str "channels/" channel-id)}))

(defn modify-channel [conn channel-id params]
  (api/api-request conn {:method :patch
                         :path   (str "channels/" channel-id)
                         :body   params}))

(defn delete-channel [conn channel-id]
  (api/api-request conn {:method :delete
                         :path   (str "channels/" channel-id)}))

(defn create-channel [conn guild-id params]
  (api/api-request conn {:method :post
                         :path   (str "guilds/" guild-id "/channels")
                         :body   params}))

; Messages
(defn send-channel-message [conn channel-id params]
  (api/api-request conn {:method :post
                         :path   (str "channels/" channel-id "/messages")
                         :body   params}))

(defn get-channel-message [conn channel-id message-id]
  (api/api-request conn {:method :get
                         :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn get-channel-messages [conn channel-id & [query-params]]
  (api/api-request conn {:method       :get
                         :path         (str "channels/" channel-id "/messages")
                         :query-params query-params}))

(defn edit-message [conn channel-id message-id params]
  (api/api-request conn {:method :patch
                         :path   (str "channels/" channel-id "/messages/" message-id)
                         :body   params}))

(defn delete-message [conn channel-id message-id]
  (api/api-request conn {:method :delete
                         :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn bulk-delete-messages [conn channel-id message-ids]
  (api/api-request conn {:method :post
                         :path   (str "channels/" channel-id "/messages/bulk-delete")
                         :body   {:messages message-ids}}))

; Reactions
(defn create-reaction [conn channel-id message-id emoji]
  (api/api-request conn {:method :put
                         :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-own-reaction [conn channel-id message-id emoji]
  (api/api-request conn {:method :delete
                         :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-user-reaction [conn channel-id message-id emoji user-id]
  (api/api-request conn {:method :delete
                         :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/" user-id)}))

(defn get-reactions [conn channel-id message-id emoji & [query-params]]
  (api/api-request conn {:method       :get
                         :path         (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji)
                         :query-params query-params}))

; Guilds
(defn get-guild [conn guild-id]
  (api/api-request conn {:method :get
                         :path   (str "guilds/" guild-id)}))

(defn modify-guild [conn guild-id params]
  (api/api-request conn {:method :patch
                         :path   (str "guilds/" guild-id)
                         :body   params}))

; Members
(defn get-guild-member [conn guild-id user-id]
  (api/api-request conn {:method :get
                         :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn list-guild-members [conn guild-id & [query-params]]
  (api/api-request conn {:method       :get
                         :path         (str "guilds/" guild-id "/members")
                         :query-params query-params}))

(defn modify-guild-member [conn guild-id user-id params]
  (api/api-request conn {:method :patch
                         :path   (str "guilds/" guild-id "/members/" user-id)
                         :body   params}))

(defn remove-guild-member [conn guild-id user-id]
  (api/api-request conn {:method :delete
                         :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn add-guild-member-role [conn guild-id user-id role-id]
  (api/api-request conn {:method :put
                         :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

(defn remove-guild-member-role [conn guild-id user-id role-id]
  (api/api-request conn {:method :delete
                         :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

; Roles
(defn get-guild-roles [conn guild-id]
  (api/api-request conn {:method :get
                         :path   (str "guilds/" guild-id "/roles")}))

(defn create-guild-role [conn guild-id params]
  (api/api-request conn {:method :post
                         :path   (str "guilds/" guild-id "/roles")
                         :body   params}))

(defn modify-guild-role [conn guild-id role-id params]
  (api/api-request conn {:method :patch
                         :path   (str "guilds/" guild-id "/roles/" role-id)
                         :body   params}))

(defn delete-guild-role [conn guild-id role-id]
  (api/api-request conn {:method :delete
                         :path   (str "guilds/" guild-id "/roles/" role-id)}))
