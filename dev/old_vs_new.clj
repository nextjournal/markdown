(ns old-vs-new
  (:require [clojure.string :as str]
            [nextjournal.markdown.graaljs :as md-old]
            [nextjournal.markdown :as md]
            [babashka.http-client :as http]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(comment
  (:body (http/get "https://jaspervdj.be/lorem-markdownum/markdown.txt?fenced-code-blocks=on"))

  (let [sample (:body (http/get "https://jaspervdj.be/lorem-markdownum/markdown.txt?num-blocks=1000&fenced-code-blocks=on"))]
    [(with-out-str (time (md-old/parse sample)))
     (with-out-str (time (md/parse sample)))]))

(def feature-keys
  (list "no-headers"
        "no-code"
        "no-quotes"
        "no-lists"
        "no-inline-markup"
        "no-external-links"
        "underline-headers"
        "underscore-em"
        "underscore-strong"
        "no-wrapping"
        "fenced-code-blocks"
        "reference-links"))

(defn opts->query [opts]
  (str/join "&" (keep (fn [[k v]] (when v (str k "=on"))) opts)))

(defn size+opts->random-md-str [size opts]
  (:body (http/get (format "https://jaspervdj.be/lorem-markdownum/markdown.txt?%s&num-blocks=%s" (opts->query opts) size))))

(def md-generator
  (gen/sized (fn [size]
               (prn :size size)
               (gen/fmap #(size+opts->random-md-str size %)
                         (gen/fmap (partial zipmap feature-keys) (gen/vector gen/boolean 12))))))

#_(gen/sample md-generator)

(def compare-old-vs-new-parse-implementations
  (prop/for-all [s md-generator]
    (= (md/parse s)
       (md-old/parse s))))

#_(tc/quick-check 100 compare-old-vs-new-parse-implementations)

(defspec test-old-vs-new-implem 100
  compare-old-vs-new-parse-implementations )

#_(test-old-vs-new-implem)
