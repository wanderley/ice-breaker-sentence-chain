(ns ice-breaker-sentence-chain.handler
  (:require [org.httpkit.server :as hk]
            [chord.http-kit :refer [with-channel]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.core.async :as async]
            [ring.util.response :as resp]
            [medley.core :refer [random-uuid]]))

(defonce main-chan (async/chan))

(defonce main-mult (async/mult main-chan))

(defonce users (atom {}))

(defn ws-handler
  [req]
  (with-channel req client-channel
    (let [client-tap (async/chan)
          client-id (.toString (random-uuid))]
      (async/tap main-mult client-tap)
      (async/go-loop []
        (async/alt!
          client-tap ([message]
                      (if message
                        (do
                          (async/>! client-channel message)
                          (recur))
                        (async/close! client-channel)))
          client-channel ([{:keys [message]}]
                          (if message
                            (let [{:keys [type data]} message]
                              (case type
                                login (do (swap! users assoc client-id (:username data))
                                          (async/>! client-channel {:type 'login :data 'success})))
                              (recur))
                            (do
                              (swap! users dissoc client-id)
                              (async/untap main-mult client-tap)))))))))

(defroutes app
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
