(ns nextjournal.markdown.parser2.footnotes
  (:require [clojure.string :as str]
            [nextjournal.markdown.parser2.types])
  (:import (nextjournal.markdown.parser2.types Footnote FootnoteRef)
           (org.commonmark.node Text Node Nodes)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder PostProcessor SourceLine)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState BlockParser)
           (org.commonmark.parser.delimiter DelimiterProcessor)))

(def footnote-id-regex (re-pattern "\\[\\^\\s*(.*)\\s*\\]"))
(def footnote-def-regex (re-pattern "^\\[\\^\\s*(.*)\\s*\\]:"))

(re-find footnote-id-regex "foo[^note] and")

(defn block-parser [label]
  (let [footnote-block (new Footnote label)]
    (proxy [AbstractBlockParser] []
      (isContainer [] true)
      (canContain [_other] true)
      (getBlock [] footnote-block)
      (tryContinue [^ParserState state]
        (let [non-space (.getNextNonSpaceIndex state)]
          ;; TODO: loose list case will have a blank line, but we want to continue
          (if (.isBlank state)
            (BlockContinue/finished)
            (BlockContinue/atIndex non-space)))))))

(comment
  (nextjournal.markdown.commonmark/parse "init [^label] end

[^label]: * this is nice _and_ nice
          * and so so
"))

(defn block-parser-factory []
  (proxy [Object BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [^SourceLine line (.getLine state)
              line-content (.getContent line)
              next-non-space (.getNextNonSpaceIndex state)
              candidate-content (subs line-content next-non-space)
              m (re-matcher footnote-def-regex candidate-content)]
          (if (re-find m)
            (let [block-index (+ next-non-space (.end m))
                  label (subs line-content (+ next-non-space 2)
                              (+ next-non-space (- (.end m) 2)))
                  ^BlockParser bp (block-parser label)]
              (.atIndex (BlockStart/of (into-array [bp]))
                        block-index))
            (BlockStart/none)))))))

(defn extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customBlockParserFactory pb (block-parser-factory)))))
