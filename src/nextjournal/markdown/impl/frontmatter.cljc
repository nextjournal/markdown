(ns nextjournal.markdown.impl.frontmatter
  "Taken mainly from markdown-clj"
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn parse-metadata-line
  "Given a line of metadata header text return either a list containing a parsed
  and normalizd key and the original text of the value, or if no header is found
  (this is a continuation or new value from a pervious header key) simply
  return the text. If a blank or invalid line is found return nil."
  [line]
  (when line
    (let [[_ key val] (re-matches #"^([0-9A-Za-z_-]*):(.*)$" line)
          [_ next-val] (re-matches #"^    (.*)$" line)]
      (when (not= (str/trim line) "")
        (cond
          key [(keyword (str/lower-case key)) val]
          next-val line)))))

(defn flatten-metadata
  "Given a list of maps which contain a single key/value, flatten them all into
  a single map with all the leading spaces removed. If an empty list is provided
  then return nil."
  [metadata]
  (when (pos? (count metadata))
    (loop [acc      {}
           remain   metadata
           prev-key nil]
      (if (seq remain)
        (let [data     (first remain)
              [key val] (if (sequential? data) data [prev-key data])
              prev-val (get acc key [])
              postfix  (if (= [\space \space] (take-last 2 val)) \newline "")
              norm-val (str (str/trim val) postfix)
              new-val  (if-not (empty? norm-val)
                         (conj prev-val norm-val)
                         prev-val)]
          (recur (merge acc {key new-val}) (rest remain) key))
        acc))))

(defn parse-wiki-metadata-headers
  [lines-seq]
  (reduce
   (fn [acc line]
     (if-let [parsed (parse-metadata-line line)]
       (conj acc parsed)
       (reduced [{:type :multimarkdown
                  :value (flatten-metadata acc)} (count acc)])))
   [] lines-seq))

(defn parse-yaml-metadata-headers
  [lines-seq opts]
  (let [yaml-parse-fn (or (get-in opts [:yaml-parse-fn])
                          (throw (ex-info "No :yaml-parse-fn provided" {})))
        yaml-lines (->> lines-seq
                        ;; leave off opening ---
                        (drop 1)
                        ;; take lines until we see the closing ---
                        (take-while (comp not (partial re-matches #"---\s*"))))]
    [{:type :yaml
      :value (->> yaml-lines
                  ;; join together and parse
                  (str/join \newline)
                  yaml-parse-fn)}
     ;; number of lines consumed must consider opening and closing ---
     (+ (count yaml-lines) 2)]))

(defn parse-edn-metadata-headers
  [lines-seq]
  ;; take sequences until you hit an empty line
  (let [meta-lines (take-while (comp not (partial re-matches #"\s*"))
                               lines-seq)]
    [{:type :edn
      :value (->> meta-lines
                  ;; join together and parse
                  (str/join \newline)
                  edn/read-string)}
     ;; count the trailing empty line
     (inc (count meta-lines))]))

(defn parse-frontmatter
  "Given a sequence of lines from a markdown document, attempt to parse a
  metadata header if it exists. Accepts wiki, yaml, and edn formats.
  Returns the parsed headers number of lines the metadata spans"
  [markdown-text opts]
  (let [lines-seq (str/split-lines markdown-text)
        [frontmatter n :as x] (cond
                           ;; Treat as yaml
                           (re-matches #"---\s*" (first lines-seq))
                           (parse-yaml-metadata-headers lines-seq opts)
                           ;; Treat as wiki
                           (re-matches #"\w+:\s+.*" (first lines-seq))
                           (parse-wiki-metadata-headers lines-seq)
                           ;; Treat as edn
                           (re-matches #"\{.*" (first lines-seq))
                           (parse-edn-metadata-headers lines-seq))]
    (def x x)
    [frontmatter (str/join \newline (drop n lines-seq))]))

(comment
  (parse-frontmatter "Foo: [bar, baz]

# Dude" nil)
  )
