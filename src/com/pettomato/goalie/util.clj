(ns pettomato.goalie.util
  (:require
   [clojure.core.logic :refer (-reify walk*)]))

(defn reify-all
  "Returns a lazy sequence of reified args."
  [a args]
  (map (partial -reify a) args))

(defn walk*-all
  "Returns a lazy sequence of walk*'d args."
  [a args]
  (map (partial walk* a) args))

(defn arglist
  "Returns the applicable arglist for the function bound to var, given
  the supplied args. This is just a convenience function that uses the
  count of args to find the applicable arglist in the var's metadata."
  [var args]
  (let [L (count args)]
    (some #(when (= (count %) L) %)
          (:arglists (meta var)))))
