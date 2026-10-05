/**
 * 以真實流程（API + 本機 storage + worker + ffmpeg）錄製 Media v1 request / response fixtures。
 * UUID、時間、簽名 URL 正規化為固定值，供 APP（Kotlin）parser 與後端 schema 測試共用。
 * 需要測試 PostgreSQL 與 ffmpeg。用法：bun run fixtures:contract
 */
import { mkdir, rm, writeFile } from 'node:fs/promises'
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, readFixture, setupTestDatabase, sha256, transfer } from '../tests/helpers.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/media-v1')

const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)

// ---- normalization ----
const UUID_RE = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g
const ISO_RE = /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z/g
const ids = new Map<string, string>()
const HEX = '123456789abcdef'
const placeholder = (n: number) => {
  if (n >= HEX.length) return `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`
  const c = HEX[n]!
  const r = c.repeat(4)
  return `${r}${r}-${r}-4${c.repeat(3)}-8${c.repeat(3)}-${r}${r}${r}`
}
const normId = (id: string) => {
  if (!ids.has(id)) ids.set(id, placeholder(ids.size))
  return ids.get(id)!
}

function normalize(value: unknown, key = ''): unknown {
  if (typeof value === 'string') {
    if (key === 'url') return value.includes('/storage/objects/uploads') || value.includes('ct=')
      ? 'https://storage.example.invalid/signed-upload'
      : 'https://storage.example.invalid/signed-download'
    if (key === 'expiresAt') return '2026-10-02T00:15:00.000Z'
    if (key === 'requestId') return '00000000-0000-4000-8000-000000000000'
    return value.replace(UUID_RE, normId).replace(ISO_RE, '2026-10-02T00:00:00.000Z')
  }
  if (Array.isArray(value)) return value.map((v) => normalize(v))
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value).map(([k, v]) => [k, normalize(v, k)]))
  }
  return value
}

const written: { name: string; description: string; status: number }[] = []
async function record(
  name: string,
  description: string,
  request: { method: string; path: string; body?: unknown },
  response: { status: number; body: unknown },
) {
  const doc = {
    description,
    request: { method: request.method, path: normalize(request.path), ...(request.body ? { body: normalize(request.body) } : {}) },
    response: { status: response.status, ...(response.body == null ? {} : { body: normalize(response.body) }) },
  }
  await writeFile(join(OUT, `${name}.json`), `${JSON.stringify(doc, null, 2)}\n`)
  written.push({ name, description, status: response.status })
}

// ---- scenarios ----
await rm(OUT, { recursive: true, force: true })
await mkdir(OUT, { recursive: true })

const ownerId = await createUser(env.app, 'Owner')
const api = client(env.app, ownerId)
const ws = (await api.post('/workspaces', { title: 'Fixtures' })).body.id
normId(ownerId)
normId(ws)
const base = `/api/v1/workspaces/${ws}/assets`
const call = async (method: 'GET' | 'POST' | 'DELETE', path: string, body?: unknown, as = api) => {
  const rel = path.replace('/api/v1', '')
  return method === 'GET' ? as.get(rel) : method === 'DELETE' ? as.delete(rel) : as.post(rel, body)
}

async function prepareUpload(file: string, mediaType: string, name?: string, overrideBytes?: Uint8Array) {
  const bytes = await readFixture(file)
  const body = { mediaType, byteSize: bytes.length, checksum: sha256(bytes) }
  const res = await call('POST', base, body)
  if (name) await record(name, `準備上傳 ${file}：201 {asset, upload}`, { method: 'POST', path: base, body }, res)
  await transfer(env.app, res.body.upload, overrideBytes ?? bytes)
  return { res, bytes, completeBody: { byteSize: bytes.length, checksum: sha256(bytes) } }
}

// 1. PNG：prepare → PUT → complete(202) → GET pending → ready → content → 重送 complete(200)
const png = await prepareUpload('image.png', 'image/png', '01-prepare-201')
const pngId = png.res.body.asset.id
await writeFile(
  join(OUT, '02-upload-put.json'),
  `${JSON.stringify(
    {
      description: 'Signed PUT：只帶 upload.headers，原始 binary（非 multipart），不帶 x-user-id；2xx 不等同 ready',
      request: {
        method: 'PUT',
        url: 'https://storage.example.invalid/signed-upload',
        headers: { 'Content-Type': 'image/png' },
        body: '<binary: tests/fixtures/media/image.png>',
      },
      response: { status: 200 },
    },
    null,
    2,
  )}\n`,
)
written.push({ name: '02-upload-put', description: 'Signed PUT', status: 200 })
const completePath = `${base}/${pngId}/complete`
await record('03-complete-202-pending', 'Durable 接受完成：202 {asset} status=pending', { method: 'POST', path: completePath, body: png.completeBody }, await call('POST', completePath, png.completeBody))
await record('04-get-pending', 'GET metadata（不包裝）：pending', { method: 'GET', path: `${base}/${pngId}` }, await call('GET', `${base}/${pngId}`))
await record('05-content-409-not-ready', 'pending 時不簽發下載票券', { method: 'GET', path: `${base}/${pngId}/content` }, await call('GET', `${base}/${pngId}/content`))
await env.runner.drain()
await record('06-get-ready', 'GET metadata：ready（伺服器驗證後的尺寸）', { method: 'GET', path: `${base}/${pngId}` }, await call('GET', `${base}/${pngId}`))
await record('07-content-200', 'Signed GET：200 {asset, download}', { method: 'GET', path: `${base}/${pngId}/content` }, await call('GET', `${base}/${pngId}/content`))
await record('08-complete-200-ready-retry', '已 ready 後重送相同 complete：200 {asset}', { method: 'POST', path: completePath, body: png.completeBody }, await call('POST', completePath, png.completeBody))
await record(
  '09-complete-409-mismatch',
  '不同 checksum 的 complete：409',
  { method: 'POST', path: completePath, body: { byteSize: png.bytes.length, checksum: `sha256:${'f'.repeat(64)}` } },
  await call('POST', completePath, { byteSize: png.bytes.length, checksum: `sha256:${'f'.repeat(64)}` }),
)
await record('10-abandon-409-completed', '已 complete 的資產不可 abandon：409', { method: 'DELETE', path: `${base}/${pngId}` }, await call('DELETE', `${base}/${pngId}`))

// 2. GIF：ready + thumbnailAssetId → 縮圖 metadata / content
const gif = await prepareUpload('animation.gif', 'image/gif')
const gifId = gif.res.body.asset.id
await call('POST', `${base}/${gifId}/complete`, gif.completeBody)
await env.runner.drain()
const gifReady = await call('GET', `${base}/${gifId}`)
await record('11-get-ready-gif-with-thumbnail', 'GIF ready：thumbnailAssetId 指向另一筆 ready asset', { method: 'GET', path: `${base}/${gifId}` }, gifReady)
const thumbId = gifReady.body.thumbnailAssetId
await record('12-get-thumbnail', '縮圖是獨立 asset（同 Workspace ACL）', { method: 'GET', path: `${base}/${thumbId}` }, await call('GET', `${base}/${thumbId}`))
await record('13-thumbnail-content-200', '縮圖下載走通用 content endpoint', { method: 'GET', path: `${base}/${thumbId}/content` }, await call('GET', `${base}/${thumbId}/content`))

// 3. 影片 ready（duration）
const mp4 = await prepareUpload('video.mp4', 'video/mp4')
await call('POST', `${base}/${mp4.res.body.asset.id}/complete`, mp4.completeBody)
await env.runner.drain()
await record('14-get-ready-video', '影片 ready：durationMs 與 poster 縮圖', { method: 'GET', path: `${base}/${mp4.res.body.asset.id}` }, await call('GET', `${base}/${mp4.res.body.asset.id}`))

// 4. rejected：內容與 checksum 不符
const corrupted = Buffer.from(await readFixture('image.jpg'))
corrupted[corrupted.length - 10]! ^= 0xff
const bad = await prepareUpload('image.jpg', 'image/jpeg', undefined, corrupted)
await call('POST', `${base}/${bad.res.body.asset.id}/complete`, bad.completeBody)
await env.runner.drain()
await record('15-get-rejected', 'Worker 驗證失敗：rejected + rejectionReason', { method: 'GET', path: `${base}/${bad.res.body.asset.id}` }, await call('GET', `${base}/${bad.res.body.asset.id}`))
await record('16-content-409-rejected', 'rejected 不簽發下載票券', { method: 'GET', path: `${base}/${bad.res.body.asset.id}/content` }, await call('GET', `${base}/${bad.res.body.asset.id}/content`))

// 5. missing：ready 後正式內容遺失
const webp = await prepareUpload('image.webp', 'image/webp')
const webpId = webp.res.body.asset.id
await call('POST', `${base}/${webpId}/complete`, webp.completeBody)
await env.runner.drain()
await env.storage.delete((await call('GET', `${base}/${webpId}`)).body.storageKey)
await record('17-content-409-missing', 'ready 但內容遺失：409，不簽發假票券', { method: 'GET', path: `${base}/${webpId}/content` }, await call('GET', `${base}/${webpId}/content`))
await record('18-get-missing', '之後 GET 顯示 missing', { method: 'GET', path: `${base}/${webpId}` }, await call('GET', `${base}/${webpId}`))

// 6. abandon（pending-only）
const pending = await prepareUpload('image.png', 'image/png')
await record('19-abandon-204', '尚未 complete 的上傳可 abandon：204', { method: 'DELETE', path: `${base}/${pending.res.body.asset.id}` }, await call('DELETE', `${base}/${pending.res.body.asset.id}`))
await record('20-complete-409-abandoned', 'abandon 後 complete：409', { method: 'POST', path: `${base}/${pending.res.body.asset.id}/complete`, body: pending.completeBody }, await call('POST', `${base}/${pending.res.body.asset.id}/complete`, pending.completeBody))

// 7. 錯誤
const zeros = `sha256:${'0'.repeat(64)}`
const viewerId = await createUser(env.app, 'Viewer')
await api.put(`/workspaces/${ws}/members/${viewerId}`, { role: 'viewer' })
const viewer = client(env.app, viewerId)
await record('21-prepare-403-viewer', 'viewer 不可準備上傳', { method: 'POST', path: base, body: { mediaType: 'image/png', byteSize: 10, checksum: zeros } }, await call('POST', base, { mediaType: 'image/png', byteSize: 10, checksum: zeros }, viewer))
await record('22-prepare-413', '原檔超過 200 MiB', { method: 'POST', path: base, body: { mediaType: 'video/mp4', byteSize: 209715201, checksum: zeros } }, await call('POST', base, { mediaType: 'video/mp4', byteSize: 209715201, checksum: zeros }))
await record('23-prepare-415', '不在 allowlist 的 MIME', { method: 'POST', path: base, body: { mediaType: 'image/svg+xml', byteSize: 10, checksum: zeros } }, await call('POST', base, { mediaType: 'image/svg+xml', byteSize: 10, checksum: zeros }))
await record('24-prepare-400', 'checksum 格式錯誤', { method: 'POST', path: base, body: { mediaType: 'image/png', byteSize: 10, checksum: 'md5:abc' } }, await call('POST', base, { mediaType: 'image/png', byteSize: 10, checksum: 'md5:abc' }))
await record('25-get-404', '不存在或不可見', { method: 'GET', path: `${base}/${crypto.randomUUID()}` }, await call('GET', `${base}/${crypto.randomUUID()}`))
await record('26-list', 'GET 列表：{assets: [...]}，含衍生縮圖', { method: 'GET', path: base }, await call('GET', base))

await writeFile(join(OUT, 'index.json'), `${JSON.stringify({ contract: 'Media v1', generatedBy: 'scripts/generate-contract-fixtures.ts', fixtures: written }, null, 2)}\n`)
console.log(`wrote ${written.length} fixtures to ${OUT}`)
await env.close()
await sql.end()
