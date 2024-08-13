(ns nextjournal.markdown.parser.impl.utils)

(def empty-doc {:type :doc
                :content []
                ;; Id -> Nat, to disambiguate ids for nodes with the same textual content
                :nextjournal.markdown.parser.impl/id->index {}
                ;; Node -> {id : String, emoji String}, dissoc from context to opt-out of ids
                :toc {:type :toc}
                :footnotes []
                :nextjournal.markdown.parser.impl/path [:content -1] ;; private
                :text-tokenizers []})

#?(:clj (defn re-groups* [m] (let [g (re-groups m)] (cond-> g (not (vector? g)) vector))))
(defn re-idx-seq
  "Takes a regex and a string, returns a seq of triplets comprised of match groups followed by indices delimiting each match."
  [re text]
  #?(:clj (let [m (re-matcher re text)]
            (take-while some? (repeatedly #(when (.find m) [(re-groups* m) (.start m) (.end m)]))))
     :cljs (let [rex (js/RegExp. (.-source re) "g")]
             (take-while some? (repeatedly #(when-some [m (.exec rex text)] [(vec m) (.-index m) (.-lastIndex rex)]))))))

;; TODO: rewrite in terms of zippers
(def ppop (comp pop pop))

(defn current-ancestor-nodes
  "Given an open parsing context `doc`, returns the list of ancestors of the node last parsed into the document, up to but
   not including the top document."
  [{:as doc :nextjournal.markdown.parser.impl/keys [path]}]
  (assert path "A path is needed in document context to retrieve the current node: `current-ancestor-nodes` cannot be called after `parse`.")
  (loop [p (ppop path) ancestors []]
    (if (seq p)
      (recur (ppop p) (conj ancestors (get-in doc p)))
      ancestors)))

(def hashtag-tokenizer
  {:regex #"(^|\B)#[\w-]+"
   :pred #(every? (complement #{:link}) (map :type (current-ancestor-nodes %)))
   :handler (fn [match] {:type :hashtag :text (subs (match 0) 1)})})

(def internal-link-tokenizer
  {:regex #"\[\[([^\]]+)\]\]"
   :pred #(every? (complement #{:link}) (map :type (current-ancestor-nodes %)))
   :handler (fn [match] {:type :internal-link :text (match 1)})})


(comment
  (re-idx-seq #"\{\{([^{]+)\}\}" "foo {{hello}} bar")
  (re-idx-seq #"\{\{[^{]+\}\}" "foo {{hello}} bar {{what}} the"))
