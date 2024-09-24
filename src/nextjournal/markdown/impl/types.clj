(ns nextjournal.markdown.impl.types)

;; See also
;; https://github.com/noties/Markwon/blob/master/markwon-ext-latex/src/main/java/io/noties/markwon/ext/latex/JLatexMathBlockParser.java

(set! *warn-on-reflection* true)

(definterface CustomNode
  (getLiteral [])
  (setLiteral [v])
  (nodeType []))

(defn ->InlineFormula [lit]
  (let [state (atom lit)]
    (proxy [org.commonmark.node.CustomNode nextjournal.markdown.impl.types.CustomNode] []
      (getLiteral [] @state)
      (nodeType [] :inline-formula))))

(defn ->BlockFormula
  ([] (->BlockFormula nil))
  ([lit]
   (let [state (atom lit)]
     (proxy [org.commonmark.node.CustomBlock nextjournal.markdown.impl.types.CustomNode] []
       (getLiteral [] @state)
       (setLiteral [v] (do (reset! state v)
                           this))
       (nodeType [] :block-formula)))))

(defn ->ToC []
  (proxy [org.commonmark.node.CustomBlock nextjournal.markdown.impl.types.CustomNode] []
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
