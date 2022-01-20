(ns ice-breaker-sentence-chain.handler
  (:require
   [compojure.core :refer :all]
   [compojure.route :as route]
   [ring.util.response :as resp]))

(defroutes app
  (GET "/" [] (resp/resource-response "index.html" {:root "public"}))
  (route/resources "/")
  (route/not-found "<h1>Page not found</h1>"))
