(ns nextjournal.markdown.parser2
  (:require [clojure.zip :as z]
            [nextjournal.markdown.parser :as parser])
  (:import (org.commonmark.parser Parser)
           (org.commonmark.ext.task.list.items TaskListItemsExtension
                                               TaskListItemMarker)
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
                                ThematicBreak
                                SoftLineBreak
                                HardLineBreak
                                Image)))

(set! *warn-on-reflection* true)

(def ^Parser parser (.. Parser
                        builder
                        (extensions [(TaskListItemsExtension/create)])
                        build))

(defmulti open-node (fn [_ctx node] (type node)))
(defmulti close-node (fn [_ctx node] (type node)))

(defmethod close-node :default [loc _node] (z/up loc))

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

(defn handle-todo-list [loc ^TaskListItemMarker node]
  (prn :todo node)
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
                   (if (get-method open-node (class node))
                     (do
                       (swap! !loc open-node node)
                       (.visitChildren this node)
                       (swap! !loc close-node node))
                     (prn :open-node/not-implemented node))))))

    (some-> @!loc z/root)))

(defn parse [md]
  (node->data (.parse parser md)))

(comment
  (do
    (parse "# Ahoi

par
broken

* a **strong** item
* what [a nice link](/to/some 'with a title')
* just _emphatic_ two

- [ ] one
- [x] two
---
![img](/some/src 'title')")
    nil))
