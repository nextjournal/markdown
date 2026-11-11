(ns nextjournal.markdown.render-test
  (:require [clojure.string :as str]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]))

(def inline-token
  (gen/one-of
   [(gen/elements ["a" "b" "word" "x1" "2" "10" " " "  "])
    (gen/elements ["*" "**" "_" "__" "~" "~~" "`" "``" "$" "$$" "[" "]" "(" ")" "![" "](" "\\"
                   "<" ">" "&" "&amp;" "#" "|" "^" "[^n]" "^[" "-" "+" "=" "." ")" "!" "\"" ":"])
    (gen/elements ["[x](/u)" "[x](/u \"t\")" "![i](/s)" "<b>" "</b>" "https://example.com"
                   "`code`" "$x$" "*em*" "**strong**" "~~del~~" "a\\" "\\*"])]))

(def inline-text
  (gen/fmap str/join (gen/vector inline-token 0 8)))

(def line-prefix
  (gen/elements ["" "" "" "# " "## " "- " "* " "+ " "1. " "2) " "> " "> > " "  " "   " "    "
                 "- [ ] " "- [x] " "[^n]: " "  - " "    1. "]))

(def line
  (gen/one-of
   [(gen/fmap str/join (gen/tuple line-prefix inline-text))
    (gen/elements ["" "" "---" "===" "***" "```" "```clj" "~~~" "$$" "| a | b |" "|-|:-:|"
                   "<div>" "</div>" "[[TOC]]"])]))

(def markdown-text
  (gen/fmap #(str/join "\n" %) (gen/vector line 0 12)))

(defn- nodes [doc]
  (tree-seq :content :content doc))

(defn- representable? [doc]
  (and (not-any? #(or (and (= :block-formula (:type %)) (str/includes? (:text %) "$$"))
                      (and (= :table-row (:type %)) (empty? (:content %))))
                 (nodes doc))
       (not-any? (comp #{:footnote-ref :plain} :type) (mapcat nodes (:footnotes doc)))))

(defn- unreferenced-footnote? [s]
  (and (str/includes? s "[^n]:")
       (= (count (re-seq #"\[\^n\]" s)) (count (re-seq #"\[\^n\]:" s)))))

(def parsable-markdown-text
  (gen/such-that #(not (or (re-find #"\[\^n\]: [\s\S]*(\^\[|\[\^n\]:)|\^\[[^\]]*\^\[" %)
                           (unreferenced-footnote? %)))
                 markdown-text 100))

(def representable-markdown
  (gen/such-that (fn [[_ doc]] (representable? doc))
                 (gen/fmap (fn [s] [s (md/parse s)]) parsable-markdown-text)
                 100))

(defspec parse-of-rendered-parse-equals-parse 500
  (prop/for-all [[_ doc] representable-markdown]
    (= doc (md/parse (md.transform/->markdown doc)))))
