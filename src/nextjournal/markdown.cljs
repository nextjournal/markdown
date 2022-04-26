(ns nextjournal.markdown
  (:require ["/js/markdown" :as md]
            ["markdown-it/lib/token" :as Token]
            ["katex" :as katex]
            [applied-science.js-interop :as j]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform]))

(extend-type Token
  ILookup
  (-lookup [this key] (j/get this key)))

(def tokenize md/parse)

(defn parse
  "Turns a markdown string into a nested clojure structure."
  [markdown-text] (-> markdown-text tokenize markdown.parser/parse))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup markdown.transform/default-hiccup-renderers markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.transform/->hiccup ctx))))

(comment
  ;; asks markdown-it parser for a sequence of tokens
  (js/console.log
   (tokenize "# Title
- [ ] one
- [x] two
"))

  ;; parse markdonw into an "AST" of nodes
  (parse "# Hello Markdown

- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  ;; default render
  (->hiccup "# Hello Markdown

What's _going_ on?
[[TOC]]")

  ;; custom overrides by type
  (->hiccup
   (assoc markdown.transform/default-hiccup-renderers
          :heading (fn [ctx node]
                     [:h1.some-extra.class
                      (markdown.transform/into-markup [:span.some-other-class] ctx node)]))
   "# Hello Markdown
What's _going_ on?
"))
