(ns nextjournal.markdown.public-api-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
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

(deftest public-namespaces-test
  (is (= '#{nextjournal.markdown.utils.emoji
            nextjournal.markdown.transform
            nextjournal.markdown
            nextjournal.markdown.utils}
         (-> (filter #(and (str/starts-with? (str %) "nextjournal.markdown")
                           (not (str/includes? (str %) ".impl"))
                           (not (str/ends-with? (str %) "-test")))
                     (map ns-name (all-ns)))
             set))))

(deftest public-vars-test
  (is (= '#{default-hiccup-renderers
            into-hiccup
            toc->hiccup
            node->text
            table-alignment
            ->hiccup
            empty-doc
            parse
            parse*}
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
            tokenize-text-node
            text-node
            insert-sidenote-containers
            block-formula
            empty-doc
            hashtag-tokenizer
            formula}
         (public-vars 'nextjournal.markdown.utils)))
  (is (= '#{regex}
         (public-vars 'nextjournal.markdown.utils.emoji))))
