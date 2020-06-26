(ns os.cli
  (:require [os.kernel :refer [queue-cmd]]))

;; from screeps console execute commands

;; os.cli.startproc()
;; starts a process
(defn startproc [] (queue-cmd :start-process))

;; os.cli.factoryreset()
;; WARNING
;; destroys all memory
(defn factoryreset [] (queue-cmd :factory-reset))
