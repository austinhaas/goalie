(ns pettomato.goalie.print
  (:require
   [pettomato.goalie.util :refer (arglist walk*-all)]
   [pettomato.goalie.graph :refer (in-node? out-node? depth)]))

(defn rstr
  "Returns a string composed of n repetitions of s."
  [n s]
  {:pre [(not (neg? n))]}
  (apply str (repeat n s)))

(defn print-node-in [node]
  {:pre [(in-node? node)]}
  (let [{:keys [gvar args a id]} node
        name (:name (meta gvar))
        arglist (arglist gvar args)
        header (str name " " arglist)
        prev-id (:id (:prev node))
        prefix (format "%05d:%05d %s" prev-id id (rstr (depth node) "|  "))]
    (printf "%s@ %s\n" prefix header)
    (when-not (empty? arglist)
      (let [rs (walk*-all a args)
            rs-strs (map pr-str rs)]
        (printf "%s|   %s : %s\n" prefix (first arglist) (first rs-strs))
        (doall (map (fn [k v] (printf "%s|   %s : %s\n" prefix k v))
                    (rest arglist) (rest rs-strs)))))))

(defn print-node-out [node]
  {:pre [(out-node? node)]}
  (let [{:keys [gvar args a id]} node
        arglist (arglist gvar args)
        prev-id (:id (:prev node))
        prefix (format "%05d:%05d %s" prev-id id (rstr (depth node) "|  "))]
    (cond
     (nil? a) (printf "%s|-> FAIL\n" prefix)

     (empty? arglist) (printf "%s|-> SUCCEED\n" prefix)

     :else (let [r1 (walk*-all (:a (:parent node)) args)
                 r2 (walk*-all a args)]
             (printf "%s|-> %s %s %s\n"
                     prefix (first arglist) (if (= (first r1) (first r2)) ":" "#") (pr-str (first r2)))
             (doall (map (fn [k old new]
                           (printf "%s|-> %s %s %s\n" prefix k (if (= old new) ":" "#") (pr-str new)))
                         (rest arglist) (rest r1) (rest r2)))))))

(defn print-node [node]
  (if (in-node? node)
    (print-node-in node)
    (print-node-out node)))

(defn print-path
  [path]
  (doall (map print-node path)))
