(ns nextjournal.markdown.tokenizer-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t :refer [deftest is testing]]
   [clojure.walk :as w]
   [matcher-combinators.test :refer [match?]]
   [nextjournal.markdown :as m]
   [nextjournal.markdown.tokenizer :refer [tokenize]]))

(defn reduce-noise [tokenized]
  (w/postwalk (fn [x]
                (cond
                  (map? x)
                  (into {}
                        (filter (fn [[_ v]] (if (string? v)
                                              (not (str/blank? v))
                                              (some? v))))
                        (-> x
                            (select-keys [:type, :content, :children, :attrs :meta])
                            ;; TODO
                            (dissoc x
                                    :meta :attrs
                                    )))
                  :else x))
              tokenized))

(defn reference-tokenize
  "Reference implementation of tokenize using `markdown-it`."
  [s]
  (-> s m/tokenize reduce-noise))

(defn compare-tokenize
  ([s] [(reference-tokenize s) (tokenize s)])
  ([f s] (mapv #(mapv f %) (compare-tokenize s))))

#_(compare-tokenize "hello")

(deftest tokenize-test
  (testing "hello world"
    (is (= (mapv :type (reference-tokenize "hello world"))
           (mapv :type (m/tokenize "hello world"))))))

(deftest paragraphs-test
  (testing "hello world"
    (let [[expected actual] (compare-tokenize "hello world")]
      (is (match? expected actual))))
  (testing "multiple paragraphs"
    (let [[expected actual] (compare-tokenize "first paragraph

second paragraph")]
      (is (match? expected actual))))
  (testing "multiple paragraphs separated by multiple blank lines"
    (let [[expected actual] (compare-tokenize "first paragraph


second paragraph")]
      (is (match? expected actual))))
  (testing "paragraphs with multiple lines"
    (let [[expected actual] (compare-tokenize "first paragraph
first paragraph sentence two


second paragraph
second paragraph sentence two")]
      (is (match? expected actual)))))

;;;; Scratch

(comment
  (require '[hiccup2.core :refer [html]])
  (str (html (m/->hiccup "hello
there")))
  (m/parse "hello
there")
  )
