(ns main-nodejs
  (:require
   ["./screeps/async" :as asyncjs]
   [screeps.memory :as memory]
   [screeps.game :as game]
   [screeps.constants :refer [env-nodejs?]]
   [os.kernel :as kernel]
   [os.process :as process]
   [logging :as log]
   [main :as main]

   ["./screeps/globals" :default global-setup]

   [cljs.core.async :as async :refer [<! >! timeout chan alt!] ]
   [node-polyfill :as node]))

(if (env-nodejs?)
  (do
    (defn sim-bootstrap! []
      (log/install!)
      (log/set-levels {:logging/root :trace})
      (global-setup js/global)
      (node/init-game!)
      (memory/reset-memory! {:kmem (second (kernel/start-process kernel/empty-kmem 0 ::process/test-starter {}))} )
      (memory/print-memory))

    (defn sim-tick []
      (main/tick)
      (memory/print-memory))

    (defn ^:export main-test []
      (sim-bootstrap!)
      (.tick asyncjs sim-tick)
      )))
