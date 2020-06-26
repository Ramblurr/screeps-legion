(ns os.process-map
  (:require [os.process :as process]
            [os.process.starter :refer [proc-test-starter]]
            [os.process.incrementer :refer [proc-test-incrementer]]))

(def PROCESSES {
                ::process/test-incrementer proc-test-incrementer
                ::process/test-starter proc-test-starter
                })

(defn process-lookup [proc-type]
  (proc-type PROCESSES))
