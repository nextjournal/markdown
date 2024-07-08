(ns nextjournal.markdown.commonmark
  (:require [clojure.zip :as z]
            [nextjournal.markdown.parser :as parser]
            [nextjournal.markdown.parser2.types]
            [nextjournal.markdown.parser2.formulas :as formulas])
  (:import (org.commonmark.parser Parser)
           (org.commonmark.ext.task.list.items TaskListItemsExtension TaskListItemMarker)
           (org.commonmark.ext.footnotes FootnotesExtension FootnoteReference FootnoteDefinition)
           (org.commonmark.node Node AbstractVisitor
            ;;;;;;;;;; node types ;;;;;;;;;;;;;;;;;;
                                Document
                                BlockQuote
                                BulletList
                                OrderedList
                                Code
                                FencedCodeBlock
                                IndentedCodeBlock
                                Heading
                                Text
                                Paragraph
                                Emphasis
                                StrongEmphasis

                                ListBlock
                                ListItem
                                Link
                                LinkReferenceDefinition
                                ThematicBreak
                                SoftLineBreak
                                HardLineBreak
                                Image)
   ;; custom types
           (nextjournal.markdown.parser2.types InlineFormula BlockFormula)))

(set! *warn-on-reflection* true)
;; TODO:
;; - [x] inline formulas
;; - [ ] block formulas
;; - [x] tight lists
;; - [x] task lists
;; - [ ] footnotes
;; - [ ] strikethroughs ext
;; - [ ] tables
;; - [ ] fenced code info
;; - [ ] html nodes
;; - [ ] auto link
;; - [ ] promote single images as blocks
;; - [ ] [[TOC]] (although not used in Clerk)


(comment
  (parse "* this is inline $\\phi$ math
* other  "))

(def ^Parser parser
  (.. Parser
      builder
      (extensions [(formulas/extension)
                   (FootnotesExtension/create)
                   (TaskListItemsExtension/create)])
      build))

;; helpers / ctx
(def ^:dynamic *in-tight-list?* false)
(def ^:dynamic *current-root* :doc)                         ;; :doc | :footnotes
(defn paragraph-type [] (if *in-tight-list?* :plain :paragraph))
(defn current-root [] *current-root*)
(defn in-tight-list? [node] (if (instance? ListBlock node) (.isTight ^ListBlock node) *in-tight-list?*))
(defn current-root-for [node] (if (instance? FootnoteDefinition node) :footnotes *current-root*))
(defmacro with-tight-list [node & body]
  `(binding [*in-tight-list?* (in-tight-list? ~node)]
     ~@body))
(defmacro with-current-root [node & body]
  `(binding [*current-root* (current-root-for ~node)]
     ~@body))

;; multi stuff
(defmulti open-node (fn [_ctx node] (type node)))
(defmulti close-node (fn [_ctx node] (type node)))

(defmethod close-node :default [loc _node] (z/up loc))

(defmethod open-node Document [loc _node] loc)
(defmethod close-node Document [loc _node] loc)

(defmethod open-node Paragraph [loc _node]
  (-> loc (z/append-child {:type (paragraph-type) :content []}) z/down z/rightmost))

(defmethod open-node BlockQuote [loc _node]
  (-> loc (z/append-child {:type :blockquote :content []}) z/down z/rightmost))

(defmethod open-node Heading [loc ^Heading node]
  (-> loc (z/append-child {:type :heading :content [] :heading-level (.getLevel node)}) z/down z/rightmost))

(defmethod open-node BulletList [loc ^ListBlock node]
  (-> loc (z/append-child {:type :bullet-list :content [] :tight? (.isTight node)}) z/down z/rightmost))

(defmethod open-node OrderedList [loc _node]
  (-> loc (z/append-child {:type :numbered-list :content []}) z/down z/rightmost))

(defmethod open-node ListItem [loc _node]
  (-> loc (z/append-child {:type :list-item :content []}) z/down z/rightmost))

(defmethod open-node Emphasis [loc _node]
  (-> loc (z/append-child {:type :em :content []}) z/down z/rightmost))

(defmethod open-node StrongEmphasis [loc _node]
  (-> loc (z/append-child {:type :strong :content []}) z/down z/rightmost))

(defmethod open-node Code [loc ^Code node]
  (-> loc (z/append-child {:type :monospace
                           :content [{:type :text
                                      :text (.getLiteral node)}]}) z/down z/rightmost))

(defmethod open-node Link [loc ^Link node]
  (-> loc (z/append-child {:type :link
                           :attrs {:href (.getDestination node) :title (.getTitle node)}
                           :content []}) z/down z/rightmost))

(defmethod open-node IndentedCodeBlock [loc ^IndentedCodeBlock node]
  (-> loc (z/append-child {:type :code
                           :content [{:text (.getLiteral node)}]}) z/down z/rightmost))

(defmethod open-node FencedCodeBlock [loc ^FencedCodeBlock node]
  (-> loc (z/append-child (merge {:type :code
                                  :info (.getInfo node)
                                  :content [{:text (.getLiteral node)}]}
                                 (parser/parse-fence-info (.getInfo node)))) z/down z/rightmost))

(defmethod open-node Image [loc ^Image node]
  (-> loc (z/append-child {:type :image
                           :attrs {:src (.getDestination node) :title (.getTitle node)}
                           :content []}) z/down z/rightmost))

(defmethod open-node FootnoteDefinition [loc ^FootnoteDefinition node]
  (-> loc (z/append-child {:type :footnote
                           :label (.getLabel node)
                           :ref (inc (-> loc z/root :content count))
                           ;; TODO: there might be definitions which do not correspond to any reference
                           :content []}) z/down z/rightmost))

(defn handle-todo-list [loc ^TaskListItemMarker node]
  (-> loc
      (z/edit assoc :type :todo-item :attrs {:checked (.isChecked node)})
      z/up (z/edit assoc :type :todo-list)
      z/down z/rightmost))

(defn update-current [ctx f & args]
  (apply update ctx (current-root) f args))

(defn node->data [^Node node]
  (let [!ctx (atom {:doc (parser/->zip {:type :doc :content []})
                    :footnotes (parser/->zip {:type :footnotes :content []})
                    :label->footnote-ref {}})]
    (.accept node
             (proxy [AbstractVisitor] []
               ;; proxy can't overload method by arg type, while gen-class can: https://groups.google.com/g/clojure/c/TVRsy4Gnf70
               (visit [^Node node]
                 (condp instance? node
                   LinkReferenceDefinition :ignore
                   Text (swap! !ctx update-current z/append-child {:type :text :text (.getLiteral ^Text node)})
                   ThematicBreak (swap! !ctx update-current z/append-child {:type :ruler})
                   SoftLineBreak (swap! !ctx update-current z/append-child {:type :softbreak})
                   HardLineBreak (swap! !ctx update-current z/append-child {:type :hardbreak})
                   TaskListItemMarker (swap! !ctx update-current handle-todo-list node)
                   InlineFormula (swap! !ctx update-current z/append-child {:type :formula :text (.getLiteral ^InlineFormula node)})
                   BlockFormula (swap! !ctx update-current z/append-child {:type :block-formula :text (.getLiteral ^BlockFormula node)})
                   FootnoteReference (swap! !ctx (fn [{:as ctx :keys [label->footnote-ref]}]
                                                   (let [label (.getLabel ^FootnoteReference node)
                                                         footnote-ref (or (get label->footnote-ref label)
                                                                          {:type :footnote-ref
                                                                           :ref (count label->footnote-ref)
                                                                           :label label})]
                                                     (-> ctx
                                                         (update-current z/append-child footnote-ref)
                                                         (update :label->footnote-ref assoc label footnote-ref)))))

                   ;; else
                   (if (get-method open-node (class node))
                     (with-tight-list node
                       (with-current-root node
                         (swap! !ctx update-current open-node node)
                         (proxy-super visitChildren node)
                         (swap! !ctx update-current close-node node)))
                     (prn :open-node/not-implemented node))))))

    (let [{:as ctx :keys [label->footnote-ref]} (deref !ctx)]
      (-> ctx
          :doc z/root
          (assoc :footnotes
                 ;; there will never be references without definitions, but the contrary may happen
                 (vec
                  (sort-by :ref
                           (keep (fn [{:as footnote :keys [label]}]
                                   (when (contains? label->footnote-ref label)
                                     (assoc footnote :ref (:ref (label->footnote-ref label)))))
                                 (-> @!ctx :footnotes z/root :content)))))))))

(defn parse
  ([md] (parse {} md))
  ([_doc md] (node->data (.parse parser md))))

(comment
  (parse "some `marks` inline and inline $formula$ with a [link _with_ em](https://what.tfk)")
  (parse "# Ahoi

> par
> broken

* a tight **strong** list
* with [a nice link](/to/some 'with a title')
* * with nested

  * lose list

- [x] one inline formula $\\phi$ here
- [ ] two

---
![img](/some/src 'title')")

  ;; footnotes
  (def text-with-footnotes "_hello_ what and foo[^note1] and

And what.

[^note1]: the _what_

* and new text[^note2] at the end.
* the hell

[^note2]: conclusion and $\\phi$

[^note3]: this should just be ignored
")

  (parse text-with-footnotes)

  (require '[nextjournal.markdown :as md])
  (md/parse text-with-footnotes)

  (parse (slurp "../clerk-px23/README.md"))

  (parse "Knuth's _Literate Programming_[^literateprogramming][^knuth84] emphasized the importance of focusing on human beings as consumers of computer programs. His original implementation involved authoring files that combine source code and documentation, which were then divided into two derived artifacts: source code for the computer and a typeset document in natural language to explain the program.

[^knuth84]: [Literate Programming](https://doi.org/10.1093/comjnl/27.2.97)
[^literateprogramming]: An extensive archive of related material is maintained [here](http://www.literateprogramming.com).")

  (-> (parse "this might[^reuse] here[^another] and here[^reuse] here

[^another]: stuff
[^reuse]: define here

another paragraph reusing[^reuse]
")
      md.parser/insert-sidenote-containers))
