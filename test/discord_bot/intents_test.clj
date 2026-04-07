(ns discord-bot.intents-test
  (:require [clojure.test :refer [deftest is testing]]
            [discord-bot.intents :as intents]))

(deftest intent-bit-values
  (testing "each intent is a single bit flag"
    (is (= 1 intents/guilds))
    (is (= 2 intents/guild-members))
    (is (= 4 intents/guild-moderation))
    (is (= 512 intents/guild-messages))
    (is (= 32768 intents/message-content))
    (is (= 4096 intents/direct-messages))))

(deftest combine-intents
  (testing "combines intents with bitwise or"
    (is (= 3 (intents/combine intents/guilds intents/guild-members)))
    (is (= 513 (intents/combine intents/guilds intents/guild-messages))))

  (testing "combining with itself is idempotent"
    (is (= intents/guilds (intents/combine intents/guilds intents/guilds))))

  (testing "single intent passes through"
    (is (= intents/guilds (intents/combine intents/guilds)))))

(deftest default-intents-value
  (testing "default-intents includes expected intents"
    (is (pos? (bit-and intents/default-intents intents/guilds)))
    (is (pos? (bit-and intents/default-intents intents/guild-messages)))
    (is (pos? (bit-and intents/default-intents intents/guild-presences)))
    (is (pos? (bit-and intents/default-intents intents/guild-voice-states))))

  (testing "default-intents does not include message-content (privileged)"
    (is (zero? (bit-and intents/default-intents intents/message-content)))))
