(ns nextjournal.clerk.sci-ext
  (:require [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.sci-viewer :as sv]
            [nextjournal.markdown :as md]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.devcards :as dc]
            [nextjournal.viewer.markdown :as viewer.markdown]
            [nextjournal.markdown.transform :as md.transform]
            [nextjournal.viewer.code :as viewer.code]
            ["react" :as react]
            [reagent.core :as r]
            [nextjournal.clojure-mode :as clojure-mode]
            ["@codemirror/view" :refer [EditorView highlightActiveLine highlightSpecialChars ViewPlugin keymap]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/language" :refer [defaultHighlightStyle syntaxHighlighting foldGutter LanguageSupport]]
            ["@codemirror/lang-markdown" :as MD :refer [markdown markdownLanguage]]
            [sci.core :as sci]))

(def theme (j/lit {"&.cm-editor.cm-focused" {:outline "none"}
                   ".cm-activeLine" {:background-color "rgb(226 232 240)"}
                   ".cm-line" {:padding "0"
                               :line-height "1.6"
                               :font-size "15px"
                               :font-family "\"Fira Mono\", monospace"}}))

(defn update-plugin [doc-update _view] (j/obj :update (fn [update] (doc-update (.. update -state -doc toString)))))

;; syntax (an LRParser) + support (a set of extensions)
(def clojure-lang (LanguageSupport. (clojure-mode/syntax)
                                    (.. clojure-mode/default-extensions (slice 1))))

;; duplicate code viewer logic to get content editable + fix concurrent issues related to clojure-mode
(defn editor [{:as opts :keys [lang doc-update doc editable?] :or {editable? true}}]
  [:div {:ref (fn [el]
                (when el
                  (let [prev-view (j/get el :editorView)]
                    (when (or (nil? prev-view)
                              (and (not editable?)
                                   (not= doc (.. prev-view -state toString))))
                      (some-> prev-view (j/call :destroy))
                      (j/assoc! el :editorView
                                (EditorView. (j/obj :parent el
                                                    :state (.create EditorState
                                                                    (j/obj :doc (str/trim doc)
                                                                           :extensions (into-array
                                                                                        (cond-> [(syntaxHighlighting defaultHighlightStyle (j/obj :fallback true))
                                                                                                 (.. EditorState -allowMultipleSelections (of editable?))
                                                                                                 (.. EditorView -editable (of editable?))
                                                                                                 (.theme EditorView theme)]

                                                                                          doc-update
                                                                                          (conj (.define ViewPlugin (partial update-plugin doc-update)))

                                                                                          (= lang :clojure)
                                                                                          (conj (j/get clojure-lang :extension))

                                                                                          (= lang :markdown)
                                                                                          (conj (markdown (j/obj :base markdownLanguage
                                                                                                                 :defaultCodeLanguage clojure-lang))))))))))))))}])

(defn markdown-editor [opts]
  (editor (assoc opts :lang :markdown)))

(defn eval-string [source]
  (when-some [code (not-empty (str/trim source))]
    (try {:result (sci/eval-string* @sv/!sci-ctx code)}
         (catch js/Error e
           {:error (str (.-message e))}))))

(defn clojure-editor [{:as opts :keys [editable?] :or {editable? false}}]
  (r/with-let [!text (r/atom "")]
    [:div
     [:div.p-2.bg-slate-100
      [editor (assoc opts :lang :clojure :editable? editable? :doc-update (partial reset! !text))]]
     [:div.viewer-result.mt-3.ml-5
      (try
        (when-some [{:keys [error result]}
                    (when-some [code (not-empty (str/trim @!text))]
                      (eval-string code))]
          (cond
            error [:div.red error]
            (react/isValidElement result) result
            'else (sv/inspect-paginated result))))]]))

(def markdown-renderers
  (assoc viewer.markdown/default-renderers
         :code (fn [_ctx node]
                 [clojure-editor {:doc (md.transform/->text node)}])))

(dc/defcard editor
  [:div
   [markdown-editor {:doc-update (constantly :ok)
                     :doc "# Hello
~~foo~~
_this_ is a **strong** text

```clojure
(reduce + [1 2 3])
```
"}]
   [:hr.mb-4]
   [clojure-editor {:doc "(+ 1 2 3)" :editable? true}]])

(sci/merge-opts @sv/!sci-ctx
                {:namespaces {'md {'parse md/parse}
                              'md.transform {'->hiccup md.transform/->hiccup}
                              'md.demo {'editor markdown-editor
                                        'renderers markdown-renderers
                                        'inspect-expanded (fn [x]
                                                            (sv/inspect {:!expanded-at (atom (constantly true))}
                                                                        (v/present x)))}}})

(comment
  (js/console.log (new LanguageSupport
                       (j/obj :language (clojure-mode/syntax)
                              :extensions (.slice clojure-mode/default-extensions 1))))

  (js/console.log (clojure-mode/syntax) )
  (do foldGutter )
  (js/console.log (markdown (j/obj :base markdownLanguage
                                   :addKeymap false
                                   :defaultCodeLanguage
                                   (LanguageSupport. (clojure-mode/syntax)
                                                     (.. clojure-mode/default-extensions
                                                         (slice 1))))))


  ;; ["@codemirror/language-data" :refer [languages]]
 ;; Closure compilation failed with 1 errors
 ;; --- node_modules/@lezer/php/dist/index.cjs:49
 ;; Illegal redeclared variable: global

 )
