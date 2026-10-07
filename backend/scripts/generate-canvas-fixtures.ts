/**
 * 錄製 canvas projection 合約 fixtures（BAI-001 / 007 / 008 / 011 / 012）：
 * 型別化 schema、group 階層、restore、原提交 receipt / fence。
 * 需要測試 PostgreSQL。用法：bun run fixtures:canvas
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid } from '../tests/helpers.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/canvas-v1')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()

const ownerId = await createUser(env.app, 'Owner')
const api = client(env.app, ownerId)
const ws = (await api.post('/workspaces', { title: 'Canvas fixtures' })).body.id
recorder.normId(ownerId)
recorder.normId(ws)
const opsPath = `/api/v1/workspaces/${ws}/operations`

type Op = { kind: string; payload: unknown; expectedObjectVersions?: Record<string, number> }
const transform = (o: Record<string, unknown> = {}) => ({ x: 0, y: 0, width: 260, height: 132, rotationDegrees: 0, ...o })
const textOp = (objectId: string, extra: Record<string, unknown> = {}): Op => ({
  kind: 'create_object',
  payload: { objectId, objectType: 'text', transform: transform(), properties: { text: 'Idea', colorToken: 'paper', shapeToken: 'rounded' }, ...extra },
})
const groupOp = (objectId: string, extra: Record<string, unknown> = {}): Op => ({
  kind: 'create_object',
  payload: { objectId, objectType: 'group', transform: transform({ width: 600, height: 400 }), properties: { title: 'Group', colorToken: 'group' }, ...extra },
})

async function submit(name: string, description: string, ops: Op[], as = api, body?: ReturnType<typeof envelope>) {
  const version = (await api.get(`/workspaces/${ws}`)).body.currentVersion
  const request = body ?? envelope(version, ops)
  const res = await as.post(`/workspaces/${ws}/operations`, request)
  await recorder.record(name, description, { method: 'POST', path: opsPath, body: request }, res)
  return { res, request }
}

async function post(name: string, description: string, path: string, body: unknown, as = api) {
  const res = await as.post(path.replace('/api/v1', ''), body)
  await recorder.record(name, description, { method: 'POST', path, body }, res)
  return res
}

// ---- BAI-008 typed schema ----
const [t1, g1] = [uuid(), uuid()]
await submit('01-create-text-group-accepted', '完整 text / group 物件：accepted', [textOp(t1), groupOp(g1)])
const bare = uuid()
await submit('02-create-defaults-accepted', '欄位不存在時套用預設：transform 由伺服器補齊並寫入正式 payload', [
  { kind: 'create_object', payload: { objectId: bare, objectType: 'text' } },
])
await submit('03-unsupported-object-type-422', '未知 objectType：回報支援型別與 objectSchemaVersion 供舊 client 提示升級', [
  { kind: 'create_object', payload: { objectId: uuid(), objectType: 'future_widget' } },
])
await submit('04-invalid-object-types-422', '錯誤型別：非數字座標、負尺寸、text 為數字', [
  textOp(uuid(), { transform: transform({ x: 'not-a-number', width: -1 }), properties: { text: 42 } }),
])
await submit('05-invalid-object-explicit-null-422', 'explicit null 不視為缺省', [textOp(uuid(), { properties: { text: null } })])
await submit('06-invalid-object-unknown-token-422', '未知 shapeToken / colorToken', [
  textOp(uuid(), { properties: { shapeToken: 'blob', colorToken: 'neon' } }),
])
await submit('07-boundary-values-accepted', '邊界值：尺寸 0、座標 ±1e7、自訂 #rrggbb 色、全部 APP shape 之一', [
  textOp(uuid(), {
    transform: transform({ x: -10_000_000, y: 10_000_000, width: 0, height: 0, rotationDegrees: -90.5 }),
    properties: { text: '', colorToken: '#a1b2c3', shapeToken: 'manual-input' },
  }),
])
await submit('08-update-merged-invalid-422', 'update 驗證 merge 後完整狀態：text 物件不可加 group 欄位', [
  { kind: 'update_object', payload: { objectId: t1, properties: { title: 'not for text' } } },
])
await submit('09-update-object-type-unsupported-422', '型別替換為明確不支援的變更（BAI-011）', [
  { kind: 'update_object', payload: { objectId: t1, objectType: 'group' } },
])

// ---- BAI-007 hierarchy ----
await submit('10-parent-not-group-422', 'parent 必須是 group', [textOp(uuid(), { parentId: t1 })])
const [ga, gb] = [uuid(), uuid()]
await submit('11-nested-groups-accepted', '同一 transaction 建立巢狀 group', [groupOp(ga), groupOp(gb, { parentId: ga })])
await submit('12-parent-cycle-422', '間接循環：A → B → A', [{ kind: 'update_object', payload: { objectId: ga, parentId: gb } }])
const child = uuid()
await submit('13-create-child-accepted', '在 group 內建立 child', [textOp(child, { parentId: g1 })])
await submit('14-delete-group-with-children-422', '刪除 group 時 children 必須一起刪除或先 reparent', [
  { kind: 'delete_objects', payload: { objectIds: [g1] } },
])

// ---- BAI-001 / BAI-011 restore ----
const [ra, rb, rel] = [uuid(), uuid(), uuid()]
await submit('15-create-relation-accepted', '兩個物件與 relation', [
  textOp(ra),
  textOp(rb),
  { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: ra, targetObjectId: rb, direction: 'forward', label: null, style: { colorToken: 'relation' } } },
])
await submit('16-delete-cascades-relation-accepted', '刪除物件：cascadedRelationIds 寫入正式 payload', [
  { kind: 'delete_objects', payload: { objectIds: [ra] }, expectedObjectVersions: { [ra]: 1 } },
])
await submit('17-create-on-tombstone-422', '同 ID create 不兼任 restore：提示 restoreWith', [textOp(ra)])
await submit('18-restore-relation-endpoint-missing-422', '端點仍刪除時不可還原 relation', [
  { kind: 'restore_relations', payload: { relationIds: [rel] } },
])
await submit('19-restore-stale-version-409', 'restore 以 tombstone 版本做樂觀鎖', [
  { kind: 'restore_objects', payload: { objectIds: [ra] }, expectedObjectVersions: { [ra]: 1 } },
])
await submit('20-restore-objects-relations-accepted', '同一 transaction 還原物件與 cascaded relations', [
  { kind: 'restore_objects', payload: { objectIds: [ra] }, expectedObjectVersions: { [ra]: 2 } },
  { kind: 'restore_relations', payload: { relationIds: [rel] } },
])
await submit('21-update-relation-endpoint-unsupported-422', 'relation 端點修改為明確不支援的變更', [
  { kind: 'update_relation', payload: { relationId: rel, targetObjectId: t1 } },
])
{
  const res = await api.get(`/workspaces/${ws}/state`)
  await recorder.record('22-state-after-restore', '還原後的 projection：版本 +1、狀態保留', { method: 'GET', path: `/api/v1/workspaces/${ws}/state` }, res)
}

// ---- BAI-012 receipts / fences ----
const receipts = `${opsPath}/receipts`
const fences = `${opsPath}/fences`
const original = envelope((await api.get(`/workspaces/${ws}`)).body.currentVersion, [textOp(uuid()), textOp(uuid())])
await submit('23-original-transaction-accepted', '原提交（兩個 operation）', [], api, original)
await post('24-receipt-partial-query', '只查其中一個 operation ID：回傳完整交易邊界 receipt（不含 payload）', receipts, {
  operationIds: [original.operations[1]!.operationId],
})

const editorId = await createUser(env.app, 'Editor')
await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'editor' })
const editor = client(env.app, editorId)
await post('25-receipt-other-actor-unknown', '其他 actor 不能認領同一 transaction / operation ID', receipts, {
  transactionIds: [original.transactionId],
  operationIds: [original.operations[0]!.operationId],
}, editor)

const late = envelope((await api.get(`/workspaces/${ws}`)).body.currentVersion, [textOp(uuid())])
await post('26-receipt-unknown', '尚未提交：unknown（不排除晚到的原請求）', receipts, { transactionIds: [late.transactionId] })
await post('27-fence-unknown-fenced', 'fence 尚未提交的原 transaction：fenced', fences, { transactionIds: [late.transactionId] })
await submit('28-late-original-fenced-409', '被 fence 的原請求晚到：409 transaction_fenced，永不 commit', [], api, late)
await post('29-fence-committed-returns-receipt', 'fence 已提交的 transaction：回傳 committed receipt', fences, {
  transactionIds: [original.transactionId],
})
await post('30-receipt-limit-400', '超過 200 個 ID', receipts, { operationIds: Array.from({ length: 201 }, () => uuid()) })
await api.delete(`/workspaces/${ws}/members/${editorId}`)
await post('31-receipt-revoked-404', '撤銷 membership 後查詢：404', receipts, { transactionIds: [original.transactionId] }, editor)

const count = await recorder.writeIndex('Canvas v1（BAI-001 / 007 / 008 / 011 / 012）', 'scripts/generate-canvas-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
