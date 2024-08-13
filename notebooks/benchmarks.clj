;; # ⏱ Some Naïve Benchmarks
(ns ^:nextjournal.clerk/no-cache benchmarks
  (:require [clojure.test :refer :all]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.eval :as clerk.eval]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.commonmark :as commonmark]
            parsing-extensibility
            [nextjournal.markdown.parser :as md.parser]))

(def reference-text (slurp "notebooks/reference.md"))

(defmacro time-ms [& expr]
  `(-> (clerk.eval/time-ms (dotimes [_# 100] ~@expr)) :time-ms (/ 100)))

(comment
  (macroexpand '(time-ms do-this)))

;; Compare with different set of tokenizers
(defn parse
  ([text] (parse [] text))
  ([extra-tokenizers text]
   (md/parse (update md.parser/empty-doc :text-tokenizers concat extra-tokenizers)
             text)))

(comment

  ;; Default set of tokenizers
  (time-ms (parse reference-text))
  (time-ms (commonmark/parse reference-text))

  (-> (parse reference-text)
      :content count )

  (-> (commonmark/parse reference-text)
   :content count )


  ;; With an extra brace-brace parser
  (time-ms (parse [{:regex #"\{\{([^\{]+)\}\}"
                    :handler (fn [m] {:type :var :text (m 1)})}]
                  reference-text))

  ;; With the losange reader
  (time-ms (parse [{:regex #"\{\{([^\{]+)\}\}"
                    :handler (fn [m] {:type :var :text (m 1)})}
                   {:tokenizer-fn parsing-extensibility/losange-tokenizer-fn
                    :handler (fn [data] {:type :losange :data data})}]
                  reference-text))

  ;; With hashtags and internal links
  (time-ms
   (parse [md.parser/hashtag-tokenizer
           md.parser/internal-link-tokenizer
           {:regex #"\{\{([^\{]+)\}\}"
            :handler (fn [m] {:type :var :text (m 1)})}
           {:tokenizer-fn parsing-extensibility/losange-tokenizer-fn
            :handler (fn [data] {:type :losange :data data})}]
          reference-text)))

^{::clerk/visibility {:code :hide :result :hide}}
(comment
  (clerk/serve! {:port 8888}))
