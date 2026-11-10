(ns nextjournal.markdown.transform
  "transform markdown data as returned by `nextjournal.markdown/parse` into other formats, currently:
     * hiccup"
  {:skip-wiki true}
  (:require [clojure.string :as str]))

;; helpers
(defn- guard [pred val] (when (pred val) val))

(defn ->text
  "Convert node into text"
  ([node] (->text nil node))
  ([ctx {:as _node :keys [type text content]}]
   (or (when (= :softbreak type) " ")
       text
       (apply str (map #(->text ctx %) content)))))

(defn- hydrate-toc
  "Scans doc contents and replaces toc node placeholder with the toc node accumulated during parse."
  [{:as doc :keys [toc]}]
  (update doc :content (partial into [] (map (fn [{:as node t :type}] (if (= :toc t) toc node))))))

(defn table-alignment [{:keys [attrs alignment]}]
  (cond
    (string? (:style attrs))
    (let [[_ alignment] (re-matches #"^text-align:(.+)$" (:style attrs))]
      (when alignment {:text-align alignment}))
    (keyword? alignment)
    {:text-align (name alignment)}))

(defn- heading-markup [{l :heading-level}] [(keyword (str "h" (or l 1)))])

;; into-markup
(declare ->hiccup)
(defn into-markup
  "Private API. See markdown/into-hiccup."
  [mkup ctx {:as node :keys [text content]}]
  (cond ;; formula nodes are leaves: have text and no contents
    text (conj mkup text)
    content (into mkup
                  (keep (partial ->hiccup (assoc ctx ::parent node)))
                  content)))

(defn toc->hiccup [{:as ctx ::keys [parent]} {:as node :keys [attrs content children]}]
  (let [id (:id attrs)
        toc-item (cond-> [:div]
                   (seq content)
                   (conj [:a {:href (str "#" id) #?@(:cljs [:on-click #(when-some [el (.getElementById js/document id)] (.preventDefault %) (.scrollIntoViewIfNeeded el))])}
                          (-> node heading-markup (into-markup ctx node))])
                   (seq children)
                   (conj (into [:ul] (map (partial ->hiccup (assoc ctx ::parent node))) children)))]
    (cond->> toc-item
      (= :toc (:type parent))
      (conj [:li.toc-item])
      (not= :toc (:type parent))
      (conj [:div.toc]))))

(comment
  ;; override toc rendering
  (-> "# Hello
a paragraph
[[TOC]]
## Section _nice_ One
### Section Nested
## Section **terrible** Idea
"
      nextjournal.markdown/parse
      ;; :toc
      ;; ->hiccup #_
      (->> (->hiccup (assoc default-hiccup-renderers
                            :toc (fn [ctx {:as node :keys [content children heading-level]}]
                                   (cond-> [:div]
                                     (seq content) (conj [:span.title {:data-level heading-level} (:id node)])
                                     (seq children) (conj (into [:ul] (map (partial ->hiccup ctx)) children)))))))))

;; emitting raw HTML unescaped is left to the consumer
(defn- missing-html-renderer [_ctx {:keys [type]}]
  [:span.message.red
   [:strong (str "No default renderer for '" type "'.")]
   " See https://github.com/nextjournal/markdown#html-blocks-and-html-inlines"])

(def default-hiccup-renderers
  {:doc (partial into-markup [:div])
   :heading (fn [ctx {:as node :keys [attrs]}] (-> (heading-markup node) (conj attrs) (into-markup ctx node)))
   :paragraph (partial into-markup [:p])
   :plain (fn [ctx {:keys [content]}]
            (seq (mapv (partial ->hiccup ctx) content)))
   :text (fn [_ {:keys [text]}] text)
   :hashtag (fn [_ {:keys [text]}] [:a.tag {:href (str "/tags/" text)} (str "#" text)]) ;; TODO: make it configurable
   :blockquote (partial into-markup [:blockquote])
   :ruler (constantly [:hr])

   ;; html
   :html-block missing-html-renderer
   :html-inline missing-html-renderer

   ;; by default we always wrap images in paragraph to restore compliance with commonmark
   :image (fn [{:as _ctx ::keys [parent]} {:as node :keys [attrs]}]
            (let [img-markup [:img (assoc attrs :alt (->text node))]]
              (if (= :doc (:type parent))
                [:p img-markup]
                img-markup)))

   ;; code
   :code (fn [_ {:keys [language] :as m}]
           [:pre
            [(if language
               (keyword (str "code.language-" language))
               :code)
             (-> m :content first :text)]])

   ;; breaks
   :softbreak (constantly " ")
   :hardbreak (constantly [:br])

   ;; formulas
   :formula (partial into-markup [:span.formula])
   :block-formula (partial into-markup [:figure.formula])

   ;; lists
   :bullet-list (partial into-markup [:ul])
   :list-item (partial into-markup [:li])
   :todo-list (partial into-markup [:ul.contains-task-list])
   :numbered-list (fn [ctx {:as node :keys [attrs]}] (into-markup (cond-> [:ol]
                                                                    attrs (conj attrs)) ctx node))

   :todo-item (fn [ctx {:as node :keys [attrs]}]
                (into-markup [:li [:input {:type "checkbox" :checked (:checked attrs)}]] ctx node))

   ;; tables
   :table (partial into-markup [:table])
   :table-head (partial into-markup [:thead])
   :table-body (partial into-markup [:tbody])
   :table-row (partial into-markup [:tr])
   :table-header (fn [ctx node]
                   (into-markup (let [ta (table-alignment node)] (cond-> [:th] ta (conj {:style ta})))
                                ctx node))
   :table-data (fn [ctx node]
                 (into-markup (let [ta (table-alignment node)] (cond-> [:td] ta (conj {:style ta})))
                              ctx node))

   ;; footnotes & sidenodes
   :sidenote-container (partial into-markup [:div.sidenote-container])
   :sidenote-column (partial into-markup [:div.sidenote-column])
   :sidenote-ref (fn [_ {:keys [ref label]}] [:sup.sidenote-ref {:data-label label} (str (inc ref))])
   :sidenote (fn [ctx {:as node :keys [ref]}]
               (into-markup [:span.sidenote [:sup {:style {:margin-right "3px"}} (str (inc ref))]] ctx node))

   :footnote-ref (fn [_ {:keys [ref label]}] [:sup.sidenote-ref {:data-label label} (str (inc ref))])
   ;; NOTE: there's no default footnote placement (see n.markdown.parser/insert-sidenotes)
   :footnote (fn [ctx {:as node :keys [ref label]}]
               (into-markup [:div.footnote [:span.footnote-label {:data-ref ref} label]] ctx node))

   ;; TOC
   :toc toc->hiccup

   ;; marks
   :em (partial into-markup [:em])
   :strong (partial into-markup [:strong])
   :monospace (partial into-markup [:code])
   :strikethrough (partial into-markup [:s])
   :link (fn [ctx {:as node :keys [attrs]}] (into-markup [:a (select-keys attrs [:href :title])] ctx node))
   :internal-link (fn [_ {:keys [attrs text]}] [:a.internal {:href (:href attrs text)} text])

   ;; default convenience fn to wrap extra markup around the default one from within the overriding function
   :default (fn [ctx {:as node t :type}] (when-some [d (get default-hiccup-renderers t)] (d ctx node)))
   })

(defn ->hiccup
  ([node] (->hiccup default-hiccup-renderers node))
  ([ctx {:as node t :type}]
   (let [{:as node :keys [type]} (cond-> node (= :doc t) hydrate-toc)]
     (if-some [f (guard fn? (get ctx type))]
       (f ctx node)
       [:span.message.red
        [:strong (str "Unknown type: '" type "'.")]
        [:code (pr-str node)]]
       ))))

(comment
  (-> "# Hello

a nice paragraph with sidenotes[^my-note]

[[TOC]]

## Section One
A nice $\\phi$ formula [for _real_ **strong** fun](/path/to) soft
break

- [ ] one **ahoi** list
- two `nice` and ~~three~~
- [x] checked

> that said who?

---

## Section Two

### Tables

| Syntax |  JVM                     | JavaScript                      |
|--------|-------------------------:|:--------------------------------|
|   foo  |  Loca _lDate_ ahoiii     | goog.date.Date                  |
|   bar  |  java.time.LocalTime     | some [kinky](link/to/something) |
|   bag  |  java.time.LocalDateTime | $\\phi$                         |

### Images

![Some **nice** caption](https://www.example.com/images/dinosaur.jpg)

and here as inline ![alt](foo/bar) image

```clj
(some nice clojure)
```

[^my-note]: Here can discuss at length"
    nextjournal.markdown/parse
    ->hiccup
    )

  ;; override defaults
  (->> "## Title
par one

par two"
    nextjournal.markdown/parse
    (->hiccup (assoc default-hiccup-renderers
                     :heading (partial into-markup [:h1.at-all-levels])
                     ;; wrap something around the default
                     :paragraph (fn [{:as ctx d :default} node] [:div.p-container (d ctx node)]))))
  )

(comment
  (require '[hiccup2.core :as h])

  (-> "
* one
* two"
      nextjournal.markdown/parse
      ->hiccup
      h/html str
      )

  (-> "
* one

* two"
      nextjournal.markdown/parse
      ->hiccup
      h/html str
      )

  (-> "# foo
- one \\
  broken
- two"
      nextjournal.markdown/parse
      ->hiccup
      h/html str
      )

  ;; https://spec.commonmark.org/0.30/#example-319
  (= (str "<div>"
          "<ul><li>a<ul><li><p>b</p><p>c</p></li></ul></li><li>d</li></ul>"
          "</div>")
     (->> "- a\n  - b\n\n    c\n- d"
          nextjournal.markdown/parse
          (->hiccup default-hiccup-renderers)
          h/html str)))

(declare ->markdown)

(defn- render-children [ctx {:as node :keys [content]}]
  (map (fn [previous child] (->markdown (assoc ctx ::parent node ::previous previous) child))
       (cons nil content)
       content))

(defn- inline-markdown [ctx node]
  (apply str (render-children ctx node)))

(defn- block-markdown
  ([ctx node] (block-markdown "\n\n" ctx node))
  ([separator ctx node] (str/join separator (render-children ctx node))))

(defn- list-item-markdown [ctx {:as node :keys [content]}]
  (block-markdown (if (some (comp #{:plain} :type) content) "\n" "\n\n") ctx node))

(defn- prefix-lines [first-prefix prefix s]
  (->> (str/split-lines s)
       (map-indexed (fn [i line]
                      (let [p (if (zero? i) first-prefix prefix)]
                        (if (= "" line) (str/trimr p) (str p line)))))
       (str/join "\n")))

(defn- escape-text [s]
  (str/replace s #"[\\`*_\[\]<>$~|&]" #(str "\\" %)))

(defn- escape-line-starts [s]
  (str/replace s #"(?m)^(?:[-+=#]|(\d+)([.)]))" (fn [[m digits delim]] (if digits (str digits "\\" delim) (str "\\" m)))))

(defn- paragraph-markdown [ctx node]
  (escape-line-starts (inline-markdown ctx node)))

(defn- backtick-fence [min-length text]
  (let [longest (reduce max 0 (map count (re-seq #"`+" text)))]
    (apply str (repeat (max min-length (inc longest)) "`"))))

(defn- tight-list? [{:keys [content]}]
  (not-any? #(some (comp #{:paragraph} :type) (:content %)) content))

(defn- list-markdown [marker]
  (fn [ctx {:as node :keys [content]}]
    (->> content
         (map-indexed (fn [i item]
                        (let [m (marker node i (= (:type node) (:type (::previous ctx))))]
                          (prefix-lines m (apply str (repeat (count m) " "))
                                        (->markdown (assoc ctx ::parent node) item)))))
         (str/join (if (tight-list? node) "\n" "\n\n")))))

(defn- link-destination [url title]
  (str (if (or (= "" url) (re-find #"\s" url))
         (str "<" (str/replace url #"[<>]" #(str "\\" %)) ">")
         (str/replace url #"[()]" #(str "\\" %)))
       (when title (str " \"" (str/replace title "\"" "\\\"") "\""))))

(defn- table-delimiter [cell]
  (case (:text-align (table-alignment cell))
    "left" ":--"
    "right" "--:"
    "center" ":-:"
    "---"))

(defn- table-markdown [ctx {:keys [content]}]
  (let [ctx (assoc ctx ::table-cell true)
        rows (mapcat :content content)
        row-markdown (fn [cells] (str "| " (str/join " | " cells) " |"))]
    (->> (concat [(row-markdown (map #(inline-markdown ctx %) (:content (first rows))))
                  (row-markdown (map table-delimiter (:content (first rows))))]
                 (map (fn [row] (row-markdown (map #(inline-markdown ctx %) (:content row))))
                      (rest rows)))
         (str/join "\n"))))

(defn- wrap-markdown [delimiter]
  (fn [ctx node] (str delimiter (inline-markdown ctx node) delimiter)))

(defn- emphasis-markdown [delimiter alternative]
  (fn [ctx node]
    (let [s (inline-markdown ctx node)
          d (if (or (str/starts-with? s "*") (str/ends-with? s "*")) alternative delimiter)]
      (str d s d))))

(defn- bullet-marker [_ _ after-list?]
  (if after-list? "* " "- "))

(def default-markdown-renderers
  {:doc (fn [ctx {:as node :keys [footnotes]}]
          (let [ctx (assoc ctx ::footnotes footnotes)]
            (str (str/join "\n\n" (cons (block-markdown ctx node)
                                        (map #(->markdown ctx %) (remove :inline? footnotes))))
                 "\n")))
   :heading (fn [ctx {:as node :keys [heading-level]}]
              (let [level (or heading-level 1)
                    text (paragraph-markdown ctx node)]
                (if (and (str/includes? text "\n") (<= level 2))
                  (str text "\n" (if (= 1 level) "===" "---"))
                  (str (apply str (repeat level "#")) " " text))))
   :paragraph paragraph-markdown
   :plain paragraph-markdown
   :text (fn [_ {:keys [text]}] (escape-text text))
   :hashtag (fn [_ {:keys [text]}] (str "#" text))
   :blockquote (fn [ctx node] (prefix-lines "> " "> " (block-markdown ctx node)))
   :ruler (constantly "---")
   :toc (constantly "[[TOC]]")

   :html-block (fn [_ node] (->text node))
   :html-inline (fn [_ node] (->text node))

   :image (fn [ctx {:as node :keys [attrs]}]
            (str "![" (inline-markdown ctx node) "](" (link-destination (:src attrs) (:title attrs)) ")"))

   :code (fn [_ {:keys [info content]}]
           (let [text (-> content first :text)
                 text (cond-> text (not (str/ends-with? text "\n")) (str "\n"))
                 f (backtick-fence 3 text)]
             (if info
               (str f info "\n" text f)
               (prefix-lines "    " "    " text))))

   :softbreak (constantly "\n")
   :hardbreak (constantly "\\\n")

   :formula (fn [_ {:keys [text]}] (str "$" text "$"))
   :block-formula (fn [_ {:keys [text]}] (str "$$\n" (str/trim text) "\n$$"))

   :bullet-list (list-markdown bullet-marker)
   :todo-list (list-markdown bullet-marker)
   :numbered-list (list-markdown (fn [{:keys [attrs]} i after-list?]
                                   (str (+ (:start attrs 1) i) (if after-list? ") " ". "))))
   :list-item list-item-markdown
   :todo-item (fn [ctx {:as node :keys [attrs]}]
                (str (if (:checked attrs) "[x] " "[ ] ") (list-item-markdown ctx node)))

   :table table-markdown

   :footnote-ref (fn [ctx {:keys [ref label inline?]}]
                   (if inline?
                     (str "^[" (->> (::footnotes ctx)
                                    (filter #(= ref (:ref %)))
                                    first :content
                                    (map #(inline-markdown ctx %))
                                    (str/join " "))
                          "]")
                     (str "[^" (or label (inc ref)) "]")))
   :footnote (fn [ctx {:as node :keys [ref label]}]
               (prefix-lines (str "[^" (or label (inc ref)) "]: ") "    " (block-markdown ctx node)))

   :em (emphasis-markdown "*" "_")
   :strong (emphasis-markdown "**" "__")
   :strikethrough (wrap-markdown "~~")
   :monospace (fn [ctx node]
                (let [text (cond-> (->text node) (::table-cell ctx) (str/replace "|" "\\|"))
                      f (backtick-fence 1 text)
                      pad (when (or (str/starts-with? text "`") (str/ends-with? text "`")
                                    (and (str/starts-with? text " ") (str/ends-with? text " ")
                                         (not (str/blank? text))))
                            " ")]
                  (str f pad text pad f)))
   :link (fn [ctx {:as node :keys [attrs]}]
           (str "[" (inline-markdown ctx node) "](" (link-destination (:href attrs) (:title attrs)) ")"))
   :internal-link (fn [_ {:keys [text]}] (str "[[" text "]]"))

   :default (fn [ctx {:as node t :type}] (when-some [d (get default-markdown-renderers t)] (d ctx node)))})

(defn ->markdown
  "Returns markdown text for node.
  ctx is a map of node type to renderer fn, or `default-markdown-renderers` if absent.
  A renderer takes ctx and a node and returns a string."
  ([node] (->markdown default-markdown-renderers node))
  ([ctx {:as node :keys [type text]}]
   (if-some [f (guard fn? (get ctx type))]
     (f ctx node)
     (or text (inline-markdown ctx node)))))

(comment

  (require '[nextjournal.markdown :as md]
           '[nextjournal.markdown.transform :as t])
  (println (t/->markdown (md/parse "Hello _world_\n===\n\n* one\n* two\n\n> quote with [a link](https://example.com)")))

  (println (t/->markdown (md/parse "
Hello _world_
===

- a
  - b
  - c

")))

  )
