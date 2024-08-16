;; # 🧩 Parsing
;; plan
;; - write this with parse/2 copying from parser.cljc
;; - introduce impl.shared
;; - copy tokenizer from markdown cljs
;; - make markdown.clj cljc (break clj parsing)
;; - delete markdown.cljs
;; -

(ns nextjournal.markdown.impl
  (:require ["/js/markdown" :as md]
            ["markdown-it/lib/token" :as Token]
            [applied-science.js-interop :as j]
            [nextjournal.markdown.utils :as u]))

(extend-type Token
  ILookup
  (-lookup [this key] (j/get this key)))

(defn hlevel [{:as _token hn :tag}] (when (string? hn) (some-> (re-matches #"h([\d])" hn) second js/parseInt)))

;; leaf nodes
;; TODO: use from utils
(defn text-node [text] {:type :text :text text})
(defn formula [text] {:type :formula :text text})
(defn block-formula [text] {:type :block-formula :text text})
(defn footnote-ref [ref label] (cond-> {:type :footnote-ref :ref ref} label (assoc :label label)))

;; node constructors
(defn node
  [type content attrs top-level]
  (cond-> {:type type :content content}
    (seq attrs) (assoc :attrs attrs)
    (seq top-level) (merge top-level)))

(defn empty-text-node? [{text :text t :type}] (and (= :text t) (empty? text)))

(defn push-node [{:as doc ::keys [path]} node]
  (try
    (cond-> doc
      ;; ⬇ mdit produces empty text tokens at mark boundaries, see edge cases below
      (not (empty-text-node? node))
      (-> #_doc
       (update ::path u/inc-last)
       (update-in (pop path) conj node)))
    (catch js/Error e
      (throw (ex-info (str "nextjournal.markdown cannot add node: " node " at path: " path)
                      {:doc doc :node node} e)))))

(def push-nodes (partial reduce push-node))

(defn open-node
  ([doc type] (open-node doc type {}))
  ([doc type attrs] (open-node doc type attrs {}))
  ([doc type attrs top-level]
   (-> doc
       (push-node (node type [] attrs top-level))
       (update ::path into [:content -1]))))

;; after closing a node, document ::path will point at it
(def ppop (comp pop pop))
(defn close-node [doc] (update doc ::path ppop))
(defn update-current [{:as doc path ::path} fn & args] (apply update-in doc path fn args))

(defn current-parent-node
  "Given an open parsing context `doc`, returns the parent of the node which was last parsed into the document."
  [{:as doc ::keys [path]}]
  (assert path "A path is needed in document context to retrieve the current node: `current-parent-node` cannot be called after `parse`.")
  (get-in doc (ppop path)))

(defn current-ancestor-nodes
  "Given an open parsing context `doc`, returns the list of ancestors of the node last parsed into the document, up to but
   not including the top document."
  [{:as doc ::keys [path]}]
  (assert path "A path is needed in document context to retrieve the current node: `current-ancestor-nodes` cannot be called after `parse`.")
  (loop [p (ppop path) ancestors []]
    (if (seq p)
      (recur (ppop p) (conj ancestors (get-in doc p)))
      ancestors)))

;; TODO: unify via zipper
(defn assign-node-id+emoji [{:as doc ::keys [id->index path] :keys [text->id+emoji-fn]}]
  (let [{:keys [id emoji]} (when (ifn? text->id+emoji-fn) (-> doc (get-in path) text->id+emoji-fn))
        id-count (when id (get id->index id))]
    (cond-> doc
      id
      (update-in [::id->index id] (fnil inc 0))
      (or id emoji)
      (update-in path (fn [node]
                        (cond-> node
                          id (assoc-in [:attrs :id] (cond-> id id-count (str "-" (inc id-count))))
                          emoji (assoc :emoji emoji)))))))

(comment                                                    ;; path after call
  (-> empty-doc                                             ;; [:content -1]
      (open-node :heading)                                  ;; [:content 0 :content -1]
      (push-node {:node/type :text :text "foo"})            ;; [:content 0 :content 0]
      (push-node {:node/type :text :text "foo"})            ;; [:content 0 :content 1]
      close-node                                            ;; [:content 1]

      (open-node :paragraph)                                ;; [:content 1 :content]
      (push-node {:node/type :text :text "hello"})
      close-node
      (open-node :bullet-list)
      ;;
      ))
;; endregion

;; region token handlers
(declare apply-tokens)
(defmulti apply-token (fn [_doc token] (:type token)))
(defmethod apply-token :default [doc token]
  (prn :apply-token/unknown-type {:token token})
  doc)

;; blocks
(defmethod apply-token "heading_open" [doc token] (open-node doc :heading {} {:heading-level (hlevel token)}))
(defmethod apply-token "heading_close" [doc {doc-level :level}]
  (let [{:as doc ::keys [path]} (close-node doc)
        doc' (assign-node-id+emoji doc)
        heading (-> doc' (get-in path) (assoc :path path))]
    (cond-> doc'
      ;; We're only considering top-level headings (e.g. not those contained inside quotes or lists)
      (zero? doc-level)
      (-> (u/add-to-toc heading)
          (u/set-title-when-missing heading)))))

;; for building the TOC we just care about headings at document top level (not e.g. nested under lists) ⬆

(defmethod apply-token "paragraph_open" [doc {:as _token :keys [hidden]}]
  ;; no trace of tight vs loose on list nodes
  ;; markdown-it passes this info directly to paragraphs via this `hidden` key
  (open-node doc (if hidden :plain :paragraph)))

(defmethod apply-token "paragraph_close" [doc _token] (close-node doc))

(defmethod apply-token "bullet_list_open" [doc {{:as attrs :keys [has-todos]} :attrs}] (open-node doc (if has-todos :todo-list :bullet-list) attrs))
(defmethod apply-token "bullet_list_close" [doc _token] (close-node doc))

(defmethod apply-token "ordered_list_open" [doc {:keys [attrs]}] (open-node doc :numbered-list attrs))
(defmethod apply-token "ordered_list_close" [doc _token] (close-node doc))

(defmethod apply-token "list_item_open" [doc {{:as attrs :keys [todo]} :attrs}] (open-node doc (if todo :todo-item :list-item) attrs))
(defmethod apply-token "list_item_close" [doc _token] (close-node doc))

(defmethod apply-token "math_block" [doc {text :content}] (push-node doc (block-formula text)))
(defmethod apply-token "math_block_end" [doc _token] doc)

(defmethod apply-token "hr" [doc _token] (push-node doc {:type :ruler}))

(defmethod apply-token "blockquote_open" [doc _token] (open-node doc :blockquote))
(defmethod apply-token "blockquote_close" [doc _token] (close-node doc))

(defmethod apply-token "tocOpen" [doc _token] (open-node doc :toc))
(defmethod apply-token "tocBody" [doc _token] doc)          ;; ignore body
(defmethod apply-token "tocClose" [doc _token] (-> doc close-node (update-current dissoc :content)))

(defmethod apply-token "code_block" [doc {:as _token c :content}]
  (-> doc
      (open-node :code)
      (push-node (text-node c))
      close-node))
(defmethod apply-token "fence" [doc {:as _token i :info c :content}]
  (-> doc
      (open-node :code {} (assoc (u/parse-fence-info i) :info i))
      (push-node (text-node c))
      close-node))

;; footnotes
(defmethod apply-token "footnote_ref" [{:as doc :keys [footnotes]} token]
  (push-node doc (footnote-ref (+ (count footnotes) (j/get-in token [:meta :id]))
                               (j/get-in token [:meta :label]))))

(defmethod apply-token "footnote_anchor" [doc token] doc)

(defmethod apply-token "footnote_open" [{:as doc ::keys [footnote-offset]} token]
  ;; consider an offset in case we're parsing multiple inputs into the same context
  (let [ref (+ (j/get-in token [:meta :id]) footnote-offset)
        label (j/get-in token [:meta :label])]
    (open-node doc :footnote nil (cond-> {:ref ref} label (assoc :label label)))))

(defmethod apply-token "footnote_close" [doc token] (close-node doc))

(defmethod apply-token "footnote_block_open" [{:as doc :keys [footnotes] ::keys [path]} _token]
  ;; store footnotes at a top level `:footnote` key
  (let [footnote-offset (count footnotes)]
    (-> doc
        (assoc ::path [:footnotes (dec footnote-offset)]
               ::footnote-offset footnote-offset
               ::path-to-restore path))))

(defmethod apply-token "footnote_block_close"
  ;; restores path for addding new tokens
  [{:as doc ::keys [path-to-restore]} _token]
  (-> doc
      (assoc ::path path-to-restore)
      (dissoc ::path-to-restore ::footnote-offset)))

(defn footnote->sidenote [{:keys [ref label content]}]
  ;; this assumes the footnote container is a paragraph, won't work for lists
  (node :sidenote (-> content first :content) nil (cond-> {:ref ref} label (assoc :label label))))

(comment
  (-> "_hello_ what and foo[^note1] and^[some other note].

And what.

[^note1]: the _what_

* and new text[^endnote] at the end.
* the
  * hell^[that warm place]

[^endnote]: conclusion.
"
      nextjournal.markdown/tokenize
      parse
      #_flatten-tokens
      #_u/insert-sidenote-containers)

  (-> empty-doc
      (update :text-tokenizers (partial map u/normalize-tokenizer))
      (apply-tokens (nextjournal.markdown/tokenize "what^[the heck]"))
      insert-sidenote-columns
      (apply-tokens (nextjournal.markdown/tokenize "# Hello"))
      insert-sidenote-columns
      (apply-tokens (nextjournal.markdown/tokenize "is^[this thing]"))
      insert-sidenote-columns))

;; tables
;; table data tokens might have {:style "text-align:right|left"} attrs, maybe better nested node > :attrs > :style ?
(defmethod apply-token "table_open" [doc _token] (open-node doc :table))
(defmethod apply-token "table_close" [doc _token] (close-node doc))
(defmethod apply-token "thead_open" [doc _token] (open-node doc :table-head))
(defmethod apply-token "thead_close" [doc _token] (close-node doc))
(defmethod apply-token "tr_open" [doc _token] (open-node doc :table-row))
(defmethod apply-token "tr_close" [doc _token] (close-node doc))
(defmethod apply-token "th_open" [doc token] (open-node doc :table-header (:attrs token)))
(defmethod apply-token "th_close" [doc _token] (close-node doc))
(defmethod apply-token "tbody_open" [doc _token] (open-node doc :table-body))
(defmethod apply-token "tbody_close" [doc _token] (close-node doc))
(defmethod apply-token "td_open" [doc token] (open-node doc :table-data (:attrs token)))
(defmethod apply-token "td_close" [doc _token] (close-node doc))

(comment
  (->
   "
   | Syntax |  JVM                     | JavaScript                      |
   |--------|:------------------------:|--------------------------------:|
   |   foo  |  Loca _lDate_ ahoiii     | goog.date.Date                  |
   |   bar  |  java.time.LocalTime     | some [kinky](link/to/something) |
   |   bag  |  java.time.LocalDateTime | $\\phi$                         |
   "
   nextjournal.markdown/parse
   nextjournal.markdown.transform/->hiccup
   ))

(comment
  (->> "# Hello #Fishes

> what about #this

_this #should be a tag_, but this [_actually #foo shouldnt_](/bar/) is not."
       (parse (update empty-doc :text-tokenizers conj (u/normalize-tokenizer u/hashtag-tokenizer)))))

(defmethod apply-token "text" [{:as doc :keys [text-tokenizers]} {:keys [content]}]
  (u/handle-text-token doc content))

(comment
  (def mustache (u/normalize-tokenizer {:regex #"\{\{([^\{]+)\}\}" :handler (fn [m] {:type :eval :text (m 1)})}))
  (tokenize-text-node mustache {} {:text "{{what}} the {{hellow}}"})
  (apply-token (assoc empty-doc :text-tokenizers [mustache])
               {:type "text" :content "foo [[bar]] dang #hashy taggy [[what]] #dangy foo [[great]] and {{eval}} me"})

  (parse (assoc empty-doc
                :text-tokenizers
                [(u/normalize-tokenizer {:regex #"\{\{([^\{]+)\}\}"
                                       :doc-handler (fn [{:as doc ::keys [path]} {[_ meta] :match}]
                                                      (update-in doc (ppop path) assoc :meta meta))})])
          "# Title {{id=heading}}
* one
* two"))

;; inlines
(defmethod apply-token "inline" [doc {:as _token ts :children}] (apply-tokens doc ts))
(defmethod apply-token "math_inline" [doc {text :content}] (push-node doc (formula text)))
(defmethod apply-token "math_inline_double" [doc {text :content}] (push-node doc (formula text)))

;; https://spec.commonmark.org/0.30/#softbreak
(defmethod apply-token "softbreak" [doc _token] (push-node doc {:type :softbreak}))
;; https://spec.commonmark.org/0.30/#hard-line-break
(defmethod apply-token "hardbreak" [doc _token] (push-node doc {:type :hardbreak}))

;; images
(defmethod apply-token "image" [doc {:keys [attrs children]}] (-> doc (open-node :image attrs) (apply-tokens children) close-node))

;; marks
(defmethod apply-token "em_open" [doc _token] (open-node doc :em))
(defmethod apply-token "em_close" [doc _token] (close-node doc))
(defmethod apply-token "strong_open" [doc _token] (open-node doc :strong))
(defmethod apply-token "strong_close" [doc _token] (close-node doc))
(defmethod apply-token "s_open" [doc _token] (open-node doc :strikethrough))
(defmethod apply-token "s_close" [doc _token] (close-node doc))
(defmethod apply-token "link_open" [doc token] (open-node doc :link (:attrs token)))
(defmethod apply-token "link_close" [doc _token] (close-node doc))
(defmethod apply-token "code_inline" [doc {text :content}] (-> doc (open-node :monospace) (push-node (text-node text)) close-node))

;; html (ignored)
(defmethod apply-token "html_inline" [doc _] doc)
(defmethod apply-token "html_block" [doc _] doc)
;; endregion

;; region data builder api
(defn pairs->kmap [pairs] (into {} (map (juxt (comp keyword first) second)) pairs))
(defn apply-tokens [doc tokens]
  (let [mapify-attrs-xf (map (fn [x] (j/update! x :attrs pairs->kmap)))]
    (reduce (mapify-attrs-xf apply-token) doc tokens)))

(defn parse
  ([markdown] (parse u/empty-doc markdown))
  ([ctx markdown] (apply-tokens ctx (md/tokenize markdown))))

(comment
  (defn pr-dbg [x] (js/console.log (js/JSON.parse (js/JSON.stringify x))))
  (parse "# 🎱 Hello")
  )

(comment
  (some-> "# Title

## Section 1

foo

- # What is this? (no!)
- maybe

### Section 1.2

## Section 2

some par

### Section 2.1

some other par

### Section 2.2

#### Section 2.2.1

two two one

#### Section 2.2.2

two two two

## Section 3

some final par"
          nextjournal.markdown/parse
          (section-at [:content 9])                         ;; ⬅ paths are stored in TOC sections
          nextjournal.markdown.transform/->hiccup))
;; endregion


;; ## 🔧 Debug
;; A view on flattened tokens to better inspect tokens
(defn flatten-tokens [tokens]
  (into []
        (comp
         (mapcat (partial tree-seq (comp seq :children) :children))
         (map #(select-keys % [:type :content :hidden :level :info :meta])))
        tokens))
