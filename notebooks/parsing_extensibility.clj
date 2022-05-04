;; # 🏗 Extending Markdown Parsing

^{:nextjournal.clerk/visibility :hide-ns :nextjournal.clerk/toc :collapsed}
(ns ^:nextjournal.clerk/no-cache parsing-extensibility
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [edamame.core :as edamame]))

;; With recent additions to our `nextjournal.markdown.parser` we added a tiny parsing layer on top of the tokenization provided by `markdown-it` ([n.markdown/tokenize](https://github.com/nextjournal/markdown/blob/ae2a2f0b6d7bdc6231f5d088ee559178b55c97f4/src/nextjournal/markdown.clj#L50-L52)).
;; We're acting on the text (leaf) tokens, splitting further each of those into a collection of [nodes](https://github.com/nextjournal/markdown/blob/ff68536eb15814fe81db7a6d6f11f049895a4282/src/nextjournal/markdown/parser.cljc#L5) according to the following strategy.
;;
;;    Match :: Any
;;    Handler :: Match -> Node
;;    IndexedMatch :: (Match, Int, Int)
;;    TokenizerFn :: String -> [IndexedMatch]
;;    Tokenizer :: {:tokenizer-fn :: TokenizerFn,
;;                  :handler :: Handler}
;;    DocOpts :: {:text-tokenizers [Tokenizer]}
;;    parse : DocOpts -> String -> [Node]
;;
;; We'll explain how that works by means of two examples.
;;
;; ## Regex-based tokenization
;;
;; A `Tokenizer` requires both keys `:handler` and `:tokenizer-fn` but for convenience we might provide a map
;; with a `:handler` and a `:regex` key and `md.parser/normalize-tokenizer` will fill in a `:tokenizer-fn`.

(def regex-tokenizer
  (md.parser/normalize-tokenizer
   {:regex #"\[\[([^\]]+)\]\]"
    :handler (fn [match] {:type :internal-link
                          :text (match 1)})}))

((:tokenizer-fn regex-tokenizer) "some [[set]] of [[wiki]] link")

(md.parser/tokenize-text regex-tokenizer "some [[set]] of [[wiki]] link")
;; and the whole story becomes
(md/parse "some [[set]] of [[wiki]] link")

;; ## Read-based tokenization
;;
;; Somewhat inspired by the Racket text processor [Pollen](https://docs.racket-lang.org/pollen/pollen-command-syntax.html) we'd like to parse a `text` like this

^{::clerk/visibility :hide ::clerk/viewer {:transform-fn (fn [{::clerk/keys [var-from-def]}] (clerk/html [:pre @var-from-def]))}}
(def text "At some point in text a losange
will signal ◊(foo \"one\" [[vector]]) we'll want to write
code and ◊not text. Moreover it has not to conflict with
existing [[links]] or #tags")
;; and _read_ any valid Clojure code comining after the lozenge character (`◊`) which we'll also call a
;; _losange_ as in French it does sound much better 🇫🇷!
;;
;; How to proceed? We might take a hint from `re-seq`.
^{::clerk/visibility :hide}
(clerk/html
 [:div.viewer-code
  (clerk/code
   (with-out-str
     (clojure.repl/source re-seq)))])

;; Now, when a form is read with [Edamame](https://github.com/borkdude/edamame#edamame), it preserves its location metadata. This allows
;; us to produce an `IndexedMatch` from matching text
(defn match->data+indexes [m text]
  (let [start (.start m) end (.end m)
        form (edamame/parse-string (subs text end))]
    [form start (+ end (dec (:end-col (meta form))))]))
;; and our modified `re-seq` becomes
(defn losange-tokenizer-fn [text]
  (let [m (re-matcher #"◊" text)]
    ((fn step []
       (when (.find m)
         (cons (match->data+indexes m text)
               (lazy-seq (step))))))))


(losange-tokenizer-fn text)
(losange-tokenizer-fn "non matching text")

(def losange-tokenizer
  {:tokenizer-fn losange-tokenizer-fn
   :handler (fn [clj-data] {:type :losange
                            :data clj-data})})

(md.parser/tokenize-text losange-tokenizer text)

;; putting it all together and giving losange topmost priority wrt other tokens
(md.parser/parse (update md.parser/empty-doc :text-tokenizers #(cons losange-tokenizer %))
                 (md/tokenize text))

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 8888}))
