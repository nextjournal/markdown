// GFM strikethrough: one or two tildes delimit, three or more are text.
const SINGLE = 0x7E
const DOUBLE = 0x7E + 0x10000 // not a char code: a separate marker keeps balance_pairs from pairing ~ with ~~

function tokenizer(requireTwoTildes) {
  return function (state, silent) {
    if (silent || state.src.charCodeAt(state.pos) !== 0x7E) { return false }
    const scanned = state.scanDelims(state.pos, true)
    const len = scanned.length
    const token = state.push('text', '', 0)
    token.content = '~'.repeat(len)
    if (len === 2 || (len === 1 && !requireTwoTildes)) {
      state.delimiters.push({
        marker: len === 1 ? SINGLE : DOUBLE,
        length: 0,
        token: state.tokens.length - 1,
        end: -1,
        open: scanned.can_open,
        close: scanned.can_close
      })
    }
    state.pos += len
    return true
  }
}

function postProcessDelimiters(state, delimiters) {
  for (const delim of delimiters) {
    if ((delim.marker !== SINGLE && delim.marker !== DOUBLE) || delim.end === -1) { continue }
    const open = state.tokens[delim.token]
    const close = state.tokens[delimiters[delim.end].token]
    Object.assign(open, {type: 's_open', tag: 's', nesting: 1, markup: open.content, content: ''})
    Object.assign(close, {type: 's_close', tag: 's', nesting: -1, markup: close.content, content: ''})
  }
}

function postProcess(state) {
  postProcessDelimiters(state, state.delimiters)
  for (const meta of state.tokens_meta) {
    if (meta && meta.delimiters) { postProcessDelimiters(state, meta.delimiters) }
  }
}

module.exports = function (md, opts) {
  md.inline.ruler.at('strikethrough', tokenizer(opts && opts.requireTwoTildes))
  md.inline.ruler2.at('strikethrough', postProcess)
}
