(ns discord-bot.intents)

(def guilds (bit-shift-left 1 0))
(def guild-members (bit-shift-left 1 1))
(def guild-moderation (bit-shift-left 1 2))
(def guild-expressions (bit-shift-left 1 3))
(def guild-integrations (bit-shift-left 1 4))
(def guild-webhooks (bit-shift-left 1 5))
(def guild-invites (bit-shift-left 1 6))
(def guild-voice-states (bit-shift-left 1 7))
(def guild-presences (bit-shift-left 1 8))
(def guild-messages (bit-shift-left 1 9))
(def guild-message-reactions (bit-shift-left 1 10))
(def guild-message-typing (bit-shift-left 1 11))
(def direct-messages (bit-shift-left 1 12))
(def direct-message-reactions (bit-shift-left 1 13))
(def direct-message-typing (bit-shift-left 1 14))
(def message-content (bit-shift-left 1 15))
(def guild-scheduled-events (bit-shift-left 1 16))
(def auto-moderation-configuration (bit-shift-left 1 20))
(def auto-moderation-execution (bit-shift-left 1 21))
(def guild-message-polls (bit-shift-left 1 24))
(def direct-message-polls (bit-shift-left 1 25))

(def default-intents
  (bit-or guilds guild-members guild-presences guild-messages
          guild-message-reactions guild-voice-states))

(defn combine [& intents]
  (apply bit-or 0 intents))
