(ns nextjournal.markdown-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.standalone :as standalone]
            [matcher-combinators.matchers :as m]
            [nextjournal.markdown :as md]
            [nextjournal.markdown.parser :as md.parser]
            [nextjournal.markdown.transform :as md.transform]))

#?(:cljs
   ;; FIXME: in matcher-combinators (should probably use a simple `:fail` dispatch instead of `:matcher-combinators/mismatch`)
   ;; mismatch are currently ignored by shadow both browser and node tests
   (defmethod t/report [:cljs-test-display.core/default :matcher-combinators/mismatch] [m]
     ;; Shadow browser tests
     (when (exists? js/document)
       (cljs-test-display.core/add-fail-node! m))

     (t/inc-report-counter! :fail)
     (println "\nFAIL in" (t/testing-vars-str m))
     (when (seq (:testing-contexts (t/get-current-env)))
       (println (t/testing-contexts-str)))
     (when-let [message (:message m)]
       (println message))
     (println "mismatch:")
     (println (:markup m))))

(def markdown-text
  "# 🎱 Hello

some **strong** _assertion_ and a [link] and a $\\pi$ formula

```clojure
(+ 1 2 3)
```

$$\\int_a^bf(t)dt$$

* one

* two

[link]:/path/to/something
")

(deftest parse-test
  (testing "ingests markdown returns nested nodes"
    (is (match? {:type :doc
            :title "🎱 Hello"
            :content [{:content [{:text "🎱 Hello"
                                  :type :text}]
                       :heading-level 1
                       :attrs {:id "hello"}
                       :emoji "🎱"
                       :type :heading}
                      {:content [{:text "some "
                                  :type :text}
                                 {:content [{:text "strong"
                                             :type :text}]
                                  :type :strong}
                                 {:text " "
                                  :type :text}
                                 {:content [{:text "assertion"
                                             :type :text}]
                                  :type :em}
                                 {:text " and a "
                                  :type :text}
                                 {:attrs {:href "/path/to/something"}
                                  :content [{:text "link"
                                             :type :text}]
                                  :type :link}
                                 {:text " and a "
                                  :type :text}
                                 {:text "\\pi"
                                  :type :formula}
                                 {:text " formula"
                                  :type :text}]
                       :type :paragraph}
                      {:content [{:text "(+ 1 2 3)\n" :type :text}]
                       :info "clojure"
                       :language "clojure"
                       :type :code}
                      {:text "\\int_a^bf(t)dt"
                       :type :block-formula}
                      {:content [{:content [{:content [{:text "one"
                                                        :type :text}]
                                             :type :paragraph}]
                                  :type :list-item}
                                 {:content [{:content [{:text "two"
                                                        :type :text}]
                                             :type :paragraph}]
                                  :type :list-item}]
                       :type :bullet-list}]
            :toc {:children [{:content [{:text "🎱 Hello"
                                         :type :text}]
                              :heading-level 1
                              :path [:content 0]
                              :type :toc}]
                  :type :toc}}
           (md/parse markdown-text))))


  (testing "parses internal links / plays well with todo lists"
    (is (match? {:toc {:type :toc}
            :type :doc
            :content [{:type :paragraph
                       :content [{:text "a "
                                  :type :text}
                                 {:text "wikistyle"
                                  :type :internal-link}
                                 {:text " link"
                                  :type :text}]}]}
           (md/parse "a [[wikistyle]] link")))

    (is (match? {:type :doc
            :title "a wikistyle link in title"
            :content [{:heading-level 1
                       :type :heading
                       :content [{:text "a "
                                  :type :text}
                                 {:text "wikistyle"
                                  :type :internal-link}
                                 {:text " link in title"
                                  :type :text}]}]
            :toc {:type :toc
                  :children [{:type :toc
                              :content [{:text "a "
                                         :type :text}
                                        {:text "wikistyle"
                                         :type :internal-link}
                                        {:text " link in title"
                                         :type :text}]
                              :heading-level 1
                              :path [:content 0]}]}}
           (md/parse "# a [[wikistyle]] link in title")))

    (is (match? {:type :doc
            :toc {:type :toc}
            :content [{:type :todo-list
                       :attrs {:has-todos true}
                       :content [{:type :todo-item
                                  :attrs {:checked true :todo true}
                                  :content [{:content [{:text "done "
                                                        :type :text}
                                                       {:text "linkme"
                                                        :type :internal-link}
                                                       {:text " to"
                                                        :type :text}]
                                             :type :plain}]}
                                 {:type :todo-item
                                  :attrs {:checked false :todo true}
                                  :content [{:type :plain
                                             :content [{:text "pending"
                                                        :type :text}]}]}
                                 {:type :todo-item
                                  :attrs {:checked false :todo true}
                                  :content [{:type :plain
                                             :content [{:text "pending"
                                                        :type :text}]}]}]}]}
           (md/parse "- [x] done [[linkme]] to
- [ ] pending
- [ ] pending")))))

(deftest ->hiccup-test
  "ingests markdown returns hiccup"
  (is (= [:div
          [:h1 {:id "hello"} "🎱 Hello"]
          [:p
           "some "
           [:strong
            "strong"]
           " "
           [:em
            "assertion"]
           " and a "
           [:a
            {:href "/path/to/something"}
            "link"]
           " and a "
           [:span.formula
            "\\pi"]
           " formula"]
          [:pre.viewer-code.not-prose "(+ 1 2 3)\n"]
          [:figure.formula
           "\\int_a^bf(t)dt"]
          [:ul
           [:li
            [:p
             "one"]]
           [:li
            [:p
             "two"]]]]
         (md/->hiccup markdown-text))))

(deftest set-title-when-missing
  (testing "sets title in document structure to the first heading of whatever level"
    (is (= "and some title"
           (:title (md/parse "- One
- Two
## and some title
### this is not a title" ))))))

(deftest ->hiccup-toc-test
  (testing
   "Builds Toc"

    (let [md "# Title

## Section 1

[[TOC]]

## Section 2

### Section 2.1
"
          data (md/parse md)
          hiccup (md.transform/->hiccup data)]

      (is (match? {:type :doc
              :title "Title"
              :content [{:content [{:text "Title"
                                    :type :text}]
                         :heading-level 1
                         :type :heading}
                        {:content [{:text "Section 1"
                                    :type :text}]
                         :heading-level 2
                         :type :heading}
                        {:type :toc}
                        {:content [{:text "Section 2"
                                    :type :text}]
                         :heading-level 2
                         :type :heading}
                        {:content [{:text "Section 2.1"
                                    :type :text}]
                         :heading-level 3
                         :type :heading}]
              :toc {:children [{:children [{:content [{:text "Section 1"
                                                       :type :text}]
                                            :heading-level 2
                                            :path [:content 1]
                                            :type :toc}
                                           {:children [{:content [{:text "Section 2.1"
                                                                   :type :text}]
                                                        :heading-level 3
                                                        :path [:content 4]
                                                        :type :toc}]
                                            :content [{:text "Section 2"
                                                       :type :text}]
                                            :heading-level 2
                                            :path [:content 3]
                                            :type :toc}]
                                :content [{:text "Title"
                                           :type :text}]
                                :heading-level 1
                                :path [:content 0]
                                :type :toc}]
                    :type :toc}}
             data))

      (is (match? [:div
                   [:h1
                    {:id "title"}
                    "Title"]
                   [:h2
                    {:id "section-1"}
                    "Section 1"]
                   [:div.toc
                    [:div
                     [:ul
                      [:li.toc-item
                       [:div
                        [:a
                         {:href "#title"}
                         [:h1
                          "Title"]]
                        [:ul
                         [:li.toc-item
                          [:div
                           [:a
                            {:href "#section-1"}
                            [:h2
                             "Section 1"]]]]
                         [:li.toc-item
                          [:div
                           [:a
                            {:href "#section-2"}
                            [:h2
                             "Section 2"]]
                           [:ul
                            [:li.toc-item
                             [:div
                              [:a
                               {:href "#section-2.1"}
                               [:h3
                                "Section 2.1"]]]]]]]]]]]]]
                   [:h2
                    {:id "section-2"}
                    "Section 2"]
                   [:h3
                    {:id "section-2.1"}
                    "Section 2.1"]]
                  hiccup)))))

(deftest todo-lists
  (testing "todo lists"
    (is (= [:div
            [:h1 {:id "todos"} "Todos"]
            [:ul.contains-task-list
             [:li
              [:input
               {:checked true
                :type "checkbox"}]
              [:<>
               "checked"]]
             [:li
              [:input
               {:checked false
                :type "checkbox"}]
              [:<>
               "unchecked"]
              [:ul.contains-task-list
               [:li
                [:input
                 {:checked false
                  :type "checkbox"}]
                [:<>
                 "nested"]]]]]]
           (md/->hiccup "# Todos
- [x] checked
- [ ] unchecked
  - [ ] nested
")))))

(deftest tags-text
  (testing "parsing tags"
    (is (match? {:type :doc
            :title "Hello Tags"
            :content [{:content [{:text "Hello Tags"
                                  :type :text}]
                       :heading-level 1
                       :type :heading}
                      {:content [{:text "par with "
                                  :type :text}
                                 {:text "really_nice"
                                  :type :hashtag}
                                 {:text " "
                                  :type :text}
                                 {:text "useful-123"
                                  :type :hashtag}
                                 {:text " tags"
                                  :type :text}]
                       :type :paragraph}]
            :toc {:type :toc
                  :children [{:type :toc
                              :content [{:text "Hello Tags"
                                         :type :text}]
                              :heading-level 1
                              :path [:content 0]}]}}
           (md/parse "# Hello Tags
par with #really_nice #useful-123 tags
"))))

  (testing "rendering tags"
    (is (= [:div
            [:h1 {:id "hello-tags"} "Hello Tags"]
            [:p
             "par with "
             [:a.tag
              {:href "/tags/really_nice"}
              "#really_nice"]
             " "
             [:a.tag
              {:href "/tags/useful-123"}
              "#useful-123"]
             " tags"]]
           (md/->hiccup "# Hello Tags
par with #really_nice #useful-123 tags
")))))


(deftest tight-vs-loose-lists
  (testing "tight lists"

    (is (match? {:type :doc
                 :content [{:type :bullet-list,
                            :content
                            [{:type :list-item,
                              :content [{:type :plain :content [{:text "one"}]}]}
                             {:type :list-item,
                              :content [{:type :plain :content [{:text "two"}]}]}]}]}
                (md/parse "
* one
* two"))))


  (testing "loose lists (2-newline separated lists)"
    (is (match? {:type :doc
                 :content [{:type :bullet-list,
                            :content
                            [{:type :list-item,
                              :content [{:type :paragraph :content [{:text "one"}]}]}
                             {:type :list-item,
                              :content [{:type :paragraph :content [{:text "two"}]}]}]}]}
                (md/parse "
* one

* two"))))

  (testing "loose lists (more than one block in any item)"
    (is (match? {:type :doc
                 :content [{:type :bullet-list,
                            :content
                            [{:type :list-item,
                              :content [{:type :paragraph :content [{:text "one"}]}
                                        {:type :paragraph :content [{:text "inner paragraph"}]}

                                        ]}
                             {:type :list-item,
                              :content [{:type :paragraph :content [{:text "two"}]}]}]}]}
                (md/parse "
* one

  inner paragraph
* two")))))

(deftest unique-heading-ids
  (is (match? {:content (m/embeds [{:type :heading :attrs {:id "introduction"}}
                                   {:type :heading :attrs {:id "quantum-physics"} :emoji "👩‍🔬"}
                                   {:type :heading :attrs {:id "references-📕"}}
                                   {:type :heading :attrs {:id "quantum-physics-2"} :emoji "⚛"}])}

              (md/parse "
## Introduction
Lorem ipsum et cetera.
### 👩‍🔬 Quantum Physics
Dolor sit and so on.
## References 📕
It's important to cite your references!
### ⚛ Quantum Physics
Particularly for quantum physics!
"))))

(deftest per-node-text-transform

  (is (= "Should ignore marks and interpret softbreaks as spaces"
         (-> (md/parse "Should **ignore** marks
and _interpret_
softbreaks as
spaces")
             md.transform/->text))))

(deftest footnotes
  (testing "foonotes via references"
    (is (match? {:content [{:attrs {:id "footnotes"}
                            :content [{:text "Footnotes"
                                       :type :text}]
                            :heading-level 1
                            :type :heading}
                           {:content [{:text "Long "
                                       :type :text}
                                      {:content [{:text "long"
                                                  :type :text}]
                                       :type :em}
                                      {:text " long time"
                                       :type :text}
                                      {:label "when"
                                       :ref 0
                                       :type :footnote-ref}
                                      {:text " ago."
                                       :type :text}]
                            :type :paragraph}]
                 :footnotes [{:content [{:content [{:text "Around "
                                                    :type :text}
                                                   {:content [{:text "20000"
                                                               :type :text}]
                                                    :type :strong}
                                                   {:text " years Ago. See "
                                                    :type :text}
                                                   {:attrs {:href "https://en.wikipedia.org/wiki/Pleistocene"}
                                                    :content [{:text "Pleistocene"
                                                               :type :text}]
                                                    :type :link}
                                                   {:text "."
                                                    :type :text}]
                                         :type :paragraph}]
                              :ref 0
                              :label "when"
                              :type :footnote}]
                 :title "Footnotes"
                 :type :doc}
                (md/parse "# Footnotes
Long _long_ long time[^when] ago.

[^when]: Around **20000** years Ago. See [Pleistocene](https://en.wikipedia.org/wiki/Pleistocene).
"))))

  (testing "Doc resuming _after_ footnotes definitions"

    (is (match? {:content [{:content [{:text "text"
                                  :type :text}
                                 {:label "note1"
                                  :ref 0
                                  :type :footnote-ref}
                                 {:text " and b"
                                  :type :text}
                                 {:label "note2"
                                  :ref 1
                                  :type :footnote-ref}
                                 {:text " c."
                                  :type :text}]
                       :type :paragraph}
                      {:attrs {:id "t"}
                       :content [{:text "T"
                                  :type :text}]
                       :heading-level 1
                       :type :heading}
                      {:content [{:text "c"
                                  :type :text}
                                 {:label "note3"
                                  :ref 2
                                  :type :footnote-ref}
                                 {:text " d."
                                  :type :text}]
                       :type :paragraph}]
            :footnotes [{:content [{:content [{:text "good"
                                               :type :text}]
                                    :type :paragraph}]
                         :ref 0
                         :type :footnote}
                        {:content [{:content [{:text "bad"
                                               :type :text}]
                                    :type :paragraph}]
                         :ref 1
                         :type :footnote}
                        {:content [{:content [{:text "closing"
                                               :type :text}]
                                    :type :paragraph}]
                         :ref 2
                         :type :footnote}]
            :type :doc}
           (md/parse "text[^note1] and b[^note2] c.
[^note1]: good
[^note2]: bad

# T
c[^note3] d.

[^note3]: closing"))))

  (testing "inline footnotes"
    (is (match? {:content [{:content [{:text "what would"
                                       :type :text}
                                      {:ref 0
                                       :type :footnote-ref}
                                      {:text "?"
                                       :type :text}]
                            :type :paragraph}]
                 :footnotes [{:content [{:content [{:text "this "
                                                    :type :text}
                                                   {:content [{:text "really"
                                                               :type :text}]
                                                    :type :em}
                                                   {:text " look like"
                                                    :type :text}]
                                         :type :paragraph}]
                              :ref 0
                              :type :footnote}]
                 :type :doc}
           (md/parse "what would^[this _really_ look like]?"))))

  (testing "Turning footnotes into sidenotes"

    (let [parse+insert-sidenotes (-> "Text[^note1] and^[inline _note_ here].

Par.

Again[^note2]

[^note1]: Explain 1
[^note2]: Explain 2
"
                                     md/parse
                                     md.parser/insert-sidenotes)]
      (is (match?
           {:toc {:type :toc},
            :footnotes [{:type :footnote,
                         :content [{:type :paragraph, :content [{:type :text, :text "Explain 1"}]}],
                         :ref 0,
                         :label "note1"}
                        {:type :footnote,
                         :content [{:type :paragraph,
                                    :content [{:type :text, :text "inline "}
                                              {:type :em, :content [{:type :text, :text "note"}]}
                                              {:type :text, :text " here"}]}],
                         :ref 1}
                        {:type :footnote,
                         :content [{:type :paragraph, :content [{:type :text, :text "Explain 2"}]}],
                         :ref 2,
                         :label "note2"}],
            :sidenotes? true,
            :content [{:type :paragraph,
                       :content [{:type :text, :text "Text"}
                                 {:type :sidenote-ref, :ref 0, :label "note1"}
                                 {:type :sidenote, :content [{:type :text, :text "Explain 1"}], :attrs {:ref 0}}
                                 {:type :text, :text " and"}
                                 {:type :sidenote-ref, :ref 1}
                                 {:type :sidenote,
                                  :content [{:type :text, :text "inline "}
                                            {:type :em, :content [{:type :text, :text "note"}]}
                                            {:type :text, :text " here"}],
                                  :attrs {:ref 1}}
                                 {:type :text, :text "."}]}
                      {:type :paragraph, :content [{:type :text, :text "Par."}]}
                      {:type :paragraph,
                       :content [{:type :text, :text "Again"}
                                 {:type :sidenote-ref, :ref 2, :label "note2"}
                                 {:type :sidenote, :content [{:type :text, :text "Explain 2"}], :attrs {:ref 2}}]}],
            :type :doc}
             parse+insert-sidenotes)))))


(comment
  (doseq [[n v] (ns-publics *ns*)] (ns-unmap *ns* n))
  (clojure.test/run-tests)
  (run-tests 'nextjournal.markdown-test)
  (run-test unique-heading-ids))
