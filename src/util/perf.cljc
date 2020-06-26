(ns util.perf)

(defmacro perf-cpu
  "Evaluates expr and returns a tuple, the first value is the
  cpu time spent executing, and the second value is the ret value of expr."
  [expr]
  `(let [start# (screeps.game/cpu-used)
         ret# ~expr]
     [(- (screeps.game/cpu-used) start#) ret#]))

(defmacro perf
  "Evalutes expr, printing the amount of CPU used along with the tag as a label"
  [tag expr]
  `(let [start# (screeps.game/cpu-used)
         ret# ~expr
         used# (- (screeps.game/cpu-used) start#)
         ]
     (println "CPU(" ~tag ")"  (.toFixed used# 5))
     ret#))

(defmacro console-time
  [label & body]
  `(do
     (.time js/console ~label)
     (let [result# (do ~@body)]
       (.timeEnd js/console ~label)
       result#)))
