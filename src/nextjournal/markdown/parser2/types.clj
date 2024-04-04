(ns nextjournal.markdown.parser2.types)

;; See also
;; https://github.com/noties/Markwon/blob/master/markwon-ext-latex/src/main/java/io/noties/markwon/ext/latex/JLatexMathBlockParser.java

(gen-class
 :name nextjournal.markdown.parser2.types.InlineFormula
 :extends org.commonmark.node.CustomNode
 :constructors {[String] []}
 :init init
 :state state
 :implements [org.commonmark.node.Delimited]
 :methods [[getLiteral [] String]]
 :prefix "inline-formula-")

(defn inline-formula-init [lit] [[] (ref {:literal lit})])
(defn inline-formula-getLiteral [this] (:literal @(.state this)))
(defn inline-formula-getClosingDelimiter [this] "$")
(defn inline-formula-getOpeningDelimiter [this] "$")

(comment
  (compile 'nextjournal.markdown.parser2.types)

  )
