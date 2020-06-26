(ns os.kernel-test
  (:require [cljs.test :refer-macros [deftest is testing use-fixtures]]
            [cljs.spec.alpha :as s]
            [medley.core :refer [dissoc-in]]

            [node-polyfill :as node :refer [reset-tick! set-cpu-used! add-cpu-used! incr-tick!]]
            [screeps.game :as game]
            [os.kernel :as kernel :refer [empty-queues empty-kmem]]
            [os.process :as process]
            [util.debug :as debug :refer [xxx]]
            [os.shared :as shared]
            [os.pid :as pidc]
            [os.specs :as k]))


;[oops.core :refer [oget oset! ocall oapply ocall! oapply!
;                   oget+ oset!+ ocall+ oapply+ ocall!+ oapply!+]]

(node/init-game!)


;;;;;;;;;;;;;;;;;;;;;;;;
(defn pid-gen-inc [_] (swap! node/uid-seq inc))

(deftest kmem-spec-valid
  (is (s/valid? ::k/kmem empty-kmem)))

(deftest indices
  (is (= '(0 2 4 6) (kernel/indices even? [0 1 2 3 4 5 6]))))

(deftest process-table
  (is (= {1 {:foo :bar}}
         (::k/procs (kernel/add-process empty-kmem 1 {:foo :bar})))))

(comment (deftest kernel-queues
           (is (= [#queue []
                   #queue [1]
                   #queue []] (kernel/demote-pid- [#queue [1] #queue [] #queue []] 1)))
           (is (= [#queue [2 3]
                   #queue [4 5 1]
                   #queue []] (kernel/demote-pid- [#queue [1 2 3] #queue [4 5] #queue []] 1)))
           (is (= [#queue []
                   #queue []
                   #queue [1]] (kernel/demote-pid- [#queue [] #queue [] #queue [1]] 1)))))

(def simple-procs {"P123" {::k/pid "P123" ::k/queue-idx 0}
                   "P124" {::k/pid "P124" ::k/queue-idx 1}
                   "P125" {::k/pid "P125" ::k/queue-idx 0}})
(def proc1 {::k/pid          1
            ::k/ppid         0
            ::k/queue-idx    0
            ::k/cpu-used     nil
            ::k/type         ::process/test-starter
            ::k/status       ::k/running
            ::k/tick-started (game/time)})

(def kmem-with-proc1
  (-> empty-kmem
      (assoc  ::k/procs {1 proc1})
      (assoc  ::k/queues {0 #queue [1] 1 #queue [] 2 #queue [] })))

(defn each-fixture [f]
  (reset-tick! nil 100)
  (f))

(use-fixtures :each each-fixture)

(deftest kernel-syscalls
  (with-redefs [shared/generate-pid pid-gen-inc]
    ;; new-process should generate a well formed process map
    (is (= proc1
           (kernel/new-process 0 ::process/test-starter 1)))

    ;; kill-process should do nothing when the kmem is empty
    (is (= empty-kmem (kernel/kill-process empty-kmem 0)))

    ;; kill process changes status and sets tick-stopped
    (is (= (-> kmem-with-proc1
               (assoc-in [::k/procs 1 ::k/status] ::k/killed)
               (assoc-in [::k/procs 1 ::k/tick-stopped] 100))
           (kernel/kill-process kmem-with-proc1 1)))

    ;; start process adds process to procs table and initializes memory
    ;; it also generates a pid and returns it
    (is (= [1 (assoc kmem-with-proc1 ::k/user-mem {1 {:foo :bar}})]
           (kernel/start-process empty-kmem 0 ::process/test-starter {:foo :bar})))

    ;; attempting to start an unknown process should do nothing
    (is (= empty-kmem
           (kernel/start-process empty-kmem 0 ::process/does-not-exist {:foo :bar})))))




(with-redefs [shared/generate-pid pid-gen-inc]
  (def kmem-base (let [[pid kmem] (kernel/start-process empty-kmem 0 ::process/test-starter {})]
                   kmem)))

(def kmem-before-run
  {::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 0
     ::k/cpu-used nil
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    }
   ::k/user-mem
   {1 {} }
   ::k/queues {0 #queue [1] 1 #queue [] 2 #queue [] }
   })

(def kmem-partial-after-one-run
  {::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 0
     ::k/cpu-used nil
     ::k/type ::process/test-incrementer
     ::k/status ::k/running
     ::k/tick-started 100}}
   ::k/user-mem
   {1 {:child 2 } 2 {:counter 0}}
   })

(def kmem-partial-after-two-runs
  {::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-incrementer
     ::k/status ::k/running
     ::k/tick-started 100}}
   ::k/user-mem
   {1 {:child 2} 2 {:counter 1}}
   })

(def kmem-partial-after-three-runs
  {::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-incrementer
     ::k/status ::k/running
     ::k/tick-started 100}}
   ::k/user-mem
   {1 {:child 2 :child-is ::k/running} 2 {:counter 1}}
   })

(def kmem-partial-after-killed
  {::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-starter
     ::k/status ::k/killed
     ::k/tick-started 100
     ::k/tick-stopped 101
     }
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 0
     ::k/cpu-used 0
     ::k/type ::process/test-incrementer
     ::k/status ::k/killed
     ::k/tick-started 100
     ::k/tick-stopped 101
     }}
   ::k/user-mem
   {1 {:child 2 :child-is ::k/running} 2 {:counter 1}}
   })


  (deftest running-process-once
    (with-redefs [shared/generate-pid pid-gen-inc]
      (let [[pid kmem-before]  (kernel/start-process empty-kmem 0 ::process/test-starter {})]
        (is (= kmem-before-run kmem-before))
        (is (= kmem-partial-after-one-run
               (select-keys (kernel/run-process kmem-before pid)
                            [::k/user-mem ::k/procs]))))))

  (deftest running-process-twice
    (with-redefs [shared/generate-pid pid-gen-inc]
      (let [[pid kmem-before]  (kernel/start-process empty-kmem 0 ::process/test-starter {})]
        (is (= kmem-partial-after-two-runs
               (-> kmem-before
                   (kernel/run-process pid)
                   (kernel/run-process 2)
                   (select-keys [::k/user-mem ::k/procs])
                   ))))))

  (deftest running-process-thrice
    (with-redefs [shared/generate-pid pid-gen-inc]
      (let [[pid kmem-before]  (kernel/start-process empty-kmem 0 ::process/test-starter {})]
        (is (= kmem-partial-after-three-runs
               (-> kmem-before
                   (kernel/run-process pid)
                   (kernel/run-process 2)
                   (kernel/run-process pid)
                   (select-keys [::k/user-mem ::k/procs])
                   ))))))

  (deftest running-process-killed
    (with-redefs [shared/generate-pid pid-gen-inc]
      (let [[pid kmem-before]  (kernel/start-process empty-kmem 0 ::process/test-starter {})]
        (is (= kmem-partial-after-killed
               (-> (kernel/setup kmem-before)
                   (kernel/run-process pid)
                   (kernel/run-process 2)
                   (kernel/run-process pid)
                   (incr-tick!)
                   (kernel/kill-process pid)
                   (select-keys [::k/user-mem ::k/procs])
                   ))))))

  (deftest running-process-garbage-collect
    (with-redefs [shared/generate-pid pid-gen-inc]
      (let [[pid kmem-before]  (kernel/start-process empty-kmem 0 ::process/test-starter {})
            kmem-after-cleanup (-> kmem-partial-after-killed
                                   (assoc-in [::k/user-mem] {})
                                   (assoc-in [::k/procs] {}))]
        (is (= kmem-after-cleanup
               (-> (kernel/setup kmem-before)
                   (kernel/run-process pid)
                   (kernel/run-process 2)
                   (kernel/run-process pid)
                   (incr-tick!)
                   (kernel/kill-process pid)
                   (incr-tick!)
                   (kernel/cleanup)
                   (select-keys [::k/user-mem ::k/procs])
                   ))))))

(def kmem-runned
  (-> kmem-partial-after-one-run
      (assoc ::k/executed [1 2])
      (assoc ::k/current-pid 2)
      (assoc ::k/queues '([1 2]))
      (assoc-in [::k/procs 2 ::k/tick-stopped] 101)
      (assoc-in [::k/procs 1 ::k/tick-stopped] 101)))

(def kmem-after-one-tick
  {
   ::k/pidc [-4500 -1000]
   ::k/cpu-limit 4
   ::k/executed [1 2]
   ::k/current-pid nil
   ::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 1
     ::k/cpu-used 2
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 1
     ::k/cpu-used 2
     ::k/type ::process/test-incrementer
     ::k/status ::k/running
     ::k/tick-started 101}}
   ::k/user-mem
   {1 {:child 2 } 2 {:counter 1}}})

(def kmem-after-two-ticks
  {
   ::k/pidc [-4500 -1000]
   ::k/cpu-limit 4
   ::k/executed [1 2]
   ::k/current-pid nil
   ::k/procs
   {1
    {::k/pid 1
     ::k/ppid 0
     ::k/queue-idx 2
     ::k/cpu-used 2
     ::k/type ::process/test-starter
     ::k/status ::k/running
     ::k/tick-started 100}
    2
    {::k/pid 2
     ::k/ppid 1
     ::k/queue-idx 2
     ::k/cpu-used 2
     ::k/type ::process/test-incrementer
     ::k/status ::k/running
     ::k/tick-started 101}}
   ::k/user-mem
   {1 {:child 2 :child-is ::k/running} 2 {:counter 2}}})

(def kmem-after-three-ticks
  (assoc-in kmem-after-two-ticks [::k/user-mem 2 :counter] 3))

(def kmem-after-six-ticks
  (-> kmem-after-three-ticks
      (dissoc-in [::k/procs 2])
      (dissoc-in [::k/user-mem 2])))

(def test-pidc-params {:Kp 0.02 :Ki 0.1 :Kd 0 :Mi 1000 :Se 0.5 :bucket-ceil 9500})
(def kernel-exec (fn [func pmem]
                              (let [result  (func pmem)]
                                (add-cpu-used! 2)
                                result)))
(defn init-test-run-kmem []
  (second (kernel/start-process empty-kmem 0 ::process/test-starter {})))
(def test-run-kmem-start (init-test-run-kmem))
(def test-pid-one 1)
(defn tick-fn [kmem]
  (incr-tick!)
  (kernel/main kmem))

(defn tick-times [n kmem]
  (nth (iterate tick-fn kmem) n))

(deftest test-build-queues
  (is (= {0 #queue [] 1 #queue [ ] 2 #queue [1 2]}
         (::k/queues (kernel/build-queues kmem-after-two-ticks)))))

(deftest run-one-tick
  (with-redefs [shared/generate-pid pid-gen-inc
                pidc/params test-pidc-params
                kernel/exec kernel-exec]
    (is (= kmem-after-one-tick (tick-times 1 (init-test-run-kmem))))))

(deftest run-two-ticks
  (with-redefs [shared/generate-pid pid-gen-inc
                pidc/params test-pidc-params
                kernel/exec kernel-exec]
    (is (= kmem-after-two-ticks (tick-times 2 (init-test-run-kmem))))))

(deftest run-three-ticks
  (with-redefs [shared/generate-pid pid-gen-inc
                pidc/params test-pidc-params
                kernel/exec kernel-exec]
    (is (= kmem-after-three-ticks (tick-times 3 (init-test-run-kmem))))))

(deftest run-six-ticks-then-kill
  (with-redefs [shared/generate-pid pid-gen-inc
                pidc/params  test-pidc-params
                kernel/exec kernel-exec]
    (is (= kmem-after-six-ticks (tick-times 6 (init-test-run-kmem))))))
