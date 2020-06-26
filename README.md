# LegionOS

> LegionOS is a screeps operating system written in [Clojurescript][0]. 

Clojure is a dynamic and functional lisp dialect that uses immutable persistent
data structures to simplify reasoning about program state. While Clojure runs on
the JVM, Clojurescript is clojure compiled to javascript.


[0]: https://en.wikipedia.org/wiki/Clojure


## Kernel Memory

The LegionOS is an immutable operating system. Mutation of data does not occur
during execution. Rather the state of the kernel flows through the functions
from beginning to end. Every function is nearly a pure function too, with the
exception of those that call out to game state (currently limited to `Game.time`
and `Game.cpu.*`).

Here is an example snapshot of the kernel memory:

```edn
{
::k/pidc [-4500 -1000]
::k/cpu-limit 4
::k/executed [1 2]
::k/current-pid nil
::k/procs
{1
{   ::k/pid 1
    ::k/ppid 0
    ::k/queue-idx 2
    ::k/cpu-used 2
    ::k/type ::process/test-starter
    ::k/status ::k/running
    ::k/tick-started 100}
2
{   ::k/pid 2
    ::k/ppid 1
    ::k/queue-idx 2
    ::k/cpu-used 2
    ::k/type ::process/test-incrementer
    ::k/status ::k/running
    ::k/tick-started 101}}
::k/user-mem
{ 1 {:child 2 :child-is ::k/running}
  2 {:counter 2}}}
```

## Kernel Scheduling

This kernel uses a [multilevel feedback queue][1] to schedule proceses for
execution during game ticks.

Unlike a normal multilevel feedback queue scheduler, the Screeps OS relys on
cooperative yielding of the CPU rather than a preemptive model

The queueing criteria are still under development, but in short the idea is to
give preference to short running proceses. Note: short here referes to the
amount of cpu used during one time slice execution, and *not* to the lifetime of
the process.

If a process sleeps, then the scheduler will promote it to a higher queue after
the sleep period elapses *(not yet implemented)*

[1]: https://en.wikipedia.org/wiki/Multilevel_feedback_queue

## System calls

Since LegionOS is an immutable non-prempt kernel, proceses cannot make system
calls and get a result immediately. Instead after an execution slice, a process
can return a vector of sys calls that it would like performed.

A sys call is represented as, surprise, data! It is a vector whether the initial
element is the unique keyworf identifiying the syscall, and the remainin
gelements are the sys call arguments.

All syscall keywords are qualified to the `os.syscall` namespace.

Sys calls are executed in the order received from the process.

### Example: start a process

```edn
;; [:os.syscall/start-process :child-pid process-type initial-mem]

[:os.syscall/start-process (os.shared/generate-pid (game/time) :my-proc-type {:a-value 0})]
```

### Example: kill a process


``` edn
;; [:kernel/kill-process pid]

[:kernel/kill-process "P1234"]
```

## Processes

Processes are functions that accept their state (local memory) as an input, and
return their new state as an output. They can also return a vector of system
calls the kernel should execute (see System calls section).

### Example: the incrementer

Here is a process that is initialzied with a counter value (default 0) and a
limit value (default 5). Every tick it increments the value until a limit is
reached and then exits.

``` clojure
(defn proc-incrementer [pmem]
  (let [current-counter (:counter pmem 0)]
    (if (>= current-counter (:limit pmem 5)
        (process/exit pmem)
        (process/return (assoc pmem :counter (inc (:counter pmem)))))))
```

### Example: the starter

The starter is a process that every tick:

* if its child process is not running, then starts a child process
* else if the child process is running, reports on the state of the child

``` clojure
(defn- report-child [pmem]
  (let [child (get-in pmem [:run :children (:child pmem)])
        child-status (::k/status child)]
    (process/return (-> pmem
                        (assoc :child-is child-status)))))

(defn- start-child [pmem]
  (let [child-pid (generate-pid (game/time)) ]
    (process/return
     (assoc pmem :child child-pid)
     (process/start-process child-pid ::process/test-incrementer {:counter 0}))))

(defn- child-alive? [pmem]
  (some? (get-in pmem [:run :children (:child pmem)])))

(defn proc-starter [pmem]
  (if (child-alive? pmem)
    (report-child pmem)
    (start-child pmem)))
```

See the tests for more examples.

## Threading / coroutines

**TODO**

The goal of multithreading in a screeps os is:

1. Split a large computation over multiple ticks
2. Enable canceling of running proceses

## Credit

* Lots of inspiration from @ags131
* Thanks to cmcfarlen and anisoptera for the original [screeps-cljs](https://github.com/anisoptera/screeps-cljs) bindings

