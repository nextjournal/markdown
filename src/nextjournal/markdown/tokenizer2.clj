(ns nextjournal.markdown.tokenizer2
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as w]
            [nextjournal.markdown :as md]))

;; port of https://github.com/commonmark/commonmark.js/blob/master/lib/blocks.js

(def code-indent 4)

(defn find-next-non-space [{:keys [line offset column]}]
  (let [[c idx cols]
        (loop [idx offset
               cols column
               ]
          (let [c (.charAt ^String line idx)]
            (case (str c)
              " "
              (recur (inc idx) (inc cols))
              "\t"
              (recur (inc idx) (+ cols (- 4 (mod cols 4))))
              [(str c) idx cols])))
        indent (- cols column)]
    {:blank (or (= "\n" c)
                (= "\r" c)
                (= "" c))
     :next-non-space idx
     :next-non-space-col cols
     :indent indent
     :indented (>= indent code-indent)}))

(comment
  (find-next-non-space {:line "hello there" :offset 0 :column 0})
  (find-next-non-space {:line " hello there" :offset 0 :column 0})
  (find-next-non-space {:line "    hello there" :offset 0 :column 0})
  )

(defn incorporate-line [line] ;; https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L738
  (let [all-matched true
        ])
  )

(defn tokenize [input]
  ;; document
  ;; blocks
  ;; parse fn
  ;; ref-map
  ;; TODO: streaming instead of everything in memory? I'll stick to a direct port for now
  (let [lines (str/split-lines input) ;; https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L942
        len (count lines)]
    ;; TODO: incorporate lines https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L954


    ;; TODO process inlines https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L966
    )

  )

;;;; Scratch

(comment
  
  (require '[nextjournal.markdown :as original])
  (tokenize "first paragraph

second paragraph")
  (original/tokenize "hello world #foo")
  (original/tokenize "hello world
#foo")

  (original/tokenize "hello world

## foo")

  (original/tokenize "foo
## foo")

  (require '[markdown.core :as m2])
  (m2/md-to-html-string* "foo\nbar" nil)

  )
