(ns nextjournal.markdown.impl.html
  (:require [clojure.string :as str]))

(defn render-node
  "Render a single node to HTML"
  [node]
  (case (:type node)
    :doc
    (str/join "\n" (map render-node (:content node)))

    :paragraph
    (str "<p>" (str/join "" (map render-node (:content node))) "</p>")

    :heading
    (let [level (:level node)
          tag (str "h" level)]
      (str "<" tag ">" (str/join "" (map render-node (:content node))) "</" tag ">"))

    :blockquote
    (str "<blockquote>" (str/join "" (map render-node (:content node))) "</blockquote>")

    :code-block
    (let [info (:info node)
          content (:literal node)]
      (if (and info (not (str/blank? info)))
        (str "<pre><code class=\"language-" info "\">" content "</code></pre>")
        (str "<pre><code>" content "</code></pre>")))

    :bullet-list
    (str "<ul>" (str/join "" (map render-node (:content node))) "</ul>")

    :ordered-list
    (let [start (:list-start node)]
      (if (and start (not= start 1))
        (str "<ol start=\"" start "\">" (str/join "" (map render-node (:content node))) "</ol>")
        (str "<ol>" (str/join "" (map render-node (:content node))) "</ol>")))

    :list-item
    (str "<li>" (str/join "" (map render-node (:content node))) "</li>")

    :text
    ;; Text nodes contain plain text - no need for emphasis processing
    (:literal node)

    :emph
    (str "<em>" (str/join "" (map render-node (:content node))) "</em>")

    :strong
    (str "<strong>" (str/join "" (map render-node (:content node))) "</strong>")

    :link
    (let [url (:destination node)]
      (str "<a href=\"" url "\">" (str/join "" (map render-node (:content node))) "</a>"))

    :image
    (let [url (:destination node)
          alt (str/join "" (map render-node (:content node)))
          title (:title node)]
      (if title
        (str "<img src=\"" url "\" alt=\"" alt "\" title=\"" title "\" />")
        (str "<img src=\"" url "\" alt=\"" alt "\" />")))

    :autolink
    ;; Autolink nodes render as links with the URL as both href and text
    (let [url (:destination node)]
      (str "<a href=\"" url "\">" url "</a>"))

    :html-inline
    ;; HTML inline nodes contain raw HTML that should be preserved
    (:literal node)

    :code
    ;; Inline code nodes should be wrapped in <code> tags
    (str "<code>" (:literal node) "</code>")

    :softbreak
    ;; Softbreak nodes render as a newline in HTML
    "\n"

    :hardbreak
    ;; Hard line break renders as HTML <br /> tag
    "<br />"

    :thematic-break
    ;; Thematic break renders as an HTML horizontal rule
    "<hr />"

    ;; Default fallback
    (str "<!-- Unknown node type: " (:type node) " -->")))

(defn ->html [ast]
  (def *ast ast)
  (render-node ast))

(comment
  (render-node *ast)
  (:content *ast)
  )
