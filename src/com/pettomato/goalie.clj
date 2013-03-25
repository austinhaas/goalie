(ns pettomato.goalie
  (:require
   [clojure.core.logic :as l]
   [clojure.core.logic.protocols :refer (bind)]
   [pettomato.goalie.graph :refer (mk-node in-node?)])
  (:import
   [clojure.core.logic Substitutions Choice]))

;; How nodes are attached to substitutions:

(def gkey ::goalie-node)
(defn get-node [a] (gkey (meta a)))
(defn set-node [a node] (vary-meta a assoc gkey node))

;; Convenient constructors.

;; Technical detail: mk-node-out exploits the fact that we must
;; propagate the :in node in order to know where we are if the goal
;; returns nil (aka, mzero), so we just use it as a data source to
;; populate the out node.

(def ^:dynamic *node-counter* (atom 0))

(defn mk-node-in [a gvar args]
  (let [id (swap! *node-counter* inc)
        prev (get-node a)
        parent (if (in-node? prev) prev (:parent (:parent prev)))]
    (mk-node parent prev :in gvar args a id)))

(defn mk-node-out [as node-in]
  (let [{:keys [gvar args id]} node-in
        prev (get-node as)]
    (mk-node node-in prev :out gvar args as id)))

;;; Hooks

;; Hooks are functions that are invoked whenever a new node is
;; created. They take a single argument: the new node. Hooks are
;; intended to be used for their side-effects; returned values are
;; discarded.

(def ^:dynamic *hooks* {})
(defn get-in-hooks  [gvar] (get-in *hooks* [gvar :in]))
(defn get-out-hooks [gvar] (get-in *hooks* [gvar :out]))
(defn run-in-hooks  [node] (doseq [f (get-in-hooks  (:gvar node))] (f node)))
(defn run-out-hooks [node] (doseq [f (get-out-hooks (:gvar node))] (f node)))

;;; Instrumentation

(defn wrap-astream [as node]
  (cond
   ;; mzero
   (nil? as)                    (let [node2 (mk-node-out as node)]
                                  (run-out-hooks node2)
                                  as)
   ;; inc
   (fn? as)                     (fn [] (wrap-astream (as) node))
   ;; unit
   (instance? Substitutions as) (let [node2 (mk-node-out as node)]
                                  (run-out-hooks node2)
                                  (set-node as node2))
   ;; choice
   (instance? Choice as)        (bind as (fn [as] (wrap-astream as node)))
   ;; none of the above
   :else (throw (Error. (str "wrap-astream doesn't know how to handle: " as)))))

(defn trace-goal-ctor
  "Returns an instrumented replacement for the goal constructor bound to gvar."
  [gvar]
  ;; f: the original goal constructor
  ;; g: a clean goal
  (let [f @gvar]
    (assert (ifn? f) (format "Must implement IFn: %s" gvar))
    (assert (not (:macro (meta gvar))) (format "Don't trace macros: %s" gvar))
    (assert (not (::traced (meta @gvar))) (format "Only trace once: %s" gvar))
    (with-meta
      (fn [& args]
        (let [g (apply f args)]
          (fn [a]
            (let [node (mk-node-in a gvar args)]
              (run-in-hooks node)
              (let [a2 (set-node a node)
                    as (g a2)]
                (wrap-astream as node))))))
      {::traced true})))

;;;; Public API

(defmacro with-traced-goals
  [gs & body]
  (let [redefs (map (fn [g] `(trace-goal-ctor ~(resolve g))) gs)
        bindings (interleave gs redefs)]
    `(binding [*node-counter* (atom 0)]
       (with-redefs ~bindings ~@body))))

(defmacro with-hooks
  [gs fin fout & body]
  `(do
     ;; Assert that each goal is within the scope of a corresponding with-traced-goals.
     ~@(for [g gs] `(assert (::traced (meta ~g)) (str "You can't add hooks to " '~g " unless it's within the scope of a with-traced-goals that also includes " '~g)))
     ;; Merge new hooks with any existing hooks, and bind the new map to our dynamic var.
     ~(let [m {:in [fin] :out [fout]}
            h1 (zipmap (map resolve gs) (repeat m))]
        `(let [h2# (merge-with (fn [old# new#] (merge-with concat old# new#)) *hooks* ~h1)]
           (binding [*hooks* h2#]
             ~@body)))))
