/**
 * 嚴格 JSON parser（不可信輸入）：與 APP `parseStrictJsonObject` 同等級的拒絕條件。
 * - 重複 key（以 unescape 後的值比較）
 * - 未配對的 UTF-16 surrogate（含 `\ud800` 這類跳脫）
 * - 超過深度上限、非有限數字、尾端多餘內容
 * `JSON.parse` 對重複 key 是後者覆蓋、對孤立 surrogate 照收，因此不能直接使用。
 */
export class StrictJsonError extends Error {
  constructor(
    readonly reason: 'syntax' | 'duplicate_key' | 'invalid_unicode' | 'depth' | 'number',
    message: string,
  ) {
    super(message)
    this.name = 'StrictJsonError'
  }
}

export function parseStrictJson(text: string, maxDepth = 64): unknown {
  let i = 0

  const fail = (reason: StrictJsonError['reason'], message: string): never => {
    throw new StrictJsonError(reason, `${message} at position ${i}`)
  }
  const ws = () => {
    while (i < text.length) {
      const c = text.charCodeAt(i)
      if (c === 0x20 || c === 0x09 || c === 0x0a || c === 0x0d) i++
      else break
    }
  }

  const parseString = (): string => {
    i++ // opening quote
    let out = ''
    let start = i
    while (true) {
      if (i >= text.length) fail('syntax', 'Unterminated string')
      const c = text.charCodeAt(i)
      if (c === 0x22) {
        out += text.slice(start, i)
        i++
        break
      }
      if (c < 0x20) fail('syntax', 'Control character in string')
      if (c === 0x5c) {
        out += text.slice(start, i)
        const e = text[i + 1]
        i += 2
        switch (e) {
          case '"': out += '"'; break
          case '\\': out += '\\'; break
          case '/': out += '/'; break
          case 'b': out += '\b'; break
          case 'f': out += '\f'; break
          case 'n': out += '\n'; break
          case 'r': out += '\r'; break
          case 't': out += '\t'; break
          case 'u': {
            const hex = text.slice(i, i + 4)
            if (!/^[0-9a-fA-F]{4}$/.test(hex)) fail('syntax', 'Invalid unicode escape')
            out += String.fromCharCode(Number.parseInt(hex, 16))
            i += 4
            break
          }
          default:
            fail('syntax', 'Invalid escape')
        }
        start = i
        continue
      }
      i++
    }
    if (!out.isWellFormed()) fail('invalid_unicode', 'Unpaired surrogate')
    return out
  }

  const parseNumber = (): number => {
    const match = /^-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?/.exec(text.slice(i, i + 400))
    if (!match) fail('syntax', 'Invalid number')
    i += match![0].length
    const value = Number(match![0])
    if (!Number.isFinite(value)) fail('number', 'Number out of range')
    return value
  }

  const parseValue = (depth: number): unknown => {
    ws()
    const c = text[i]
    if (c === '{') {
      if (depth >= maxDepth) fail('depth', 'Nesting too deep')
      i++
      const obj: Record<string, unknown> = {}
      ws()
      if (text[i] === '}') {
        i++
        return obj
      }
      while (true) {
        ws()
        if (text[i] !== '"') fail('syntax', 'Expected key')
        const key = parseString()
        if (Object.hasOwn(obj, key)) fail('duplicate_key', 'Duplicate key')
        ws()
        if (text[i] !== ':') fail('syntax', 'Expected colon')
        i++
        // __proto__ 等 key 以 defineProperty 寫入，不污染 prototype
        Object.defineProperty(obj, key, { value: parseValue(depth + 1), enumerable: true, writable: true, configurable: true })
        ws()
        if (text[i] === ',') {
          i++
          continue
        }
        if (text[i] === '}') {
          i++
          return obj
        }
        fail('syntax', 'Expected comma or }')
      }
    }
    if (c === '[') {
      if (depth >= maxDepth) fail('depth', 'Nesting too deep')
      i++
      const arr: unknown[] = []
      ws()
      if (text[i] === ']') {
        i++
        return arr
      }
      while (true) {
        arr.push(parseValue(depth + 1))
        ws()
        if (text[i] === ',') {
          i++
          continue
        }
        if (text[i] === ']') {
          i++
          return arr
        }
        fail('syntax', 'Expected comma or ]')
      }
    }
    if (c === '"') return parseString()
    if (c === '-' || (c !== undefined && c >= '0' && c <= '9')) return parseNumber()
    if (text.startsWith('true', i)) {
      i += 4
      return true
    }
    if (text.startsWith('false', i)) {
      i += 5
      return false
    }
    if (text.startsWith('null', i)) {
      i += 4
      return null
    }
    return fail('syntax', 'Unexpected token')
  }

  const value = parseValue(0)
  ws()
  if (i !== text.length) fail('syntax', 'Trailing content')
  return value
}
