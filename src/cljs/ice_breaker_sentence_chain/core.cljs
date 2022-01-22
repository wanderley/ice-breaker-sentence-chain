(ns ice-breaker-sentence-chain.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]))

(enable-console-print!)

(def ws-url "ws://localhost:3449/ws")

(defonce app-state (atom {:status "offline"
                          :component 'login}))


;;; Actions

(defn set-connection-status! [status]
  (reset! app-state (assoc @app-state :status status)))


;;; Websocket

(defonce outgoing-messages (async/chan))

(defn send-message [message]
  (async/put! outgoing-messages message))

(defn send-messages [ws-channel]
  (async/go-loop []
    (when-let [message (async/<! outgoing-messages)]
      (async/>! ws-channel message)
      (recur))))

(defn receive-messages [ws-channel]
  (async/go-loop []
    (let [{:keys [message]} (async/<! ws-channel)]
      (let [{:keys [type data]} message]
        (case type
          login (swap! app-state assoc :component 'sentence-chain)))
      (recur))))

(defn connect!
  "Connects with the server and starts the input and output channels."
  [username]
  (async/go
    (let [{:keys [ws-channel error]} (async/<! (ws-ch ws-url))]
      (if error
        (set-connection-status! "connection-error")
        (do
          (set-connection-status! "online")
          (send-messages ws-channel)
          (receive-messages ws-channel)
          (send-message {:type 'login
                         :data {:username username}}))))))


;;; Views

(defn login []
  (let [username (atom "")]
    (fn []
      [:div
       [:form {:on-submit (fn [_]
                            (.preventDefault _)
                            (connect! @username))}
        [:input {:type "text"
                 :placeholder "What is your nickname?"
                 :value @username
                 :on-change #(reset! username (-> % .-target .-value))}]
        [:button {:type "submit"} "Login"]]])))

(defn sentence-chain []
  [:div "Sentence Chain: TODO"])

(defn container []
  [:div
   [:h1 "Ice Breaker: Sentence Chain"]
   [:p "Connection status: " [:strong (:status @app-state)]]
   (case (:component @app-state)
     login          [login]
     sentence-chain [sentence-chain])])

(rd/render [container]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
