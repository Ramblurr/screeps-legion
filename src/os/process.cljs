(ns os.process
  (:require [os.syscall :as syscall]))

;; This ns contains the user-land apis

(def process-types
  [
   ::test-starter
   ::test-incrementer
   ])


(defn return
  ([pmem]
   [pmem []])
  ([pmem & scs]
   [pmem scs]))


(defn start-process [cpid proc-type initial-mem]
  [::syscall/start-process cpid proc-type initial-mem])

(defn kill-process [pid]
  [::syscall/kill-process pid])


(defn child-info [pmem]
  (get-in pmem [:run :children] []))

(defn exit [pmem]
  (return pmem (kill-process ::syscall/self)))
