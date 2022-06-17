;; # ✏️ Nextjournal Markdown Live Demo
^{:nextjournal.clerk/visibility :hide-ns}
(ns try
  (:require [nextjournal.clerk :as clerk]))
;; _Edit markdown text, see parsed AST and transformed hiccup live. Preview how Clerk renders it._
^{::clerk/width :full
  ::clerk/visibility :fold
  ::clerk/viewer {:render-fn '(fn [_]
                                (v/html
                                 (reagent/with-let
                                  [init-text "# 👋️ Hello Markdown

```clojure id=xxyyzzww
(reduce + [1 2 3])
```
## Subsection
- [x] type **some**
- [x] ~~nasty~~
- [ ] _stuff_ here"
                                   text->state (fn [text] (as-> (md/parse text) parsed {:parsed parsed
                                                                                        :hiccup (md.transform/->hiccup md.demo/renderers parsed)}))
                                   !state (reagent/atom (text->state init-text))
                                   text-update! (fn [text] (reset! !state (text->state text)))]
                                  [:div.grid.grid-cols-2.m-10
                                   [:div.m-2.p-2.text-xl.border-2.overflow-y-scroll.bg-slate-100 {:style {:height "20rem"}} [md.demo/editor {:doc-update text-update! :doc init-text}]]
                                   [:div.m-2.p-2.font-medium.overflow-y-scroll {:style {:height "20rem"}} [md.demo/inspect-expanded (:parsed @!state)]]
                                   [:div.m-2.p-2.overflow-x-scroll [md.demo/inspect-expanded (:hiccup @!state)]]
                                   [:div.m-2.p-2.bg-slate-50.viewer-markdown [v/html (:hiccup @!state)]]])))}}
(Object.)
