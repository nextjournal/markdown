;; # Tight Lists
(ns tight-lists
  {:nextjournal.clerk/no-cache true}
  (:require [clojure.data.json :as json]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pprint]
            [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.markdown :as md]))

;; Markdown (commonmark) distingushes between [loose and tight lists](https://spec.commonmark.org/0.30/#loose)
;;
;; > A list is loose if any of its constituent list items are separated by blank lines, or
;; > if any of its constituent list items directly contain two block-level elements with a blank line between them.
;; > Otherwise a list is tight. (The difference in HTML output is that paragraphs in a loose list are wrapped in `<p>` tags,
;; > while paragraphs in a tight list are not.)
;;
;; ## Pandoc to the Rescue
;;
;; To comply with this behaviour [Pandoc uses a `Plain` container type](https://github.com/jgm/pandoc-types/blob/694c383dd674dad97557eb9b97adda17079ebb2c/src/Text/Pandoc/Definition.hs#L275-L278), and I think we should follow their advice

^{::clerk/visibility {:result :hide}}
(defn ->pandoc-ast [text]
  (clerk/html [:pre
               (with-out-str
                 (clojure.pprint/pprint
                  (json/read-str
                   (:out
                    (shell/sh "pandoc" "-f" "markdown" "-t" "json" :in text))
                   :key-fn keyword)))]))

;; tight
(->pandoc-ast "
- one
- two
")

;; vs loose lists
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

^{::clerk/visibility {:result :hide}}
(defn example [md-string]
  (v/html
   [:div.flex-col
    [:pre.code md-string]
    [:pre.code (with-out-str
                 (clojure.pprint/pprint
                  (dissoc (md/parse md-string) :toc :title :footnotes)))]
    [:pre.code (with-out-str
                 (clojure.pprint/pprint
                  (md/->hiccup md-string)))]
    (v/html (md/->hiccup md-string))
    ;; TODO: fix in clerk
    #_
    (v/html (str (h/html (md/->hiccup md-string))))]))

(clerk/present!
 (example "
* this
* is
* tight!"))

(example "
* this
* is
  > very loose

  indeed
* fin")

(example "* one \\
  hardbreak
* two")

(example "
* one
  softbreak
* two")

;; https://spec.commonmark.org/0.30/#example-314 (loose list)
(example "- a\n- b\n\n- c")
;; https://spec.commonmark.org/0.30/#example-319 (tight with loose sublist inside)
(example "- a\n  - b\n\n    c\n- d\n")
;; https://spec.commonmark.org/0.30/#example-320 (tight with blockquote inside)
(example "* a\n  > b\n  >\n* c")
