(ns screeps.setup-test
    (:require
     [cljs.test :as test]
     [cljs.spec.alpha :as s]
     [cljs.spec.test.alpha :as ts]
     [expound.alpha :as expound]
     [cljs.repl :as repl]
     [pjstadig.humane-test-output]
     [logging :as log]
     ))

(comment [pjstadig.print :as humane-print]
         [pjstadig.util :as humane-util]
         [])

(log/install!)
(log/set-levels {:logging/root :trace})
(set! s/*explain-out* expound/printer)

(defmethod test/report [:cljs.test/default :error] [m]
  (test/inc-report-counter! :error)
  (println "\nERROR in" (test/testing-vars-str m))
  (when (seq (:testing-contexts (test/get-current-env)))
    (println (test/testing-contexts-str)))
  (when-let [message (:message m)] (println message))
  (let [actual (:actual m)
        ex-data (ex-data actual)]
    (if (:cljs.spec.alpha/failure ex-data)
      (do (println "expected:" (pr-str (:expected m)))
          (print "  actual:" (.-message actual))
          (println (repl/error->str actual)))
      (test/print-comparison m))
    (println (.-stack actual))))

(comment
  ;; :( https://github.com/pjstadig/humane-test-output/pull/26
  (def report #'humane-util/report-)

  (defmethod test/report [:cljs.test/default :fail] [m]
    (let [actual (:actual m)
          ex-data (ex-data actual)]
      (if (:cljs.spec.alpha/failure ex-data)
        (do (test/inc-report-counter! :fail)
            (when (seq (:testing-contexts (test/get-current-env)))
              (println "--" (:testing-contexts (test/get-current-env))))
            (when-let [message (:message m)] (println message))
            (println "expected:" (pr-str (:expected m)))
            (print "  actual:\n")
            (println (.-message actual)))
        ;; humane does it all
        (report (humane-print/convert-event m))))))


(s/check-asserts true)
(ts/instrument)

;; here is a spec test to test the nice errors
(comment
  (s/def ::foothing #(contains? % :foo))
  (defn get-foo [foothing]
    (:foo foothing))
  (s/fdef get-foo :args (s/cat :foothing ::foothing) :ret any?))
