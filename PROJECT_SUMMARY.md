# NextJournal Markdown - Project Summary

## Overview

NextJournal Markdown is a cross-platform Clojure library for parsing Markdown into structured data and transforming it into various output formats, primarily Hiccup. The library provides a data-first approach to Markdown processing, yielding an Abstract Syntax Tree (AST) that represents the structured document content as nested Clojure data.

**Key Features:**
- Cross-platform support (JVM via commonmark-java, ClojureScript via markdown-it)
- CommonMark Spec compliance with GitHub Flavored Markdown extensions
- LaTeX formula support (inline: $...$ and display: $$...$$)
- Extensible parsing with custom text tokenizers
- Configurable Hiccup transformation
- Table of contents generation
- Footnotes and sidenotes support
- Task list support

## Project Structure

### Core Source Files

- **`src/nextjournal/markdown.cljc`** - Main public API namespace
  - `parse` - Main function to parse markdown into AST
  - `parse*` - Lower-level parsing for multiple strings into same document
  - `->hiccup` - Transform AST or markdown string to Hiccup format
  - `empty-doc` - Base document structure

- **`src/nextjournal/markdown/transform.cljc`** - Transformation engine
  - `->hiccup` - Core transformation function with customizable renderers
  - `->text` - Extract plain text from nodes
  - `default-hiccup-renderers` - Default transformation rules for all node types
  - `into-markup` - Helper for building Hiccup structures
  - `hydrate-toc` - Process table of contents

- **`src/nextjournal/markdown/utils.cljc`** - Utility functions
  - `empty-doc` - Base document structure with zipper support
  - Text tokenization system for extensibility
  - Footnote/sidenote processing
  - Emoji handling and ID generation
  - Zipper operations for AST manipulation

- **`src/nextjournal/markdown/utils/emoji.cljc`** - Emoji regex patterns for different platforms

### Platform-Specific Implementation

- **`src/nextjournal/markdown/impl.clj`** - JVM implementation using commonmark-java
- **`src/nextjournal/markdown/impl.cljs`** - ClojureScript implementation using markdown-it
- **`src/js/markdown.js`** - JavaScript markdown-it configuration with plugins

### Configuration Files

- **`deps.edn`** - Project dependencies and aliases
- **`shadow-cljs.edn`** - ClojureScript build configuration
- **`bb.edn`** - Babashka build tasks and scripts
- **`build.clj`** - Library build and deployment scripts
- **`package.json`** - Node.js dependencies for ClojureScript build

### Documentation & Examples

- **`notebooks/`** - Clerk notebooks demonstrating usage
  - `try.clj` - Interactive examples
  - `pandoc.clj` - Pandoc-style AST examples
  - `parsing_extensibility.clj` - Custom parser examples
  - `images.clj` - Image handling examples
- **`API.md`** - Generated API documentation
- **`README.md`** - Project overview and basic usage

### Tests

- **`test/nextjournal/markdown_test.cljc`** - Main test suite
- **`test/nextjournal/markdown/multi_threading_test.clj`** - JVM threading tests
- **`test/test_runner.clj`** - Test execution configuration

## Dependencies

### Core Dependencies (JVM)
- `org.commonmark/commonmark` (0.24.0) - CommonMark parsing
- `org.commonmark/commonmark-ext-*` - Extensions for autolinks, footnotes, tables, etc.

### Development Dependencies
- `io.github.nextjournal/clerk` - Notebook system for documentation
- `thheller/shadow-cljs` - ClojureScript compilation
- `nubank/matcher-combinators` - Enhanced test assertions
- `hiccup/hiccup` - HTML generation for testing

### JavaScript Dependencies (ClojureScript)
- `markdown-it` (14.1.0) - Core markdown parser
- `markdown-it-*` plugins - Footnotes, LaTeX, tables, etc.
- `react` and `react-dom` - For interactive examples
- `katex` - LaTeX rendering

## Architecture

### Data Flow

1. **Input**: Markdown string
2. **Platform Detection**: Choose implementation (JVM/ClojureScript)
3. **Tokenization**: Platform-specific markdown parsing
4. **AST Construction**: Build Clojure data structure via multimethod dispatch
5. **Post-processing**: Handle footnotes, TOC, custom tokenizers
6. **Output**: Return AST or transform to Hiccup

### Key Concepts

**AST Structure**: All nodes have `:type` and `:content` keys, with optional `:attrs` for metadata.

```clojure
{:type :doc
 :content [{:type :paragraph
            :content [{:type :text :text "Hello"}
                      {:type :strong
                       :content [{:type :text :text "world"}]}]}]}
```

**Extensibility**: Custom text tokenizers can be added via `:text-tokenizers` option:

```clojure
(parse {:text-tokenizers [hashtag-tokenizer internal-link-tokenizer]} 
       "Hello #world [[link]]")
```

**Zipper Integration**: Uses `clojure.zip` for AST navigation and transformation.

## Available Tools/APIs

### Primary Functions

```clojure
;; Basic parsing
(require '[nextjournal.markdown :as md])
(md/parse "# Hello **world**")
;; => {:type :doc, :content [...]}

;; Transform to Hiccup
(md/->hiccup "# Hello **world**")
;; => [:div [:h1 "Hello " [:strong "world"]]]

;; Custom transformation
(md/->hiccup custom-renderers markdown-ast)
```

### Customization

```clojure
;; Custom hiccup renderers
(assoc md/default-hiccup-renderers
  :heading (fn [ctx node] [:h1.custom-class ...])
  :text (fn [ctx node] [:span.custom-text (:text node)]))

;; Custom text tokenizers
{:regex #"#\w+"
 :handler (fn [ctx match] {:type :hashtag :tag (subs match 1)})}
```

### Utility Functions

```clojure
(require '[nextjournal.markdown.transform :as t])
(t/->text ast-node)  ; Extract plain text
(t/hydrate-toc doc)  ; Process table of contents

(require '[nextjournal.markdown.utils :as u])
(u/insert-sidenote-containers doc)  ; Convert footnotes to sidenotes
```

## Development Workflow

### Setup

```bash
# Install dependencies
bb yarn-install

# Start development server with auto-reload
bb dev

# Run tests
bb test                    # Clojure tests
bb test:cljs              # ClojureScript tests
```

### Build Tasks

```bash
# Build JavaScript module
bb build-esm

# Build jar
bb jar

# Install locally
bb install

# Generate documentation
bb quickdoc
```

### Testing

- **JVM**: Uses standard `clojure.test` with matcher-combinators
- **ClojureScript**: Browser and Node.js test environments via shadow-cljs
- **Multi-threading**: Dedicated tests for concurrent parsing safety

## Implementation Patterns

### Multimethod Dispatch

Platform-specific parsing uses multimethods based on token types:

```clojure
(defmulti apply-token (fn [ctx token] (.-type token)))
(defmethod apply-token "heading_open" [ctx token] ...)
(defmethod apply-token "text" [ctx token] ...)
```

### Zipper-based AST Construction

Document building uses zipper for efficient tree manipulation:

```clojure
(-> empty-doc
    (update-current-loc #(z/append-child % node))
    (update-current-loc z/down)
    (update-current-loc z/rightmost))
```

### Cross-platform Compatibility

Platform detection and feature abstraction:

```clojure
#?(:clj  (commonmark-based-parsing)
   :cljs (markdown-it-based-parsing))
```

## Extension Points

### Custom Text Tokenizers

Add custom parsing for special syntax within text nodes:

```clojure
{:regex #"\[\[([^\]]+)\]\]"
 :handler (fn [ctx match] 
            {:type :internal-link 
             :text (second match)})}
```

### Custom Hiccup Renderers

Override default rendering for any node type:

```clojure
{:heading (fn [ctx {:keys [heading-level attrs content]}]
            [heading-markup ...)
 :image   (fn [ctx {:keys [attrs]}]
            [:figure [:img attrs] [:figcaption ...]])}
```

### Post-processing Transformations

Add document-level transformations:

```clojure
(-> parsed-doc
    (transform/hydrate-toc)
    (utils/insert-sidenote-containers)
    (custom-transform))
```

### Parser Extensions

For ClojureScript, modify `src/js/markdown.js` to add markdown-it plugins.
For JVM, extend commonmark-java configuration in `impl.clj`.

## Version and Release

- Version managed via git commit count + offset in `bb.edn`
- Published to Clojars as `io.github.nextjournal/markdown`
- Release workflow: `bb publish` → git push with tag → CI deployment
- Current status: ALPHA (frequent breaking changes expected)

The library is designed as a building block for larger systems like [Clerk](https://github.com/nextjournal/clerk) where it enables rendering of "literate fragments" in computational notebooks.
