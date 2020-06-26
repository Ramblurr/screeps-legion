;; This module is based on;
;;     glogi
;;     https://github.com/lambdaisland/glogi
;;     Copyright © 2019-2020 Arne Brasseur
;; And is licensed under the Eclipse Public License - v 1.0

(ns logging

  (:require
   [clojure.string :as str]
   [goog.array :as array]
   [goog.object :as gobj]
   [goog.log :as glog]
   [goog.debug.Logger :as Logger]
   [goog.debug.Logger.Level :as Level]
   [goog.debug.Console :as Console])
  (:import [goog.debug Logger Console LogRecord])
  (:require-macros [logging]))

(defn name-str [x]
  (cond
    (= :logging/root x)
    ""

    (string? x)
    x

    (simple-ident? x)
    (name x)

    (qualified-ident? x)
    (str (namespace x) "/" (name x))

    :else
    (str x)))

(defn logger
  "Get a logger by name, and optionally set its level. Name can be a string
  keyword, or symbol. The special keyword :logging/root returns the root logger."
  (^Logger [n]
   (glog/getLogger (name-str n)))
  (^Logger [n level]
   (glog/getLogger (name-str n) level)))

(def ^Logger root-logger (logger ""))

(def levels
  {:off     Level/OFF
   :shout   Level/SHOUT
   :severe  Level/SEVERE
   :warning Level/WARNING
   :info    Level/INFO
   :config  Level/CONFIG
   :fine    Level/FINE
   :finer   Level/FINER
   :finest  Level/FINEST
   :all     Level/ALL

   ;; pedestal style
   :trace Level/FINER
   :debug Level/FINE
   :warn  Level/WARNING
   :error Level/SEVERE})




(defn level ^Level [lvl]
  (get levels lvl))

(defn level-value
  "Get the numeric value of a log level (keyword)."
  [lvl]
  (.-value (level lvl)))

(defn make-log-record ^LogRecord [level message name exception]
  (let [record (LogRecord. level message name)]
    (when exception
      (.setException record exception))
    record))

(defn log
  "Output a log message to the given logger, optionally with an exception to be
  logged."
  ([name lvl message]
   (log name lvl message nil))
  ([name lvl message exception]
   (when glog/ENABLED
     (when-let [l (logger name)]
       (.logRecord l
                   (make-log-record (level lvl) message name exception))))))

(defn set-level
  "Set the level (a keyword) of the given logger, identified by name."
  [name lvl]
  (assert (contains? levels lvl))
  (some-> (logger name) (.setLevel (level lvl))))

(defn set-levels
  "Convenience function for setting several levels at one. Takes a map of logger name => level keyword."
  [lvls]
  (doseq [[logger level] lvls]
    (set-level logger level)))

(defn enable-console-logging!
  "Log to the browser console. This uses goog.debug.Console directly,
  use [lambdaisland.logging.console/install!] for a version that plays nicely with
  cljs-devtools."
  []
  (when-let [instance Console/instance]
    (.setCapturing instance true)
    (let [instance (Console.)]
      (set! Console/instance instance)
      (.setCapturing instance)))
  nil)


(defn add-handler
  "Add a log handler to the given logger, or to the root logger if no logger is
  specified. The handler is a function which receives a map as its argument."
  ([handler-fn]
   (add-handler "" handler-fn))
  ([name handler-fn]
   (some-> (logger name)
           (.addHandler
             (doto
               (fn [^LogRecord record]
                 (handler-fn {:sequenceNumber (.-sequenceNumber_ record)
                              :time (.-time_ record)
                              :level (keyword (str/lower-case (.-name (.-level_ record))))
                              :message (.-msg_ record)
                              :logger-name (.-loggerName_ record)
                              :exception (.-exception_ record)}))
               (gobj/set "handler-fn" handler-fn))))))

(defn remove-handler
  ([handler-fn]
   (remove-handler "" handler-fn))
  ([name handler-fn]
   (some-> (logger name) (.removeHandler handler-fn))))

(defn add-handler-once
  ([handler-fn]
   (add-handler-once "" handler-fn))
  ([name handler-fn]
   (when-let [l (logger name)]
     (when-not (some (comp #{handler-fn} #(gobj/get % "handler-fn"))
                     (.-handlers_ l))
       (add-handler name handler-fn)))))

(defn log-method [level]
  (condp #(>= %2 %1) (level-value level)
    (level-value :severe)  "error"
    (level-value :warning) "warn"
    (level-value :info)    "info"
    (level-value :config)  "log"
    "log"))

(defn format-plain [{:keys [level logger-name message exception]}]
  [(str "[" logger-name "]") (pr-str message)])

(defn make-console-log [format]
  (fn [{:keys [logger-name level exception] :as record}]
    (let [method-name (log-method level)
          method      (or (gobj/get js/console method-name)
                          js/console.log)]
      (apply method (format record))
      (when exception
        (method (str "[" logger-name "]") (str exception) "\n" (.-stack exception))))))

(defonce console-log-plain (make-console-log format-plain))

(defn select-handler []
  console-log-plain)

(defn add-default-handler []
  (add-handler (select-handler)))

(defn install! []
  (enable-console-logging!)
  (add-default-handler))
