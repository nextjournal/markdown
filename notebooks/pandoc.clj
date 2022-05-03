;; # 🏳️‍🌈 Pandoc
^{:nextjournal.clerk/visibility :hide-ns}
(ns ^:nextjournal.clerk/no-cache pandoc
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

;; By transforming our markdown data format into [Pandoc](https://pandoc.org)'s internal AST, we can achieve conversion to potentially all of
;; their supported formats (scroll the list to see all):
^{::clerk/visibility :hide}
(clerk/html
 (into [:ul {:style {:height "200px"}}]
       (map (partial vector :li))
       (str/split-lines (:out (shell/sh "pandoc" "--list-output-formats")))))

;; Let's define a map of tranform functions indexed by (a ftm subset of) our markdown types
^{::clerk/visibility :hide}
(declare ->pandoc)
(def type->transform
  {:doc (fn [{:keys [content]}]
          {:blocks (into [] (map ->pandoc) content)
           :pandoc-api-version [1 22]
           :meta {}})

   :heading (fn [{:keys [content heading-level]}] {:t "Header" :c [heading-level ["id" [] []] (map ->pandoc content)]})
   :paragraph (fn [{:keys [content]}] {:t "Para" :c (map ->pandoc content)})
   :code (fn [{:as node :keys [language]}] {:t "CodeBlock" :c [["" [language "code"] []] (md.transform/->text node)]})
   :block-formula (fn [{:keys [text]}] {:t "Para" :c [{:t "Math" :c [{:t "DisplayMath"} text]}]})

   :em (fn [{:keys [content]}] {:t "Emph" :c (map ->pandoc content)})
   :strong (fn [{:keys [content]}] {:t "Strong" :c (map ->pandoc content)})
   :strikethrough (fn [{:keys [content]}] {:t "Strikeout" :c (map ->pandoc content)})
   :link (fn [{:keys [attrs content]}] {:t "Link" :c [["" [] []] (map ->pandoc content) [(:href attrs) ""]]})

   :text (fn [{:keys [text]}] {:t "Str" :c text})})

;; along with a dispatch function
(defn ->pandoc
  ([node] (->pandoc type->transform node))
  ([ctx {:as node :keys [type]}]
   (if-some [xf (get ctx type)]
     (xf node)
     (throw (ex-info (str "Not implemented: '" type ".") node)))))

;; and a conversion function.
(defn pandoc-convert [format pandoc-data]
  (let [{:keys [out err]}
        (shell/sh "pandoc" "-f" "json" "-t" format
                  :in (json/write-str pandoc-data))]
    (or (not-empty err) out)))

;; Now take a piece of `markdown-text`

^{::clerk/visibility :hide ::clerk/viewer {:transform-fn #(v/html [:pre @(::clerk/var-from-def %)])}}
(def markdown-text "# Hello

## Sub _section_


```python
1 + 1
```

With a block formula:

$$F(t) = \\int_{t_0}^t \\phi(x)dx$$

this _is_ a ~~boring~~ **awesome** [example](https://some/path)!")

;; once we've turned it into pandoc json AST
(def pandoc-data (-> markdown-text md/parse ->pandoc))

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(def verbatim (partial clerk/with-viewer {:transform-fn #(v/html [:pre %])}))

;; then we can convert it to whatever supported format. Say **org mode**
(verbatim (pandoc-convert "org" pandoc-data))

;; or **reStructuredText**
(verbatim (pandoc-convert "rst" pandoc-data))

;; or even to a **Jupyter notebook**.
(verbatim (pandoc-convert "ipynb" pandoc-data))

;; If you're in that exotic party mode, you can also go for a pdf
(shell/sh "pandoc" "--pdf-engine=tectonic" "-f" "json" "-t" "pdf" "-o" "notebooks/demo.pdf"
          :in (json/write-str pandoc-data))

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
 (clerk/serve! {:port 8888})

 (json/read-str
  (:out
   (shell/sh "pandoc" "-f" "markdown" "-t" "json" :in markdown-text))))
