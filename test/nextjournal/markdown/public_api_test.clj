(ns nextjournal.markdown.public-api-test
  (:require [clojure.test :refer [deftest is]]
            [nextjournal.markdown]
            [nextjournal.markdown.transform]
            [nextjournal.markdown.utils]))

(defn var-name [v]
  (-> v meta :name))

(defn public-vars [ns]
  (remove-ns ns)
  (require ns :reload)
  (->> (ns-publics ns)
       vals
       (map var-name)
       set))

(deftest public-api-test
  (is (= '#{->hiccup empty-doc parse parse*}
         (public-vars 'nextjournal.markdown)))
  (is (= '#{default-hiccup-renderers
            toc->hiccup
            table-alignment
            ->hiccup
            into-markup
            ->text}
         (public-vars 'nextjournal.markdown.transform)))
  (is (= '#{normalize-tokenizer
            internal-link-tokenizer
            hashtag-tokenizer
            empty-doc
            insert-sidenote-containers}
       (public-vars 'nextjournal.markdown.utils))))
