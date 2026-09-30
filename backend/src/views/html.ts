const ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

/** 已跳脫、可安全嵌入的 HTML 片段 */
export class SafeHtml {
  constructor(readonly value: string) {}
  toString() {
    return this.value
  }
}

export function escapeHtml(value: unknown): string {
  return String(value).replace(/[&<>"']/g, (c) => ESCAPES[c]!)
}

/** Tagged template：插值自動跳脫，SafeHtml 與陣列（巢狀片段）原樣輸出 */
export function html(strings: TemplateStringsArray, ...values: unknown[]): SafeHtml {
  const render = (v: unknown): string => {
    if (v instanceof SafeHtml) return v.value
    if (Array.isArray(v)) return v.map(render).join('')
    if (v === null || v === undefined || v === false) return ''
    return escapeHtml(v)
  }
  return new SafeHtml(strings.reduce((out, str, i) => out + str + (i < values.length ? render(values[i]) : ''), ''))
}
