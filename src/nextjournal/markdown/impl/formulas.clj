(ns nextjournal.markdown.impl.formulas
  (:require [clojure.string :as str])
  (:import (java.util.regex Matcher)
           (nextjournal.markdown.impl.types InlineFormula BlockFormula)
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

(defn block-formula-delimiter-matcher ^Matcher [^ParserState state]
  (let [^SourceLine line (.getLine state)
        next-non-space (.getNextNonSpaceIndex state)]
    (re-matcher block-formula-delimiter-regex (subs (.getContent line) next-non-space))))

(defn close-block-formula? [state !lines]
  ;; we allow 1-liner blocks like A)
  ;; text
  ;;
  ;; $$\\bigoplus$$
  ;;
  ;; or blocks delimited by $$ B)
  ;;
  ;; $$
  ;; \\bigoplus
  ;; $$
  (or #_A (when-some [l (last @!lines)] (str/ends-with? (str/trimr l) "$$"))
      #_B (some? (re-find (block-formula-delimiter-matcher state)))))

(defn block-parser ^BlockParser []
  (let [block-formula (new BlockFormula)
        !lines (atom [])]
    (proxy [AbstractBlockParser] []
      (isContainer [] false)
      (canContain [_other] false)
      (getBlock [] block-formula)
      (addLine [^SourceLine line]
        (when-some [l (not-empty (str/trim (.getContent line)))]
          (swap! !lines conj l)))
      (closeBlock []
        (.setLiteral block-formula (let [formula-body (str/join \newline @!lines)]
                                     (cond-> formula-body
                                       (str/ends-with? formula-body "$$")
                                       (subs 0 (- (count formula-body) 2))))))
      (tryContinue [^ParserState state]
        (let [non-space (.getNextNonSpaceIndex state)]
          (if (close-block-formula? state !lines)
            (BlockContinue/finished)
            (BlockContinue/atIndex non-space)))))))

(def block-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [next-non-space (.getNextNonSpaceIndex state)
              m (block-formula-delimiter-matcher state)]
          (if (re-find m)
            (.atIndex (BlockStart/of (into-array [(block-parser)]))
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
  (class (re-matcher #"" ""))
  (nextjournal.markdown.commonmark/parse "
# Title

This is an $\\mathit{inline}$ formula

$$
\\begin{equation}
\\dfrac{1}{128\\pi^{2}}
\\end{equation}
$$

* a $\\int_a^b\\phi(t)dt$ with discount
* and what"))
