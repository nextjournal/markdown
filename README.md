# nextjournal markdown 
[![Notebooks](https://img.shields.io/static/v1?logo=plex&logoColor=rgb(155,187,157)&label=clerk&message=notebook&color=rgb(155,187,157))](https://nextjournal.github.io/markdown)


A semi-extensible cross-platform clojure/script parser for Markdown suitable for multi-format conversions.

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

Check the clerk notebooks to see it in action.

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

## Extended Parsing

...
