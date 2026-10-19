import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

const LOOP = { schema: 'boarderless.relation-geometry.v1', route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } }

/** 一份時序圖：container + 兩個 participant + loop block（含 self-message）+ 三個 message relation，全部同一 transaction 建立 */
function diagramGraph() {
  const ids = { container: uuid(), pA: uuid(), pB: uuid(), block: uuid(), r1: uuid(), r2: uuid(), r3: uuid() }
  const group = (id: string, extra: object = {}) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'group', transform: { x: 0, y: 0, width: 600, height: 400, rotationDegrees: 0 }, ...extra } })
  const node = (id: string, label: string) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'text', parentId: ids.container, properties: { text: label } } })
  const rel = (relationId: string, s: string, t: string, label: string, extra: object = {}) => ({
    kind: 'create_relation',
    payload: { relationId, sourceObjectId: s, targetObjectId: t, direction: 'forward', label, ...extra },
  })
  const diagram = {
    containerId: { value: ids.container },
    participants: [
      { participant: { id: 'client', label: 'Client', role: 'Actor' }, objectId: { value: ids.pA } },
      { participant: { id: 'server', label: 'Server', role: 'Participant' }, objectId: { value: ids.pB } },
    ],
    messages: [
      { message: { id: 'm1', sourceId: 'client', targetId: 'server', text: 'request', kind: 'Call', dashed: false }, relationId: { value: ids.r1 }, position: { blockId: null, branchIndex: 0, order: 0 } },
      { message: { id: 'm2', sourceId: 'server', targetId: 'client', text: 'response', kind: 'Return', dashed: true }, relationId: { value: ids.r2 }, position: { blockId: null, branchIndex: 0, order: 1 } },
      { message: { id: 'm3', sourceId: 'client', targetId: 'client', text: 'retry', kind: 'Async', dashed: false }, relationId: { value: ids.r3 }, position: { blockId: 'retryLoop', branchIndex: 0, order: 0 } },
    ],
    blocks: [{ id: 'retryLoop', kind: 'Loop', branchLabels: ['until ok'], objectId: { value: ids.block }, position: { blockId: null, branchIndex: 0, order: 2 } }],
    schema: 'boarderless.sequence-canvas.v1',
  }
  const graphOps = [
    group(ids.container, { properties: { title: 'Login' } }),
    node(ids.pA, 'Client'),
    node(ids.pB, 'Server'),
    group(ids.block, { parentId: ids.container, properties: { title: 'retry' } }),
    rel(ids.r1, ids.pA, ids.pB, 'request'),
    rel(ids.r2, ids.pB, ids.pA, 'response'),
    rel(ids.r3, ids.pA, ids.pA, 'retry', { style: { geometry: LOOP } }),
  ]
  return { ids, diagram, graphOps }
}

describe.skipIf(!sql)('sequence diagrams (batch E)', () => {
  let env: TestEnv
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    api = client(env.app, await createUser(env.app, 'Owner'))
  })
  afterAll(async () => {
    await env?.close()
  })

  const newWorkspace = async () => (await api.post('/workspaces', { title: 'Sequence' })).body.id as string
  const head = async (ws: string) => (await api.get(`/workspaces/${ws}`)).body.currentVersion as number
  const submit = async (ws: string, ops: any[]) => api.post(`/workspaces/${ws}/operations`, envelope(await head(ws), ops))
  const state = async (ws: string) => (await api.get(`/workspaces/${ws}/state`)).body
  const seq = (expected: number, before: unknown[], after: unknown[]) => ({ kind: 'update_sequence_diagrams', payload: { expectedSequenceDiagramsVersion: expected, before, after } })

  test('graph + metadata in one transaction; state / snapshot / capability', async () => {
    const ws = await newWorkspace()
    expect(await state(ws)).toMatchObject({ sequenceDiagrams: [], sequenceDiagramsVersion: 0 })
    const { diagram, graphOps } = diagramGraph()
    const res = await submit(ws, [...graphOps, seq(0, [], [diagram])])
    expect(res.body.status).toBe('accepted')
    expect(res.body.operations.at(-1).payload).toMatchObject({ expectedSequenceDiagramsVersion: 0, before: [], after: [diagram], sequenceDiagramsVersion: 1 })
    expect(await state(ws)).toMatchObject({ sequenceDiagrams: [diagram], sequenceDiagramsVersion: 1 })
    expect((await api.post(`/workspaces/${ws}/snapshots`)).body).toMatchObject({ schemaVersion: 3, snapshot: { sequenceDiagrams: [diagram], sequenceDiagramsVersion: 1 } })
    expect((await client(env.app).get('/capabilities')).body.sequenceDiagrams).toMatchObject({ supported: true, operationKinds: ['update_sequence_diagrams'], snapshotSchemaVersion: 3 })
  })

  test('CAS: stale version or before mismatch → 409 sequence_diagrams_conflict with the authoritative metadata', async () => {
    const ws = await newWorkspace()
    const { diagram, graphOps } = diagramGraph()
    await submit(ws, [...graphOps, seq(0, [], [diagram])])
    const stale = await submit(ws, [seq(0, [], [])])
    expect(stale.status).toBe(409)
    expect(stale.body).toMatchObject({ code: 'sequence_diagrams_conflict', sequenceDiagrams: { expectedVersion: 0, actualVersion: 1, diagrams: [diagram], sequenceDiagramsVersion: 1 } })
    const wrongBefore = await submit(ws, [seq(1, [], [])])
    expect(wrongBefore.body.code).toBe('sequence_diagrams_conflict')
    // 正確的 before + version：刪除 metadata（version 單調 +1）
    const removed = await submit(ws, [seq(1, [diagram], [])])
    expect(removed.body.status).toBe('accepted')
    expect(await state(ws)).toMatchObject({ sequenceDiagrams: [], sequenceDiagramsVersion: 2 })
  })

  test('graph changes that would leave dangling or inconsistent metadata reject the whole transaction', async () => {
    const ws = await newWorkspace()
    const { ids, diagram, graphOps } = diagramGraph()
    await submit(ws, [...graphOps, seq(0, [], [diagram])])
    const code = async (ops: any[]) => (await submit(ws, ops)).body.error?.code
    expect(await code([{ kind: 'delete_relations', payload: { relationIds: [ids.r1] } }])).toBe('invalid_sequence_binding')
    expect(await code([{ kind: 'update_object', payload: { objectId: ids.pA, properties: { text: 'Renamed' } } }])).toBe('invalid_sequence_binding')
    expect(await code([{ kind: 'update_relation', payload: { relationId: ids.r1, direction: 'both' } }])).toBe('invalid_sequence_binding')
    expect(await code([{ kind: 'update_relation', payload: { relationId: ids.r1, label: 'other' } }])).toBe('invalid_sequence_binding')
    expect(await code([{ kind: 'update_object', payload: { objectId: ids.pB, parentId: null } }])).toBe('invalid_sequence_binding')
    // 改名 + metadata 同交易：合法
    const renamed = structuredClone(diagram)
    renamed.participants[0]!.participant.label = 'Browser'
    const ok = await submit(ws, [{ kind: 'update_object', payload: { objectId: ids.pA, properties: { text: 'Browser' } } }, seq(1, [diagram], [renamed])])
    expect(ok.body.status).toBe('accepted')
    expect((await state(ws)).sequenceDiagramsVersion).toBe(2)
  })

  test('metadata without its graph, invalid semantics, shared ownership, wrong id formats are rejected', async () => {
    const ws = await newWorkspace()
    const { diagram, graphOps } = diagramGraph()
    expect((await submit(ws, [seq(0, [], [diagram])])).body.error.code).toBe('invalid_sequence_binding') // graph 尚不存在
    await submit(ws, graphOps)
    const mutate = (fn: (d: any) => void) => {
      const d = structuredClone(diagram) as any
      fn(d)
      return d
    }
    const code = async (after: unknown[]) => (await submit(ws, [seq(0, [], after)])).body.error?.code
    expect(await code([mutate((d) => (d.messages[1].message.dashed = false))])).toBe('invalid_sequence_binding') // Return 必須 dashed
    expect(await code([mutate((d) => (d.messages[0].position.order = 1))])).toBe('invalid_sequence_binding')
    expect(await code([mutate((d) => (d.blocks[0].kind = 'Alt'))])).toBe('invalid_sequence_binding')
    expect(await code([mutate((d) => (d.participants[0].participant.id = 'end'))])).toBe('invalid_sequence_binding')
    expect(await code([diagram, mutate((d) => (d.containerId.value = d.blocks[0].objectId.value))])).toBe('invalid_sequence_binding')
    expect(await code([diagram, diagram])).toBe('invalid_sequence_diagram') // containerId 重複
    expect(await code([mutate((d) => (d.containerId.value = 'container-1'))])).toBe('invalid_operation') // graph ID 必須是 UUID
    expect(await code([mutate((d) => delete d.blocks)])).toBe('invalid_operation') // canonical：欄位必填
    expect((await state(ws)).sequenceDiagramsVersion).toBe(0)
  })

  test('Undo / Redo restore metadata and graph together with a monotonic version', async () => {
    const ws = await newWorkspace()
    const { ids, diagram, graphOps } = diagramGraph()
    await submit(ws, [...graphOps, seq(0, [], [diagram])])
    const graphIds = [ids.container, ids.pA, ids.pB, ids.block]
    const relationIds = [ids.r1, ids.r2, ids.r3]
    // Undo 建立：metadata 清空 + 刪 graph（同交易）
    const undo = await submit(ws, [seq(1, [diagram], []), { kind: 'delete_objects', payload: { objectIds: graphIds } }])
    expect(undo.body.status).toBe('accepted')
    expect(undo.body.operations[1].payload.cascadedRelationIds.sort()).toEqual([...relationIds].sort())
    // Redo：restore graph + metadata（version 3，不退回 1）
    const redo = await submit(ws, [
      { kind: 'restore_objects', payload: { objectIds: graphIds } },
      { kind: 'restore_relations', payload: { relationIds } },
      seq(2, [], [diagram]),
    ])
    expect(redo.body.status).toBe('accepted')
    expect(await state(ws)).toMatchObject({ sequenceDiagrams: [diagram], sequenceDiagramsVersion: 3 })
    // 只還原 metadata 而不還原 graph：拒絕
    await submit(ws, [seq(3, [diagram], []), { kind: 'delete_objects', payload: { objectIds: graphIds } }])
    expect((await submit(ws, [seq(4, [], [diagram])])).body.error.code).toBe('invalid_sequence_binding')
  })
})
