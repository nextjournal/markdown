(ns nextjournal.markdown
  "Markdown as data"
  (:require [nextjournal.markdown.parser.impl :as impl]
            [nextjournal.markdown.parser.impl.utils :as u]
            [nextjournal.markdown.transform :as markdown.transform]))


;; TODO: remove fixme (shadow compile warnings)
(defn tokenize [_] [])

(defn parse
  "Turns a markdown string into a nested clojure structure."
  ([markdown-text] (impl/parse markdown-text))
  ([doc markdown-text]
   (-> doc
       (impl/parse markdown-text)
       (dissoc :text-tokenizers
               :nextjournal.markdown.parser.impl/id->index
               :nextjournal.markdown.parser.impl/path))))

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

  (with-new-parser
   (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
"))

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
  (shadow.cljs.devtools.api/repl :sci))
