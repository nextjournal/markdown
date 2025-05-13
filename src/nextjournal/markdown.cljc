(ns nextjournal.markdown
  "Markdown as data"
  (:require
   [nextjournal.markdown.impl :as impl]
   [nextjournal.markdown.impl.frontmatter :as frontmatter]
   [nextjournal.markdown.transform :as markdown.transform]
   [nextjournal.markdown.utils :as u]))

(def empty-doc u/empty-doc)

(defn parse*
  "Turns a markdown string into an AST of nested clojure data.
  Allows to parse multiple strings into the same document
  e.g. `(-> u/empty-doc (parse* text-1) (parse* text-2))`."
  ([markdown-text] (parse* empty-doc markdown-text))
  ([ctx markdown-text]
   (-> ctx
       (update :text-tokenizers (partial map u/normalize-tokenizer))
       (impl/parse markdown-text))))

(defn parse
  "Turns a markdown string into an AST of nested clojure data.

  Accept options:
    - `:text-tokenizers` to customize parsing of text in leaf nodes (see https://nextjournal.github.io/markdown/notebooks/parsing_extensibility).
  "
  ([markdown-text] (parse empty-doc markdown-text))
  ([ctx markdown-text]
   (-> (parse* ctx markdown-text)
       (dissoc :text-tokenizers
               :text->id+emoji-fn
               ::impl/footnote-offset
               ::impl/id->index
               ::impl/label->footnote-ref
               ::impl/path
               ::impl/root))))

(defn parse-frontmatter
  "Returns a map of :frontmatter and raw :markdown-text.
  The :frontmatter key contains :type (:yaml, :edn, :multimarkdown) and :value.
  The :multimarkdown format is documented here: https://fletcher.github.io/MultiMarkdown-4/metadata
  To parse :yaml you need to provide :yaml-parse-fn (e.g. `clj-yaml.core/parse-string`)"
  ([markdown-text] (parse-frontmatter nil markdown-text))
  ([opts markdown-text]
   (let [[frontmatter markdown-text] (frontmatter/parse-frontmatter markdown-text opts)]
     {:markdown-text markdown-text
      :frontmatter frontmatter})))

(comment
  (-> u/empty-doc
      (parse* "# title
* one
* two
  ")
      (parse* "new par")
      (parse* "new par")))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup markdown.transform/default-hiccup-renderers markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.transform/->hiccup ctx))))

(comment
  (parse "# 🎱 Hello")

  (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  (-> (nextjournal.markdown.graaljs/parse "[alt](https://this/is/a.link)") :content first :content first)
  (-> (parse "[alt](https://this/is/a.link)") :content first :content first)

  (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  (->> (with-out-str
         (time (dotimes [_ 100] (parse (slurp "notebooks/reference.md")))))
      (re-find #"\d+.\d+")
       parse-double
       ((fn [d] (/ d 100))))

  (->hiccup "# Hello Markdown

* What's _going_ on?
")

  (->hiccup
   (assoc markdown.transform/default-hiccup-renderers
          :heading (fn [ctx node]
                     [:h1.some-extra.class
                      (markdown.transform/into-markup [:span.some-other-class] ctx node)]))
   "# Hello Markdown
* What's _going_ on?
")

  ;; launch shadow cljs repl
  (shadow.cljs.devtools.api/repl :browser-test))
