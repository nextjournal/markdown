(ns nextjournal.clerk.sci-ext
  (:require ["katex" :as katex]
            ["react" :as react]
            ["@codemirror/view" :refer [EditorView highlightActiveLine highlightSpecialChars ViewPlugin keymap]]
            ["@codemirror/state" :refer [EditorState]]
            ["@codemirror/language" :refer [defaultHighlightStyle syntaxHighlighting foldGutter LanguageSupport]]
            ["@codemirror/lang-markdown" :as MD :refer [markdown markdownLanguage]]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [nextjournal.clerk.render :as render]
            [nextjournal.clerk.sci-env]
            [nextjournal.clerk.viewer :as v]
            [nextjournal.clojure-mode :as clojure-mode]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.transform :as md.transform]
            [reagent.core :as r]
            [sci.core :as sci]
            [sci.ctx-store]))

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
                                                                                        (cond-> [(syntaxHighlighting defaultHighlightStyle)
                                                                                                 (.. EditorState -allowMultipleSelections (of editable?))
                                                                                                 #_ (foldGutter)
                                                                                                 (.. EditorView -editable (of editable?))
                                                                                                 (.of keymap clojure-mode/complete-keymap)
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
    (try {:result (sci/eval-string* (sci.ctx-store/get-ctx) code)}
         (catch js/Error e
           {:error (str (.-message e))}))))

(defn clojure-editor [{:as opts :keys [editable?] :or {editable? false}}]
  (r/with-let [!text (r/atom "")]
    [:div
     [:div.p-2.bg-slate-100
      [editor (assoc opts :lang :clojure :editable? editable? :doc-update (partial reset! !text))]]
     [:div.viewer-result.mt-3.ml-5
      (try
        (when-some [{:keys [error result]} (eval-string @!text)]
          (cond
            error [:div.red error]
            (react/isValidElement result) result
            'else (render/inspect result))))]]))

(def markdown-renderers
  (assoc md.transform/default-hiccup-renderers
         :code (fn [_ctx node] [clojure-editor {:doc (md.transform/->text node)}])
         :todo-item (fn [ctx {:as node :keys [attrs]}]
                      (md.transform/into-markup [:li [:input {:type "checkbox" :default-checked (:checked attrs)}]] ctx node))
         :formula (fn [_ctx node]
                    [:span {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node))}}])
         :block-formula (fn [_ctx node]
                          [:div {:dangerouslySetInnerHTML {:__html (.renderToString katex (md.transform/->text node) #js {:displayMode true})}}])))

#_
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

(defn expand-all-by-default [store]
  (reify
    ILookup
    (-lookup [_ k] (get store k true))
    IAssociative
    (-assoc [_ k v] (expand-all-by-default (assoc store k v)))
    IMap
    (-dissoc [_ k] (expand-all-by-default (dissoc store k)))))

(sci.ctx-store/swap-ctx! sci/merge-opts
                         {:namespaces {'md {'parse md/parse}
                                       'md.transform {'->hiccup md.transform/->hiccup}
                                       'md.demo {'editor markdown-editor
                                                 'renderers markdown-renderers
                                                 'inspect-expanded (fn [x]
                                                                     (r/with-let [expanded-at (r/atom (expand-all-by-default {:hover-path [] :prompt-multi-expand? false}))]
                                                                       (render/inspect-presented {:!expanded-at expanded-at}
                                                                                                 (v/present x))))}}})

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
