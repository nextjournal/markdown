(ns nextjournal.markdown.tokenizer2
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.walk :as w]
            [nextjournal.markdown :as md]))

;; port of https://github.com/commonmark/commonmark.js/blob/master/lib/blocks.js

(def code-indent 4)

(defn find-next-nonspace [{:keys [line offset column] :as state}]
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
    (assoc state
           :blank (or (= "\n" c)
                      (= "\r" c)
                      (= "" c))
           :next-nonspace idx
           :next-nonspace-col cols
           :indent indent
           :indented (>= indent code-indent))))

(comment
  (find-next-nonspace {:line "hello there" :offset 0 :column 0})
  (find-next-nonspace {:line " hello there" :offset 0 :column 0})
  (find-next-nonspace {:line "    hello there" :offset 0 :column 0})
  )

(defn get-last-child [{:keys [container]}]
  ;; TODO
  )

(defn open? [node]
  ;; TODO
  )

(defn container-type [container]
  ;; TODO
  )

(def blocks {:document {:continue (fn [_] 0)
                        :finalize (fn [])
                        :can-contain? (fn [t] (= "item" t))
                        :accepts-lines false}
             :list     {:continue (fn [_] 0)
                        :finalize (fn []
                                    ;; TODO
                                    )
                        :can-contain? (fn [t] (= "item" t))
                        :accepts-lines false}

             :block-quote {:continue (fn [_]
                                       ;; TODO
                                       )
                           :finalize (fn []
                                       ;; TODO
                                       )
                           :can-contain? (fn [t] (= "item" t))
                           :accepts-lines false}
             :item {:continue (fn [_]
                                ;; TODO
                                )
                    :finalize (fn []
                                ;; TODO
                                )
                    :can-contain? (fn [t] (= "item" t))
                    :accepts-lines false}
             :heading {:continue (fn [_]
                                   ;; TODO
                                   )
                       :finalize (fn []
                                   ;; TODO
                                   )
                       :can-contain? (fn [t] (= "item" t))
                       :accepts-lines false}
             :thematic-break {:continue (fn [_]
                                          ;; TODO
                                          )
                              :finalize (fn []
                                          ;; TODO
                                          )
                              :can-contain? (fn [t] (= "item" t))
                              :accepts-lines false}
             :code-block {:continue (fn [_]
                                      ;; TODO
                                      )
                          :finalize (fn []
                                      ;; TODO
                                      )
                          :can-contain? (fn [t] (= "item" t))
                          :accepts-lines true}
             :html-block {:continue (fn [_]
                                      ;; TODO
                                      )
                          :finalize (fn [_]
                                      ;; TODO
                                      )
                          :can-contain? (fn [t] (= "item" t))
                          :accepts-lines true}
             :paragraph {:continue (fn [_]
                                     ;; TODO
                                     )
                         :finalize (fn []
                                     ;; TODO
                                     )
                         :can-contain? (fn [t] (= "item" t))
                         :accepts-lines true}})

(defn parent [container]
  ;; TODO
  )

;; var reMaybeSpecial = /^[#`~*+_=<>0-9-]/;
(def re-maybe-special #"^[#`~*+_=<>0-9-]")

(comment
  (re-find re-maybe-special "#")
  )

(defn advance-next-nonspace [{:keys [next-nonspace next-nonspace-col] :as state}]
  (assoc state
         :offset next-nonspace
         :column next-nonspace-col
         :partially-consumed-tab false))

(defn incorporate-line [{:keys [doc tip line-number line] :as state}] ;; https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L738
  (let [container doc
        all-matched true
        old-tip tip
        offset 0
        column 0
        blank false
        partially-consumed-tab false
        line-number (inc line-number)
        ;; TODO: https://github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L750

        current-line line
        last-child (get-last-child container)
        [container] (loop [last-child last-child]
                      (if (open? last-child)
                        ;; TODO, what do to with the result of find-next-nonspace?
                        (let [state (find-next-nonspace (assoc state :line current-line :offset offset :column column))
                              ct (container-type last-child)
                              continue-fn (get blocks ct)]
                          (case (continue-fn state)
                            0 (recur (get-last-child last-child)) ;; recur
                            1 [(parent last-child)] ;; recur
                            2 (recur (get-last-child last-child))
                            (throw (ex-info "continue returned illegal value, must be 0, 1 or 2" {}))))
                        [last-child]))
        state (assoc state
                     :all-closed (= container (:old-tip state))
                     :last-matched-container container
                     )
        matched-leaf (and (not= :paragraph (:type container))
                          (:accepts-lines (get blocks (:type container))))
        starts (:block-starts state) ;; https"//github.com/commonmark/commonmark.js/blob/9a16ff4fb8111bc0f71e03206f5e3bdbf7c69e8d/lib/blocks.js#L788"
        starts-len (count starts)
        [state container] (loop [state state
                                 container container]
                            (if (:matched-leaf state)
                              (let [{:keys [indented next-nonspace] :as state} (find-next-nonspace state)]
                                (if (and (not indented)
                                         (not (re-find re-maybe-special (subs line next-nonspace))))
                                  [(advance-next-nonspace state) container]
                                  (loop [idx 0]
                                    (if (< idx starts-len)
                                      (let [res ((nth starts idx) state container)]
                                        (cond (= 1 res)
                                              [state (:tip state)]
                                              (= 2 res)
                                              [(assoc state :matched-leaf true)
                                               (:tip state)]))
                                      (recur (inc idx))))))
                              [state container]))
        ]
    ;; TODO more work

    )
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
