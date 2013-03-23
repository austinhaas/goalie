(ns pettomato.goalie.graph)

(defn mk-node [parent prev dir gvar args a id]
  {:parent parent ; The parent node, lexically. Lexically?
   :prev prev     ; The previous node on the branch.
   :dir dir       ; :in or :out
   :a a           ; The substitution.
   :gvar gvar     ; The var bound to the goal. It has useful metadata.
   :args args     ; The arguments that the original goal constructor was called with.
   :id id         ; A unique identifier, shared by the :in node and all :out nodes.
   })

(defn in-node?  [node] (= (:dir node) :in))
(defn out-node? [node] (= (:dir node) :out))

;; Path

(defn path
  "Returns the sequence of nodes from the root to node."
  [node]
  (reverse (take-while (complement nil?) (iterate :prev node))))

(defn parent-path
  "This is like path, but it walks up by parent edges, rather than the
  previous edges. Usually, you'll want to use path. This is useful
  when considering the search in the context of the source code."
  [node]
  (reverse (take-while (complement nil?) (iterate :parent node))))

(defn depth
  "Returns the number of :in nodes in the supplied node's ancestry."
  [node]
  (let [c (count (parent-path node))]
   (if (in-node? node)
     (dec c)
     (dec (dec c)))))
