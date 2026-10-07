import { afterAll, describe, expect, test } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type postgres from 'postgres'
import { LocalObjectStorage } from '../../src/storage/local.storage.ts'
import { S3ObjectStorage } from '../../src/storage/s3.storage.ts'
import type { ObjectStorage } from '../../src/storage/types.ts'
import { client, createTestEnv, createUser, hasMediaTools, readFixture, setupTestDatabase, sha256 } from '../helpers.ts'

/**
 * 透過真實 HTTP 走完整流程：API（signed directive）→ client 直傳 storage → complete → worker → signed GET。
 * app.inject 不經過真實 socket，這裡驗證串流上傳 / 下載與 S3 簽名。
 */
const sql = await setupTestDatabase()
const S3_ENDPOINT = process.env.TEST_S3_ENDPOINT ?? 'http://localhost:9000'
const s3Reachable = await fetch(S3_ENDPOINT, { signal: AbortSignal.timeout(1500) }).then(
  () => true,
  () => false,
)
if (sql && !s3Reachable) console.warn(`[s3] ${S3_ENDPOINT} 無法連線，略過 S3 端到端測試（先執行 \`bun run db:up\`）`)

afterAll(async () => {
  await sql?.end()
})

async function runFlow(storage: ObjectStorage, port: number, file: { name: string; mediaType: string; bytes: Uint8Array }) {
  const env = await createTestEnv(sql as postgres.Sql, { storage })
  await env.app.listen({ host: '127.0.0.1', port })
  try {
    const base = `http://127.0.0.1:${port}/api/v1`
    const userId = await createUser(env.app)
    const headers = { 'content-type': 'application/json', 'x-user-id': userId }
    const ws = (await client(env.app, userId).post('/workspaces', { title: 'E2E' })).body.id

    const prepared = await fetch(`${base}/workspaces/${ws}/assets`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ mediaType: file.mediaType, byteSize: file.bytes.length, checksum: sha256(file.bytes) }),
    }).then((r) => r.json() as Promise<any>)

    // 錯誤的 Content-Type 不符合簽名
    const wrongType = await fetch(prepared.upload.url, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: file.bytes,
    })
    expect(wrongType.status).toBe(403)

    // APP 只轉送 directive.headers，不帶 x-user-id
    const put = await fetch(prepared.upload.url, { method: 'PUT', headers: prepared.upload.headers, body: file.bytes })
    expect(put.ok).toBe(true)

    const completed = await fetch(`${base}/workspaces/${ws}/assets/${prepared.asset.id}/complete`, {
      method: 'POST',
      headers,
      body: JSON.stringify({ byteSize: prepared.asset.byteSize, checksum: prepared.asset.checksum }),
    })
    expect(completed.status).toBe(202)

    await env.runner.drain()

    const content = await fetch(`${base}/workspaces/${ws}/assets/${prepared.asset.id}/content`, { headers }).then(
      (r) => r.json() as Promise<any>,
    )
    expect(content.asset.status).toBe('ready')
    const download = await fetch(content.download.url, { headers: content.download.headers })
    expect(download.status).toBe(200)
    const downloaded = new Uint8Array(await download.arrayBuffer())
    expect(downloaded.length).toBe(file.bytes.length)
    expect(sha256(downloaded)).toBe(sha256(file.bytes))
    return content.asset
  } finally {
    await env.close()
  }
}

describe.skipIf(!sql || !hasMediaTools)('local storage driver over real HTTP', () => {
  test('streams a multi-megabyte video upload and download', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'boarderless-e2e-'))
    try {
      // 約 4 MB 的影片：驗證非 inject 的串流 PUT
      const path = join(dir, 'big.mp4')
      spawnSync('ffmpeg', [
        '-v', 'error', '-y', '-f', 'lavfi', '-i', 'testsrc2=size=1280x720:rate=30:duration=4',
        '-c:v', 'libx264', '-preset', 'ultrafast', '-qp', '10', '-pix_fmt', 'yuv420p', path,
      ])
      const bytes = await readFile(path)
      expect(bytes.length).toBeGreaterThan(1_000_000)
      const port = 41000 + Math.floor(Math.random() * 1000)
      const storage = new LocalObjectStorage(join(dir, 'objects'), `http://127.0.0.1:${port}`, 'e2e-secret')
      const asset = await runFlow(storage, port, { name: 'big.mp4', mediaType: 'video/mp4', bytes })
      expect(asset).toMatchObject({ width: 1280, height: 720 })
      expect(asset.durationMs).toBeGreaterThanOrEqual(3900)
    } finally {
      await rm(dir, { recursive: true, force: true })
    }
  }, 60_000)
})

describe.skipIf(!sql || !hasMediaTools || !s3Reachable)('S3-compatible storage (RustFS / MinIO)', () => {
  const storage = new S3ObjectStorage({
    driver: 's3',
    endpoint: S3_ENDPOINT,
    publicEndpoint: S3_ENDPOINT,
    region: 'us-east-1',
    bucket: 'boarderless-test',
    accessKeyId: process.env.TEST_S3_ACCESS_KEY_ID ?? 'boarderless',
    secretAccessKey: process.env.TEST_S3_SECRET_ACCESS_KEY ?? 'boarderless-dev-secret',
    ensureBucket: true,
  })

  test('presigned PUT / GET, verification and thumbnail through S3', async () => {
    await storage.init(['*'])
    const bytes = await readFixture('animation.gif')
    const port = 42000 + Math.floor(Math.random() * 1000)
    const asset = await runFlow(storage, port, { name: 'animation.gif', mediaType: 'image/gif', bytes })
    expect(asset.thumbnailAssetId).toBeString()
    expect(await storage.head(asset.storageKey)).toMatchObject({ size: bytes.length })
  }, 60_000)

  test('If-None-Match prevents overwriting an existing object', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'boarderless-s3-'))
    try {
      const path = join(dir, 'x.png')
      await Bun.write(path, await readFixture('image.png'))
      const key = `tests/${crypto.randomUUID()}.png`
      await storage.putFile(key, path, { contentType: 'image/png', ifNoneMatch: true })
      await expect(storage.putFile(key, path, { contentType: 'image/png', ifNoneMatch: true })).rejects.toThrow(
        'Object already exists',
      )
      await storage.delete(key)
      expect(await storage.head(key)).toBeNull()
      await storage.delete(key) // 冪等
    } finally {
      await rm(dir, { recursive: true, force: true })
    }
  })
})
