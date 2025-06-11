# Table of contents
-  [`nextjournal.markdown`](#nextjournal.markdown)  - Markdown as data.
    -  [`->hiccup`](#nextjournal.markdown/->hiccup) - Turns a markdown string into hiccup.
    -  [`empty-doc`](#nextjournal.markdown/empty-doc) - Empty document to be used with <code>parse*</code>.
    -  [`parse`](#nextjournal.markdown/parse) - Turns a markdown string into an AST of nested clojure data.
    -  [`parse*`](#nextjournal.markdown/parse*) - Turns a markdown string into an AST of nested clojure data.
-  [`nextjournal.markdown.transform`](#nextjournal.markdown.transform)  - transform markdown data as returned by <code>nextjournal.markdown/parse</code> into other formats, currently: * hiccup.
    -  [`->hiccup`](#nextjournal.markdown.transform/->hiccup)
    -  [`->text`](#nextjournal.markdown.transform/->text) - Convert node into text.
    -  [`default-hiccup-renderers`](#nextjournal.markdown.transform/default-hiccup-renderers)
    -  [`into-markup`](#nextjournal.markdown.transform/into-markup) - Takes a hiccup vector, a context and a node, puts node's <code>:content</code> into markup mapping through <code>-&gt;hiccup</code>.
    -  [`table-alignment`](#nextjournal.markdown.transform/table-alignment)
    -  [`toc->hiccup`](#nextjournal.markdown.transform/toc->hiccup)
-  [`nextjournal.markdown.utils`](#nextjournal.markdown.utils) 
    -  [`empty-doc`](#nextjournal.markdown.utils/empty-doc) - The empty doc.
    -  [`hashtag-tokenizer`](#nextjournal.markdown.utils/hashtag-tokenizer)
    -  [`insert-sidenote-containers`](#nextjournal.markdown.utils/insert-sidenote-containers)
    -  [`internal-link-tokenizer`](#nextjournal.markdown.utils/internal-link-tokenizer)
    -  [`normalize-tokenizer`](#nextjournal.markdown.utils/normalize-tokenizer) - Normalizes a map of regex and handler into a Tokenizer.
-  [`nextjournal.markdown.utils.emoji`](#nextjournal.markdown.utils.emoji)  - https://github.com/mathiasbynens/emoji-test-regex-pattern MIT License Copyright Mathias Bynens <https://mathiasbynens.be/>.
    -  [`regex`](#nextjournal.markdown.utils.emoji/regex)

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
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L48-L55">Source</a></sub></p>

## <a name="nextjournal.markdown/empty-doc">`empty-doc`</a>




Empty document to be used with [`parse*`](#nextjournal.markdown/parse*)
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown.cljc#L8-L10">Source</a></sub></p>

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
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L153-L162">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/->text">`->text`</a>
``` clojure

(->text node)
(->text ctx {:as _node, :keys [type text content]})
```
Function.

Convert node into text
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L8-L14">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/default-hiccup-renderers">`default-hiccup-renderers`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L71-L151">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/into-markup">`into-markup`</a>
``` clojure

(into-markup mkup ctx {:as node, :keys [text content]})
```
Function.

Takes a hiccup vector, a context and a node, puts node's `:content` into markup mapping through [`->hiccup`](#nextjournal.markdown.transform/->hiccup).
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L30-L37">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/table-alignment">`table-alignment`</a>
``` clojure

(table-alignment {:keys [style]})
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L21-L24">Source</a></sub></p>

## <a name="nextjournal.markdown.transform/toc->hiccup">`toc->hiccup`</a>
``` clojure

("toc->hiccup[{:as ctx ::keys [parent]} {:as node :keys [attrs content children]}]")
```
Function.
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/transform.cljc#L39-L51">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.utils">nextjournal.markdown.utils</a>






## <a name="nextjournal.markdown.utils/empty-doc">`empty-doc`</a>




The empty doc
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L5-L7">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/hashtag-tokenizer">`hashtag-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L13-L13">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/insert-sidenote-containers">`insert-sidenote-containers`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L17-L17">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/internal-link-tokenizer">`internal-link-tokenizer`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L15-L15">Source</a></sub></p>

## <a name="nextjournal.markdown.utils/normalize-tokenizer">`normalize-tokenizer`</a>




Normalizes a map of regex and handler into a Tokenizer
<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils.cljc#L9-L11">Source</a></sub></p>

-----
# <a name="nextjournal.markdown.utils.emoji">nextjournal.markdown.utils.emoji</a>


https://github.com/mathiasbynens/emoji-test-regex-pattern
  MIT License
  Copyright Mathias Bynens <https://mathiasbynens.be/>




## <a name="nextjournal.markdown.utils.emoji/regex">`regex`</a>



<p><sub><a href="https://github.com/nextjournal/markdown/blob/main/src/nextjournal/markdown/utils/emoji.cljc#L15-L16">Source</a></sub></p>
