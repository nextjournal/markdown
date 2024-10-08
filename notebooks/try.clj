;; # ✏️ Nextjournal Markdown Live Demo
(ns try
  {:nextjournal.clerk/visibility {:code :hide}}
  (:require [nextjournal.clerk :as clerk]))
;; _Edit markdown text, see parsed AST and transformed hiccup live. Preview how Clerk renders it._
^{::clerk/width :full
  ::clerk/visibility {:code :fold}}
(clerk/with-viewer {:render-fn 'nextjournal.markdown.render/try-markdown
                    :require-cljs true}
  "# 👋 Hello Markdown

```clojure id=xxyyzzww
(reduce + [1 2 3])
```
## Subsection
- [x] type **some**
- [x] ~~nasty~~
- [ ] _stuff_ here")

#_(clerk/serve! {:port 8989 :browse true})
