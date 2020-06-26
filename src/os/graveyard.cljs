(ns os.graveyard)
;;
;; Where code goes to die, but not dissapear in case it needs to be resurrected
;;
;;
(comment
  (defn pop-next-pid-to-run
    "Removes the next pid from its queue, returning the queues."
    [queues]
    (x/transform [(x/filterer not-empty) x/FIRST] pop queues))


;;; find the first element in the nested array that is not contained in the execed-pids
  ;;  and pop it
  (defn pop-next-pid-to-run2
    "Removes the next pid from its queue, returning the queues."
    [queues executed-pids]
    (let [pred  (fn [v] (not (xxx "subs?" (clojure.set/subset? (xxx "exepids" executed-pids) (xxx "val" v)))))
          ]
      (x/transform [
                    ;; (x/subselect (x/filterer not-empty) x/ALL pred)
                    (x/filterer not-empty) x/ALL pred
                    ]
                   (fn [v coll]
                     (println "DISJ" v coll)
                     :a
                     )
                   queues
                   ))

    (xxx "res" (pop-next-pid-to-run2 [(sorted-set 2 1) (sorted-set 3) (sorted-set 4)] (sorted-set 3))))
  ;; (defn pop-next-pid-to-run3
  ;;   "Removes the next pid from its queue, returning the queues."
  ;;   [queues executed-pids]
  ;;   (x/transform [(x/subselect (x/filterer not-empty) x/ALL x/ALL #(not-in? executed-pids %)) x/FIRST]
  ;;                pop
  ;;                ;; (fn [v]
  ;;                ;;   (xxx "DISJ" v)
  ;;                ;;   )
  ;;                queues))
  ;; (pop-next-pid-to-run3 [[2 1] [3] [4]] [2 1])
  ;;
  ;;

  ;; (defn vec-remove2
  ;;   "remove elem in coll"
  ;;   [coll elem]
  ;;   (println "REMOVE" elem coll)
  ;;   (filterv (complement #{elem}) coll))

  ;; (vec-remove2 :a [:a])
  ;;
  (defn demote-next-pid-to-run-
    "Demotes the next pid to the end of the next lower level queue."
    [queues]
    (let [next-pid (next-pid-to-run queues)
          ;; _ (prn (count queues))
          next-queue-idx (first (indices not-empty queues))
          next-next-queue-idx (min (inc next-queue-idx) (dec (count queues)))]
      (queue-at next-next-queue-idx (dequeue-pid queues next-pid) next-pid)))
  (def to-remove [ 4 5 6 ])
  (def coll [ [1 2 4 4] [3 5 8 ] [6] [] ])
  (x/setval [x/ALL x/ALL (fn [val]  (some #(= val %) to-remove)) x/FIRST] x/NONE coll)


  (defn positions
    [pred coll]
    (keep-indexed (fn [idx x]
                    (when (pred x)
                      idx))
                  coll))

  (defn dequeue-pid
    "Deqeueues the given pid from all the queues" [queues pid]
    ;;  a side effect of this is that it removes ALL instances of pid
    (x/setval [ x/ALL x/ALL #(= pid %) ] x/NONE queues))

  ;; (dequeue-pid [[2 1 ] [3 1] [4] []] 1)
  (defn queue-at
    "Adds a pid to the nth queue. Returns queues"
    [queue-idx queues pid]
    (x/transform [(x/nthpath queue-idx)] #(add-to-queue % pid) queues)))
