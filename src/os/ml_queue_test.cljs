(ns os.ml-queue-test
  (:require [os.ml-queue :as q]
            [cljs.test :as t :refer-macros [deftest is testing use-fixtures]]))

(def empty-queue (sorted-map 0 #queue [] 1 #queue [] 2 #queue []))
(def test-queue (sorted-map 0 #queue [1 2] 1 #queue [3] 2 #queue []))
(def test-queue2 (sorted-map 0 #queue [2] 1 #queue [3] 2 #queue []))
(def test-queue3 (sorted-map 0 #queue [2 10] 1 #queue [3] 2 #queue []))
(def test-queue4 (sorted-map 0 #queue [2 10] 1 #queue [3 11] 2 #queue []))
(def test-queue5 (sorted-map 0 #queue [2 10] 1 #queue [3 11] 2 #queue [12]))

(deftest test-make
  (is (= empty-queue (q/mlq 3))))

(deftest test-peek
  (is (= 1 (q/peek test-queue)))
  (is (= nil (q/peek empty-queue))))

(deftest test-pop
  (is (= test-queue2 (q/pop test-queue)))
  (is (= empty-queue (q/pop empty-queue))))

(deftest test-priorty
  (is (=  0 (q/priority test-queue))))

(deftest test-pop-with-priorty
  (is (=  [test-queue2 0] (q/pop-with-priority test-queue)))
  (is (=  [empty-queue nil] (q/pop-with-priority empty-queue))))

(deftest test-conj
  (is (= test-queue3 (q/conj test-queue2 0 10)))
  (is (= test-queue4 (q/conj test-queue3 1 11)))
  (is (= test-queue5 (q/conj test-queue4 2 12))))

(deftest test-priorities
  (is (= 3 (q/priorities test-queue))))

(deftest test-count
  (is (= 3 (q/count test-queue)))
  (is (= 5 (q/count test-queue5))))
