(ns discord-bot.clients.discord.rate-limit
  (:require [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [clojure.string :as cs]))

(defn new-state []
  (atom {:buckets {}
         :route->bucket {}
         :global-reset-at 0}))

(defn- now-seconds []
  (/ (System/currentTimeMillis) 1000.0))

(defn- parse-header [headers name]
  (when-let [v (get headers name)]
    (try (Double/parseDouble v) (catch Exception _ nil))))

(defn route-key [method url]
  (let [path (or (second (re-find #"https?://[^/]+(/.+)" url)) url)
        ; tag major param IDs so they survive the blanket replacement
        tagged (cs/replace path #"/(channels|guilds|webhooks)/(\d{16,20})" "/$1/MAJOR:$2")
        ; replace all remaining snowflake IDs
        normalized (cs/replace tagged #"/\d{16,20}" "/:id")
        ; restore major param IDs
        restored (cs/replace normalized #"MAJOR:(\d+)" "$1")]
    (str (name method) " " restored)))

(defn update-rate-limits! [state rk response]
  (let [headers (:headers response)]
    (when-let [bucket (get headers "x-ratelimit-bucket")]
      (let [remaining (parse-header headers "x-ratelimit-remaining")
            reset     (parse-header headers "x-ratelimit-reset")]
        (swap! state (fn [s]
                       (-> s
                           (assoc-in [:route->bucket rk] bucket)
                           (assoc-in [:buckets bucket] {:remaining remaining
                                                        :reset-at  reset}))))))))

(defn wait-if-needed! [state rk]
  (let [{:keys [buckets route->bucket global-reset-at]} @state
        now (now-seconds)]
    (when (> global-reset-at now)
      (let [wait-ms (long (* (- global-reset-at now) 1000))]
        (log/info "Global rate limit hit, waiting" wait-ms "ms")
        (Thread/sleep wait-ms)))
    (when-let [bucket-id (get route->bucket rk)]
      (when-let [{:keys [remaining reset-at]} (get buckets bucket-id)]
        (when (and remaining (<= remaining 0) reset-at (> reset-at now))
          (let [wait-ms (long (* (- reset-at now) 1000))]
            (log/info "Rate limit for" rk "- waiting" wait-ms "ms")
            (Thread/sleep wait-ms)))))))

(defn- handle-429! [state response]
  (let [retry-after (or (parse-header (:headers response) "retry-after") 1.0)
        global?     (get-in response [:body :global])]
    (when global?
      (swap! state assoc :global-reset-at (+ (now-seconds) retry-after)))
    (log/warn "Rate limited (429). Retry after" retry-after "seconds. Global?" global?)
    (Thread/sleep (long (* retry-after 1000)))))

(defn discord-request
  ([state opts] (discord-request state opts 0))
  ([state {:keys [method url] :as opts} attempt]
   (let [rk (route-key method url)]
     (wait-if-needed! state rk)
     (let [response (try
                      (http/request (assoc (dissoc opts :method)
                                           :method method
                                           :url url))
                      (catch clojure.lang.ExceptionInfo e
                        (let [resp (ex-data e)]
                          (if (= 429 (:status resp))
                            resp
                            (throw e)))))]
       (update-rate-limits! state rk response)
       (if (and (= 429 (:status response)) (< attempt 3))
         (do (handle-429! state response)
             (discord-request state opts (inc attempt)))
         response)))))
