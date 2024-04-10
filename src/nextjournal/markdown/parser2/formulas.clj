(ns nextjournal.markdown.parser2.formulas
  (:import (nextjournal.markdown.parser2.types InlineFormula)
           (org.commonmark.node Node)
           (org.commonmark.internal InlineParserImpl)
           (org.commonmark.internal.inline InlineContentParser InlineParserState ParsedInline)
           (org.commonmark.parser InlineParserFactory Parser Parser$ParserExtension Parser$Builder)))

(defn inline-formula-parser []
  (proxy [InlineContentParser] []
    (tryParse [^InlineParserState parser-state]

      (let [scanner (.scanner parser-state)
            dollars-open (.matchMultiple scanner \$)
            after-opening (.position scanner)]

        (if (< 0 (.find scanner \$))
          (let [before-closing (.position scanner)
                dollars-close (.matchMultiple scanner \$)]
            (if (= dollars-open dollars-close)
              (let [^String source (.getContent (.getSource scanner after-opening before-closing))]
                (prn :source source)
                (ParsedInline/of (new InlineFormula source) (.position scanner)))))
          (ParsedInline/none))))))

(defn extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.inlineParserFactory pb (proxy [InlineParserFactory] []
                                 (create [ctx]
                                   (.addInlineParser (new InlineParserImpl ctx)
                                                     \$ (list (inline-formula-parser)))))))))

(comment

  (nextjournal.markdown.commonmark/parse "
  # Ok
  Aloha, that costs
  * a $\\int_a^b\\phi(t)dt$ with discount
  * and what"))
