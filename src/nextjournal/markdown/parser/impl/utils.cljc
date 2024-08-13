(ns nextjournal.markdown.parser.impl.utils
  (:require [clojure.string :as str]
            [clojure.zip :as z]))

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

;; zipper utils

(defn ->zip [doc]
  (z/zipper (every-pred map? :type) :content
            (fn [node cs] (assoc node :content (vec cs)))
            doc))

;; TODO: rewrite in terms of zippers
(def ppop (comp pop pop))

;; TODO: rewrite in terms of zippers
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

(defn parse-fence-info [info-str]
  (try
    (when (string? info-str)
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
  (->zip {:type :doc :content []})


  (re-idx-seq #"\{\{([^{]+)\}\}" "foo {{hello}} bar")
  (re-idx-seq #"\{\{[^{]+\}\}" "foo {{hello}} bar {{what}} the"))
