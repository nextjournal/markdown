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
                        (dissoc x
                                ;; TODO
                                :meta :hidden :attrs :map :nesting
                                :block :level))
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
           (mapv :type (m/tokenize "hello world")))))

  (testing "hello world 2"
    (let [[expected actual] (compare-tokenize "hello world")]
      (is (match? expected actual)))))
