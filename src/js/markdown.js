let MarkdownIt = require('markdown-it'),
    MD = new MarkdownIt({html: true, linkify: true, breaks: false})

let texmath = require('markdown-it-texmath')
MD.use(texmath, {delimiters: "dollars"})

let blockImage = require("markdown-it-block-image")
MD.use(blockImage)

let mdToc = require("markdown-it-toc-done-right")
MD.use(mdToc)

let sidenotes = require("markdown-it-sidenote")
MD.use(sidenotes)

// TODO: move to its own requirable (local) package or file
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

MD.use(todoListPlugin)

function parse(text)  { return MD.parse(text, {}) }
function parseJ(text) { return JSON.stringify(MD.parse(text, {})) }

module.exports = {parseJ: parseJ, parse: parse}
