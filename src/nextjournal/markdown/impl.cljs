;; # 🧩 Parsing
(ns nextjournal.markdown.impl
  (:require ["/js/markdown" :as md]
            ["markdown-it/lib/token" :as Token]
            [clojure.zip :as z]
            [nextjournal.markdown.utils :as u]))

(defn get* [o k]
  (unchecked-get o (cond-> k (keyword? k) name)))

(defn get-token-attr [token key]
  (cond-> (get* token key)
    (= :attrs key)
    (->> (into {} (map (juxt (comp keyword first) second))))
    (= :meta key)
    (js->clj :keywordize-keys true)))

(extend-type Token
  ILookup
  (-lookup
    ([this key] (get-token-attr this key))
    ([this key not-found] (or (get-token-attr this key) not-found))))

(defn hlevel [{:as _token hn :tag}] (when (string? hn) (some-> (re-matches #"h([\d])" hn) second js/parseInt)))

;; leaf nodes
;; TODO: use from utils
(defn text-node [text] {:type :text :text text})
(defn formula [text] {:type :formula :text text})
(defn block-formula [text] {:type :block-formula :text text})

;; node constructors
(defn node
  [type content attrs top-level]
  (cond-> {:type type :content content}
    (seq attrs) (assoc :attrs attrs)
    (seq top-level) (merge top-level)))

(defn empty-text-node? [{text :text t :type}] (and (= :text t) (empty? text)))

(defn push-node [ctx node]
  (cond-> ctx
    (not (empty-text-node? node))
    (u/update-current-loc z/append-child node)))

(defn open-node
  ([ctx type] (open-node ctx type {}))
  ([ctx type attrs] (open-node ctx type attrs {}))
  ([ctx type attrs top-level]
   (u/update-current-loc ctx u/zopen-node (node type [] attrs top-level))))

(defn close-node [doc] (u/update-current-loc doc z/up))

(comment

  (-> u/empty-doc
      (assoc :doc (u/->zip {:type :doc}))                   ;; [:content -1]
      (open-node :heading)                                  ;; [:content 0 :content -1]
      (push-node {:node/type :text :text "foo"})          ;; [:content 0 :content 0]
      (push-node {:node/type :text :text "foo"})          ;; [:content 0 :content 1]
      close-node                                          ;; [:content 1]

      (open-node :paragraph)                              ;; [:content 1 :content]
      (push-node {:node/type :text :text "hello"})
      close-node
      (open-node :bullet-list)
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
(defmethod apply-token "heading_close" [ctx _]
  (u/handle-close-heading ctx))

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
(defmethod apply-token "tocClose" [ctx _token]
  (-> ctx
      (u/update-current-loc
       (fn [loc]
         (-> loc (z/edit dissoc :content) z/up)))))

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

(defn footnote-label [{:as _ctx ::keys [footnote-offset]} token]
  ;; TODO: consider initial offset in case we're parsing multiple inputs
  (or (get-in token [:meta :label])
      ;; inline labels won't have a label
      (str "inline-note-" (+ footnote-offset (get-in token [:meta :id])))))

;; footnotes
(defmethod apply-token "footnote_ref" [{:as ctx ::keys [label->footnote-ref]} token]
  (let [label (footnote-label ctx token)
        footnote-ref (or (get label->footnote-ref label)
                         {:type :footnote-ref :inline? (not (get-in token [:meta :label]))
                          :ref (count label->footnote-ref)  ;; was (+ (count footnotes) (j/get-in token [:meta :id])) ???
                          :label label})]
    (-> ctx
        (u/update-current-loc z/append-child footnote-ref)
        (update ::label->footnote-ref assoc label footnote-ref))))

(defmethod apply-token "footnote_open" [ctx token]
  ;; TODO unify in utils
  (let [label (footnote-label ctx token)]
    (-> ctx
        (u/update-current-loc (fn [loc]
                                (u/zopen-node loc {:type :footnote
                                                   :inline? (not (get-in token [:meta :label]))
                                                   :label label}))))))

;; inline footnotes^[like this one]
(defmethod apply-token "footnote_close" [ctx _token]
  (-> ctx (u/update-current-loc z/up)))

(defmethod apply-token "footnote_block_open" [ctx token]
  ;; store footnotes at a top level `:footnote` key
  (assoc ctx ::root :footnotes))

(defmethod apply-token "footnote_block_close"
  ;; restores path for addding new tokens
  [ctx _token]
  (assoc ctx ::root :doc))

(defmethod apply-token "footnote_anchor" [doc _token] doc)

(comment
  (parse "some text^[inline note]"))

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

(defmethod apply-token "text" [ctx {:keys [content]}]
  (u/handle-text-token ctx content))

(comment
  (def mustache (u/normalize-tokenizer {:regex #"\{\{([^\{]+)\}\}" :handler (fn [m] {:type :eval :text (m 1)})}))
  (u/tokenize-text-node mustache {} {:text "{{what}} the {{hellow}}"})
  (u/handle-text-token (assoc u/empty-doc :text-tokenizers [mustache])
                       "foo [[bar]] dang #hashy taggy [[what]] #dangy foo [[great]] and {{eval}} me"))

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
(defn apply-tokens [doc tokens] (reduce apply-token doc tokens))

(defn parse
  ([markdown] (parse u/empty-doc markdown))
  ([ctx-in markdown]
   ;; TODO: unify implementations
   (let [{:as ctx-out :keys [doc title toc footnotes] ::keys [label->footnote-ref]}
         (-> ctx-in
             (assoc ::footnote-offset (count (::label->footnote-ref ctx-in)))
             (update :text-tokenizers (partial map u/normalize-tokenizer))
             (assoc :doc (u/->zip ctx-in)
                    :footnotes (u/->zip {:type :footnotes
                                         :content (or (:footnotes ctx-in) [])}))
             (apply-tokens (md/tokenize markdown)))]
     (-> ctx-out
         (dissoc :doc)
         (cond->
           (and title (not (:title ctx-in)))
           (assoc :title title))
         (assoc :toc toc
                :content (:content (z/root doc))
                ::label->footnote-ref label->footnote-ref
                :footnotes
                ;; there will never be references without definitions, but the contrary may happen
                (->> footnotes z/root :content
                     (keep (fn [{:as footnote :keys [label]}]
                             (when (contains? label->footnote-ref label)
                               (assoc footnote :ref (:ref (label->footnote-ref label))))))
                     (sort-by :ref)
                     (vec)))))))

(comment
  (-> (parse "text^[a]") ::label->footnote-ref)

  (-> (parse "text^[a]")
      (parse "text^[b]")))

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
