;; # 🏗 Extending Markdown Parsing
(ns parsing-extensibility
  {:nextjournal.clerk/toc :collapsed
   :nextjournal.clerk/no-cache true}
  (:require
   [clojuer.java.io :as io]
   [clojure.core.async :as async]
   [clojure.string :as str]
   [edamame.core :as edamame]
   [nextjournal.clerk :as clerk]
   [nextjournal.markdown :as md]
   [nextjournal.markdown.parser :as md.parser]
   [nextjournal.markdown.transform :as md.transform]))

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
;; 
;; ### Example #2: custom emoji 
;;
;; Another, sligthly more complex example is extending the Hiccup transformer,
;; so that we can add emojis (like :smile:) to the text.
;;
;; Assuming we have multiple emoji packs installed, we need to first build a data
;; structure to hold the metadata about the emoji, like this:
'([:memes [{:name "bonk"
            :path "img/emoji/memes/bonk.png"}]]
  [:yellow_ball [{:name "yb-sunglasses"
                  :path "img/emoji/yellow_ball/yb-sunglasses.png"}]])

(defonce emoji-dir "resources/public/img/emotes")

(defn scan-emoji-dir
  "Scans the emoji directory and returns a map of emoji info.
  The map is a vector of composite map vectors"
  []
  (let [root (io/file emoji-dir)
        packs (.listFiles root)]
    (mapcat (fn [pack]
              (when (.isDirectory pack)
                (let [pack-name (.getName pack)
                      files (.listFiles pack)
                      emoji-names (mapv (fn [file]
                                          (-> file
                                              .getName
                                              (str/split #"\." 2) ; trim the extension naively
                                              first))
                                        files)
                      ;; you can remove str/replace call here if it won't cause issues with
                      ;; your public resources path not being accessible to the client
                      paths (mapv #(str/replace (.getPath %) #"resources/public" "") files)]
                  (assoc {} (keyword pack-name) (mapv #(assoc {} :name % :path %2) emoji-names paths)))))
            packs)))

;; cache the emoji. This is a great boost for performance, 
;; but you'll need to restart your application when you add new emoji
(defonce emoji (scan-emoji-dir))

;; Next, we need to look up our emoji data we just grabbed. 
;; Now, depending on whether you want better performance and less risk 
;; of conflicts, you might want to require fully qualified emoji names, 
;; at the expense of some user inconvenience.  
;; This function makes that configurable
(def unqualified-emoji-names? true)

(defn find-emoji-info
  "Looks up the emoji info for an emoji token.
  If unqualified names are enabled in the settings, will
  sift through all packs."
  [emoji-token]
  (let [[pack name] (str/split emoji-token #"\." 2)]
    (->
     (if unqualified-emoji-names?
       (reduce (fn [acc [_ emote-pack]]
                 (concat acc (filter #(= emoji-token (:name %)) emote-pack)))
               []
               emoji)
       (filter #(= name (:name %)) ((keyword pack) emoji)))
     (first)))) ;; **note**: we discard any duplicates here

;; Finally, we can create our handler and renderer
(defn emoji-handler [match]
  (let [emoji-name (second match)
        emote-info (find-emoji-info emoji-name)]
    (if emote-info
      {:type :emoji
       :tag :img
       :attrs {:src (:path emote-info)
               :alt (:name emote-info)
               :style {:max-width "2.5rem"}}}
      {:type :text
       :text (str ":" emoji-name ":")})))

(def ^:private emoji-tokenizer
  (md.parser/normalize-tokenizer
   {:regex #":([a-zA-Z0-9_\-.]+):"
    :handler emoji-handler}))

;; Assuming we're using Tailwind CSS.
;; Otherwise, add appropriate styles in `emoji-handler`
(defn emoji-renderer
  [ctx node]
  (let [params (:attrs node)
        src (:path params)
        alt (:name params)]
    [:img.inline-flex params]))

;; Finally, we can try to offset the performance hit of the emoji lookup by adding some
;; asynchrony and timeouts. It's up to you to add spinners, error messages,
;; fallback values etc. to your Hiccup template.
(defn parse-message
  "Parses message's formatting (extended markdown, see docs)
  and returns a rum template"
  [message]
  (async/go
    (try
      (let [parsing-chan (async/go (md.parser/parse
                                     ;; add the emoji tokenizer to text tokenizers
                                    (update md.parser/empty-doc :text-tokenizers conj emoji-tokenizer)
                                    (md/tokenize message)))
            timeout-chan (async/timeout 2000)
            ;; add the renderer to the default renderers map
            renderers (assoc md.transform/default-hiccup-renderers :emoji emoji-renderer)
            [result port] (async/alts! [parsing-chan timeout-chan])]
        (if (= port timeout-chan)
          {:error "Parsing timed out"}
          (md.transform/->hiccup renderers result)))
      (catch Exception e
        {:error (str "Error parsing message: " (.getMessage e))}))))

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
