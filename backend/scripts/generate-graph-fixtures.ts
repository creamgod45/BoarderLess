/**
 * 錄製批次 D fixtures：relation geometry（四方向 / 平行 / 自循環 / manual / auto reset / round-trip / cascade）、
 * 插入節點交易（forward / backward / both / none / self-loop、Undo / Redo、CAS race、receipt）、
 * TextNode vectorPath / textPlacement。用法：bun run fixtures:graph
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid } from '../tests/helpers.ts'
import { SAMPLE_VECTOR_PATH } from '../tests/support/selection-samples.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/graph-v2')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()
const owner = await createUser(env.app, 'Owner')
const api = client(env.app, owner)
const ws = (await api.post('/workspaces', { title: 'Graph fixtures' })).body.id
recorder.normId(owner)
recorder.normId(ws)
const ops = `/api/v1/workspaces/${ws}/operations`
const GEO = 'boarderless.relation-geometry.v1'
const AUTO = { schema: GEO, route: { mode: 'auto' }, labelPlacement: { mode: 'auto' } }
const MANUAL = { schema: GEO, route: { mode: 'manual', waypoints: [{ x: 320, y: 180 }, { x: 520, y: 180 }] }, labelPlacement: { mode: 'manual', pathFraction: 0.5, tangentOffset: 0, normalOffset: 24 } }
const LOOP = { schema: GEO, route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } }

const head = async () => (await api.get(`/workspaces/${ws}`)).body.currentVersion
async function submit(name: string, description: string, operations: any[], body?: any) {
  const request = body ?? envelope(await head(), operations)
  const res = await api.post(`/workspaces/${ws}/operations`, request)
  await recorder.record(name, description, { method: 'POST', path: ops, body: request }, res)
  return { res, request }
}
async function get(name: string, description: string, path: string) {
  const res = await api.get(path)
  await recorder.record(name, description, { method: 'GET', path: `/api/v1${path}` }, res)
  return res
}
const text = (objectId: string, extra: object = {}) => ({ kind: 'create_object', payload: { objectId, objectType: 'text', ...extra } })
const rel = (relationId: string, s: string, t: string, extra: object = {}) => ({ kind: 'create_relation', payload: { relationId, sourceObjectId: s, targetObjectId: t, direction: 'forward', ...extra } })
const relationOf = async (id: string) => (await api.get(`/workspaces/${ws}/state`)).body.relations.find((r: any) => r.relationId === id)

const [a, b] = [uuid(), uuid()]
await submit('01-nodes', '兩個端點節點', [text(a, { properties: { text: 'A' } }), text(b, { properties: { text: 'B' } })])

// ---- geometry / directions / parallel ----
const [rf, rb, rboth, rnone, rpar] = [uuid(), uuid(), uuid(), uuid(), uuid()]
await submit('02-create-four-directions-parallel', '四方向 + 平行線（不同 relationId 同端點）；manual route + manual label', [
  rel(rf, a, b, { label: 'call', intent: 'flow', style: { colorToken: 'relation', geometry: MANUAL } }),
  rel(rb, a, b, { direction: 'backward', style: { geometry: AUTO } }),
  rel(rboth, a, b, { direction: 'both' }),
  rel(rnone, a, b, { direction: 'none' }),
  rel(rpar, a, b, { intent: 'parallel' }),
])
await submit('03-update-geometry-auto-reset', 'geometry 整個子物件替換（重設 auto），不殘留舊 waypoints', [{ kind: 'update_relation', payload: { relationId: rf, style: { geometry: AUTO } } }])
await submit('04-update-colortoken-keeps-geometry', '舊 client 只改 colorToken：geometry 保留', [{ kind: 'update_relation', payload: { relationId: rf, style: { colorToken: '#123456' } } }])
await submit('05-update-manual-label', 'manual route + manual label', [{ kind: 'update_relation', payload: { relationId: rf, style: { geometry: MANUAL } } }])

// ---- self-loop ----
const [loop, autoLoop] = [uuid(), uuid()]
await submit('10-self-loop-without-geometry-422', '自循環必須明確 geometry：422 invalid_relation_geometry', [rel(uuid(), a, a)])
await submit('11-self-loop-manual-422', '自循環不接受 manual route', [rel(uuid(), a, a, { style: { geometry: MANUAL } })])
await submit('12-create-self-loops', '自循環：loop route 與 auto（預設 right / 80 由 renderer 固定）', [
  rel(loop, a, a, { direction: 'none', label: 'retry', style: { geometry: LOOP } }),
  rel(autoLoop, a, a, { style: { geometry: AUTO } }),
])
await submit('13-loop-on-normal-relation-422', '普通線不接受 loop route', [rel(uuid(), a, b, { style: { geometry: LOOP } })])
const c = uuid()
await submit('14-node-with-self-loop', '（前置）帶自循環的節點', [text(c), rel(uuid(), c, c, { style: { geometry: LOOP } })])
const cLoop = (await api.get(`/workspaces/${ws}/state`)).body.relations.find((r: any) => r.sourceObjectId === c).relationId
await submit('15-delete-node-cascades-self-loop-once', '刪除節點：自循環只 cascade 一次（cascadedRelationIds）', [{ kind: 'delete_objects', payload: { objectIds: [c] } }])
await submit('16-restore-node-and-self-loop', '還原：relation version 只 +1', [{ kind: 'restore_objects', payload: { objectIds: [c] } }, { kind: 'restore_relations', payload: { relationIds: [cLoop] } }])

// ---- invalid geometry ----
const invalid: [string, string, unknown][] = [
  ['20-unknown-geometry-schema-422', '未知 geometry schema：unsupported_geometry_schema', { ...AUTO, schema: 'boarderless.relation-geometry.v2' }],
  ['21-null-geometry-422', 'explicit null 不是重設（重設用完整 auto）', null],
  ['22-33-waypoints-422', '33 個 waypoints', { ...MANUAL, route: { mode: 'manual', waypoints: Array.from({ length: 33 }, (_, i) => ({ x: i, y: 0 })) } }],
  ['23-coordinate-out-of-range-422', '座標超過 ±10,000,000', { ...MANUAL, route: { mode: 'manual', waypoints: [{ x: 1e8, y: 0 }] } }],
  ['24-incomplete-manual-label-422', 'manual label 必須完整四欄', { ...MANUAL, labelPlacement: { mode: 'manual', pathFraction: 0.5 } }],
  ['25-unknown-field-422', '分支內不相干欄位', { ...AUTO, route: { mode: 'auto', waypoints: [] } }],
]
for (const [name, description, geometry] of invalid) await submit(name, `${description}；同交易的其他 operation 不提交`, [text(uuid()), rel(uuid(), a, b, { style: { geometry } })])

// ---- insert node ----
async function insertCase(prefix: string, label: string, s: string, t: string, segments: (n: string) => [string, string, object][], extra: object = {}, geometry: unknown = MANUAL) {
  const direction = label === 'self-loop' ? 'forward' : label
  const r = uuid()
  await submit(`${prefix}-0-original`, `（前置）原線 ${label}`, [rel(r, s, t, { direction, ...extra, style: { geometry } })])
  const original = await relationOf(r)
  const n = uuid()
  const r1 = uuid()
  const r2 = uuid()
  const [[s1, t1, e1], [s2, t2, e2]] = segments(n)
  await submit(`${prefix}-1-insert`, `插入節點（${label}）：delete R（版本 CAS）+ create N + create R1 / R2，同一 transaction；新段 geometry auto`, [
    { kind: 'delete_relations', payload: { relationIds: [r] }, expectedObjectVersions: { [r]: original.relationVersion } },
    text(n, { properties: { text: 'Step' } }),
    rel(r1, s1, t1, e1),
    rel(r2, s2, t2, e2),
  ])
  await submit(`${prefix}-2-undo`, 'Undo：刪 R1 / R2、刪 N（版本 CAS）、restore 原 R（tombstone CAS，原 geometry 保留）', [
    { kind: 'delete_relations', payload: { relationIds: [r1, r2] } },
    { kind: 'delete_objects', payload: { objectIds: [n] }, expectedObjectVersions: { [n]: 1 } },
    { kind: 'restore_relations', payload: { relationIds: [r] }, expectedObjectVersions: { [r]: original.relationVersion + 1 } },
  ])
  await submit(`${prefix}-3-redo`, 'Redo：刪 R、restore N、restore R1 / R2（同 ID，不重建）', [
    { kind: 'delete_relations', payload: { relationIds: [r] } },
    { kind: 'restore_objects', payload: { objectIds: [n] } },
    { kind: 'restore_relations', payload: { relationIds: [r1, r2] } },
  ])
}
await insertCase('30-insert-forward', 'forward', a, b, (n) => [[a, n, { label: 'call', intent: 'flow' }], [n, b, {}]], { label: 'call', intent: 'flow' })
await insertCase('31-insert-backward', 'backward', a, b, (n) => [[b, n, {}], [n, a, {}]])
await insertCase('32-insert-both', 'both', a, b, (n) => [[a, n, { direction: 'both' }], [n, b, { direction: 'both' }]])
await insertCase('33-insert-none', 'none', a, b, (n) => [[a, n, { direction: 'none' }], [n, b, { direction: 'none' }]])
await insertCase('34-insert-self-loop', 'self-loop', a, a, (n) => [[a, n, {}], [n, a, {}]], {}, LOOP)
{
  const r = uuid()
  await submit('35-race-0-original', '（前置）', [rel(r, a, b)])
  const original = await relationOf(r)
  const tx = (n: string) => [
    { kind: 'delete_relations', payload: { relationIds: [r] }, expectedObjectVersions: { [r]: original.relationVersion } },
    text(n),
    rel(uuid(), a, n),
    rel(uuid(), n, b),
  ]
  await submit('35-race-1-first-accepted', '兩端同時插入：先到者 accepted', tx(uuid()))
  await submit('35-race-2-second-conflict-409', '後到者以舊版本 CAS：409 conflict，不建立 N', tx(uuid()))
}
{
  const insertTx = envelope(await head(), [text(uuid()), rel(uuid(), a, b)])
  await recorder.record('36-receipt-unknown', '未知 ACK：先查原 transaction receipt（未提交 → unknown）', { method: 'POST', path: `${ops}/receipts`, body: { transactionIds: [insertTx.transactionId] } }, await api.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [insertTx.transactionId] }))
  await submit('37-insert-commit', '原請求送達並 commit', [], insertTx)
  await recorder.record('38-receipt-committed', '再查：committed receipt；重送原 envelope 回 duplicate，不另造 ID', { method: 'POST', path: `${ops}/receipts`, body: { transactionIds: [insertTx.transactionId] } }, await api.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [insertTx.transactionId] }))
}

// ---- vectorPath / textPlacement ----
const holes = {
  ...SAMPLE_VECTOR_PATH,
  commands: [
    { type: 'move', point: { x: 0, y: 0 } }, { type: 'line', point: { x: 100, y: 0 } }, { type: 'line', point: { x: 100, y: 100 } }, { type: 'line', point: { x: 0, y: 100 } }, { type: 'close' },
    { type: 'move', point: { x: 25, y: 25 } }, { type: 'line', point: { x: 75, y: 25 } }, { type: 'quadratic', control: { x: 150, y: 50 }, point: { x: 75, y: 75 } }, { type: 'close' },
    { type: 'move', point: { x: -40, y: -40 } }, { type: 'cubic', control1: { x: 200, y: -60 }, control2: { x: -60, y: 200 }, point: { x: 10, y: 10 } },
  ],
  style: { fillColorToken: 'paper', strokeColorToken: null, strokeWidth: 2, fillRule: 'EvenOdd' },
}
const pen = uuid()
await submit('40-create-vector-path', 'TextNode vectorPath：line / quadratic / cubic、closed / open、多 subpath（EvenOdd 孔洞）、控制點超出 viewBox', [text(pen, { properties: { text: '', vectorPath: holes } })])
await submit('41-update-vector-path', 'vectorPath 整個替換（properties 淺層 merge 的單一 key）', [{ kind: 'update_object', payload: { objectId: pen, properties: { vectorPath: SAMPLE_VECTOR_PATH } } }])
const placement = { anchor: 'Below', offsetX: 0, offsetY: 8, appearance: { fontSize: 18, weight: 700, color: '#112233', font: 'Serif', italic: true, underline: false, strikethrough: false } }
await submit('42-text-placement', 'textPlacement（APP v8）', [{ kind: 'update_object', payload: { objectId: pen, properties: { textPlacement: placement } } }])
await submit('43-clear-with-null', 'null 清除 vectorPath / textPlacement', [{ kind: 'update_object', payload: { objectId: pen, properties: { vectorPath: null, textPlacement: null } } }])
await submit('44-invalid-command-order-422', '非法 command 順序（連續 move / 空 subpath）', [text(uuid(), { properties: { vectorPath: { ...SAMPLE_VECTOR_PATH, commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'move', point: { x: 1, y: 1 } }] } } })])
await submit('45-unknown-command-422', '未知 command（不接受 SVG）', [text(uuid(), { properties: { vectorPath: { ...SAMPLE_VECTOR_PATH, commands: [{ type: 'move', point: { x: 0, y: 0 } }, { type: 'svg', d: 'M0 0 L1 1' }] } } })])
await submit('46-unknown-schema-422', 'vectorPath schemaVersion 2', [text(uuid(), { properties: { vectorPath: { ...SAMPLE_VECTOR_PATH, schemaVersion: 2 } } })])
await submit('47-delete-restore-vector-path', '刪除 / 還原保留 path（BAI-011 restore）', [{ kind: 'update_object', payload: { objectId: pen, properties: { vectorPath: holes } } }])
await submit('48-delete', '（刪除）', [{ kind: 'delete_objects', payload: { objectIds: [pen] } }])
await submit('49-restore', '還原後 path 完整', [{ kind: 'restore_objects', payload: { objectIds: [pen] } }])
await get('50-state', '最終 projection：geometry / 自循環 / vectorPath 完整', `/workspaces/${ws}/state`)
await get('51-catch-up', 'operation log catch-up：正式 payload 保留 geometry / vectorPath', `/workspaces/${ws}/operations?afterSeq=0&limit=1000`)

const count = await recorder.writeIndex('Graph v2（batch D：relation geometry / insert node / vectorPath）', 'scripts/generate-graph-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
