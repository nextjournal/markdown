(ns nextjournal.markdown.impl.custom-block
  (:require [clojure.string :as str])
  (:import (java.util.regex Matcher Pattern)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState)
           (org.commonmark.node CustomBlock)))

(def custom-block-start-regex (re-pattern "^\\{%"))
(def custom-block-end-regex (re-pattern "%\\}$"))

(defn custom-block-start-matcher ^Matcher [^ParserState state]
  (let [^SourceLine line (.getLine state)
        next-non-space (.getNextNonSpaceIndex state)]
    (re-matcher custom-block-start-regex (subs (.getContent line) next-non-space))))

(defn custom-block-parser []
  (let [custom-block (CustomBlock.)
        !lines (atom [])]
    (proxy [AbstractBlockParser] []
      (isContainer [] false)
      (canContain [_other] false)
      (getBlock [] custom-block)
      (addLine [^SourceLine line]
        (swap! !lines conj (.getContent line)))
      (closeBlock []
        (let [content (str/join "\n" @!lines)
              ;; Remove the {% and %} delimiters
              cleaned-content (-> content
                                  (str/replace #"^\{%" "")
                                  (str/replace #"%\}$" "")
                                  str/trim)]
          (.setLiteral custom-block cleaned-content)))
      (tryContinue [^ParserState state]
        (let [^SourceLine line (.getLine state)
              line-content (.getContent line)]
          (if (re-find custom-block-end-regex line-content)
            (BlockContinue/finished)
            (BlockContinue/atIndex (.getNextNonSpaceIndex state))))))))

(def custom-block-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (if (<= 4 (.getIndent state))
        (BlockStart/none)
        (let [next-non-space (.getNextNonSpaceIndex state)
              m (custom-block-start-matcher state)]
          (if (re-find m)
            (.atIndex (BlockStart/of (into-array [(custom-block-parser)]))
                      (+ next-non-space (.end m)))
            (BlockStart/none)))))))

(defn create-extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customBlockParserFactory pb custom-block-parser-factory))))
