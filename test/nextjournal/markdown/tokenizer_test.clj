(ns nextjournal.markdown.tokenizer-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [clojure.walk :as w]
            [nextjournal.markdown :as m]
            [nextjournal.markdown.tokenizer :as t]))

(defn reduce-noise [tokenized]
  (w/postwalk (fn [x]
                (cond->> x
                  (map? x) (into {}
                                 (filter (fn [[_ v]] (if (string? v)
                                                      (not (str/blank? v))
                                                      (some? v)))))))
              tokenized))

(defn reference-tokenize
  "Reference implementation of tokenize using `markdown-it`."
  [s]
  (-> s m/tokenize reduce-noise))

(defn compare-tokenize
  ([s] [(reference-tokenize s) (t/tokenize s)])
  ([f s] (mapv #(mapv f %) (compare-tokenize s))))

#_(compare-tokenize "hello")

(deftest tokenize
  (testing "hello world"
    (is (= (mapv :type (reference-tokenize "hello world"))
           (mapv :type (m/tokenize "hello world")))))

  (testing "hello world 2"
    (is (apply = (compare-tokenize "hello world")))))
