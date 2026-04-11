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
(defn register-global-command
  "Registers an application command globally (available in all guilds).
  app-id is a string snowflake. command-data is a map with :name, :description,
  and optionally :options (vector of option maps). Returns the created command map."
  [app-id command-data]
  (api/api-request {:method :post
                    :path   (str "applications/" app-id "/commands")
                    :body   command-data}))

(defn register-guild-command
  "Registers an application command for a specific guild. app-id and guild-id
  are string snowflakes. command-data is a map with :name, :description, and
  optionally :options. Returns the created command map."
  [app-id guild-id command-data]
  (api/api-request {:method :post
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")
                    :body   command-data}))

(defn list-global-commands
  "Lists all global application commands. app-id is a string snowflake.
  Returns a vector of command maps."
  [app-id]
  (api/api-request {:method :get
                    :path   (str "applications/" app-id "/commands")}))

(defn list-guild-commands
  "Lists all application commands for a specific guild. app-id and guild-id
  are string snowflakes. Returns a vector of command maps."
  [app-id guild-id]
  (api/api-request {:method :get
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")}))

(defn delete-global-command
  "Deletes a global application command. app-id and command-id are string
  snowflakes. Returns :ok on success."
  [app-id command-id]
  (api/api-request {:method :delete
                    :path   (str "applications/" app-id "/commands/" command-id)}))

(defn delete-guild-command
  "Deletes a guild-specific application command. app-id, guild-id, and
  command-id are string snowflakes. Returns :ok on success."
  [app-id guild-id command-id]
  (api/api-request {:method :delete
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands/" command-id)}))

(defn bulk-overwrite-global-commands
  "Replaces all global application commands with the provided list. app-id is
  a string snowflake. commands is a vector of command data maps. Returns a
  vector of the new command maps."
  [app-id commands]
  (api/api-request {:method :put
                    :path   (str "applications/" app-id "/commands")
                    :body   commands}))

(defn bulk-overwrite-guild-commands
  "Replaces all application commands for a specific guild. app-id and guild-id
  are string snowflakes. commands is a vector of command data maps. Returns a
  vector of the new command maps."
  [app-id guild-id commands]
  (api/api-request {:method :put
                    :path   (str "applications/" app-id "/guilds/" guild-id "/commands")
                    :body   commands}))

; Interaction responses
(defn respond-to-interaction
  "Sends a response to an interaction. interaction-id is a string snowflake.
  interaction-token is a string. callback-type is either a keyword
  (:channel-message-with-source, :deferred-channel-message-with-source,
  :update-message, :deferred-update-message, :autocomplete-result, :modal,
  :pong) or a raw integer. data is an optional map (e.g. {:content \"hi\"}).
  Returns the API response map."
  [interaction-id interaction-token callback-type & [data]]
  (let [type-val (if (keyword? callback-type)
                   (get callback-types callback-type)
                   callback-type)]
    (api/api-request {:method :post
                      :path   (str "interactions/" interaction-id "/" interaction-token "/callback")
                      :body   (cond-> {:type type-val}
                                data (assoc :data data))})))

(defn create-followup-message
  "Sends a followup message to an interaction. app-id is a string snowflake.
  interaction-token is a string. message-data is a map with :content and/or
  :embeds. Returns the created message map."
  [app-id interaction-token message-data]
  (api/api-request {:method :post
                    :path   (str "webhooks/" app-id "/" interaction-token)
                    :body   message-data}))

(defn edit-original-response
  "Edits the original interaction response. app-id is a string snowflake.
  interaction-token is a string. message-data is a map of fields to update.
  Returns the updated message map."
  [app-id interaction-token message-data]
  (api/api-request {:method :patch
                    :path   (str "webhooks/" app-id "/" interaction-token "/messages/@original")
                    :body   message-data}))

(defn delete-original-response
  "Deletes the original interaction response. app-id is a string snowflake.
  interaction-token is a string. Returns :ok on success."
  [app-id interaction-token]
  (api/api-request {:method :delete
                    :path   (str "webhooks/" app-id "/" interaction-token "/messages/@original")}))
