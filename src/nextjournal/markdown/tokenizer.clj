(ns nextjournal.markdown.tokenizer
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

(defn next-paragraph [state]
  (cond
    (:paragraph state)
    (-> state (dissoc :paragraph)
        (update :blocks conj
                {:type "paragraph_close"
                 :tag "p"}))
    (not (:end state))
    (-> state (assoc :paragraph true)
        (update :blocks conj
                {:type "paragraph_open"
                 :tag "p"
                 :block true
                 :level 0}))
    :else state))

(defn add-child [state line]
  (update state :blocks conj {:children [{:content line
                                          :type "text"}]
                              :content line
                              :type "inline"}))

(defn tokenize [s]
  (let [rdr (io/reader (java.io.StringReader. s))]
    (binding [*in* rdr]
      (loop [state {:begin true
                    :blocks []}]
        (if-let [line (read-line)]
          (cond
            (str/blank? line)
            (next-paragraph state)
            (:begin state)
            (recur
             (-> state
                 (dissoc :begin)
                 (next-paragraph)
                 (add-child line)))
            :else (recur (add-child state line)))
          (:blocks (next-paragraph (assoc state :end true))))))))

;;;; Scratch

(comment
  (tokenize "hello")

  (require '[nextjournal.markdown :as original])
  (tokenize "hello world #foo")
  (original/tokenize "hello world #foo")
  (original/tokenize "hello world
#foo")

  (original/tokenize "hello world

## foo")
  )
