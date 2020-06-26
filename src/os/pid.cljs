(ns os.pid
  (:require
   [goog.math :as math]))

(def params {
             :Kp 0.02
             :Ki 0.1
             :Kd 0
             :Mi 1000
             :Se 0.5
             :bucket-ceil 9500
             })


(defn pid [Kp Ki Kd Mi e2 e1 i]
  (let [
        Up (* e2 Kp)
        Ui (* Ki i)
        Ud (* Kd (/ e2 e1 ) e2)
        out (+ Up Ui Ud)
        ]
    out))

(defn cpu [cpu-bucket cpu-limit cpu-used e i]
  ;; (println "CALCPUID" cpu-bucket cpu-limit cpu-used)

  (let [{:keys [Kp Ki Kd Mi Se bucket-ceil]} params
        limit-min (* 0.2 cpu-limit)
        e1 e
        e2 (* Se (- cpu-bucket bucket-ceil ))
        i2 (math/clamp  (+ i e2) (- Mi) Mi)

        v (pid Kp Ki Kd Mi e2 e1 i2)
        cpu-max    (max (- (+ cpu-limit v ) cpu-used))
        ]
    (if (or (js/isNaN cpu-max) (< cpu-max 0))
      [limit-min e2 i2]
      [cpu-max e2 i2])))

;; (cpu 200 20 0 0 0)
