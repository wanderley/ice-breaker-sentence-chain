(ns ice-breaker-sentence-chain.shared.event-handler)

(defmulti event    (fn [type app-state & params] type))
(defmacro defevent [type args & body]
  `(defmethod ~'event ~type [~'_ ~@args] (do ~@body)))

(defmulti  effect (fn [type & params] type))
(defmethod effect :default [& _] nil)
(defmacro  defeffect [type args & body]
  `(defmethod ~'effect ~type [~'_ ~@args] (do ~@body)))

(defmacro defemit [app-state]
  `(defn ~'emit [type# & params#]
     (swap! ~app-state
            #(let [r# (apply event (concat [type# %] params#))]
               (if (:effect r#) (apply effect (:effect r#)))
               (:app-state r#)))))
