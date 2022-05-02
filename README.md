# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)

A cross-platform clojure library for Markdown parsing and transformation.

## Features

1. Cross-Platform: our parser folds a collection of tokens emitted by the js library [markdown-it](https://github.com/markdown-it/markdown-it), that is we're reaching the JVM by means of [Graal's Polyglot Engine](), while getting clojurescript for free.
2. A focus on data: parsing yields an AST (à la [Pandoc](https://pandoc.org/using-the-pandoc-api.html#pandocs-architecture)) of clojure nested data representing a structured document.
3. Hiccup conversion: a set of convenience functions for transforming parsed data into markup. We do allow to configure the tranformation of each markdown node.

## Usage

```clojure
(require '[nextjournal.markdown :as md]
         '[nextjournal.markdown.transform :as md.transform])
```

Parse a string

```clojure
(def data (md/parse "# 👋🏻 Hello Markdown
* so
* so
---


and another paragraph.
"))
```

Transform the data

```clojure
(def hiccup (md.transform/->hiccup data))
```

Check the [clerk notebook](https://nextjournal.github.io/markdown) to see this in action.

```clojure
(nextjournal.clerk/html hiccup)
```

## Custom Rendering

```clojure
(nextjournal.clerk/html
 (md.transform/->hiccup
  (assoc md.transform/default-hiccup-renderers
         :text (fn [ctx node] [:span {:style {:color "teal"}} (:text node)])
         :paragraph (partial md.transform/into-markup [:p {:style {:margin-top "2rem"}}])
         :ruler (fn [ctx node] [:hr {:style {:border "3px solid magenta"}}]))
  data))
```

## Custom Parsing

...
