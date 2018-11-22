(ns app.core
  (:require
   [clojure.core.async :as a :refer [<! timeout chan]]
   [com.wsscode.pathom.core :as p]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [com.wsscode.pathom.fulcro.network :as pn]
   [devcards.core :as dc]
   [fulcro.client :as fc]
   [fulcro.client.cards :refer [defcard-fulcro]]
   [fulcro.client.dom :as dom]
   [fulcro.client.primitives :as prim :refer [defsc]]
   [fulcro.incubator.ui-state-machines :as uism :refer [defstatemachine]])
  (:require-macros
   [clojure.core.async :refer [go]]))

(defonce app (atom nil))

(defstatemachine foo-machine
  {::uism/actor-names #{:foo :root}
   ::uism/states
   {:initial {::uism/events {::uism/started {::uism/target-state :default}}}
    :default {::uism/events
              {:exit              {::uism/target-state ::uism/exit}
               :do-stuff          {::uism/handler (fn [env]
                                                    (-> env
                                                        (uism/trigger-remote-mutation
                                                         :root
                                                         `some-mutation
                                                         {::uism/mutation-remote :custom
                                                          ::uism/ok-event        :mutation-happened})))}
               :mutation-happened {::uism/handler (fn [env]
                                                    (js/console.log "mutation happened" env))}}}}})

(defsc Root
  [this _]
  {:ident                (fn [] [:root/by-id 0])
   :query                [:derp]
   :initial-state        {:derp 0}
   :componentDidMount    (fn []
                           (uism/begin! this foo-machine ::foo {:root (uism/with-actor-class
                                                                        [:root/by-id 0]
                                                                        Root)}))
   :componentWillUnmount (fn []
                           (uism/trigger! this ::foo :exit))}
  (dom/div
    (dom/button {:onClick (fn []
                            (uism/trigger! this ::foo :do-stuff))}
      "Derp")))

(pc/defmutation some-remote-mutation
  [env params]
  {::pc/sym    `some-mutation
   ::pc/params []}
  (go
    (js/console.log "remote-mutation before-timeout")
    (<! (timeout 1000))
    (js/console.log "remote-mutation after-timeout")
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

(defcard-fulcro derpes
  Root
  {}
  {:fulcro {:networking {:custom (new-custom-remote app)}}})

(defn init ^:export [] (dc/start-devcard-ui!))
