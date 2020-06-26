(ns node-polyfill)

(defn now [] (.getTime (js/Date.)))

(def cpu-used (atom 0))
(def uid-seq (atom 0))

(defn init-game! []
  (goog.object/set js/global "Game" #js {:time 100})
  (goog.object/set (.-Game js/global) "cpu" #js {})
  (goog.object/set (.-cpu  (.-Game js/global)) "getUsed" (fn [] @cpu-used))
  (goog.object/set (.-cpu  (.-Game js/global)) "bucket" 500)
  (goog.object/set (.-cpu  (.-Game js/global)) "limit" 20)
  (.-Game js/global))

(defn set-cpu-used! [val]
  (reset! cpu-used val))

(defn add-cpu-used! [val]
  (swap! cpu-used + val))

(defn reset-tick! [kmem time]
  (set! (-> js/global (.-Game) (.-time)) time)
  (set-cpu-used! 0)
  (reset! uid-seq 0)
  kmem)

(defn incr-tick!
  ([]
   (set-cpu-used! 0)
   (set!
    (-> js/global
        (.-Game)
        (.-time))
    (inc (-> js/global (.-Game) (.-time))))
   (println "TICK" (.-time js/Game)))
  ([kmem] (incr-tick!) kmem))
