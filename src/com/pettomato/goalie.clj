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

(def ^:dynamic *node-counter* (atom 0))

(defn mk-node-in [a gvar args id]
  (let [prev (get-node a)
        parent (if (in-node? prev) prev (:parent (:parent prev)))]
    (mk-node parent prev :in gvar args a id)))

(defn mk-node-out [a gvar args id parent-node]
  ;; parent-node is a hack to provide context when a branch fails.
  (let [prev (if a
               (get-node a)
               parent-node)
        parent (if a
                 (if (in-node? prev) prev (:parent (:parent prev)))
                 parent-node)]
    (mk-node parent prev :out gvar args a id)))

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

;; BUG: Once mzero is returned, we lose the ability to pass our
;; tracing functionality back up the branch. We can still call
;; run-out-hooks and get some useful information, but we won't have a
;; valid :prev field since there is no stream to carry the info. Maybe
;; there are clever ways to work around that, but I have a hard time
;; believing that would be the best solution.

(defn wrap-astream [as gvar args id parent-node]
  (cond
   ;; mzero
   (nil? as)                    (let [node2 (mk-node-out as gvar args id parent-node)]
                                  (run-out-hooks node2)
                                  as)
   ;; inc
   (fn? as)                     (fn [] (wrap-astream (as) gvar args id parent-node))
   ;; unit
   (instance? Substitutions as) (let [node2 (mk-node-out as gvar args id parent-node)]
                                  (run-out-hooks node2)
                                  (set-node as node2))
   ;; choice
   (instance? Choice as)        (bind as (fn [as] (wrap-astream as gvar args id parent-node)))
   ;; none of the above
   :else (throw (Error. (str "wrap-astream doesn't know how to handle: " (class as))))))

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
        (let [g (apply f args)
              id (swap! *node-counter* inc)]
          (fn [a]
            (let [node (mk-node-in a gvar args id)]
              (run-in-hooks node)
              (let [a2 (set-node a node)
                    as (g a2)]
                (wrap-astream as gvar args id node))))))
      {::traced true})))

;;;; Public API

(defmacro with-traced-goals
  "gs is a vector of symbols naming goals. This macro establishes the
  dynamic scope under which the supplied goals will be included in the
  instrumentation system. That means that you can add hooks to the
  goals, and the goals will appear as nodes in the reified search
  graph that is made available to hooks.

  Without at least one with-hooks in body, this macro should have no
  user visible effects.

  Note that every goal mentioned in a with-hooks expression must be
  included in an outer with-traced-goals expression, but not every
  goal needs hooks. You may only want to add hooks to a single goal,
  the one you think is misbehaving, but you want traces to include a
  few auxiliary goals, so you include them in the with-traced-goals.

  See with-hooks for more information.

  Also see the examples in src/com/pettomato/goalie/examples.clj

  WARNING: I'm not sure of the consequences of nested
  with-traced-goals expressions. I'd stick to a single one that
  mentions all of the goals of interest."
  [gs & body]
  (let [redefs (map (fn [g] `(trace-goal-ctor ~(resolve g))) gs)
        bindings (interleave gs redefs)]
    `(binding [*node-counter* (atom 0)]
       (with-redefs ~bindings ~@body))))

(defmacro with-hooks
  "gs is a vector of symbols naming goals. This macro establishes the
  dynamic scope under which the supplied goals will be instrumented
  with the supplied hooks.

  Each time one of the supplied goals is first invoked, fin will be
  passed a map containing various details about the current state of
  the search. Likewise, fout will be called with a similar map each
  time the goal produces a new value.

  See with-traced-goals for more information.

  Also see the examples in src/com/pettomato/goalie/examples.clj

  WARNING: I'm not sure what happens right now if you include the same
  goal in nested with-hooks expressions. Separate with-hooks with
  different goals should be fine; that way you can add different hooks
  to different goals."
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
