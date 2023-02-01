(ns nextjournal.markdown.transform
  "transform markdown data as returned by `nextjournal.markdown/parse` into other formats, currently:
     * hiccup
     * markdown"
  (:require [clojure.string :as str]))

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
    (seq content) (into mkup
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
   :plain (partial into-markup [:<>])
   :text (fn [_ {:keys [text]}] text)
   :hashtag (fn [_ {:keys [text]}] [:a.tag {:href (str "/tags/" text)} (str "#" text)]) ;; TODO: make it configurable
   :blockquote (partial into-markup [:blockquote])
   :ruler (constantly [:hr])

   ;; images
   :image (fn [{:as ctx ::keys [parent]} {:as node :keys [attrs]}]
            (if (= :paragraph (:type parent))
              [:img.inline attrs]
              [:figure.image [:img attrs] (into-markup [:figcaption] ctx node)]))

   ;; code
   :code (partial into-markup [:pre.viewer-code.not-prose])

   ;; softbreaks
   ;; :softbreak (constantly [:br]) (treat as space)
   :softbreak (constantly " ")

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
   :table-header (fn [ctx {:as node :keys [attrs]}] (into-markup [:th {:style (table-alignment attrs)}] ctx node))
   :table-data (fn [ctx {:as node :keys [attrs]}] (into-markup [:td {:style (table-alignment attrs)}] ctx node))

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

;; Text Transform
(defn write [ctx & strs] (update ctx ::buf into strs))

;; ctx -> node -> ctx
(defn write-node [ctx {:as node :keys [type]}]
  (if-some [handler (get ctx type)]
    (handler ctx node)
    (throw (ex-info (str "unhandled node type: " type) {:node node}))))

(defn write-child-nodes [ctx node]
  (update (reduce write-node (update ctx ::parents conj (:type node)) (:content node))
          ::parents pop))

;; {node ->} str
(def new-line "\n")
(def block-end "\n\n")
(def code-fence "```")
(defn code-fence+info [_ {:keys [language]}] (str code-fence language new-line))
(def tab "indent unit" "  ")
(defn heading-marker [_ {:keys [heading-level]}]
  (str (str/join (repeat heading-level "#")) " "))

;; handler -> handler
(defn ?->fn [m] (cond-> m (not (fn? m)) constantly))
(defn before [bf f] (fn [ctx n] (f (write ctx ((?->fn bf) ctx n)) n)))
(defn after [af f] (fn [ctx n]
                     (let [ctx' (f ctx n)]
                       (write ctx' ((?->fn af) ctx' n)))))

(defn block [f] (after block-end f))

;; nest children
(defn prepend-to-child-nodes [bf] (before bf write-child-nodes))
(defn append-to-child-nodes [af] (after af write-child-nodes))
(defn wrap-child-nodes [bf af] (after af (before bf write-child-nodes)))
(defn wrap-mark [m] (wrap-child-nodes m m))

(def top? (comp #{:doc} peek ::parents))
(defn quote? [{::keys [parents]}] (some #{:blockquote} parents))
(defn list-container [{::keys [parents]}] (some #{:bullet-list :numbered-list} parents))
(defn write-list-padding [{:as ctx ::keys [parents]}]
  (apply write ctx (repeat (dec (count (keep #{:bullet-list :numbered-list :todo-list} parents))) tab)))

(defn write-list [ctx n]
  (-> ctx
      (write-child-nodes n)
      (cond-> (top? ctx) (write new-line))))

(defn item-marker [{:as ctx ::keys [item-number]}]
  (case (list-container ctx)
    :bullet-list "* "
    :numbered-list (str item-number ". ")))

(defn write-footnote [ctx {:as node :keys [label ref]}]
  (-> ctx (write "[^" (or label ref) "]: ") (write-child-nodes node) (write new-line)))

(declare ->md)
(defn process-table-cell [ctx node]
  (-> node (select-keys [:attrs]) (assoc :text (str/trim (->md (dissoc ctx ::buf) node)))))

(defn write-row [col-widths ctx row]
  (as-> ctx c
    (write c "|")
    (reduce-kv (fn [ctx i {:as cell :keys [text]}]
                 (as-> ctx c
                   (write c " ")
                   (write c text)
                   (apply write c (repeat (- (col-widths i) (count text)) " "))
                   (write c " |"))) c (vec row))
    (write c new-line)))

(defn write-head-line [col-widths ctx row]
  (as-> ctx c
    (write c "|")
    (reduce-kv (fn [ctx i {{:keys [style]} :attrs}]
                 (as-> ctx c
                   (write c (if (#{"text-align:center" "text-align:left"} style) ":" "-"))
                   (apply write c (repeat (col-widths i) "-"))
                   (write c (if (#{"text-align:center" "text-align:right"} style) ":" "-") "|"))) c (vec row))
    (write c new-line)))

(defn write-table [{:as ctx ::keys [table]}]
  (def table table)
  (let [[head & body :as rows] (:rows table)
        column-widths (mapv (fn [i] (apply max (map (comp count :text #(nth % i)) rows)))
                            (range (count (first rows))))]
    (as-> ctx c
      (write-row column-widths c head)
      (write-head-line column-widths c head)
      (reduce (partial write-row column-widths) c body)
      (write c new-line))))

;; md text renderers
(def default-md-renderers
  {:doc write-child-nodes
   :toc (fn [ctx n] ctx)                              ;; ignore toc
   :text (fn [ctx {:keys [text]}] (write ctx text))
   :heading (block (prepend-to-child-nodes heading-marker))
   :ruler (block (fn [ctx _] (write ctx "---")))
   :paragraph (block
               (prepend-to-child-nodes
                (fn [ctx _] (when (quote? ctx) "> "))))
   :plain (append-to-child-nodes new-line)
   :softbreak (fn [ctx _]
                (-> ctx
                    (write new-line)
                    (cond-> (list-container ctx) (-> write-list-padding (write "  ")))
                    (cond-> (quote? ctx) (write "> "))))
   :blockquote write-child-nodes

   :formula (fn [ctx {:keys [text]}] (write ctx (str "$" text "$")))
   :block-formula (block (fn [ctx {:keys [text]}] (write ctx (str "$$" (str/trim text) "$$"))))

   ;; marks
   :em (wrap-mark "_")
   :strong (wrap-mark "**")
   :monospace (wrap-mark "`")
   :strikethrough (wrap-mark "~~")
   :hashtag (fn [ctx {:keys [text]}] (write ctx (str \# text)))
   :link (fn [ctx {:as n :keys [attrs]}]
           (-> ctx
               (write "[")
               (write-child-nodes n)
               (write "](" (:href attrs) ")")))
   :internal-link (fn [ctx {:keys [text]}] (write ctx "[[" text "]]"))

   :code (block (wrap-child-nodes code-fence+info code-fence))

   ;; lists
   :bullet-list write-list
   :numbered-list (fn [ctx n]
                    (-> ctx
                        (assoc ::item-number (-> n :attrs (:start 1)))
                        (write-list n)
                        (dissoc ::item-number)))
   :todo-list write-list
   :list-item (fn [{:as ctx ::keys [item-number]} n]
                (-> ctx
                    (cond-> item-number (update ::item-number inc))
                    write-list-padding
                    (write (item-marker ctx))
                    (write-child-nodes n)))
   :todo-item (fn [ctx {:as n :keys [attrs]}]
                (-> ctx
                    write-list-padding
                    (write (str "- [" (if (:checked attrs) "x" " ") "] "))
                    (write-child-nodes n)))

   :image (fn [{:as ctx ::keys [parents]} {:as n :keys [attrs]}]
            (-> ctx
                (write "![")
                (write-child-nodes n)
                (write "](" (:src attrs) ")")
                (cond-> (top? ctx) (write block-end))))

   :footnote-ref (fn [ctx {:keys [ref label]}] (write ctx "[^" (or label ref) "]"))

   ;; tables
   :table (fn [ctx n] (-> ctx (assoc ::table {:rows []}) (write-child-nodes n) write-table (dissoc ::table)))
   :table-head write-child-nodes
   :table-body write-child-nodes
   :table-header write-child-nodes
   :table-data write-child-nodes
   :table-row (fn [ctx {:keys [content]}]
                (update-in ctx [::table :rows] conj (map (partial process-table-cell ctx) content)))})

(defn ->md
  ([doc] (->md default-md-renderers doc))
  ([ctx {:as doc :keys [footnotes]}]
   (as-> ctx c
     (write-node c doc)
     (reduce write-footnote c (reverse footnotes))
     (str (str/trim (apply str (reverse (::buf c))))
          "\n"))))

#_ (->md (nextjournal.markdown/parse "# Ahoi
this is *just* a __strong__ ~~text~~ with a $\\phi$ and a #hashtag

this is an ![inline-image](/some/src) and a [_link_](/foo/bar)

par with a sidenote at the end[^sidenote] and another[^sn2] somewhere

```clojure
(+ 1 2)
```

$$
\\int_a^b\\phi(t)dt
$$

* _this_

  * sub1
  * sub2 some bla
    bla bla

* is not

  2. nsub1
  3. nsub2
  4. nsub3

* thight
  - [ ] undone
  - [x] done

* > and
  > a nice
  > quote

![block *image*](/some/src)

> so what
> is this

1. one
2. two

---

another

| _col1_        | col2                    |
|:-------------:|:------------------------|
| whatthasdasfd | hell                    |
| this is       | insane as as as as as f |

end

[^sidenote]: Here a __description__
[^sn2]: And some _other_
"))
