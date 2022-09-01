;; # 🏳️‍🌈 Pandoc
^{:nextjournal.clerk/visibility {:code :hide} :nextjournal.clerk/toc :collapsed}
(ns ^:nextjournal.clerk/no-cache pandoc
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [nextjournal.markdown.transform :as md.transform]))

;; From the [docs](https://pandoc.org/MANUAL.html#description):
;;
;; > Pandoc has a modular design: it consists of a set of readers, which parse text in a given format and produce a native representation of the document (an abstract syntax tree or AST), and a set of writers, which convert this native representation into a target format. Thus, adding an input or output format requires only adding a reader or writer. Users can also run custom pandoc filters to modify the intermediate AST.
;;
;; By transforming our markdown data format to and from [Pandoc](https://pandoc.org)'s internal
;; [AST](https://hackage.haskell.org/package/pandoc-types-1.22.2/docs/Text-Pandoc-Definition.html), we can achieve conversions
;; from and to potentially all of their supported formats. In both directions we're using Pandoc [JSON representation](https://pandoc.org/filters.html)
;; as intermediate format.
;;
;; ## 📤 Export
;;
;; this is a list of supported output formats as of Pandoc v2.18 (API version 1.22.2):
^{::clerk/visibility :hide}
(clerk/html
 [:div.overflow-y-auto.shadow-lg {:style {:height "200px" :width "85%"}}
  (into [:ul]
        (map (partial vector :li))
        (str/split-lines (:out (shell/sh "pandoc" "--list-output-formats"))))])

;; Let's define a map of transform functions indexed by (a subset of) our markdown types
^{::clerk/visibility :hide}
(declare md->pandoc)
(def md-type->transform
  {:doc (fn [{:keys [content]}]
          {:blocks (into [] (map md->pandoc) content)
           :pandoc-api-version [1 22]
           :meta {}})

   :heading (fn [{:keys [content heading-level]}] {:t "Header" :c [heading-level ["id" [] []] (map md->pandoc content)]})
   :paragraph (fn [{:keys [content]}] {:t "Para" :c (map md->pandoc content)})
   :plain (fn [{:keys [content]}] {:t "Plain" :c (map md->pandoc content)})
   :code (fn [{:as node :keys [language]}] {:t "CodeBlock" :c [["" [language "code"] []] (md.transform/->text node)]})
   :block-formula (fn [{:keys [text]}] {:t "Para" :c [{:t "Math" :c [{:t "DisplayMath"} text]}]})

   :em (fn [{:keys [content]}] {:t "Emph" :c (map md->pandoc content)})
   :strong (fn [{:keys [content]}] {:t "Strong" :c (map md->pandoc content)})
   :strikethrough (fn [{:keys [content]}] {:t "Strikeout" :c (map md->pandoc content)})
   :link (fn [{:keys [attrs content]}] {:t "Link" :c [["" [] []] (map md->pandoc content) [(:href attrs) ""]]})

   :list-item (fn [{:keys [content]}] (map md->pandoc content))
   :bullet-list (fn [{:keys [content]}] {:t "BulletList" :c (map md->pandoc content)})

   :text (fn [{:keys [text]}] {:t "Str" :c text})})

;; along with a dispatch function
(defn md->pandoc
  [{:as node :keys [type]}]
  (if-some [xf (get md-type->transform type)]
    (xf node)
    (throw (ex-info (str "Not implemented: '" type "'.") node))))

;; and a conversion function.
(defn pandoc-> [pandoc-data format]
  (let [{:keys [exit out err]} (shell/sh "pandoc" "-f" "json" "-t" format
                                         :in (json/write-str pandoc-data))]
    (if (zero? exit) out err)))

;; Now take a piece of `markdown-text`
^{::clerk/visibility :hide ::clerk/viewer {:transform-fn #(v/html [:pre @(::clerk/var-from-def (v/->value %))])}}
(def markdown-text "# Hello

## Sub _Section_


```python
1 + 1
```

With a block formula:

$$F(t) = \\int_{t_0}^t \\phi(x)dx$$

this _is_ a
* ~~boring~~
* **awesome**
* [example](https://some/path)!")

;; once we've turned it into Pandoc's JSON format
(def pandoc-data (-> markdown-text md/parse md->pandoc))

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(def verbatim (partial clerk/with-viewer {:transform-fn #(v/html [:pre (v/->value %)])}))

;; then we can convert it to whatever supported format. Say **Org Mode**
(-> pandoc-data (pandoc-> "org") verbatim)

;; or **reStructuredText**
(-> pandoc-data (pandoc-> "rst") verbatim)

;; or even to a **Jupyter Notebook**.
(-> pandoc-data (pandoc-> "ipynb") verbatim)

;; If you're in that exotic party mode, you can also go for a pdf
(shell/sh "pandoc" "--pdf-engine=tectonic" "-f" "json" "-t" "pdf" "-o" "notebooks/demo.pdf"
          :in (json/write-str pandoc-data))

;; ## 📥 Import
;;
;; Import works same same. This is a list of supported input formats:
^{::clerk/visibility :hide}
(clerk/html
 [:div.overflow-y-auto.shadow-lg {:style {:height "200px" :width "85%"}}
  (into [:ul]
        (map (partial vector :li))
        (str/split-lines (:out (shell/sh "pandoc" "--list-input-formats"))))])

(declare pandoc->md)
(defn node+content [type pd-node] {:type type :content (keep pandoc->md (:c pd-node))})

(def pandoc-type->transform
  {:Space (constantly {:type :text :text " "})
   :Str (fn [node] {:type :text :text (:c node)})
   :Para (partial node+content :paragraph)
   :Plain (partial node+content :plain)
   :Header (fn [node]
             (let [[level _meta content] (:c node)]
               {:type :heading
                :heading-level level
                :content (keep pandoc->md content)}))

   :Emph (partial node+content :em)
   :Strong (partial node+content :strong)
   :Strikeout (partial node+content :strikethrough)
   :Underline (partial node+content :em)                    ;; missing on markdown
   :Link (fn [node]
           (let [[_meta content [href _]] (:c node)]
             {:type :link
              :attrs {:href href}
              :content (keep pandoc->md content)}))

   :BulletList (fn [node]
                 {:type :bullet-list
                  :content (map (fn [li]
                                  {:type :list-item
                                   :content (keep pandoc->md li)}) (:c node))})
   :OrderedList (fn [node]
                  {:type :numbered-list
                   :content (map (fn [li]
                                   {:type :list-item
                                    :content (keep pandoc->md li)}) (second (:c node)))})

   :Math (fn [node] (let [[_meta latex] (:c node)] (md.parser/block-formula latex)))
   :Code (fn [node]
           (let [[_meta code] (:c node)]
             {:type :monospace :content [(md.parser/text-node code)]}))
   :CodeBlock (fn [node]
                (let [[[_id classes _meta] code] (:c node)]
                  {:type :code
                   :content [(md.parser/text-node code)]}))
   :SoftBreak (constantly {:type :softbreak})
   :RawBlock (constantly nil)
   :RawInline (fn [{:keys [c]}]
                (cond
                  (and (vector? c) (= "latex" (first c)))
                  (md.parser/formula (second c))))})

(defn pandoc->md [{:as node :keys [t pandoc-api-version blocks]}]
  (if pandoc-api-version
    {:type :doc :content (keep pandoc->md blocks)}
    (if-some [xf (when t (get pandoc-type->transform (keyword t)))]
      (xf node)
      (throw (ex-info (str "Not Implemented '" t "'.") node)))))

(defn pandoc<- [input format]
  (-> (shell/sh "pandoc" "-f" format "-t" "json" :in input)
      :out (json/read-str :key-fn keyword)))

;; Let us test the machinery above against a **Microsoft Word** file, turning it into markdown and natively rendering it with Clerk

(v/html
 [:div.shadow-xl.p-8
  (-> (io/file "notebooks/demo.docx")
      (pandoc<- "docx")
      pandoc->md
      v/md)])

;; or ingest some **Org Mode**.
(v/html
 [:div.overflow-y-auto.shadow-xl {:style {:height "400px"}}
  [:div.p-8
   (-> (io/input-stream "https://raw.githubusercontent.com/erikriverson/org-mode-R-tutorial/master/org-mode-R-tutorial.org")
       (pandoc<- "org")
       pandoc->md
       v/md)]])

;; We also might want to test that our functions are invertible:
(v/html
 [:div
  [:div.shadow-xl.p-8
   (-> markdown-text
       md/parse
       md->pandoc
       #_#_ ;; we're not property testing Pandoc!
       (pandoc-> "org")
       (pandoc<- "org")
       pandoc->md
       v/md)]])

;; this brief experiment shows how Pandoc AST makes for an interesting format for Clerk to potentially
;; interact with formats other than markdown and clojure.

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 9999})
  (-> *e ex-cause ex-data)
  (json/read-str
   (:out
    (shell/sh "pandoc" "-f" "markdown" "-t" "json" :in markdown-text))
   :key-fn keyword))
