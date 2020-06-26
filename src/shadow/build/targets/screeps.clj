(ns shadow.build.targets.screeps
  (:require
    [clojure.java.io :as io]
    [shadow.build :as b]
    [shadow.build.targets.browser :as browser]
    [shadow.build.targets.shared :as shared]
    [shadow.cljs.repl :as repl]
    [shadow.build.api :as build-api]
    [shadow.cljs.devtools.api :as api]
    [shadow.build.modules :as modules]
    [shadow.build.output :as output]
    [shadow.build.data :as data]
    [clojure.data.json :as json]
    [clojure.string :as str]
    [shadow.cljs.util :as util]))

(defn configure
  [state mode {:keys [main output-to output-to-lib entries preloads append append-js prepend prepend-js] :as config}]
  (let [output-file
        (io/file output-to)
        output-file-lib
        (io/file output-to-lib)

        output-dir
        (-> output-file
            (.getParentFile)
            (.getAbsolutePath))

        module-config
        {:main
         (-> {:module-id :main
              :default true
              :entries entries
              :output-name (.getName output-file)
              :prepend-js (slurp (io/resource "shadow/build/targets/screeps_bootstrap.js"))
              :append-js (slurp (io/resource "shadow/build/targets/screeps_bootstrap_end.js"))
              }

             (cond->
               preloads
               (assoc :preloads preloads)
               prepend-js
               (update :prepend-js str "\n" prepend-js)
               prepend
               (assoc :prepend prepend)
               append-js
               (assoc :append-js append-js)
               append
               (assoc :append append)))}]

    ;; FIXME: should this just default to :simple?
    (-> state
        (assoc ::output-file output-file)
        (assoc ::output-file-lib output-file-lib)
        (build-api/merge-build-options
          {:asset-path "/"
           :output-dir output-dir})

        (build-api/with-js-options
          {:js-provider :shadow})

        (cond->
          (nil? (get-in config [:compiler-options :output-feature-set]))
          (assoc-in [:compiler-options :output-feature-set] :es6))
        (assoc-in [:compiler-options :variable-renaming] :off)
        (assoc-in [:compiler-options :property-renaming] :off)
        (assoc-in [:compiler-options :remove-dead-code] true)

        (modules/configure module-config)
        )))

;; always produce just one file
;; FIXME: could provide a helper utility for source maps
;; AFAICT graal itself doesn't support source maps directly but stacktraces seem reasonable easy to parse

(defn flush-dev
  [{::keys [output-file output-file-lib compiler-env] :as state}]

  (io/make-parents output-file)
  (io/make-parents output-file-lib)

  (let [{:keys [prepend append sources] :as mod}
        (-> state :build-modules first)

        header
        (str prepend
             "var shadow$provide = {};\n"

             (let [{:keys [polyfill-js]} state]
               (when (seq polyfill-js)
                 (str "\n" polyfill-js)))

                                        ;(output/closure-defines-and-base state)

                                        ;"goog.global[\"$CLJS\"] = goog.global;\n"

             ;; technically don't need this until we get a REPL in there somehow
                                        ;(slurp (io/resource "shadow/boot/static.js"))
             "\n\n"
             )

        reject-pats [
                     #"goog/uri/.*"
                     #"goog/html/.*"
                     #"goog/labs/.*"
                     #"goog/i18n/.*"
                     #"goog/fs/.*"
                     #"goog/dom/.*"
                     #".*userAgent.*"
                     ]

        accept-regex-pred (fn [pat]
                            (fn [[provides output-name]]
                              (println output-name)
                              (re-matches pat (str output-name))))
        reject-regex-pred (fn [pat]
                            (fn [[provides output-name]]
                              (not (re-matches pat (str output-name)))))
        build-filter (fn [pred-type filters pred-maker]
                       (apply pred-type (map pred-maker filters)))
        out-lib
        (->> sources
             ;; (filter (build-filter every-pred reject-pats reject-regex-pred))
             (filter (build-filter some-fn [#"goog.*", #"cljs/.*" #"clojure/.*"] accept-regex-pred))
             (map #(data/get-source-by-id state %))
             (map #(data/get-output! state %))
             (map :js)
             (map #(str/replace % #"var goog = .*;" "var goog = global.goog = {};"))
             (map #(str/replace % #"goog.global = this.*;" "goog.global = global;"))
             ;; (map #(str/replace % #"goog.require\(\"goog.dom.*;" ""))
             ;; (map #(str/replace % #"goog.require\(\"goog.html.*;" ""))
             ;; (map #(str/replace % #"goog.require\(\"goog.labs.*;" ""))
             (str/join "\n"))
        out
        (str header
             (->> sources
                  ;; (filter (build-filter every-pred [#"goog.*", #"cljs/.*" #"clojure/.*"] reject-regex-pred))
                  (map #(data/get-source-by-id state %))
                  (map #(data/get-output! state %))
                  (map :js)
                  (map #(str/replace % #"var goog = .*;" ""))
                  (map #(str/replace % #"goog.global = this.*;" ""))
                  (str/join "\n"))
             append)]

    (spit output-file out)
    ;; (spit output-file-lib out-lib)
    )

  state)

(defn process
  [{::b/keys [stage mode config] :as state}]
  state
  (cond
    (= :configure stage)
    (configure state mode config)

    (and (= :dev mode) (= :flush stage))
    (flush-dev state)

    (and (= :release mode) (= :flush stage))
    (output/flush-optimized state)

    :else
    state))
