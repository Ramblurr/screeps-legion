(ns os.process.starter
  (:require [os.process :as process]
            [os.syscall :as syscall]
            [os.specs :as k]
            [os.shared :refer [generate-pid]]
            [logging :as log]

            [screeps.game :as game]
            [util.debug :refer [xxx]]

            ))

;; process: test-starter
;; purpose: a test process that starts a child process and prints its status every tick
(defn- report-child [pmem]
  (let [child (get-in pmem [:run :children (:child pmem)])
        child-status (::k/status child)]
    ;; (log/info "child has status" child-status)
    (process/return (-> pmem
                        (assoc :child-is child-status)))))

(defn- start-child [pmem]
  (let [child-pid (generate-pid (game/time)) ]
    (log/info "starting child" child-pid)
    (process/return
     (assoc pmem :child child-pid)
     (process/start-process child-pid ::process/test-incrementer {:counter 0}))))

(defn child-alive? [pmem]
  (let [child (get-in pmem [:run :children (:child pmem)]) ]
    (some? child)))

(defn proc-test-starter [pmem]
  (if (child-alive? pmem)
    (report-child pmem)
    (start-child pmem)))
