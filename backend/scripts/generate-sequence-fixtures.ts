/**
 * 錄製批次 E fixtures：update_sequence_diagrams（graph + metadata 同交易、CAS / before 衝突、懸空 / 不一致拒絕、
 * Undo / Redo 新版本）、state / snapshot schema 3。用法：bun run fixtures:sequence
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid } from '../tests/helpers.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/sequence-v1')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()
const owner = await createUser(env.app, 'Owner')
const api = client(env.app, owner)
const ws = (await api.post('/workspaces', { title: 'Sequence fixtures' })).body.id
recorder.normId(owner)
recorder.normId(ws)
const opsPath = `/api/v1/workspaces/${ws}/operations`
const head = async () => (await api.get(`/workspaces/${ws}`)).body.currentVersion
async function submit(name: string, description: string, ops: any[]) {
  const request = envelope(await head(), ops)
  const res = await api.post(`/workspaces/${ws}/operations`, request)
  await recorder.record(name, description, { method: 'POST', path: opsPath, body: request }, res)
  return res
}

const ids = { container: uuid(), pA: uuid(), pB: uuid(), block: uuid(), r1: uuid(), r2: uuid(), r3: uuid() }
const LOOP = { schema: 'boarderless.relation-geometry.v1', route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } }
const group = (id: string, extra: object = {}) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'group', transform: { x: 0, y: 0, width: 600, height: 400, rotationDegrees: 0 }, ...extra } })
const node = (id: string, label: string) => ({ kind: 'create_object', payload: { objectId: id, objectType: 'text', parentId: ids.container, properties: { text: label } } })
const rel = (relationId: string, s: string, t: string, label: string, extra: object = {}) => ({ kind: 'create_relation', payload: { relationId, sourceObjectId: s, targetObjectId: t, direction: 'forward', label, ...extra } })
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
const seq = (expected: number, before: unknown[], after: unknown[]) => ({ kind: 'update_sequence_diagrams', payload: { expectedSequenceDiagramsVersion: expected, before, after } })
const graph = [
  group(ids.container, { properties: { title: 'Login' } }),
  node(ids.pA, 'Client'),
  node(ids.pB, 'Server'),
  group(ids.block, { parentId: ids.container, properties: { title: 'retry' } }),
  rel(ids.r1, ids.pA, ids.pB, 'request'),
  rel(ids.r2, ids.pB, ids.pA, 'response'),
  rel(ids.r3, ids.pA, ids.pA, 'retry', { style: { geometry: LOOP } }),
]

await recorder.record('00-state-empty', '初始：sequenceDiagrams = []、version 0', { method: 'GET', path: `/api/v1/workspaces/${ws}/state` }, await api.get(`/workspaces/${ws}/state`))
await submit('01-metadata-without-graph-422', '只送 metadata、graph 不存在：invalid_sequence_binding（全交易拒絕）', [seq(0, [], [diagram])])
await submit('02-create-graph-and-metadata', 'container / participants / block / message relations + update_sequence_diagrams 同一 transaction', [...graph, seq(0, [], [diagram])])
await submit('03-stale-version-409', 'expectedSequenceDiagramsVersion 過期：409 sequence_diagrams_conflict + 權威 metadata', [seq(0, [], [])])
await submit('04-before-mismatch-409', 'before 與目前不完全一致：409', [seq(1, [], [])])
await submit('05-delete-message-relation-422', '刪除訊息 relation 而不更新 metadata：拒絕（不留懸空引用）', [{ kind: 'delete_relations', payload: { relationIds: [ids.r1] } }])
await submit('06-rename-participant-alone-422', '只改 participant 節點文字：label 不一致，拒絕', [{ kind: 'update_object', payload: { objectId: ids.pA, properties: { text: 'Browser' } } }])
const renamed = structuredClone(diagram)
renamed.participants[0]!.participant.label = 'Browser'
await submit('07-rename-with-metadata', '節點改名 + metadata 同交易：accepted，version 2', [{ kind: 'update_object', payload: { objectId: ids.pA, properties: { text: 'Browser' } } }, seq(1, [diagram], [renamed])])
const bad = structuredClone(renamed) as any
bad.messages[1].message.dashed = false
await submit('08-return-not-dashed-422', 'Return 必須 dashed（APP SequenceMessage 規則）', [seq(2, [renamed], [bad])])
const nonUuid = structuredClone(renamed) as any
nonUuid.containerId.value = 'container-1'
await submit('09-graph-id-not-uuid-422', 'server 路徑的 graph ID 必須是 UUID（semantic ID 才是受限 ASCII）', [seq(2, [renamed], [nonUuid])])
const graphIds = [ids.container, ids.pA, ids.pB, ids.block]
await submit('10-undo-create', 'Undo：metadata 清空 + 刪 graph（同交易），version 3', [seq(2, [renamed], []), { kind: 'delete_objects', payload: { objectIds: graphIds } }])
await submit('11-redo-create', 'Redo：restore graph + relations + metadata，version 4（單調，不退回）', [
  { kind: 'restore_objects', payload: { objectIds: graphIds } },
  { kind: 'restore_relations', payload: { relationIds: [ids.r1, ids.r2, ids.r3] } },
  seq(3, [], [renamed]),
])
await recorder.record('12-state', 'state：sequenceDiagrams 與 graph 同一 throughServerSeq', { method: 'GET', path: `/api/v1/workspaces/${ws}/state` }, await api.get(`/workspaces/${ws}/state`))
await recorder.record('13-snapshot-schema-3', 'snapshot schema 3：含 sequenceDiagrams / version', { method: 'POST', path: `/api/v1/workspaces/${ws}/snapshots` }, await api.post(`/workspaces/${ws}/snapshots`))

const count = await recorder.writeIndex('Sequence diagrams v1（batch E）', 'scripts/generate-sequence-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
