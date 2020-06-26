(ns screeps.constants)

(defn env-screeps? []
  (= true (.-IS_SCREEPS js/global)))

(def env-nodejs? (complement env-screeps?))

(defn env []
  (if (env-screeps?)
    :screeps
    :nodejs))
