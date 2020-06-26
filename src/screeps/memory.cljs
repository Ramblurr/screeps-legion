(ns screeps.memory
  (:require
   ["./async" :as asyncjs]
   [screeps.game :as game]
   [cognitect.transit :as t]
   [medley.core :as m]
   [clojure.pprint :refer [pprint]]))

(def ^:dynamic *memory* (atom {}))

(defn read-memory
  "Reads and parses the RawMemory, returning the parsed value"
  []
  (let [r (t/reader :json)
        mem (.get js/RawMemory)]
    (if (empty? mem)
      {}
      (t/read r mem))))

(defn print-memory []
  (pprint (read-memory)))

(defn ^:export load-memory
  "Parses the memory and stores it in the local memory atom."
  []
  (reset! *memory* (read-memory)))

(defn ^:export write-memory!
  "Writes the current memory atom to RawMemory"
  []
  (let [w (t/writer :json)]
    (.set js/RawMemory (t/write w @*memory*))))

(defn reset-memory!
  "Resets the value of memory to be v"
  [v]
  (reset! *memory* v)
  (write-memory!))

(defn fetch
  ([]
   (deref *memory*))
  ([k]
   (fetch k nil))
  ([k default]
    (if-let [v (@*memory* k)]
      v
      default)))

(defn store!
  [k o]
  (swap! *memory* #(assoc % k o)))

(defn update!
  "call f with memory location k and store the result back in k"
  [k f & args]
  (let [d (fetch k)]
    (store! k (apply f d args))))

(defn remember!
  [ks v]
  (swap! *memory* #(assoc-in % ks v)))

(defn forget!
  [ks]
  (swap! *memory* #(m/dissoc-in % ks)))

(defn remember
  ([ks]
   (get-in @*memory* ks))
  ([ks default]
   (get-in @*memory* ks default)))

(defn go-to-sleep! []
  (reset! *memory* "")
  (write-memory!))
