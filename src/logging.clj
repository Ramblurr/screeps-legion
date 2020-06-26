;; This module is based on;
;;     glogi
;;     https://github.com/lambdaisland/glogi
;;     Copyright © 2019-2020 Arne Brasseur
;; And is licensed under the Eclipse Public License - v 1.0

(ns logging
  (:require [clojure.string :as str]))

(defn- log-expr [form level keyvals]
  (let []
    `(log ~(str *ns*)
          ~level
          (str/join " " (map str [~@keyvals]))
          ~(:exception keyvals))))

(defn- log-expr-structured [form level keyvals]
  (let [keyvals-map (apply array-map keyvals)
        formatter (::formatter keyvals-map 'identity)]
    `(log ~(::logger keyvals-map (str *ns*))
          ~level
          (~formatter
           ~(-> keyvals-map
                (dissoc ::logger)
                (assoc :line (:line (meta form)))))
          ~(:exception keyvals-map))))


(defmacro shout [& keyvals]
  (log-expr &form :shout keyvals))

(defmacro error [& keyvals]
  (log-expr &form :error keyvals))

(defmacro severe [& keyvals]
  (log-expr &form :severe keyvals))

(defmacro warn [& keyvals]
  (log-expr &form :warn keyvals))

(defmacro info [& keyvals]
  (log-expr &form :info keyvals))

(defmacro debug [& keyvals]
  (log-expr &form :debug keyvals))

(defmacro config [& keyvals]
  (log-expr &form :config keyvals))

(defmacro trace [& keyvals]
  (log-expr &form :trace keyvals))

(defmacro fine [& keyvals]
  (log-expr &form :fine keyvals))

(defmacro finer [& keyvals]
  (log-expr &form :finer keyvals))

(defmacro finest [& keyvals]
  (log-expr &form :finest keyvals))

(defmacro spy [form]
  (let [res (gensym)]
    `(let [~res ~form]
       ~(log-expr &form :debug [:spy `'~form
                                :=> res])
       ~res)))

(defmacro inspect [& keyvals]
  (log-expr-structured &form :debug keyvals))
