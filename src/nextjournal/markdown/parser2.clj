(ns nextjournal.markdown.parser2
  (:require [clojure.string :as str]
            [clojure.zip :as z]
            [nextjournal.markdown.parser :as parser]
            [nextjournal.markdown.parser2.types]
            [nextjournal.markdown.parser2.footnotes :as footnotes])
  (:import (org.commonmark.parser Parser Parser$ParserExtension Parser$Builder)
           (org.commonmark.parser.delimiter DelimiterProcessor)
           (org.commonmark.ext.task.list.items TaskListItemsExtension TaskListItemMarker)
           (org.commonmark.node Node Nodes AbstractVisitor
            ;;;;;;;;;; node types ;;;;;;;;;;;;;;;;;;
                                Document
                                BlockQuote
                                BulletList
                                OrderedList
                                Code
                                FencedCodeBlock
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
           (nextjournal.markdown.parser2.types InlineFormula
                                               Footnote)))

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

(def InlineFormulaExtension
  (proxy [Object Parser$ParserExtension] []
    (extend [^Parser$Builder pb]
      (.customDelimiterProcessor
       pb
       (proxy [Object DelimiterProcessor] []
         (getOpeningCharacter [] \$)
         (getClosingCharacter [] \$)
         (getMinLength [] 1)
         (process [open close]
           (if (and (= 1 (.length open))
                    (= 1 (.length close)))
             (let [text (str/join
                         (keep #(when (instance? Text %) (.getLiteral %))
                               (Nodes/between (.. open getOpener) (.. close getCloser))))]
               (doseq [^Node n (Nodes/between (.. open getOpener)
                                              (.. close getCloser))]
                 (.unlink n))
               (.. open getOpener
                   ;; needs a named class `gen-class`
                   (insertAfter (new InlineFormula text)))
               1)
             0)))))))

(comment
  (parse "* this is inline $\\phi$ math
* other  "))

(def ^Parser parser
  (.. Parser
      builder
      (extensions [(TaskListItemsExtension/create)
                   InlineFormulaExtension
                   (footnotes/extension)])
      build))

;; helpers / ctx
(def ^:dynamic *in-tight-list?* false)
(defn paragraph-type [] (if *in-tight-list?* :plain :paragrpah))
(defn in-tight-list? [node] (if (instance? ListBlock node) (.isTight ^ListBlock node) *in-tight-list?*))
(defmacro with-tight-list [node & body]
  `(binding [*in-tight-list?* (in-tight-list? ~node)]
     ~@body))

;; multi stuff
(defmulti open-node (fn [_ctx node] (type node)))
(defmulti close-node (fn [_ctx node] (type node)))

(defmethod close-node :default [loc _node] (z/up loc))

(defmethod open-node Document [loc _node] loc)
(defmethod close-node Document [loc _node] loc)

(defmethod open-node Paragraph [loc _node]
  (-> loc (z/append-child {:type (paragraph-type) :content []}) z/down z/rightmost))

(defmethod open-node Heading [loc ^Heading node]
  (-> loc (z/append-child {:type :heading :content [] :level (.getLevel node)}) z/down z/rightmost))

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

(defmethod open-node Link [loc ^Link node]
  (-> loc (z/append-child {:type :link
                           :attrs {:href (.getDestination node) :title (.getTitle node)}
                           :content []}) z/down z/rightmost))

(defmethod open-node Image [loc ^Image node]
  (-> loc (z/append-child {:type :image
                           :attrs {:src (.getDestination node) :title (.getTitle node)}
                           :content []}) z/down z/rightmost))

(defmethod open-node Footnote [loc ^Footnote node]
  (-> loc (z/append-child {:type :footnote :label (.getLabel node)
                           :content []}) z/down z/rightmost))

(defn handle-todo-list [loc ^TaskListItemMarker node]
  (-> loc
      (z/edit assoc :type :todo-item :attrs {:checked (.isChecked node)})
      z/up (z/edit assoc :type :todo-list)
      z/down z/rightmost))

(defn node->data [^Node node]
  (let [!loc (atom (parser/->zip {:type :doc :content []}))]
    (.accept node
             (proxy [AbstractVisitor] []
               (visit [^Node node]
                 #_ (prn :visit (str node) (z/node @!loc))
                 (assert @!loc (format "Can't add node: '%s' to an empty location" node))
                 (condp instance? node
                   Text (swap! !loc z/append-child {:type :text :text (.getLiteral ^Text node)})
                   ThematicBreak (swap! !loc z/append-child {:type :ruler})
                   SoftLineBreak (swap! !loc z/append-child {:type :softbreak})
                   HardLineBreak (swap! !loc z/append-child {:type :softbreak})
                   TaskListItemMarker (swap! !loc handle-todo-list node)
                   InlineFormula (swap! !loc z/append-child {:type :inline-formula
                                                             :text (.getLiteral ^InlineFormula node)})

                   LinkReferenceDefinition (prn :link-ref node)


                   (if (get-method open-node (class node))
                     (with-tight-list node
                       (swap! !loc open-node node)
                       (proxy-super visitChildren node)
                       (swap! !loc close-node node))
                     (prn :open-node/not-implemented node))))))

    (some-> @!loc z/root)))

(defn parse [md]
  (node->data (.parse parser md)))

(comment
  (parse "# Ahoi

par
broken

* a tight **strong** list
* with [a nice link](/to/some 'with a title')
* * with nested

  * lose list

- [x] one inline formula $\\phi$ here
- [ ] two

---
![img](/some/src 'title')")


  ;; link refs

  (parse "some text with a [^link] ahoi

[^link]: https://application.garden 'whatatitle'


# text continues here
")



  ;; block footnotes
  (md/parse "_hello_ what and foo[^note1] and^[some other note].

And what.

[^note1]: the _what_

* and new text[^endnote] at the end.
* the
  * hell^[that warm place]

[^endnote]: conclusion.
")
  (require '[nextjournal.markdown :as md])

  ;; inline footnotes (might be handled via a delimited processor)
  (md/parse "some text with^[ and not without] a footnote")
  (parse "some text with^[ and not without] a footnote")
  (parse "some text with $and not
without$ a footnote"))
