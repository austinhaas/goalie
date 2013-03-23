(ns pettomato.goalie.stacktrace
  (:require
   [pettomato.goalie.graph :refer (path)]))

;; I don't know the proper format for these fields; I just tried to
;; mimic what I saw in other stacktraces.
(defn stackelement [node]
  (let [{:keys [name ns file line]} (meta (:gvar node))
        decl-class (str (ns-name ns) "$" name)
        method-name "invoke"]
    (StackTraceElement. decl-class method-name file line)))

(defn stacktrace [node]
  (into-array (map stackelement (path node))))
