(ns ice-breaker-sentence-chain.main
  (:require [org.httpkit.server :as hk]
            [ice-breaker-sentence-chain.handler :as h])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [& [port]]
  (hk/run-server h/app {:port (or (Integer. port) 8080)}))
