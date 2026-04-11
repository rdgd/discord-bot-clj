(ns discord-bot.core
  (:require [potemkin :refer [import-vars]]))

(import-vars
  [discord-bot.clients.discord.ws
   init
   stop
   get-presences
   get-user-by-id
   started-new-game?
   server-state
   close-connection]

  [discord-bot.clients.discord.http
   get-channels
   send-channel-message
   get-channel-message
   get-channel-messages
   edit-message
   delete-message
   bulk-delete-messages
   get-channel
   modify-channel
   delete-channel
   create-channel
   get-guild
   modify-guild
   get-guild-member
   list-guild-members
   modify-guild-member
   remove-guild-member
   add-guild-member-role
   remove-guild-member-role
   get-guild-roles
   create-guild-role
   modify-guild-role
   delete-guild-role
   create-reaction
   delete-own-reaction
   delete-user-reaction
   get-reactions]

  [discord-bot.clients.discord.interactions
   register-global-command
   register-guild-command
   list-global-commands
   list-guild-commands
   delete-global-command
   delete-guild-command
   bulk-overwrite-global-commands
   bulk-overwrite-guild-commands
   respond-to-interaction
   create-followup-message
   edit-original-response
   delete-original-response])
