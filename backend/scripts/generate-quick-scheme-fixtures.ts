/**
 * 錄製快速方案簿（批次 B）fixtures：selection v1–v9 round-trip、ETag / Idempotency-Key、tombstone、分頁快照、
 * 素材 binding 與錯誤。payload 樣本見 tests/support/selection-samples.ts（依 APP ClipboardPayload.kt 手寫的 kotlinx 形狀）。
 * 需要測試 PostgreSQL 與 ffmpeg。用法：bun run fixtures:quick-scheme
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, readFixture, setupTestDatabase, sha256, transfer, uuid } from '../tests/helpers.ts'
import { selectionV1, selectionV1Legacy, selectionV4, TEXT_ONLY_SAMPLES } from '../tests/support/selection-samples.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/quick-scheme-v2')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()

const owner = await createUser(env.app, 'Owner')
const other = await createUser(env.app, 'Other')
recorder.normId(owner)
const BASE = '/api/v1/users/me/quick-schemes'

const CONTRACT_HEADERS = ['if-match', 'if-none-match', 'idempotency-key']
async function call(
  name: string,
  description: string,
  method: 'GET' | 'PUT' | 'DELETE',
  path: string,
  opts: { headers?: Record<string, string>; body?: unknown; raw?: string; as?: string } = {},
) {
  const res = await env.app.inject({
    method,
    url: path,
    headers: { 'x-user-id': opts.as ?? owner, ...(opts.body !== undefined || opts.raw !== undefined ? { 'content-type': 'application/json' } : {}), ...opts.headers },
    ...(opts.raw !== undefined ? { payload: opts.raw } : opts.body !== undefined ? { payload: JSON.stringify(opts.body) } : {}),
  })
  const body = res.body ? JSON.parse(res.body) : null
  const responseHeaders: Record<string, string> = {}
  for (const h of ['etag', 'cache-control', 'idempotent-replayed']) if (res.headers[h]) responseHeaders[h] = String(res.headers[h])
  const requestHeaders = Object.fromEntries(Object.entries(opts.headers ?? {}).filter(([k]) => CONTRACT_HEADERS.includes(k)))
  await recorder.record(
    name,
    description,
    {
      method,
      path,
      ...(Object.keys(requestHeaders).length ? { headers: requestHeaders } : {}),
      ...(opts.body !== undefined ? { body: opts.body } : opts.raw !== undefined ? { body: `<raw: ${opts.raw.slice(0, 60)}${opts.raw.length > 60 ? '…' : ''}>` } : {}),
    },
    { status: res.statusCode, body, headers: responseHeaders },
  )
  return { status: res.statusCode, body, etag: String(res.headers.etag ?? '') }
}
const resource = (payload: any, name: string, assetBindings: unknown[] = []) => ({ resourceVersion: 2, name, schemaVersion: payload.version, payload, assetBindings })
const createHeaders = (key = uuid()) => ({ 'if-none-match': '*', 'idempotency-key': key })
const updateHeaders = (etag: string, key = uuid()) => ({ 'if-match': etag, 'idempotency-key': key })

await call('00-capabilities-200', 'resource / selection 版本、limits、receipt TTL、materializationEnabled=false', 'GET', '/api/v1/users/me/quick-scheme-capabilities')

// ---- selection v1–v9 round-trip ----
let n = 1
for (const [version, make] of Object.entries(TEXT_ONLY_SAMPLES)) {
  const id = uuid()
  const label = `v${version}`
  await call(`${String(n++).padStart(2, '0')}-create-selection-${label}-201`, `selection ${label}：201，payload 原樣保存（不降版、不丟欄位）`, 'PUT', `${BASE}/${id}`, {
    headers: createHeaders(),
    body: resource(make(), `Selection ${label}`),
  })
}
await call('10-create-selection-v1-legacy-201', '舊版最小 v1（缺省欄位依預設值）：201', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: resource(selectionV1Legacy(), 'Legacy') })

// ---- v4 媒體：真實 ready 素材 + 縮圖 ----
const api = client(env.app, owner)
const ws = (await api.post('/workspaces', { title: 'Scheme source' })).body.id
recorder.normId(ws)
async function readyAsset(file: string, mediaType: string) {
  const bytes = await readFixture(file)
  const prepared = await api.post(`/workspaces/${ws}/assets`, { mediaType, byteSize: bytes.length, checksum: sha256(bytes) })
  await transfer(env.app, prepared.body.upload, bytes)
  await api.post(`/workspaces/${ws}/assets/${prepared.body.asset.id}/complete`, { byteSize: bytes.length, checksum: sha256(bytes) })
  return prepared.body.asset.id as string
}
const image = await readyAsset('image.png', 'image/png')
const video = await readyAsset('video.mp4', 'video/mp4')
await env.runner.drain()
const videoThumbnail = (await api.get(`/workspaces/${ws}/assets/${video}`)).body.thumbnailAssetId
const assets = { image, video, videoThumbnail }
const bindings = [
  { assetId: image, sourceWorkspaceId: ws, kind: 'original' },
  { assetId: video, sourceWorkspaceId: ws, kind: 'original' },
  { assetId: videoThumbnail, sourceWorkspaceId: ws, kind: 'thumbnail' },
]
const mediaId = uuid()
await call('11-create-selection-v4-media-201', 'v4 媒體方案：assetBindings 精確覆蓋原檔 / 縮圖；assetAvailability 為讀取時狀態', 'PUT', `${BASE}/${mediaId}`, {
  headers: createHeaders(),
  body: resource(selectionV4(assets), 'Media plan', bindings),
})

// ---- CRUD / ETag / 冪等 ----
const id = uuid()
const key = uuid()
const body1 = resource(selectionV1(), 'Planning card')
const created = await call('20-create-201', 'If-None-Match: * + Idempotency-Key：201 resource + ETag', 'PUT', `${BASE}/${id}`, { headers: createHeaders(key), body: body1 })
await call('21-get-200', 'GET：200 + ETag + Cache-Control: private, no-store', 'GET', `${BASE}/${id}`)
await call('22-get-304', 'If-None-Match 命中：304 無 body', 'GET', `${BASE}/${id}`, { headers: { 'if-none-match': created.etag } })
const renamed = await call('23-update-200', '以最新 ETag 完整替換（改名也送完整資源）：200 revision 2、新 ETag', 'PUT', `${BASE}/${id}`, {
  headers: updateHeaders(created.etag),
  body: resource(selectionV1(), 'Renamed card'),
})
await call('24-update-stale-412', '過期 ETag：412 revision_conflict（UI 保留 draft，提供使用遠端 / 另存副本 / 檢查差異）', 'PUT', `${BASE}/${id}`, {
  headers: updateHeaders(created.etag),
  body: resource(selectionV1(), 'Lost update'),
})
await call('25-create-replay-201', '同 Idempotency-Key + 同內容重送：回原結果（原 ETag），即使之後已更新', 'PUT', `${BASE}/${id}`, { headers: createHeaders(key), body: body1 })
await call('26-idempotency-mismatch-409', '同 key 不同內容：409 idempotency_mismatch', 'PUT', `${BASE}/${id}`, { headers: createHeaders(key), body: resource(selectionV1(), 'Other') })
await call('27-create-existing-412', '已存在的 id 再 create：412', 'PUT', `${BASE}/${id}`, { headers: createHeaders(), body: body1 })
await call('28-missing-precondition-428', '缺 If-None-Match / If-Match：428 precondition_required', 'PUT', `${BASE}/${uuid()}`, { headers: { 'idempotency-key': uuid() }, body: body1 })
await call('29-if-match-star-428', 'If-Match: * 不能取代具體 ETag：428', 'PUT', `${BASE}/${id}`, { headers: updateHeaders('*'), body: body1 })
await call('30-foreign-owner-404', '他人的方案 ID：404（不洩漏存在）', 'GET', `${BASE}/${id}`, { as: other })

const delKey = uuid()
await call('31-delete-204', 'DELETE（If-Match + Idempotency-Key）：204；保留 tombstone、移除自己的 pin、不刪素材', 'DELETE', `${BASE}/${id}`, { headers: updateHeaders(renamed.etag, delKey) })
await call('32-delete-replay-204', '同 key 重送 delete：204（原結果）', 'DELETE', `${BASE}/${id}`, { headers: updateHeaders(renamed.etag, delKey) })
await call('33-get-deleted-404', '刪除後 GET：404', 'GET', `${BASE}/${id}`)
await call('34-create-after-delete-412', 'tombstone 防復活：同 id create 回 412 {deleted: true}', 'PUT', `${BASE}/${id}`, { headers: createHeaders(), body: body1 })

// ---- 驗證錯誤 ----
await call('40-unsupported-resource-version-422', 'resourceVersion 不支援：422', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: { ...body1, resourceVersion: 3 } })
await call('41-unsupported-selection-422', 'selection schemaVersion 不支援：422', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: { ...body1, schemaVersion: 10, payload: { ...body1.payload, version: 10 } } })
await call('42-invalid-payload-422', 'payload 不合法：422 invalid_payload + APP issue code', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: { ...body1, payload: { ...body1.payload, nodes: [] } } })
await call('43-body-owner-rejected-422', 'body 不接受 ownerId / revision 等 server 欄位：422', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: { ...body1, ownerId: other } })
await call('44-duplicate-key-400', '嚴格 JSON：重複 key 拒絕', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), raw: '{"resourceVersion":2,"resourceVersion":2}' })
await call('45-missing-binding-422', '媒體引用缺 binding：422 invalid_asset_binding', 'PUT', `${BASE}/${uuid()}`, { headers: createHeaders(), body: resource(selectionV4(assets), 'No bindings') })
await call('46-asset-access-denied-403', '非來源 workspace 成員：403 asset_access_denied（持有 UUID 不等於授權）', 'PUT', `${BASE}/${uuid()}`, {
  as: other,
  headers: createHeaders(),
  body: resource(selectionV4(assets), 'Foreign', bindings),
})
const quotaUser = await createUser(env.app, 'Quota')
await call('47-create-first-for-quota', '（quota 前置）', 'PUT', `${BASE}/${uuid()}`, { as: quotaUser, headers: createHeaders(), body: body1 })
await sql`UPDATE quick_scheme_collections SET active_count = 1000 WHERE owner_id = ${quotaUser}`
await call('48-quota-exceeded-409', '每使用者 1000 方案：409 quota_exceeded', 'PUT', `${BASE}/${uuid()}`, { as: quotaUser, headers: createHeaders(), body: body1 })

// ---- 分頁 ----
const lister = await createUser(env.app, 'Lister')
const listed: { id: string; etag: string }[] = []
for (let i = 0; i < 3; i++) {
  const sid = uuid()
  listed.push({ id: sid, etag: (await call(`50-list-seed-${i}`, '（分頁前置）', 'PUT', `${BASE}/${sid}`, { as: lister, headers: createHeaders(), body: resource(selectionV1(), `Item ${i}`) })).etag })
}
const page1 = await call('51-list-page-1', '列表：穩定快照 cursor（綁定 owner / collectionRevision / 期限）', 'GET', `${BASE}?limit=2`, { as: lister })
await call('52-list-page-2', '下一頁：同一快照', 'GET', `${BASE}?limit=2&cursor=${page1.body.nextCursor}`, { as: lister })
const again = await call('53-list-restart', '（重新列舉）', 'GET', `${BASE}?limit=2`, { as: lister })
await call('54-list-not-modified-304', '首頁 If-None-Match = collection ETag：304', 'GET', `${BASE}?limit=2`, { as: lister, headers: { 'if-none-match': again.etag } })
const changed = listed.sort((x, y) => x.id.localeCompare(y.id))[0]!
await call('55-list-mutation-during-listing', '（列舉途中變更）', 'PUT', `${BASE}/${changed.id}`, { as: lister, headers: updateHeaders(changed.etag), body: resource(selectionV1(), 'Changed') })
await call('56-list-cursor-expired-410', '快照期間 collection 變更：410 cursor_expired，重新列舉', 'GET', `${BASE}?limit=2&cursor=${again.body.nextCursor}`, { as: lister })

const count = await recorder.writeIndex('Quick scheme v2（batch B）', 'scripts/generate-quick-scheme-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
