# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://snapshots.nextjournal.com/markdown/build/9c419d0158436ab7f9f24b8d7b875a9f514c38e7/index.html#/README.md)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

- _Cross Platform_: our parser folds tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it). We're reaching out to the JVM by means of [Graal's Polyglot Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context) while targeting clojurescript is for free.
- _Focus on data_: parsing yields an AST (à la [Pandoc](https://pandoc.org/using-the-pandoc-api.html#pandocs-architecture)) of clojure nested data representing a structured document.
- _Configurable Hiccup conversion_: a set of convenience functions for transforming parsed data into markup, allowing to configure the tranformation of each markdown node.
- _Extensibility_: a tiny layer for [parsing custom expressions](https://snapshots.nextjournal.com/markdown/build/7f5c1e24aeb3842235bc6175aa55dbd9a96d25d1/index.html#/notebooks/parsing_extensibility.clj) at the level of text leaf nodes.

## Usage

```clojure
(require '[nextjournal.clerk :as clerk]
         '[nextjournal.markdown :as md]
         '[nextjournal.markdown.transform :as md.transform])
```

This library does essentially one thing:

```clojure
(def data (md/parse "### 👋🏻 Hello Markdown
* this _looks_
* something ~~unusual~~ **familiar**
---
"))
```

and just incidentally, helps you transform markdown data to hiccup.

```clojure
(md.transform/->hiccup data)
```

We've built hiccup transformation in for convenience but nothing prevents you from targeting more formats: [Pandoc is definitely our source of inspiration here](https://snapshots.nextjournal.com/markdown/build/9c419d0158436ab7f9f24b8d7b875a9f514c38e7/index.html#/notebooks/pandoc.clj).

This library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk) where it is used for handling the textual parts in notebooks.
As such, markdown data natively renders well in a notebook

```clojure
^{::clerk/viewer {:transform-fn nextjournal.clerk.viewer/with-md-viewer}}
data
```

The transformation of each single markdown node can be specified like this

```clojure
^{::clerk/viewer :html}
(md.transform/->hiccup
 (assoc md.transform/default-hiccup-renderers
        :text (fn [_ctx node] [:span {:style {:color "teal"}} (:text node)])
        :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "-1.6rem"}}])
        :ruler (constantly [:hr {:style {:border "2px dashed teal"}}]))
 data)
```

## Customizing Parsing

In [this notebook](https://snapshots.nextjournal.com/markdown/build/9c419d0158436ab7f9f24b8d7b875a9f514c38e7/index.html#/notebooks/parsing_extensibility.clj) 
we show how to extend parsing of text nodes.
