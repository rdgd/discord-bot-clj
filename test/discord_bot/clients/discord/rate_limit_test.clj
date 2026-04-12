(ns discord-bot.clients.discord.rate-limit-test
  (:require [clojure.test :refer [deftest is testing]]
            [discord-bot.clients.discord.rate-limit :as rl]))

(deftest route-key-normalizes-ids
  (testing "normalizes snowflake IDs in paths"
    (let [rk (rl/route-key :get "https://discord.com/api/v10/channels/123456789012345678/messages/987654321098765432")]
      (is (string? rk))
      (is (not (.contains rk "987654321098765432")) "non-major IDs should be replaced")))

  (testing "different channels produce different route keys"
    (is (not= (rl/route-key :get "https://discord.com/api/v10/channels/111111111111111111/messages")
              (rl/route-key :get "https://discord.com/api/v10/channels/222222222222222222/messages"))))

  (testing "different methods produce different route keys"
    (is (not= (rl/route-key :get "https://discord.com/api/v10/channels/111111111111111111/messages")
              (rl/route-key :post "https://discord.com/api/v10/channels/111111111111111111/messages")))))

(deftest route-key-preserves-major-params
  (testing "channel id is preserved as major param"
    (let [rk (rl/route-key :get "https://discord.com/api/v10/channels/123456789012345678/messages")]
      (is (.contains rk "123456789012345678")))))

(deftest update-rate-limits-tracks-buckets
  (testing "parses rate limit headers and stores bucket state"
    (let [state (rl/new-state)
          rk "GET /channels/:major/messages"
          response {:headers {"x-ratelimit-bucket"    "abc123"
                              "x-ratelimit-remaining" "4"
                              "x-ratelimit-reset"     "1700000000.000"}}]
      (rl/update-rate-limits! state rk response)
      (let [{:keys [buckets route->bucket]} @state]
        (is (= "abc123" (get route->bucket rk)))
        (is (= 4.0 (:remaining (get buckets "abc123"))))
        (is (= 1700000000.0 (:reset-at (get buckets "abc123")))))))

  (testing "ignores responses without rate limit headers"
    (let [state (rl/new-state)]
      (rl/update-rate-limits! state "GET /foo" {:headers {}})
      (is (empty? (:buckets @state))))))
