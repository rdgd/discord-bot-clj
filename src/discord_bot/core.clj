(ns discord-bot.core
  (:require [discord-bot.clients.discord.http :as http]
            [discord-bot.clients.discord.interactions :as interactions]
            [discord-bot.clients.discord.ws :as ws])
  (:gen-class))

; WEBSOCKETS AND INITIALIZATION
(def init ws/initialize)
(def stop ws/stop)

; UTILITY
(def get-presences     ws/get-presences)
(def get-user-by-id    ws/get-user-by-id)
(def started-new-game? ws/started-new-game?)
(def server-state      ws/server-state)

; CHANNELS
(def get-channels         http/get-channels)
(def send-channel-message http/send-channel-message)
(def close-connection     ws/close-connection)

; MESSAGES
(def get-channel-message  http/get-channel-message)
(def get-channel-messages http/get-channel-messages)
(def edit-message         http/edit-message)
(def delete-message       http/delete-message)
(def bulk-delete-messages http/bulk-delete-messages)

; CHANNEL MANAGEMENT
(def get-channel    http/get-channel)
(def modify-channel http/modify-channel)
(def delete-channel http/delete-channel)
(def create-channel http/create-channel)

; GUILDS
(def get-guild    http/get-guild)
(def modify-guild http/modify-guild)

; MEMBERS
(def get-guild-member         http/get-guild-member)
(def list-guild-members       http/list-guild-members)
(def modify-guild-member      http/modify-guild-member)
(def remove-guild-member      http/remove-guild-member)
(def add-guild-member-role    http/add-guild-member-role)
(def remove-guild-member-role http/remove-guild-member-role)

; ROLES
(def get-guild-roles   http/get-guild-roles)
(def create-guild-role http/create-guild-role)
(def modify-guild-role http/modify-guild-role)
(def delete-guild-role http/delete-guild-role)

; REACTIONS
(def create-reaction      http/create-reaction)
(def delete-own-reaction  http/delete-own-reaction)
(def delete-user-reaction http/delete-user-reaction)
(def get-reactions        http/get-reactions)

; INTERACTIONS / SLASH COMMANDS
(def register-global-command        interactions/register-global-command)
(def register-guild-command         interactions/register-guild-command)
(def list-global-commands           interactions/list-global-commands)
(def list-guild-commands            interactions/list-guild-commands)
(def delete-global-command          interactions/delete-global-command)
(def delete-guild-command           interactions/delete-guild-command)
(def bulk-overwrite-global-commands interactions/bulk-overwrite-global-commands)
(def bulk-overwrite-guild-commands  interactions/bulk-overwrite-guild-commands)
(def respond-to-interaction         interactions/respond-to-interaction)
(def create-followup-message        interactions/create-followup-message)
(def edit-original-response         interactions/edit-original-response)
(def delete-original-response       interactions/delete-original-response)
