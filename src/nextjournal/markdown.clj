(ns nextjournal.markdown
  "Facility functions for handling markdown conversions"
  (:require [clojure.java.io :as io]
            [clojure.data.json :as json]
            [nextjournal.markdown.parser :as markdown.parser]
            [nextjournal.markdown.transform :as markdown.transform])
  (:import (org.graalvm.polyglot Context Context$Builder Source Value)
           (java.io Reader)))

(set! *warn-on-reflection* true)

(def ^Context$Builder context-builder
  (doto (Context/newBuilder (into-array String ["js"]))
    (.option "js.timer-resolution" "1")
    (.option "js.java-package-globals" "false")
    (.option "js.esm-eval-returns-exports", "true")
    ;; ⬆ returns module exports when evaling an esm module file, note this will only work on stock JVM or on GraalJVM
    ;; matching the poliglot library versions specified in deps.edn
    (.out System/out)
    (.err System/err)
    (.allowIO true)
    (.allowExperimentalOptions true)
    (.allowAllAccess true)
    (.allowNativeAccess true)
    (.option "engine.WarnInterpreterOnly" "false")))

(def ^Context ctx (.build context-builder))

(def ^Value MD-imports
  ;; Contructing a `java.io.Reader` first to work around a bug with graal on windows
  ;; see https://github.com/oracle/graaljs/issues/534 and https://github.com/nextjournal/viewers/pull/33
  (let [source (-> (io/resource "js/markdown.mjs")
                   io/input-stream
                   io/reader
                   (as-> r (Source/newBuilder "js" ^Reader r "markdown.mjs")))]
    (.. ctx
        (eval (.build source))
        (getMember "default"))))

(def ^Value tokenize-fn (.getMember MD-imports "tokenizeJSON"))

(defn tokenize [markdown-text]
  (let [^Value tokens-json (.execute tokenize-fn (to-array [markdown-text]))]
    (json/read-str (.asString tokens-json) :key-fn keyword)))

(defn parse
  "Turns a markdown string into a nested clojure structure."
  ([markdown-text] (parse markdown.parser/empty-doc markdown-text))
  ([doc markdown-text] (markdown.parser/parse doc (tokenize markdown-text))))

(defn ->hiccup
  "Turns a markdown string into hiccup."
  ([markdown-text] (->hiccup markdown.transform/default-hiccup-renderers markdown-text))
  ([ctx markdown-text] (->> markdown-text parse (markdown.transform/->hiccup ctx))))

(comment
  (tokenize "# Title
- [ ] one
- [x] two
")

  (parse "# Hello Markdown
- [ ] what
- [ ] [nice](very/nice/thing)
- [x] ~~thing~~
")

  (->hiccup "# Hello Markdown

* What's _going_ on?
")

  (->hiccup
   (assoc markdown.transform/default-hiccup-renderers
          :heading (fn [ctx node]
                     [:h1.some-extra.class
                      (markdown.transform/into-markup [:span.some-other-class] ctx node)]))
   "# Hello Markdown
* What's _going_ on?
")

  ;; launch shadow cljs repl
  (shadow.cljs.devtools.api/repl :sci))
