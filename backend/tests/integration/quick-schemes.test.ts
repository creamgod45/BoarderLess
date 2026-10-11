import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, hasMediaTools, readFixture, setupTestDatabase, sha256, transfer, uuid, type TestEnv } from '../helpers.ts'
import { selectionV1, selectionV1Legacy, selectionV4, selectionV7, TEXT_ONLY_SAMPLES } from '../support/selection-samples.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

describe.skipIf(!sql)('quick schemes (batch B)', () => {
  let env: TestEnv
  let owner: string
  let other: string

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    owner = await createUser(env.app, 'Owner')
    other = await createUser(env.app, 'Other')
  })
  afterAll(async () => {
    await env?.close()
  })

  type Res = { status: number; body: any; headers: Record<string, unknown> }
  async function req(method: 'GET' | 'PUT' | 'DELETE', url: string, opts: { as?: string; body?: unknown; raw?: string; headers?: Record<string, string> } = {}): Promise<Res> {
    const res = await env.app.inject({
      method,
      url: `/api/v1/users/me/quick-schemes${url}`,
      headers: { 'x-user-id': opts.as ?? owner, ...(opts.body !== undefined || opts.raw !== undefined ? { 'content-type': 'application/json' } : {}), ...opts.headers },
      ...(opts.raw !== undefined ? { payload: opts.raw } : opts.body !== undefined ? { payload: JSON.stringify(opts.body) } : {}),
    })
    return { status: res.statusCode, body: res.body ? JSON.parse(res.body) : null, headers: res.headers }
  }
  const resource = (payload: any, name = 'Planning card', assetBindings: unknown[] = []) => ({ resourceVersion: 2, name, schemaVersion: payload.version, payload, assetBindings })
  const create = (id: string, body: unknown, key = uuid(), as = owner) => req('PUT', `/${id}`, { as, body, headers: { 'if-none-match': '*', 'idempotency-key': key } })
  const update = (id: string, etag: string, body: unknown, key = uuid(), as = owner) => req('PUT', `/${id}`, { as, body, headers: { 'if-match': etag, 'idempotency-key': key } })
  const remove = (id: string, etag: string, key = uuid(), as = owner) => req('DELETE', `/${id}`, { as, headers: { 'if-match': etag, 'idempotency-key': key } })

  test('capabilities and global flag', async () => {
    const caps = await env.app.inject({ method: 'GET', url: '/api/v1/users/me/quick-scheme-capabilities', headers: { 'x-user-id': owner } })
    expect(JSON.parse(caps.body)).toMatchObject({
      resourceVersions: [2],
      selectionSchemaVersions: [1, 2, 3, 4, 5, 6, 7, 8, 9],
      limits: { maxRequestBytes: 1048576, maxObjects: 1000, maxRelations: 2000, maxSchemesPerUser: 1000, pageLimitMax: 100, pageLimitDefault: 50, nameMaxScalars: 80 },
      idempotencyKeyTtlSeconds: 2592000,
      materializationEnabled: false,
    })
    expect(caps.headers['cache-control']).toBe('private, no-store')
    const global = JSON.parse((await env.app.inject({ method: 'GET', url: '/api/v1/capabilities' })).body)
    expect(global.quickSchemes).toMatchObject({ supported: true, resourceVersions: [2], materializationEnabled: false })
  })

  test('selection v1–v9 (text-only versions) round-trip without losing fields', async () => {
    for (const [version, make] of Object.entries({ ...TEXT_ONLY_SAMPLES, legacy: selectionV1Legacy })) {
      const id = uuid()
      const payload = make()
      const created = await create(id, resource(payload, `v${version}`))
      expect({ version, status: created.status }).toEqual({ version, status: 201 })
      expect(created.headers.etag).toBe(created.body.etag)
      expect(created.body).toMatchObject({ id, ownerId: owner, resourceVersion: 2, schemaVersion: payload.version, revision: 1, assetBindings: [] })
      const got = await req('GET', `/${id}`)
      expect(got.body.payload).toEqual(payload)
      expect(got.headers['cache-control']).toBe('private, no-store')
    }
  })

  test('preconditions: 428 without If-None-Match / If-Match / Idempotency-Key, weak or * ETag', async () => {
    const id = uuid()
    const body = resource(selectionV1())
    expect((await req('PUT', `/${id}`, { body, headers: { 'idempotency-key': uuid() } })).status).toBe(428)
    expect((await req('PUT', `/${id}`, { body, headers: { 'if-none-match': '*' } })).body.error.code).toBe('precondition_required')
    expect((await req('PUT', `/${id}`, { body, headers: { 'if-none-match': '*', 'idempotency-key': 'NOT-A-UUID' } })).body.error.code).toBe('invalid_idempotency_key')
    const created = await create(id, body)
    for (const ifMatch of ['*', `W/${created.body.etag}`, `${created.body.etag}, "x"`]) {
      expect((await update(id, ifMatch, body)).status).toBe(428)
    }
    expect((await req('DELETE', `/${id}`, { headers: { 'idempotency-key': uuid() } })).status).toBe(428)
  })

  test('ETag CAS: stale 412, concurrent updates with the same ETag → exactly one wins', async () => {
    const id = uuid()
    const v1 = await create(id, resource(selectionV1(), 'First'))
    const renamed = await update(id, v1.body.etag, resource(selectionV1(), 'Second'))
    expect([renamed.status, renamed.body.revision, renamed.body.name]).toEqual([200, 2, 'Second'])
    expect(renamed.body.etag).not.toBe(v1.body.etag)
    const stale = await update(id, v1.body.etag, resource(selectionV1(), 'Third'))
    expect([stale.status, stale.body.error.code, stale.body.error.details.revision]).toEqual([412, 'revision_conflict', 2])

    const racers = await Promise.all(Array.from({ length: 6 }, (_, i) => update(id, renamed.body.etag, resource(selectionV1(), `Race ${i}`))))
    expect(racers.filter((r) => r.status === 200)).toHaveLength(1)
    expect(racers.filter((r) => r.status === 412)).toHaveLength(5)
    expect((await req('GET', `/${id}`)).body.revision).toBe(3)
  })

  test('idempotency: same key replays the original result even after later changes; mismatch 409', async () => {
    const id = uuid()
    const key = uuid()
    const body = resource(selectionV1(), 'Idem')
    const first = await create(id, body, key)
    const later = await update(id, first.body.etag, resource(selectionV1(), 'Changed'))
    expect(later.status).toBe(200)
    const replay = await create(id, body, key)
    expect([replay.status, replay.headers.etag, replay.headers['idempotent-replayed']]).toEqual([201, first.body.etag, 'true'])
    expect(replay.body).toEqual(first.body)
    expect((await create(id, resource(selectionV1(), 'Different'), key)).body.error.code).toBe('idempotency_mismatch')
    expect((await create(uuid(), body, key)).body.error.code).toBe('idempotency_mismatch')
    // 同 key 的並行重送只建立一次
    const id2 = uuid()
    const key2 = uuid()
    const results = await Promise.all(Array.from({ length: 5 }, () => create(id2, body, key2)))
    expect(results.every((r) => r.status === 201 && r.body.etag === results[0]!.body.etag)).toBe(true)
  })

  test('delete keeps a tombstone: replay 204, GET 404, no revival by create or stale update', async () => {
    const id = uuid()
    const created = await create(id, resource(selectionV1(), 'Doomed'))
    const key = uuid()
    expect((await remove(id, created.body.etag, key)).status).toBe(204)
    expect((await remove(id, created.body.etag, key)).status).toBe(204) // replay
    expect((await req('GET', `/${id}`)).status).toBe(404)
    const revive = await create(id, resource(selectionV1(), 'Revived'))
    expect([revive.status, revive.body.error.details]).toEqual([412, { exists: true, deleted: true }])
    expect((await update(id, created.body.etag, resource(selectionV1()))).body.error.details).toEqual({ deleted: true })
    expect((await remove(id, created.body.etag)).status).toBe(412)
  })

  test('owner isolation: foreign ids are 404 for every method', async () => {
    const id = uuid()
    const created = await create(id, resource(selectionV1(), 'Mine'))
    expect((await req('GET', `/${id}`, { as: other })).status).toBe(404)
    expect((await create(id, resource(selectionV1()), uuid(), other)).status).toBe(404)
    expect((await update(id, created.body.etag, resource(selectionV1()), uuid(), other)).status).toBe(404)
    expect((await remove(id, created.body.etag, uuid(), other)).status).toBe(404)
    expect((await req('GET', '?limit=100', { as: other })).body.items.map((s: any) => s.id)).not.toContain(id)
  })

  test('schema errors are 422 with APP issue codes; body ownerId / revision rejected', async () => {
    const base = resource(selectionV1())
    const code = async (body: unknown) => {
      const r = await create(uuid(), body)
      return [r.status, r.body.error.code, r.body.error.details?.issue].filter((x) => x !== undefined)
    }
    expect(await code({ ...base, resourceVersion: 3 })).toEqual([422, 'unsupported_resource_version'])
    expect(await code({ ...base, schemaVersion: 10, payload: { ...base.payload, version: 10 } })).toEqual([422, 'unsupported_selection_schema'])
    expect(await code({ ...base, schemaVersion: 2 })).toEqual([422, 'unsupported_selection_schema'])
    expect(await code({ ...base, ownerId: other })).toEqual([422, 'invalid_payload'])
    expect(await code({ ...base, revision: 9 })).toEqual([422, 'invalid_payload'])
    expect(await code({ ...base, payload: { ...base.payload, nodes: [] } })).toEqual([422, 'invalid_payload', 'Empty'])
    const v7 = selectionV7() as any
    v7.nodes[0].text = 'Renamed'
    expect(await code(resource(v7))).toEqual([422, 'invalid_payload', 'InvalidSequence'])
    expect(await code({ ...base, name: '   ' })).toEqual([422, 'invalid_payload'])
    expect(await code({ ...base, name: '😀'.repeat(81) })).toEqual([422, 'invalid_payload'])
    expect((await create(uuid(), { ...base, name: `  ${'😀'.repeat(80)}  ` })).body.name).toBe('😀'.repeat(80))
  })

  test('strict JSON: duplicate keys, lone surrogates, oversized body', async () => {
    const id = uuid()
    const headers = { 'if-none-match': '*', 'idempotency-key': uuid() }
    const dup = await req('PUT', `/${id}`, { raw: '{"resourceVersion":2,"resourceVersion":2}', headers })
    expect([dup.status, dup.body.error.code, dup.body.error.details.reason]).toEqual([400, 'invalid_json', 'duplicate_key'])
    const lone = await req('PUT', `/${id}`, { raw: '{"name":"\\udc00"}', headers })
    expect(lone.body.error.details.reason).toBe('invalid_unicode')
    const huge = await req('PUT', `/${id}`, { raw: JSON.stringify({ ...resource(selectionV1()), name: 'x'.repeat(1_100_000) }), headers })
    expect([huge.status, huge.body.error.code]).toEqual([413, 'payload_too_large'])
  })

  test('quota: at most 1000 active schemes per user', async () => {
    const user = await createUser(env.app, 'Quota')
    expect((await create(uuid(), resource(selectionV1()), uuid(), user)).status).toBe(201)
    await sql!`UPDATE quick_scheme_collections SET active_count = 1000 WHERE owner_id = ${user}`
    const over = await create(uuid(), resource(selectionV1()), uuid(), user)
    expect([over.status, over.body.error.code]).toEqual([409, 'quota_exceeded'])
  })

  test('list: stable snapshot cursor, cursor_expired on change, 304 validators', async () => {
    const user = await createUser(env.app, 'Lister')
    const ids = Array.from({ length: 5 }, () => uuid()).sort()
    const etags: Record<string, string> = {}
    for (const id of ids) etags[id] = (await create(id, resource(selectionV1(), id.slice(0, 8)), uuid(), user)).body.etag

    const page1 = await req('GET', '?limit=2', { as: user })
    expect(page1.body.items.map((s: any) => s.id)).toEqual(ids.slice(0, 2))
    expect(page1.body.collectionRevision).toBe(5)
    const page2 = await req('GET', `?limit=2&cursor=${page1.body.nextCursor}`, { as: user })
    expect(page2.body.items.map((s: any) => s.id)).toEqual(ids.slice(2, 4))
    const page3 = await req('GET', `?limit=2&cursor=${page2.body.nextCursor}`, { as: user })
    expect([page3.body.items.map((s: any) => s.id), page3.body.nextCursor]).toEqual([ids.slice(4), null])

    // 列舉途中有變更：明確 cursor_expired，不讓 client 漏資料
    const restart = await req('GET', '?limit=2', { as: user })
    await update(ids[0]!, etags[ids[0]!]!, resource(selectionV1(), 'changed'), uuid(), user)
    const expired = await req('GET', `?limit=2&cursor=${restart.body.nextCursor}`, { as: user })
    expect([expired.status, expired.body.error.code, expired.body.error.details.collectionRevision]).toEqual([410, 'cursor_expired', 6])
    expect((await req('GET', `?limit=2&cursor=${restart.body.nextCursor}`, { as: other })).body.error.code).toBe('invalid_cursor')
    expect((await req('GET', '?cursor=garbage', { as: user })).status).toBe(400)
    expect((await req('GET', '?limit=101', { as: user })).status).toBe(400)

    const fresh = await req('GET', '', { as: user })
    expect((await req('GET', '', { as: user, headers: { 'if-none-match': String(fresh.headers.etag) } })).status).toBe(304)
    const one = await req('GET', `/${ids[1]}`, { as: user })
    expect((await req('GET', `/${ids[1]}`, { as: user, headers: { 'if-none-match': one.body.etag } })).status).toBe(304)
  })

  test('CORS exposes ETag / Retry-After and allows precondition headers', async () => {
    const preflight = await env.app.inject({
      method: 'OPTIONS',
      url: `/api/v1/users/me/quick-schemes/${uuid()}`,
      headers: { origin: 'https://app.example', 'access-control-request-method': 'PUT', 'access-control-request-headers': 'if-match,idempotency-key,content-type' },
    })
    expect(String(preflight.headers['access-control-allow-headers'])).toContain('idempotency-key')
    const get = await env.app.inject({ method: 'GET', url: '/api/v1/users/me/quick-schemes', headers: { origin: 'https://app.example', 'x-user-id': owner } })
    expect(String(get.headers['access-control-expose-headers'])).toContain('etag')
    expect(String(get.headers['access-control-expose-headers'])).toContain('retry-after')
  })

  describe.skipIf(!hasMediaTools)('asset bindings (v4 media)', () => {
    let ws: string
    let assets: { image: string; video: string; videoThumbnail: string }

    async function readyAsset(workspaceId: string, file: string, mediaType: string) {
      const api = client(env.app, owner)
      const bytes = await readFixture(file)
      const prepared = await api.post(`/workspaces/${workspaceId}/assets`, { mediaType, byteSize: bytes.length, checksum: sha256(bytes) })
      await transfer(env.app, prepared.body.upload, bytes)
      await api.post(`/workspaces/${workspaceId}/assets/${prepared.body.asset.id}/complete`, { byteSize: bytes.length, checksum: sha256(bytes) })
      return prepared.body.asset.id as string
    }

    beforeAll(async () => {
      ws = (await client(env.app, owner).post('/workspaces', { title: 'Scheme source' })).body.id
      const image = await readyAsset(ws, 'image.png', 'image/png')
      const video = await readyAsset(ws, 'video.mp4', 'video/mp4')
      await env.runner.drain()
      const videoThumbnail = (await client(env.app, owner).get(`/workspaces/${ws}/assets/${video}`)).body.thumbnailAssetId
      assets = { image, video, videoThumbnail }
    })

    const bindings = () => [
      { assetId: assets.image, sourceWorkspaceId: ws, kind: 'original' },
      { assetId: assets.video, sourceWorkspaceId: ws, kind: 'original' },
      { assetId: assets.videoThumbnail, sourceWorkspaceId: ws, kind: 'thumbnail' },
    ]

    test('v4 media round-trip with exact bindings, pins and availability', async () => {
      const id = uuid()
      const payload = selectionV4(assets)
      const created = await create(id, resource(payload, 'Media', bindings()))
      expect(created.status).toBe(201)
      expect(created.body.assetAvailability.map((a: any) => a.status)).toEqual(['available', 'available', 'available'])
      expect((await req('GET', `/${id}`)).body.payload).toEqual(payload)
      const pins = await sql!`SELECT asset_id, kind FROM quick_scheme_asset_pins WHERE scheme_id = ${id} ORDER BY kind, asset_id`
      expect(pins).toHaveLength(3)
      await remove(id, created.body.etag)
      expect(await sql!`SELECT 1 FROM quick_scheme_asset_pins WHERE scheme_id = ${id}`).toHaveLength(0)
      // 素材本身不受影響
      expect((await client(env.app, owner).get(`/workspaces/${ws}/assets/${assets.image}`)).body.status).toBe('ready')
    })

    test('missing / extra / fake bindings and wrong kinds are rejected', async () => {
      const payload = selectionV4(assets)
      const code = async (b: unknown[], p: unknown = payload) => (await create(uuid(), resource(p, 'Media', b))).body.error
      expect((await code([])).code).toBe('invalid_asset_binding')
      expect((await code([...bindings(), { assetId: uuid(), sourceWorkspaceId: ws, kind: 'original' }])).code).toBe('invalid_asset_binding')
      expect((await code([...bindings(), bindings()[0]])).code).toBe('invalid_asset_binding')
      const fakeThumb = selectionV4({ ...assets, videoThumbnail: assets.image })
      expect((await code([bindings()[0], bindings()[1], { assetId: assets.image, sourceWorkspaceId: ws, kind: 'thumbnail' }], fakeThumb)).code).toBe('invalid_asset_binding')
      const wrongKind = selectionV4(assets) as any
      wrongKind.media[0].mediaKind = 'video'
      expect((await code(bindings(), wrongKind)).code).toBe('invalid_asset_binding')
    })

    test('source workspace ACL and asset readiness are checked; availability reflects revocation', async () => {
      const payload = selectionV4(assets)
      // other 不是來源 workspace 成員
      expect((await create(uuid(), resource(payload, 'Media', bindings()), uuid(), other)).body.error.code).toBe('asset_access_denied')
      const pendingWs = (await client(env.app, owner).post('/workspaces', { title: 'Pending' })).body.id
      const pending = await readyAsset(pendingWs, 'image.png', 'image/png') // 尚未 drain → pending
      const p = selectionV4({ ...assets, image: pending })
      const b = [{ assetId: pending, sourceWorkspaceId: pendingWs, kind: 'original' }, ...bindings().slice(1)]
      const r = await create(uuid(), resource(p, 'Media', b))
      expect([r.status, r.body.error.code, r.body.error.details.status]).toEqual([409, 'asset_unavailable', 'pending'])

      await client(env.app, owner).put(`/workspaces/${ws}/members/${other}`, { role: 'viewer' })
      const id = uuid()
      const mine = await create(id, resource(payload, 'Shared source', bindings()), uuid(), other)
      expect(mine.status).toBe(201)
      await client(env.app, owner).delete(`/workspaces/${ws}/members/${other}`)
      const after = await req('GET', `/${id}`, { as: other })
      expect(after.body.assetAvailability.every((a: any) => a.status === 'access_denied')).toBe(true)
    })
  })
})
