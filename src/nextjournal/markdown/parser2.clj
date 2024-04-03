(ns nextjournal.markdown.parser2
  (:require [clojure.zip :as z]
            [nextjournal.markdown.parser :as parser])
  (:import (org.commonmark.parser Parser)
           (org.commonmark.node Node AbstractVisitor
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

                                ListItem
                                Link
                                SoftLineBreak
                                HardLineBreak
                                Image)))

(set! *warn-on-reflection* true)

(def ^Parser parser (.. Parser builder build))

(defmulti open-node (fn [_ctx node] (type node)))
(defmulti close-node (fn [_ctx node] (type node)))
(defmethod open-node :default [loc node] (prn :unknown-node node) loc)
(defmethod close-node :default [loc _node]
  (or (z/up loc) loc))

(defmethod open-node Document [loc _node] loc)
(defmethod close-node Document [loc _node] loc)

(defmethod open-node Paragraph [loc _node]
  (-> loc (z/append-child {:type :paragraph :content []}) z/down z/rightmost))

(defmethod open-node Heading [loc ^Heading node]
  (-> loc (z/append-child {:type :heading :content [] :level (.getLevel node)}) z/down z/rightmost))

(defmethod open-node BulletList [loc _node]
  (-> loc (z/append-child {:type :bullet-list :content []}) z/down z/rightmost))

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

(defn insert-text [loc ^Text node]
  (z/append-child loc {:type :text :text (.getLiteral node)}))

(defn node->data [^Node node]
  (let [!loc (atom (parser/->zip {:type :doc :content []}))]
    (.accept node
             (proxy [AbstractVisitor] []
               (visit [^Node node]
                 (prn :visit (str node) (z/node @!loc))
                 (assert @!loc (format "Can't add node: '%s' to an empty location" node))
                 (condp instance? node
                   Text (swap! !loc insert-text node)
                   (do
                     (swap! !loc open-node node)
                     (.visitChildren this node)
                     (swap! !loc close-node node))))))
    (some-> @!loc z/root)))

(defn parse [md]
  (node->data (.parse parser md)))

(comment
  (parse "# Ahoi
par
* a **strong** item
* what [a nice link](/to/some 'with a title')
* just _emphatic_ two

![img](/some/src 'title')"))
