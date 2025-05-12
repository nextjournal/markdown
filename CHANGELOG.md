# Changelog

## Unreleased

* Hiccup JVM compatibiltiy for fragments (see [#34](https://github.com/nextjournal/markdown/issues/34))

## 0.6.157

* Swap out GraalJS ([#28](https://github.com/nextjournal/markdown/issues/28)) in favour of [commonmark-java](https://github.com/markdown-it/markdown-it) on the JVM side.
  This makes the library compatible with Java 22 and yields an approximate speedup of 10x. The clojurescript implementation stays the same.
* Comply with commonmark rendering of images by default (see [#18](https://github.com/nextjournal/markdown/issues/18)).

## 0.5.148

* Fixes a bug in the construction of the table of contents ([#19](https://github.com/nextjournal/markdown/issues/19)).

## 0.5.146
* Fix graaljs multithreaded access ([#17](https://github.com/nextjournal/markdown/issues/17))

## 0.5.144
* Disable parsing hashtags and internal links by default ([#14](https://github.com/nextjournal/markdown/issues/14))
* Allow conditional application of custom tokenizers depending on document state around the text location
* Arity 2 to `nextjournal.markdown/parse` was added to customize parsing options (e.g. custom tokenizers) more conveniently.
* Support hard-breaks
* Fix conversion to hiccup for tables with empty cells ([#13](https://github.com/nextjournal/markdown/issues/13))

## 0.4.138
* Uses the official markdown-it/footnote plugin 
* Adds optional (post-parse) handling of footnotes as sidenotes

## 0.4.135
* node-to-text transformation interprets softbreaks as spaces

## 0.4.132
* Extract and assign leading emoji from heading nodes

## 0.4.130
* Produce unique ids in attrs for header nodes
* Drop lambdaisland.uri dependency

## 0.4.126
* Add `deps.cljs` to classpath

## 0.4.123
* downgrade GraalJS to keep Java 8 compatibility

## 0.4.116
* Bump data.json

## 0.4.112
* Distinguish between tight and loose lists

## 0.4.109
* [More work on parsing extensibility](https://snapshots.nextjournal.com/markdown/build/7f5c1e24aeb3842235bc6175aa55dbd9a96d25d1/index.html#/notebooks/parsing_extensibility.clj)
* A new home: https://github.com/nextjournal/markdown

## 0.3.69
* Extensible parsing of leaf text nodes

## 0.2.44
* Simplified `:toc` structure.

## 0.1.37
* First Release.
