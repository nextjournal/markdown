;; # 🧩 Parsing
(ns nextjournal.markdown.impl
  (:require [clojure.zip :as z]
            [nextjournal.markdown.impl.types]
            [nextjournal.markdown.impl.extensions :as extensions]
            [nextjournal.markdown.utils :as u])
  (:import (org.commonmark.ext.autolink AutolinkExtension)
           (org.commonmark.ext.footnotes FootnotesExtension FootnoteReference FootnoteDefinition InlineFootnote)
           (org.commonmark.ext.gfm.strikethrough Strikethrough StrikethroughExtension)
           (org.commonmark.ext.gfm.tables TableBlock TableBody TableRow TableHead TableCell TablesExtension)
           (org.commonmark.ext.task.list.items TaskListItemsExtension TaskListItemMarker)
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
           (org.commonmark.parser Parser)
           (nextjournal.markdown.impl.types BlockFormula InlineFormula ToC)))

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
      (extensions [(extensions/create)
                   (AutolinkExtension/create)
                   (TaskListItemsExtension/create)
                   (TablesExtension/create)
                   (StrikethroughExtension/create)
                   (.. (FootnotesExtension/builder)
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

(defmethod close-node :default [ctx _node] (u/update-current-loc ctx z/up))

(defmethod open-node Document [ctx _node] ctx)
(defmethod close-node Document [ctx _node] ctx)

(defmethod open-node Paragraph [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type (paragraph-type)}))))

(defmethod open-node BlockQuote [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :blockquote}))))

(defmethod open-node Heading [ctx ^Heading node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :heading
                                                         :heading-level (.getLevel node)}))))

(defmethod close-node Heading [ctx ^Heading _node]
  (u/handle-close-heading ctx))

(defmethod open-node BulletList [ctx ^ListBlock node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :bullet-list :content [] #_#_:tight? (.isTight node)}))))

(defmethod open-node OrderedList [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :numbered-list :content []}))))

(defmethod open-node ListItem [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :list-item :content []}))))

(defmethod open-node Emphasis [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :em :content []}))))

(defmethod open-node StrongEmphasis [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :strong :content []}))))

(defmethod open-node Code [ctx ^Code node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :monospace
                                                     :content [{:type :text
                                                                :text (.getLiteral node)}]}))))

(defmethod open-node Strikethrough [ctx _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :strikethrough :content []}))))

(defmethod open-node Link [ctx ^Link node]
  (u/update-current-loc ctx (fn [loc]
                          (u/zopen-node loc {:type :link
                                             :attrs (cond-> {:href (.getDestination node)}
                                                      (.getTitle node)
                                                      (assoc :title (.getTitle node)))}))))

(defmethod open-node IndentedCodeBlock [ctx ^IndentedCodeBlock node]
  (u/update-current-loc ctx (fn [loc]
                          (u/zopen-node loc {:type :code
                                             :content [{:type :text
                                                        :text (.getLiteral node)}]}))))

(defmethod open-node FencedCodeBlock [ctx ^FencedCodeBlock node]
  (u/update-current-loc ctx (fn [loc]
                          (u/zopen-node loc (merge {:type :code
                                                    :info (.getInfo node)
                                                    :content [{:type :text
                                                               :text (.getLiteral node)}]}
                                                   (u/parse-fence-info (.getInfo node)))))))

(defmethod open-node Image [ctx ^Image node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :image
                                                     :attrs {:src (.getDestination node) :title (.getTitle node)}}))))

(defmethod open-node TableBlock [ctx ^TableBlock _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :table}))))
(defmethod open-node TableHead [ctx ^TableHead _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :table-head}))))
(defmethod open-node TableBody [ctx ^TableBody _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :table-body}))))
(defmethod open-node TableRow [ctx ^TableRow _node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type :table-row}))))
(defmethod open-node TableCell [ctx ^TableCell node]
  (u/update-current-loc ctx (fn [loc] (u/zopen-node loc {:type (if (.isHeader node) :table-header :table-data)
                                                     :content []}))))

(defmethod open-node FootnoteDefinition [ctx ^FootnoteDefinition node]
  (-> ctx
      (assoc ::root :footnotes)
      (u/update-current-loc (fn [loc]
                              (-> loc
                                  (z/append-child {:type :footnote
                                                   :label (.getLabel node)
                                                   :content []}) z/down z/rightmost)))))

(defmethod close-node FootnoteDefinition [ctx ^FootnoteDefinition _node]
  (-> ctx (u/update-current-loc z/up) (assoc ::root :doc)))

(defmethod open-node InlineFootnote [{:as ctx ::keys [label->footnote-ref]} ^InlineFootnote _node]
  (let [label (str "inline-note-" (count label->footnote-ref))
        footnote-ref {:type :footnote-ref
                      :inline? true
                      :ref (count label->footnote-ref)
                      :label label}]
    (-> ctx
        (u/update-current-loc z/append-child footnote-ref)
        (update ::label->footnote-ref assoc label footnote-ref)
        (assoc ::root :footnotes)
        (u/update-current-loc (fn [loc]
                                (-> loc
                                    (u/zopen-node {:type :footnote :inline? true :label label :content []})
                                    (u/zopen-node {:type :paragraph :content []})))))))

(defmethod close-node InlineFootnote [ctx ^FootnoteDefinition _node]
  (-> ctx (u/update-current-loc (comp z/up z/up)) (assoc ::root :doc)))

(defn handle-todo-list [loc ^TaskListItemMarker node]
  (-> loc
      (z/edit assoc :type :todo-item :attrs {:checked (.isChecked node)})
      z/up (z/edit assoc :type :todo-list)
      z/down z/rightmost))

(defn node->data [{:as ctx-in :keys [footnotes]} ^Node node]
  (assert (:type ctx-in) ":type must be set on initial doc")
  (assert (:content ctx-in) ":content must be set on initial doc")
  (assert (::root ctx-in) "context needs a ::root")
  ;; TODO: unify pre/post parse across impls
  (let [!ctx (atom (assoc ctx-in
                          :doc (u/->zip ctx-in)
                          :footnotes (u/->zip {:type :footnotes :content (or footnotes [])})))]
    (.accept node
             (proxy [AbstractVisitor] []
               ;; proxy can't overload method by arg type, while gen-class can: https://groups.google.com/g/clojure/c/TVRsy4Gnf70
               (visit [^Node node]
                 (condp instance? node
                   ;; leaf nodes
                   LinkReferenceDefinition :ignore
                   ;;Text (swap! !ctx u/update-current z/append-child {:type :text :text (.getLiteral ^Text node)})
                   Text (swap! !ctx u/handle-text-token (.getLiteral ^Text node))
                   ThematicBreak (swap! !ctx u/update-current-loc z/append-child {:type :ruler})
                   SoftLineBreak (swap! !ctx u/update-current-loc z/append-child {:type :softbreak})
                   HardLineBreak (swap! !ctx u/update-current-loc z/append-child {:type :hardbreak})
                   ToC (swap! !ctx u/update-current-loc z/append-child {:type :toc})
                   TaskListItemMarker (swap! !ctx u/update-current-loc handle-todo-list node)
                   InlineFormula (swap! !ctx u/update-current-loc z/append-child {:type :formula :text (.getLiteral ^InlineFormula node)})
                   BlockFormula (swap! !ctx u/update-current-loc z/append-child {:type :block-formula :text (.getLiteral ^BlockFormula node)})
                   FootnoteReference (swap! !ctx (fn [{:as ctx ::keys [label->footnote-ref]}]
                                                   (let [label (.getLabel ^FootnoteReference node)
                                                         footnote-ref (or (get label->footnote-ref label)
                                                                          {:type :footnote-ref
                                                                           :ref (count label->footnote-ref)
                                                                           :label label})]
                                                     (-> ctx
                                                         (u/update-current-loc z/append-child footnote-ref)
                                                         (update ::label->footnote-ref assoc label footnote-ref)))))

                   ;; else branch nodes
                   (if (get-method open-node (class node))
                     (with-tight-list node
                       (swap! !ctx open-node node)
                       (proxy-super visitChildren node)
                       (swap! !ctx close-node node))
                     (prn ::not-implemented node))))))

    (let [{:as ctx-out :keys [doc title toc footnotes] ::keys [label->footnote-ref]} (deref !ctx)]
      (-> ctx-out
          (dissoc :doc)
          (cond->
            (and title (not (:title ctx-in)))
            (assoc :title title))
          (assoc :toc toc
                 :content (:content (z/root doc))
                 ::label->footnote-ref label->footnote-ref
                 :footnotes
                 ;; there will never be references without definitions, but the contrary may happen
                 (->> footnotes z/root :content
                      (keep (fn [{:as footnote :keys [label]}]
                              (when (contains? label->footnote-ref label)
                                (assoc footnote :ref (:ref (label->footnote-ref label))))))
                      (sort-by :ref)
                      (vec)))))))

(defn parse
  ([md] (parse u/empty-doc md))
  ([ctx md] (node->data (update ctx :text-tokenizers (partial map u/normalize-tokenizer))
                        (.parse parser md))))

(comment
  (import '[org.commonmark.renderer.html HtmlRenderer])
  (remove-all-methods open-node)
  (remove-all-methods close-node)

  (.render (.build (HtmlRenderer/builder))
           (.parse parser "some text^[and a note]"))

  (parse "some text^[and a note]")

  (-> {}
      (parse "# Title")
      (parse "some para^[with note]")
      (parse "some para^[with other note]"))

  (parse "some `marks` inline and inline $formula$ with a [link _with_ em](https://what.tfk)")
  (parse (assoc u/empty-doc :text-tokenizers [u/internal-link-tokenizer])
         "what a [[link]] is this")
  (parse "what the <em>real</em> deal is")
  (parse "some

[[TOC]]

what")

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
