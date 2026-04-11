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

(defn get-channels
  "Fetches all channels for a guild and groups them by type.
  server-id is a string snowflake. Returns a map:
    {:text    [channel-maps ...]
     :voice   [channel-maps ...]
     :parents [channel-maps ...]}
  where each channel map has :name, :id, and :type."
  [server-id]
  (-> (api/api-request {:method :get
                        :path   (str "guilds/" server-id "/channels")})
      parse-channels))

(defn get-channel
  "Fetches a channel by ID. channel-id is a string snowflake.
  Returns a channel map with :id, :name, :type, :guild_id, etc."
  [channel-id]
  (api/api-request {:method :get
                    :path   (str "channels/" channel-id)}))

(defn modify-channel
  "Updates a channel's settings. channel-id is a string snowflake. params is
  a map of fields to change (e.g. {:name \"new-name\" :topic \"new topic\"}).
  Returns the updated channel map."
  [channel-id params]
  (api/api-request {:method :patch
                    :path   (str "channels/" channel-id)
                    :body   params}))

(defn delete-channel
  "Deletes a channel. channel-id is a string snowflake. Returns the
  deleted channel map."
  [channel-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id)}))

(defn create-channel
  "Creates a new channel in a guild. guild-id is a string snowflake. params
  is a map with at minimum {:name \"channel-name\"} and optionally :type,
  :topic, :parent_id, etc. Returns the created channel map."
  [guild-id params]
  (api/api-request {:method :post
                    :path   (str "guilds/" guild-id "/channels")
                    :body   params}))

; Messages
(defn send-channel-message
  "Sends a message to a channel. channel-id is a string snowflake. params is
  a map with :content (string) and/or :embeds (vector of embed maps).
  Returns the created message map."
  [channel-id params]
  (api/api-request {:method :post
                    :path   (str "channels/" channel-id "/messages")
                    :body   params}))

(defn get-channel-message
  "Fetches a single message from a channel. channel-id and message-id are
  string snowflakes. Returns a message map with :id, :content, :author, etc."
  [channel-id message-id]
  (api/api-request {:method :get
                    :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn get-channel-messages
  "Fetches messages from a channel. channel-id is a string snowflake.
  query-params is an optional map for pagination, e.g. {:limit 50 :before id}.
  Returns a vector of message maps."
  [channel-id & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "channels/" channel-id "/messages")
                    :query-params query-params}))

(defn edit-message
  "Edits an existing message. channel-id and message-id are string snowflakes.
  params is a map of fields to update (e.g. {:content \"edited\"}).
  Returns the updated message map."
  [channel-id message-id params]
  (api/api-request {:method :patch
                    :path   (str "channels/" channel-id "/messages/" message-id)
                    :body   params}))

(defn delete-message
  "Deletes a message from a channel. channel-id and message-id are string
  snowflakes. Returns nil on success."
  [channel-id message-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id)}))

(defn bulk-delete-messages
  "Deletes multiple messages from a channel. channel-id is a string snowflake.
  message-ids is a sequence of string snowflakes (2-100 messages, not older
  than 14 days). Returns nil on success."
  [channel-id message-ids]
  (api/api-request {:method :post
                    :path   (str "channels/" channel-id "/messages/bulk-delete")
                    :body   {:messages message-ids}}))

; Reactions
(defn create-reaction
  "Adds a reaction to a message as the bot user. channel-id and message-id
  are string snowflakes. emoji is a URL-encoded string (e.g. \"%F0%9F%94%A5\"
  or \"custom_emoji:123456\"). Returns nil on success."
  [channel-id message-id emoji]
  (api/api-request {:method :put
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-own-reaction
  "Removes the bot's own reaction from a message. channel-id and message-id
  are string snowflakes. emoji is a URL-encoded string. Returns nil on success."
  [channel-id message-id emoji]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/@me")}))

(defn delete-user-reaction
  "Removes another user's reaction from a message. channel-id, message-id,
  and user-id are string snowflakes. emoji is a URL-encoded string.
  Returns nil on success."
  [channel-id message-id emoji user-id]
  (api/api-request {:method :delete
                    :path   (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji "/" user-id)}))

(defn get-reactions
  "Fetches users who reacted with a given emoji on a message. channel-id and
  message-id are string snowflakes. emoji is a URL-encoded string.
  query-params is an optional map (e.g. {:limit 25 :after user-id}).
  Returns a vector of user maps."
  [channel-id message-id emoji & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "channels/" channel-id "/messages/" message-id "/reactions/" emoji)
                    :query-params query-params}))

; Guilds
(defn get-guild
  "Fetches a guild by ID. guild-id is a string snowflake. Returns a guild map
  with :id, :name, :icon, :owner_id, :roles, etc."
  [guild-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id)}))

(defn modify-guild
  "Updates a guild's settings. guild-id is a string snowflake. params is a map
  of fields to change (e.g. {:name \"new name\"}). Returns the updated guild map."
  [guild-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id)
                    :body   params}))

; Members
(defn get-guild-member
  "Fetches a guild member. guild-id and user-id are string snowflakes.
  Returns a member map with :user, :nick, :roles, :joined_at, etc."
  [guild-id user-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn list-guild-members
  "Lists members of a guild. guild-id is a string snowflake. query-params is
  an optional map (e.g. {:limit 100 :after user-id}). Returns a vector of
  member maps."
  [guild-id & [query-params]]
  (api/api-request {:method       :get
                    :path         (str "guilds/" guild-id "/members")
                    :query-params query-params}))

(defn modify-guild-member
  "Updates a guild member's attributes. guild-id and user-id are string
  snowflakes. params is a map of fields to change (e.g. {:nick \"new nick\"}).
  Returns the updated member map."
  [guild-id user-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id "/members/" user-id)
                    :body   params}))

(defn remove-guild-member
  "Kicks a member from a guild. guild-id and user-id are string snowflakes.
  Returns nil on success."
  [guild-id user-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/members/" user-id)}))

(defn add-guild-member-role
  "Assigns a role to a guild member. guild-id, user-id, and role-id are string
  snowflakes. Returns nil on success."
  [guild-id user-id role-id]
  (api/api-request {:method :put
                    :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

(defn remove-guild-member-role
  "Removes a role from a guild member. guild-id, user-id, and role-id are
  string snowflakes. Returns nil on success."
  [guild-id user-id role-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/members/" user-id "/roles/" role-id)}))

; Roles
(defn get-guild-roles
  "Lists all roles in a guild. guild-id is a string snowflake. Returns a
  vector of role maps with :id, :name, :color, :permissions, etc."
  [guild-id]
  (api/api-request {:method :get
                    :path   (str "guilds/" guild-id "/roles")}))

(defn create-guild-role
  "Creates a new role in a guild. guild-id is a string snowflake. params is
  a map (e.g. {:name \"Mod\" :color 0x00FF00 :permissions \"8\"}).
  Returns the created role map."
  [guild-id params]
  (api/api-request {:method :post
                    :path   (str "guilds/" guild-id "/roles")
                    :body   params}))

(defn modify-guild-role
  "Updates a role's attributes. guild-id and role-id are string snowflakes.
  params is a map of fields to change. Returns the updated role map."
  [guild-id role-id params]
  (api/api-request {:method :patch
                    :path   (str "guilds/" guild-id "/roles/" role-id)
                    :body   params}))

(defn delete-guild-role
  "Deletes a role from a guild. guild-id and role-id are string snowflakes.
  Returns nil on success."
  [guild-id role-id]
  (api/api-request {:method :delete
                    :path   (str "guilds/" guild-id "/roles/" role-id)}))


