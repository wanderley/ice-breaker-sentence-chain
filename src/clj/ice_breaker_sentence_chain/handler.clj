(ns ice-breaker-sentence-chain.handler
  (:require [org.httpkit.server :as hk]
            [chord.http-kit :refer [with-channel]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [clojure.core.async :as async]
            [ring.util.response :as resp]
            [medley.core :refer [random-uuid]]))

;;; State

(def initial-state {:users    []
                    :sentence "Something"})
(defonce app-state (atom initial-state))

(defn add-user! [client-id username]
  (swap! app-state
         #(assoc % :users
                 (conj (:users %)
                       [client-id username]))))

(defn remove-user! [client-id]
  (swap! app-state
         #(assoc % :users
                 (->> (:users %)
                      (remove (fn [u] (= (first u) client-id)))
                      (vec)))))

(defn set-contribution! [contribution]
  (swap! app-state
         #(-> %
           (assoc :sentence
                  (str (:sentence %) " " contribution))
           (assoc :users
                  (let [users (:users @app-state)]
                    (into [] (conj (vec (drop 1 users))
                                   (first users))))))))

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
                            (let [{:keys [type data]} message]
                              (case type
                                login (async/>! main-chan
                                                {:type 'state
                                                 :data (->> (:username data)
                                                            (add-user! client-id)
                                                            (get-client-state))})
                                contribution (async/>! main-chan
                                                       {:type 'state
                                                        :data (->> data
                                                                   (set-contribution!)
                                                                   (get-client-state))}))
                              (recur))
                            (do
                              (async/untap main-mult client-tap)
                              (async/>! main-chan
                                        {:type 'state
                                         :data (-> client-id
                                                   (remove-user!)
                                                   (get-client-state))})))))))))

(defroutes app
  (GET "/ws" [] ws-handler)
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
