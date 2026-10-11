import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

const DEFAULT = { schemaVersion: 1, backgroundToken: 'default', gridStyle: { pattern: 'lines', colorToken: 'auto' } }
const WARM_DOTS = { schemaVersion: 1, backgroundToken: '#204060', gridStyle: { pattern: 'dots', colorToken: 'auto' } }

describe.skipIf(!sql)('canvas style (batch C)', () => {
  let env: TestEnv
  let owner: string
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    owner = await createUser(env.app, 'Owner')
    api = client(env.app, owner)
  })
  afterAll(async () => {
    await env?.close()
  })

  const newWorkspace = async () => (await api.post('/workspaces', { title: 'Style' })).body.id as string
  const head = async (ws: string) => (await api.get(`/workspaces/${ws}`)).body.currentVersion as number
  const styleOp = (expected: number, canvasStyle: unknown = WARM_DOTS) => ({ kind: 'update_canvas_style', payload: { expectedCanvasStyleVersion: expected, canvasStyle } })
  const submit = async (ws: string, ops: any[], as = api) => as.post(`/workspaces/${ws}/operations`, envelope(await head(ws), ops))
  const state = async (ws: string) => (await api.get(`/workspaces/${ws}/state`)).body

  test('new workspaces start with the default style at version 0; capability declared', async () => {
    const ws = await newWorkspace()
    expect(await state(ws)).toMatchObject({ canvasStyle: DEFAULT, canvasStyleVersion: 0 })
    const caps = (await client(env.app).get('/capabilities')).body.canvasStyle
    expect(caps).toEqual({ supported: true, operationKinds: ['update_canvas_style'], styleSchemaVersions: [1], snapshotSchemaVersion: 2, defaultStyle: DEFAULT })
  })

  test('accepted update: CAS +1, committed payload carries the authoritative version; state / snapshot agree', async () => {
    const ws = await newWorkspace()
    const res = await submit(ws, [styleOp(0)])
    expect(res.body.status).toBe('accepted')
    expect(res.body.operations[0]).toMatchObject({
      operationType: 'update_canvas_style',
      payload: { expectedCanvasStyleVersion: 0, canvasStyle: WARM_DOTS, canvasStyleVersion: 1 },
    })
    expect(await state(ws)).toMatchObject({ canvasStyle: WARM_DOTS, canvasStyleVersion: 1, throughServerSeq: 1, workspaceVersion: 1 })
    const snap = await api.post(`/workspaces/${ws}/snapshots`)
    expect(snap.body).toMatchObject({ schemaVersion: 2, throughServerSeq: 1, snapshot: { canvasStyle: WARM_DOTS, canvasStyleVersion: 1 } })
  })

  test('stale expected version → 409 canvas_style_version_conflict with the authoritative style and checkpoint', async () => {
    const ws = await newWorkspace()
    await submit(ws, [styleOp(0)])
    const stale = await submit(ws, [styleOp(0, DEFAULT)])
    expect(stale.status).toBe(409)
    expect(stale.body).toMatchObject({
      status: 'conflict',
      code: 'canvas_style_version_conflict',
      conflicts: [],
      workspaceVersion: 1,
      lastServerSeq: 1,
      canvasStyle: { expectedVersion: 0, actualVersion: 1, canvasStyle: WARM_DOTS, canvasStyleVersion: 1 },
    })
    expect((await state(ws)).canvasStyleVersion).toBe(1)
  })

  test('two clients with the same expected version: exactly one accepted', async () => {
    const ws = await newWorkspace()
    const results = await Promise.all(
      ['#111111', '#222222', '#333333'].map((bg) => api.post(`/workspaces/${ws}/operations`, envelope(0, [styleOp(0, { ...DEFAULT, backgroundToken: bg })]))),
    )
    expect(results.filter((r) => r.body.status === 'accepted')).toHaveLength(1)
    expect(results.filter((r) => r.body.code === 'canvas_style_version_conflict')).toHaveLength(2)
    expect((await state(ws)).canvasStyleVersion).toBe(1)
  })

  test('strict tokens and schema; no expectedObjectVersions; viewer cannot write', async () => {
    const ws = await newWorkspace()
    const code = async (ops: any[]) => (await submit(ws, ops)).body.error?.code
    expect(await code([styleOp(0, { ...DEFAULT, backgroundToken: '#ABCDEF' })])).toBe('invalid_canvas_style')
    expect(await code([styleOp(0, { ...DEFAULT, backgroundToken: 'neon' })])).toBe('invalid_canvas_style')
    expect(await code([styleOp(0, { ...DEFAULT, gridStyle: { pattern: 'hex', colorToken: 'auto' } })])).toBe('invalid_canvas_style')
    expect(await code([styleOp(0, { ...DEFAULT, gridStyle: { pattern: 'dots', colorToken: '#12345' } })])).toBe('invalid_canvas_style')
    expect(await code([styleOp(0, { ...DEFAULT, schemaVersion: 2 })])).toBe('unsupported_canvas_style_schema')
    expect(await code([styleOp(0, { ...DEFAULT, alpha: 0.5 })])).toBe('invalid_operation')
    expect(await code([{ kind: 'update_canvas_style', payload: { canvasStyle: DEFAULT } }])).toBe('invalid_operation')
    expect(await code([{ ...styleOp(0), expectedObjectVersions: { [uuid()]: 1 } }])).toBe('invalid_expected_versions')
    expect((await state(ws)).canvasStyleVersion).toBe(0)

    const viewer = await createUser(env.app, 'Viewer')
    await api.put(`/workspaces/${ws}/members/${viewer}`, { role: 'viewer' })
    expect((await submit(ws, [styleOp(0)], client(env.app, viewer))).status).toBe(403)
  })

  test('atomic with graph operations: partial failure rolls everything back; success shares one workspaceVersion', async () => {
    const ws = await newWorkspace()
    const bad = await submit(ws, [styleOp(0), { kind: 'create_object', payload: { objectId: uuid(), objectType: 'future' } }])
    expect(bad.status).toBe(422)
    expect(await state(ws)).toMatchObject({ canvasStyleVersion: 0, canvasStyle: DEFAULT, workspaceVersion: 0 })

    const good = await submit(ws, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } }, styleOp(0), styleOp(1, DEFAULT)])
    expect(good.body.status).toBe('accepted')
    expect(good.body.operations.map((o: any) => o.workspaceVersion)).toEqual([1, 1, 1])
    expect(good.body.operations.slice(1).map((o: any) => o.payload.canvasStyleVersion)).toEqual([1, 2])
    expect(await state(ws)).toMatchObject({ canvasStyleVersion: 2, canvasStyle: DEFAULT })
  })

  test('duplicate does not advance; Undo is a new operation with a higher version; log replay equals state', async () => {
    const ws = await newWorkspace()
    const tx = envelope(0, [styleOp(0)])
    await api.post(`/workspaces/${ws}/operations`, tx)
    const dup = await api.post(`/workspaces/${ws}/operations`, tx)
    expect(dup.body).toMatchObject({ status: 'duplicate', toServerSeq: 1 })
    expect((await state(ws)).canvasStyleVersion).toBe(1)
    // Undo：after = 原 before，版本單調增加（不退回 0）
    await submit(ws, [styleOp(1, DEFAULT)])
    // Redo：再一次新 operation
    await submit(ws, [styleOp(2, WARM_DOTS)])
    const s = await state(ws)
    expect(s).toMatchObject({ canvasStyleVersion: 3, canvasStyle: WARM_DOTS })

    // 依正式 log 重播（與 APP reducer 相同規則）得到相同 style / version
    const log = (await api.get(`/workspaces/${ws}/operations?afterSeq=0`)).body.operations
    let replayed = { canvasStyle: DEFAULT, canvasStyleVersion: 0 }
    for (const op of log) {
      if (op.operationType !== 'update_canvas_style') continue
      expect(op.payload.expectedCanvasStyleVersion).toBe(replayed.canvasStyleVersion)
      replayed = { canvasStyle: op.payload.canvasStyle, canvasStyleVersion: op.payload.canvasStyleVersion }
    }
    expect(replayed).toEqual({ canvasStyle: s.canvasStyle, canvasStyleVersion: s.canvasStyleVersion })
  })

  test('schema 1 snapshots (before batch C) are served unchanged with their original checksum', async () => {
    const ws = await newWorkspace()
    const legacy = { workspaceId: ws, workspaceVersion: 0, throughServerSeq: 0, objects: [], relations: [] }
    await sql!`
      INSERT INTO workspace_snapshots (workspace_id, through_server_seq, schema_version, snapshot, checksum)
      VALUES (${ws}, 0, 1, ${sql!.json(legacy)}, 'sha256:legacy')
    `
    const latest = await api.get(`/workspaces/${ws}/snapshots/latest`)
    expect(latest.body).toMatchObject({ schemaVersion: 1, checksum: 'sha256:legacy', snapshot: legacy })
    expect(latest.body.snapshot.canvasStyle).toBeUndefined()
  })
})
