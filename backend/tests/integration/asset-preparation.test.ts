import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import { spawnSync } from 'node:child_process'
import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type postgres from 'postgres'
import { preparationDigest } from '../../src/domain/asset-preparation.ts'
import { collectExpiredUploads } from '../../src/jobs/upload-gc.ts'
import { LocalObjectStorage } from '../../src/storage/local.storage.ts'
import { StorageUnavailableError, type PresignPutOptions } from '../../src/storage/types.ts'
import {
  client,
  createTestEnv,
  createUser,
  fixture,
  hasMediaTools,
  readFixture,
  sha256,
  setupTestDatabase,
  transfer,
  uuid,
  type TestEnv,
} from '../helpers.ts'
import { jpegWithOrientation } from '../support/exif.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

/** presignPut 可切換成失敗（模擬簽名 / storage 設定錯誤） */
class FlakyStorage extends LocalObjectStorage {
  failPresign = false
  override async presignPut(key: string, options: PresignPutOptions) {
    if (this.failPresign) throw new StorageUnavailableError('presign failed (test)')
    return super.presignPut(key, options)
  }
}

type Api = ReturnType<typeof client>

const metadataOf = (bytes: Uint8Array, mediaType: string, extra: Partial<Record<'width' | 'height' | 'durationMs', number | null>> = {}) => ({
  byteSize: bytes.length,
  checksum: sha256(bytes),
  durationMs: null,
  height: null,
  mediaType,
  width: null,
  ...extra,
})

const prepareBody = (requestId: string, metadata: object) => ({
  schema: 'boarderless.asset-prepare.v1',
  requestId,
  metadata,
})

describe.skipIf(!sql || !hasMediaTools)('asset preparation v2', () => {
  let env: TestEnv
  let storage: FlakyStorage
  let storageDir: string
  let ownerId: string
  let api: Api

  beforeAll(async () => {
    storageDir = await mkdtemp(join(tmpdir(), 'boarderless-prep-'))
    storage = new FlakyStorage(storageDir, 'http://storage.test', 'test-secret')
    env = await createTestEnv(sql as postgres.Sql, { storage })
    ownerId = await createUser(env.app, 'Owner')
    api = client(env.app, ownerId)
  })

  afterAll(async () => {
    await env?.close()
    await rm(storageDir, { recursive: true, force: true })
  })

  const newWorkspace = async () => (await api.post('/workspaces', { title: 'Prep v2' })).body.id as string
  const base = (ws: string) => `/workspaces/${ws}/assets/preparations`
  const prepare = (ws: string, requestId: string, metadata: object, as = api) =>
    as.post(base(ws), prepareBody(requestId, metadata))
  const ticket = (ws: string, requestId: string, ticketRequestId = uuid(), as = api) =>
    as.post(`${base(ws)}/${requestId}/upload-tickets`, { ticketRequestId })
  const complete = (ws: string, requestId: string, leaseId: string, meta: { byteSize: number; checksum: string }, as = api) =>
    as.post(`${base(ws)}/${requestId}/complete`, { requestId, leaseId, byteSize: meta.byteSize, checksum: meta.checksum })
  const assetCount = async (ws: string) =>
    (await sql!<{ n: number }[]>`SELECT count(*)::int AS n FROM assets WHERE workspace_id = ${ws}`)[0]!.n

  /** prepare → ticket → PUT → complete，回傳各步驟 */
  async function upload(ws: string, file: string, body?: Uint8Array, declared: Parameters<typeof metadataOf>[2] = {}) {
    const bytes = await readFixture(file)
    const meta = metadataOf(bytes, fixture(file).mediaType, declared)
    const requestId = uuid()
    const prepared = await prepare(ws, requestId, meta)
    expect(prepared.status).toBe(201)
    const issued = await ticket(ws, requestId)
    expect(issued.status).toBe(201)
    expect((await transfer(env.app, issued.body.upload, body ?? bytes)).status).toBe(200)
    const completed = await complete(ws, requestId, issued.body.upload.leaseId, meta)
    return { requestId, meta, bytes, prepared, issued, completed, assetId: prepared.body.asset.id as string }
  }

  /** 以任意 bytes 走完整 v2 流程並等 worker 完成，回傳最終 receipt */
  async function uploadBytes(ws: string, bytes: Uint8Array, mediaType: string, declared: Parameters<typeof metadataOf>[2] = {}) {
    const meta = metadataOf(bytes, mediaType, declared)
    const requestId = uuid()
    expect((await prepare(ws, requestId, meta)).status).toBe(201)
    const issued = await ticket(ws, requestId)
    expect((await transfer(env.app, issued.body.upload, bytes)).status).toBe(200)
    expect((await complete(ws, requestId, issued.body.upload.leaseId, meta)).status).toBe(202)
    await env.runner.drain()
    return (await api.get(`${base(ws)}/${requestId}`)).body
  }

  test('golden vector digest matches the APP serialization', () => {
    const digest = preparationDigest({
      byteSize: 3,
      checksum: `sha256:${'b'.repeat(64)}`,
      durationMs: null,
      height: 1,
      mediaType: 'image/png',
      width: 1,
    })
    expect(digest).toBe('32d5f0d51e3a30dc0c7057664dc28914132cf09fe6e109098725c05d26f602ee')
  })

  test('first prepare 201, identical retry 200 with the same identity, changed metadata 409 without new asset', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png', { width: 32, height: 24 })
    const requestId = uuid()

    const first = await prepare(ws, requestId, meta)
    expect(first.status).toBe(201)
    expect(first.body).toMatchObject({
      schema: 'boarderless.asset-preparation.v1',
      requestId,
      requestDigest: preparationDigest(meta),
      phase: 'prepared',
      lease: null,
      asset: { status: 'pending', ownerId, workspaceId: ws, byteSize: bytes.length, width: 32, height: 24, durationMs: null },
    })
    expect(first.body.upload).toBeUndefined()
    expect(first.body.asset.id).not.toBe(requestId)

    const retry = await prepare(ws, requestId, meta)
    expect(retry.status).toBe(200)
    expect(retry.body.asset.id).toBe(first.body.asset.id)
    expect(retry.body.requestDigest).toBe(first.body.requestDigest)

    const changed = await prepare(ws, requestId, { ...meta, width: 33 })
    expect(changed.status).toBe(409)
    expect(changed.body.error.code).toBe('preparation_mismatch')
    expect(changed.body.error.details.requestDigest).toBe(first.body.requestDigest)
    expect(await assetCount(ws)).toBe(1)
  })

  test('concurrent identical prepares create exactly one asset', async () => {
    const ws = await newWorkspace()
    const meta = metadataOf(await readFixture('image.png'), 'image/png')
    const requestId = uuid()
    const results = await Promise.all(Array.from({ length: 12 }, () => prepare(ws, requestId, meta)))
    expect(results.filter((r) => r.status === 201)).toHaveLength(1)
    expect(results.filter((r) => r.status === 200)).toHaveLength(11)
    expect(new Set(results.map((r) => r.body.asset.id)).size).toBe(1)
    expect(await assetCount(ws)).toBe(1)
  })

  test('same requestId is scoped per user and per workspace', async () => {
    const [ws1, ws2] = [await newWorkspace(), await newWorkspace()]
    const editorId = await createUser(env.app, 'Editor')
    await api.put(`/workspaces/${ws1}/members/${editorId}`, { role: 'editor' })
    const editor = client(env.app, editorId)
    const meta = metadataOf(await readFixture('image.png'), 'image/png')
    const requestId = uuid()

    const mine = await prepare(ws1, requestId, meta)
    const theirs = await prepare(ws1, requestId, meta, editor)
    const otherWs = await prepare(ws2, requestId, meta)
    expect([mine.status, theirs.status, otherWs.status]).toEqual([201, 201, 201])
    expect(new Set([mine.body.asset.id, theirs.body.asset.id, otherWs.body.asset.id]).size).toBe(3)
    expect(theirs.body.asset.ownerId).toBe(editorId)
  })

  test('strict body validation: unknown / missing fields, coercion, non-canonical tokens', async () => {
    const ws = await newWorkspace()
    const meta = metadataOf(await readFixture('image.png'), 'image/png')
    const bad: [string, unknown][] = [
      ['unknown top-level field', { ...prepareBody(uuid(), meta), ownerId: ownerId }],
      ['unknown metadata field', prepareBody(uuid(), { ...meta, localId: uuid() })],
      ['missing nullable field', prepareBody(uuid(), { ...meta, durationMs: undefined })],
      ['string number', prepareBody(uuid(), { ...meta, byteSize: String(meta.byteSize) })],
      ['uppercase requestId', prepareBody(uuid().toUpperCase(), meta)],
      ['uppercase checksum', prepareBody(uuid(), { ...meta, checksum: meta.checksum.toUpperCase().replace('SHA256', 'sha256') })],
      ['wrong schema', { ...prepareBody(uuid(), meta), schema: 'boarderless.asset-prepare.v2' }],
      ['zero width', prepareBody(uuid(), { ...meta, width: 0 })],
      ['unsafe duration', prepareBody(uuid(), { ...meta, durationMs: 9007199254740992 })],
      ['fractional height', prepareBody(uuid(), { ...meta, height: 1.5 })],
    ]
    for (const [label, body] of bad) {
      const res = await api.post(base(ws), body)
      expect({ label, status: res.status, code: res.body.error?.code }).toEqual({ label, status: 400, code: 'validation_error' })
    }
    expect((await prepare(ws, uuid(), { ...meta, mediaType: 'IMAGE/PNG' })).status).toBe(415)
    expect((await prepare(ws, uuid(), { ...meta, mediaType: 'image/svg+xml' })).status).toBe(415)
    expect((await prepare(ws, uuid(), { ...meta, byteSize: 209715201 })).status).toBe(413)
    expect((await prepare(ws, uuid(), { ...meta, width: 2147483648 })).status).toBe(400)
    const maxed = await prepare(ws, uuid(), { ...meta, durationMs: 9007199254740991, width: 2147483647 })
    expect([maxed.status, maxed.body.asset.durationMs]).toEqual([201, 9007199254740991])
    expect(await assetCount(ws)).toBe(1)
  })

  test('receipt lookup: 200 own, 404 unknown / other actor / other workspace / revoked, 403 demoted', async () => {
    const [ws, other] = [await newWorkspace(), await newWorkspace()]
    const editorId = await createUser(env.app, 'Editor')
    await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'editor' })
    await api.put(`/workspaces/${other}/members/${editorId}`, { role: 'editor' })
    const editor = client(env.app, editorId)
    const requestId = uuid()
    const prepared = await prepare(ws, requestId, metadataOf(await readFixture('image.png'), 'image/png'), editor)

    const own = await editor.get(`${base(ws)}/${requestId}`)
    expect(own.status).toBe(200)
    expect(own.body.asset.id).toBe(prepared.body.asset.id)

    const unknown = await editor.get(`${base(ws)}/${uuid()}`)
    expect([unknown.status, unknown.body.error.code]).toEqual([404, 'preparation_not_found'])
    // 他人不可查到（owner 也不行），也不洩漏存在與否
    const otherActor = await api.get(`${base(ws)}/${requestId}`)
    expect([otherActor.status, otherActor.body.error.code]).toEqual([404, 'preparation_not_found'])
    expect((await editor.get(`${base(other)}/${requestId}`)).status).toBe(404)

    await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'viewer' })
    expect((await editor.get(`${base(ws)}/${requestId}`)).status).toBe(403)
    await api.delete(`/workspaces/${ws}/members/${editorId}`)
    expect((await editor.get(`${base(ws)}/${requestId}`)).status).toBe(404)
    expect((await prepare(ws, requestId, metadataOf(await readFixture('image.png'), 'image/png'), editor)).status).toBe(404)
  })

  test('lost prepare response and API restart: identity is recoverable by requestId', async () => {
    const ws = await newWorkspace()
    const meta = metadataOf(await readFixture('image.png'), 'image/png')
    const requestId = uuid()
    await prepare(ws, requestId, meta) // 回覆遺失：APP 不知道 asset ID

    // 新的連線池 + 新的 app instance（模擬 API / DB 連線重啟）
    const sql2 = (await setupTestDatabase())!
    const env2 = await createTestEnv(sql2)
    try {
      const api2 = client(env2.app, ownerId)
      const found = await api2.get(`${base(ws)}/${requestId}`)
      expect(found.status).toBe(200)
      const retried = await api2.post(base(ws), prepareBody(requestId, meta))
      expect(retried.status).toBe(200)
      expect(retried.body.asset.id).toBe(found.body.asset.id)
    } finally {
      await env2.close()
      await sql2.end()
    }
    expect(await assetCount(ws)).toBe(1)
  })

  test('presign failure keeps the reservation and the durable lease; retry returns the same lease', async () => {
    const ws = await newWorkspace()
    const requestId = uuid()
    await prepare(ws, requestId, metadataOf(await readFixture('image.png'), 'image/png'))
    const ticketRequestId = uuid()

    storage.failPresign = true
    try {
      const failed = await ticket(ws, requestId, ticketRequestId)
      expect([failed.status, failed.body.error.code]).toEqual([503, 'storage_unavailable'])
    } finally {
      storage.failPresign = false
    }

    const found = await api.get(`${base(ws)}/${requestId}`)
    expect(found.body.phase).toBe('prepared')
    expect(found.body.lease).toMatchObject({ ticketRequestId, generation: 1 })

    const retried = await ticket(ws, requestId, ticketRequestId)
    expect(retried.status).toBe(200)
    expect(retried.body.upload.leaseId).toBe(found.body.lease.leaseId)
    expect(retried.body.lease.generation).toBe(1)
  })

  test('full flow: ticket → PUT → complete 202 → ready; retries do not rebuild work; ready forbids PUT', async () => {
    const ws = await newWorkspace()
    const flow = await upload(ws, 'image.png')
    expect(flow.issued.body).toMatchObject({ phase: 'prepared', lease: { generation: 1 }, upload: { method: 'PUT' } })
    expect(flow.issued.body.upload.headers).toEqual({ 'Content-Type': 'image/png' })
    expect(flow.completed.status).toBe(202)
    expect(flow.completed.body.phase).toBe('completionAccepted')
    expect(flow.completed.body.asset.status).toBe('pending')
    const leaseId = flow.issued.body.upload.leaseId

    const again = await complete(ws, flow.requestId, leaseId, flow.meta)
    expect([again.status, again.body.phase]).toEqual([202, 'completionAccepted'])
    const jobs = await sql!`SELECT 1 FROM asset_jobs WHERE asset_id = ${flow.assetId} AND job_type = 'verify_original'`
    expect(jobs).toHaveLength(1)
    // 已接受後不可再取得 PUT
    expect((await ticket(ws, flow.requestId)).body.error.code).toBe('upload_not_allowed')

    await env.runner.drain()
    const ready = await api.get(`${base(ws)}/${flow.requestId}`)
    expect(ready.body).toMatchObject({ phase: 'ready', asset: { status: 'ready', width: 64, height: 48 }, lease: { leaseId } })
    const blocked = await ticket(ws, flow.requestId)
    expect([blocked.status, blocked.body.error.code, blocked.body.error.details.phase]).toEqual([409, 'upload_not_allowed', 'ready'])
    const doneRetry = await complete(ws, flow.requestId, leaseId, flow.meta)
    expect([doneRetry.status, doneRetry.body.phase]).toEqual([200, 'ready'])

    // ready original 可簽 GET，內容與 checksum 一致
    const content = await api.get(`/workspaces/${ws}/assets/${flow.assetId}/content`)
    expect(sha256((await transfer(env.app, content.body.download)).bytes)).toBe(flow.meta.checksum)
  })

  test('lease idempotency, expiry and supersession', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    const requestId = uuid()
    await prepare(ws, requestId, meta)

    const t1 = uuid()
    const first = await ticket(ws, requestId, t1)
    const retry = await ticket(ws, requestId, t1)
    expect([first.status, retry.status]).toEqual([201, 200])
    expect(retry.body.upload.leaseId).toBe(first.body.upload.leaseId)
    expect(retry.body.lease).toEqual(first.body.lease)
    const lease1 = first.body.upload.leaseId

    // 過期：同 ticketRequestId 不自動 renew
    await sql!`UPDATE asset_upload_leases SET expires_at = now() - interval '1 second' WHERE lease_id = ${lease1}`
    const expired = await ticket(ws, requestId, t1)
    expect([expired.status, expired.body.error.code, expired.body.error.details.leaseId]).toEqual([409, 'upload_lease_expired', lease1])

    // 新的 ticketRequestId → generation 2，舊 lease 被取代
    const second = await ticket(ws, requestId, uuid())
    expect([second.status, second.body.lease.generation]).toEqual([201, 2])
    const lease2 = second.body.upload.leaseId
    expect(lease2).not.toBe(lease1)
    expect((await ticket(ws, requestId, t1)).body.error.code).toBe('upload_lease_superseded')

    // 舊 lease 已上傳也不能成為 original
    await transfer(env.app, first.body.upload, bytes).catch(() => {}) // 已過期：403
    const viaOld = await complete(ws, requestId, lease1, meta)
    expect([viaOld.status, viaOld.body.error.code]).toEqual([409, 'upload_lease_superseded'])
    const wrong = await complete(ws, requestId, uuid(), meta)
    expect([wrong.status, wrong.body.error.code]).toEqual([409, 'upload_lease_mismatch'])
    const notUploaded = await complete(ws, requestId, lease2, meta)
    expect([notUploaded.status, notUploaded.body.error.code]).toEqual([409, 'upload_not_found'])
    const mismatch = await complete(ws, requestId, lease2, { ...meta, checksum: `sha256:${'f'.repeat(64)}` })
    expect([mismatch.status, mismatch.body.error.code]).toEqual([409, 'completion_mismatch'])
    const pathMismatch = await api.post(`${base(ws)}/${requestId}/complete`, {
      requestId: uuid(),
      leaseId: lease2,
      byteSize: meta.byteSize,
      checksum: meta.checksum,
    })
    expect([pathMismatch.status, pathMismatch.body.error.code]).toEqual([400, 'request_id_mismatch'])

    expect((await transfer(env.app, second.body.upload, bytes)).status).toBe(200)
    const accepted = await complete(ws, requestId, lease2, meta)
    expect([accepted.status, accepted.body.lease.leaseId]).toEqual([202, lease2])
    // 接受後，其他 lease 一律 409
    expect((await complete(ws, requestId, lease1, meta)).body.error.code).toBe('upload_lease_mismatch')
  })

  test('complete vs renew race: exactly one side wins', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    for (let i = 0; i < 6; i++) {
      const requestId = uuid()
      await prepare(ws, requestId, meta)
      const issued = await ticket(ws, requestId)
      await transfer(env.app, issued.body.upload, bytes)
      const [completed, renewed] = await Promise.all([
        complete(ws, requestId, issued.body.upload.leaseId, meta),
        ticket(ws, requestId),
      ])
      const outcome = `${completed.status}/${completed.body.error?.code ?? completed.body.phase}|${renewed.status}/${renewed.body.error?.code ?? renewed.body.phase}`
      expect([
        '202/completionAccepted|409/upload_not_allowed',
        '409/upload_lease_superseded|201/prepared',
      ]).toContain(outcome)
    }
  })

  test('concurrent completes of the same lease enqueue a single verify job', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    const requestId = uuid()
    const prepared = await prepare(ws, requestId, meta)
    const issued = await ticket(ws, requestId)
    await transfer(env.app, issued.body.upload, bytes)
    const results = await Promise.all(Array.from({ length: 8 }, () => complete(ws, requestId, issued.body.upload.leaseId, meta)))
    expect(results.every((r) => r.status === 202)).toBe(true)
    const jobs = await sql!`SELECT 1 FROM asset_jobs WHERE asset_id = ${prepared.body.asset.id}`
    expect(jobs).toHaveLength(1)
  })

  test('a still-valid lease PUT cannot rewrite the ready original', async () => {
    const ws = await newWorkspace()
    const flow = await upload(ws, 'image.png')
    await env.runner.drain()
    const other = await readFixture('image.jpg')
    // staging key 仍可被有效簽名寫入，但 ready original 已固定在不可變 key
    await transfer(env.app, flow.issued.body.upload, other, { 'content-type': 'image/png' })
    await env.runner.drain()
    const content = await api.get(`/workspaces/${ws}/assets/${flow.assetId}/content`)
    expect(sha256((await transfer(env.app, content.body.download)).bytes)).toBe(flow.meta.checksum)
    expect((await api.get(`${base(ws)}/${flow.requestId}`)).body.phase).toBe('ready')
  })

  test('source content changed after prepare → rejected; same requestId cannot be re-prepared with new metadata', async () => {
    const ws = await newWorkspace()
    const changed = Buffer.from(await readFixture('image.png'))
    changed[changed.length - 20]! ^= 0xff
    const flow = await upload(ws, 'image.png', changed)
    expect(flow.completed.status).toBe(202)
    await env.runner.drain()
    const rejected = await api.get(`${base(ws)}/${flow.requestId}`)
    expect(rejected.body).toMatchObject({ phase: 'rejected', asset: { status: 'rejected', rejectionReason: 'checksum_mismatch' } })
    const reprepare = await prepare(ws, flow.requestId, metadataOf(changed, 'image/png'))
    expect(reprepare.body.error.code).toBe('preparation_mismatch')
    expect((await ticket(ws, flow.requestId)).body.error.details.phase).toBe('rejected')
  })

  test('missing original keeps the identity with phase missing', async () => {
    const ws = await newWorkspace()
    const flow = await upload(ws, 'image.webp')
    await env.runner.drain()
    await env.storage.delete(flow.prepared.body.asset.storageKey)
    expect((await api.get(`/workspaces/${ws}/assets/${flow.assetId}/content`)).status).toBe(409)
    const missing = await api.get(`${base(ws)}/${flow.requestId}`)
    expect(missing.body).toMatchObject({ phase: 'missing', asset: { id: flow.assetId, status: 'missing' } })
  })

  test('abandoned tombstone: identity kept, late original request does not revive or rebuild', async () => {
    const ws = await newWorkspace()
    const meta = metadataOf(await readFixture('image.png'), 'image/png')
    const requestId = uuid()
    const prepared = await prepare(ws, requestId, meta)
    const issued = await ticket(ws, requestId)
    expect((await api.delete(`/workspaces/${ws}/assets/${prepared.body.asset.id}`)).status).toBe(204)

    const late = await prepare(ws, requestId, meta)
    expect([late.status, late.body.phase, late.body.asset.id]).toEqual([200, 'abandoned', prepared.body.asset.id])
    expect((await api.get(`${base(ws)}/${requestId}`)).body.phase).toBe('abandoned')
    expect((await ticket(ws, requestId)).body.error.details.phase).toBe('abandoned')
    expect((await complete(ws, requestId, issued.body.upload.leaseId, meta)).body.error.code).toBe('asset_abandoned')
    expect(await assetCount(ws)).toBe(1)
  })

  test('revoked or demoted actor cannot ticket or complete', async () => {
    const ws = await newWorkspace()
    const editorId = await createUser(env.app, 'Editor')
    await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'editor' })
    const editor = client(env.app, editorId)
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    const requestId = uuid()
    await prepare(ws, requestId, meta, editor)
    const issued = await ticket(ws, requestId, uuid(), editor)
    await transfer(env.app, issued.body.upload, bytes)

    await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'viewer' })
    expect((await ticket(ws, requestId, uuid(), editor)).status).toBe(403)
    expect((await complete(ws, requestId, issued.body.upload.leaseId, meta, editor)).status).toBe(403)
    await api.delete(`/workspaces/${ws}/members/${editorId}`)
    expect((await complete(ws, requestId, issued.body.upload.leaseId, meta, editor)).status).toBe(404)
  })

  test('v1 complete cannot bypass the v2 lease', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    const requestId = uuid()
    const prepared = await prepare(ws, requestId, meta)
    const issued = await ticket(ws, requestId)
    await transfer(env.app, issued.body.upload, bytes)
    const v1 = await api.post(`/workspaces/${ws}/assets/${prepared.body.asset.id}/complete`, {
      byteSize: meta.byteSize,
      checksum: meta.checksum,
    })
    expect([v1.status, v1.body.error.code]).toEqual([409, 'preparation_v2_required'])
  })

  test('GC collects expired lease staging but never expires the v2 reservation', async () => {
    const ws = await newWorkspace()
    const bytes = await readFixture('image.png')
    const meta = metadataOf(bytes, 'image/png')
    const requestId = uuid()
    await prepare(ws, requestId, meta)
    const issued = await ticket(ws, requestId)
    await transfer(env.app, issued.body.upload, bytes)
    const leaseId = issued.body.upload.leaseId
    await sql!`UPDATE asset_upload_leases SET expires_at = now() - interval '1 hour' WHERE lease_id = ${leaseId}`

    // 測試 DB 會累積先前執行留下的過期 lease；GC 每批 100 筆依到期順序處理，執行到沒有可回收的為止
    while ((await collectExpiredUploads(env.jobContext)) > 0) {}
    await env.runner.drain()
    const [lease] = await sql!`SELECT collected_at, upload_key FROM asset_upload_leases WHERE lease_id = ${leaseId}`
    expect(lease!.collectedAt).not.toBeNull()
    expect(await storage.head(lease!.uploadKey)).toBeNull()

    expect((await api.get(`${base(ws)}/${requestId}`)).body.phase).toBe('prepared')
    expect((await complete(ws, requestId, leaseId, meta)).body.error.code).toBe('upload_lease_expired')
    // 新票券仍可簽發（新 generation）
    const renewed = await ticket(ws, requestId)
    expect([renewed.status, renewed.body.lease.generation]).toEqual([201, 2])
  })

  test('accepted lease is not collected by GC even after it expires', async () => {
    const ws = await newWorkspace()
    const flow = await upload(ws, 'image.png')
    await sql!`UPDATE asset_upload_leases SET expires_at = now() - interval '1 hour' WHERE asset_id = ${flow.assetId}`
    while ((await collectExpiredUploads(env.jobContext)) > 0) {}
    const [lease] = await sql!`SELECT collected_at FROM asset_upload_leases WHERE asset_id = ${flow.assetId}`
    expect(lease!.collectedAt).toBeNull()
    await env.runner.drain()
    expect((await api.get(`${base(ws)}/${flow.requestId}`)).body.phase).toBe('ready')
  })

  test('capabilities declare preparation v2', async () => {
    const res = await client(env.app).get('/capabilities')
    expect(res.status).toBe(200)
    expect(res.body.assetPreparation).toMatchObject({
      requestSchema: 'boarderless.asset-prepare.v1',
      responseSchema: 'boarderless.asset-preparation.v1',
    })
  })

  describe('declared vs verified (ASSET-08)', () => {
    test('declared null: not compared, stays null; verified carries the parsed values', async () => {
      const ws = await newWorkspace()
      const receipt = await uploadBytes(ws, await readFixture('image.png'), 'image/png')
      expect(receipt).toMatchObject({
        phase: 'ready',
        declared: { width: null, height: null, durationMs: null },
        verified: { width: 64, height: 48, durationMs: null },
        asset: { status: 'ready', width: 64, height: 48 },
      })
      expect(receipt.requestDigest).toBe(preparationDigest(receipt.declared))
    })

    test('matching declared size → ready; declared is never rewritten', async () => {
      const ws = await newWorkspace()
      const receipt = await uploadBytes(ws, await readFixture('image.png'), 'image/png', { width: 64, height: 48 })
      expect(receipt).toMatchObject({ phase: 'ready', declared: { width: 64, height: 48 }, verified: { width: 64, height: 48 } })
    })

    test('declared size differs → rejected declared_metadata_mismatch, verified kept for diagnosis', async () => {
      const ws = await newWorkspace()
      const receipt = await uploadBytes(ws, await readFixture('image.png'), 'image/png', { width: 32, height: 24 })
      expect(receipt).toMatchObject({
        phase: 'rejected',
        declared: { width: 32, height: 24 },
        verified: { width: 64, height: 48, durationMs: null },
        asset: { status: 'rejected', rejectionReason: 'declared_metadata_mismatch' },
      })
      // 不簽發下載、不寫入不可變 original
      expect((await api.get(`/workspaces/${ws}/assets/${receipt.asset.id}/content`)).status).toBe(409)
      expect(await storage.head(receipt.asset.storageKey)).toBeNull()
    })

    test('declared duration on a still image → rejected', async () => {
      const ws = await newWorkspace()
      const receipt = await uploadBytes(ws, await readFixture('image.png'), 'image/png', { durationMs: 0 })
      expect(receipt.asset.rejectionReason).toBe('declared_metadata_mismatch')
    })

    test('video duration within max(100ms, 1%) passes, beyond is rejected', async () => {
      const ws = await newWorkspace()
      const video = fixture('video.mp4')
      const bytes = await readFixture('video.mp4')
      const within = await uploadBytes(ws, bytes, 'video/mp4', { durationMs: video.durationMs! + 100 })
      expect(within).toMatchObject({ phase: 'ready', verified: { durationMs: video.durationMs } })
      const beyond = await uploadBytes(ws, bytes, 'video/mp4', { durationMs: video.durationMs! + 101 + Math.ceil(video.durationMs! * 0.01) })
      expect([beyond.phase, beyond.asset.rejectionReason]).toEqual(['rejected', 'declared_metadata_mismatch'])
    })

    test('EXIF orientation 6 JPEG: verified is the display size (width / height swapped)', async () => {
      const ws = await newWorkspace()
      const rotated = jpegWithOrientation(await readFixture('image.jpg'), 6)
      const stored = await uploadBytes(ws, rotated, 'image/jpeg', { width: 64, height: 48 })
      expect([stored.phase, stored.asset.rejectionReason, stored.verified]).toEqual([
        'rejected',
        'declared_metadata_mismatch',
        { width: 48, height: 64, durationMs: null },
      ])
      const display = await uploadBytes(ws, rotated, 'image/jpeg', { width: 48, height: 64 })
      expect(display).toMatchObject({ phase: 'ready', verified: { width: 48, height: 64 }, asset: { width: 48, height: 64 } })
    })

    test('video display-matrix rotation 90: verified is the display size', async () => {
      const dir = await mkdtemp(join(tmpdir(), 'rotated-'))
      try {
        const out = join(dir, 'rotated.mp4')
        const source = join(import.meta.dirname, '../fixtures/media/video.mp4')
        const made = spawnSync('ffmpeg', ['-v', 'error', '-y', '-display_rotation', '90', '-i', source, '-c', 'copy', out])
        if (made.status !== 0) return console.warn('[asset-08] ffmpeg 不支援 -display_rotation，略過旋轉影片案例')
        const video = fixture('video.mp4')
        const ws = await newWorkspace()
        const receipt = await uploadBytes(ws, await readFile(out), 'video/mp4', { width: video.height!, height: video.width! })
        expect(receipt).toMatchObject({ phase: 'ready', verified: { width: video.height, height: video.width } })
      } finally {
        await rm(dir, { recursive: true, force: true })
      }
    })
  })
})
