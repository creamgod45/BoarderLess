import { describe, expect, test } from 'bun:test'
import { validateSelectionPayload } from '../../src/domain/selection.ts'
import { parseStrictJson } from '../../src/utils/strict-json.ts'
import { SAMPLE_VECTOR_PATH, selectionV1Legacy, selectionV4, selectionV7, TEXT_ONLY_SAMPLES } from '../support/selection-samples.ts'

const clone = <T>(v: T): T => structuredClone(v)
const issue = (payload: unknown) => validateSelectionPayload(payload).issue ?? 'ok'
const media = { image: '11111111-1111-4111-8111-111111111111', video: '22222222-2222-4222-8222-222222222222', videoThumbnail: '33333333-3333-4333-8333-333333333333' }

describe('selection payload v1–v9', () => {
  test('every version sample is valid', () => {
    for (const [version, make] of Object.entries(TEXT_ONLY_SAMPLES)) expect({ version, result: validateSelectionPayload(make()) }).toEqual({ version, result: { ok: true } })
    expect(validateSelectionPayload(selectionV4(media))).toEqual({ ok: true })
    expect(validateSelectionPayload(selectionV1Legacy())).toEqual({ ok: true })
  })

  test('structure: unknown keys, wrong primitives, explicit null on non-nullable fields', () => {
    const p = TEXT_ONLY_SAMPLES[1]!() as any
    expect(issue({ ...p, extra: 1 })).toBe('Malformed')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], x: '0' }] })).toBe('Malformed')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], locked: null }] })).toBe('Malformed')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], zOffset: 1.5 }] })).toBe('Malformed')
    const { nodes: _nodes, ...noNodes } = p
    expect(issue(noNodes)).toBe('Malformed') // nodes 沒有預設值
    expect(issue({ ...p, version: 1.5 })).toBe('Malformed')
  })

  test('APP issue codes', () => {
    const p = TEXT_ONLY_SAMPLES[2]!() as any
    expect(issue({ ...p, format: 'other' })).toBe('UnsupportedFormat')
    expect(issue({ ...p, version: 10 })).toBe('UnsupportedVersion')
    expect(issue({ ...p, nodes: [], groups: [], relations: [] })).toBe('Empty')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], originalId: ' ' }] })).toBe('InvalidObjectId')
    expect(issue({ ...p, nodes: [p.nodes[0], { ...p.nodes[1], originalId: 'n1' }] })).toBe('DuplicateObjectId')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], width: 0 }] })).toBe('InvalidTransform')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], x: 1_000_001 }] })).toBe('InvalidTransform')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], shapeToken: 'blob' }] })).toBe('UnsupportedShape')
    expect(issue({ ...p, nodes: [{ ...p.nodes[0], parentOriginalId: 'missing' }] })).toBe('InvalidParent')
    expect(issue({ ...p, groups: [{ ...p.groups[0], parentOriginalId: 'g2' }, p.groups[1]] })).toBe('ParentCycle')
    expect(issue({ ...p, relations: [{ ...p.relations[0], direction: 'Sideways' }] })).toBe('InvalidRelation')
    expect(issue({ ...p, relations: [{ ...p.relations[0], targetId: 'g1' }] })).toBe('InvalidRelation') // group 不是端點
    expect(issue({ ...p, nodes: Array.from({ length: 1001 }, (_, i) => ({ ...p.nodes[1], originalId: `x${i}`, parentOriginalId: null })) })).toBe('Limit')
  })

  test('version gates', () => {
    const v4 = selectionV4(media) as any
    expect(issue({ ...v4, relations: [{ sourceId: 'n1', targetId: 'n1', direction: 'Forward', intent: null, label: null, geometry: null }] })).toBe('InvalidRelation')
    const v5 = TEXT_ONLY_SAMPLES[5]!() as any
    expect(issue({ ...v5, version: 4 })).toBe('InvalidRelation') // geometry 需要 v5
    const v6 = TEXT_ONLY_SAMPLES[6]!() as any
    expect(issue({ ...v6, version: 5 })).toBe('UnsupportedVersion') // vectorPath 需要 v6
    expect(issue({ ...selectionV7(), version: 6 })).toBe('UnsupportedVersion')
    expect(issue({ ...TEXT_ONLY_SAMPLES[8]!(), version: 7 })).toBe('UnsupportedVersion')
    expect(issue({ ...TEXT_ONLY_SAMPLES[9]!(), version: 8 })).toBe('UnsupportedVersion')
  })

  test('relation geometry rules', () => {
    const v5 = TEXT_ONLY_SAMPLES[5]!() as any
    const manualSelf = clone(v5)
    manualSelf.relations[1].geometry.route = { mode: 'manual', waypoints: [{ x: 1, y: 1 }] }
    expect(issue(manualSelf)).toBe('InvalidRelation')
    const loopNormal = clone(v5)
    loopNormal.relations[0].geometry.route = { mode: 'loop', side: 'top', extent: 40 }
    expect(issue(loopNormal)).toBe('InvalidRelation')
    const extent = clone(v5)
    extent.relations[1].geometry.route.extent = 8
    expect(issue(extent)).toBe('InvalidRelation')
    const fraction = clone(v5)
    fraction.relations[0].geometry.labelPlacement.pathFraction = 1.5
    expect(issue(fraction)).toBe('InvalidRelation')
    const waypoints = clone(v5)
    waypoints.relations[0].geometry.route.waypoints = Array.from({ length: 33 }, () => ({ x: 0, y: 0 }))
    expect(issue(waypoints)).toBe('Malformed')
  })

  test('vector path and text placement init rules', () => {
    const v6 = TEXT_ONLY_SAMPLES[6]!() as any
    const set = (vp: unknown) => ({ ...v6, nodes: [{ ...v6.nodes[0], vectorPath: vp }, v6.nodes[1]] })
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, commands: [{ type: 'line', point: { x: 0, y: 0 } }, { type: 'close' }] }))).toBe('InvalidVectorPath')
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'move', point: { x: 1, y: 1 } }] }))).toBe('InvalidVectorPath')
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, viewBox: { width: 0, height: 10 } }))).toBe('InvalidVectorPath')
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, style: { fillColorToken: null, strokeColorToken: null } }))).toBe('InvalidVectorPath')
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, schemaVersion: 2 }))).toBe('InvalidVectorPath')
    expect(issue(set({ ...SAMPLE_VECTOR_PATH, commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'svg', d: 'M0 0' }] }))).toBe('Malformed')
    const v8 = TEXT_ONLY_SAMPLES[8]!() as any
    const tp = clone(v8)
    tp.nodes[0].textPlacement.appearance.weight = 950
    expect(issue(tp)).toBe('InvalidTextPlacement')
    tp.nodes[0].textPlacement.appearance.weight = 700
    tp.nodes[0].textPlacement.appearance.color = 'red'
    expect(issue(tp)).toBe('InvalidTextPlacement')
  })

  test('sequence diagram bindings', () => {
    const ok = selectionV7() as any
    const mutate = (fn: (p: any) => void) => {
      const p = clone(ok)
      fn(p)
      return issue(p)
    }
    expect(mutate((p) => (p.nodes[0].text = 'Renamed'))).toBe('InvalidSequence') // label 必須等於節點文字
    expect(mutate((p) => (p.relations[0].direction = 'Both'))).toBe('InvalidSequence')
    expect(mutate((p) => (p.relations[2].geometry = null))).toBe('InvalidRelation') // 自循環需要 geometry
    expect(mutate((p) => (p.sequenceDiagrams[0].messages[1].message.dashed = false))).toBe('InvalidSequence') // Return 必須 dashed
    expect(mutate((p) => (p.sequenceDiagrams[0].messages[1].position.order = 0))).toBe('InvalidSequence') // order 重複
    expect(mutate((p) => (p.sequenceDiagrams[0].participants[0].participant.id = 'end'))).toBe('InvalidSequence') // 保留字
    expect(mutate((p) => (p.sequenceDiagrams[0].participants[0].participant.label = 'a<b'))).toBe('InvalidSequence')
    expect(mutate((p) => (p.sequenceDiagrams[0].blocks[0].kind = 'Alt'))).toBe('InvalidSequence') // Alt 需要 2..16 branches
    expect(mutate((p) => (p.nodes[0].parentOriginalId = null))).toBe('InvalidSequence') // participant 必須在 container 內
    expect(mutate((p) => delete p.relations[0].originalId)).toBe('InvalidSequence')
    expect(mutate((p) => (p.sequenceDiagrams[0].schema = 'boarderless.sequence-canvas.v2'))).toBe('InvalidSequence')
  })
})

describe('strict JSON', () => {
  test('rejects duplicate keys, lone surrogates, deep nesting, trailing content', () => {
    expect(() => parseStrictJson('{"a":1,"a":2}')).toThrow('Duplicate key')
    expect(() => parseStrictJson('{"a":1,"\\u0061":2}')).toThrow('Duplicate key')
    expect(() => parseStrictJson('{"a":"\\ud800"}')).toThrow('Unpaired surrogate')
    expect(() => parseStrictJson('[' .repeat(70) + ']'.repeat(70))).toThrow('Nesting too deep')
    expect(() => parseStrictJson('{} x')).toThrow('Trailing content')
    expect(() => parseStrictJson('1e999')).toThrow('Number out of range')
    expect(parseStrictJson('{"a":"\\ud83d\\ude00","b":[1,2.5,-3e2,true,null]}')).toEqual({ a: '😀', b: [1, 2.5, -300, true, null] })
    const proto = parseStrictJson('{"__proto__":{"x":1}}') as any
    expect(Object.getPrototypeOf(proto)).toBe(Object.prototype)
    expect(proto.__proto__).toEqual({ x: 1 })
  })
})
