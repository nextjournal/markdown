(ns nextjournal.markdown.impl.extensions
  (:require [clojure.string :as str]
            [nextjournal.markdown.impl.types :as t])
  (:import (java.util.regex Matcher Pattern)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.beta InlineContentParser InlineContentParserFactory ParsedInline InlineParserState Position Scanner)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState BlockParser)))

(set! *warn-on-reflection* true)

(def block-formula-delimiter-regex (re-pattern "^\\$\\$"))
(def block-toc-delimiter-regex (re-pattern "^\\[\\[TOC\\]\\]"))

(defn delimiter-matcher ^Matcher [^Pattern regex ^ParserState state]
  (let [^SourceLine line (.getLine state)
        next-non-space (.getNextNonSpaceIndex state)]
    (re-matcher regex (subs (.getContent line) next-non-space))))

(defn block-formula-delimiter-matcher ^Matcher [^ParserState s] (delimiter-matcher block-formula-delimiter-regex s))
(defn block-toc-delimiter-matcher ^Matcher [^ParserState s] (delimiter-matcher block-toc-delimiter-regex s))

(defn ascii-digit? [c]
  (<= (int \0) (int c) (int \9)))

(defn space? [c]
  (let [c (char c)]
    (or (Character/isWhitespace c) (Character/isSpaceChar c))))

(defn source-since [^Scanner scanner ^Position open]
  (.getContent (.getSource scanner open (.position scanner))))

(defn formula-end [^Scanner scanner content]
  (.next scanner)
  (when-not (ascii-digit? (.peek scanner))
    (ParsedInline/of (t/->InlineFormula content) (.position scanner))))

(defn double-dollar-formula [^Scanner scanner]
  (.next scanner)
  (when (= \$ (.peek scanner))
    (.next scanner)
    (let [open (.position scanner)]
      (when (and (pos? (.find scanner \$))
                 (not= (int \\) (.peekPreviousCodePoint scanner)))
        (let [content (source-since scanner open)]
          (.next scanner)
          (when (= \$ (.peek scanner))
            (formula-end scanner content)))))))

(defn single-dollar-formula [^Scanner scanner]
  (.next scanner)
  (let [open (.position scanner)]
    (when-not (space? (.peek scanner))
      (loop [prev nil]
        (let [c (.peek scanner)]
          (cond
            (= Scanner/END c) nil
            (and (= \$ c) prev (not (space? prev)) (not= \\ prev)) (formula-end scanner (source-since scanner open))
            :else (do (.next scanner) (recur c))))))))

(defn formula-parser-factory [parse]
  (reify InlineContentParserFactory
    (getTriggerCharacters [_] #{\$})
    (create [_]
      (reify InlineContentParser
        (tryParse [_ parser-state]
          (let [scanner (.scanner ^InlineParserState parser-state)]
            (or (when-not (ascii-digit? (.peekPreviousCodePoint scanner))
                  (parse scanner))
                (ParsedInline/none))))))))

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

(defn block-formula-parser ^BlockParser []
  (let [block-formula (t/->BlockFormula)
        !lines (atom [])]
    (proxy [AbstractBlockParser] []
      (isContainer [] false)
      (canContain [_other] false)
      (getBlock [] block-formula)
      (addLine [^SourceLine line]
        (when-some [l (not-empty (str/trim (.getContent line)))]
          (swap! !lines conj l)))
      (closeBlock []
        (t/setLiteral block-formula (let [formula-body (str/join \newline @!lines)]
                                      (cond-> formula-body
                                        (str/ends-with? formula-body "$$")
                                        (subs 0 (- (count formula-body) 2))))))
      (tryContinue [^ParserState state]
        (let [non-space (.getNextNonSpaceIndex state)]
          (if (close-block-formula? state !lines)
            (BlockContinue/finished)
            (BlockContinue/atIndex non-space)))))))

(def block-formula-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [next-non-space (.getNextNonSpaceIndex state)
              m (block-formula-delimiter-matcher state)]
          (if (re-find m)
            (.atIndex (BlockStart/of (into-array BlockParser [(block-formula-parser)]))
                      (+ next-non-space (.end m)))
            (BlockStart/none)))))))

(defn block-toc-parser ^BlockParser []
  (let [toc (t/->ToC)]
    (proxy [AbstractBlockParser] []
      (getBlock [] toc)
      ;; close immediately
      (tryContinue [^ParserState _state] (BlockContinue/finished)))))

(def block-toc-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [next-non-space (.getNextNonSpaceIndex state)
              m (block-toc-delimiter-matcher state)]
          (if (re-find m)
            (.atIndex (BlockStart/of (into-array BlockParser [(block-toc-parser)]))
                      (+ next-non-space (.end m)))
            (BlockStart/none)))))))

(defn create [ctx]
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customBlockParserFactory pb block-toc-parser-factory)
      (.customBlockParserFactory pb block-formula-parser-factory)
      (when-not (:disable-inline-formulas (:opts ctx))
        (.customInlineContentParserFactory pb (formula-parser-factory double-dollar-formula))
        (.customInlineContentParserFactory pb (formula-parser-factory single-dollar-formula))))))


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
