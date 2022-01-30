(ns ice-breaker-sentence-chain.server.handler
  (:require [org.httpkit.server :as hk]
            [chord.http-kit :refer [with-channel]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.core.async :as async]
            [ring.util.response :as resp]
            [medley.core :refer [random-uuid]]
            [ice-breaker-sentence-chain.shared.event-handler
             :refer [event effect defevent defeffect defemit]]))

;;; State

(def initial-state {:users    []
                    :sentence ""})
(defonce app-state (atom initial-state))
(defemit app-state)

(defevent :default [app-state & _]
  {:app-state app-state})

(defevent :login [app-state client-id username]
  {:app-state
   (assoc app-state
          :users (conj (:users app-state)
                       [client-id username]))})

(defevent :logout [app-state client-id]
  {:app-state
   (assoc app-state :users
          (->> (:users app-state)
               (filterv #(not (= (first %) client-id)))))})

(defevent :contribution [app-state client-id contribution]
  {:app-state
   (-> app-state
       (assoc :sentence (str (:sentence app-state) " " contribution))
       (assoc :users
              (let [users (:users app-state)]
                (into [] (conj (vec (drop 1 users))
                               (first users))))))})

(defn get-client-state [app-state]
  {:users    (mapv second (:users app-state))
   :sentence (:sentence app-state)})

;;; Communication

(defonce main-chan (async/chan))

(defonce main-mult (async/mult main-chan))

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
                            (do
                              (apply emit (concat [(first message) client-id]
                                                  (rest message)))
                              (async/>! main-chan
                                        [:sync (get-client-state @app-state)])
                              (recur))
                            (do
                              (emit :logout client-id)
                              (async/untap main-mult client-tap)
                              (async/>! main-chan
                                        [:sync (get-client-state @app-state)])))))))))

(defroutes app
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
