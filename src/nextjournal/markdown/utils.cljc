;; # Markdown parsing shared utils
(ns nextjournal.markdown.utils
  (:require [clojure.string :as str]
            [clojure.zip :as z]
            [nextjournal.markdown.utils.emoji :as emoji]
            [nextjournal.markdown.transform :as md.transform]))

#?(:clj (defn re-groups* [m] (let [g (re-groups m)] (cond-> g (not (vector? g)) vector))))
(defn re-idx-seq
  "Takes a regex and a string, returns a seq of triplets comprised of match groups followed by indices delimiting each match."
  [re text]
  #?(:clj (let [m (re-matcher re text)]
            (take-while some? (repeatedly #(when (.find m) [(re-groups* m) (.start m) (.end m)]))))
     :cljs (let [rex (js/RegExp. (.-source re) "g")]
             (take-while some? (repeatedly #(when-some [m (.exec rex text)] [(vec m) (.-index m) (.-lastIndex rex)]))))))

#_ (re-idx-seq #"\{\{([^{]+)\}\}" "foo {{hello}} bar")
#_ (re-idx-seq #"\{\{[^{]+\}\}" "foo {{hello}} bar {{what}} the")

;; ## Context and Nodes

(defn split-by-emoji [s]
  (let [[match start end] (first (re-idx-seq emoji/regex s))]
    (if match
      [(subs s start end) (str/trim (subs s end))]
      [nil s])))

#_(split-by-emoji " Stop")
#_(split-by-emoji "🤚🏽 Stop")
#_(split-by-emoji "🤚🏽🤚 Stop")
#_(split-by-emoji "🤚🏽Stop")
#_(split-by-emoji "🤚🏽   Stop")
#_(split-by-emoji "😀 Stop")
#_(split-by-emoji "⚛️ Stop")
#_(split-by-emoji "⚛ Stop")
#_(split-by-emoji "⬇ Stop")
#_(split-by-emoji "Should not 🙁️ Split")
#_(text->id+emoji "Hello There")
#_(text->id+emoji "Hello_There")
#_(text->id+emoji "👩‍🔬 Quantum Physics")

(defn text->id+emoji [text]
  (when (string? text)
    (let [[emoji text'] (split-by-emoji (str/trim text))]
      (cond-> {:id (apply str (map (comp str/lower-case (fn [c] (case c (\space \_) \- c))) text'))}
        emoji (assoc :emoji emoji)))))

;; TODO: move this to n.markdown ns
(def empty-doc
  {:type :doc
   :content []
   :toc {:type :toc}
   :footnotes []
   :text-tokenizers []
   ;; Node -> {id : String, emoji String}, dissoc from context to opt-out of ids
   :text->id+emoji-fn (comp text->id+emoji md.transform/->text)

   ;; private
   ;; Id -> Nat, to disambiguate ids for nodes with the same textual content
   :nextjournal.markdown.impl/id->index {}
   ;; allow to swap between :doc or :footnotes
   :nextjournal.markdown.impl/root :doc
   :nextjournal.markdown.impl/path [:content -1]})

(defn current-loc [{:as ctx :nextjournal.markdown.impl/keys [root]}] (get ctx root))
(defn update-current-loc [{:as ctx :nextjournal.markdown.impl/keys [root]} f & args]
  (assert root (str "Missing root: '" (keys ctx) "'"))
  (apply update ctx root f args))

(defn text-node [s] {:type :text :text s})
(defn formula [text] {:type :formula :text text})
(defn block-formula [text] {:type :block-formula :text text})

(defn node
  [type content attrs top-level]
  (cond-> {:type type :content content}
    (seq attrs) (assoc :attrs attrs)
    (seq top-level) (merge top-level)))

;; ## 🤐 Zipper Utils

(defn ->zip [doc]
  (z/zipper (every-pred map? :type) :content
            (fn [node cs] (assoc node :content (vec cs)))
            doc))
(def zip? (comp some? :zip/children meta))
(defn zdepth [loc] (-> loc second :pnodes count))

#_(zip? (->zip {:type :doc :content []}))
#_(->zip {:type :doc :content []})
#_(-> {:type :doc :content []} ->zip
      (z/append-child {:type :heading})
      z/down zdepth)

(defn zopen-node [loc node]
  (-> loc (z/append-child node) z/down z/rightmost))

(defn zpath
  "Given a document zipper location `loc` returns a vector corresponding to the path of node at `loc`
   suitable for get-in from root. That is `(= (z/node loc) (get-in (z/root loc) (zpath loc)`"
  [loc]
  (loop [coords (second loc) idxs ()]
    (if-some [idx (when (and coords (:l coords)) (count (:l coords)))]
      (recur (:ppath coords) (conj idxs idx))
      (vec (when (seq idxs)
             (cons :content (interpose :content idxs)))))))

(comment
  (def loc
    (-> {:type :doc} ->zip
        (z/append-child {:type :paragraph})
        (z/append-child {:type :paragraph})
        z/down z/rightmost
        (z/append-child {:type :text :text "ahoi"})
        z/down))
  (-> loc z/node)
  (-> loc second)
  )

;; TODO: rewrite in terms of zippers
(def ppop (comp pop pop))
(defn inc-last [path] (update path (dec (count path)) inc))
(defn empty-text-node? [{text :text t :type}] (and (= :text t) (empty? text)))
;; TODO: unify in terms of zippers
#?(:cljs
   (defn push-node [{:as doc :nextjournal.markdown.impl/keys [path]} node]
     (try
       (cond-> doc
         ;; ⬇ mdit produces empty text tokens at mark boundaries, see edge cases below
         (not (empty-text-node? node))
         (-> #_doc
          (update :nextjournal.markdown.impl/path inc-last)
          (update-in (pop path) conj node)))
       (catch js/Error e
         (throw (ex-info (str "nextjournal.markdown cannot add node: " node " at path: " path)
                         {:doc doc :node node} e)))))
   :clj
   (def push-node z/append-child))

;; ## 🗂️ ToC Handling
;; region toc:
;; toc nodes are heading nodes but with `:type` `:toc` and an extra branching
;; on the key `:children` representing the sub-sections of the document

(defn into-toc [toc {:as toc-item :keys [heading-level]}]
  (loop [toc toc l heading-level toc-path [:children]]
    ;; `toc-path` is `[:children i₁ :children i₂ ... :children]`
    (let [type-path (assoc toc-path (dec (count toc-path)) :type)]
      (cond
        ;; insert intermediate default empty :content collections for the final update-in (which defaults to maps otherwise)
        (not (get-in toc toc-path))
        (recur (assoc-in toc toc-path []) l toc-path)

        ;; fill in toc types for non-contiguous jumps like h1 -> h3
        (not (get-in toc type-path))
        (recur (assoc-in toc type-path :toc) l toc-path)

        (= 1 l)
        (update-in toc toc-path (fnil conj []) toc-item)

        :else
        (recur toc
               (dec l)
               (conj toc-path
                     (max 0 (dec (count (get-in toc toc-path)))) ;; select last child at level if it exists
                     :children))))))

(defn add-to-toc [doc {:as h :keys [heading-level]}]
  (cond-> doc (pos-int? heading-level) (update :toc into-toc (assoc h :type :toc))))

(defn set-title-when-missing [{:as doc :keys [title]} heading]
  (cond-> doc (nil? title) (assoc :title (md.transform/->text heading))))

(defn add-title+toc
  "Computes and adds a :title and a :toc to the document-like structure `doc` which might have not been constructed by means of `parse`."
  [{:as doc :keys [content]}]
  (let [rf (fn [doc heading] (-> doc (add-to-toc heading) (set-title-when-missing heading)))
        xf (filter (comp #{:heading} :type))]
    (reduce (xf rf) (assoc doc :toc {:type :toc}) content)))

(comment
  (-> {:type :toc}
      ;;(into-toc {:heading-level 3 :title "Foo"})
      ;;(into-toc {:heading-level 2 :title "Section 1"})
      (into-toc {:heading-level 1 :title "Title" :type :toc})
      (into-toc {:heading-level 4 :title "Section 2" :type :toc})
      ;;(into-toc {:heading-level 4 :title "Section 2.1"})
      ;;(into-toc {:heading-level 2 :title "Section 3"})
      )

  (-> "# Top _Title_

par

### Three

## Two

par
- and a nested
- ### Heading not included

foo

## Two Again

par

# One Again

[[TOC]]

#### Four

end"
      nextjournal.markdown/parse
      :toc
      ))
;; endregion

;; ## Parsing Extensibility
;;
;;    normalize-tokenizer :: {:regex, :doc-handler} | {:tokenizer-fn, :handler} -> Tokenizer
;;    Tokenizer :: {:tokenizer-fn :: TokenizerFn, :doc-handler :: DocHandler}
;;
;;    Match :: Any
;;    Handler :: Match -> Node
;;    IndexedMatch :: (Match, Int, Int)
;;    TokenizerFn :: String -> [IndexedMatch]
;;    DocHandler :: Doc -> {:match :: Match} -> Doc

(defn tokenize-text-node [{:as tkz :keys [tokenizer-fn pred doc-handler]} doc {:as node :keys [text]}]
  ;; TokenizerFn -> HNode -> [HNode]
  (assert (and (fn? tokenizer-fn)
               (fn? doc-handler)
               (fn? pred)
               (string? text))
          {:text text :tokenizer tkz})
  (let [idx-seq (when (pred doc) (tokenizer-fn text))]
    (if (seq idx-seq)
      (let [text-hnode (fn [s] (assoc (text-node s) :doc-handler push-node))
            {:keys [nodes remaining-text]}
            (reduce (fn [{:as acc :keys [remaining-text]} [match start end]]
                      (-> acc
                          (update :remaining-text subs 0 start)
                          (cond->
                            (< end (count remaining-text))
                            (update :nodes conj (text-hnode (subs remaining-text end))))
                          (update :nodes conj {:doc-handler doc-handler
                                               :match match :text text
                                               :start start :end end})))
                    {:remaining-text text :nodes ()}
                    (reverse idx-seq))]
        (cond-> nodes
          (seq remaining-text)
          (conj (text-hnode remaining-text))))
      [node])))

(defn handle-text-token [{:as doc :keys [text-tokenizers]} text]
  (let [text-tokenizers (:text-tokenizers (if (zip? doc) (z/root doc) doc))]
    (reduce (fn [doc {:as node :keys [doc-handler]}] (doc-handler doc (dissoc node :doc-handler)))
            doc
            (reduce (fn [nodes tokenizer]
                      (mapcat (fn [{:as node :keys [type]}]
                                (if (= :text type) (tokenize-text-node tokenizer doc node) [node]))
                              nodes))
                    [{:type :text :text text :doc-handler push-node}]
                    text-tokenizers))))

;; clj
#_(handle-text-token (->zip {:type :doc :content []}) "some-text")

;; tokenizers
(defn normalize-tokenizer
  "Normalizes a map of regex and handler into a Tokenizer"
  [{:as tokenizer :keys [doc-handler pred handler regex tokenizer-fn]}]
  (assert (and (or doc-handler handler) (or regex tokenizer-fn)))
  (cond-> tokenizer
    (not doc-handler) (assoc :doc-handler (fn [doc {:keys [match]}] (push-node doc (handler match))))
    (not tokenizer-fn) (assoc :tokenizer-fn (partial re-idx-seq regex))
    (not pred) (assoc :pred (constantly true))))

;; TODO: rewrite in terms of zippers
#?(:cljs
   (defn current-ancestor-nodes
     "Given an open parsing context `doc`, returns the list of ancestors of the node last parsed into the document, up to but
      not including the top document."
     [{:as doc :nextjournal.markdown.impl/keys [path]}]
     (assert path "A path is needed in document context to retrieve the current node: `current-ancestor-nodes` cannot be called after `parse`.")
     (loop [p (ppop path) ancestors []]
       (if (seq p)
         (recur (ppop p) (conj ancestors (get-in doc p)))
         ancestors)))
   :clj
   (defn current-ancestor-nodes [loc]
     (loop [loc loc ancestors []]
       (let [parent (z/up loc)]
         (if (and parent (not= :doc (:type (z/node parent))))
           (recur parent (conj ancestors (z/node parent)))
           ancestors)))))

(def hashtag-tokenizer
  {:regex #"(^|\B)#[\w-]+"
   :pred #(every? (complement #{:link}) (map :type (current-ancestor-nodes %)))
   :handler (fn [match] {:type :hashtag :text (subs (match 0) 1)})})

(def internal-link-tokenizer
  {:regex #"\[\[([^\]]+)\]\]"
   :pred #(every? (complement #{:link}) (map :type (current-ancestor-nodes %)))
   :handler (fn [match] {:type :internal-link :text (match 1)})})

#_(normalize-tokenizer internal-link-tokenizer)
#_(normalize-tokenizer hashtag-tokenizer)

;; ## 🤺 Fence Info
;; `parse-fence-info` ingests nextjournal, GFM, Pandoc and RMarkdown fenced code block info (any text following the leading 3 backticks) and returns a map
;;
;; _nextjournal_ / _GFM_
;;
;;    ```python id=2e3541da-0735-4b7f-a12f-4fb1bfcb6138
;;    python code
;;    ```
;;
;; _Pandoc_
;;
;;    ```{#pandoc-id .languge .extra-class key=Val}
;;    code in language
;;    ```
;;
;; _Rmd_
;;
;;    ```{r cars, echo=FALSE}
;;    R code
;;    ```
;;
;; See also:
;; - https://github.github.com/gfm/#info-string
;; - https://pandoc.org/MANUAL.html#fenced-code-blocks
;; - https://rstudio.com/wp-content/uploads/2016/03/rmarkdown-cheatsheet-2.0.pdf"

(defn parse-fence-info [info-str]
  (try
    (when (and (string? info-str) (seq info-str))
      (let [tokens (-> info-str
                       str/trim
                       (str/replace #"[\{\}\,]" "")         ;; remove Pandoc/Rmarkdown brackets and commas
                       (str/replace "." "")                 ;; remove dots
                       (str/split #" "))]                   ;; split by spaces
        (reduce
         (fn [{:as info-map :keys [language]} token]
           (let [[_ k v] (re-matches #"^([^=]+)=([^=]+)$" token)]
             (cond
               (str/starts-with? token "#") (assoc info-map :id (str/replace token #"^#" "")) ;; pandoc #id
               (and k v) (assoc info-map (keyword k) v)
               (not language) (assoc info-map :language token) ;; language is the first simple token which is not a pandoc's id
               :else (assoc info-map (keyword token) true))))
         {}
         tokens)))
    (catch #?(:clj Throwable :cljs :default) _ {})))

(comment
  (parse-fence-info "python runtime-id=5f77e475-6178-47a3-8437-45c9c34d57ff")
  (parse-fence-info "{#some-id .lang foo=nex}")
  (parse-fence-info "#id clojure")
  (parse-fence-info "clojure #id")
  (parse-fence-info "clojure")
  (parse-fence-info "{r cars, echo=FALSE}"))

;; ## Footnote handling

(defn node-with-sidenote-refs [p-node]
  (loop [l (->zip p-node) refs []]
    (if (z/end? l)
      (when (seq refs)
        {:node (z/root l) :refs refs})
      (let [{:keys [type ref]} (z/node l)]
        (if (= :footnote-ref type)
          (recur (z/next (z/edit l assoc :type :sidenote-ref)) (conj refs ref))
          (recur (z/next l) refs))))))

(defn footnote->sidenote [{:keys [ref label content]}]
  ;; this assumes the footnote container is a paragraph, won't work for lists
  (node :sidenote (-> content first :content) nil (cond-> {:ref ref} label (assoc :label label))))

(defn insert-sidenote-containers
  "Handles footnotes as sidenotes.

   Takes and returns a parsed document. When the document has footnotes, wraps every top-level block which contains footnote references
   with a `:footnote-container` node, into each of such nodes, adds a `:sidenote-column` node containing a `:sidenote` node for each found ref.
   Renames type `:footnote-ref` to `:sidenote-ref."
  [{:as doc ::keys [path] :keys [footnotes]}]
  (if-not (seq footnotes)
    doc
    (let [root (->zip doc)]
      (loop [loc (z/down root) parent root]
        (cond
          (nil? loc)
          (-> parent z/node (assoc :sidenotes? true))
          (contains? #{:plain :paragraph :blockquote :numbered-list :bullet-list :todo-list :heading :table}
                     (:type (z/node loc)))
          (if-some [{:keys [node refs]} (node-with-sidenote-refs (z/node loc))]
            (let [new-loc (-> loc (z/replace {:type :sidenote-container :content []})
                              (z/append-child node)
                              (z/append-child {:type :sidenote-column
                                               ;; TODO: broken in the old implementation
                                               ;; should be :content (mapv #(footnote->sidenote (get footnotes %)) (distinct refs))}))]
                                               :content (mapv #(footnote->sidenote (get footnotes %)) refs)}))]
              (recur (z/right new-loc) (z/up new-loc)))
            (recur (z/right loc) parent))
          :else
          (recur (z/right loc) parent))))))
