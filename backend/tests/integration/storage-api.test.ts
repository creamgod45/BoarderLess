import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type { FastifyInstance } from 'fastify'
import type postgres from 'postgres'
import { client, createTestApp, createUser, envelope, setupTestDatabase, uuid } from '../helpers.ts'

const sql = await setupTestDatabase()
if (!sql) console.warn('[integration] 無法連線測試 PostgreSQL，略過整合測試（先執行 `bun run db:up`）')

describe.skipIf(!sql)('storage API (PostgreSQL)', () => {
  let app: FastifyInstance
  let ownerId: string
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    app = await createTestApp(sql as postgres.Sql)
    ownerId = await createUser(app, 'Owner')
    api = client(app, ownerId)
  })

  afterAll(async () => {
    await app?.close()
    await sql?.end()
  })

  async function newWorkspace(): Promise<string> {
    const res = await api.post('/workspaces', { title: 'Test workspace' })
    expect(res.status).toBe(201)
    return res.body.id
  }

  test('create / move / delete nodes and replay is idempotent (B0)', async () => {
    const ws = await newWorkspace()
    const node = uuid()

    const create = envelope(0, [{ kind: 'create_object', payload: { objectId: node, objectType: 'text' } }])
    const first = await api.post(`/workspaces/${ws}/operations`, create)
    expect(first.status).toBe(200)
    expect(first.body.status).toBe('accepted')
    expect(first.body.fromServerSeq).toBe(1)

    // 相同 operation 重送：不重複寫入，回傳原結果
    const retry = await api.post(`/workspaces/${ws}/operations`, create)
    expect(retry.body.status).toBe('duplicate')
    expect(retry.body.operations[0].serverSeq).toBe(1)

    const move = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(1, [
        {
          kind: 'move_objects',
          expectedObjectVersions: { [node]: 1 },
          payload: { moves: [{ objectId: node, transform: { x: 40, y: 8 } }] },
        },
      ]),
    )
    expect(move.body.status).toBe('accepted')

    const del = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(2, [{ kind: 'delete_objects', payload: { objectIds: [node] } }]),
    )
    expect(del.body.status).toBe('accepted')

    const state = await api.get(`/workspaces/${ws}/state`)
    expect(state.body.workspaceVersion).toBe(3)
    expect(state.body.throughServerSeq).toBe(3)
    expect(state.body.objects).toHaveLength(0)
  })

  test('concurrent writers get unique, gapless serverSeq', async () => {
    const ws = await newWorkspace()
    const results = await Promise.all(
      Array.from({ length: 20 }, () =>
        api.post(
          `/workspaces/${ws}/operations`,
          envelope(0, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'shape' } }]),
        ),
      ),
    )
    expect(results.every((r) => r.body.status === 'accepted')).toBe(true)
    const seqs = results.map((r) => r.body.fromServerSeq).sort((a, b) => a - b)
    expect(seqs).toEqual(Array.from({ length: 20 }, (_, i) => i + 1))

    const ops = await api.get(`/workspaces/${ws}/operations?afterSeq=0&limit=100`)
    expect(ops.body.operations.map((o: { serverSeq: number }) => o.serverSeq)).toEqual(seqs)
  })

  test('stale expectedObjectVersions returns conflict with missing operations', async () => {
    const ws = await newWorkspace()
    const node = uuid()
    await api.post(
      `/workspaces/${ws}/operations`,
      envelope(0, [{ kind: 'create_object', payload: { objectId: node, objectType: 'text' } }]),
    )
    await api.post(
      `/workspaces/${ws}/operations`,
      envelope(1, [{ kind: 'update_object', payload: { objectId: node, properties: { text: 'A' } } }]),
    )

    const stale = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(1, [
        { kind: 'update_object', expectedObjectVersions: { [node]: 1 }, payload: { objectId: node, properties: { text: 'B' } } },
      ]),
    )
    expect(stale.status).toBe(409)
    expect(stale.body.status).toBe('conflict')
    expect(stale.body.conflicts[0]).toEqual({ id: node, expectedVersion: 1, actualVersion: 2 })
    expect(stale.body.missingOperations).toHaveLength(1)
    expect(stale.body.missingOperations[0].workspaceVersion).toBe(2)
  })

  test('failed invariant rolls back the whole transaction', async () => {
    const ws = await newWorkspace()
    const res = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(0, [
        { kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } },
        { kind: 'delete_objects', payload: { objectIds: [uuid()] } },
      ]),
    )
    expect(res.status).toBe(422)
    expect(res.body.error.code).toBe('object_not_found')

    const state = await api.get(`/workspaces/${ws}/state`)
    expect(state.body.objects).toHaveLength(0)
    expect(state.body.throughServerSeq).toBe(0)
  })

  test('deleting a node cascades its relations in the same transaction', async () => {
    const ws = await newWorkspace()
    const [a, b, rel] = [uuid(), uuid(), uuid()]
    await api.post(
      `/workspaces/${ws}/operations`,
      envelope(0, [
        { kind: 'create_object', payload: { objectId: a, objectType: 'text' } },
        { kind: 'create_object', payload: { objectId: b, objectType: 'text' } },
        { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: a, targetObjectId: b, direction: 'forward' } },
      ]),
    )
    const del = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(1, [{ kind: 'delete_objects', payload: { objectIds: [a] } }]),
    )
    expect(del.body.operations[0].payload.cascadedRelationIds).toEqual([rel])

    const state = await api.get(`/workspaces/${ws}/state`)
    expect(state.body.relations).toHaveLength(0)
    expect(state.body.objects).toHaveLength(1)
  })

  test('roles and revoked membership are enforced', async () => {
    const ws = await newWorkspace()
    const otherId = await createUser(app, 'Other')
    const other = client(app, otherId)
    const op = () =>
      envelope(0, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } }])

    // 非成員看不到 Workspace
    expect((await other.get(`/workspaces/${ws}`)).status).toBe(404)

    await api.put(`/workspaces/${ws}/members/${otherId}`, { role: 'viewer' })
    expect((await other.get(`/workspaces/${ws}`)).status).toBe(200)
    expect((await other.post(`/workspaces/${ws}/operations`, op())).status).toBe(403)

    await api.put(`/workspaces/${ws}/members/${otherId}`, { role: 'editor' })
    expect((await other.post(`/workspaces/${ws}/operations`, op())).status).toBe(200)

    await api.delete(`/workspaces/${ws}/members/${otherId}`)
    expect((await other.post(`/workspaces/${ws}/operations`, op())).status).toBe(404)

    // owner 角色不可被變更
    expect((await api.put(`/workspaces/${ws}/members/${ownerId}`, { role: 'viewer' })).status).toBe(422)
  })

  test('commits write an outbox event in the same transaction', async () => {
    const ws = await newWorkspace()
    const res = await api.post(
      `/workspaces/${ws}/operations`,
      envelope(0, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } }]),
    )
    const rows = await sql!`SELECT event_type, server_seq FROM transaction_outbox WHERE workspace_id = ${ws}`
    expect(rows).toHaveLength(1)
    expect(rows[0]!.eventType).toBe('operations_committed')
    expect(rows[0]!.serverSeq).toBe(res.body.toServerSeq)
  })

  test('snapshot checkpoint and asset metadata', async () => {
    const ws = await newWorkspace()
    await api.post(
      `/workspaces/${ws}/operations`,
      envelope(0, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'image' } }]),
    )
    const snap = await api.post(`/workspaces/${ws}/snapshots`)
    expect(snap.status).toBe(201)
    expect(snap.body.throughServerSeq).toBe(1)
    expect(snap.body.checksum).toStartWith('sha256:')
    expect((await api.get(`/workspaces/${ws}/snapshots/latest`)).body.throughServerSeq).toBe(1)

    const bad = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'application/x-sh', byteSize: 10, checksum: 'x' })
    expect(bad.status).toBe(422)

    const asset = await api.post(`/workspaces/${ws}/assets`, { mediaType: 'image/png', byteSize: 10, checksum: 'sha256:x' })
    expect(asset.status).toBe(201)
    expect(asset.body.asset.status).toBe('pending')
    expect(asset.body.asset.storageKey).toContain(asset.body.asset.id)
  })

  test('requests without a user are rejected', async () => {
    const res = await client(app).get('/workspaces')
    expect(res.status).toBe(401)
    expect(res.body.error.code).toBe('unauthorized')
  })
})
