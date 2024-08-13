(ns nextjournal.markdown.parser.impl.types)

;; See also
;; https://github.com/noties/Markwon/blob/master/markwon-ext-latex/src/main/java/io/noties/markwon/ext/latex/JLatexMathBlockParser.java

(gen-class
 :name nextjournal.markdown.parser2.types.InlineFormula
 :extends org.commonmark.node.CustomNode
 :constructors {[String] []}
 :init init
 :state state
 :methods [[getLiteral [] String]]
 :prefix "inline-formula-")

(defn inline-formula-init [lit] [[] (ref {:literal lit})])
(defn inline-formula-getLiteral [this] (:literal @(.state this)))

(gen-class
 :name nextjournal.markdown.parser2.types.BlockFormula
 :extends org.commonmark.node.CustomBlock
 :constructors {[] []}
 :init init
 :state state
 :prefix "block-formula-"
 :methods [[getLiteral [] String]
           [setLiteral [String] String]])

(defn block-formula-init [] [[] (atom nil)])
(defn block-formula-getLiteral [this] @(.state this))
(defn block-formula-setLiteral [this val] (reset! (.state this) val))

(comment
  (compile 'nextjournal.markdown.parser2.types))
