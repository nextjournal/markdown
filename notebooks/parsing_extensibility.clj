;; # 🏗 Extending Markdown Parsing
(ns parsing-extensibility
  {:nextjournal.clerk/toc :collapsed
   :nextjournal.clerk/no-cache true}
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [edamame.core :as edamame]
            [clojure.string :as str]))

^{:nextjournal.clerk/visibility {:code :hide :result :hide}}
(def show-text
  {:var-from-def? true
   :transform-fn (fn [{{::clerk/keys [var-from-def]} :nextjournal/value}] (clerk/html [:pre @var-from-def]))})

;; With recent additions to our `nextjournal.markdown.parser` we're allowing for a customizable parsing layer on top of the tokenization provided by `markdown-it` ([n.markdown/tokenize](https://github.com/nextjournal/markdown/blob/ae2a2f0b6d7bdc6231f5d088ee559178b55c97f4/src/nextjournal/markdown.clj#L50-L52)).
;; We're acting on the text (leaf) tokens, splitting each of those into a collection of [nodes](https://github.com/nextjournal/markdown/blob/ff68536eb15814fe81db7a6d6f11f049895a4282/src/nextjournal/markdown/parser.cljc#L5).  We'll explain how that works by means of three examples.
;;
;; ## Regex-based tokenization
;;
;; A `Tokenizer` is a map with keys `:doc-handler` and `:tokenizer-fn`. For convenience, the function `md.parser/normalize-tokenizer` will fill in the missing keys
;; starting from a map with a `:regex` and a `:handler`:

(def internal-link-tokenizer
  (md.parser/normalize-tokenizer
   {:regex #"\[\[([^\]]+)\]\]"
    :handler (fn [match] {:type :internal-link
                          :text (match 1)})}))

((:tokenizer-fn internal-link-tokenizer) "some [[set]] of [[wiki]] link")

(md.parser/tokenize-text-node internal-link-tokenizer {} {:text "some [[set]] of [[wiki]] link"})


;; In order to opt-in of the extra tokenization above, we need to configure the document context as follows:
(md/parse (update md.parser/empty-doc :text-tokenizers conj internal-link-tokenizer)
          "some [[set]] of [[wiki]] link")

;; We provide an `internal-link-tokenizer` as well as a `hashtag-tokenizer` as part of the `nextjournal.markdown.parser` namespace. By default, these are not used during parsing and need to be opted-in for like explained above.

;; ## Read-based tokenization
;;
;; Somewhat inspired by the Racket text processor [Pollen](https://docs.racket-lang.org/pollen/pollen-command-syntax.html) we'd like to parse a `text` like this

^{::clerk/visibility {:code :hide} ::clerk/viewer show-text}
(def text "At some point in text a losange
will signal ◊(foo \"one\" [[vector]]) we'll want to write
code and ◊not text. Moreover it has not to conflict with
existing [[links]] or #tags")
;; and _read_ any valid Clojure code comining after the lozenge character (`◊`) which we'll also call a
;; _losange_ as in French it does sound much better 🇫🇷!
;;
;; How to proceed? We might take a hint from `re-seq`.
^{::clerk/visibility {:code :hide}}
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
  (md.parser/normalize-tokenizer
   {:tokenizer-fn losange-tokenizer-fn
    :handler (fn [clj-data] {:type :losange
                             :data clj-data})}))

(md.parser/tokenize-text-node losange-tokenizer {} {:text text})

;; putting it all together and giving losange topmost priority wrt other tokens
(md.parser/parse (update md.parser/empty-doc :text-tokenizers #(cons losange-tokenizer %))
                 (md/tokenize text))

;; ## Parsing with Document Handlers
;;
;; Using tokenizers with document handlers we can let parsed tokens act upon the whole document tree. Consider
;; the following textual example (**TODO** _rewrite parsing with a zipper state_):
^{::clerk/viewer show-text}
(def text-with-meta
  "# Example ◊(add-meta {:attrs {:id \"some-id\"} :class \"semantc\"})
In this example we're using the losange tokenizer to modify the
document AST in conjunction with the following functions:
* `add-meta`: looks up the parent node, merges a map in it
and adds a flag to its text.
* `strong`: makes the text ◊(strong much more impactful) indeeed.
")

(defn add-meta [{:as doc ::md.parser/keys [path]} meta]
  (-> doc
      (update-in (md.parser/ppop path) merge meta)
      (update-in (conj (md.parser/ppop path) :content)
                 (fn [content]
                   (-> content
                       (update-in [(dec (count content)) :text]
                                  #(-> % str/trimr (str "🚩️"))))))))

(defn strong [doc & terms]
  (-> doc
      (md.parser/open-node :strong)
      (md.parser/push-node (md.parser/text-node (apply str (interpose " " terms))))
      md.parser/close-node))

(def data
  (md.parser/parse
   (-> md.parser/empty-doc
       (dissoc :text->id+emoji-fn)
       (update :text-tokenizers conj
               (assoc losange-tokenizer
                      :doc-handler (fn [doc {:keys [match]}]
                                     (apply (eval (first match)) doc (rest match))))))
   (md/tokenize text-with-meta)))

(clerk/md data)

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! {:port 8888})
  ;;    Tokenizer :: {:tokenizer-fn :: TokenizerFn,
  ;;                  :doc-handler :: DocHandler}
  ;;    normalize-tokenizer :: {:regex, :doc-handler} |
  ;;                           {:tokenizer-fn, :handler} |
  ;;                           {:regex, :handler} -> Tokenizer
  ;;
  ;;    Match :: Any
  ;;    Handler :: Match -> Node
  ;;    IndexedMatch :: (Match, Integer, Integer)
  ;;    TokenizerFn :: String -> [IndexedMatch]
  ;;    DocHandler :: Doc -> {:match :: Match} -> Doc

  ;;    DocOpts :: {:text-tokenizers [Tokenizer]}
  ;;    parse : DocOpts -> [Token] -> Doc
  ;;
  )
