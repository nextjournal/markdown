(ns nextjournal.markdown.parser2.footnotes
  (:require [nextjournal.markdown.parser2.types])
  (:import (nextjournal.markdown.parser2.types Footnote)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState BlockParser)))

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
  (nextjournal.markdown.parser2/parse "what the

[^what]: * what _the_ heck
* the

what a what"))

(defn extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customBlockParserFactory
       pb (proxy [Object BlockParserFactory] []
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
                      (println :match label (.start m) (.end m))
                      (.atIndex (BlockStart/of (into-array [bp]))
                                block-index))
                    (BlockStart/none))))))))))
