;; A Screeps kernel written in clojurescript
;; props to @ags131 for his advice and multi level queue impl (see ZeSwarm)
(ns os.kernel
  (:require
   [clojure.spec.alpha :as s]
   [screeps.game :as game]
   [screeps.memory :as memory]
   [logging :as log]
   [util.debug :as debug :refer [xxx]]
   [os.process-map :refer [process-lookup]]
   [os.syscall :as syscall]
   [os.process :as p]
   [os.shared :as shared]
   [os.specs :as k]
   [os.pid :as pid]
   [os.ml-queue :as q]
   [medley.core :refer [filter-vals map-vals]]
   [com.rpl.specter :as x]
   [com.rpl.specter :as x :refer-macros [select transform select-first]]
   [goog.math :as math])
  (:require-macros [util.perf :refer [perf-cpu perf]]))

;; ---- start util functions

(defn indices
  "Returns seq of indices matching pred in coll"
  [pred coll]
  (keep-indexed #(when (pred %2) %1) coll))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn not-in? [coll elm]
  (not (in? coll elm)))

(defn queue
  ([] #queue [])
  ([coll]
   (reduce conj #queue [] coll)))

;; ---- end util functions

(def QUEUE-COUNT 3)
(def TOP-QUEUE 0)
(def BOTTOM-QUEUE (dec QUEUE-COUNT))
(def PRIORITY-DEFAULT TOP-QUEUE)
(def PRIORITY-MAX BOTTOM-QUEUE)
(def PRIORITY-MIN TOP-QUEUE)

;; ---------------------

(def QUANTUMS (map #(* 0.05 %) (range QUEUE-COUNT)))

(def empty-queues (q/mlq QUEUE-COUNT))

(def empty-kmem {::k/procs    {}
                 ::k/user-mem {}
                 ::k/queues   empty-queues})

(defn pid-exists? [kmem pid]
  (contains? (::k/procs kmem) pid))

(s/fdef pid-exists?
  :args (s/cat :kmem ::k/kmem :pid ::k/pid)
  :ret boolean?)

(defn add-to-queue [queue v] (conj queue v))

(defn peek-next-pid
  [kmem]
  (q/peek (::k/queues kmem)))

(defn pop-next-pid
  [kmem]
  (let [[mlq priority] (q/pop-with-priority (::k/queues kmem))]
    [(assoc kmem ::k/queues mlq) priority]))

(s/fdef pop-next-pid
  :args (s/cat :kmem ::k/kmem-setup)
  :ret (s/tuple ::k/kmem-setup ::k/priority))

(defn queue-at
  [queue-idx queues pid]
  (q/conj queues queue-idx pid))

(defn priority-for-pid [kmem pid]
  (get-in kmem [::k/procs pid ::k/queue-idx] PRIORITY-MIN))

(defn safe-priority [priority]
  (math/clamp priority
              PRIORITY-MIN
              PRIORITY-MAX))

(defn queue-pid
  "Queue the given pid in the appropriate priority queue. Returns kmem."
  ([kmem pid priority]
   (update-in kmem [::k/queues]
              #(queue-at (safe-priority priority) % pid)))
  ([kmem pid]
   (queue-pid kmem pid (priority-for-pid kmem pid))))

(defn add-process
  "Add pinfo with pid to the process table"
  [kmem pid pinfo]
  (assoc-in kmem [::k/procs pid] pinfo))

(defn update-process-cpu-used
  "Update the cpu used for a process"
  [kmem pid cpu-used]
  (assoc-in kmem [::k/procs pid ::k/cpu-used] cpu-used))

(defn update-process-stopped
  "Mark a process as stopped"
  [kmem pid new-status time]
  (-> kmem
      (assoc-in [::k/procs pid ::k/status] new-status)
      (assoc-in [::k/procs pid ::k/tick-stopped] time)))

(s/fdef update-process-stopped
  :args (s/cat :kmem ::k/kmem :pid ::k/pid :new-status #{::k/killed ::k/crashed} :time ::k/game-time)
  :ret ::k/kmem)

(defn update-process-user-mem
  "Update the user memory for a process"
  [kmem pid user-mem]
  (assoc-in kmem [::k/user-mem pid] user-mem))

(defn new-process
  ([ppid process-type pid]
   {::k/pid          pid
    ::k/ppid         ppid
    ::k/queue-idx    TOP-QUEUE
    ::k/cpu-used     nil
    ::k/type         process-type
    ::k/status       ::k/running
    ::k/tick-started (game/time)})
  ([ppid process-type]
   (new-process ppid process-type (shared/generate-pid (game/time)))))

(s/fdef new-process
  :args (s/cat :ppid (s/or :foo ::k/pid :bar nil?) :process-type ::k/type :pid (s/? ::k/pid))
  :ret ::k/pinfo)

(defn get-pinfo [kmem pid]
  (get-in kmem [::k/procs pid]))

(defn get-process-type [kmem pid]
  (::k/type (get-pinfo kmem pid)))

(defn get-process-mem [kmem pid]
  (get-in kmem [::k/user-mem pid]))

(defn current-process [kmem] (::k/current-pid kmem "ROOT"))

(defn start-process
  "Start a process of proc-type with the memory initialized to mem-init."
  ([kmem pid ppid proc-type mem-init]
   (if-let [process-function (process-lookup proc-type)]
     (let [pinfo (new-process ppid proc-type pid)
           pid (::k/pid pinfo)
           kmem (-> kmem
                    (add-process pid pinfo)
                    (update-process-user-mem pid mem-init)
                    (queue-pid pid))]
       [pid kmem])
     (do
       (log/error "start-process: unknown proc-type" proc-type)
       kmem)))
  ([kmem ppid proc-type mem-init]
   (start-process kmem (shared/generate-pid (game/time)) ppid proc-type mem-init)))

(defn log-process-crash [pid e]
  ;; TODO better crash reporting
  (log/error "process crashed" pid e))

(defn start-child-process
  "Start a child process
  Starting a process adds it to the process table and adds it to the top level queue. Returns kmem."
  ([kmem process-type initial-mem])
  ([kmem parent-pid child-pid process-type initial-mem]
   (let [[pid kmem] (start-process kmem child-pid parent-pid process-type initial-mem)]
     kmem)))

(defn child-pids
  "Returns a sequence of pids of all the children of ppid"
  [kmem ppid]
  (->> (::k/procs kmem)
       (filter (fn [[pid pinfo]] (= ppid (::k/ppid pinfo))))
       (map first)))

(defn child-processes
  "Returns a map of proceses that are children of ppid"
  [kmem ppid]
  (filter-vals #(= ppid (::k/ppid %)) (::k/procs kmem)))

(defn kill-children
  "Kills the children of process identified by `ppid`"
  [kmem ppid kill-fn]
  (perf "killchild" (reduce (fn [kmem pid] (kill-fn kmem pid)) kmem (child-pids kmem ppid))))

(defn kill-process
  "Kills the process identified by `pid-to-kill` and all child processes
  Killing a process merely marks it as `::k/killed` in the process table. Cleaning up and removing
  it from memory is handled elsewhere. Returns kmem"
  ([kmem pid-to-kill]
   (if (pid-exists? kmem pid-to-kill)
     (do
       (log/trace "killed process" pid-to-kill)
       (-> kmem
           (update-process-stopped pid-to-kill ::k/killed (game/time))
           (kill-children pid-to-kill kill-process)))
     (do (log/error :kernel "kill-process: no such pid" pid-to-kill) kmem)))
  ([kmem calling-pid pid-to-kill]
   (perf "kill-proc" (if (= ::syscall/self pid-to-kill)
                       (do
                         (kill-process kmem calling-pid))
                       (kill-process kmem pid-to-kill)))))

(def sys-call-handlers {::syscall/start-process start-child-process
                        ::syscall/kill-process  kill-process})

;;  sys-calls is a vector of sys-call vectors
;;  a sys-call is vector of one of the following:
;;   `[:kernel/start-process :child-pid process-type initial-mem]`
;;   `[:kernel/kill-process pid]`
;; syscalls are executed in order
(defn handle-sys-calls
  "Handles the requested system calls by a process.
  Returns kmem"
  [kmem pid sys-calls]
  (perf "syscalls" (if (empty? sys-calls)
                       ;; if sys-calls is empty, then we have nothing to do
                        kmem
                       ;; otherwise sequentially apply each sys call in order
                        (reduce (fn [kmem sys-callv]
                                  (let [sys-call-type (first sys-callv)
                                        sys-call-handler (sys-call-type sys-call-handlers)]
                                    (if (nil? sys-call-handler)
                                      (do (log/error :kernel "no such sys-call" sys-call-type "called by pid" pid)
                                          kmem)
                                      (apply sys-call-handler kmem pid (rest sys-callv)))))
                                kmem sys-calls))))

(defn post-run-pmem-clean [pmem]
  (-> pmem
      (dissoc :run)
      (dissoc :tmp)))

(defn post-run-process
  "Handles the result of running a process for a time slice.
  Returns kmem."
  [kmem pid [cpu-used [local-mem sys-calls]]]
  (log/info "PID used" cpu-used)
  (perf "post-run" (-> kmem
                       (update-process-cpu-used pid cpu-used)
                       (update-process-user-mem pid (post-run-pmem-clean local-mem))
                       (handle-sys-calls pid sys-calls))))

(defn enrich-process-mem [kmem pid pmem]
  (let [children (child-processes kmem pid)]
    (-> pmem
        (assoc-in [:run :children] children)
        (assoc-in [:tmp] {}))))

(s/fdef enrich-process-mem
  :args (s/cat :kmem ::k/kmem :pid  ::k/pid :pmem map?)
  :ret ::k/pmem)

(defn exec [func pmem]
  ;; this exec function provides a place to hook in during testing
  (func pmem))

(defn run-process
  "Returns kmem"
  [kmem pid]
  (let [proc-type (get-process-type kmem pid)
        pmem (enrich-process-mem kmem pid (get-process-mem kmem pid))
        process-function (process-lookup proc-type)]
    (if-not process-function
      (do
        (log/info "process function not found, killing func " {:type proc-type :pid pid})
        (kill-process kmem pid))
      (try
        (post-run-process kmem pid
                          (perf-cpu
                           (exec process-function pmem)))
        (catch js/Object e
          (log-process-crash pid e)
          kmem)))))

(defn add-to-executed [kmem pid]
  (update kmem ::k/executed conj pid))

(defn set-current-pid [kmem pid]
  (assoc kmem ::k/current-pid pid))

(defn run-pid [kmem pid]
  ;; (log/trace "+++RUNNING pid: " pid)
  (-> kmem
      (add-to-executed pid)
      (set-current-pid pid)
      (run-process pid)))

(s/fdef run-pid
  :args (s/cat :kmem ::k/kmem :pid ::k/pid)
  :ret ::k/kmem-setup)

(defn promote-pid [kmem pid]
  (update-in kmem [::k/procs pid ::k/queue-idx] dec))

(defn demote-pid [kmem pid]
  (update-in kmem [::k/procs pid ::k/queue-idx] inc))

(defn munge-queues
  "Promote or demote every executed process according to their CPU usage "
  [kmem]
  (perf "munge"
        (reduce (fn [kmem executed-pid]
                  (if-let [{:keys  [::k/cpu-used ::k/queue-idx]} (get-pinfo kmem executed-pid)]
                    (let [quantum (nth QUANTUMS queue-idx)]
                      (cond
                        (and (> queue-idx TOP-QUEUE) (< cpu-used (* 0.75 quantum)))
                        (promote-pid kmem executed-pid)

                        (and (< queue-idx BOTTOM-QUEUE) (> cpu-used quantum))
                        (demote-pid kmem executed-pid)
                        :else kmem))
                    kmem))
                kmem
                (::k/executed kmem))))

(defn build-queues [kmem]
  ; build the list of queues from the process table
  (perf "build-queues"
        (assoc kmem ::k/queues
               (->> (vals (::k/procs kmem))
                    (filter #(= ::k/running  (::k/status %)))
                    (group-by ::k/queue-idx)
                    (map-vals (fn [procs] (map #(::k/pid %) procs)))
                    (reduce-kv (fn [mqueue k pids]
                                 (reduce (fn [mqueue pid] (q/conj mqueue k pid)) mqueue  pids))
                               empty-queues)))))

(defn cpu-limit-reached? [kmem]
  ;; (log/info "cpu-limit" (game/cpu-used)  (::k/cpu-limit kmem))
  (> (game/cpu-used)  (::k/cpu-limit kmem)))

(defn calc-cpu-limit [kmem]
  (let [[e i] (get-in kmem [::k/pidc] [0 0])
        [cpu-limit e i] (pid/cpu (game/cpu-bucket) (game/cpu-limit) (game/cpu-used) e i)]
    (-> kmem
        (assoc-in [::k/pidc] [e i])
        (assoc-in [::k/cpu-limit] cpu-limit))))

(defn setup [kmem]
  (-> kmem
      (assoc ::k/executed [])
      (assoc ::k/current-pid nil)
      (calc-cpu-limit)
      (build-queues)))

(defn cleanup [kmem]
  (let [dead-procs (filter-vals #(= ::k/killed (::k/status %))
                                (get-in kmem [::k/procs]))
        dead-pids (keys dead-procs)]
    (-> kmem
        (update-in [::k/user-mem] #(apply dissoc % dead-pids))
        (update-in [::k/procs] #(apply dissoc % dead-pids))
        munge-queues
        (dissoc ::k/queues)
        (assoc ::k/current-pid nil))))

(defn continue? [kmem next-pid]
  (and
   (not (cpu-limit-reached? kmem))
   (some? next-pid)))

(defn run
  "Run all processes until we cannot run anymore. Optionally executes post-hook with the value of kmem after every process execution. Hook must return kmem."
  ([kmem post-hook]
   (let [next-pid (perf "peek-pid" (peek-next-pid kmem))
         [kmem priority] (perf "pop-pid" (pop-next-pid kmem))]
     (if (continue? kmem next-pid)
       (-> kmem
           (run-pid next-pid)
           post-hook
           (recur post-hook))
       ;; (recur (post-hook (run-pid kmem next-pid)) post-hook)
       (do
         ;; todo report total used
         (log/info "finished")
         kmem))))
  ([kmem]
   (run kmem identity)))

(def cmds (atom []))

(defn pop-cmd []
  (when-let [cmd (first @cmds)]
    (swap! cmds #(subvec % 1))
    cmd))

(defn exec-cmd [kmem cmd]
  (condp = cmd
    :start-process (second (start-process kmem 0 ::p/test-starter {}))
    :factory-reset empty-kmem
    kmem))

(defn exec-cmds- [kmem]
  (if-let [cmd (pop-cmd)]
    (recur (exec-cmd kmem cmd))
    kmem))

(defn exec-cmds [kmem]
  ;; perf macro b0rks the recur, so we use a helper func
  (perf "exec-cmds" (exec-cmds- kmem)))

(defn queue-cmd [cmd]
  (swap! cmds conj cmd))

(defn report [kmem]
  (println "LegionOS: processes(" (count (::k/procs kmem)) ") ")
  kmem)

(defn main
  [kmem]
  (perf "main" (->  kmem
                    (setup)
                    (run)
                    (exec-cmds)
                    (report)
                    (cleanup))))
