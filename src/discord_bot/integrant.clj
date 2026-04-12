(ns discord-bot.integrant
  "Integrant integration for discord-bot. Requires integrant on the classpath;
  users who don't use integrant should not load this namespace.

  Example system map:

    (require '[integrant.core :as ig])
    (require 'discord-bot.integrant)  ; registers methods

    (def config
      {:discord-bot/connection
       {:config   {:token           \"...\"
                   :project-name    \"my-bot\"
                   :project-version \"1.0.0\"}
        :handlers {:on-message-create (fn [conn msg] ...)}
        :intents  513}})

    (def system (ig/init config))
    (def conn (:discord-bot/connection system))
    ; use conn with discord-bot.core functions
    (ig/halt! system)"
  (:require [discord-bot.core :as discord]
            [integrant.core :as ig]))

(defmethod ig/init-key :discord-bot/connection
  [_ opts]
  (discord/connect opts))

(defmethod ig/halt-key! :discord-bot/connection
  [_ conn]
  (discord/disconnect conn))
