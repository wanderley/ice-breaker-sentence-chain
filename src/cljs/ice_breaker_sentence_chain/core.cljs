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

(defn login!
  "Executes the login of a user"
  [username]
  (swap! app-state
         #(-> %
              (assoc :username username)
              (assoc :component 'sentence-chain))))


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

(defn login [username]
  (let [username (atom (or username ""))]
    (fn []
      [:div {:style {:position "absolute"
                     :top "30%"
                     :width "100%"}}
       [:form {:on-submit (fn [_]
                            (.preventDefault _)
                            (login! @username))}
        [:div {:style {:width "80%"
                       :margin-left "auto"
                       :margin-right "auto"}}
         [:input {:type "text"
                  :placeholder "My name is ..."
                  :style {:width "100%"
                          :boxSizing "border-box"
                          :font-size "2rem"
                          :padding "0.5rem"
                          :margin-bottom "0.5rem"
                          :border-radius "10px"}
                  :value @username
                  :on-change #(reset! username (-> % .-target .-value))}]]
        [:div {:style {:width "80%"
                       :text-align "right"
                       :margin "auto"}}
         [:button {:type "submit"
                   :style {:font-size "1.5rem"
                           :padding "0.5rem"
                           :background "lightgreen"
                           :border-radius "10px"}}
          "Let's go!"]]]])))

(defn sentence-chain []
  [:div {:style {:width "100%"
                 :position "absolute"
                 :top "10%"}}
   [:div {:style {:width "80%"
                  :height "80%"
                  :margin-left "auto"
                  :margin-right "auto"}}
    [:div {:style {:display "grid"
                   :grid-template-columns "1fr 3fr"
                   :grid-template-rows "1fr min-content"
                   :grid-template-areas (pr-str "one two"
                                                "one three")}}
     [:div {:style {:border "1px solid gray"
                    :border-radius "10px"
                    :padding "0.5rem"
                    :font-size "1.5rem"
                    :grid-area "one"
                    :height "20rem"}}
      [:div "User 1"]
      [:div "User 2"]
      [:div "User 3"]
      [:div "User 4"]
      [:div "User 5"]
      [:div "User 6"]]
     [:div {:style {:margin-left "0.5rem"
                    :border "1px solid gray"
                    :grid-area "two"
                    :height "17rem"
                    :border-radius "10px"}}
      [:div {:style {:padding "0.5rem"
                     :font-size "2rem"}}
       "Lorem Ipsum is simply dummy text of the printing and typesetting"]]
     [:div {:style {:grid-area "three"
                    :margin-left "0.5rem"}}
      [:input {:type "text"
               :placeholder "Wait your turn ..."
               :style {:width "100%"
                       :boxSizing "border-box"
                       :font-size "2rem"
                       :padding "0.5rem"
                       :border "1px solid gray"
                       :border-radius "10px"}}]]]]])

(defn container []
  (case (:component @app-state)
    login          [login (:username @app-state)]
    sentence-chain [sentence-chain]))

(rd/render [container]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
