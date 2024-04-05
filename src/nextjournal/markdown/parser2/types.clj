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

(gen-class
 :name nextjournal.markdown.parser2.types.Footnote
 :extends org.commonmark.node.CustomBlock
 :constructors {[String] []}
 :init init
 :state state
 :methods [[getLabel [] String]]
 :prefix "footnote-")

(defn footnote-init [label] [[] (ref {:label label})])
(defn footnote-getLabel [this] (:label @(.state this)))

(gen-class
 :name nextjournal.markdown.parser2.types.FootnoteRef
 :extends org.commonmark.node.CustomNode
 :constructors {[String] []}
 :init init
 :state state
 :methods [[getLabel [] String]]
 :prefix "footnote-ref-")

(defn footnote-ref-init [label] [[] (ref {:label label})])
(defn footnote-ref-getLabel [this] (:label @(.state this)))

(comment
  (compile 'nextjournal.markdown.parser2.types))
