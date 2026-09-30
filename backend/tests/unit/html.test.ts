import { expect, test } from 'bun:test'
import { html } from '../../src/views/html.ts'

test('html escapes interpolated values but keeps nested fragments', () => {
  const inner = html`<b>${'<x>'}</b>`
  const out = html`<p title="${'"q"'}">${inner}${['a', html`<i>b</i>`]}</p>`.value
  expect(out).toBe('<p title="&quot;q&quot;"><b>&lt;x&gt;</b>a<i>b</i></p>')
})
