import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'
import { SAMPLE_VECTOR_PATH } from '../support/selection-samples.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

const GEO = 'boarderless.relation-geometry.v1'
const AUTO = { schema: GEO, route: { mode: 'auto' }, labelPlacement: { mode: 'auto' } }
const MANUAL = {
  schema: GEO,
  route: { mode: 'manual', waypoints: [{ x: 320, y: 180 }, { x: 520, y: 180 }] },
  labelPlacement: { mode: 'manual', pathFraction: 0.5, tangentOffset: 0, normalOffset: 24 },
}
const LOOP = { schema: GEO, route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } }

describe.skipIf(!sql)('relation geometry, self-loops, insert-node and vector paths (batch D)', () => {
  let env: TestEnv
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    api = client(env.app, await createUser(env.app, 'Owner'))
  })
  afterAll(async () => {
    await env?.close()
  })

  const newWorkspace = async () => (await api.post('/workspaces', { title: 'Graph' })).body.id as string
  const head = async (ws: string) => (await api.get(`/workspaces/${ws}`)).body.currentVersion as number
  const submit = async (ws: string, ops: any[]) => api.post(`/workspaces/${ws}/operations`, envelope(await head(ws), ops))
  const state = async (ws: string) => (await api.get(`/workspaces/${ws}/state`)).body
  const text = (id: string, extra: object = {}) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'text', ...extra } })
  const rel = (relationId: string, s: string, t: string, extra: object = {}) => ({
    kind: 'create_relation',
    payload: { relationId, sourceObjectId: s, targetObjectId: t, direction: 'forward', ...extra },
  })
  const relation = async (ws: string, id: string) => (await state(ws)).relations.find((r: any) => r.relationId === id)

  async function seed() {
    const ws = await newWorkspace()
    const [a, b] = [uuid(), uuid()]
    await submit(ws, [text(a), text(b)])
    return { ws, a, b }
  }

  test('capability declares geometry, self-loop, parallel edges, vector path', async () => {
    const caps = (await client(env.app).get('/capabilities')).body
    expect(caps.relationGeometry).toMatchObject({ supported: true, schema: GEO, selfLoop: true, parallelEdges: true, insertNodeTransaction: true, endpointRewire: false })
    expect(caps.vectorPath).toMatchObject({ supported: true, objectType: 'text', property: 'vectorPath', schemaVersions: [1] })
  })

  test('geometry round-trip: create → update (whole replacement) → delete → restore → log', async () => {
    const { ws, a, b } = await seed()
    const r = uuid()
    expect((await submit(ws, [rel(r, a, b, { label: 'go', style: { colorToken: 'relation', geometry: MANUAL } })])).body.status).toBe('accepted')
    expect((await relation(ws, r)).style).toEqual({ colorToken: 'relation', geometry: MANUAL })
    // 只送 geometry：整個子物件替換，不殘留舊 waypoints；colorToken 保留
    await submit(ws, [{ kind: 'update_relation', payload: { relationId: r, style: { geometry: AUTO } } }])
    expect((await relation(ws, r))).toMatchObject({ relationVersion: 2, style: { colorToken: 'relation', geometry: AUTO } })
    // 舊 client 只改 colorToken：不抹除 geometry
    await submit(ws, [{ kind: 'update_relation', payload: { relationId: r, style: { colorToken: '#123456' } } }])
    expect((await relation(ws, r)).style).toEqual({ colorToken: '#123456', geometry: AUTO })
    await submit(ws, [{ kind: 'delete_relations', payload: { relationIds: [r] } }])
    await submit(ws, [{ kind: 'restore_relations', payload: { relationIds: [r] } }])
    expect(await relation(ws, r)).toMatchObject({ relationVersion: 5, style: { geometry: AUTO } })
    const log = (await api.get(`/workspaces/${ws}/operations?afterSeq=0`)).body.operations
    expect(log.find((o: any) => o.operationType === 'create_relation').payload.style.geometry).toEqual(MANUAL)
  })

  test('self-loops need explicit auto / loop geometry; cascade deletes them exactly once', async () => {
    const { ws, a } = await seed()
    const code = async (ops: any[]) => (await submit(ws, ops)).body.error?.code
    expect(await code([rel(uuid(), a, a)])).toBe('invalid_relation_geometry')
    expect(await code([rel(uuid(), a, a, { style: { geometry: MANUAL } })])).toBe('invalid_relation_geometry')
    const loop = uuid()
    const autoLoop = uuid()
    expect((await submit(ws, [rel(loop, a, a, { direction: 'none', style: { geometry: LOOP } }), rel(autoLoop, a, a, { style: { geometry: AUTO } })])).body.status).toBe('accepted')
    // 自循環不能改成 manual
    expect(await code([{ kind: 'update_relation', payload: { relationId: loop, style: { geometry: MANUAL } } }])).toBe('invalid_relation_geometry')
    const del = await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [a] } }])
    expect(del.body.operations[0].payload.cascadedRelationIds.sort()).toEqual([loop, autoLoop].sort())
    const restored = await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [a] } }, { kind: 'restore_relations', payload: { relationIds: [loop, autoLoop] } }])
    expect(restored.body.operations[1].payload.restoredVersions).toEqual({ [loop]: 3, [autoLoop]: 3 })
  })

  test('parallel relations are distinct identities', async () => {
    const { ws, a, b } = await seed()
    const [r1, r2, r3] = [uuid(), uuid(), uuid()]
    expect((await submit(ws, [rel(r1, a, b), rel(r2, a, b, { intent: 'x' }), rel(r3, b, a)])).body.status).toBe('accepted')
    expect((await state(ws)).relations.map((r: any) => r.relationId).sort()).toEqual([r1, r2, r3].sort())
  })

  test('invalid geometry is rejected without partial commits', async () => {
    const { ws, a, b } = await seed()
    const bad = async (geometry: unknown) => (await submit(ws, [text(uuid()), rel(uuid(), a, b, { style: { geometry } })])).body.error.code
    expect(await bad({ ...AUTO, schema: 'boarderless.relation-geometry.v2' })).toBe('unsupported_geometry_schema')
    expect(await bad(null)).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, route: { mode: 'manual', waypoints: Array.from({ length: 33 }, () => ({ x: 0, y: 0 })) } })).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, route: { mode: 'manual', waypoints: [] } })).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, route: { mode: 'manual', waypoints: [{ x: 1e8, y: 0 }] } })).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, labelPlacement: { mode: 'manual', pathFraction: 2, tangentOffset: 0, normalOffset: 0 } })).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, labelPlacement: { mode: 'manual', pathFraction: 0.5, tangentOffset: 0, normalOffset: 5000 } })).toBe('invalid_relation_geometry')
    expect(await bad({ ...MANUAL, labelPlacement: { mode: 'manual', pathFraction: 0.5 } })).toBe('invalid_relation_geometry') // 必須完整四欄
    expect(await bad({ ...LOOP })).toBe('invalid_relation_geometry') // 普通線不接受 loop
    expect(await bad({ ...AUTO, route: { mode: 'auto', waypoints: [] } })).toBe('invalid_relation_geometry')
    expect(await bad({ schema: GEO, route: { mode: 'auto' } })).toBe('invalid_relation_geometry')
    expect((await state(ws)).objects).toHaveLength(2)
  })

  describe('insert node on a relation (single transaction recipe)', () => {
    async function insert(ws: string, r: any, n: string, segments: [string, string, object][]) {
      return submit(ws, [
        { kind: 'delete_relations', payload: { relationIds: [r.relationId] }, expectedObjectVersions: { [r.relationId]: r.relationVersion } },
        text(n),
        ...segments.map(([s, t, extra]) => rel(uuid(), s, t, extra)),
      ])
    }

    test('forward / backward / both / none / self-loop splits; Undo and Redo restore exact identities', async () => {
      const { ws, a, b } = await seed()
      const cases: [string, string, string, object, (n: string) => [string, string, object][]][] = [
        ['forward', a, b, { label: 'call', intent: 'flow' }, (n) => [[a, n, { label: 'call', intent: 'flow' }], [n, b, {}]]],
        ['backward', a, b, {}, (n) => [[b, n, {}], [n, a, {}]]],
        ['both', a, b, {}, (n) => [[a, n, { direction: 'both' }], [n, b, { direction: 'both' }]]],
        ['none', a, b, {}, (n) => [[a, n, { direction: 'none' }], [n, b, { direction: 'none' }]]],
      ]
      for (const [direction, s, t, extra, split] of cases) {
        const r = uuid()
        await submit(ws, [rel(r, s, t, { direction, ...extra, style: { geometry: MANUAL } })])
        const original = await relation(ws, r)
        const n = uuid()
        const res = await insert(ws, original, n, split(n))
        expect({ direction, status: res.body.status }).toEqual({ direction, status: 'accepted' })
        expect(new Set(res.body.operations.map((o: any) => o.workspaceVersion)).size).toBe(1)
        expect(await relation(ws, r)).toBeUndefined()
        const [r1, r2] = res.body.operations.slice(2).map((o: any) => o.payload.relationId)

        // Undo：刪新線與 N，restore 原 R（原 geometry 保留）
        const undo = await submit(ws, [
          { kind: 'delete_relations', payload: { relationIds: [r1, r2] } },
          { kind: 'delete_objects', payload: { objectIds: [n] }, expectedObjectVersions: { [n]: 1 } },
          { kind: 'restore_relations', payload: { relationIds: [r] }, expectedObjectVersions: { [r]: original.relationVersion + 1 } },
        ])
        expect(undo.body.status).toBe('accepted')
        expect(await relation(ws, r)).toMatchObject({ direction, style: { geometry: MANUAL }, relationVersion: original.relationVersion + 2 })
        // Redo：刪 R、restore N、restore R1 / R2（同 ID）
        const redo = await submit(ws, [
          { kind: 'delete_relations', payload: { relationIds: [r] } },
          { kind: 'restore_objects', payload: { objectIds: [n] } },
          { kind: 'restore_relations', payload: { relationIds: [r1, r2] } },
        ])
        expect(redo.body.status).toBe('accepted')
        const ids = (await state(ws)).relations.map((x: any) => x.relationId)
        expect(ids).toContain(r1)
        expect(ids).toContain(r2)
      }

      // 自循環 A→A：拆成兩條普通線 A→N→A（新段 auto，不沿用 loop）
      const loop = uuid()
      await submit(ws, [rel(loop, a, a, { style: { geometry: LOOP } })])
      const n = uuid()
      const res = await insert(ws, await relation(ws, loop), n, [[a, n, {}], [n, a, {}]])
      expect(res.body.status).toBe('accepted')
    })

    test('stale relation version → conflict; concurrently deleted endpoint → whole transaction rejected (no orphan N)', async () => {
      const { ws, a, b } = await seed()
      const r = uuid()
      await submit(ws, [rel(r, a, b)])
      const original = await relation(ws, r)
      const [n1, n2] = [uuid(), uuid()]
      const results = await Promise.all([insert(ws, original, n1, [[a, n1, {}], [n1, b, {}]]), insert(ws, original, n2, [[a, n2, {}], [n2, b, {}]])])
      const statuses = results.map((x) => x.body.status ?? x.body.error?.code).sort()
      expect(statuses).toContain('accepted')
      expect(statuses.filter((s) => s === 'accepted')).toHaveLength(1)

      const r2 = uuid()
      await submit(ws, [rel(r2, a, b)])
      const before = await relation(ws, r2)
      await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [b] } }]) // 端點被刪（cascade 刪 r2）
      const n3 = uuid()
      const late = await insert(ws, before, n3, [[a, n3, {}], [n3, b, {}]])
      expect(late.status).toBe(409)
      expect((await state(ws)).objects.map((o: any) => o.objectId)).not.toContain(n3)
    })
  })

  test('vectorPath and textPlacement on text nodes: round-trip, clear with null, strict rules', async () => {
    const ws = await newWorkspace()
    const n = uuid()
    const placement = { anchor: 'Below', offsetX: 0, offsetY: 8, appearance: { fontSize: 18, weight: 700, color: '#112233', font: 'Serif', italic: true, underline: false, strikethrough: false } }
    expect((await submit(ws, [text(n, { properties: { text: '', vectorPath: SAMPLE_VECTOR_PATH, textPlacement: placement } })])).body.status).toBe('accepted')
    const obj = (await state(ws)).objects.find((o: any) => o.objectId === n)
    expect(obj.properties).toEqual({ text: '', vectorPath: SAMPLE_VECTOR_PATH, textPlacement: placement })

    const bad = async (properties: object, transform?: object) => (await submit(ws, [text(uuid(), { properties, ...(transform ? { transform } : {}) })])).body.error
    const vp = (patch: object) => ({ vectorPath: { ...SAMPLE_VECTOR_PATH, ...patch } })
    expect((await bad(vp({ commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'move', point: { x: 1, y: 1 } }] }))).code).toBe('invalid_object')
    expect((await bad(vp({ commands: [{ type: 'line', point: { x: 0, y: 0 } }, { type: 'close' }] }))).code).toBe('invalid_object')
    expect((await bad(vp({ schemaVersion: 2 }))).code).toBe('invalid_object')
    expect((await bad(vp({ viewBox: { width: 0, height: 1 } }))).code).toBe('invalid_object')
    expect((await bad(vp({ commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'svg', d: 'M0 0 L1 1' }] }))).code).toBe('invalid_object')
    const { style: _style, ...noStyle } = SAMPLE_VECTOR_PATH as any
    expect((await bad({ vectorPath: noStyle })).code).toBe('invalid_object') // server 寫入格式欄位完整
    expect((await bad(vp({}), { x: 0, y: 0, width: 0, height: 10, rotationDegrees: 0 })).details[0].path).toBe('/transform')
    expect((await bad({ textPlacement: { ...placement, appearance: { ...placement.appearance, weight: 950 } } })).code).toBe('invalid_object')

    // 清除：null（properties 為淺層 merge）
    await submit(ws, [{ kind: 'update_object', payload: { objectId: n, properties: { vectorPath: null, textPlacement: null } } }])
    expect((await state(ws)).objects.find((o: any) => o.objectId === n).properties).toMatchObject({ vectorPath: null, textPlacement: null })
  })
})
