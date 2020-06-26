(ns os.specs
  (:require
   [clojure.spec.alpha :as s]))

(defn queue? [x]
  (instance? cljs.core/PersistentQueue x))

(def uid-regex #"P[a-zA-Z0-9]+")

(s/def ::maybe-int (s/or :int int? :nil nil?))
(s/def ::game-time int?)
(s/def ::uid (s/and string? #(re-matches uid-regex %)))
(s/def ::pid (s/or :uid ::uid :int int?))                ; int used for tests
;(s/def ::pid (s/and int? #(>= % 0) #(<= % MAX-PID)))
;(s/def ::last-pid ::pid)
(s/def ::current-pid (s/or :pid ::pid :nil nil?))
(s/def ::pids (s/coll-of ::pid :kind vector? :distinct true :into []))
(s/def ::executed ::pids)
(s/def ::cpu-val ::maybe-int)
(s/def ::cpu-used ::cpu-val)
(s/def ::cpu-limit ::cpu-val)
(s/def ::queue-idx int?)
(s/def ::type keyword?)
(s/def ::status #{::running ::killed ::crashed})
(s/def ::tick-started int?)
(s/def ::ppid ::pid)                                        ; parent process id
(s/def ::pinfo (s/keys :req [::pid ::ppid ::queue-idx ::type ::status ::tick-started] :opt [::tick-stopped ::cpu-used]))
(s/def ::procs (s/map-of ::pid ::pinfo))
(s/def ::user-mem (s/map-of ::pid any?))
(s/def ::queue (s/coll-of ::pid))
(s/def ::queues (s/map-of int? ::queue))
(s/def ::priority (s/and int? #(>= % 0)))
(s/def ::pidc (s/coll-of int? :kind vector? :into []))
(s/def ::kmem (s/keys :req [::procs ::queues ::user-mem] :opt [ ::current-pid ::executed ::cpu-limit ::pidc]))
(s/def ::kmem-setup (s/keys :req [::procs ::queues ::user-mem ::current-pid ::executed ::cpu-limit ::pidc] :opt [::queues]))
(s/def ::tmp map?)
(s/def ::children ::procs)
(s/def ::run (s/keys :req [::children]))
(s/def ::pmem (s/keys :req [::tmp ::run]))
