(ns ice-breaker-sentence-chain.client.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.dom :as rd]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :as async :include-macros true]
            [ice-breaker-sentence-chain.shared.event-handler
             :refer [event effect]
             :refer-macros [defevent defeffect defemit]]
            [ice-breaker-sentence-chain.client.server :refer [connect!]]))

(enable-console-print!)

(def initial-state {:status "offline"
                    :component 'login
                    :sentence ""})
(defonce app-state (atom initial-state))

(defn can-change-sentence? [app-state]
  (and
   (> (count (:users app-state)) 1)
   (= (first (:users app-state))
      (:username app-state))))


;;; Events

(defemit app-state)

(defevent :login [app-state username]
  {:app-state
   (merge app-state
          {:username  username
           :users     [username]
           :component 'sentence-chain})
   :effect
   [:server/emit :login username]})

(defevent :change-contribution [app-state contribution]
  {:app-state
   (assoc app-state :contribution contribution)})

(defevent :submit-contribution [app-state contribution]
  {:app-state
   (assoc app-state :contribution "")
   :effect
   [:server/emit :contribution contribution]})

(defevent :sync [app-state server-state]
  {:app-state (merge app-state server-state)})


;;; Server

(defonce incoming-messages (async/chan))
(defonce outgoing-messages (async/chan))
(defeffect :server/emit [& message] (async/put! outgoing-messages message))
(connect! "ws://localhost:3449/ws" outgoing-messages incoming-messages)
(async/go-loop []
  (apply emit (async/<! incoming-messages))
  (recur))


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

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
