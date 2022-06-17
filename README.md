# nextjournal markdown

[![Clojars Project](https://img.shields.io/clojars/v/io.github.nextjournal/markdown.svg)](https://clojars.org/io.github.nextjournal/markdown) [![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebooks&color=rgb(155,187,157))](https://nextjournal.github.io/markdown/#/README.md)

A cross-platform clojure library for [Markdown](https://en.wikipedia.org/wiki/Markdown) parsing and transformation.


🚧 _ALPHA_ status, subject to frequent change. For a richer reading experience [read this readme as a clerk notebook](https://nextjournal.github.io/markdown/#/README.md).

## Features

* _Focus on data_: parsing yields an AST ([à la Pandoc](https://nextjournal.github.io/markdown/#/notebooks/pandoc.clj)) of nested data representing a structured document.
* _Cross Platform_: clojurescript native, we target the JVM using [Graal's Polyglot Library](https://www.graalvm.org/22.1/reference-manual/js/JavaInteroperability/#polyglot-context).
* _Configurable [Hiccup](https://github.com/weavejester/hiccup) conversion_.

## Flavor

By building on top of [markdown-it](https://github.com/markdown-it/markdown-it), we adhere to [CommonMark Spec](https://spec.commonmark.org/0.30/) and also comply with extensions from [Github flavoured Markdown](https://github.github.com/gfm). Additionally, we parse $\LaTeX$ formulas (delimited by a $ for inline rendering or $$ for display mode).

For more details you might have a look at [the set of plugins](https://github.com/nextjournal/markdown/blob/main/src/js/markdown.js) we're using.

## Usage

```clojure
(ns hello-markdown
  (:require [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))
```

Parsing markdown into an AST:

```clojure
(def data 
  (md/parse "> et tout autour, la longue cohorte de ses personnages, avec leur histoire, leur passé, leurs légendes:
> - Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga
> - La cantatrice exilée de Russie suivant Schönberg à Amsterdam
> - Le petit chat sourd aux yeux vairons vivant au dernier étage
> - ...

**Georges Perec**, _La Vie mode d'emploi_.

---
"))
```
    ;; =>
    {:type :doc,
     :content [{:type :blockquote,
                :content [{:type :paragraph,
                           :content [{:type :text,
                                      :text "et tout autour, la longue cohorte de ses personnage, avec leur histoire, leur passé, leurs légendes:"}]}
                          {:type :bullet-list,
                           :content [{:type :list-item,
                                      :content [{:type :plain,
                                                 :content [{:type :text,
                                                            :text "Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga"}]}]}
                                     {:type :list-item,
                                      :content [{:type :plain,
                                                 :content [{:type :text,
                                                            :text "La cantatrice exilée de Russie suivant Schönberg à Amsterdam"}]}]}
                                     {:type :list-item,
                                      :content [{:type :plain,
                                                 :content [{:type :text,
                                                            :text "Le petit chat sourd aux yeux vairons vivant au dernier étage"}]}]}]}]}
               {:type :paragraph,
                :content [{:type :strong, :content [{:type :text, :text "Georges Perec"}]}
                          {:type :text, :text ", "}
                          {:type :em, :content [{:type :text, :text "La Vie mode d'emploi"}]}
                          {:type :text, :text "."}]}
               {:type :ruler}]}

and transform that AST into `hiccup` syntax.

```clojure
(md.transform/->hiccup data)
```
    ;; =>
    [:div
     [:blockquote
      [:p "et tout autour, la longue cohorte de ses personnage, avec leur histoire, leur passé, leurs légendes:"]
      [:ul
       [:li [:<> "Pélage vainqueur d'Alkhamah se faisant couronner à Covadonga"]]
       [:li [:<> "La cantatrice exilée de Russie suivant Schönberg à Amsterdam"]]
       [:li [:<> "Le petit chat sourd aux yeux vairons vivant au dernier étage"]]]]
     [:p [:strong "Georges Perec"] ", " [:em "La Vie mode d'emploi"] "."]
     [:hr]]

We've built hiccup transformation in for convenience, but the same approach can be used to target [more formats](https://nextjournal.github.io/markdown/#/notebooks/pandoc.clj).

This library is one of the building blocks of [Clerk](https://github.com/nextjournal/clerk) where it is used for rendering _literate fragments_.

```clojure
^{:nextjournal.clerk/viewer :markdown}
data
```

The transformation of markdown node types can be customised like this:

```clojure
^{:nextjournal.clerk/viewer :html}
(md.transform/->hiccup
 (assoc md.transform/default-hiccup-renderers
        ;; :doc specify a custom container for the whole doc
        :doc (partial md.transform/into-markup [:div.viewer-markdown])
        ;; :text is funkier when it's zinc toned 
        :text (fn [_ctx node] [:span {:style {:color "#71717a"}} (:text node)])
        ;; :plain fragments might be nice, but paragraphs help when no reagent is at hand
        :plain (partial md.transform/into-markup [:p {:style {:margin-top "-1.2rem"}}])
        ;; :ruler gets to be funky, too
        :ruler (constantly [:hr {:style {:border "2px dashed #71717a"}}]))
 data)
```

## Extensibility

We added minimal tooling for [extending markdown expressions](https://nextjournal.github.io/markdown/#/notebooks/parsing_extensibility.clj).
