(ns app.core
  (:require
   [clojure.core.async :as a :refer [<! timeout chan]]
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [com.wsscode.pathom.fulcro.network :as pn]
   [fulcro.client :as fc]
   [fulcro.client.dom :as dom]
   [fulcro.client.primitives :as prim :refer [defsc]])
  (:require-macros
   [clojure.core.async :refer [go]]))

(defonce app (atom nil))

(defsc Root
  [this _]
  {}
  (dom/div nil "hi"))

(pc/defmutation some-remote-mutation
  [env params]
  {::pc/sym    `some-mutation
   ::pc/params []}
  (go
    (<! (timeout 1000))
    nil))

(def registry
  [some-remote-mutation])

(defn rest-parser
  [app extra-env]
  (p/parallel-parser
   {::p/env     (merge extra-env
                       {:app-atom                  app
                        ::p/reader                 [p/map-reader
                                                    pc/all-parallel-readers]
                        ::pc/resolver-dispatch     pc/resolver-dispatch-embedded
                        ::pc/mutate-dispatch       pc/mutation-dispatch-embedded
                        ::pc/mutation-join-globals [::prim/tempids]
                        ::p/union-path             pn/fulcro-union-path})
    ::p/mutate  pc/mutate-async
    ::p/plugins [(pc/connect-plugin {::pc/register registry})
                 p/request-cache-plugin
                 (p/post-process-parser-plugin p/elide-not-found)]}))

(defn new-custom-remote
  [{:keys [app]}]
  (pn/pathom-remote
   (rest-parser app {})))

(defn started-callback
  [{:keys [reconciler] :as app}]
  (js/console.log "started!")
  )

(defn start []
  (swap! app
         (fn [application]
           (fc/mount application Root "app"))))

(defn init ^:export
  []
  (reset!
   app
   (fc/new-fulcro-client
    :networking {:custom (new-custom-remote app)}
    :started-callback started-callback))
  (start))
