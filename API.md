# Table of contents
-  [`nextjournal.markdown`](#nextjournal.markdown)  - Markdown as data.
    -  [`->hiccup`](#nextjournal.markdown/->hiccup) - Turns a markdown string into hiccup.
    -  [`empty-doc`](#nextjournal.markdown/empty-doc)
    -  [`parse`](#nextjournal.markdown/parse) - Turns a markdown string into an AST of nested clojure data.
    -  [`parse*`](#nextjournal.markdown/parse*) - Turns a markdown string into an AST of nested clojure data.
-  [`nextjournal.markdown.transform`](#nextjournal.markdown.transform)  - transform markdown data as returned by <code>nextjournal.markdown/parse</code> into other formats, currently: * hiccup.
    -  [`->hiccup`](#nextjournal.markdown.transform/->hiccup)
    -  [`->text`](#nextjournal.markdown.transform/->text)
    -  [`default-hiccup-renderers`](#nextjournal.markdown.transform/default-hiccup-renderers)
    -  [`guard`](#nextjournal.markdown.transform/guard)
    -  [`heading-markup`](#nextjournal.markdown.transform/heading-markup)
    -  [`hydrate-toc`](#nextjournal.markdown.transform/hydrate-toc) - Scans doc contents and replaces toc node placeholder with the toc node accumulated during parse.
    -  [`into-markup`](#nextjournal.markdown.transform/into-markup) - Takes a hiccup vector, a context and a node, puts node's <code>:content</code> into markup mapping through <code>-&gt;hiccup</code>.
    -  [`table-alignment`](#nextjournal.markdown.transform/table-alignment)
    -  [`toc->hiccup`](#nextjournal.markdown.transform/toc->hiccup)
-  [`nextjournal.markdown.utils`](#nextjournal.markdown.utils) 
    -  [`->zip`](#nextjournal.markdown.utils/->zip)
    -  [`add-title+toc`](#nextjournal.markdown.utils/add-title+toc) - Computes and adds a :title and a :toc to the document-like structure <code>doc</code> which might have not been constructed by means of <code>parse</code>.
    -  [`add-to-toc`](#nextjournal.markdown.utils/add-to-toc)
    -  [`block-formula`](#nextjournal.markdown.utils/block-formula)
    -  [`current-ancestor-nodes`](#nextjournal.markdown.utils/current-ancestor-nodes)
    -  [`current-loc`](#nextjournal.markdown.utils/current-loc)
    -  [`empty-doc`](#nextjournal.markdown.utils/empty-doc)
    -  [`footnote->sidenote`](#nextjournal.markdown.utils/footnote->sidenote)
    -  [`formula`](#nextjournal.markdown.utils/formula)
    -  [`handle-close-heading`](#nextjournal.markdown.utils/handle-close-heading)
    -  [`handle-text-token`](#nextjournal.markdown.utils/handle-text-token)
    -  [`hashtag-tokenizer`](#nextjournal.markdown.utils/hashtag-tokenizer)
    -  [`inc-last`](#nextjournal.markdown.utils/inc-last)
    -  [`insert-sidenote-containers`](#nextjournal.markdown.utils/insert-sidenote-containers) - Handles footnotes as sidenotes.
    -  [`internal-link-tokenizer`](#nextjournal.markdown.utils/internal-link-tokenizer)
    -  [`into-toc`](#nextjournal.markdown.utils/into-toc)
    -  [`node`](#nextjournal.markdown.utils/node)
    -  [`node-with-sidenote-refs`](#nextjournal.markdown.utils/node-with-sidenote-refs)
    -  [`normalize-tokenizer`](#nextjournal.markdown.utils/normalize-tokenizer) - Normalizes a map of regex and handler into a Tokenizer.
    -  [`parse-fence-info`](#nextjournal.markdown.utils/parse-fence-info)
    -  [`ppop`](#nextjournal.markdown.utils/ppop)
    -  [`re-groups*`](#nextjournal.markdown.utils/re-groups*)
    -  [`re-idx-seq`](#nextjournal.markdown.utils/re-idx-seq) - Takes a regex and a string, returns a seq of triplets comprised of match groups followed by indices delimiting each match.
    -  [`set-title-when-missing`](#nextjournal.markdown.utils/set-title-when-missing)
    -  [`split-by-emoji`](#nextjournal.markdown.utils/split-by-emoji)
    -  [`text->id+emoji`](#nextjournal.markdown.utils/text->id+emoji)
    -  [`text-node`](#nextjournal.markdown.utils/text-node)
    -  [`tokenize-text-node`](#nextjournal.markdown.utils/tokenize-text-node)
    -  [`update-current-loc`](#nextjournal.markdown.utils/update-current-loc)
    -  [`zdepth`](#nextjournal.markdown.utils/zdepth)
    -  [`zip?`](#nextjournal.markdown.utils/zip?)
    -  [`zopen-node`](#nextjournal.markdown.utils/zopen-node)
    -  [`zpath`](#nextjournal.markdown.utils/zpath) - Given a document zipper location <code>loc</code> returns a vector corresponding to the path of node at <code>loc</code> suitable for get-in from root.
-  [`nextjournal.markdown.utils.emoji`](#nextjournal.markdown.utils.emoji)  - https://github.com/mathiasbynens/emoji-test-regex-pattern MIT License Copyright Mathias Bynens <https://mathiasbynens.be/>.
    -  [`regex`](#nextjournal.markdown.utils.emoji/regex)
    -  [`regex-java`](#nextjournal.markdown.utils.emoji/regex-java)
    -  [`regex-js`](#nextjournal.markdown.utils.emoji/regex-js)

-----
# <a name="nextjournal.markdown">nextjournal.markdown</a>


Markdown as data




## <a name="nextjournal.markdown/->hiccup">`->hiccup`</a>
``` clojure

(->hiccup markdown)
(->hiccup ctx markdown)
```
Function.

Turns a markdown string into hiccup.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L46-L53">Source</a></sub></p>

## <a name="nextjournal.markdown/empty-doc">`empty-doc`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L8-L8">Source</a></sub></p>

## <a name="nextjournal.markdown/parse">`parse`</a>
``` clojure

(parse markdown-text)
(parse ctx markdown-text)
```
Function.

Turns a markdown string into an AST of nested clojure data.

  Accept options:
    - `:text-tokenizers` to customize parsing of text in leaf nodes (see https://nextjournal.github.io/markdown/notebooks/parsing_extensibility).
  
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L20-L35">Source</a></sub></p>

## <a name="nextjournal.markdown/parse*">`parse*`</a>
``` clojure

(parse* markdown-text)
(parse* ctx markdown-text)
```
Function.

Turns a markdown string into an AST of nested clojure data.
  Allows to parse multiple strings into the same document
  e.g. `(-> u/empty-doc (parse* text-1) (parse* text-2))`.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L10-L18">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.transform">nextjournal.markdown.transform</a>


transform markdown data as returned by [`nextjournal.markdown/parse`](#nextjournal.markdown/parse) into other formats, currently:
     * hiccup




## <a name="nextjournal.markdown.transform/->hiccup">`->hiccup`</a>
``` clojure

(->hiccup node)
(->hiccup ctx {:as node, t :type})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L149-L158">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/->text">`->text`</a>
``` clojure

(->text {:as _node, :keys [type text content]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L7-L10">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/default-hiccup-renderers">`default-hiccup-renderers`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L67-L147">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/guard">`guard`</a>
``` clojure

(guard pred val)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L6-L6">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/heading-markup">`heading-markup`</a>
``` clojure

(heading-markup {l :heading-level})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L22-L22">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/hydrate-toc">`hydrate-toc`</a>
``` clojure

(hydrate-toc {:as doc, :keys [toc]})
```
Function.

Scans doc contents and replaces toc node placeholder with the toc node accumulated during parse.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L12-L15">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/into-markup">`into-markup`</a>
``` clojure

(into-markup mkup ctx {:as node, :keys [text content]})
```
Function.

Takes a hiccup vector, a context and a node, puts node's `:content` into markup mapping through [`->hiccup`](#nextjournal.markdown.transform/->hiccup).
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L26-L33">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/table-alignment">`table-alignment`</a>
``` clojure

(table-alignment {:keys [style]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L17-L20">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/toc->hiccup">`toc->hiccup`</a>
``` clojure

("toc->hiccup[{:as ctx ::keys [parent]} {:as node :keys [attrs content children]}]")
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L35-L47">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.utils">nextjournal.markdown.utils</a>






## <a name="nextjournal.markdown.utils/->zip">`->zip`</a>
``` clojure

(->zip doc)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L81-L84">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/add-title+toc">`add-title+toc`</a>
``` clojure

(add-title+toc {:as doc, :keys [content]})
```
Function.

Computes and adds a :title and a :toc to the document-like structure `doc` which might have not been constructed by means of `parse`.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L157-L162">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/add-to-toc">`add-to-toc`</a>
``` clojure

(add-to-toc doc {:as h, :keys [heading-level]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L151-L152">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/block-formula">`block-formula`</a>
``` clojure

(block-formula text)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L71-L71">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/current-ancestor-nodes">`current-ancestor-nodes`</a>
``` clojure

(current-ancestor-nodes loc)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L282-L287">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/current-loc">`current-loc`</a>
``` clojure

(current-loc {:as ctx, :nextjournal.markdown.impl/keys [root]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L64-L64">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/empty-doc">`empty-doc`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L49-L62">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/footnote->sidenote">`footnote->sidenote`</a>
``` clojure

(footnote->sidenote {:keys [ref label content]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L368-L370">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/formula">`formula`</a>
``` clojure

(formula text)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L70-L70">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/handle-close-heading">`handle-close-heading`</a>
``` clojure

(handle-close-heading ctx)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L164-L179">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/handle-text-token">`handle-text-token`</a>
``` clojure

(handle-text-token {:as ctx, :keys [text-tokenizers]} text)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L259-L267">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/hashtag-tokenizer">`hashtag-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L289-L292">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/inc-last">`inc-last`</a>
``` clojure

(inc-last path)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L121-L121">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/insert-sidenote-containers">`insert-sidenote-containers`</a>
``` clojure

(insert-sidenote-containers {:as doc, :keys [footnotes]})
```
Function.

Handles footnotes as sidenotes.

   Takes and returns a parsed document. When the document has footnotes, wraps every top-level block which contains footnote references
   with a `:footnote-container` node, into each of such nodes, adds a `:sidenote-column` node containing a `:sidenote` node for each found ref.
   Renames type `:footnote-ref` to `:sidenote-ref.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L372-L398">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/internal-link-tokenizer">`internal-link-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L294-L297">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/into-toc">`into-toc`</a>
``` clojure

(into-toc toc {:as toc-item, :keys [heading-level]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L128-L149">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/node">`node`</a>
``` clojure

(node type content attrs top-level)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L73-L77">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/node-with-sidenote-refs">`node-with-sidenote-refs`</a>
``` clojure

(node-with-sidenote-refs p-node)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L358-L366">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/normalize-tokenizer">`normalize-tokenizer`</a>
``` clojure

(normalize-tokenizer {:as tokenizer, :keys [doc-handler pred handler regex tokenizer-fn]})
```
Function.

Normalizes a map of regex and handler into a Tokenizer
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L273-L280">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/parse-fence-info">`parse-fence-info`</a>
``` clojure

(parse-fence-info info-str)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L328-L346">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/ppop">`ppop`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L120-L120">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/re-groups*">`re-groups*`</a>
``` clojure

(re-groups* m)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L8-L8">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/re-idx-seq">`re-idx-seq`</a>
``` clojure

(re-idx-seq re text)
```
Function.

Takes a regex and a string, returns a seq of triplets comprised of match groups followed by indices delimiting each match.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L9-L15">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/set-title-when-missing">`set-title-when-missing`</a>
``` clojure

(set-title-when-missing {:as doc, :keys [title]} heading)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L154-L155">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/split-by-emoji">`split-by-emoji`</a>
``` clojure

(split-by-emoji s)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L22-L26">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/text->id+emoji">`text->id+emoji`</a>
``` clojure

(text->id+emoji text)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L42-L46">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/text-node">`text-node`</a>
``` clojure

(text-node s)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L69-L69">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/tokenize-text-node">`tokenize-text-node`</a>
``` clojure

(tokenize-text-node {:as tkz, :keys [tokenizer-fn pred doc-handler]} ctx {:as node, :keys [text]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L232-L257">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/update-current-loc">`update-current-loc`</a>
``` clojure

(update-current-loc {:as ctx, :nextjournal.markdown.impl/keys [root]} f & args)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L65-L67">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/zdepth">`zdepth`</a>
``` clojure

(zdepth loc)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L86-L86">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/zip?">`zip?`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L85-L85">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/zopen-node">`zopen-node`</a>
``` clojure

(zopen-node loc node)
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L94-L95">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/zpath">`zpath`</a>
``` clojure

(zpath loc)
```
Function.

Given a document zipper location `loc` returns a vector corresponding to the path of node at `loc`
   suitable for get-in from root. That is `(= (z/node loc) (get-in (z/root loc) (zpath loc)`
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L97-L105">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.utils.emoji">nextjournal.markdown.utils.emoji</a>


https://github.com/mathiasbynens/emoji-test-regex-pattern
  MIT License
  Copyright Mathias Bynens <https://mathiasbynens.be/>




## <a name="nextjournal.markdown.utils.emoji/regex">`regex`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils/emoji.cljc#L13-L14">Source</a></sub></p>

## <a name="nextjournal.markdown.utils.emoji/regex-java">`regex-java`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils/emoji.cljc#L7-L8">Source</a></sub></p>

## <a name="nextjournal.markdown.utils.emoji/regex-js">`regex-js`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils/emoji.cljc#L11-L11">Source</a></sub></p>
