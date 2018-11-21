(ns app.core
  (:require
   [fulcro.client :as fc]
   [fulcro.client.primitives :as prim :refer [defsc]]))

(defn init ^:export
  []
  (js/console.log "huy"))
