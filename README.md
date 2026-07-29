# nextjournal markdown

[![Clojars Project](https://img.shields.io/clojars/v/io.github.nextjournal/markdown.svg)](https://clojars.org/io.github.nextjournal/markdown) [![Notebooks](https://img.shields.io/static/v1?label=clerk&message=notebooks&color=rgb(155,187,157))](https://nextjournal.github.io/markdown) [![bb built-in](https://raw.githubusercontent.com/babashka/babashka/master/logo/built-in-badge.svg)](https://book.babashka.org#badges)

A cross-platform clojure library for [Markdown](https://en.wikipedia.org/wiki/Markdown) parsing and transformation.

🚧 For a richer reading experience [read this readme as a clerk notebook](https://nextjournal.github.io/markdown/README).

## Features

* _Focus on data_: parsing yields an AST ([à la Pandoc](https://nextjournal.github.io/markdown/notebooks/pandoc)) of nested data representing a structured document.
* _Cross Platform_: using [commonmark-java](https://github.com/commonmark/commonmark-java) on the JVM and [markdown-it](https://github.com/markdown-it/markdown-it) for ClojureScript.
* _Configurable [Hiccup](https://github.com/weavejester/hiccup) conversion_.

## Try

[Try it online](https://nextjournal.github.io/markdown/notebooks/try).

## Flavor

We adhere to [CommonMark Spec](https://spec.commonmark.org/0.30/) and comply with extensions from [Github flavoured Markdown](https://github.github.com/gfm). Additionally, we parse $\LaTeX$ formulas (delimited by a $ for inline rendering or $$ for display mode).

## Usage

```clojure
(ns hello-markdown
  (:require [nextjournal.markdown :as md]))
```

Parsing markdown into an AST:

```clojure
(def data
  (md/parse "> et tout autour, la longue cohorte de ses personnages, avec leur histoire, leur passé, leurs légendes:
> 1. Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga
> 2. La cantatrice exilée de Russie suivant Schönberg à Amsterdam
> 3. Le petit chat sourd aux yeux vairons vivant au dernier étage
> 4. ...

**Georges Perec**, _La Vie mode d'emploi_.

---
"))

#_#_=>
{:type :doc
 :content
 [{:type :blockquote,
   :content
   [{:type :paragraph,
     :content
     [{:type :text,
       :text
       "et tout autour, la longue cohorte de ses personnages, avec leur histoire, leur passé, leurs légendes:"}]}
    {:type :numbered-list,
     :content
     [{:type :list-item,
       :content
       [{:type :plain,
         :content
         [{:type :text,
           :text
           "Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga"}]}]}
      {:type :list-item,
       :content
       [{:type :plain,
         :content
         [{:type :text,
           :text
           "La cantatrice exilée de Russie suivant Schönberg à Amsterdam"}]}]}
      {:type :list-item,
       :content
       [{:type :plain,
         :content
         [{:type :text,
           :text
           "Le petit chat sourd aux yeux vairons vivant au dernier étage"}]}]}
      {:type :list-item,
       :content
       [{:type :plain, :content [{:type :text, :text "..."}]}]}]}]}
  {:type :paragraph,
   :content
   [{:type :strong, :content [{:type :text, :text "Georges Perec"}]}
    {:type :text, :text ", "}
    {:type :em, :content [{:type :text, :text "La Vie mode d'emploi"}]}
    {:type :text, :text "."}]}
  {:type :ruler}]}
```

## Hiccup rendering

To transform the above AST into `hiccup` syntax:

```clojure
(md/->hiccup data)
#_#_=>
[:div
 [:blockquote
  [:p
   "et tout autour, la longue cohorte de ses personnages, avec leur histoire, leur passé, leurs légendes:"]
  [:ol
   [:li
    ("Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga")]
   [:li
    ("La cantatrice exilée de Russie suivant Schönberg à Amsterdam")]
   [:li
    ("Le petit chat sourd aux yeux vairons vivant au dernier étage")]
   [:li ("...")]]]
 [:p [:strong "Georges Perec"] ", " [:em "La Vie mode d'emploi"] "."]
 [:hr]]
```

The transformation of markdown node types can be customized like this:

```clojure
^{:nextjournal.clerk/viewer 'nextjournal.clerk.viewer/html-viewer}
(md/->hiccup
 (assoc md/default-hiccup-renderers
        ;; :doc specify a custom container for the whole doc
        :doc (partial md/into-hiccup [:div.viewer-markdown])
        ;; :text is funkier when it's zinc toned
        :text (fn [_ctx node] [:span {:style {:color "#71717a"}} (md/node->text node)])
        ;; :ruler gets to be funky, too
        :ruler (constantly [:hr {:style {:border "2px dashed #71717a"}}]))
 data)
```

### Parse options

The following options are available that affect parsing:

- `:disable-inline-formulas true`: disable the parsing of inline formulas

### HTML blocks and HTML inlines

There is no default renderer for `:html-inline` and `:html-block`, since emitting raw strings is left to the consumer. Supply your own:

``` clojure
(require '[hiccup2.core :as hiccup])

(def renderers
  (assoc md/default-hiccup-renderers
         :html-inline (comp hiccup/raw md/node->text)
         :html-block (comp hiccup/raw md/node->text)))

(str (hiccup/html (md/->hiccup renderers "<img src=\"...\"/>")))

#_#_=>
"<div><img src=\"...\"/></div>"
```

Per CommonMark, an HTML block ends at the first blank line, and everything up to that point stays raw. Put a blank line after the opening tag to have the contents parsed as markdown:

``` clojure
(str (hiccup/html (md/->hiccup renderers "<aside>\n\n*emphasis*\n\n</aside>")))

#_#_=>
"<div><aside><p><em>emphasis</em></p></aside></div>"

(str (hiccup/html (md/->hiccup renderers "<aside>\n*emphasis*\n\n</aside>")))

#_#_=>
"<div><aside>\n*emphasis*</aside></div>"
```

## Transforming to other targets

We've built hiccup transformation in for convenience, but the same approach can be used to target [more formats](https://nextjournal.github.io/markdown/notebooks/pandoc).

This library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk) where it is used for rendering _literate fragments_.

```clojure
^{:nextjournal.clerk/viewer 'nextjournal.clerk.viewer/markdown-viewer}
data
```

## Extensibility

We added minimal tooling for [extending markdown expressions](https://nextjournal.github.io/markdown/notebooks/parsing_extensibility).
