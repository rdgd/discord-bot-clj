(ns discord-bot.clients.discord.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [discord-bot.clients.discord.http :as http]))

(deftest parse-channels-groups-by-type
  (let [channels [{:name "general" :id "1" :type 0}
                  {:name "voice-chat" :id "2" :type 2}
                  {:name "Category" :id "3" :type 4}
                  {:name "random" :id "4" :type 0}]]
    (testing "groups text channels (type 0)"
      (is (= [{:name "general" :id "1" :type 0}
              {:name "random" :id "4" :type 0}]
             (:text (http/parse-channels channels)))))

    (testing "groups voice channels (type 2)"
      (is (= [{:name "voice-chat" :id "2" :type 2}]
             (:voice (http/parse-channels channels)))))

    (testing "groups category channels (type 4)"
      (is (= [{:name "Category" :id "3" :type 4}]
             (:parents (http/parse-channels channels)))))))

(deftest parse-channels-selects-keys
  (testing "strips extra fields from channel objects"
    (let [channels [{:name "general" :id "1" :type 0 :topic "hi" :position 0}]
          result (http/parse-channels channels)]
      (is (= [{:name "general" :id "1" :type 0}]
             (:text result))))))

(deftest parse-channels-empty-input
  (is (= {:text [] :voice [] :parents []}
         (http/parse-channels []))))

(deftest parse-channels-unknown-type
  (testing "skips channels with unrecognized type"
    (let [channels [{:name "stage" :id "1" :type 13}]]
      (is (= {:text [] :voice [] :parents []}
             (http/parse-channels channels))))))
