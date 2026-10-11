/**
 * 錄製批次 F fixtures：restore_media_references（provenance Undo / Redo、成對縮圖、各種拒絕）。
 * 需要測試 PostgreSQL 與 ffmpeg。用法：bun run fixtures:media-restore
 */
import { join } from 'node:path'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, readFixture, setupTestDatabase, sha256, transfer, uuid } from '../tests/helpers.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/media-restore-v1')
const sql = (await setupTestDatabase()) as postgres.Sql
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const env = await createTestEnv(sql)
const recorder = new FixtureRecorder(OUT)
await recorder.reset()
const owner = await createUser(env.app, 'Owner')
const api = client(env.app, owner)
const ws = (await api.post('/workspaces', { title: 'Media restore fixtures' })).body.id
recorder.normId(owner)
recorder.normId(ws)
const opsPath = `/api/v1/workspaces/${ws}/operations`
const head = async () => (await api.get(`/workspaces/${ws}`)).body.currentVersion
async function submit(name: string, description: string, ops: any[], as = api) {
  const request = envelope(await head(), ops)
  const res = await as.post(`/workspaces/${ws}/operations`, request)
  await recorder.record(name, description, { method: 'POST', path: opsPath, body: request }, res)
  return { res, request }
}
async function readyAsset(file: string, mediaType: string) {
  const bytes = await readFixture(file)
  const prepared = await api.post(`/workspaces/${ws}/assets`, { mediaType, byteSize: bytes.length, checksum: sha256(bytes) })
  await transfer(env.app, prepared.body.upload, bytes)
  await api.post(`/workspaces/${ws}/assets/${prepared.body.asset.id}/complete`, { byteSize: bytes.length, checksum: sha256(bytes) })
  await env.runner.drain()
  return (await api.get(`/workspaces/${ws}/assets/${prepared.body.asset.id}`)).body
}
const restore = (objectId: string, expectedObjectVersion: number, original: { transactionId: string; operationId: string }, targetSide: string) => ({
  kind: 'restore_media_references',
  payload: { objectId, expectedObjectVersion, originalTransactionId: original.transactionId, originalOperationId: original.operationId, targetSide },
})

const a = await readyAsset('image.png', 'image/png')
const b = await readyAsset('image.webp', 'image/webp')
const objectId = uuid()
await submit('01-create-media-node', 'MediaNode 引用 A（ready）', [{ kind: 'create_object', payload: { objectId, objectType: 'media', properties: { assetId: a.id, mediaKind: 'image', altText: 'keep me', thumbnailAssetId: null } } }])
const replace = await submit('02-replace-asset', '換素材 A → B（server 同交易保存 before / after provenance）', [{ kind: 'update_object', payload: { objectId, properties: { assetId: b.id } } }])
const original = { transactionId: replace.request.transactionId, operationId: replace.request.operations[0]!.operationId }
await env.storage.delete(a.storageKey)
await api.get(`/workspaces/${ws}/assets/${a.id}/content`) // A → missing
await submit('03-plain-update-to-missing-422', '一般 update_object 換回 missing 的 A：仍要求 Ready（asset_not_ready）', [{ kind: 'update_object', payload: { objectId, properties: { assetId: a.id } } }])
await submit('04-restore-before', 'Undo：restore_media_references targetSide=before（不提交 assetId；由 provenance 取 A），objectVersion +1，素材不復活', [restore(objectId, 2, original, 'before')])
await submit('05-restore-after', 'Redo：targetSide=after', [restore(objectId, 3, original, 'after')])
await submit('06-wrong-transaction-422', 'originalTransactionId 不符：provenance_not_found', [restore(objectId, 4, { ...original, transactionId: uuid() }, 'before')])
await submit('07-stale-version-409', 'expectedObjectVersion 過期：409 conflict', [restore(objectId, 3, original, 'before')])
await submit('08-not-opposite-side-422', '目前引用不是原交易另一側（已是 after）：media_reference_changed', [restore(objectId, 4, original, 'after')])
const editor = await createUser(env.app, 'Editor')
await api.put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
await submit('09-foreign-actor-422', '其他使用者引用此原交易：provenance_not_found（不取得他人的 Undo 權限）', [restore(objectId, 4, original, 'before')], client(env.app, editor))
await submit('10-client-asset-id-rejected-422', 'payload 不接受 assetId：invalid_operation', [{ kind: 'restore_media_references', payload: { ...restore(objectId, 4, original, 'before').payload, assetId: a.id } }])
await recorder.record('11-capabilities', 'capabilities：mediaReferenceRestore', { method: 'GET', path: '/api/v1/capabilities' }, await client(env.app).get('/capabilities'))

const count = await recorder.writeIndex('Media reference restore v1（batch F）', 'scripts/generate-media-restore-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await env.close()
await sql.end()
