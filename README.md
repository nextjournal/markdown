# nextjournal markdown

[![Clojars Project](https://img.shields.io/clojars/v/io.github.nextjournal/markdown.svg)](https://clojars.org/io.github.nextjournal/markdown) [![Notebooks](<https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebooks&color=rgb(155,187,157)>)](https://nextjournal.github.io/markdown/#/README.md)

A cross-platform clojure library for
[Markdown](https://en.wikipedia.org/wiki/Markdown) parsing and transformation.

---

🚧 ALPHA status, subject to frequent change. 🚧

_For a richer reading experience and [read this readme as a clerk notebook](https://nextjournal.github.io/markdown/#/README.md)._

## Features 🦶

- _Focus on data_: parsing yields an AST ([à la Pandoc](https://nextjournal.github.io/markdown/#/notebooks/pandoc.clj)) of nested data representing a structured document.
- _Cross Platform_: clojurescript native with bindings to the JVM using [Graal's
  Polyglot
  Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context).
- _Configurable [Hiccup](https://github.com/weavejester/hiccup) conversion_:
  Uses [hiccup](https://github.com/weavejester/hiccup) for custom markdown
  representation.

## Flavor 👅

By building on top of [markdown-it](https://github.com/markdown-it/markdown-it), we adhere to [CommonMark Spec](https://spec.commonmark.org/0.30/) and also
comply with extensions from [Github flavoured
Markdown](https://github.github.com/gfm/#what-is-github-flavored-markdown-).
Additionally, we parse $\LaTeX$ formulas (with $ for inline latex or $$ for equation
display).

## Usage 🔩

```clojure
(ns hello-markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))
```

Parsing markdown into an AST:

```clojure
(def data (md/parse "### 👋🏻 Hello Markdown
* this _looks_
* something ~~unusual~~ **familiar**
---
"))
;; =>
;; {:type :doc
;;  :toc {:type :toc :children [...]}
;;  :title "👋🏻 Hello Markdown"
;;  :content [{:type :heading
;;             :content [{:type :text :text "👋🏻 Hello Markdown"}] :heading-level 3}
;;            {:type :bullet-list
;;             :content [{:type :list-item
;;                        :content [{:type :paragraph
;;                                   :content [{:type :text :text "this "}
;;                                             {:type :em :content [{:type :text :text "looks"}]}]}]}
;;                       {:type :list-item
;;                        :content [{:type :paragraph
;;                                   :content [{:type :text :text "something "}
;;                                             {:type :strikethrough, :content [{:type :text :text "unusual"}]}
;;                                             {:type :text :text " "}
;;                                             {:type :strong, :content [{:type :text :text "familiar"}]}]}]}]}
;;            {:type :ruler}]}
```

and transform that AST into `hiccup` syntax.

```clojure
(md.transform/->hiccup data)
;; =>
;; [:div
;;  [:h3 {:id "%F0%9F%91%8B%F0%9F%8F%BB%20Hello%20Markdown"} "👋🏻 Hello Markdown"]
;;  [:ul [:li [:p "this " [:em "looks"]]] [:li [:p "something " [:s "unusual"] " " [:strong "familiar"]]]]
;;  [:hr]]
```

We've built hiccup transformation in for convenience, but the same approach can be used to target [more formats](https://nextjournal.github.io/markdown/#/notebooks/pandoc.clj).

This library is one of the building blocks of
[Clerk](https://github.com/nextjournal/clerk) where it is used for rendering
_literate fragments_.

```clojure
^{:nextjournal.clerk/viewer :markdown}
data
;; If you don't see "👋 Hello Markdown" below,
;; consider loading this README as a notebook!
;; https://nextjournal.github.io/markdown/#/README.md
```

The transformation of markdown node types can be specified like this:

```clojure
^{:nextjournal.clerk/viewer :html}
(md.transform/->hiccup
 (assoc md.transform/default-hiccup-renderers
        ;; :text is funkier when it's teal
        :text (fn [_ctx node] [:span {:style {:color "teal"}} (:text node)])
        ;; :paragraphs linebreaks are too damn high 🐜
        :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "-1.6rem"}}])
        ;; :ruler gets to be funky, too
        :ruler (constantly [:hr {:style {:border "2px dashed teal"}}]))
 data)
```

## Extensibility 🐢

We added minimal tooling for [extending markdown
expressions](https://nextjournal.github.io/markdown/#/notebooks/parsing_extensibility.clj).
See the linked notebook for an extended example.
