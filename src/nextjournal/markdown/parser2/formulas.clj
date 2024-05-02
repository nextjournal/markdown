(ns nextjournal.markdown.parser2.formulas
  (:import (java.util Set)
           (nextjournal.markdown.parser2.types InlineFormula BlockFormula)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.beta InlineContentParser InlineContentParserFactory ParsedInline InlineParserState)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState BlockParser)))

(set! *warn-on-reflection* true)

(def block-formula-delimiter-regex (re-pattern "^\\$\\$"))

(defn inline-formula-parser []
  (proxy [InlineContentParser] []
    (tryParse [^InlineParserState parser-state]
      (let [scanner (.scanner parser-state)
            ;; move past opening $
            _ (.next scanner)
            open-pos (.position scanner)]
        (if (= -1 (.find scanner \$))
          (ParsedInline/none)
          (let [^String content (.getContent (.getSource scanner open-pos (.position scanner)))]
            (.next scanner)
            (ParsedInline/of (new InlineFormula content) (.position scanner))))))))

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
      (.customBlockParserFactory pb block-parser-factory)
      (.customInlineContentParserFactory pb (reify InlineContentParserFactory
                                              (getTriggerCharacters [_] #{\$})
                                              (create [_] (inline-formula-parser)))))))

(comment

  (nextjournal.markdown.commonmark/parse "
  # Ok
  This is an $\\mathit{inline}$ formula

  $$
  \\bigoplus
  $$

  * a $\\int_a^b\\phi(t)dt$ with discount
  * and what"))
