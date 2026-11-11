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
  (map (fn [previous child next]
         (->markdown (assoc ctx ::parent node ::previous previous ::next next) child))
       (cons nil content)
       content
       (concat (rest content) [nil])))

(declare emphasis-colors emphasis?)

(defn- inline-markdown [ctx node]
  (let [ctx (cond-> ctx
              (and (nil? (::emphasis-colors ctx)) (not (emphasis? node)))
              (assoc ::emphasis-colors (emphasis-colors ctx node)))]
    (apply str (render-children ctx node))))

(defn- block-markdown
  ([ctx node] (block-markdown "\n\n" ctx node))
  ([separator ctx node] (str/join separator (render-children ctx node))))

(defn- list-item-markdown [{:as ctx ::keys [tight]} {:as node :keys [content]}]
  (let [blocks (render-children ctx node)
        separators (map #(cond
                           (= :html-block (:type %)) (if tight "\n\n" "\n\n\n")
                           tight "\n"
                           :else "\n\n")
                        content)]
    (apply str (first blocks) (interleave separators (rest blocks)))))

(defn- prefix-lines [first-prefix prefix s]
  (->> (str/split-lines s)
       (map-indexed (fn [i line]
                      (let [p (if (zero? i) first-prefix prefix)]
                        (if (= "" line) (str/trimr p) (str p line)))))
       (str/join "\n")))

(defn- escape-text [s]
  (str/replace s #"[\\`*_\[\]<>$~|&]" #(str "\\" %)))

(defn- escape-line-start [s]
  (str/replace s #"^(?:[-+=#]|(\d+)([.)]))" (fn [[m digits delim]] (if digits (str digits "\\" delim) (str "\\" m)))))

(defn- line-start? [{::keys [parent previous]}]
  (and (nil? previous) (contains? #{:paragraph :plain :heading} (:type parent))))

(defn- paragraph-markdown [ctx node]
  (str/replace (inline-markdown ctx node) #"\n(?=[#>\-+*=_|<\[~$`0-9])" "\n    "))

(defn- text-markdown [{:as ctx ::keys [next]} {:keys [text]}]
  (cond-> (escape-text text)
    (line-start? ctx) escape-line-start
    (= :link (:type next)) (str/replace #"[!^]$" #(str "\\" %))))

(defn- formula-markdown [_ {:keys [text]}]
  (if (re-find #"^\s|\s$" text)
    (str "$$" text "$$")
    (str "$" text "$")))

(defn- block-formula-markdown [_ {:keys [text]}]
  #?(:clj (str "$$\n" text (when-not (re-find #"[ \t]$" text) "\n") "$$")
     :cljs (str "$$" text "$$")))

(defn- footnote-markdown [ctx {:as node :keys [ref label content]}]
  (let [blocks (render-children ctx node)
        separators (map #(if (= :paragraph (:type %)) "\n\n" "\n") (rest content))]
    (prefix-lines (str "[^" (or label (inc ref)) "]: ") "    "
                  (apply str (first blocks) (interleave separators (rest blocks))))))

(defn- fence [ch min-length text]
  (let [longest (reduce max 0 (map count (re-seq (re-pattern (str "[" ch "]+")) text)))]
    (apply str (repeat (max min-length (inc longest)) ch))))

(defn- code-text [{:keys [content]}]
  (or (-> content first :text) ""))

(defn- open-ended? [{:as node :keys [type]}]
  (let [text (if (= :html-block type) (->text node) (code-text node))]
    (and (#{:code :html-block} type)
         (not= "" text)
         (not (str/ends-with? text "\n")))))

(defn- indentable? [{::keys [previous]} {:as node :keys [info]}]
  (let [text (code-text node)
        lines (str/split (cond-> text (str/ends-with? text "\n") (subs 0 (dec (count text)))) #"\n" -1)]
    (and (nil? info)
         (not (open-ended? node))
         (not (str/blank? (first lines)))
         (not (str/blank? (last lines)))
         (not (#{:plain :bullet-list :numbered-list :todo-list} (:type previous)))
         (not (and (= :code (:type previous)) (nil? (:info previous)))))))

(defn- code-markdown [ctx {:as node :keys [info]}]
  (let [text (code-text node)]
    (if (indentable? ctx node)
      (prefix-lines "    " "    " text)
      (let [f (if (and info (str/includes? info "`")) (fence "~" 3 text) (fence "`" 3 text))]
        (str f (when (and info (re-find #"^[`~]" info)) " ") info "\n" text
             (when-not (open-ended? node) f))))))

(defn- tight-list? [{:keys [content]}]
  (not-any? #(some (comp #{:paragraph} :type) (:content %)) content))

(def ^:private list-family
  {:bullet-list :bullet :todo-list :bullet :numbered-list :numbered})

(defn- alternate-marker? [{::keys [parent alternate]} node]
  (let [item-parent? (#{:list-item :todo-item} (:type parent))]
    (loop [[sibling & siblings] (:content parent) previous nil previous-alternate false]
      (let [alternate? (cond
                         (and (nil? previous) item-parent?) (not alternate)
                         (and previous (= (list-family (:type sibling)) (list-family (:type previous))))
                         (not previous-alternate)
                         :else false)]
        (if (or (identical? sibling node) (empty? siblings))
          alternate?
          (recur siblings sibling alternate?))))))

(defn- list-markdown [marker]
  (fn [ctx {:as node :keys [content]}]
    (let [tight (tight-list? node)
          alternate? (alternate-marker? ctx node)]
      (->> content
           (map-indexed (fn [i item]
                          (let [m (marker node i alternate?)
                                pad (apply str (repeat (count m) " "))
                                s (->markdown (assoc ctx ::parent node ::tight tight ::alternate alternate?) item)]
                            (if (and (str/starts-with? s " ")
                                     (not= :code (:type (first (:content item)))))
                              (str (str/trimr m) "\n" (prefix-lines pad pad s))
                              (prefix-lines m pad s)))))
           (str/join (if tight "\n" "\n\n"))))))

(defn- bare-url? [{::keys [next]} {:keys [attrs content]}]
  (let [{:keys [href title]} attrs]
    (and (nil? title)
         (string? href)
         (re-matches #"[A-Za-z][A-Za-z0-9+.-]*://[^\s<>()\[\]]*[^\s<>()\[\].,:;!?\"']" href)
         (= [{:type :text :text href}] content)
         (or (nil? next)
             (#{:softbreak :hardbreak} (:type next))
             (and (= :text (:type next)) (re-find #"^\s" (:text next)))))))

(defn- link-destination [url title]
  (str (if (or (= "" url) (re-find #"\s" url))
         (str "<" (str/replace url #"[\\<>&]" #(str "\\" %)) ">")
         (str/replace url #"[\\()&]" #(str "\\" %)))
       (when title (str " \"" (str/replace title #"[\\\"&]" #(str "\\" %)) "\""))))

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

(def ^:private emphasis? (comp #{:em :strong} :type))

(defn- word-char? [c]
  (boolean (and c (re-find #"[\p{L}\p{N}]" c))))

(defn- neighbor-char [ctx node side]
  (when node
    (if (emphasis? node)
      "*"
      (let [s (->markdown (assoc ctx ::previous nil ::next nil ::emphasis-colors nil) node)]
        (when (seq s)
          (if (= :first side) (subs s 0 1) (subs s (dec (count s)))))))))

(defn- emphasis-constraints
  "Returns {:nodes [node ...] :edges [[i j same?] ...] :intraword #{i ...}} for the emphasis nodes below root."
  [ctx root]
  (let [state (volatile! {:nodes [] :edges [] :intraword #{}})
        add! (fn [k v] (vswap! state update k conj v))
        walk (fn walk [parent parent-id parent-left parent-right ancestor-ids]
               (let [content (vec (:content parent))
                     previous-id (volatile! nil)]
                 (doseq [i (range (count content))]
                   (let [child (nth content i)
                         previous (get content (dec i))
                         next (get content (inc i))]
                     (if (emphasis? child)
                       (let [id (count (:nodes @state))
                             left (word-char? (neighbor-char ctx previous :last))
                             right (word-char? (neighbor-char ctx next :first))]
                         (add! :nodes child)
                         (when (or left right) (add! :intraword id))
                         (when (emphasis? previous) (add! :edges [@previous-id id false]))
                         (vreset! previous-id id)
                         (when (and parent-id (or (nil? previous) (nil? next)))
                           (add! :edges [parent-id id (boolean (or (and (nil? previous) parent-left)
                                                                   (and (nil? next) parent-right)))]))
                         (when left
                           (doseq [a ancestor-ids] (add! :edges [a id false])))
                         (walk child id left right (conj ancestor-ids id)))
                       (walk child nil false false ancestor-ids))))))]
    (walk root nil false false [])
    @state))

(defn- emphasis-cost [colors edges intraword]
  (+ (* 100 (count (filter #(and (colors %) (intraword %)) (keys colors))))
     (reduce (fn [cost [a b same?]]
               (if (and (contains? colors a) (contains? colors b) (not= same? (= (colors a) (colors b))))
                 (+ cost (if same? 10 1))
                 cost))
             0
             edges)))

(defn- emphasis-components [n edges]
  (let [neighbors (reduce (fn [m [a b]] (-> m (update a (fnil conj []) b) (update b (fnil conj []) a))) {} edges)]
    (loop [ids (range n) seen #{} components []]
      (if-let [[id & ids] (seq ids)]
        (if (seen id)
          (recur ids seen components)
          (let [component (loop [queue [id] component #{id}]
                            (if-let [[x & queue] (seq queue)]
                              (let [new (remove component (neighbors x))]
                                (recur (concat queue new) (into component new)))
                              component))]
            (recur ids (into seen component) (conj components (sort component)))))
        components))))

(defn- emphasis-colors
  "Returns [[node underscore?] ...] for the emphasis nodes below root."
  [ctx root]
  (let [{:keys [nodes edges intraword]} (emphasis-constraints ctx root)
        best (fn [component]
               (let [assignments (if (<= (count component) 10)
                                   (for [bits (range (bit-shift-left 1 (count component)))]
                                     (zipmap component (map #(bit-test bits %) (range (count component)))))
                                   [(zipmap component (repeat false))])]
                 (apply min-key #(emphasis-cost % edges intraword) (reverse assignments))))
        colors (into {} (map best) (emphasis-components (count nodes) edges))]
    (mapv (fn [id] [(nth nodes id) (colors id)]) (range (count nodes)))))

(defn- emphasis-markdown [delimiter alternative]
  (fn [{:as ctx ::keys [emphasis-colors]} node]
    (let [underscore? (some (fn [[n u]] (when (identical? n node) u)) emphasis-colors)
          d (if underscore? alternative delimiter)]
      (str d (inline-markdown ctx node) d))))

(defn- bullet-marker [_ _ after-list?]
  (if after-list? "* " "- "))

(def default-markdown-renderers
  {:doc (fn [ctx {:as node :keys [footnotes content]}]
          (let [ctx (assoc ctx ::footnotes footnotes)
                footnotes (remove :inline? footnotes)]
            (str (str/join "\n\n" (remove str/blank?
                                          (cons (block-markdown ctx node)
                                                (map #(->markdown ctx %) footnotes))))
                 (when-not (and (empty? footnotes) (open-ended? (last content))) "\n"))))
   :heading (fn [ctx {:as node :keys [heading-level]}]
              (let [level (or heading-level 1)
                    text (paragraph-markdown ctx node)]
                (if (and (str/includes? text "\n") (<= level 2))
                  (str text "\n" (if (= 1 level) "===" "---"))
                  (str (apply str (repeat level "#")) " "
                       (str/replace text #"(^|[ \t])(#+[ \t]*)$" "$1\\\\$2")))))
   :paragraph paragraph-markdown
   :plain paragraph-markdown
   :text text-markdown
   :hashtag (fn [_ {:keys [text]}] (str "#" text))
   :blockquote (fn [ctx node] (prefix-lines "> " "> " (block-markdown ctx node)))
   :ruler (fn [{::keys [parent]} _] (if (#{:list-item :todo-item} (:type parent)) "___" "---"))
   :toc (constantly "[[TOC]]")

   :html-block (fn [_ node] (->text node))
   :html-inline (fn [_ node] (->text node))

   :image (fn [ctx {:as node :keys [attrs]}]
            (str "![" (inline-markdown ctx node) "](" (link-destination (:src attrs) (:title attrs)) ")"))

   :code code-markdown

   :softbreak (constantly "\n")
   :hardbreak (constantly "\\\n")

   :formula formula-markdown
   :block-formula block-formula-markdown

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
                                    (map #(inline-markdown (dissoc ctx ::emphasis-colors) %))
                                    (str/join " "))
                          "]")
                     (str "[^" (or label (inc ref)) "]")))
   :footnote footnote-markdown

   :sidenote-container (fn [ctx node]
                         (block-markdown ctx (update node :content #(remove (comp #{:sidenote-column} :type) %))))
   :sidenote-ref (fn [ctx node] ((:footnote-ref default-markdown-renderers) ctx node))

   :em (emphasis-markdown "*" "_")
   :strong (emphasis-markdown "**" "__")
   :strikethrough (fn [ctx node]
                    (let [d (if (some (comp #{:strikethrough} :type) (rest (tree-seq :content :content node)))
                              "~"
                              "~~")]
                      (str d (inline-markdown ctx node) d)))
   :monospace (fn [ctx node]
                (let [text (cond-> (->text node) (::table-cell ctx) (str/replace "|" "\\|"))
                      f (fence "`" 1 text)
                      pad (when (or (str/starts-with? text "`") (str/ends-with? text "`")
                                    (and (str/starts-with? text " ") (str/ends-with? text " ")
                                         (not (str/blank? text))))
                            " ")]
                  (str f pad text pad f)))
   :link (fn [ctx {:as node :keys [attrs]}]
           (if (bare-url? ctx node)
             (escape-text (:href attrs))
             (str "[" (inline-markdown ctx node) "](" (link-destination (:href attrs) (:title attrs)) ")")))
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
