(ns util.debug
  (:require [clojure.string :as string]))

(defn xxx
  ([v] (prn "XXX: " v v) v)
  ([msg v] (prn (str "XXX(" msg "):") v) v)
  ([x msg v] (prn (str (string/join "" (take 3 (iterate identity x))) "(" msg "):") v) v))
