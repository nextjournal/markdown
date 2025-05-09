(ns nextjournal.markdown.transform
  "transform markdown data as returned by `nextjournal.markdown/parse` into other formats, currently:
     * hiccup")

;; helpers
(defn guard [pred val] (when (pred val) val))
(defn ->text [{:as _node :keys [type text content]}]
  (or (when (= :softbreak type) " ")
      text
      (apply str (map ->text content))))

(defn hydrate-toc
  "Scans doc contents and replaces toc node placeholder with the toc node accumulated during parse."
  [{:as doc :keys [toc]}]
  (update doc :content (partial into [] (map (fn [{:as node t :type}] (if (= :toc t) toc node))))))

(defn table-alignment [{:keys [style]}]
  (when (string? style)
    (let [[_ alignment] (re-matches #"^text-align:(.+)$" style)]
      (when alignment {:text-align alignment}))))

(defn heading-markup [{l :heading-level}] [(keyword (str "h" (or l 1)))])

;; into-markup
(declare ->hiccup)
(defn into-markup
  "Takes a hiccup vector, a context and a node, puts node's `:content` into markup mapping through `->hiccup`."
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

(def default-hiccup-renderers
  {:doc (partial into-markup [:div])
   :heading (fn [ctx {:as node :keys [attrs]}] (-> (heading-markup node) (conj attrs) (into-markup ctx node)))
   :paragraph (partial into-markup [:p])
   :plain (fn [ctx {:keys [content]}]
            (apply list (map (partial ->hiccup ctx) content)))
   :text (fn [_ {:keys [text]}] text)
   :hashtag (fn [_ {:keys [text]}] [:a.tag {:href (str "/tags/" text)} (str "#" text)]) ;; TODO: make it configurable
   :blockquote (partial into-markup [:blockquote])
   :ruler (constantly [:hr])

   ;; by default we always wrap images in paragraph to restore compliance with commonmark
   :image (fn [{:as _ctx ::keys [parent]} {:as node :keys [attrs]}]
            (let [img-markup [:img (assoc attrs :alt (->text node))]]
              (if (= :doc (:type parent))
                [:p img-markup]
                img-markup)))

   ;; code
   :code (partial into-markup [:pre.viewer-code.not-prose])

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
   :numbered-list (fn [ctx {:as node :keys [attrs]}] (into-markup [:ol attrs] ctx node))

   :todo-item (fn [ctx {:as node :keys [attrs]}]
                (into-markup [:li [:input {:type "checkbox" :checked (:checked attrs)}]] ctx node))

   ;; tables
   :table (partial into-markup [:table])
   :table-head (partial into-markup [:thead])
   :table-body (partial into-markup [:tbody])
   :table-row (partial into-markup [:tr])
   :table-header (fn [ctx {:as node :keys [attrs]}]
                   (into-markup (let [ta (table-alignment attrs)] (cond-> [:th] ta (conj {:style ta})))
                                ctx node))
   :table-data (fn [ctx {:as node :keys [attrs]}]
                 (into-markup (let [ta (table-alignment attrs)] (cond-> [:td] ta (conj {:style ta})))
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
   :link (fn [ctx {:as node :keys [attrs]}] (into-markup [:a {:href (:href attrs)}] ctx node))
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
