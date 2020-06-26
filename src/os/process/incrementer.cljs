(ns os.process.incrementer
  (:require [os.process :as process]
            [os.syscall :as syscall]
            [logging :as log]
            [util.debug :refer [xxx]]
            ))

;; process: test-incrementer
;; purpose: a test process that is initialized with an integer, and every time it is run, increments the counter
(defn proc-test-incrementer [pmem]
  ;; (log/inspect :pmem pmem)
  (let [current-counter (:counter pmem)]
    (if (>= current-counter 5)
      (do (log/info "counter finished")
          (process/exit pmem))
      (do
        (log/info (:counter pmem) "-->" (inc (:counter pmem)))
        (process/return
         (assoc pmem :counter (inc (:counter pmem))))))))
