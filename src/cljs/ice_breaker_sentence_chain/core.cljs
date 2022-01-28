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

(defmulti  action  (fn [type app-state & params] type))
(defmulti  effect! (fn [type app-state & params] type))
(defmethod effect! :default [_ & _] nil)
(defn emit [type & params]
  (swap! app-state
         #(let [next-app-state (apply action (concat [type %] params))]
            (println type params next-app-state)
            (apply effect! (concat [type next-app-state] params))
            next-app-state)))

(declare connect! send-message!)
(defn server-emit! [& params] (send-message! params))

(defmethod action :login [_ app-state username]
  (merge app-state
         {:username  username
          :users     [username]
          :component 'sentence-chain}))

(defmethod effect! :login [_ app-state username]
  (server-emit! :login username))

(defmethod action :change-contribution [_ app-state contribution]
  (assoc app-state :contribution contribution))

(defmethod action :submit-contribution [_ app-state contribution]
  (assoc app-state :contribution ""))

(defmethod effect! :submit-contribution [_ app-state contribution]
  (server-emit! :contribution contribution))

(defmethod action :sync [_ app-state server-state]
  (merge app-state server-state))


;;; Websocket

(defonce outgoing-messages (async/chan))

(defn send-message! [message]
  (async/put! outgoing-messages message))

(defn send-messages! [ws-channel]
  (async/go-loop []
    (when-let [message (async/<! outgoing-messages)]
      (async/>! ws-channel message)
      (recur))))

(defn receive-messages! [ws-channel]
  (async/go-loop []
    (let [{:keys [message]} (async/<! ws-channel)]
      (apply emit message)
      (recur))))

(defn connect!
  "Connects with the server and starts the input and output channels."
  []
  (async/go
    (let [{:keys [ws-channel error]} (async/<! (ws-ch ws-url))]
      (if error
        (println "Connection failed with" (str error))
        (do
          (println "Connected!")
          (send-messages! ws-channel)
          (receive-messages! ws-channel))))))

;;; Views

(defn login [username]
  (let [username (atom (or username ""))]
    (fn []
      [:div {:style {:position "absolute"
                     :top "30%"
                     :width "100%"}}
       [:form {:on-submit (fn [_]
                            (.preventDefault _)
                            (emit :login @username))}
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

(defn sentence-chain [users
                      sentence
                      contribution
                      can-change-sentence?]
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
      (for [i (range (count users))
            :let [user (get users i)]]
        [:div (if (zero? i)
                [:strong user]
                user)]))
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
      [:form {:on-submit (fn [_]
                           (.preventDefault _)
                           (emit :submit-contribution contribution))}
       [:input {:type "text"
                :placeholder (if can-change-sentence?
                               "Your turn ..."
                               "Wait your turn ...")
                :value (or contribution "")
                :on-change #(emit :change-contribution (-> % .-target .-value))
                :disabled (not can-change-sentence?)
                :style {:width "100%"
                        :boxSizing "border-box"
                        :font-size "2rem"
                        :padding "0.5rem"
                        :border "1px solid gray"
                        :border-radius "10px"}}]]]]]])

(defn container []
  (case (:component @app-state)
    login          [login (:username @app-state)]
    sentence-chain [sentence-chain
                    (:users @app-state)
                    (:sentence @app-state)
                    (:contribution @app-state)
                    (can-change-sentence? @app-state)]))

(rd/render [container]
           (. js/document (getElementById "app")))
(connect!)

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
