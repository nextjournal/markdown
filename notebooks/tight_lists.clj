;; # Tight Lists

^{:nextjournal.clerk/visibility :hide-ns :nextjournal.clerk/toc :collapsed}
(ns ^:nextjournal.clerk/no-cache tight-lists
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [nextjournal.markdown.transform :as md.transform]))

;; Markdown (commonmark) distingushes between [loose and tight lists](https://spec.commonmark.org/0.30/#loose)
;;
;; > A list is loose if any of its constituent list items are separated by blank lines, or
;; > if any of its constituent list items directly contain two block-level elements with a blank line between them.
;; > Otherwise a list is tight. (The difference in HTML output is that paragraphs in a loose list are wrapped in `<p>` tags,
;; > while paragraphs in a tight list are not.)
;;
;; We're solving this ambiguity by getting closer to Pandoc types: introduce a `:plain` type, that is a container for
;; inline elemets which is not a paragraph. The advantage will be two-fold:
;;
;; * being able to distinguish among tight and loose lists via markup
;; * get closer to the Pandoc and ease document format conversions
;;
;; ## Markdown-It
;;
;; Markdown-it hides handling of tight/loose behind an [obscure `:hidden` property on the token](https://github.com/markdown-it/markdown-it/blob/8bcc82aa74164a5e13a104f433c26671a92ed872/lib/token.js#L111-L116).
;;
;;     * Token#hidden -> Boolean
;;     *
;;     * If it's true, ignore this element when rendering. Used for tight lists
;;     * to hide paragraphs.
;;     **/
;;     this.hidden = false
;;
;; so when a paragraph is marked as hidden, markdown-it will unwrap its contents.

(defn flat-tokenize [text]
  (into []
        (comp
         (mapcat (partial tree-seq (comp seq :children) :children))
         (map #(select-keys % [:type :content :hidden :level :info])))
        (md/tokenize text)))

;; Some examples follow of a
;; * tight list
(flat-tokenize "
- one
- two")

;; * loose list because of an inner paragraph
(flat-tokenize "
- one

  inner par
- two")

;; * loose list with 2-newline separated items
(flat-tokenize "
- one

- two")

(flat-tokenize "
- one
  * thight sub one
- two
")

;; ## Pandoc to the Rescue
;;
;; To comply with this behaviour [Pandoc uses a `Plain` container type](https://github.com/jgm/pandoc-types/blob/694c383dd674dad97557eb9b97adda17079ebb2c/src/Text/Pandoc/Definition.hs#L275-L278), and I think we should follow their advice

(defn ->pandoc-ast [text]
  (json/read-str
   (:out
    (shell/sh "pandoc" "-f" "markdown" "-t" "json" :in text))
   :key-fn keyword))

;; Again, tight
(->pandoc-ast "
- one
- two
")

;; and loose lists
(->pandoc-ast "
- one

  inner par
- two
")

(->pandoc-ast "
- one

- two
")

(->pandoc-ast "
- one
  * thignt sub one
- two
")

;; ## Adding Plain nodes
;; We're pushing plain nodes into the document when encoutering a paragraph token wiht the `:hidden` flag.

(md/parse "
* this
* is
* tight!")

(md/parse "
* this
* is
  > very loose

  indeed
* fin")

;; in terms of Clerk support, that amounts to introduce a new viewer, the natural candidate for rendering plain nodes is
;; the empty container `:<>`
(clerk/add-viewers! [{:name ::md/plain
                      :transform-fn (v/into-markup [:<>])}])

^{::clerk/visibility :hide ::clerk/viewer :hide-result}
(comment
  (clerk/serve! {:port 9999})
  (-> *e ex-cause ex-data))
