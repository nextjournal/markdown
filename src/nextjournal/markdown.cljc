(ns nextjournal.markdown
  "Markdown as data"
  (:require
   [nextjournal.markdown.impl :as impl]
   [nextjournal.markdown.transform :as markdown.transform]
   [nextjournal.markdown.utils :as u]))

(def empty-doc
  "Empty document to be used with `parse*`"
  u/empty-doc)

(defn parse*
  "Turns a markdown string into an AST of nested clojure data.
  Allows to parse multiple strings into the same document
  e.g. `(-> empty-doc (parse* text-1) (parse* text-2))`."
  ([markdown-text] (parse* {} markdown-text))
  ([ctx markdown-text]
   (impl/parse ctx markdown-text)))

(defn parse
  "Turns the given `markdown-string` into an AST of nested clojure data.

  Accepted `opts`:
    - `:text-tokenizers`: customize parsing of text in leaf nodes (see https://nextjournal.github.io/markdown/notebooks/parsing_extensibility).
    - `:disable-inline-formulas`: turn off parsing of $-delimited inline formulas."
  ([markdown-string] (parse {} markdown-string))
  ([opts markdown-string]
   (-> (parse* {:opts opts} markdown-string)
       (dissoc :opts
               ::impl/footnote-offset
               ::impl/id->index
               ::impl/label->footnote-ref
               ::impl/path
               ::impl/root))))

;; Transform

(def default-hiccup-renderers
  "Default map of node type -> hiccup renderers, to be used with `->hiccup`"
  markdown.transform/default-hiccup-renderers)

(defn ->hiccup
  "Turns a markdown string or document node into hiccup. Optionally takes
  `hiccup-renderers` as first argument."
  ([markdown] (->hiccup default-hiccup-renderers markdown))
  ([hiccup-renderers markdown]
   (let [parsed (if (string? markdown)
                  (parse markdown)
                  markdown)]
     (markdown.transform/->hiccup hiccup-renderers parsed))))

(def node->text
  "Convert node into text."
  markdown.transform/->text)

(def into-hiccup
  "Helper function to be used with custom hiccup renderer.
   Takes a hiccup vector, a context and a node, embeds node's `:content` into the hiccup vector markup mapping through `->hiccup`.
   The node itself is not embedded, only its children."
  markdown.transform/into-markup)

(def table-alignment
  "Takes a table-ish node, returns a map suitable for hiccup style attributes with a :text-align property."
  markdown.transform/table-alignment)

(def toc->hiccup
  "Transform a toc node into hiccup data, suitable for using as renderer function in hiccup transform, see [->hiccup](#markdown.transform/toc->hiccup)"
  markdown.transform/toc->hiccup)

(comment
  (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
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
  #_:clj-kondo/ignore
  (shadow.cljs.devtools.api/repl :browser-test))
