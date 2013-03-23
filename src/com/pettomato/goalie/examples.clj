(ns pettomato.goalie.examples
  (:refer-clojure :exclude [==])
  (:require
   [clojure.core.logic :as l :refer (run run* s# u# == conde fresh all nilo emptyo conso)]
   [pettomato.goalie :refer (with-traced-goals with-hooks)]
   [pettomato.goalie.print :refer (print-node print-path)]
   [pettomato.goalie.graph :refer (path)]
   [pettomato.goalie.stacktrace :refer (stacktrace)]))

;;; Some interesting goals we can use in examples.

(defn appendo [x y z]
  (conde
    [(nilo x) (== y z)]
    [(fresh [a d r]
       (conso a d x)
       (conso a r z)
       (appendo d y r))]))

(defn membero [x l]
  (conde
    [(fresh [d]
       (conso x d l))]
    [(fresh [a d]
       (conso a d l)
       (membero x d))]))

(defn pairo [p]
  (fresh [a d]
    (== (l/lcons a d) p)))

(defn flatteno [s out]
  (conde
    [(emptyo s) (== '() out)]
    [(pairo s)
     (fresh [a d res-a res-d]
       (conso a d s)
       (flatteno a res-a)
       (flatteno d res-d)
       (appendo res-a res-d out))]
    [(conso s '() out)]))

(defn anyo [q]
  (conde
    [q s#]
    [(anyo q)]))

(defn nevero []
  (anyo u#))

(defn alwayso []
  (anyo s#))

(comment

  ;; This will print out all the data we use to trace membero every
  ;; time it is called and every time it produces a new value.

  (with-traced-goals [membero]
    (with-hooks [membero]
      (partial println " IN:")
      (partial println "OUT:")
      (doall (run* [q] (membero q [1 2])))))

  ;; We can try to format that output a little better. We walk the
  ;; input args and display the result. When we print :out nodes, we
  ;; use a # instead of a : to highlight which args were constrained.

  ;; Note that the FAILs you see at the end are due to the system
  ;; attempting to get a 3rd value for q and failing.

  (with-traced-goals [membero conso]
    (with-hooks [membero conso]
      print-node
      print-node
      (doall (run* [q] (membero q [1 2])))))

  ;; Another example. We print SUCCEED when there are no args to
  ;; display.

  (with-traced-goals [alwayso nevero ==]
    (with-hooks [alwayso nevero ==]
      print-node
      print-node
      (doall
       (run 3 [q]
         (conde
           [(alwayso) s#]
           [(nevero)])
         (== q true)))))

  ;; An example that uses goals as arguments.

  (with-traced-goals [== anyo]
    (with-hooks [== anyo]
      print-node
      print-node
      (doall (run 2 [q]
               (anyo
                (conde
                  [(== q 1)]
                  [(== q 2)]))
               (== q 2)))))

  ;; A simple conde.

  (defn oilo [x]
    (conde
      [(== 'extra x) s#]
      [(== 'virgin x) u#]
      [(== 'olive x) s#]
      [(== 'oil x) s#]))

  (with-traced-goals [oilo]
    (with-hooks [oilo]
      print-node
      print-node
      (doall (run 3 [q] (oilo q)))))

  ;; More membero.

  (with-traced-goals [== membero]
    (with-hooks [membero]
      print-node
      print-node
      (doall
       (run* [q]
         (fresh [a b c]
           (== q [a b 3])
           (membero c q))))))

  ;; If the goal is called with only unground args, print out the path to
  ;; the goal, then throw an exception to avoid a stack overflow.

  (defn all-unground? [a args]
    (every? (comp l/lvar? (partial l/walk* a)) args))

  (defn warn-on-unground-args [b]
    (let [{:keys [gvar args a]} b]
      (when (all-unground? a args)
        (print-path (path b))
        (throw (Error. (format "All args to %s were fresh." (:name (meta gvar))))))))

  (with-traced-goals [emptyo conso pairo appendo flatteno]
    (with-hooks [flatteno]
      warn-on-unground-args
      (constantly nil)
      (doall (run* [q] (fresh [a] (flatteno [1 2 a] q))))))

  ;; Instead of printing out the path, convert it to a stacktrace and
  ;; add the new stacktrace to the exception.

  ;; This sounds interesting, but I'm under the impression that the
  ;; Java stacktrace facility doesn't offer enough customization to
  ;; make this worthwhile. There is nowhere to display reified lvars,
  ;; for example. - Austin Haas <austin@pettomato.com>

  ;; Note that at the time of this writing, there appears to be a bug
  ;; in clj-stacktrace which may cause the parsing of this stacktrace
  ;; to throw a different exception.
  ;;   https://github.com/mmcgrana/clj-stacktrace/issues/20

  (defn stacktrace-on-unground-args [b]
    (let [{:keys [gvar args a]} b]
      (when (all-unground? a args)
        (throw (doto (Error. (format "All args to %s were fresh." (:name (meta gvar))))
                 (.setStackTrace (stacktrace b)))))))

  (with-traced-goals [membero]
    (with-hooks [membero]
      stacktrace-on-unground-args
      (constantly nil)
      (doall (run* [q] (fresh [a] (membero q a))))))

  )
