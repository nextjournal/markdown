(ns nextjournal.markdown.impl.types
  (:import [nextjournal.markdown.impl.types CustomNode]))

;; See also
;; https://github.com/noties/Markwon/blob/master/markwon-ext-latex/src/main/java/io/noties/markwon/ext/latex/JLatexMathBlockParser.java

(set! *warn-on-reflection* true)

(defn ->InlineFormula [lit]
  (let [state (atom lit)]
    (proxy [org.commonmark.node.CustomNode CustomNode] []
      (getLiteral [] @state)
      (nodeType [] :inline-formula))))

(defn ->BlockFormula
  ([] (->BlockFormula nil))
  ([lit]
   (let [state (atom lit)]
     (proxy [org.commonmark.node.CustomBlock CustomNode] []
       (getLiteral [] @state)
       (setLiteral [v] (do (reset! state v)
                           this))
       (nodeType [] :block-formula)))))

(defn ->ToC []
  (proxy [org.commonmark.node.CustomBlock CustomNode] []
    (nodeType [] :toc)))

(defn setLiteral [^CustomNode n lit]
  (.setLiteral n lit))

(defn getLiteral [^CustomNode n]
  (.getLiteral n))

(defn nodeType [^CustomNode n]
  (.nodeType n))

(comment
  (def i (->InlineFormula "1+1"))
  (instance? nextjournal.markdown.impl.types.CustomNode i)
  (let [b (->BlockFormula)]
    (-> (setLiteral b "dude")
        (getLiteral)))
  )
