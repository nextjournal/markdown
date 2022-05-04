# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

1. Cross-Platform: our parser folds tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it). We're reaching out to the JVM by means of [Graal's Polyglot Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context) while targeting clojurescript is for free.
2. A focus on data: parsing yields an AST (à la [Pandoc](https://pandoc.org/using-the-pandoc-api.html#pandocs-architecture)) of clojure nested data representing a structured document.
3. Hiccup conversion: a set of convenience functions for transforming parsed data into markup, allowing to configure the tranformation of each markdown node.
4. Extensibility: a tiny layer for [parsing custom expressions](https://snapshots.nextjournal.com/markdown/build/f1de3e445db8ad0288d787454420867f96d2c323/index.html#/notebooks/parsing_extensibility.clj) at the level of Markdown text leaf nodes.

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

We've built hiccup transformation in for convenience but nothing prevents you from targeting more formats: [Pandoc is definitely our source of inspiration here](https://snapshots.nextjournal.com/markdown/build/fbb3a3b91c07a69102364b7955d0a042be2905f1/index.html#/notebooks/pandoc.clj).

## Clerk

This library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk) where it is used for handling the textual parts in notebooks.
As such, markdown data natively renders well in a notebook

```clojure
^{::clerk/viewer {:transform-fn nextjournal.clerk.viewer/with-md-viewer}}
data
```

## Customizing Hiccup Transform

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

In [this notebook](https://snapshots.nextjournal.com/markdown/build/f1de3e445db8ad0288d787454420867f96d2c323/index.html#/notebooks/parsing_extensibility.clj) 
we show how to extend parsing of text nodes.
