(ns nextjournal.markdown-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [matcher-combinators.test :refer [match?]]
            [matcher-combinators.matchers :as m]
            [nextjournal.markdown :as md]
            [matcher-combinators.ansi-color]
            [nextjournal.markdown.utils :as u]
            [nextjournal.markdown.transform :as md.transform]))

;; com.bhauman/cljs-test-display doesn't play well with ANSI codes
#?(:cljs (matcher-combinators.ansi-color/disable!))

(deftest simple-parsing
  (is (= {:type :doc
          :title "Ahoi"
          :toc {:type :toc,
                :children [{:type :toc,
                            :heading-level 1,
                            :content [{:type :text, :text "Ahoi"}],
                            :attrs {:id "ahoi"},
                            :path [:content 0]}]}
          :content [{:type :heading
                     :heading-level 1
                     :attrs {:id "ahoi"}
                     :content [{:type :text, :text "Ahoi"}]}
                    {:type :bullet-list,
                     :content [{:type :list-item, :content [{:type :plain, :content [{:type :text, :text "one"}]}]}
                               {:type :list-item, :content [{:type :plain, :content [{:type :em, :content [{:type :text, :text "nice"}]}]}]}
                               {:type :list-item, :content [{:type :plain, :content [{:type :text, :text "list"}]}]}]}]
          :footnotes []}
         (md/parse "# Ahoi
* one
* _nice_
* list"))))

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

(deftest autolinks
  (is (match? {:type :doc
               :content [{:type :paragraph
                          :content [{:type :link
                                     :content [{:type :text, :text "https://clerk.vision"}]}]}]}
              (md/parse "https://clerk.vision"))))

(defn parse-internal-links [text]
  (md/parse* (update u/empty-doc :text-tokenizers conj u/internal-link-tokenizer)
             text))

(defn parse-hashtags [text]
  (md/parse* (update u/empty-doc :text-tokenizers conj u/hashtag-tokenizer)
             text))

(deftest parse-test
  (testing "ingests markdown returns nested nodes"
    (is (= {:type :doc
            :footnotes []
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
            :toc {:type :toc
                  :children [{:type :toc
                              :content [{:type :text, :text "🎱 Hello"}]
                              :heading-level 1
                              :attrs {:id "hello"}
                              :emoji "🎱"
                              :path [:content 0]}]}}
           (md/parse markdown-text))))

  (testing "parses internal links / plays well with todo lists"
    (is (match? {:type :doc
                 :content [{:type :paragraph
                            :content [{:text "a "
                                       :type :text}
                                      {:text "wikistyle"
                                       :type :internal-link}
                                      {:text " link"
                                       :type :text}]}]}
                (parse-internal-links "a [[wikistyle]] link")))

    (is (match? {:type :doc
                 :content [{:heading-level 1
                            :type :heading
                            :content [{:text "a "
                                       :type :text}
                                      {:text "wikistyle"
                                       :type :internal-link}
                                      {:text " link in title"
                                       :type :text}]}]}
                (parse-internal-links "# a [[wikistyle]] link in title")))

    (is (match? {:type :doc
                 :toc {:type :toc}
                 :content [{:type :todo-list
                            :content [{:type :todo-item
                                       :attrs {:checked true}
                                       :content [{:content [{:text "done "
                                                             :type :text}
                                                            {:text "linkme"
                                                             :type :internal-link}
                                                            {:text " to"
                                                             :type :text}]
                                                  :type :plain}]}
                                      {:type :todo-item
                                       :attrs {:checked false}
                                       :content [{:type :plain
                                                  :content [{:text "pending"
                                                             :type :text}]}]}
                                      {:type :todo-item
                                       :attrs {:checked false}
                                       :content [{:type :plain
                                                  :content [{:text "pending"
                                                             :type :text}]}]}]}]}
                (parse-internal-links "- [x] done [[linkme]] to
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

(deftest strikethrough-test
  (testing "single tilde")
  ;; Here markdown-it follows Pandoc and uses single ~ for 'sub' (enabled by default https://github.com/markdown-it/markdown-it/tree/master?tab=readme-ov-file#syntax-extensions).
  ;; It differs from GFM spec https://github.github.com/gfm/#strikethrough-extension- which allows signle tilde syntax
  (testing "double tilde"
    (is (= {:type :paragraph, :content [{:type :text, :text "some "} {:type :strikethrough, :content [{:type :text, :text "not ok"}]} {:type :text, :text " text"}]}
           (-> (md/parse "some ~~not ok~~ text")
               :content first)))))

(deftest tables-test
  (is (= {:type :doc
          :content [{:type :table
                     :content [{:type :table-head :content [{:type :table-row, :content [{:type :table-header, :content [{:type :text, :text "x"}]} {:type :table-header, :content [{:type :text, :text "y"}]}]}]}
                               {:type :table-body :content [{:type :table-row, :content [{:type :table-data, :content [{:type :text, :text "1"}]} {:type :table-data, :content [{:type :text, :text "2"}]}]} {:type :table-row, :content [{:type :table-data, :content [{:type :text, :text "3"}]} {:type :table-data, :content [{:type :text, :text "4"}]}]}]}]}]
          :toc {:type :toc}
          :footnotes []}

         (md/parse "
|  x |  y |
|----|----|
|  1 |  2 |
|  3 |  4 |
"))))

(deftest table-with-empty-cells
  (is (match? [:div [:table
                     [:thead
                      [:tr [:th "x"][:th "y"]]]
                     [:tbody
                      [:tr [:td "1"][:td "2"]]
                      [:tr [:td][:td "4"]]]]]
              (md/->hiccup "
|  x |  y |
|----|----|
|  1 |  2 |
|    |  4 |
"))))

(deftest table-alignment
  (is (match? [:div [:table
                     [:thead
                      [:tr
                       [:th {:style {:text-align "left"}} "x"]
                       [:th {:style {:text-align "center"}} "y"]
                       [:th {:style {:text-align "right"}} "z"]]]
                     [:tbody
                      [:tr
                       [:td {:style {:text-align "left"}} "1"]
                       [:td {:style {:text-align "center"}} "2"]
                       [:td {:style {:text-align "right"}} "3"]]]]]
              (md/->hiccup "
| x |  y | z |
|:--|:--:|--:|
| 1 |  2 | 3 |
"))))


(deftest hard-breaks
  (is (= [:div [:p "Please don't inter" [:br] "rupt me when I'm writing."]]
         (md/->hiccup "Please don't inter  \nrupt me when I'm writing.")
         (md/->hiccup "Please don't inter\\
rupt me when I'm writing."))))

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
              '("checked")]
             [:li
              [:input
               {:checked false
                :type "checkbox"}]
              '("unchecked")
              [:ul.contains-task-list
               [:li
                [:input
                 {:checked false
                  :type "checkbox"}]
                '("nested")]]]]]
           (md/->hiccup "# Todos
- [x] checked
- [ ] unchecked
  - [ ] nested
")))))

(deftest hashtags-test
  (testing "parsing tags"
    (is (match? {:type :doc
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
                            :type :paragraph}]}
                (parse-hashtags "# Hello Tags
par with #really_nice #useful-123 tags
"))))

  (testing "Should not parse hashtags within link text"
    (is (match? {:type :doc
                 :content [{:attrs {:id "hello-fishes"}
                       :content [{:text "Hello "
                                  :type :text}
                                 {:text "Fishes"
                                  :type :hashtag}]
                       :heading-level 1
                       :type :heading}
                      {:content [{:content [{:text "what about "
                                             :type :text}
                                            {:text "this"
                                             :type :hashtag}
                                            {:type :softbreak}
                                            {:content [{:text "this "
                                                        :type :text}
                                                       {:text "should"
                                                        :type :hashtag}
                                                       {:text " be a tag"
                                                        :type :text}]
                                             :type :em}
                                            {:text ", but this "
                                             :type :text}
                                            {:attrs {:href "/bar/"}
                                             :content [{:content [{:text "actually #foo shouldnt"
                                                                   :type :text}]
                                                        :type :em}]
                                             :type :link}
                                            {:text " is not."
                                             :type :text}]
                                  :type :paragraph}]
                       :type :blockquote}]}
                (parse-hashtags
                 "# Hello #Fishes
> what about #this
_this #should be a tag_, but this [_actually #foo shouldnt_](/bar/) is not."))))

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
           (md.transform/->hiccup
            (parse-hashtags "# Hello Tags
par with #really_nice #useful-123 tags
"))))))


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
  (is (match? (m/embeds [{:type :heading :attrs {:id "introduction"}}
                         {:type :heading :attrs {:id "quantum-physics"} :emoji "👩‍🔬"}
                         {:type :heading :attrs {:id "references-📕"}}
                         {:type :heading :attrs {:id "quantum-physics-2"} :emoji "⚛"}
                         {:type :heading :attrs {:id "quantum-physics-3"}}])

              (:content (md/parse "
## Introduction
Lorem ipsum et cetera.
### 👩‍🔬 Quantum Physics
Dolor sit and so on.
## References 📕
It's important to cite your references!
### ⚛ Quantum Physics
Particularly for quantum physics!
#### Quantum Physics
Did we mention qtmphysics?
")))))

(deftest per-node-text-transform

  (is (= "Should ignore marks and interpret softbreaks as spaces"
         (-> (md/parse "Should **ignore** marks
and _interpret_
softbreaks as
spaces")
             md.transform/->text))))

(def parsed+sidenotes
  (-> "Text[^firstnote] and^[inline _note_ here].

Par.

- again[^note2]
- here

[^firstnote]: Explain 1
[^note2]: Explain 2
"
      md/parse
      u/insert-sidenote-containers))

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

    (is (match? {:type :doc
                 :sidenotes? true
                 :content [{:type :sidenote-container
                            :content [{:type :paragraph
                                       :content [{:text "Text"
                                                  :type :text}
                                                 {:label "firstnote"
                                                  :ref 0
                                                  :type :sidenote-ref}
                                                 {:text " and"
                                                  :type :text}
                                                 {:ref 1
                                                  :type :sidenote-ref}
                                                 {:text "."
                                                  :type :text}]}
                                      {:type :sidenote-column
                                       :content [{:type :sidenote
                                                  :ref 0
                                                  :content [{:text "Explain 1" :type :text}]
                                                  :label "firstnote"}
                                                 {:type :sidenote
                                                  :ref 1
                                                  :content [{:text "inline " :type :text}
                                                            {:content [{:text "note"
                                                                        :type :text}]
                                                             :type :em}
                                                            {:text " here"
                                                             :type :text}]}]}]}
                           {:type :paragraph
                            :content [{:text "Par." :type :text}]}
                           {:type :sidenote-container
                            :content [{:type :bullet-list
                                       :content [{:type :list-item
                                                  :content [{:type :plain
                                                             :content [{:text "again"
                                                                        :type :text}
                                                                       {:label "note2"
                                                                        :ref 2
                                                                        :type :sidenote-ref}]}]}
                                                 {:type :list-item
                                                  :content [{:content [{:text "here" :type :text}]
                                                             :type :plain}]}]}
                                      {:type :sidenote-column
                                       :content [{:type :sidenote
                                                  :ref 2
                                                  :content [{:text "Explain 2"
                                                             :type :text}]
                                                  :label "note2"}]}]}]
                 :footnotes [{:content [{:content [{:text "Explain 1"
                                                    :type :text}]
                                         :type :paragraph}]
                              :label "firstnote"
                              :ref 0
                              :type :footnote}
                             {:content [{:content [{:text "inline "
                                                    :type :text}
                                                   {:content [{:text "note"
                                                               :type :text}]
                                                    :type :em}
                                                   {:text " here"
                                                    :type :text}]
                                         :type :paragraph}]
                              :ref 1
                              :type :footnote}
                             {:content [{:content [{:text "Explain 2"
                                                    :type :text}]
                                         :type :paragraph}]
                              :label "note2"
                              :ref 2
                              :type :footnote}]}

                parsed+sidenotes))

    (is (= [:div
            [:div.sidenote-container
             [:p
              "Text"
              [:sup.sidenote-ref
               {:data-label "firstnote"}
               "1"]
              " and"
              [:sup.sidenote-ref
               {:data-label "inline-note-1"}
               "2"]
              "."]
             [:div.sidenote-column
              [:span.sidenote
               [:sup
                {:style {:margin-right "3px"}}
                "1"]
               "Explain 1"]
              [:span.sidenote
               [:sup
                {:style {:margin-right "3px"}}
                "2"]
               "inline "
               [:em
                "note"]
               " here"]]]
            [:p
             "Par."]
            [:div.sidenote-container
             [:ul
              [:li
               '("again"
                 [:sup.sidenote-ref
                  {:data-label "note2"}
                  "3"])]
              [:li
               '("here")]]
             [:div.sidenote-column
              [:span.sidenote
               [:sup
                {:style {:margin-right "3px"}}
                "3"]
               "Explain 2"]]]]
           (md.transform/->hiccup parsed+sidenotes)))))

(deftest parse-multiple-inputs
  (testing "adding to the ToC"
    (is (match? {:type :doc
                 :toc {:type :toc
                       :children [{:attrs {:id "title"}
                                   :heading-level 1,
                                   :children [{:attrs {:id "section"}
                                               :heading-level 2}]}]}}
                (-> (md/parse* "# Title")
                    (md/parse* "## Section")))))

  (testing "footnote defs"
    (is (match? {:type :doc
                 :footnotes [{:type :footnote :ref 0 :label "n1"}
                             {:type :footnote :ref 1 :label "n2"}
                             {:type :footnote :ref 2 :label "n3"}]}
                (-> (md/parse* "some text[^n1] and[^n2].

[^n1]: Some _nice_ explanation
[^n2]: Some _nicer_ explanation
")
                    (md/parse* "some new text[^n2] and some own[^n3].

[^n3]: Some _own_ explanation")))))

  (testing "inline footnotes"
    (is (match? {:type :doc
                 :content [{:type :paragraph} {:type :paragraph}]
                 :footnotes [{:type :footnote :label "inline-note-0"}
                             {:type :footnote :label "inline-note-1"}]}
                (-> (md/parse* "some text^[with am inline note]")
                    (md/parse* "some new text^[with another inline note]"))))))

(deftest commonmark-compliance
  ;; we need an extra [:div] for embedding purposes, which might be dropped e.g. by configuring the `:doc` type renderer to use a react fragment `[:<>]`

  (testing "images"
    ;; https://spec.commonmark.org/0.30/#example-571
    (is (= [:div [:p [:img {:src "/url" :alt "foo" :title "title"}]]]
           (md/->hiccup "![foo](/url \"title\")")))

    ;; https://spec.commonmark.org/0.30/#example-578
    (is (= [:div [:p "My " [:img {:alt "foo bar" :src "/path/to/train.jpg" :title "title"}]]]
           (md/->hiccup "My ![foo bar](/path/to/train.jpg  \"title\"   )"))))

  (testing "loose vs. tight lists"
    ;; https://spec.commonmark.org/0.30/#example-314 (loose list)
    (is (= [:div [:ul [:li [:p "a"]] [:li [:p "b"]] [:li [:p "c"]]]]
           (md/->hiccup "- a\n- b\n\n- c")))

    ;; https://spec.commonmark.org/0.30/#example-319 (tight with loose sublist inside)
    (is (= '[:div [:ul [:li ("a") [:ul [:li [:p "b"] [:p "c"]]]] [:li ("d")]]]
           (md/->hiccup "- a\n  - b\n\n    c\n- d\n")))

    ;; https://spec.commonmark.org/0.30/#example-320 (tight with blockquote inside)
    (is (= '[:div [:ul [:li ("a") [:blockquote [:p "b"]]] [:li ("c")]]]
           (md/->hiccup "* a\n  > b\n  >\n* c")))))

(deftest repro-19-test
  (is (match? {:type :toc
               :children [{:type :toc
                           :heading-level 1
                           :children [{:type :toc
                                       :heading-level 2}]}]}
             (:toc (md/parse "# Title
some par

$$p(z\\\\mid x) = \\\\frac{p(x\\\\mid z)p(z)}{p(x)}.$$\n\n

## SubTitle
")))))


(deftest formulas
  (is (match? {:type :doc
               :content [{:type :heading}
                         {:type :paragraph
                          :content [{:type :text, :text "This is an "}
                                    {:type :formula, :text "\\mathit{inline}"}
                                    {:type :text, :text " formula."}]}
                         {:type :block-formula, :text string?}
                         {:type :block-formula, :text "\\bigoplus"}]}
              (md/parse "# Title
This is an $\\mathit{inline}$ formula.

$$
\\begin{equation}
\\dfrac{1}{128\\pi^{2}}
\\end{equation}
$$

$$\\bigoplus$$
"))))

(deftest toc-test
  (testing "extracts toc structure"
    (is (match? {:type :toc,
                 :children [{:type :toc,
                             :content [{:type :text, :text "Title"}],
                             :heading-level 1,
                             :attrs {:id "title"},
                             :path [:content 0],
                             :children [{:type :toc,
                                         :content [{:type :text, :text "Section One"}],
                                         :heading-level 2,
                                         :attrs {:id "section-one"},
                                         :path [:content 1],
                                         :children [{:type :toc,
                                                     :content [{:type :text, :text "Section 1.1"}],
                                                     :heading-level 3,
                                                     :attrs {:id "section-1.1"},
                                                     :path [:content 3]}
                                                    {:type :toc,
                                                     :content [{:type :text, :text "Section 1.2"}],
                                                     :heading-level 3,
                                                     :attrs {:id "section-1.2"},
                                                     :path [:content 4]}]}
                                        {:type :toc,
                                         :content [{:type :text, :text "Section Two"}],
                                         :heading-level 2,
                                         :attrs {:id "section-two"},
                                         :path [:content 6],
                                         :children [{:type :toc,
                                                     :content [{:type :text, :text "Section 2.1"}],
                                                     :heading-level 3,
                                                     :attrs {:id "section-2.1"},
                                                     :path [:content 8],
                                                     :children [{:type :toc,
                                                                 :content [{:type :text, :text "Section 3.1"}],
                                                                 :heading-level 4,
                                                                 :attrs {:id "section-3.1"},
                                                                 :path [:content 9]}]}]}]}]}
                (:toc (md/parse "# Title
## Section One
some text
### Section 1.1
### Section 1.2
some text
## Section Two
some text
### Section 2.1
#### Section 3.1
"))))))


(deftest link-test
  (testing "links with a title"
    (is (= {:type :link
            :attrs {:href "/some/path" :title "this is the link title"}
            :content [{:type :text :text "ahoi"}]}
           (-> (md/parse "[ahoi](/some/path 'this is the link title')")
               :content first :content first))))
  (testing "links with no title"
    (is (= {:type :link
            :attrs {:href "/some/path"}
            :content [{:type :text :text "ahoi"}]}
           (-> (md/parse "[ahoi](/some/path)")
               :content first :content first))))
  (testing "links with nested content"
    (is (= {:type :link
            :attrs {:href "/some/path"}
            :content [{:type :text :text "some "}
                      {:type :em :content [{:type :text :text "markup"}]}
                      {:type :text :text " here"}]}
           (-> (md/parse "[some _markup_ here](/some/path)")
               :content first :content first)))))

(deftest code-test
  (testing "inline code"
    (is (= {:type :paragraph
            :content [{:type :text :text "some "}
                      {:type :monospace :content [{:text "inline code" :type :text}]}
                      {:type :text :text " here"}]}
           (-> (md/parse "some `inline code` here")
               :content first))))
  (testing "indented code blocks"
    (is (= {:type :code
            :content [{:type :text :text "and indented\ncode here\n"}]}
           (-> (md/parse "some text

    and indented
    code here

back to text") :content second)))

    (testing "fenced code blocks"
      (is (= {:type :code :info ""
              :content [{:type :text :text "and fenced\ncode here\n"}]}
             (-> (md/parse "some text
```
and fenced
code here
```
back to text") :content second)))

      (is (= {:type :code
              :content [{:type :text :text "(this is code)\n"}]
              :info "clojure id=12345 no-exec"
              :language "clojure" :id "12345" :no-exec true}
             (-> (md/parse "```clojure id=12345 no-exec
(this is code)
```")
                 :content first))))))

(deftest html-test
  (is (= {:type :html-block, :text "<article>\nfoobar\n</article>"}
         (-> (md/parse "<article>
foobar
</article>
") :content first)))
  (is (= {:type :html-block, :text "<img src=\"foo\"/>"}
         (-> (md/parse "<img src=\"foo\"/>
") :content first)))
  (is (= {:type :paragraph,
          :content
          [{:type :text, :text "Hello "}
           {:type :html-inline, :text "<a href=\"dude\">"}
           {:type :em, :content [{:type :text, :text "Dude"}]}
           {:type :html-inline, :text "</a>"}]}
         (-> (md/parse "Hello <a href=\"dude\">*Dude*</a>")
             :content first))))


;; Hello <a href=\"dude\">*Dude*</a>

(comment
  (clojure.test/run-test-var #'formulas)
  (shadow.cljs.devtools.api/repl :browser-test)
  (doseq [[n v] (ns-publics *ns*)] (ns-unmap *ns* n))
  (clojure.test/run-tests)
  (run-tests 'nextjournal.markdown-test)
  (run-test unique-heading-ids))
