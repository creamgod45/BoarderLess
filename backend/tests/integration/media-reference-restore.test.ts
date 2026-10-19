import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, hasMediaTools, readFixture, setupTestDatabase, sha256, transfer, uuid, type TestEnv } from '../helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

describe.skipIf(!sql || !hasMediaTools)('media reference restore (batch F)', () => {
  let env: TestEnv
  let owner: string
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    owner = await createUser(env.app, 'Owner')
    api = client(env.app, owner)
  })
  afterAll(async () => {
    await env?.close()
  })

  async function readyAsset(ws: string, file: string, mediaType: string) {
    const bytes = await readFixture(file)
    const prepared = await api.post(`/workspaces/${ws}/assets`, { mediaType, byteSize: bytes.length, checksum: sha256(bytes) })
    await transfer(env.app, prepared.body.upload, bytes)
    await api.post(`/workspaces/${ws}/assets/${prepared.body.asset.id}/complete`, { byteSize: bytes.length, checksum: sha256(bytes) })
    await env.runner.drain()
    return (await api.get(`/workspaces/${ws}/assets/${prepared.body.asset.id}`)).body as { id: string; thumbnailAssetId: string | null; storageKey: string }
  }
  const head = async (ws: string) => (await api.get(`/workspaces/${ws}`)).body.currentVersion as number
  const submit = async (ws: string, ops: any[], as = api) => as.post(`/workspaces/${ws}/operations`, envelope(await head(ws), ops))
  const node = async (ws: string, id: string) => (await api.get(`/workspaces/${ws}/state`)).body.objects.find((o: any) => o.objectId === id)
  const restore = (objectId: string, expectedObjectVersion: number, tx: { transactionId: string; operationId: string }, targetSide: 'before' | 'after') => ({
    kind: 'restore_media_references',
    payload: { objectId, expectedObjectVersion, originalTransactionId: tx.transactionId, originalOperationId: tx.operationId, targetSide },
  })

  /** 建立 MediaNode（引用 A）並以 update_object 換成 B；回傳換素材的原交易身分 */
  async function replaced(ws: string, first: { id: string; thumbnailAssetId: string | null }, second: { id: string; thumbnailAssetId: string | null }, kind: string) {
    const objectId = uuid()
    await submit(ws, [{ kind: 'create_object', payload: { objectId, objectType: 'media', properties: { assetId: first.id, mediaKind: kind, altText: 'keep me', thumbnailAssetId: first.thumbnailAssetId } } }])
    const tx = envelope(await head(ws), [{ kind: 'update_object', payload: { objectId, properties: { assetId: second.id, thumbnailAssetId: second.thumbnailAssetId } } }])
    expect((await api.post(`/workspaces/${ws}/operations`, tx)).body.status).toBe('accepted')
    return { objectId, original: { transactionId: tx.transactionId, operationId: tx.operations[0]!.operationId } }
  }

  test('Undo / Redo of a replacement to a missing original via provenance; ordinary update still requires ready', async () => {
    const ws = (await api.post('/workspaces', { title: 'Restore' })).body.id
    const a = await readyAsset(ws, 'image.png', 'image/png')
    const b = await readyAsset(ws, 'image.webp', 'image/webp')
    const { objectId, original } = await replaced(ws, a, b, 'image')

    // A 變成 missing
    await env.storage.delete(a.storageKey)
    expect((await api.get(`/workspaces/${ws}/assets/${a.id}/content`)).status).toBe(409)

    // 一般 update 不放寬 Ready 規則
    const plain = await submit(ws, [{ kind: 'update_object', payload: { objectId, properties: { assetId: a.id } } }])
    expect(plain.body.error.code).toBe('asset_not_ready')

    const undo = await submit(ws, [restore(objectId, 2, original, 'before')])
    expect(undo.body.status).toBe('accepted')
    expect(undo.body.operations[0].payload).toMatchObject({ restoredReference: { assetId: a.id, thumbnailAssetId: null }, objectVersion: 3 })
    expect((await node(ws, objectId)).properties).toEqual({ assetId: a.id, mediaKind: 'image', altText: 'keep me', thumbnailAssetId: null })
    // 素材不復活
    expect((await api.get(`/workspaces/${ws}/assets/${a.id}`)).body.status).toBe('missing')

    const redo = await submit(ws, [restore(objectId, 3, original, 'after')])
    expect(redo.body.status).toBe('accepted')
    expect((await node(ws, objectId))).toMatchObject({ objectVersion: 4, properties: { assetId: b.id } })
  })

  test('original and thumbnail are restored as a pair', async () => {
    const ws = (await api.post('/workspaces', { title: 'Pair' })).body.id
    const v1 = await readyAsset(ws, 'video.mp4', 'video/mp4')
    const v2 = await readyAsset(ws, 'video.webm', 'video/webm')
    expect(v1.thumbnailAssetId && v2.thumbnailAssetId).toBeTruthy()
    const { objectId, original } = await replaced(ws, v1, v2, 'video')
    await submit(ws, [restore(objectId, 2, original, 'before')])
    expect((await node(ws, objectId)).properties).toMatchObject({ assetId: v1.id, thumbnailAssetId: v1.thumbnailAssetId })
  })

  test('rejections: wrong provenance, foreign actor, object mismatch, stale version, rewritten reference, viewer, client asset ids', async () => {
    const ws = (await api.post('/workspaces', { title: 'Reject' })).body.id
    const a = await readyAsset(ws, 'image.png', 'image/png')
    const b = await readyAsset(ws, 'image.jpg', 'image/jpeg')
    const c = await readyAsset(ws, 'image.webp', 'image/webp')
    const { objectId, original } = await replaced(ws, a, b, 'image')
    const other = await replaced(ws, a, c, 'image')
    const code = async (ops: any[], as = api) => {
      const r = await submit(ws, ops, as)
      return r.body.error?.code ?? r.body.status
    }
    expect(await code([restore(objectId, 2, { ...original, transactionId: uuid() }, 'before')])).toBe('provenance_not_found')
    expect(await code([restore(objectId, 2, { ...original, operationId: uuid() }, 'before')])).toBe('provenance_not_found')
    expect(await code([restore(other.objectId, 2, original, 'before')])).toBe('provenance_mismatch')
    expect(await code([restore(objectId, 1, original, 'before')])).toBe('conflict')
    expect(await code([restore(objectId, 2, original, 'after')])).toBe('media_reference_changed') // 目前已是 after
    expect(await code([{ ...restore(objectId, 2, original, 'before'), payload: { ...restore(objectId, 2, original, 'before').payload, assetId: a.id } }])).toBe('invalid_operation')

    const editor = await createUser(env.app, 'Editor')
    await api.put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
    // 不自動取得其他使用者的 Undo 權限
    expect(await code([restore(objectId, 2, original, 'before')], client(env.app, editor))).toBe('provenance_not_found')
    const viewer = await createUser(env.app, 'Viewer')
    await api.put(`/workspaces/${ws}/members/${viewer}`, { role: 'viewer' })
    expect((await submit(ws, [restore(objectId, 2, original, 'before')], client(env.app, viewer))).status).toBe(403)

    // 之後被另一筆變更改寫：舊 Undo 拒絕
    await submit(ws, [{ kind: 'update_object', payload: { objectId, properties: { assetId: c.id } } }])
    expect(await code([restore(objectId, 3, original, 'before')])).toBe('media_reference_changed')

    // 沒有 provenance 的舊變更：明確 unsupported，不回推
    await sql!`DELETE FROM media_reference_provenance WHERE operation_id = ${other.original.operationId}`
    expect(await code([restore(other.objectId, 2, other.original, 'before')])).toBe('provenance_not_found')
  })
})
