(ns os.ml-queue
  (:require
   [medley.core :refer [find-first]]))

(defn mlq
  "Returns a multilevel queue with n levels."
  [n]
  (into (sorted-map) (zipmap
                      (take n (iterate inc 0))
                      (take n (iterate identity #queue [])))))

(defn peek
  "Returns the next item in the multilevel queue."
  [mqueue]
  (let [next-queue (find-first not-empty (vals mqueue))]
    (when (some? next-queue) (clojure.core/peek next-queue))))

(defn pop
  "Returns a new multilevel queue without the next item"
  [mqueue]
  (if-let [[priority queue] (find-first #(not-empty (second %)) mqueue)]
    (assoc mqueue priority (clojure.core/pop queue))
    mqueue))

(defn priority
  "Returns the priority queue of the next item"
  [mqueue]
  (first (find-first #(not-empty (second %)) mqueue)))

(defn pop-with-priority
  "Like pop, but returns a 2 element vector of [popped-queue priority-of-popped-item]"
  [mqueue]
  (if-let [[priority queue] (find-first #(not-empty (second %)) mqueue)]
    [(assoc mqueue priority (clojure.core/pop queue)) priority]
    [mqueue nil]))

(defn conj
  "Returns a new multilevel queue with val added at priority."
  [mqueue priority val]
  (let [queue (mqueue priority 0)]
    (assoc mqueue priority (clojure.core/conj queue val))))

(defn priorities
  "Returns the number priority queues in the multilevel queue"
  [mqueue]
  (clojure.core/count mqueue))

(defn count
  "Returns the number of items in the multilevel queue"
  [mqueue]
  (reduce-kv (fn [total _ queue] (+ total (clojure.core/count queue))) 0 mqueue))
