(ns discord-bot.core
  "Public API for the discord-bot library.

  Every function that interacts with Discord can be called in two ways:

    1. Explicit: pass the connection as the first argument.
       (discord/send-channel-message conn channel-id msg)

    2. Implicit: scope the connection once with `with-conn`, then call
       functions without repeating it.
       (discord/with-conn conn
         (discord/send-channel-message channel-id msg)
         (discord/get-user-by-id user-id))

  Create a conn with `connect` (opens a live WebSocket) or `new-connection`
  (state only, no WS). The dynamic binding does not propagate across thread
  boundaries — inside event handlers (which receive conn as an argument),
  you'll typically want to re-establish it with `with-conn` at the top."
  (:require [discord-bot.clients.discord.http :as http]
            [discord-bot.clients.discord.interactions :as interactions]
            [discord-bot.clients.discord.ws :as ws]))

; Implicit connection binding

(def ^{:dynamic true
       :doc "The connection in scope for the current thread. Bind with
             `with-conn` rather than setting directly."}
  *conn* nil)

(defmacro with-conn
  "Executes body with `conn` bound as the implicit connection. Functions
  called within body can omit the conn argument."
  [conn & body]
  `(binding [*conn* ~conn] ~@body))

(defn- current-conn []
  (or *conn*
      (throw (IllegalStateException.
               "No connection in scope. Pass conn explicitly or wrap the call in `with-conn`."))))

; Connection lifecycle

(def ^{:doc "Default Discord API base URL."} default-url
  "https://discord.com/api/v10/")

(def ^{:doc "Default Discord gateway URL."} default-ws-url
  "wss://gateway.discord.gg/?v=10&encoding=json")

(defn- normalize-config [config]
  (merge {:url default-url
          :ws-url default-ws-url}
         config))

(defn- wrap-handler
  "Turns a user-supplied (fn [data]) callback into a (fn [conn data]) that
  binds *conn* before invoking it. Handlers can access the connection via
  `*conn*` if they need it explicitly."
  [handler]
  (fn [conn data]
    (binding [*conn* conn]
      (handler data))))

(defn- wrap-handlers [handlers]
  (reduce-kv (fn [acc k h] (assoc acc k (wrap-handler h))) {} handlers))

(defn new-connection
  "Builds a connection map without opening a WebSocket. Useful for REST-only
  usage, testing, or deferred start.

  opts:
    :config   - {:token :project-name :project-version [:url :ws-url]}
                :url and :ws-url default to the current Discord v10 endpoints.
    :handlers - map of event callbacks. Each callback is (fn [data]). Inside
                a handler, `*conn*` is bound so API calls can omit conn.
                If you need the connection explicitly (e.g. to pass to a
                future), deref `discord-bot.core/*conn*`. Supported keys:
                  :on-message-create        :on-message-update
                  :on-presence-update       :on-typing-start
                  :on-channel-create        :on-channel-delete
                  :on-voice-state-update    :on-message-reaction-add
                  :on-guild-member-update   :on-guild-member-remove
                  :on-guild-role-delete     :on-interaction-create
    :intents  - integer bitfield for gateway intents"
  [opts]
  (ws/new-connection (-> opts
                         (update :config normalize-config)
                         (update :handlers wrap-handlers))))

(defn- remove-shutdown-hook! [hook]
  (when hook
    (try (.removeShutdownHook (Runtime/getRuntime) hook)
         (catch IllegalStateException _))))  ; already in shutdown

(defn- register-shutdown-hook! [conn]
  (let [hook (Thread. ^Runnable #(ws/stop! conn) "discord-bot-shutdown")]
    (.addShutdownHook (Runtime/getRuntime) hook)
    hook))

(defn connect
  "Builds a connection and opens the WebSocket. Returns the connection map,
  which must be passed to subsequent API calls. See `new-connection` for the
  shape of opts.

  By default, registers a JVM shutdown hook that cleanly disconnects from
  Discord on SIGTERM / SIGINT / normal exit. Pass
  `:install-shutdown-hook? false` in opts to skip it (e.g. for REPL development
  or when you manage lifecycle another way)."
  [opts]
  (let [conn (ws/start! (new-connection opts))]
    (if (false? (:install-shutdown-hook? opts))
      conn
      (assoc conn :shutdown-hook (register-shutdown-hook! conn)))))

(defn disconnect
  "Gracefully disconnects from Discord without triggering auto-reconnect.
  Cancels the watchdog and removes the shutdown hook (if one was installed).
  Returns nil."
  ([] (disconnect (current-conn)))
  ([conn]
   (remove-shutdown-hook! (:shutdown-hook conn))
   (ws/stop! conn)))

; WebSocket state

(defn get-presences
  "Returns the presences for the connected guild as a vector of presence maps,
  each containing :user, :status, :activities, etc. Returns nil if no guild
  data has been received yet."
  ([] (get-presences (current-conn)))
  ([conn] (ws/get-presences conn)))

(defn get-user-by-id
  "Looks up a guild member by their user ID. user-id is a string snowflake.
  Returns the member map (containing :user, :roles, :nick, etc.) or nil if
  not found."
  ([user-id] (get-user-by-id (current-conn) user-id))
  ([conn user-id] (ws/get-user-by-id conn user-id)))

(defn started-new-game?
  "Returns true if the game changed between two presence activity entries.
  current-game and new-game are maps with a :name key (or nil). Returns boolean."
  [current-game new-game]
  (ws/started-new-game? current-game new-game))

(defn server-state
  "Returns the current guild state map (containing :members, :channels,
  :presences, etc) or nil if no GUILD_CREATE has been received yet."
  ([] (server-state (current-conn)))
  ([conn] @(:server-state conn)))

; Channels

(defn get-channels
  "Fetches all channels for a guild and groups them by type.
  server-id is a string snowflake. Returns a map:
    {:text    [channel-maps ...]
     :voice   [channel-maps ...]
     :parents [channel-maps ...]}
  where each channel map has :name, :id, and :type."
  ([server-id] (get-channels (current-conn) server-id))
  ([conn server-id] (http/get-channels conn server-id)))

(defn get-channel
  "Fetches a channel by ID. channel-id is a string snowflake.
  Returns a channel map with :id, :name, :type, :guild_id, etc."
  ([channel-id] (get-channel (current-conn) channel-id))
  ([conn channel-id] (http/get-channel conn channel-id)))

(defn modify-channel
  "Updates a channel's settings. channel-id is a string snowflake. params is
  a map of fields to change (e.g. {:name \"new-name\" :topic \"new topic\"}).
  Returns the updated channel map."
  ([channel-id params] (modify-channel (current-conn) channel-id params))
  ([conn channel-id params] (http/modify-channel conn channel-id params)))

(defn delete-channel
  "Deletes a channel. channel-id is a string snowflake. Returns the
  deleted channel map."
  ([channel-id] (delete-channel (current-conn) channel-id))
  ([conn channel-id] (http/delete-channel conn channel-id)))

(defn create-channel
  "Creates a new channel in a guild. guild-id is a string snowflake. params
  is a map with at minimum {:name \"channel-name\"} and optionally :type,
  :topic, :parent_id, etc. Returns the created channel map."
  ([guild-id params] (create-channel (current-conn) guild-id params))
  ([conn guild-id params] (http/create-channel conn guild-id params)))

; Messages

(defn send-channel-message
  "Sends a message to a channel. channel-id is a string snowflake. params is
  a map with :content (string) and/or :embeds (vector of embed maps).
  Returns the created message map."
  ([channel-id params] (send-channel-message (current-conn) channel-id params))
  ([conn channel-id params] (http/send-channel-message conn channel-id params)))

(defn get-channel-message
  "Fetches a single message from a channel. channel-id and message-id are
  string snowflakes. Returns a message map with :id, :content, :author, etc."
  ([channel-id message-id] (get-channel-message (current-conn) channel-id message-id))
  ([conn channel-id message-id] (http/get-channel-message conn channel-id message-id)))

(defn get-channel-messages
  "Fetches messages from a channel. channel-id is a string snowflake.
  query-params is an optional map for pagination, e.g. {:limit 50 :before id}.
  Returns a vector of message maps."
  ([channel-id] (get-channel-messages (current-conn) channel-id nil))
  ([channel-id query-params] (get-channel-messages (current-conn) channel-id query-params))
  ([conn channel-id query-params] (http/get-channel-messages conn channel-id query-params)))

(defn edit-message
  "Edits an existing message. channel-id and message-id are string snowflakes.
  params is a map of fields to update (e.g. {:content \"edited\"}).
  Returns the updated message map."
  ([channel-id message-id params] (edit-message (current-conn) channel-id message-id params))
  ([conn channel-id message-id params] (http/edit-message conn channel-id message-id params)))

(defn delete-message
  "Deletes a message from a channel. channel-id and message-id are string
  snowflakes. Returns :ok on success."
  ([channel-id message-id] (delete-message (current-conn) channel-id message-id))
  ([conn channel-id message-id] (http/delete-message conn channel-id message-id)))

(defn bulk-delete-messages
  "Deletes multiple messages from a channel. channel-id is a string snowflake.
  message-ids is a sequence of string snowflakes (2-100 messages, not older
  than 14 days). Returns :ok on success."
  ([channel-id message-ids] (bulk-delete-messages (current-conn) channel-id message-ids))
  ([conn channel-id message-ids] (http/bulk-delete-messages conn channel-id message-ids)))

; Threads

(defn start-thread-from-message
  "Creates a thread branched off an existing message. channel-id and message-id
  are string snowflakes. params is a map with at minimum {:name \"thread-name\"}
  and optionally :auto_archive_duration, :rate_limit_per_user. Returns the
  created channel (thread) map. Send messages into the thread by passing its
  :id to send-channel-message."
  ([channel-id message-id params]
   (start-thread-from-message (current-conn) channel-id message-id params))
  ([conn channel-id message-id params]
   (http/start-thread-from-message conn channel-id message-id params)))

(defn start-thread-in-channel
  "Creates a thread in a channel without an anchor message. channel-id is a
  string snowflake. params is a map with :name and optionally :type,
  :auto_archive_duration, :invitable. Returns the created thread map."
  ([channel-id params] (start-thread-in-channel (current-conn) channel-id params))
  ([conn channel-id params] (http/start-thread-in-channel conn channel-id params)))

; Reactions

(defn create-reaction
  "Adds a reaction to a message as the bot user. channel-id and message-id
  are string snowflakes. emoji is a URL-encoded string (e.g. \"%F0%9F%94%A5\"
  or \"custom_emoji:123456\"). Returns :ok on success."
  ([channel-id message-id emoji] (create-reaction (current-conn) channel-id message-id emoji))
  ([conn channel-id message-id emoji] (http/create-reaction conn channel-id message-id emoji)))

(defn delete-own-reaction
  "Removes the bot's own reaction from a message. channel-id and message-id
  are string snowflakes. emoji is a URL-encoded string. Returns :ok on success."
  ([channel-id message-id emoji] (delete-own-reaction (current-conn) channel-id message-id emoji))
  ([conn channel-id message-id emoji] (http/delete-own-reaction conn channel-id message-id emoji)))

(defn delete-user-reaction
  "Removes another user's reaction from a message. channel-id, message-id,
  and user-id are string snowflakes. emoji is a URL-encoded string.
  Returns :ok on success."
  ([channel-id message-id emoji user-id]
   (delete-user-reaction (current-conn) channel-id message-id emoji user-id))
  ([conn channel-id message-id emoji user-id]
   (http/delete-user-reaction conn channel-id message-id emoji user-id)))

(defn get-reactions
  "Fetches users who reacted with a given emoji on a message. channel-id and
  message-id are string snowflakes. emoji is a URL-encoded string.
  query-params is an optional map (e.g. {:limit 25 :after user-id}).
  Returns a vector of user maps."
  ([channel-id message-id emoji]
   (get-reactions (current-conn) channel-id message-id emoji nil))
  ([channel-id message-id emoji query-params]
   (get-reactions (current-conn) channel-id message-id emoji query-params))
  ([conn channel-id message-id emoji query-params]
   (http/get-reactions conn channel-id message-id emoji query-params)))

; Guilds

(defn get-guild
  "Fetches a guild by ID. guild-id is a string snowflake. Returns a guild map
  with :id, :name, :icon, :owner_id, :roles, etc."
  ([guild-id] (get-guild (current-conn) guild-id))
  ([conn guild-id] (http/get-guild conn guild-id)))

(defn modify-guild
  "Updates a guild's settings. guild-id is a string snowflake. params is a map
  of fields to change (e.g. {:name \"new name\"}). Returns the updated guild map."
  ([guild-id params] (modify-guild (current-conn) guild-id params))
  ([conn guild-id params] (http/modify-guild conn guild-id params)))

; Members

(defn get-guild-member
  "Fetches a guild member. guild-id and user-id are string snowflakes.
  Returns a member map with :user, :nick, :roles, :joined_at, etc."
  ([guild-id user-id] (get-guild-member (current-conn) guild-id user-id))
  ([conn guild-id user-id] (http/get-guild-member conn guild-id user-id)))

(defn list-guild-members
  "Lists members of a guild. guild-id is a string snowflake. query-params is
  an optional map (e.g. {:limit 100 :after user-id}). Returns a vector of
  member maps."
  ([guild-id] (list-guild-members (current-conn) guild-id nil))
  ([guild-id query-params] (list-guild-members (current-conn) guild-id query-params))
  ([conn guild-id query-params] (http/list-guild-members conn guild-id query-params)))

(defn modify-guild-member
  "Updates a guild member's attributes. guild-id and user-id are string
  snowflakes. params is a map of fields to change (e.g. {:nick \"new nick\"}).
  Returns the updated member map."
  ([guild-id user-id params] (modify-guild-member (current-conn) guild-id user-id params))
  ([conn guild-id user-id params] (http/modify-guild-member conn guild-id user-id params)))

(defn remove-guild-member
  "Kicks a member from a guild. guild-id and user-id are string snowflakes.
  Returns :ok on success."
  ([guild-id user-id] (remove-guild-member (current-conn) guild-id user-id))
  ([conn guild-id user-id] (http/remove-guild-member conn guild-id user-id)))

(defn add-guild-member-role
  "Assigns a role to a guild member. guild-id, user-id, and role-id are string
  snowflakes. Returns :ok on success."
  ([guild-id user-id role-id] (add-guild-member-role (current-conn) guild-id user-id role-id))
  ([conn guild-id user-id role-id] (http/add-guild-member-role conn guild-id user-id role-id)))

(defn remove-guild-member-role
  "Removes a role from a guild member. guild-id, user-id, and role-id are
  string snowflakes. Returns :ok on success."
  ([guild-id user-id role-id] (remove-guild-member-role (current-conn) guild-id user-id role-id))
  ([conn guild-id user-id role-id] (http/remove-guild-member-role conn guild-id user-id role-id)))

; Roles

(defn get-guild-roles
  "Lists all roles in a guild. guild-id is a string snowflake. Returns a
  vector of role maps with :id, :name, :color, :permissions, etc."
  ([guild-id] (get-guild-roles (current-conn) guild-id))
  ([conn guild-id] (http/get-guild-roles conn guild-id)))

(defn create-guild-role
  "Creates a new role in a guild. guild-id is a string snowflake. params is
  a map (e.g. {:name \"Mod\" :color 0x00FF00 :permissions \"8\"}).
  Returns the created role map."
  ([guild-id params] (create-guild-role (current-conn) guild-id params))
  ([conn guild-id params] (http/create-guild-role conn guild-id params)))

(defn modify-guild-role
  "Updates a role's attributes. guild-id and role-id are string snowflakes.
  params is a map of fields to change. Returns the updated role map."
  ([guild-id role-id params] (modify-guild-role (current-conn) guild-id role-id params))
  ([conn guild-id role-id params] (http/modify-guild-role conn guild-id role-id params)))

(defn delete-guild-role
  "Deletes a role from a guild. guild-id and role-id are string snowflakes.
  Returns :ok on success."
  ([guild-id role-id] (delete-guild-role (current-conn) guild-id role-id))
  ([conn guild-id role-id] (http/delete-guild-role conn guild-id role-id)))

; Interactions / Slash Commands

(defn register-global-command
  "Registers an application command globally (available in all guilds).
  app-id is a string snowflake. command-data is a map with :name, :description,
  and optionally :options (vector of option maps). Returns the created command map."
  ([app-id command-data] (register-global-command (current-conn) app-id command-data))
  ([conn app-id command-data] (interactions/register-global-command conn app-id command-data)))

(defn register-guild-command
  "Registers an application command for a specific guild. app-id and guild-id
  are string snowflakes. command-data is a map with :name, :description, and
  optionally :options. Returns the created command map."
  ([app-id guild-id command-data]
   (register-guild-command (current-conn) app-id guild-id command-data))
  ([conn app-id guild-id command-data]
   (interactions/register-guild-command conn app-id guild-id command-data)))

(defn list-global-commands
  "Lists all global application commands. app-id is a string snowflake.
  Returns a vector of command maps."
  ([app-id] (list-global-commands (current-conn) app-id))
  ([conn app-id] (interactions/list-global-commands conn app-id)))

(defn list-guild-commands
  "Lists all application commands for a specific guild. app-id and guild-id
  are string snowflakes. Returns a vector of command maps."
  ([app-id guild-id] (list-guild-commands (current-conn) app-id guild-id))
  ([conn app-id guild-id] (interactions/list-guild-commands conn app-id guild-id)))

(defn delete-global-command
  "Deletes a global application command. app-id and command-id are string
  snowflakes. Returns :ok on success."
  ([app-id command-id] (delete-global-command (current-conn) app-id command-id))
  ([conn app-id command-id] (interactions/delete-global-command conn app-id command-id)))

(defn delete-guild-command
  "Deletes a guild-specific application command. app-id, guild-id, and
  command-id are string snowflakes. Returns :ok on success."
  ([app-id guild-id command-id]
   (delete-guild-command (current-conn) app-id guild-id command-id))
  ([conn app-id guild-id command-id]
   (interactions/delete-guild-command conn app-id guild-id command-id)))

(defn bulk-overwrite-global-commands
  "Replaces all global application commands with the provided list. app-id is
  a string snowflake. commands is a vector of command data maps. Returns a
  vector of the new command maps."
  ([app-id commands] (bulk-overwrite-global-commands (current-conn) app-id commands))
  ([conn app-id commands] (interactions/bulk-overwrite-global-commands conn app-id commands)))

(defn bulk-overwrite-guild-commands
  "Replaces all application commands for a specific guild. app-id and guild-id
  are string snowflakes. commands is a vector of command data maps. Returns a
  vector of the new command maps."
  ([app-id guild-id commands]
   (bulk-overwrite-guild-commands (current-conn) app-id guild-id commands))
  ([conn app-id guild-id commands]
   (interactions/bulk-overwrite-guild-commands conn app-id guild-id commands)))

(defn respond-to-interaction
  "Sends a response to an interaction. interaction-id is a string snowflake.
  interaction-token is a string. callback-type is either a keyword
  (:channel-message-with-source, :deferred-channel-message-with-source,
  :update-message, :deferred-update-message, :autocomplete-result, :modal,
  :pong) or a raw integer. data is an optional map (e.g. {:content \"hi\"}).
  Returns the API response map."
  ([interaction-id interaction-token callback-type]
   (respond-to-interaction (current-conn) interaction-id interaction-token callback-type nil))
  ([interaction-id interaction-token callback-type data]
   (respond-to-interaction (current-conn) interaction-id interaction-token callback-type data))
  ([conn interaction-id interaction-token callback-type data]
   (interactions/respond-to-interaction conn interaction-id interaction-token callback-type data)))

(defn create-followup-message
  "Sends a followup message to an interaction. app-id is a string snowflake.
  interaction-token is a string. message-data is a map with :content and/or
  :embeds. Returns the created message map."
  ([app-id interaction-token message-data]
   (create-followup-message (current-conn) app-id interaction-token message-data))
  ([conn app-id interaction-token message-data]
   (interactions/create-followup-message conn app-id interaction-token message-data)))

(defn edit-original-response
  "Edits the original interaction response. app-id is a string snowflake.
  interaction-token is a string. message-data is a map of fields to update.
  Returns the updated message map."
  ([app-id interaction-token message-data]
   (edit-original-response (current-conn) app-id interaction-token message-data))
  ([conn app-id interaction-token message-data]
   (interactions/edit-original-response conn app-id interaction-token message-data)))

(defn delete-original-response
  "Deletes the original interaction response. app-id is a string snowflake.
  interaction-token is a string. Returns :ok on success."
  ([app-id interaction-token]
   (delete-original-response (current-conn) app-id interaction-token))
  ([conn app-id interaction-token]
   (interactions/delete-original-response conn app-id interaction-token)))
