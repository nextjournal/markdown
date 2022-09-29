;; # 🗜 Clerk to Markdown
(ns clerk_to_markdown
  {:nextjournal.clerk/no-cache true}
  (:require [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.parser :as clerk.parser]
            [nextjournal.markdown.transform :as md.transform]))

;; This notebook shows how to transform a Clojure namespace with Clerk style _literal fragments_ into a [markdown](https://daringfireball.net/projects/markdown/) file.

(def this-notebook "notebooks/clerk_to_markdown.clj")

;; A clerk notebook is composed of blocks, of type `:markdown` and `:code`. With recent additions to the markdown library we can turn our markdown AST data back into markdown text.

;; This function turns a Clerk block into a markdown string

(defn block->md [{:as block :keys [type text doc]}]
  (case type
    :code (str "```clojure\n" text "\n```\n\n")
    :markdown (md.transform/->md doc)))

;; to put everything together, parse this notebook with Clerk and emit markdown as follows.

^{::clerk/viewer '(fn [s _] (v/html [:pre s]))}
(def as-markdown
  (->> this-notebook
       (clerk.parser/parse-file {:doc? true})
       :blocks
       (map block->md)
       (apply str)))

;; We can go back to a clojure namespace as follows
(defn block->clj [{:as block :keys [type text doc]}]
  (case type
    :code (str "\n" text "\n")
    :markdown (apply str
                     (interleave (repeat ";; ")
                                 (str/split-lines (md.transform/->md doc))
                                 (repeat "\n")))))

^{::clerk/viewer '(fn [s _] (v/html [:pre s]))}
(->> (clerk.parser/parse-markdown-string {:doc? true} as-markdown)
     :blocks
     (map block->clj)
     (apply str))

;; To learn more about clerk visit our [github page](https://github.com/nextjournal/clerk).
