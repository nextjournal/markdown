;; # ✏️ Nextjournal Markdown Live Demo
(ns try
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]))
;; _Edit markdown text, see parsed AST and transformed hiccup live. Preview how Clerk renders it._
^{::clerk/width :full
  ::clerk/visibility {:code :fold}
  ::clerk/viewer {:render-fn '(fn [_]
                                (let [init-text "# 👋 Hello Markdown

```clojure id=xxyyzzww
(reduce + [1 2 3])
```
## Subsection
- [x] type **some**
- [x] ~~nasty~~
- [ ] _stuff_ here"
                                      text->state (fn [text]
                                                    (let [parsed (md/parse text)]
                                                      {:parsed parsed
                                                       :hiccup (md.transform/->hiccup md.demo/renderers parsed)}))
                                      !state (nextjournal.clerk.render.hooks/use-state (text->state init-text))]
                                  [:div.grid.grid-cols-2.m-10
                                   [:div.m-2.p-2.text-xl.border-2.overflow-y-scroll.bg-slate-100 {:style {:height "20rem"}}
                                    [md.demo/editor {:doc init-text :on-change #(reset! !state (text->state %)) :lang :markdown}]]
                                   [:div.m-2.p-2.font-medium.overflow-y-scroll {:style {:height "20rem"}}
                                    [md.demo/inspect-expanded (:parsed @!state)]]
                                   [:div.m-2.p-2.overflow-x-scroll
                                    [md.demo/inspect-expanded (:hiccup @!state)]]
                                   [:div.m-2.p-2.bg-slate-50.viewer-markdown
                                    [nextjournal.clerk.viewer/html (:hiccup @!state)]]]))}}
(Object.)


(comment
  (clerk/serve! {:port 8023}))
