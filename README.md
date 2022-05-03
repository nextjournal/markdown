# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

1. Cross-Platform: our parser folds tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it). We're reaching out to the JVM by means of [Graal's Polyglot Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context) while targeting clojurescript is for free.
2. A focus on data: parsing yields an AST (à la [Pandoc](https://pandoc.org/using-the-pandoc-api.html#pandocs-architecture)) of clojure nested data representing a structured document.
3. Hiccup conversion: a set of convenience functions for transforming parsed data into markup, allowing to configure the tranformation of each markdown node.
4. Extensibility (experimental): a tiny layer for parsing custom expressions at the level of Markdown text leaf nodes.

## Usage

```clojure
(require '[nextjournal.markdown :as md]
         '[nextjournal.markdown.transform :as md.transform])
```

Parse a string

```clojure
(def data (md/parse "### 👋🏻 Hello Markdown
* this _looks_
* something ~~unusual~~ **familiar**
"))
```

Transform markdown data to hiccup.

```clojure
(def hiccup (md.transform/->hiccup data))
```

We've built-in hiccup transformation for convenience but nothing prevents us from [targeting more formats](https://snapshots.nextjournal.com/markdown/build/4f118bd0d6be42dc76c6b894070e6825da0aaf6c/index.html#/notebooks/pandoc.clj).

```clojure
(nextjournal.clerk/html hiccup)
```

Since this library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk), no surprise it can natively render our Markdown data 

```clojure
(nextjournal.clerk.viewer/with-md-viewer data)
```

## Custom Rendering

The transformation of each markdown node can be specified like this

```clojure
(nextjournal.clerk/html
 (md.transform/->hiccup
  (assoc md.transform/default-hiccup-renderers
         :text (fn [_ctx node] [:span {:style {:color "teal"}} (:text node)])
         :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "-1.6rem"}}])
         :ruler (constantly [:hr {:style {:border "3px solid magenta"}}]))
  data))
```

## Custom Parsing

In [this notebook](https://snapshots.nextjournal.com/markdown/build/19d2ff554058ea895772668b6293e772eda2228a/index.html#/notebooks/parsing_extensibility.clj) 
we show how to extend parsing of text nodes.
