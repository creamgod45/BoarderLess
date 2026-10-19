import { describe, expect, test } from 'bun:test'
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { NODE_SHAPES } from '../../src/domain/node-shapes.ts'
import { CapabilitiesController } from '../../src/controllers/capabilities.controller.ts'

/** 原有 25 種：不得改名、改順序（舊資料與舊 APP 依賴）。 */
const ORIGINAL_25 = [
  'rounded', 'rectangle', 'ellipse', 'diamond', 'pill', 'parallelogram', 'hexagon', 'document', 'database',
  'plain-text', 'triangle', 'pentagon', 'octagon', 'trapezoid', 'plus', 'arrow-right', 'arrow-left', 'arrow-up',
  'arrow-down', 'triangle-down', 'right-triangle', 'chevron', 'double-arrow', 'star', 'manual-input',
]

describe('NODE_SHAPES', () => {
  test('keeps the original 25 tokens first, in order, then 24 new ones', () => {
    expect([...NODE_SHAPES].slice(0, 25) as string[]).toEqual(ORIGINAL_25)
    expect(NODE_SHAPES.length).toBe(49)
  })

  test('tokens are unique lowercase kebab-case', () => {
    expect(new Set(NODE_SHAPES).size).toBe(NODE_SHAPES.length)
    for (const token of NODE_SHAPES) expect(token).toMatch(/^[a-z]+(-[a-z]+)*$/)
  })

  test('the contract fixture lists exactly NODE_SHAPES in order (the APP compares against it)', async () => {
    const fixture = JSON.parse(await readFile(join(import.meta.dirname, '../fixtures/contract/node-shapes.json'), 'utf8'))
    expect(fixture.shapes).toEqual([...NODE_SHAPES])
    expect(fixture.count).toBe(NODE_SHAPES.length)
  })

  test('capabilities announce the accepted shapes', async () => {
    const caps = await new CapabilitiesController({ realtime: false }).get()
    expect(caps.nodeShapes).toEqual([...NODE_SHAPES])
  })
})
