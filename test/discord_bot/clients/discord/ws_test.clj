(ns discord-bot.clients.discord.ws-test
  (:require [clojure.test :refer [deftest is testing]]
            [discord-bot.clients.discord.ws :as ws]))

(deftest started-new-game?-test
  (testing "detects when game name changes"
    (is (true? (ws/started-new-game? {:name "Elden Ring"} {:name "Factorio"}))))

  (testing "returns false when game name is the same"
    (is (false? (ws/started-new-game? {:name "Elden Ring"} {:name "Elden Ring"}))))

  (testing "detects going from no game to a game"
    (is (true? (ws/started-new-game? nil {:name "Factorio"}))))

  (testing "detects going from a game to no game"
    (is (true? (ws/started-new-game? {:name "Factorio"} nil))))

  (testing "both nil is not a new game"
    (is (false? (ws/started-new-game? nil nil)))))

(deftest reconnect?-test
  (testing "reconnectable close codes (network/transient errors)"
    (doseq [code [1000 1001 1006 4000 4001 4002 4003 4005 4007 4008 4009]]
      (is (true? (ws/reconnect? code)) (str "should reconnect on code " code))))

  (testing "non-reconnectable close codes (auth/config failures per Discord spec)"
    (doseq [code [4004 4010 4011 4012 4013 4014]]
      (is (false? (ws/reconnect? code)) (str "should not reconnect on code " code)))))
