(ns main
  (:require
   ["./screeps/async" :as asyncjs]
   [screeps.memory :as memory]
   [screeps.game :as game]
   [screeps.constants :refer [env-screeps? env-nodejs?]]
   [os.kernel :as kernel]
   [os.process :as process]
   [os.cli :as cli]
   [logging :as log]

   ["./screeps/globals" :default global-setup]

   [cljs.core.async :as async :refer [<! >! timeout chan alt!] ])
  (:require-macros
   [cljs.core.async.macros :refer [go]]
   [util.perf :refer [perf]]))

(defn initial-kmem []
  (second (kernel/start-process kernel/empty-kmem 0 ::process/test-starter {})))

(defn ^:export tick []
  (println "================ tick" (game/time) "================")
  (perf "load" (memory/load-memory))
  (->> (memory/remember [:kmem] (initial-kmem))
       (kernel/main)
       (memory/remember! [:kmem]))
  (perf "write" (memory/write-memory!))
  ;; (memory/print-memory)
  (println "End Tick: CPU USED(" (.toFixed  (game/cpu-used) 5) ")"))

(if (env-screeps?)
  (do
    (log/install!)
    (log/set-levels {:logging/root :error})
    (defn ^:export main-loop []
      (tick)
      (.runTimeout js/global))))

(defn go-to-sleep []
  (println "sleeping")
  (memory/go-to-sleep!))
