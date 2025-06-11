;; # Markdown parsing shared utils
(ns nextjournal.markdown.utils
  (:require [nextjournal.markdown.impl.utils :as u]
            [nextjournal.markdown.utils.emoji :as emoji]))

(def empty-doc
  "The empty doc"
  u/empty-doc)

(def normalize-tokenizer
  "Normalizes a map of regex and handler into a Tokenizer"
  u/normalize-tokenizer)

(def hashtag-tokenizer u/hashtag-tokenizer)

(def internal-link-tokenizer u/internal-link-tokenizer)

(def insert-sidenote-containers u/insert-sidenote-containers)

(def text-node u/text-node)

(def formula u/formula)

(def block-formula u/block-formula)

(def tokenize-text-node u/tokenize-text-node)

(def emoji-regex emoji/regex)
