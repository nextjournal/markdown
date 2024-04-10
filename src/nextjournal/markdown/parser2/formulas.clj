(ns nextjournal.markdown.parser2.formulas
  (:import (nextjournal.markdown.parser2.types InlineFormula BlockFormula)
           (org.commonmark.internal InlineParserImpl)
           (org.commonmark.internal.inline InlineContentParser InlineParserState ParsedInline)
           (org.commonmark.parser InlineParserFactory Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState BlockParser)))

(set! *warn-on-reflection* true)

(def block-formula-delimiter-regex (re-pattern "^\\$\\$"))

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
                (ParsedInline/of (new InlineFormula source) (.position scanner)))))
          (ParsedInline/none))))))

(def inline-parser-factory
  (proxy [InlineParserFactory] []
    (create [ctx]
      (.addInlineParser ^InlineParserImpl (new InlineParserImpl ctx)
                        \$ (list (inline-formula-parser))))))

(def ^BlockParser block-parser
  (let [block-formula (new BlockFormula "formula text")]
    (proxy [AbstractBlockParser] []
      (isContainer [] false)
      (canContain [_other] false)
      (getBlock [] block-formula)
      (tryContinue [^ParserState state]
        (let [non-space (.getNextNonSpaceIndex state)]
          ;; ends at the next delimiter
          (if (.isBlank state)
            (BlockContinue/finished)
            (BlockContinue/atIndex non-space)))))))

(def block-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [^SourceLine line (.getLine state)
              line-content (.getContent line)
              next-non-space (.getNextNonSpaceIndex state)
              candidate-content (subs line-content next-non-space)
              m (re-matcher block-formula-delimiter-regex candidate-content)]
          (if (re-find m)
            (.atIndex (BlockStart/of (into-array [block-parser]))
                      (+ next-non-space (.end m)))
            (BlockStart/none)))))))

(defn extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.inlineParserFactory pb inline-parser-factory)
      (.customBlockParserFactory pb block-parser-factory))))

(comment

  (nextjournal.markdown.commonmark/parse "
  # Ok
  Aloha, that costs

  $$
  \\bigoplus
  $$

  * a $\\int_a^b\\phi(t)dt$ with discount
  * and what"))
