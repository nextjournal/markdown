# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

1. Cross-Platform: our parser folds tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it). We're reaching out to the JVM by means of [Graal's Polyglot Engine](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context) while targeting clojurescript is for free.
2. A focus on data: parsing yields an AST (à la [Pandoc](https://pandoc.org/using-the-pandoc-api.html#pandocs-architecture)) of clojure nested data representing a structured document.
3. Hiccup conversion: a set of convenience functions for transforming parsed data into markup. We also allow to configure the tranformation of each markdown node.

## Usage

```clojure
(require '[nextjournal.markdown :as md]
         '[nextjournal.markdown.transform :as md.transform])
```

Parse a string

```clojure
(def data (md/parse "# 👋🏻 Hello Markdown

* this _looks_
* something ~~unusual~~ **familiar**
"))
```

Transform markdown data to hiccup.

```clojure
(def hiccup (md.transform/->hiccup data))
```

We've built-in hiccup transformation for convenience but nothing prevents us from [targeting more formats]().



Check the [clerk notebook](https://nextjournal.github.io/markdown) to see this in action.

```clojure
(nextjournal.clerk/html hiccup)
```

## Custom Rendering

The transformation of each markdown node can be specified like this

```clojure
(nextjournal.clerk/html
 (md.transform/->hiccup
  (assoc md.transform/default-hiccup-renderers
         :text (fn [_ctx node] [:span {:style {:color "teal"}} (:text node)])
         :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "2rem"}}])
         :ruler (constantly [:hr {:style {:border "3px solid magenta"}}]))
  data))
```

## Custom Parsing
