import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

/** 同 transaction / operation ID：內容相同才回原結果（duplicate），內容不同明確拒絕 */
describe.skipIf(!sql)('operation idempotency requires identical content', () => {
  let env: TestEnv
  let api: ReturnType<typeof client>
  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    api = client(env.app, await createUser(env.app, 'Owner'))
  })
  afterAll(async () => {
    await env?.close()
  })

  const text = (objectId: string, label = 'Idea') => ({ kind: 'create_object', payload: { objectId, objectType: 'text', properties: { text: label } } })

  test('identical resend → duplicate with the original result', async () => {
    const ws = (await api.post('/workspaces', { title: 'Idem' })).body.id
    const tx = envelope(0, [text(uuid())])
    const first = await api.post(`/workspaces/${ws}/operations`, tx)
    // key 順序不同但內容相同：仍是 duplicate
    const reordered = { ...tx, operations: tx.operations.map(({ payload, kind, operationId, clientSeq }) => ({ payload, clientSeq, operationId, kind })) }
    const again = await api.post(`/workspaces/${ws}/operations`, reordered)
    expect(again.body).toEqual({ ...first.body, status: 'duplicate' })
  })

  test('same IDs with different content, other transaction ID, or a reused transaction ID → 409 idempotency_mismatch', async () => {
    const ws = (await api.post('/workspaces', { title: 'Idem' })).body.id
    const objectId = uuid()
    const tx = envelope(0, [text(objectId)])
    await api.post(`/workspaces/${ws}/operations`, tx)
    const cases: [string, unknown, string][] = [
      ['payload changed', { ...tx, operations: [{ ...tx.operations[0], payload: text(objectId, 'Changed').payload }] }, 'content_mismatch'],
      ['clientSeq changed', { ...tx, operations: [{ ...tx.operations[0], clientSeq: 9 }] }, 'content_mismatch'],
      ['baseVersion changed', { ...tx, baseVersion: 1 }, 'content_mismatch'],
      ['expectedObjectVersions added', { ...tx, operations: [{ ...tx.operations[0], expectedObjectVersions: { [objectId]: 1 } }] }, 'content_mismatch'],
      ['other transaction ID', { ...tx, transactionId: uuid() }, 'transaction_mismatch'],
      ['transaction ID reused with new operations', { ...envelope(1, [text(uuid())]), transactionId: tx.transactionId }, 'transaction_reused'],
    ]
    for (const [label, body, reason] of cases) {
      const res = await api.post(`/workspaces/${ws}/operations`, body)
      expect({ label, status: res.status, code: res.body.error?.code, reason: res.body.error?.details?.reason }).toEqual({ label, status: 409, code: 'idempotency_mismatch', reason })
    }
    expect((await api.get(`/workspaces/${ws}`)).body.currentVersion).toBe(1)
  })

  test('operations committed without a digest (before migration 011) cannot be confirmed as duplicates', async () => {
    const ws = (await api.post('/workspaces', { title: 'Legacy' })).body.id
    const tx = envelope(0, [text(uuid())])
    await api.post(`/workspaces/${ws}/operations`, tx)
    await sql!`UPDATE workspace_operations SET request_digest = NULL WHERE workspace_id = ${ws}`
    const res = await api.post(`/workspaces/${ws}/operations`, tx)
    expect([res.status, res.body.error.details.reason]).toEqual([409, 'original_digest_unavailable'])
  })
})
