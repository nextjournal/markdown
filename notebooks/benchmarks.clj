;; # ⏱ Some Naïve Benchmarks
(ns ^:nextjournal.clerk/no-cache benchmarks
  (:require [clojure.test :refer :all]
            [nextjournal.clerk :as clerk]
            [nextjournal.markdown :as md]
            parsing-extensibility
            [nextjournal.markdown.parser :as md.parser]))

(def reference-text (slurp "notebooks/reference.md"))

(defmacro time-ms [& expr]
  `(-> (clerk/time-ms (dotimes [_# 100] ~@expr)) :time-ms (/ 100)))

(comment
  (macroexpand '(time-ms do-this)))

;; Compare with different set of tokenizers
(defn parse
  ([text] (parse [] text))
  ([extra-tokenizers text]
   (md.parser/parse (update md.parser/empty-doc :text-tokenizers concat extra-tokenizers)
                    (md/tokenize text))))

;; Default set of tokenizers
(time-ms (parse reference-text))

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

(parse [{:regex #"\{\{([^\{]+)\}\}"
         :handler (fn [m] {:type :var :text (m 1)})}
        {:tokenizer-fn parsing-extensibility/losange-tokenizer-fn
         :handler (fn [data] {:type :losange :data data})}]
       reference-text)

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 8888}))
