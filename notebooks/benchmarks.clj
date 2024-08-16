;; # ⏱ Some Naïve Benchmarks
(ns benchmarks
  {:nextjournal.clerk/no-cache true}
  (:require [clojure.test :refer :all]
            [nextjournal.clerk.eval :as clerk.eval]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.graaljs :as old-md]
            [nextjournal.markdown.utils :as u]
            [parsing-extensibility]))

(def reference-text (slurp "notebooks/reference.md"))

(defmacro time-ms [& expr]
  `(-> (clerk.eval/time-ms (dotimes [_# 100] ~@expr)) :time-ms (/ 100)))

(comment
  (macroexpand '(time-ms do-this)))

;; Compare with different set of tokenizers
(defn parse
  ([text] (parse [] text))
  ([extra-tokenizers text]
   (md/parse (assoc u/empty-doc :text-tokenizers extra-tokenizers)
             text)))

(-> (parse reference-text) :content count)

;; Default set of tokenizers, warmup
[(time-ms (parse reference-text))
 (time-ms (parse reference-text))
 (time-ms (parse reference-text))]

;; GraalJS based implementation
[(time-ms (old-md/parse reference-text))
 (time-ms (old-md/parse reference-text))
 (time-ms (old-md/parse reference-text))]

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
 (parse [u/hashtag-tokenizer
         u/internal-link-tokenizer
         {:regex #"\{\{([^\{]+)\}\}"
          :handler (fn [m] {:type :var :text (m 1)})}
         {:tokenizer-fn parsing-extensibility/losange-tokenizer-fn
          :handler (fn [data] {:type :losange :data data})}]
        reference-text))
