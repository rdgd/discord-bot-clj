# discord-bot

A Clojure library for the [Discord API](https://discord.com/developers/docs/intro) (v10). The library provides an API for [gateway](https://discord.com/developers/docs/events/gateway) websocket connection, 
a REST client, and [slash command](https://discord.com/developers/docs/interactions/application-commands) support.

## Install

deps.edn:
```clojure
io.rdgd/discord-bot {:mvn/version "0.1.19"}
```

## Configuration

Create a `bot.edn` in your project root:

```clojure
{:token "your-bot-token"
 :project-name "my-bot"
 :project-version "0.1.0"}
```

## Usage

### Connecting to the gateway

```clojure
(require '[discord-bot.core :as bot]
         '[discord-bot.intents :as intents])

(bot/init {:intents (intents/combine intents/guilds
                                     intents/guild-messages
                                     intents/message-content)
           :on-message-create (fn [msg] (println "Got message:" (:content msg)))
           :on-interaction-create (fn [interaction] (println "Got interaction:" interaction))})
```

[Intents](https://discord.com/developers/docs/events/gateway#gateway-intents) are mandatory in API v10. Use `intents/default-intents` for a reasonable starting set, or combine specific ones. [Privileged intents](https://discord.com/developers/docs/events/gateway#privileged-intents) (`guild-members`, `guild-presences`, `message-content`) require approval in the Discord Developer Portal for bots in 100+ servers.

### Sending messages

```clojure
(bot/send-channel-message "channel-id" {:content "Hello"})
```

### Slash commands

```clojure
;; Register a global command
(bot/register-global-command "app-id"
  {:name "ping" :description "Responds with pong" :type 1})

;; Handle interactions — :id and :token come from the interaction payload
(bot/init {:intents ...
           :on-interaction-create
           (fn [{:keys [id token data]}]
             (when (= (:name data) "ping")
               (bot/respond-to-interaction id token
                 :channel-message-with-source
                 {:content "Pong!"})))})
```

### REST API

Messages:
`get-channel-message`, `get-channel-messages`, `send-channel-message`, `edit-message`, `delete-message`, `bulk-delete-messages`

Channels:
`get-channel`, `get-channels`, `create-channel`, `modify-channel`, `delete-channel`

Guilds:
`get-guild`, `modify-guild`

Members:
`get-guild-member`, `list-guild-members`, `modify-guild-member`, `remove-guild-member`, `add-guild-member-role`, `remove-guild-member-role`

Roles:
`get-guild-roles`, `create-guild-role`, `modify-guild-role`, `delete-guild-role`

Reactions:
`create-reaction`, `delete-own-reaction`, `delete-user-reaction`, `get-reactions`

Interactions:
`register-global-command`, `register-guild-command`, `list-global-commands`, `list-guild-commands`, `delete-global-command`, `delete-guild-command`, `bulk-overwrite-global-commands`, `bulk-overwrite-guild-commands`, `respond-to-interaction`, `create-followup-message`, `edit-original-response`, `delete-original-response`

All REST calls are automatically [rate-limited](https://discord.com/developers/docs/topics/rate-limits) per Discord's bucket system.

### [Gateway events](https://discord.com/developers/docs/events/gateway-events)

Pass callbacks in the opts map to `init`:

| Key | Discord Event |
|---|---|
| `:on-message-create` | MESSAGE_CREATE |
| `:on-message-update` | MESSAGE_UPDATE |
| `:on-message-reaction-add` | MESSAGE_REACTION_ADD |
| `:on-presence-update` | PRESENCE_UPDATE |
| `:on-typing-start` | TYPING_START |
| `:on-channel-create` | CHANNEL_CREATE |
| `:on-guild-member-update` | GUILD_MEMBER_UPDATE |
| `:on-guild-member-remove` | GUILD_MEMBER_REMOVE |
| `:on-guild-role-delete` | GUILD_ROLE_DELETE |
| `:on-voice-state-update` | VOICE_STATE_UPDATE |
| `:on-interaction-create` | INTERACTION_CREATE |

The gateway handles reconnection and session resumption automatically.

## Publishing

```
clj -Spom && clj -A:jar discord-bot.jar
env CLOJARS_USERNAME=<username> CLOJARS_PASSWORD=<token> clj -X:deploy
```

## License

MIT
