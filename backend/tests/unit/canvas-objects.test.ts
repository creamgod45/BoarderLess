import { describe, expect, test } from 'bun:test'
import { completeTransform, DEFAULT_TRANSFORM, NODE_SHAPES, validateObjectState } from '../../src/domain/canvas-objects.ts'

const t = DEFAULT_TRANSFORM
const id = () => crypto.randomUUID()

describe('validateObjectState', () => {
  test('accepts every APP node shape and color form', () => {
    for (const shapeToken of NODE_SHAPES) {
      expect(validateObjectState('text', t, { text: 'x', shapeToken }).ok).toBe(true)
    }
    for (const colorToken of ['paper', 'lilac', 'amber', 'mint', '#00ff7a', '#ABCDEF']) {
      expect(validateObjectState('text', t, { colorToken }).ok).toBe(true)
    }
    expect(validateObjectState('group', t, { title: 'G', colorToken: 'group' }).ok).toBe(true)
  })

  test('rejects wrong types, unknown tokens, explicit null and extra keys', () => {
    const bad: [string, unknown, unknown][] = [
      ['text', { ...t, x: '1' }, {}],
      ['text', { ...t, width: -0.5 }, {}],
      ['text', { ...t, rotationDegrees: Number.NaN }, {}],
      ['text', t, { text: 1 }],
      ['text', t, { colorToken: null }],
      ['text', t, { colorToken: '#fff' }],
      ['text', t, { shapeToken: 'Rounded' }],
      ['group', t, { text: 'x' }],
      ['media', t, { mediaKind: 'image' }],
      ['media', t, { assetId: id(), mediaKind: 'audio' }],
    ]
    for (const [type, transform, properties] of bad) {
      expect(validateObjectState(type, transform, properties).ok).toBe(false)
    }
  })

  test('media allows clearing the thumbnail with explicit null', () => {
    expect(validateObjectState('media', t, { assetId: id(), mediaKind: 'video', altText: '', thumbnailAssetId: null }).ok).toBe(true)
  })

  test('unknown types report unsupported_object_type', () => {
    expect(validateObjectState('future_widget', t, {})).toMatchObject({ ok: false, code: 'unsupported_object_type' })
  })
})

test('completeTransform fills only absent fields', () => {
  expect(completeTransform({ x: 5 })).toEqual({ ...DEFAULT_TRANSFORM, x: 5 })
  expect(completeTransform({ width: null })).toMatchObject({ width: null }) // explicit null 不被預設值掩蓋
})
