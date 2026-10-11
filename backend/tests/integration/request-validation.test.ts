import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

/** CORE-02：錯誤 primitive、未知欄位、null 一律按 schema 拒絕（400），不得 500 或產生部分資料 */
describe.skipIf(!sql)('request validation (CORE-02)', () => {
  let env: TestEnv
  let api: ReturnType<typeof client>
  let ws: string

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    api = client(env.app, await createUser(env.app, 'Validator'))
    ws = (await api.post('/workspaces', { title: 'Validation' })).body.id
  })

  afterAll(async () => {
    await env?.close()
  })

  const textOp = { kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } }

  test('operation envelope: string numbers, unknown fields and null are rejected without side effects', async () => {
    const valid = envelope(0, [textOp])
    const cases: [string, unknown][] = [
      ['protocolVersion as string', { ...valid, protocolVersion: '1' }],
      ['baseVersion as string', { ...valid, baseVersion: '0' }],
      ['clientSeq as string', { ...valid, operations: [{ ...valid.operations[0], clientSeq: '1' }] }],
      ['unknown envelope field', { ...valid, actorId: uuid() }],
      ['unknown operation field', { ...valid, operations: [{ ...valid.operations[0], serverSeq: 1 }] }],
      ['null transactionId', { ...valid, transactionId: null }],
      ['missing operations', { ...valid, operations: undefined }],
    ]
    for (const [label, body] of cases) {
      const res = await api.post(`/workspaces/${ws}/operations`, body)
      expect({ label, status: res.status, code: res.body.error?.code }).toEqual({ label, status: 400, code: 'validation_error' })
    }
    expect((await api.get(`/workspaces/${ws}`)).body.currentVersion).toBe(0)
    expect((await api.post(`/workspaces/${ws}/operations`, valid)).body.status).toBe('accepted')
  })

  test('other bodies: workspace, member role, Media v1 prepare', async () => {
    expect((await api.post('/workspaces', { title: 42 })).status).toBe(400)
    expect((await api.patch(`/workspaces/${ws}`, { title: 'x', extra: true })).status).toBe(400)
    expect((await api.put(`/workspaces/${ws}/members/${uuid()}`, { role: null })).status).toBe(400)
    const prepare = (body: object) => api.post(`/workspaces/${ws}/assets`, body)
    const checksum = `sha256:${'0'.repeat(64)}`
    expect((await prepare({ mediaType: 'image/png', byteSize: '10', checksum })).status).toBe(400)
    expect((await prepare({ mediaType: 'image/png', byteSize: 10, checksum, width: 2147483648 })).status).toBe(400)
  })

  test('querystring is still converted from URL strings and gets defaults', async () => {
    const page = await api.get(`/workspaces/${ws}/operations?afterSeq=0&limit=10`)
    expect(page.status).toBe(200)
    expect((await api.get(`/workspaces/${ws}/operations`)).status).toBe(200)
    expect((await api.get(`/workspaces/${ws}/operations?afterSeq=abc`)).status).toBe(400)
    expect((await api.get(`/workspaces/not-a-uuid`)).status).toBe(400)
  })
})
