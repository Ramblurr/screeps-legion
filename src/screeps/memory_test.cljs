(ns screeps.memory-test
  (:require [cljs.test :as t :refer (use-fixtures deftest is async testing)]
            ["./globals" :default global-setup]
            [screeps.memory :as memory]
            ;; [oops.core :refer [oget oset! oset!+]]

            ))

(global-setup js/global)

(defn mock-global [name value]
  (goog.object.set js/global name value) )


(deftest memory-io
  (mock-global "RawMemory" {:a 1})
  (is (= {:a 1} js/RawMemory)))
