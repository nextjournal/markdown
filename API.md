# Table of contents
-  [`nextjournal.markdown`](#nextjournal.markdown)  - Markdown as data.
    -  [`->hiccup`](#nextjournal.markdown/->hiccup) - Turns a markdown string or document node into hiccup.
    -  [`default-hiccup-renderers`](#nextjournal.markdown/default-hiccup-renderers) - Default map of node type -> hiccup renderers, to be used with <code>-&gt;hiccup</code>.
    -  [`empty-doc`](#nextjournal.markdown/empty-doc) - Empty document to be used with <code>parse*</code>.
    -  [`into-hiccup`](#nextjournal.markdown/into-hiccup) - Helper function to be used with custom hiccup renderer.
    -  [`node->text`](#nextjournal.markdown/node->text) - Convert node into text.
    -  [`parse`](#nextjournal.markdown/parse) - Turns a markdown string into an AST of nested clojure data.
    -  [`parse*`](#nextjournal.markdown/parse*) - Turns a markdown string into an AST of nested clojure data.
    -  [`table-alignment`](#nextjournal.markdown/table-alignment) - Takes a table-ish node, returns a map suitable for hiccup style attributes with a :text-align property.
    -  [`toc->hiccup`](#nextjournal.markdown/toc->hiccup) - Transform a toc node into hiccup data, suitable for using as renderer function in hiccup transform, see [->hiccup](#markdown.transform/toc->hiccup).
-  [`nextjournal.markdown.utils`](#nextjournal.markdown.utils) 
    -  [`block-formula`](#nextjournal.markdown.utils/block-formula)
    -  [`emoji-regex`](#nextjournal.markdown.utils/emoji-regex)
    -  [`empty-doc`](#nextjournal.markdown.utils/empty-doc) - The empty doc.
    -  [`formula`](#nextjournal.markdown.utils/formula)
    -  [`hashtag-tokenizer`](#nextjournal.markdown.utils/hashtag-tokenizer)
    -  [`insert-sidenote-containers`](#nextjournal.markdown.utils/insert-sidenote-containers)
    -  [`internal-link-tokenizer`](#nextjournal.markdown.utils/internal-link-tokenizer)
    -  [`normalize-tokenizer`](#nextjournal.markdown.utils/normalize-tokenizer) - Normalizes a map of regex and handler into a Tokenizer.
    -  [`text-node`](#nextjournal.markdown.utils/text-node)
    -  [`tokenize-text-node`](#nextjournal.markdown.utils/tokenize-text-node)

-----
# <a name="nextjournal.markdown">nextjournal.markdown</a>


Markdown as data




## <a name="nextjournal.markdown/->hiccup">`->hiccup`</a>
``` clojure

(->hiccup markdown)
(->hiccup hiccup-renderers markdown)
```
Function.

Turns a markdown string or document node into hiccup. Optionally takes
  `hiccup-renderers` as first argument.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L45-L53">Source</a></sub></p>

## <a name="nextjournal.markdown/default-hiccup-renderers">`default-hiccup-renderers`</a>




Default map of node type -> hiccup renderers, to be used with [`->hiccup`](#nextjournal.markdown/->hiccup)
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L41-L43">Source</a></sub></p>

## <a name="nextjournal.markdown/empty-doc">`empty-doc`</a>




Empty document to be used with [`parse*`](#nextjournal.markdown/parse*)
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L8-L10">Source</a></sub></p>

## <a name="nextjournal.markdown/into-hiccup">`into-hiccup`</a>




Helper function to be used with custom hiccup renderer.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L59-L61">Source</a></sub></p>

## <a name="nextjournal.markdown/node->text">`node->text`</a>




Convert node into text.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L55-L57">Source</a></sub></p>

## <a name="nextjournal.markdown/parse">`parse`</a>
``` clojure

(parse markdown-text)
(parse ctx markdown-text)
```
Function.

Turns a markdown string into an AST of nested clojure data.

  Accept options:
    - `:text-tokenizers` to customize parsing of text in leaf nodes (see https://nextjournal.github.io/markdown/notebooks/parsing_extensibility).
  
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L22-L37">Source</a></sub></p>

## <a name="nextjournal.markdown/parse*">`parse*`</a>
``` clojure

(parse* markdown-text)
(parse* ctx markdown-text)
```
Function.

Turns a markdown string into an AST of nested clojure data.
  Allows to parse multiple strings into the same document
  e.g. `(-> empty-doc (parse* text-1) (parse* text-2))`.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L12-L20">Source</a></sub></p>

## <a name="nextjournal.markdown/table-alignment">`table-alignment`</a>




Takes a table-ish node, returns a map suitable for hiccup style attributes with a :text-align property.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L63-L65">Source</a></sub></p>

## <a name="nextjournal.markdown/toc->hiccup">`toc->hiccup`</a>




Transform a toc node into hiccup data, suitable for using as renderer function in hiccup transform, see [->hiccup](#markdown.transform/toc->hiccup)
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L67-L69">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.utils">nextjournal.markdown.utils</a>






## <a name="nextjournal.markdown.utils/block-formula">`block-formula`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L24-L24">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/emoji-regex">`emoji-regex`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L28-L28">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/empty-doc">`empty-doc`</a>




The empty doc
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L6-L8">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/formula">`formula`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L22-L22">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/hashtag-tokenizer">`hashtag-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L14-L14">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/insert-sidenote-containers">`insert-sidenote-containers`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L18-L18">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/internal-link-tokenizer">`internal-link-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L16-L16">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/normalize-tokenizer">`normalize-tokenizer`</a>




Normalizes a map of regex and handler into a Tokenizer
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L10-L12">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/text-node">`text-node`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L20-L20">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/tokenize-text-node">`tokenize-text-node`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L26-L26">Source</a></sub></p>
