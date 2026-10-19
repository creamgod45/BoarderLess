/**
 * 錄製共用背景（批次 C）fixtures：state 預設、update_canvas_style accepted / CAS conflict / 非法 token / 未知 schema、
 * 與圖形 operation 同交易 rollback、duplicate、Undo（新版本）、viewer、snapshot schema 2。
 * 用法：bun run fixtures:canvas-style
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid } from '../tests/helpers.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/canvas-style-v1')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()

const owner = await createUser(env.app, 'Owner')
const api = client(env.app, owner)
const ws = (await api.post('/workspaces', { title: 'Style fixtures' })).body.id
recorder.normId(owner)
recorder.normId(ws)
const DEFAULT = { schemaVersion: 1, backgroundToken: 'default', gridStyle: { pattern: 'lines', colorToken: 'auto' } }
const CUSTOM = { schemaVersion: 1, backgroundToken: '#204060', gridStyle: { pattern: 'dots', colorToken: 'auto' } }
const style = (expected: number, canvasStyle: unknown) => ({ kind: 'update_canvas_style', payload: { expectedCanvasStyleVersion: expected, canvasStyle } })
const opsPath = `/api/v1/workspaces/${ws}/operations`
const head = async () => (await api.get(`/workspaces/${ws}`)).body.currentVersion

async function submit(name: string, description: string, ops: unknown[], body?: unknown, as = api) {
  const request = body ?? envelope(await head(), ops as any)
  const res = await as.post(`/workspaces/${ws}/operations`, request)
  await recorder.record(name, description, { method: 'POST', path: opsPath, body: request }, res)
  return { res, request }
}
async function get(name: string, description: string, path: string) {
  await recorder.record(name, description, { method: 'GET', path: `/api/v1${path}` }, await api.get(path))
}

await get('01-state-default', '新 Workspace：canvasStyle = default、canvasStyleVersion = 0（與 objects 同一 throughServerSeq）', `/workspaces/${ws}/state`)
const accepted = await submit('02-update-accepted', 'update_canvas_style：CAS 成功 +1；正式 payload 帶權威 canvasStyleVersion', [style(0, CUSTOM)])
await get('03-state-after-update', 'state 反映新 style / version', `/workspaces/${ws}/state`)
await submit('04-cas-conflict-409', 'expectedCanvasStyleVersion 過期：409 canvas_style_version_conflict + 權威 style 與 checkpoint', [style(0, DEFAULT)])
await submit('05-invalid-hex-422', '大寫 hex 不靜默修正：422 invalid_canvas_style', [style(1, { ...DEFAULT, backgroundToken: '#ABCDEF' })])
await submit('06-unknown-schema-422', '未知 style schemaVersion fail closed：422 unsupported_canvas_style_schema', [style(1, { ...DEFAULT, schemaVersion: 2 })])
await submit('07-unknown-field-422', '未知欄位（alpha）：422 invalid_operation', [style(1, { ...DEFAULT, alpha: 0.5 })])
await submit('08-mixed-rollback-422', '與圖形 operation 同交易：任一失敗全部 rollback（style 不變）', [style(1, DEFAULT), { kind: 'create_object', payload: { objectId: uuid(), objectType: 'future' } }])
await submit('09-duplicate', '原 envelope 重送：duplicate，不再 +1', [], accepted.request)
await submit('10-undo-new-version', 'Undo 是新的 operation：after = 原 before，版本單調增加到 2', [style(1, DEFAULT)])
await submit('11-mixed-accepted', '圖形 + style 同一 transaction：同一 workspaceVersion', [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } }, style(2, CUSTOM)])
const viewer = await createUser(env.app, 'Viewer')
await api.put(`/workspaces/${ws}/members/${viewer}`, { role: 'viewer' })
await submit('12-viewer-forbidden-403', 'viewer / commenter 不可修改共用背景', [style(3, DEFAULT)], undefined, client(env.app, viewer))
{
  const res = await api.post(`/workspaces/${ws}/snapshots`)
  await recorder.record('13-snapshot-schema-2', 'snapshot schema 2：含 canvasStyle / canvasStyleVersion', { method: 'POST', path: `/api/v1/workspaces/${ws}/snapshots` }, res)
}
await get('14-capabilities', '全域 capabilities：canvasStyle 宣告 operation kind、style schema、snapshot schema、default', '/capabilities')

const count = await recorder.writeIndex('Canvas style v1（batch C）', 'scripts/generate-canvas-style-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
