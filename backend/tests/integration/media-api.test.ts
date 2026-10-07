import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { generateThumbnail } from '../../src/jobs/generate-thumbnail.job.ts'
import { JobRunner } from '../../src/jobs/runner.ts'
import { collectExpiredUploads } from '../../src/jobs/upload-gc.ts'
import { jobKeys } from '../../src/models/job.ts'
import { StorageUnavailableError, type ObjectStorage } from '../../src/storage/types.ts'
import {
  client,
  createTestEnv,
  createUser,
  envelope,
  fixture,
  hasMediaTools,
  mediaFixtures,
  readFixture,
  sha256,
  setupTestDatabase,
  transfer,
  uuid,
  type TestEnv,
} from '../helpers.ts'

const sql = await setupTestDatabase()
if (sql && !hasMediaTools) console.warn('[media] 找不到 ffmpeg / ffprobe，略過素材整合測試')
afterAll(async () => {
  await sql?.end()
})

describe.skipIf(!sql || !hasMediaTools)('media asset lifecycle (Media v1)', () => {
  let env: TestEnv
  let ownerId: string
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    ownerId = await createUser(env.app, 'Owner')
    api = client(env.app, ownerId)
  })

  afterAll(async () => {
    await env?.close()
  })

  async function newWorkspace(): Promise<string> {
    return (await api.post('/workspaces', { title: 'Media' })).body.id
  }

  async function prepare(ws: string, bytes: Uint8Array, mediaType: string, overrides: Record<string, unknown> = {}) {
    return api.post(`/workspaces/${ws}/assets`, {
      mediaType,
      byteSize: bytes.length,
      checksum: sha256(bytes),
      ...overrides,
    })
  }

  /** prepare → PUT → complete，回傳 complete 回應 */
  async function uploadAndComplete(ws: string, file: string, body?: Uint8Array) {
    const bytes = await readFixture(file)
    const prepared = await prepare(ws, bytes, fixture(file).mediaType)
    expect(prepared.status).toBe(201)
    const put = await transfer(env.app, prepared.body.upload, body ?? bytes)
    expect(put.status).toBe(200)
    const { asset } = prepared.body
    const completed = await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, {
      byteSize: asset.byteSize,
      checksum: asset.checksum,
    })
    return { prepared, completed, assetId: asset.id as string, bytes }
  }

  async function downloadContent(ws: string, assetId: string, as = api) {
    const content = await as.get(`/workspaces/${ws}/assets/${assetId}/content`)
    expect(content.status).toBe(200)
    expect(content.body.download.method).toBe('GET')
    const res = await transfer(env.app, content.body.download)
    expect(res.status).toBe(200)
    return { content, bytes: res.bytes, headers: res.headers }
  }

  describe('prepare', () => {
    test('returns {asset, upload} with a signed PUT directive and no legacy uploadUrl', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const res = await prepare(ws, bytes, 'image/png', { width: 64, height: 48 })
      expect(res.status).toBe(201)
      expect(res.body).not.toHaveProperty('uploadUrl')
      expect(res.body.asset).toMatchObject({
        workspaceId: ws,
        ownerId,
        mediaType: 'image/png',
        byteSize: bytes.length,
        checksum: sha256(bytes),
        status: 'pending',
        thumbnailAssetId: null,
      })
      expect(res.body.asset.storageKey).toContain(res.body.asset.id)
      expect(res.body.upload).toMatchObject({ method: 'PUT', headers: { 'Content-Type': 'image/png' } })
      expect(Date.parse(res.body.upload.expiresAt)).toBeGreaterThan(Date.now())
      // signed URL 不指向 storageKey（opaque handle），也不在 metadata 中
      expect(res.body.upload.url).not.toContain(res.body.asset.storageKey)
      expect(JSON.stringify(res.body.asset)).not.toContain('sig=')
    })

    test('validates MIME allowlist, size limit and checksum format', async () => {
      const ws = await newWorkspace()
      const checksum = `sha256:${'0'.repeat(64)}`
      const unsupported = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'image/svg+xml', byteSize: 10, checksum })
      expect(unsupported.status).toBe(415)
      expect(unsupported.body.error.code).toBe('unsupported_media_type')

      const tooLarge = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'video/mp4', byteSize: 209_715_201, checksum })
      expect(tooLarge.status).toBe(413)
      const maxSize = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'video/mp4', byteSize: 209_715_200, checksum })
      expect(maxSize.status).toBe(201)

      const badChecksum = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'image/png', byteSize: 10, checksum: 'sha256:ABC' })
      expect(badChecksum.status).toBe(400)
      const zero = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'image/png', byteSize: 0, checksum })
      expect(zero.status).toBe(400)
    })

    test('viewers cannot prepare uploads', async () => {
      const ws = await newWorkspace()
      const viewerId = await createUser(env.app, 'Viewer')
      await api.put(`/workspaces/${ws}/members/${viewerId}`, { role: 'viewer' })
      const res = await client(env.app, viewerId).post(`/workspaces/${ws}/assets`, {
        mediaType: 'image/png',
        byteSize: 1,
        checksum: `sha256:${'0'.repeat(64)}`,
      })
      expect(res.status).toBe(403)
    })
  })

  describe('signed upload', () => {
    test('rejects a different Content-Type, oversized body and tampered signature', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const { upload } = (await prepare(ws, bytes, 'image/png')).body

      expect((await transfer(env.app, upload, bytes, { 'Content-Type': 'image/jpeg' })).status).toBe(403)
      expect((await transfer(env.app, upload, new Uint8Array(bytes.length + 1))).status).toBe(413)
      expect((await transfer(env.app, { ...upload, url: upload.url.replace(/sig=[^&]+/, 'sig=forged') }, bytes)).status).toBe(403)
      expect((await transfer(env.app, upload, bytes)).status).toBe(200)
    })
  })

  describe('complete → worker → ready', () => {
    for (const media of mediaFixtures) {
      test(`${media.mediaType} (${media.file}) becomes ready with verified metadata`, async () => {
        const ws = await newWorkspace()
        const { completed, assetId, bytes } = await uploadAndComplete(ws, media.file)
        expect(completed.status).toBe(202)
        expect(completed.body.asset.status).toBe('pending')

        // ready 前不可簽發下載
        const early = await api.get(`/workspaces/${ws}/assets/${assetId}/content`)
        expect(early.status).toBe(409)
        expect(early.body.error.code).toBe('asset_not_ready')

        await env.runner.drain()

        const asset = (await api.get(`/workspaces/${ws}/assets/${assetId}`)).body
        expect(asset).not.toHaveProperty('asset') // GET 回傳不包裝的 metadata
        expect(asset).toMatchObject({
          id: assetId,
          status: 'ready',
          mediaType: media.mediaType,
          byteSize: media.byteSize,
          checksum: media.checksum,
          width: media.width,
          height: media.height,
          durationMs: media.durationMs,
        })

        const { content, bytes: downloaded, headers } = await downloadContent(ws, assetId)
        expect(content.body.asset.id).toBe(assetId)
        expect(sha256(downloaded)).toBe(sha256(bytes))
        expect(downloaded.length).toBe(media.byteSize)
        expect(headers['content-type']).toBe(media.mediaType)

        const animated = media.mediaType === 'image/gif' || media.mediaType.startsWith('video/')
        if (!animated) {
          expect(asset.thumbnailAssetId).toBeNull()
          return
        }
        // 縮圖：另一筆 ready asset，透過通用 metadata / content endpoint 讀取
        expect(asset.thumbnailAssetId).toBeString()
        expect(asset.thumbnailAssetId).not.toBe(assetId)
        const thumb = (await api.get(`/workspaces/${ws}/assets/${asset.thumbnailAssetId}`)).body
        expect(thumb).toMatchObject({
          workspaceId: ws,
          status: 'ready',
          mediaType: media.mediaType === 'image/gif' ? 'image/png' : 'image/jpeg',
          thumbnailAssetId: null,
          durationMs: null,
        })
        expect(thumb.width).toBeLessThanOrEqual(512)
        const thumbContent = await downloadContent(ws, thumb.id)
        expect(sha256(thumbContent.bytes)).toBe(thumb.checksum)
        expect(thumbContent.bytes.length).toBe(thumb.byteSize)

        // 原檔下載內容不被縮圖或轉碼取代
        expect(sha256((await downloadContent(ws, assetId)).bytes)).toBe(media.checksum)
      })
    }

    test('list returns all visible assets including derived thumbnails', async () => {
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'animation.gif')
      await env.runner.drain()
      const { assets } = (await api.get(`/workspaces/${ws}/assets`)).body
      const original = assets.find((a: { id: string }) => a.id === assetId)
      expect(assets).toHaveLength(2)
      expect(assets.map((a: { id: string }) => a.id)).toContain(original.thumbnailAssetId)
    })
  })

  describe('worker validation', () => {
    const rejection = async (ws: string, assetId: string) => {
      await env.runner.drain()
      return (await api.get(`/workspaces/${ws}/assets/${assetId}`)).body
    }

    test('truncated upload is rejected (byte_size_mismatch)', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.jpg')
      const { assetId } = await uploadAndComplete(ws, 'image.jpg', bytes.subarray(0, bytes.length - 100))
      expect(await rejection(ws, assetId)).toMatchObject({ status: 'rejected', rejectionReason: 'byte_size_mismatch' })
      const content = await api.get(`/workspaces/${ws}/assets/${assetId}/content`)
      expect(content.status).toBe(409)
      expect(content.body.error.code).toBe('asset_rejected')
    })

    test('content that does not match the checksum is rejected (checksum_mismatch)', async () => {
      const ws = await newWorkspace()
      const bytes = Buffer.from(await readFixture('image.png'))
      const corrupted = Buffer.from(bytes)
      corrupted[corrupted.length - 20]! ^= 0xff
      const { assetId } = await uploadAndComplete(ws, 'image.png', corrupted)
      expect(await rejection(ws, assetId)).toMatchObject({ status: 'rejected', rejectionReason: 'checksum_mismatch' })
    })

    test('declared MIME is not trusted: JPEG bytes declared as PNG are rejected', async () => {
      const ws = await newWorkspace()
      const jpeg = await readFixture('image.jpg')
      const prepared = await prepare(ws, jpeg, 'image/png')
      await transfer(env.app, prepared.body.upload, jpeg)
      const { asset } = prepared.body
      await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum })
      expect(await rejection(ws, asset.id)).toMatchObject({ status: 'rejected', rejectionReason: 'media_type_mismatch' })
    })

    test('valid signature with undecodable body is rejected', async () => {
      const ws = await newWorkspace()
      const png = await readFixture('image.png')
      const broken = Buffer.concat([png.subarray(0, 16), Buffer.alloc(400, 0x41)])
      const prepared = await prepare(ws, broken, 'image/png')
      await transfer(env.app, prepared.body.upload, broken)
      const { asset } = prepared.body
      await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum })
      expect(await rejection(ws, asset.id)).toMatchObject({ status: 'rejected', rejectionReason: 'undecodable_media' })
    })

    test('server corrects client-declared dimensions', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.webp')
      const prepared = await prepare(ws, bytes, 'image/webp', { width: 9999, height: 1 })
      await transfer(env.app, prepared.body.upload, bytes)
      const { asset } = prepared.body
      await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum })
      await env.runner.drain()
      expect((await api.get(`/workspaces/${ws}/assets/${asset.id}`)).body).toMatchObject({ width: 64, height: 48 })
    })
  })

  describe('complete idempotency and conflicts', () => {
    test('complete before upload is a 409 and leaves the asset pending', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const { asset } = (await prepare(ws, bytes, 'image/png')).body
      const res = await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum })
      expect(res.status).toBe(409)
      expect(res.body.error.code).toBe('upload_not_found')
      expect((await api.get(`/workspaces/${ws}/assets/${asset.id}`)).body.status).toBe('pending')
    })

    test('a repeated complete (lost response) returns the current state without new jobs', async () => {
      const ws = await newWorkspace()
      const { completed, assetId, prepared } = await uploadAndComplete(ws, 'image.png')
      expect(completed.status).toBe(202)
      const body = { byteSize: prepared.body.asset.byteSize, checksum: prepared.body.asset.checksum }

      const retry = await api.post(`/workspaces/${ws}/assets/${assetId}/complete`, body)
      expect(retry.status).toBe(202)
      expect(retry.body.asset.status).toBe('pending')
      expect(await env.db.repos.jobs.listByAsset(assetId)).toHaveLength(1)

      await env.runner.drain()
      const afterReady = await api.post(`/workspaces/${ws}/assets/${assetId}/complete`, body)
      expect(afterReady.status).toBe(200)
      expect(afterReady.body.asset.status).toBe('ready')
      expect((await env.db.repos.jobs.listByAsset(assetId)).filter((j) => j.jobType === 'verify_original')).toHaveLength(1)
    })

    test('complete with a different checksum or size is a 409 conflict', async () => {
      const ws = await newWorkspace()
      const { assetId, prepared } = await uploadAndComplete(ws, 'image.png')
      const { byteSize } = prepared.body.asset
      const wrongChecksum = await api.post(`/workspaces/${ws}/assets/${assetId}/complete`, {
        byteSize,
        checksum: `sha256:${'f'.repeat(64)}`,
      })
      expect(wrongChecksum.status).toBe(409)
      expect(wrongChecksum.body.error.code).toBe('completion_mismatch')
      const wrongSize = await api.post(`/workspaces/${ws}/assets/${assetId}/complete`, {
        byteSize: byteSize + 1,
        checksum: prepared.body.asset.checksum,
      })
      expect(wrongSize.status).toBe(409)
    })
  })

  describe('pending-only abandon', () => {
    test('abandon is idempotent and blocks a later complete', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const prepared = await prepare(ws, bytes, 'image/png')
      await transfer(env.app, prepared.body.upload, bytes)
      const { asset } = prepared.body

      expect((await api.delete(`/workspaces/${ws}/assets/${asset.id}`)).status).toBe(204)
      expect((await api.delete(`/workspaces/${ws}/assets/${asset.id}`)).status).toBe(204)
      expect((await api.get(`/workspaces/${ws}/assets/${asset.id}`)).status).toBe(404)

      const complete = await api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum })
      expect(complete.status).toBe(409)
      expect(complete.body.error.code).toBe('asset_abandoned')

      // storage 清理經 durable job
      expect(await env.db.repos.jobs.findByKey(jobKeys.deleteObject(new URL(prepared.body.upload.url).pathname.replace('/storage/objects/', '')))).not.toBeNull()
    })

    test('abandon after complete is a 409 and does not delete the asset', async () => {
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'image.png')
      const res = await api.delete(`/workspaces/${ws}/assets/${assetId}`)
      expect(res.status).toBe(409)
      expect(res.body.error.code).toBe('asset_not_abandonable')
      await env.runner.drain()
      expect((await api.delete(`/workspaces/${ws}/assets/${assetId}`)).status).toBe(409)
      expect((await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.status).toBe('ready')
    })

    test('concurrent complete and abandon: exactly one wins', async () => {
      for (let i = 0; i < 5; i++) {
        const ws = await newWorkspace()
        const bytes = await readFixture('image.png')
        const prepared = await prepare(ws, bytes, 'image/png')
        await transfer(env.app, prepared.body.upload, bytes)
        const { asset } = prepared.body
        const [complete, abandon] = await Promise.all([
          api.post(`/workspaces/${ws}/assets/${asset.id}/complete`, { byteSize: asset.byteSize, checksum: asset.checksum }),
          api.delete(`/workspaces/${ws}/assets/${asset.id}`),
        ])
        const completeWon = complete.status === 202 && abandon.status === 409
        const abandonWon = abandon.status === 204 && complete.status === 409
        expect(completeWon || abandonWon).toBe(true)
      }
    })

    test('viewers and commenters cannot abandon', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const { asset } = (await prepare(ws, bytes, 'image/png')).body
      const viewerId = await createUser(env.app, 'Viewer')
      await api.put(`/workspaces/${ws}/members/${viewerId}`, { role: 'commenter' })
      expect((await client(env.app, viewerId).delete(`/workspaces/${ws}/assets/${asset.id}`)).status).toBe(403)
    })
  })

  describe('garbage collection', () => {
    test('expires unfinished uploads but never accepted (processing) assets', async () => {
      const ws = await newWorkspace()
      const bytes = await readFixture('image.png')
      const stale = (await prepare(ws, bytes, 'image/png')).body.asset
      const { assetId: processingId } = await uploadAndComplete(ws, 'image.png')
      await sql!`UPDATE assets SET upload_expires_at = now() - interval '1 day' WHERE id IN (${stale.id}, ${processingId})`

      await collectExpiredUploads(env.jobContext)

      expect((await api.get(`/workspaces/${ws}/assets/${stale.id}`)).status).toBe(404)
      expect((await api.get(`/workspaces/${ws}/assets/${processingId}`)).body.status).toBe('pending')
      await env.runner.drain()
      expect((await api.get(`/workspaces/${ws}/assets/${processingId}`)).body.status).toBe('ready')
    })
  })

  describe('worker resilience', () => {
    test('a job whose worker crashed is re-claimed after its lease expires', async () => {
      await env.runner.drain() // 清空其他測試留下的 job
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'image.png')

      const crashed = await env.db.repos.jobs.claim('crashed-worker', 60)
      expect(crashed?.assetId).toBe(assetId)
      // 另一個 worker 在 lease 期間不可領取
      expect(await env.db.repos.jobs.claim('other-worker', 60)).toBeNull()
      await sql!`UPDATE asset_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ${crashed!.id}`

      await env.runner.drain()
      const job = await env.db.repos.jobs.findByKey(jobKeys.verifyOriginal(assetId))
      expect(job).toMatchObject({ status: 'done', attempts: 2 })
      expect((await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.status).toBe('ready')
    })

    test('duplicate thumbnail execution publishes a single derivative', async () => {
      await env.runner.drain()
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'video.mp4')
      // 只執行 verify，保留 thumbnail job
      expect(await env.runner.runOnce()).toBe(true)

      const job = await env.db.repos.jobs.findByKey(jobKeys.thumbnail(assetId, 1))
      expect(job).not.toBeNull()
      await Promise.all([generateThumbnail(job!, env.jobContext), generateThumbnail(job!, env.jobContext)])

      const rows = await sql!`SELECT id FROM assets WHERE source_asset_id = ${assetId}`
      expect(rows).toHaveLength(1)
      expect((await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.thumbnailAssetId).toBe(rows[0]!.id)
    })

    test('parallel workers process each job once', async () => {
      const ws = await newWorkspace()
      const ids = await Promise.all(['image.png', 'image.jpg', 'animation.gif', 'video.webm'].map(async (f) => (await uploadAndComplete(ws, f)).assetId))
      const opts = { concurrency: 1, pollIntervalMs: 10, leaseSeconds: 60, gcIntervalMs: 60_000 }
      const workers = [1, 2, 3].map((n) => new JobRunner(env.jobContext, opts, undefined, `parallel-${n}`))
      await Promise.all(workers.map((w) => w.drain()))
      await env.runner.drain()

      for (const id of ids) expect((await api.get(`/workspaces/${ws}/assets/${id}`)).body.status).toBe('ready')
      const jobs = await sql!`SELECT status, attempts FROM asset_jobs WHERE asset_id = ANY(${ids}::uuid[])`
      expect(jobs.every((j) => j.status === 'done' && j.attempts === 1)).toBe(true)
    })
  })

  describe('immutability and missing content', () => {
    test('a still-valid upload URL cannot overwrite ready content', async () => {
      const ws = await newWorkspace()
      const { prepared, assetId, bytes } = await uploadAndComplete(ws, 'image.png')
      await env.runner.drain()

      const other = await readFixture('image.jpg')
      await transfer(env.app, prepared.body.upload, other.subarray(0, Math.min(other.length, bytes.length)))
      await env.runner.drain()

      const { bytes: downloaded } = await downloadContent(ws, assetId)
      expect(sha256(downloaded)).toBe(sha256(bytes))
    })

    test('ready asset whose object disappeared becomes missing without a download ticket', async () => {
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'image.png')
      await env.runner.drain()
      const asset = (await api.get(`/workspaces/${ws}/assets/${assetId}`)).body
      await env.storage.delete(asset.storageKey)

      const content = await api.get(`/workspaces/${ws}/assets/${assetId}/content`)
      expect(content.status).toBe(409)
      expect(content.body.error.code).toBe('asset_missing')
      expect(content.body).not.toHaveProperty('download')
      expect((await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.status).toBe('missing')
    })
  })

  describe('authorization', () => {
    test('workspace ACL applies to originals and thumbnails; revocation stops new tickets', async () => {
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'animation.gif')
      await env.runner.drain()
      const thumbId = (await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.thumbnailAssetId

      const outsiderId = await createUser(env.app, 'Outsider')
      const outsider = client(env.app, outsiderId)
      const outsiderWs = (await outsider.post('/workspaces', { title: 'Other' })).body.id
      for (const id of [assetId, thumbId]) {
        expect((await outsider.get(`/workspaces/${ws}/assets/${id}`)).status).toBe(404)
        expect((await outsider.get(`/workspaces/${ws}/assets/${id}/content`)).status).toBe(404)
        // 換成自己的 Workspace 路徑也看不到別人的資產
        expect((await outsider.get(`/workspaces/${outsiderWs}/assets/${id}`)).status).toBe(404)
        expect((await outsider.get(`/workspaces/${outsiderWs}/assets/${id}/content`)).status).toBe(404)
      }

      const viewerId = await createUser(env.app, 'Viewer')
      const viewer = client(env.app, viewerId)
      await api.put(`/workspaces/${ws}/members/${viewerId}`, { role: 'viewer' })
      expect((await viewer.get(`/workspaces/${ws}/assets`)).status).toBe(200)
      await downloadContent(ws, thumbId, viewer)

      await api.delete(`/workspaces/${ws}/members/${viewerId}`)
      expect((await viewer.get(`/workspaces/${ws}/assets/${assetId}/content`)).status).toBe(404)
    })
  })

  describe('media node references', () => {
    test('media nodes must reference assets of the same workspace with a matching kind', async () => {
      const ws = await newWorkspace()
      const { assetId } = await uploadAndComplete(ws, 'video.mp4')
      await env.runner.drain()
      const thumbId = (await api.get(`/workspaces/${ws}/assets/${assetId}`)).body.thumbnailAssetId
      const media = (props: Record<string, unknown>) =>
        envelope(0, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'media', properties: props } }])

      const ok = await api.post(`/workspaces/${ws}/operations`, media({ assetId, mediaKind: 'video', altText: '', thumbnailAssetId: thumbId }))
      expect(ok.body.status).toBe('accepted')

      const wrongKind = await api.post(`/workspaces/${ws}/operations`, media({ assetId, mediaKind: 'image' }))
      expect(wrongKind.status).toBe(422)
      expect(wrongKind.body.error.code).toBe('media_kind_mismatch')

      const otherWs = await newWorkspace()
      const foreign = await api.post(`/workspaces/${otherWs}/operations`, media({ assetId, mediaKind: 'video' }))
      expect(foreign.status).toBe(422)
      expect(foreign.body.error.code).toBe('asset_not_found')

      // 缺 assetId 由型別 schema 拒絕（BAI-008）
      const missingRef = await api.post(`/workspaces/${ws}/operations`, media({ mediaKind: 'video' }))
      expect(missingRef.body.error.code).toBe('invalid_object')
    })
  })
})

describe.skipIf(!sql)('storage outage', () => {
  test('storage failures surface as 503 without leaking internals', async () => {
    const failing: ObjectStorage = {
      driver: 'failing',
      presignPut: async () => {
        throw new StorageUnavailableError('connect ECONNREFUSED 10.0.0.5:9000')
      },
      presignGet: async () => {
        throw new StorageUnavailableError('down')
      },
      head: async () => {
        throw new StorageUnavailableError('down')
      },
      getStream: async () => null,
      putFile: async () => {},
      delete: async () => {},
    }
    const env = await createTestEnv(sql as postgres.Sql, { storage: failing })
    try {
      const userId = await createUser(env.app)
      const api = client(env.app, userId)
      const ws = (await api.post('/workspaces', { title: 'Outage' })).body.id
      const res = await api.post(`/workspaces/${ws}/assets`, {
        mediaType: 'image/png',
        byteSize: 10,
        checksum: `sha256:${'0'.repeat(64)}`,
      })
      expect(res.status).toBe(503)
      expect(res.body.error.code).toBe('storage_unavailable')
      expect(JSON.stringify(res.body)).not.toContain('10.0.0.5')
    } finally {
      await env.close()
    }
  })
})
