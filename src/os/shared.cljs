(ns os.shared
  (:require
   [clojure.spec.alpha :as s]
   [os.specs :as k]))
;;
;; Functions shared across multiple kernel modules, pulled out to prevent circular deps
;;

(defn generate-pid
  "Generates a new pid for a process using the game-time as a seed of sorts
  source: @ags131 - ZeSwarm Kernel"
  [game-time]
  (clojure.string/upper-case (str "P"
                                  (.slice (.toString game-time 36) -6)
                                  (.slice (.toString (js/Math.random) 36) -3))))

(s/fdef pid-generating-fn
  :ret ::k/pid)
