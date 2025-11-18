let MarkdownIt = require('markdown-it')
let texmath = require('./markdown-it-texmath.js')
let blockImage = require("markdown-it-block-image")
let mdToc = require("markdown-it-toc-done-right")
let footnotes = require("markdown-it-footnote")

function todoListPlugin(md, opts) {
  const startsWithTodoSequence = (text) => {
    return text.startsWith("[ ] ") || text.startsWith("[x] ")
  }
  const isITodoInlineToken = (tokens, i) => {
    return tokens[i].type   === 'inline' &&
           tokens[i-1].type === 'paragraph_open' &&
           tokens[i-2].type === 'list_item_open' &&
           startsWithTodoSequence(tokens[i].content)
  }
  const removeMarkup = (token) => {
    let textNode = token.children[0]
    textNode.content = textNode.content.slice(4)
  }
  const closestList = (tokens, index) => {
    for (let i = index; i >= 0; i--) {
      let token = tokens[i]
      if (token.type == 'bullet_list_open') { return token }
    }
  }
  const rule = (state) => {
    let tokens = state.tokens
    for (let i = 2; i < tokens.length; i++) {
      if (isITodoInlineToken(tokens, i)) {
        // set attrs on the list item
        tokens[i-2].attrSet("todo", true)
        tokens[i-2].attrSet("checked", tokens[i].content.startsWith("[x] "))
        // removes the [-] sequence from the first inline children
        removeMarkup(tokens[i])
        // set attrs on closest list container
        let container = closestList(tokens, i-3)
        if (container) { container.attrSet("has-todos", true) }
      }
    }
  }

  md.core.ruler.after('inline', 'todo-list-rule', rule)
}

function MD(opts) {
  var md = new MarkdownIt({html: true, linkify: true, breaks: false})
  if (!(opts.inline_formula_disabled || opts.block_formula_disabled)) {
    md.use(texmath, {delimiters: "dollars"})
  }
  md.use(blockImage)
  md.use(mdToc)
  md.use(footnotes)
  md.use(todoListPlugin)
  return md;
}

function tokenize(opts, text)  { return MD(opts).parse(text, {}) }

module.exports = {tokenize}
