(ns ice-breaker-sentence-chain.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]))

(enable-console-print!)

(def ws-url "ws://localhost:3449/ws")


(def initial-state {:status "offline"
                    :component 'login
                    :sentence ""})
(defonce app-state (atom initial-state))


(defn can-change-sentence? [app-state]
  (and
   (> (count (:users app-state)) 1)
   (= (first (:users app-state))
      (:username app-state))))


;;; Actions

(defn login!
  "Executes the login of a user"
  [username]
  (swap! app-state
         #(-> %
              (assoc :username username)
              (assoc :users [username])
              (assoc :component 'sentence-chain))))

(defn sync!
  "Syncs the (whole) server state with the client state.

  NOTE: In a bigger system, it would be better to have smaller updates but it
  won't pay too much here since the state is super small."
  [server-state]
  (swap! app-state merge server-state))

(defn set-connection-status! [status]
  (reset! app-state (assoc @app-state :status status)))


(comment
  ;; This code won't live long, since I am using it just to test the client-side
  ;; behavior.  But I will leave it here to show how I was testing the UI from
  ;; the REPL.

  (reset! app-state initial-state)
  (login! "Wanderley")
  (sync! {:users    ["Wanderley"] :sentence ""})
  ;; User can't type
  (sync! {:users    ["User 1" "Wanderley"]
                      :sentence "Lorem Ipsum is something else"})
  ;; User can type
  (sync! {:users    ["Wanderley" "User 1"]
                      :sentence "Lorem Ipsum is something else"})
  )

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

(defn sentence-chain [users sentence can-change-sentence?]
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
     (into
      [:div {:style {:border "1px solid gray"
                     :border-radius "10px"
                     :padding "0.5rem"
                     :font-size "1.5rem"
                     :grid-area "one"
                     :height "20rem"}}]
      (for [user users]
        [:div user]))
     [:div {:style {:margin-left "0.5rem"
                    :border "1px solid gray"
                    :grid-area "two"
                    :height "17rem"
                    :border-radius "10px"}}
      [:div {:style {:padding "0.5rem"
                     :font-size "2rem"}}
       sentence]]
     [:div {:style {:grid-area "three"
                    :margin-left "0.5rem"}}
      [:input {:type "text"
               :placeholder (if can-change-sentence?
                              "Your turn ..."
                              "Wait your turn ...")
               :disabled (not can-change-sentence?)
               :style {:width "100%"
                       :boxSizing "border-box"
                       :font-size "2rem"
                       :padding "0.5rem"
                       :border "1px solid gray"
                       :border-radius "10px"}}]]]]])

(defn container []
  (case (:component @app-state)
    login          [login (:username @app-state)]
    sentence-chain [sentence-chain
                    (:users @app-state)
                    (:sentence @app-state)
                    (can-change-sentence? @app-state)]))

(rd/render [container]
           (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
