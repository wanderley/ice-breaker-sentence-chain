(ns ice-breaker-sentence-chain.server.main
  (:require [org.httpkit.server :as hk]
            [ice-breaker-sentence-chain.server.handler :as h])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (let [port (if (empty? args) 8080 (or (Integer. (first args)) 8080))]
    (println (str "Starting server on http://localhost:" port))
    (hk/run-server h/app {:port port})))
