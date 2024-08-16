(ns nextjournal.markdown.parser.impl
  (:require [clojure.zip :as z]
            [nextjournal.markdown.parser.impl.types]
            [nextjournal.markdown.parser.impl.formulas :as formulas]
            [nextjournal.markdown.parser.impl.utils :as u])
  (:import (org.commonmark.parser Parser)
           (org.commonmark.ext.task.list.items TaskListItemsExtension TaskListItemMarker)
           (org.commonmark.ext.gfm.tables TableBlock TableBody TableRow TableHead TableCell TablesExtension)
   #_(org.commonmark.ext.footnotes FootnotesExtension FootnoteReference FootnoteDefinition InlineFootnote)
           (org.commonmark.node Node AbstractVisitor
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
           (nextjournal.markdown.parser.impl.types InlineFormula
                                                   BlockFormula)))

(set! *warn-on-reflection* true)
;; TODO:
;; - [x] inline formulas
;; - [x] block formulas
;; - [x] tight lists
;; - [x] task lists
;; - [x] footnotes
;; - [ ] strikethroughs ext
;; - [x] tables
;; - [x] fenced code info
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
                   (TaskListItemsExtension/create)
                   (TablesExtension/create)
                   #_(.. (FootnotesExtension/builder)
                         (inlineFootnotes true)
                         (build))])
      build))

;; helpers / ctx
(def ^:dynamic *in-tight-list?* false)

(defn paragraph-type [] (if *in-tight-list?* :plain :paragraph))

(defn in-tight-list? [node]
  (cond
    (instance? ListBlock node) (.isTight ^ListBlock node)
    (instance? BlockQuote node) false
    :else *in-tight-list?*))

(defmacro with-tight-list [node & body]
  `(binding [*in-tight-list?* (in-tight-list? ~node)]
     ~@body))

;; multi stuff
(defmulti open-node (fn [_ctx node] (type node)))
(defmulti close-node (fn [_ctx node] (type node)))

(defn current-loc [{:as ctx :keys [root]}] (get ctx root))
(defn update-current [{:as ctx :keys [root]} f & args]
  (assert root (str "Missing root: '" (keys ctx) "'"))
  (apply update ctx root f args))

(defmethod close-node :default [ctx _node] (update-current ctx z/up))

(defmethod open-node Document [ctx _node] ctx)
(defmethod close-node Document [ctx _node] ctx)

(defmethod open-node Paragraph [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type (paragraph-type) :content []}) z/down z/rightmost))))

(defmethod open-node BlockQuote [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :blockquote :content []}) z/down z/rightmost))))

(defmethod open-node Heading [ctx ^Heading node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :heading :content [] :heading-level (.getLevel node)}) z/down z/rightmost))))

(defmethod close-node Heading [ctx ^Heading _node]
  (let [{:keys [text->id+emoji-fn]} (-> ctx current-loc z/root)
        {:keys [id emoji]} (when (ifn? text->id+emoji-fn)
                             (text->id+emoji-fn (-> ctx current-loc z/node)))]
    (def ctx ctx)
    (update-current ctx
                    (fn [loc]
                      (-> loc
                          (z/edit (fn [node]
                                    (cond-> node
                                      id (assoc-in [:attrs :id] id)
                                      emoji (assoc :emoji emoji))))
                          (as-> l
                            ;; only add top level headings to ToC
                            (if (= 1 (u/zdepth l))
                              (let [heading-node (z/node l)]
                                (-> l z/up
                                    (z/edit (fn [doc]
                                              (-> doc
                                                  (u/add-to-toc (assoc heading-node :path (u/zpath l)))
                                                  (u/set-title-when-missing heading-node)))) z/down z/rightmost))
                              l))
                          z/up)))))

(defmethod open-node BulletList [ctx ^ListBlock node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :bullet-list :content [] #_#_:tight? (.isTight node)}) z/down z/rightmost))))

(defmethod open-node OrderedList [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :numbered-list :content []}) z/down z/rightmost))))

(defmethod open-node ListItem [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :list-item :content []}) z/down z/rightmost))))

(defmethod open-node Emphasis [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :em :content []}) z/down z/rightmost))))

(defmethod open-node StrongEmphasis [ctx _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :strong :content []}) z/down z/rightmost))))

(defmethod open-node Code [ctx ^Code node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :monospace
                                                         :content [{:type :text
                                                                    :text (.getLiteral node)}]}) z/down z/rightmost))))

(defmethod open-node Link [ctx ^Link node]
  (update-current ctx (fn [loc]
                        (-> loc (z/append-child {:type :link
                                                 :content []
                                                 :attrs (cond-> {:href (.getDestination node)}
                                                          (.getTitle node)
                                                          (assoc :title (.getTitle node)))}) z/down z/rightmost))))

(defmethod open-node IndentedCodeBlock [ctx ^IndentedCodeBlock node]
  (update-current ctx (fn [loc]
                        (-> loc (z/append-child {:type :code
                                                 :content [{:type :text
                                                            :text (.getLiteral node)}]}) z/down z/rightmost))))

(defmethod open-node FencedCodeBlock [ctx ^FencedCodeBlock node]
  (update-current ctx (fn [loc]
                        (-> loc (z/append-child (merge {:type :code
                                                        :info (.getInfo node)
                                                        :content [{:type :text
                                                                   :text (.getLiteral node)}]}
                                                       (u/parse-fence-info (.getInfo node)))) z/down z/rightmost))))

(defmethod open-node Image [ctx ^Image node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :image
                                                         :attrs {:src (.getDestination node) :title (.getTitle node)}
                                                         :content []}) z/down z/rightmost))))

(defmethod open-node TableBlock [ctx ^TableBlock _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :table
                                                         :content []}) z/down z/rightmost))))
(defmethod open-node TableHead [ctx ^TableHead _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :table-head
                                                         :content []}) z/down z/rightmost))))
(defmethod open-node TableBody [ctx ^TableBody _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :table-body
                                                         :content []}) z/down z/rightmost))))
(defmethod open-node TableRow [ctx ^TableRow _node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type :table-row
                                                         :content []}) z/down z/rightmost))))

(defmethod open-node TableCell [ctx ^TableCell node]
  (update-current ctx (fn [loc] (-> loc (z/append-child {:type (if (.isHeader node) :table-header :table-data)
                                                         :content []}) z/down z/rightmost))))

#_(defmethod open-node FootnoteDefinition [ctx ^FootnoteDefinition node]
    (-> ctx
        (assoc :root :footnotes)
        (update-current (fn [loc]
                          (-> loc
                              (z/append-child {:type :footnote
                                               :label (.getLabel node)
                                               :content []}) z/down z/rightmost)))))
#_(defmethod close-node FootnoteDefinition [ctx ^FootnoteDefinition _node]
    (-> ctx (update-current z/up) (assoc :root :doc)))
#_(defmethod open-node InlineFootnote [{:as ctx :keys [label->footnote-ref]} ^InlineFootnote _node]
    (let [label (str "note-" (count label->footnote-ref))
          footnote-ref {:type :footnote-ref
                        :inline? true
                        :ref (count label->footnote-ref)
                        :label label}]
      (-> ctx
          (update-current z/append-child footnote-ref)
          (update :label->footnote-ref assoc label footnote-ref)
          (assoc :root :footnotes)
          (update-current (fn [loc]
                            (-> loc
                                (z/append-child {:type :footnote
                                                 :inline? true
                                                 :label label
                                                 :content []}) z/down z/rightmost))))))
#_(defmethod close-node InlineFootnote [ctx ^FootnoteDefinition _node]
    (-> ctx (update-current z/up) (assoc :root :doc)))

(defn handle-todo-list [loc ^TaskListItemMarker node]
  (-> loc
      (z/edit assoc :type :todo-item :attrs {:checked (.isChecked node)})
      z/up (z/edit assoc :type :todo-list)
      z/down z/rightmost))

(defn node->data [{:as ctx :keys [footnotes]} ^Node node]
  (assert (:type ctx) ":type must be set on initial doc")
  (assert (:content ctx) ":content must be set on initial doc")
  (let [!ctx (atom (-> ctx
                       (update :label->footnote-ref #(or % {}))
                       (assoc :doc (u/->zip ctx)
                              :footnotes (u/->zip {:type :footnotes :content (or footnotes [])})
                              :root :doc)))]
    (.accept node
             (proxy [AbstractVisitor] []
               ;; proxy can't overload method by arg type, while gen-class can: https://groups.google.com/g/clojure/c/TVRsy4Gnf70
               (visit [^Node node]
                 (condp instance? node
                   ;; leaf nodes
                   LinkReferenceDefinition :ignore
                   ;;Text (swap! !ctx update-current z/append-child {:type :text :text (.getLiteral ^Text node)})
                   Text (swap! !ctx update-current u/handle-text-token (.getLiteral ^Text node))
                   ThematicBreak (swap! !ctx update-current z/append-child {:type :ruler})
                   SoftLineBreak (swap! !ctx update-current z/append-child {:type :softbreak})
                   HardLineBreak (swap! !ctx update-current z/append-child {:type :hardbreak})
                   TaskListItemMarker (swap! !ctx update-current handle-todo-list node)
                   InlineFormula (swap! !ctx update-current z/append-child {:type :formula :text (.getLiteral ^InlineFormula node)})
                   BlockFormula (swap! !ctx update-current z/append-child {:type :block-formula :text (.getLiteral ^BlockFormula node)})
                   #_#_FootnoteReference (swap! !ctx (fn [{:as ctx :keys [label->footnote-ref]}]
                                                       (let [label (.getLabel ^FootnoteReference node)
                                                             footnote-ref (or (get label->footnote-ref label)
                                                                              {:type :footnote-ref
                                                                               :ref (count label->footnote-ref)
                                                                               :label label})]
                                                         (-> ctx
                                                             (update-current z/append-child footnote-ref)
                                                             (update :label->footnote-ref assoc label footnote-ref)))))

                   ;; else branch nodes
                   (if (get-method open-node (class node))
                     (with-tight-list node
                       (swap! !ctx open-node node)
                       (proxy-super visitChildren node)
                       (swap! !ctx close-node node))
                     (prn :open-node/not-implemented node))))))

    (let [{:as ctx :keys [label->footnote-ref]} (deref !ctx)]
      (-> ctx
          :doc z/root
          (assoc :label->footnote-ref label->footnote-ref
                 :footnotes
                 ;; there will never be references without definitions, but the contrary may happen
                 (->> @!ctx :footnotes z/root :content
                      (keep (fn [{:as footnote :keys [label]}]
                              (when (contains? label->footnote-ref label)
                                (assoc footnote :ref (:ref (label->footnote-ref label))))))
                      (sort-by :ref)
                      (vec)))))))

(defn parse
  ([md] (parse u/empty-doc md))
  ([ctx md] (node->data ctx (.parse parser md))))

(comment
  (remove-all-methods open-node)
  (remove-all-methods close-node)
  (-> {}
      (parse "# Title")
      (parse "some para^[with note]")
      (parse "some para^[with other note]"))

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
  (parse "_hello_ what and foo[^note1] and

And what.

[^note1]: the _what_

* and new text[^note2] at the end.
* the hell^[crazy _inline_ note with [a](https://a-link.xx) inside]

[^note2]: conclusion and $\\phi$

[^note3]: this should just be ignored
")

  (parse (slurp "../clerk-px23/README.md"))
  ;; => :ref 27

  (parse "Knuth's _Literate Programming_[^literateprogramming][^knuth84] emphasized the importance of focusing on human beings as consumers of computer programs. His original implementation involved authoring files that combine source code and documentation, which were then divided into two derived artifacts: source code for the computer and a typeset document in natural language to explain the program.

[^knuth84]: [Literate Programming](https://doi.org/10.1093/comjnl/27.2.97)
[^literateprogramming]: An extensive archive of related material is maintained [here](http://www.literateprogramming.com).")

  (-> (parse "this might[^reuse] here[^another] and here[^reuse] here

[^another]: stuff
[^reuse]: define here

this should be left as is

another paragraph reusing[^reuse]
")
      md.parser/insert-sidenote-containers))
