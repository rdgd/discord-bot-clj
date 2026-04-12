# discord-bot-clj

A Clojure library for the [Discord API](https://discord.com/developers/docs/intro) (v10). The library provides an API for [gateway](https://discord.com/developers/docs/events/gateway) websocket connection, 
a REST client, and [slash command](https://discord.com/developers/docs/interactions/application-commands) support.

## Install

deps.edn:
```clojure
io.rdgd/discord-bot {:mvn/version "0.3.1"}
```

## Configuration

Bot credentials live in a map you pass to `connect`. A common pattern is to keep them in `bot.edn` and load with `discord-bot.config/load-bot-config`:

```clojure
{:token "your-bot-token"
 :project-name "my-bot"
 :project-version "1.0.0"}
```

## Usage

### Connecting to the gateway

`connect` returns a connection map

```clojure
(require '[discord-bot.core :as bot]
         '[discord-bot.config :as bot-config]
         '[discord-bot.intents :as intents])

(def conn
  (bot/connect
   {:config   (bot-config/load-bot-config)
    :intents  (intents/combine intents/guilds
                               intents/guild-messages
                               intents/message-content)
    :handlers {:on-message-create
               (fn [msg] (println "Got message:" (:content msg)))
               :on-interaction-create
               (fn [interaction] (println "Got interaction:" interaction))}}))

; when shutting down:
(bot/disconnect conn)
```

`connect` registers a JVM shutdown hook by default so Discord sees a clean close frame on SIGTERM / SIGINT / normal exit.
Pass `:install-shutdown-hook? false` in opts to skip it (useful in REPL workflows or when you manage lifecycle yourself).

Event handlers take `(fn [data])`. The library binds `*conn*` before calling them, so API calls inside handlers work
without an explicit conn argument. If you need the connection explicitly (e.g. to pass to a `future`), deref `bot/*conn*`,
or use the return value of your call to `initialize`

[Intents](https://discord.com/developers/docs/events/gateway#gateway-intents) are mandatory in API v10. [Privileged intents](https://discord.com/developers/docs/events/gateway#privileged-intents) (`guild-members`, `guild-presences`, `message-content`) require approval in the Discord Developer Portal for bots in 100+ servers.

### Sending messages

```clojure
(bot/send-channel-message "channel-id" {:content "Hello"})
; or
(bot/send-channel-message conn "channel-id" {:content "Hello"})

```

### Implicit connection

Passing `conn` to every call is cumbersome. Scope it once with `with-conn` and the dynamic binding carries through:

```clojure
(bot/with-conn conn
  (bot/send-channel-message "channel-id" {:content "Hello"})
  (bot/get-user-by-id user-id))
```

Every API function supports both arities. Inside event handlers the library does the binding for you, so you can call API functions freely without wrapping. For work spawned into a new thread (`future`, `send-off`), dynamic bindings don't carry over; pass conn explicitly or use `bound-fn`.

### Slash commands

```clojure
; Register a global command
(bot/register-global-command conn "app-id"
  {:name "ping" :description "Responds with pong" :type 1})

; Handle interactions. :id and :token come from the interaction payload.
(def conn
  (bot/connect {:intents ...
                :handlers
                {:on-interaction-create
                 (fn [{:keys [id token data]}]
                   (when (= (:name data) "ping")
                     (bot/respond-to-interaction id token
                                                 :channel-message-with-source
                                                 {:content "Pong!"})))}}))
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

All REST calls are automatically [rate-limited](https://discord.com/developers/docs/topics/rate-limits) per Discord's bucket system. Rate-limit state is per-connection, so multiple bots in one JVM keep independent quotas.

### [Gateway events](https://discord.com/developers/docs/events/gateway-events)

Pass callbacks in the `:handlers` map to `connect`. Each callback receives `data`:

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

The gateway handles reconnection and session resumption automatically. A watchdog forces a reconnect if no traffic arrives from Discord for 90 seconds.

### Integrant

If you happen to use [integrant](https://github.com/weavejester/integrant) for system lifecycle, require `discord-bot.integrant` to register methods for `:discord-bot/connection`:

```clojure
(require '[integrant.core :as ig])
(require 'discord-bot.integrant)

(def system-config
  {:discord-bot/connection
   {:config   {:token "..." :project-name "my-bot" :project-version "1.0.0"}
    :intents  513
    :handlers {...}}})

(def system (ig/init system-config))
(def conn (:discord-bot/connection system))
(ig/halt! system)
```

Integrant is not a required dependency. It only needs to be on the classpath if you load `discord-bot.integrant`.

## Publishing

```
make build
env CLOJARS_USERNAME=rdgd CLOJARS_PASSWORD=<token> make deploy 
```

## License

MIT
