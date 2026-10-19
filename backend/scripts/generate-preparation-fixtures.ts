/**
 * 錄製 asset preparation v2 request / response fixtures（docs/BACKEND_ASSET_PREPARATION_V2_SPEC.md）：
 * 固定 identity、receipt 查詢、ticket lease、complete 與各 terminal phase。
 * 需要測試 PostgreSQL 與 ffmpeg。用法：bun run fixtures:preparation
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, readFixture, setupTestDatabase, sha256, transfer, uuid } from '../tests/helpers.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/asset-preparation-v2')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()

const ownerId = await createUser(env.app, 'Owner')
const api = client(env.app, ownerId)
const ws = (await api.post('/workspaces', { title: 'Preparation fixtures' })).body.id
const otherWs = (await api.post('/workspaces', { title: 'Other workspace' })).body.id
recorder.normId(ownerId)
recorder.normId(ws)
const prep = `/workspaces/${ws}/assets/preparations`
const base = `/api/v1${prep}`
type Client = typeof api

async function call(name: string, description: string, method: 'GET' | 'POST', path: string, body?: unknown, as: Client = api) {
  const rel = path.replace('/api/v1', '')
  const res = method === 'GET' ? await as.get(rel) : await as.post(rel, body)
  await recorder.record(name, description, { method, path, ...(body === undefined ? {} : { body }) }, res)
  return res
}

const metadataOf = (bytes: Uint8Array, mediaType: string, dims: { width?: number; height?: number } = {}) => ({
  byteSize: bytes.length,
  checksum: sha256(bytes),
  durationMs: null,
  height: dims.height ?? null,
  mediaType,
  width: dims.width ?? null,
})
const prepareBody = (requestId: string, metadata: object) => ({ schema: 'boarderless.asset-prepare.v1', requestId, metadata })
const completeBody = (requestId: string, leaseId: string, meta: { byteSize: number; checksum: string }) => ({
  requestId,
  leaseId,
  byteSize: meta.byteSize,
  checksum: meta.checksum,
})

/** 不錄製的前置步驟：prepare + ticket + PUT */
async function staged(file: string, mediaType: string, bytes?: Uint8Array) {
  const original = await readFixture(file)
  const meta = metadataOf(original, mediaType)
  const requestId = uuid()
  await api.post(prep, prepareBody(requestId, meta))
  const issued = await api.post(`${prep}/${requestId}/upload-tickets`, { ticketRequestId: uuid() })
  await transfer(env.app, issued.body.upload, bytes ?? original)
  return { requestId, meta, leaseId: issued.body.upload.leaseId as string }
}

await call('00-capabilities-200', '能力宣告：APP 只在此存在時使用 preparation v2', 'GET', '/api/v1/capabilities')

// ---- 固定 identity ----
const png = await readFixture('image.png')
const metaA = metadataOf(png, 'image/png', { width: 64, height: 48 })
const reqA = uuid()
await call('01-prepare-201', '首次 prepare：201，伺服器生成獨立 asset UUID，phase=prepared、lease=null、無 upload', 'POST', base, prepareBody(reqA, metaA))
await call('02-prepare-retry-200', '相同 key + 相同 metadata 重送：200，同一 identity', 'POST', base, prepareBody(reqA, metaA))
await call('03-prepare-metadata-changed-409', '同 key 不同 metadata：409 preparation_mismatch，不另建資產', 'POST', base, prepareBody(reqA, { ...metaA, width: 65 }))
await call(
  '04-prepare-golden-vector-201',
  '跨端 golden vector：requestDigest = 32d5f0d5…02ee',
  'POST',
  base,
  prepareBody(uuid(), { byteSize: 3, checksum: `sha256:${'b'.repeat(64)}`, durationMs: null, height: 1, mediaType: 'image/png', width: 1 }),
)
await call('05-prepare-unknown-field-400', '未知欄位（不信任 body owner）：400 validation_error', 'POST', base, { ...prepareBody(uuid(), metaA), ownerId })
await call('06-prepare-string-number-400', '不做型別轉換："442" 不是數字', 'POST', base, prepareBody(uuid(), { ...metaA, byteSize: String(metaA.byteSize) }))
await call('07-prepare-unsupported-media-415', '非 canonical / allowlist MIME：415', 'POST', base, prepareBody(uuid(), { ...metaA, mediaType: 'image/svg+xml' }))

// ---- receipt 查詢 ----
await call('08-receipt-200-prepared', '查原 prepare：200 同 identity', 'GET', `${base}/${reqA}`)
await call('09-receipt-404-unknown', '真 absent：404 preparation_not_found', 'GET', `${base}/${uuid()}`)
const editorId = await createUser(env.app, 'Editor')
await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'editor' })
await api.put(`/workspaces/${otherWs}/members/${editorId}`, { role: 'editor' })
const editor = client(env.app, editorId)
await call('10-receipt-404-other-actor', '其他 actor 查同 requestId：404，不洩漏他人 request', 'GET', `${base}/${reqA}`, undefined, editor)
await call('11-receipt-404-wrong-workspace', '同 requestId 換 Workspace：404', 'GET', `/api/v1/workspaces/${otherWs}/assets/preparations/${reqA}`)
const reqEditor = uuid()
await editor.post(prep, prepareBody(reqEditor, metadataOf(png, 'image/png')))
await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'viewer' })
await call('12-receipt-403-demoted', '本人 request 但已降為 viewer：每次重新授權，403', 'GET', `${base}/${reqEditor}`, undefined, editor)

// ---- ticket lease ----
const ticketA = uuid()
const issued = await call('13-ticket-201', '簽發 lease：generation 1、獨立 staging key；upload directive 只在此回應', 'POST', `${base}/${reqA}/upload-tickets`, { ticketRequestId: ticketA })
const leaseA = issued.body.upload.leaseId as string
await call('14-ticket-retry-200', '同 ticketRequestId 重送：同一 lease（重新簽名同 staging key、同到期時間）', 'POST', `${base}/${reqA}/upload-tickets`, { ticketRequestId: ticketA })
await recorder.recordRaw(
  '15-upload-put',
  'Signed PUT：只帶 upload.headers、原始 binary；2xx 不等同 accepted / ready',
  {
    request: {
      method: 'PUT',
      url: 'https://storage.example.invalid/signed-upload',
      headers: { 'Content-Type': 'image/png' },
      body: '<binary: tests/fixtures/media/image.png>',
    },
    response: { status: 200 },
  },
  200,
)
await transfer(env.app, issued.body.upload, png)

const reqB = uuid()
const metaB = metadataOf(png, 'image/png')
await api.post(prep, prepareBody(reqB, metaB))
const ticketB1 = uuid()
const leaseB1 = (await api.post(`${prep}/${reqB}/upload-tickets`, { ticketRequestId: ticketB1 })).body.upload.leaseId
await sql`UPDATE asset_upload_leases SET expires_at = now() - interval '1 second' WHERE lease_id = ${leaseB1}`
await call('16-ticket-expired-409', '過期 lease 的同 ticketRequestId：409 upload_lease_expired，不自動 renew', 'POST', `${base}/${reqB}/upload-tickets`, { ticketRequestId: ticketB1 })
await call('17-ticket-new-generation-201', '另一個先保存的 ticketRequestId：generation 2', 'POST', `${base}/${reqB}/upload-tickets`, { ticketRequestId: uuid() })
await call('18-ticket-superseded-409', '舊 ticketRequestId 已被取代：409 upload_lease_superseded', 'POST', `${base}/${reqB}/upload-tickets`, { ticketRequestId: ticketB1 })
await call('19-complete-superseded-lease-409', '以被取代的 lease complete：409', 'POST', `${base}/${reqB}/complete`, completeBody(reqB, leaseB1, metaB))

// ---- complete ----
await call('20-complete-wrong-lease-409', '不屬於此 preparation 的 leaseId：409 upload_lease_mismatch', 'POST', `${base}/${reqA}/complete`, completeBody(reqA, uuid(), metaA))
await call('21-complete-checksum-mismatch-409', '不同 checksum：409 completion_mismatch', 'POST', `${base}/${reqA}/complete`, completeBody(reqA, leaseA, { ...metaA, checksum: `sha256:${'f'.repeat(64)}` }))
await call('22-complete-request-id-mismatch-400', 'body requestId 必須等於 path', 'POST', `${base}/${reqA}/complete`, completeBody(uuid(), leaseA, metaA))
await call('23-complete-202-accepted', 'durable 接受：202 phase=completionAccepted（asset.status 仍 pending）', 'POST', `${base}/${reqA}/complete`, completeBody(reqA, leaseA, metaA))
await call('24-receipt-completion-accepted', 'worker 處理中：phase=completionAccepted，與未完成 prepare 區分', 'GET', `${base}/${reqA}`)
await call('25-complete-retry-202', '同 lease 重送：回目前 phase，不重建工作', 'POST', `${base}/${reqA}/complete`, completeBody(reqA, leaseA, metaA))
await call('26-ticket-after-accepted-409', '已 accepted 不可再取得 PUT', 'POST', `${base}/${reqA}/upload-tickets`, { ticketRequestId: uuid() })
await env.runner.drain()
await call('27-receipt-ready', 'ready：伺服器驗證後的尺寸，lease 為被接受的 lease', 'GET', `${base}/${reqA}`)
await call('28-ticket-ready-409', 'ready 禁止 PUT：409 upload_not_allowed', 'POST', `${base}/${reqA}/upload-tickets`, { ticketRequestId: uuid() })
await call('29-complete-ready-200', 'ready 後重送 complete：200 phase=ready', 'POST', `${base}/${reqA}/complete`, completeBody(reqA, leaseA, metaA))

// ---- terminal phases ----
const corrupted = Buffer.from(await readFixture('image.jpg'))
corrupted[corrupted.length - 10]! ^= 0xff
const rejected = await staged('image.jpg', 'image/jpeg', corrupted)
await api.post(`${prep}/${rejected.requestId}/complete`, completeBody(rejected.requestId, rejected.leaseId, rejected.meta))
await env.runner.drain()
await call('30-receipt-rejected', '內容與宣告 checksum 不符：phase=rejected（identity 保留）', 'GET', `${base}/${rejected.requestId}`)

const missing = await staged('image.webp', 'image/webp')
await api.post(`${prep}/${missing.requestId}/complete`, completeBody(missing.requestId, missing.leaseId, missing.meta))
await env.runner.drain()
const missingAsset = (await api.get(`${prep}/${missing.requestId}`)).body.asset
await env.storage.delete(missingAsset.storageKey)
await api.get(`/workspaces/${ws}/assets/${missingAsset.id}/content`)
await call('31-receipt-missing', 'ready 後原檔遺失：phase=missing', 'GET', `${base}/${missing.requestId}`)

const abandoned = await staged('image.png', 'image/png')
const abandonedId = (await api.get(`${prep}/${abandoned.requestId}`)).body.asset.id
await api.delete(`/workspaces/${ws}/assets/${abandonedId}`)
await call('32-receipt-abandoned', 'abandon 後：tombstone identity 保留，phase=abandoned', 'GET', `${base}/${abandoned.requestId}`)
await call('33-prepare-abandoned-retry-200', '晚到的原 prepare：200 同 identity，不復活不重建', 'POST', base, prepareBody(abandoned.requestId, abandoned.meta))
await call('34-complete-abandoned-409', 'abandoned 後 complete：409 asset_abandoned', 'POST', `${base}/${abandoned.requestId}/complete`, completeBody(abandoned.requestId, abandoned.leaseId, abandoned.meta))

const bypass = await staged('image.png', 'image/png')
const bypassId = (await api.get(`${prep}/${bypass.requestId}`)).body.asset.id
await call(
  '35-v1-complete-409',
  'v1 complete 不能繞過 v2 lease：409 preparation_v2_required',
  'POST',
  `/api/v1/workspaces/${ws}/assets/${bypassId}/complete`,
  { byteSize: bypass.meta.byteSize, checksum: bypass.meta.checksum },
)

// ---- ASSET-08：declared 與 verified 分離 ----
async function finished(declared: Record<string, number>) {
  const meta = { ...metadataOf(png, 'image/png'), ...declared }
  const requestId = uuid()
  await api.post(prep, prepareBody(requestId, meta))
  const issued = await api.post(`${prep}/${requestId}/upload-tickets`, { ticketRequestId: uuid() })
  await transfer(env.app, issued.body.upload, png)
  await api.post(`${prep}/${requestId}/complete`, completeBody(requestId, issued.body.upload.leaseId, meta))
  await env.runner.drain()
  return requestId
}
const declaredNull = await finished({})
await call('36-receipt-ready-declared-null', 'declared 為 null：不比較、永不改寫；verified 為伺服器解析的顯示尺寸', 'GET', `${base}/${declaredNull}`)
const declaredWrong = await finished({ width: 32, height: 24 })
await call(
  '37-receipt-rejected-declared-mismatch',
  '宣告尺寸與實際不符：rejected declared_metadata_mismatch，declared 保留、verified 保存實際值',
  'GET',
  `${base}/${declaredWrong}`,
)

const count = await recorder.writeIndex('Asset preparation v2', 'scripts/generate-preparation-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
