# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebooks&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

* _Focus on data_: parsing yields an AST ([à la Pandoc](https://snapshots.nextjournal.com/markdown/build/9c419d0158436ab7f9f24b8d7b875a9f514c38e7/index.html#/notebooks/pandoc.clj)) of clojure nested data representing a structured document.
* _Cross Platform_: our parser folds tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it). We're reaching out to the JVM by means of [Graal's Polyglot Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context) while targeting clojurescript comes for free. By using a [common codebase](https://github.com/nextjournal/markdown/blob/ae2a2f0b6d7bdc6231f5d088ee559178b55c97f4/src/js/markdown.js) we can gurantee™️ that parsing server- or client-side leads to the same results.
* _Configurable Hiccup conversion_: a set of convenience functions for transforming parsed data into hiccup, allowing to change the representation of each markdown node.

## Flavour

We adhere to [CommonMark Spec](https://spec.commonmark.org/0.30/) and also comply with extensions from [Github flavoured Markdown](https://github.github.com/gfm/#what-is-github-flavored-markdown-). We additionally parse $\LaTeX$ formulas delimited by a single dollar sign `$` for inline mode or by a double dollar sign `$$` for display mode.

## Usage

```clojure
(ns hello-markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))
```

This library does essentially one thing:

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

and just incidentally, helps you transform markdown data to hiccup.

```clojure
(md.transform/->hiccup data)
;; =>
;; [:div
;;  [:h3 {:id "%F0%9F%91%8B%F0%9F%8F%BB%20Hello%20Markdown"} "👋🏻 Hello Markdown"]
;;  [:ul [:li [:p "this " [:em "looks"]]] [:li [:p "something " [:s "unusual"] " " [:strong "familiar"]]]]
;;  [:hr]]
```

We've built hiccup transformation in for convenience, but the same approach can be used to target [more formats](https://snapshots.nextjournal.com/markdown/build/9c419d0158436ab7f9f24b8d7b875a9f514c38e7/index.html#/notebooks/pandoc.clj).

This library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk) where it is used for handling _literate_ fragments. As such, markdown data is natively rendered in notebooks 

```clojure
^{:nextjournal.clerk/viewer {:transform-fn nextjournal.clerk.viewer/with-md-viewer}}
data
```

The transformation of each single markdown node can be specified like this

```clojure
^{:nextjournal.clerk/viewer :html}
(md.transform/->hiccup
 (assoc md.transform/default-hiccup-renderers
        :text (fn [_ctx node] [:span {:style {:color "teal"}} (:text node)])
        :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "-1.6rem"}}])
        :ruler (constantly [:hr {:style {:border "2px dashed teal"}}]))
 data)
```

## Extensibility

We added a tiny layer for [parsing custom expressions](https://snapshots.nextjournal.com/markdown/build/7f5c1e24aeb3842235bc6175aa55dbd9a96d25d1/index.html#/notebooks/parsing_extensibility.clj) at the level of text leaf nodes on top of the base tokenization from markdown-it.
