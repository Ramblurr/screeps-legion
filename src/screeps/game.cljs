(ns screeps.game
  (:require
    [cognitect.transit :as t])
  (:require-macros [cljs.core :refer [exists?]])
  (:refer-clojure :exclude [time])
  (:use [screeps.utils :only [jsx->clj]]))

(defn time
  "Return the game time"
  []
  (.-time js/Game))

(defn creeps
  ([]
   (-> (.-creeps js/Game)
       jsx->clj
       vals))
  ([n]
   (aget (.-creeps js/Game) n)))

(defn spawns
  ([]
   (-> (.-spawns js/Game)
       jsx->clj
       vals))
  ([n]
   (aget (.-spawns js/Game) n)))

(defn rooms
  ([]
   (-> (.-rooms js/Game)
       jsx->clj
       vals))
  ([n]
   (aget (.-rooms js/Game) n)))

(defn object
  [id]
  (.getObjectById js/Game id))

(defn cpu
  []
  (.-cpu js/Game))

(defn cpu-used
  []
  (.getUsed (.-cpu js/Game)))

(defn cpu-limit
  []
  (.-limit (.-cpu js/Game)))

(defn shard
  []
  (.-shard js/Game))

(defn shard-name
  []
  (if (exists? (.-shard js/Game))
    (aget js/Game "shard" "name")
    nil))


(defn cpu-bucket
  []
  (.-bucket (cpu)))
