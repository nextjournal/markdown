(require '[clojure.pprint :as pprint]
         '[nextjournal.markdown :as md]
         '[nextjournal.markdown.transform :as md.transform]
         '[nextjournal.markdown.utils :as u])

;; (def blockquote-renderer (:blockquote md.transform/default-hiccup-renderers))

;; (clojure.pprint/pprint
;;  (md/->hiccup
;;   (assoc md.transform/default-hiccup-renderers
;;          :blockquote
;;          (fn [ctx {:keys [content] :as elt}]
;;            (if (= 1 (count content))
;;              (let [{:keys [content type]} (first content)]
;;                (if (and (= :paragraph type)
;;                         (= "[!NOTE]" (-> content first :text)))
;;                  [:div {:style {:color "green"}}
;;                   (blockquote-renderer ctx
;;                                        (update elt :content
;;                                                (fn [[paragraph]]
;;                                                  [(update paragraph :content (fn [text-nodes]
;;                                                                                (drop 2 text-nodes)))])))]
;;                  (blockquote-renderer ctx elt)))
;;              (blockquote-renderer ctx elt))))
;;   "> [!NOTE]
;; > Useful information that users should know, even when skimming content.

;; > Normal blockquote"
;; ))




(md/parse text)

(ns foo
  (:require [clojure.string :as str])
  (:import (java.util.regex Matcher)
           (org.commonmark.parser Parser$ParserExtension Parser$Builder SourceLine)
           (org.commonmark.parser.block AbstractBlockParser BlockContinue BlockParserFactory BlockStart ParserState)))

(def text "hello\n{%\nsomecontent\n%}")

;; Define custom block node type
(defrecord CustomTemplateBlock [content])

;; Regex patterns for block detection
(def custom-block-start-regex (re-pattern "^\\{%\\s*$"))
(def custom-block-end-regex (re-pattern "^%\\}\\s*$"))

(defn custom-block-start-matcher ^Matcher [^ParserState state]
  (let [^SourceLine line (.getLine state)
        next-non-space (.getNextNonSpaceIndex state)]
    (re-matcher custom-block-start-regex 
                (subs (.getContent line) next-non-space))))

(defn custom-block-parser []
  (let [custom-block (->CustomTemplateBlock nil)
        !lines (atom [])]
    (proxy [AbstractBlockParser] []
      (isContainer [] false)
      (canContain [_other] false)
      (getBlock [] custom-block)
      (addLine [^SourceLine line]
        (let [content (.getContent line)]
          ;; Don't include the closing %} line
          (when-not (re-find custom-block-end-regex content)
            (swap! !lines conj content))))
      (closeBlock []
        ;; Remove the opening {% line and set content
        (let [content-lines (drop 1 @!lines)
              cleaned-content (str/join "\n" content-lines)]
          (reset! (:content custom-block) cleaned-content)))
      (tryContinue [^ParserState state]
        (let [^SourceLine line (.getLine state)
              line-content (.getContent line)]
          (if (re-find custom-block-end-regex line-content)
            (BlockContinue/finished)
            (BlockContinue/atIndex (.getNextNonSpaceIndex state))))))))

(def custom-block-parser-factory
  (proxy [BlockParserFactory] []
    (tryStart [^ParserState state _matchedBlockParser]
      (let [next-non-space (.getNextNonSpaceIndex state)
            m (custom-block-start-matcher state)]
        (if (re-find m)
          (.atIndex (BlockStart/of (into-array [(custom-block-parser)]))
                    (+ next-non-space (.end m)))
          (BlockStart/none))))))

(defn create-extension []
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customBlockParserFactory pb custom-block-parser-factory))))




