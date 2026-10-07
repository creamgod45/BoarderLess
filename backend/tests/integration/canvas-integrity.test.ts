import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import type postgres from 'postgres'
import { client, createTestEnv, createUser, envelope, setupTestDatabase, uuid, type TestEnv } from '../helpers.ts'

/** BAI-001 / 007 / 008 / 011 / 012：projection 正確性、還原與原提交 reconciliation */
const sql = await setupTestDatabase()

afterAll(async () => {
  await sql?.end()
})

const transform = (overrides: Record<string, unknown> = {}) => ({ x: 0, y: 0, width: 260, height: 132, rotationDegrees: 0, ...overrides })
const text = (objectId = uuid(), extra: Record<string, unknown> = {}) => ({
  kind: 'create_object',
  payload: { objectId, objectType: 'text', transform: transform(), properties: { text: 'hi', colorToken: 'paper', shapeToken: 'rounded' }, ...extra },
})
const group = (objectId = uuid(), extra: Record<string, unknown> = {}) => ({
  kind: 'create_object',
  payload: { objectId, objectType: 'group', transform: transform(), properties: { title: 'G', colorToken: 'group' }, ...extra },
})
const reparent = (objectId: string, parentId: string | null) => ({ kind: 'update_object', payload: { objectId, parentId } })

describe.skipIf(!sql)('canvas integrity', () => {
  let env: TestEnv
  let api: ReturnType<typeof client>

  beforeAll(async () => {
    env = await createTestEnv(sql as postgres.Sql)
    api = client(env.app, await createUser(env.app, 'Owner'))
  })

  afterAll(async () => {
    await env?.close()
  })

  async function workspace(): Promise<string> {
    return (await api.post('/workspaces', { title: 'Integrity' })).body.id
  }

  /** 依序提交（每筆用最新 version） */
  async function submit(ws: string, ops: { kind: string; payload: unknown; expectedObjectVersions?: Record<string, number> }[]) {
    const version = (await api.get(`/workspaces/${ws}`)).body.currentVersion
    return api.post(`/workspaces/${ws}/operations`, envelope(version, ops))
  }

  const state = async (ws: string) => (await api.get(`/workspaces/${ws}/state`)).body

  describe('BAI-008 typed object schema', () => {
    test('rejects the payload reproduced in BAI-008', async () => {
      const ws = await workspace()
      const unknownType = await submit(ws, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'future_widget' } }])
      expect(unknownType.status).toBe(422)
      expect(unknownType.body.error).toMatchObject({
        code: 'unsupported_object_type',
        details: { supportedObjectTypes: ['text', 'group', 'media'], objectSchemaVersion: 1 },
      })

      const cases: Record<string, unknown>[] = [
        { transform: transform({ x: 'not-a-number' }) },
        { transform: transform({ width: -1 }) },
        { transform: transform({ height: 1e12 }) },
        { transform: { ...transform(), z: 3 } },
        { properties: { text: 42 } },
        { properties: { text: null } },
        { properties: { shapeToken: 'blob' } },
        { properties: { colorToken: 'neon' } },
        { properties: { text: 'x', secret: 'token' } },
      ]
      for (const extra of cases) {
        const res = await submit(ws, [text(uuid(), extra)])
        expect(res.status).toBe(422)
        expect(res.body.error.code).toBe('invalid_object')
      }
      expect((await state(ws)).objects).toHaveLength(0)
    })

    test('accepts boundary values, custom colors and every APP shape; defaults only for absent fields', async () => {
      const ws = await workspace()
      const ok = await submit(ws, [
        text(uuid(), { transform: transform({ width: 0, height: 0, x: -10_000_000, rotationDegrees: -90.5 }) }),
        text(uuid(), { properties: { text: '', colorToken: '#a1b2c3', shapeToken: 'manual-input' } }),
        { kind: 'create_object', payload: { objectId: uuid(), objectType: 'text' } },
        group(uuid(), { properties: { title: 'Lilac', colorToken: 'lilac' } }),
      ])
      expect(ok.body.status).toBe('accepted')
      const bare = (await state(ws)).objects.find((o: { properties: object }) => Object.keys(o.properties).length === 0)
      // 缺省 transform 由伺服器補齊並寫入正式 projection
      expect(bare.transform).toEqual({ x: 0, y: 0, width: 260, height: 132, rotationDegrees: 0 })
    })

    test('update validates the merged state, not only the patch', async () => {
      const ws = await workspace()
      const id = uuid()
      await submit(ws, [text(id)])
      const badMerge = await submit(ws, [{ kind: 'update_object', payload: { objectId: id, properties: { title: 'groups only' } } }])
      expect(badMerge.body.error.code).toBe('invalid_object')
      const badMove = await submit(ws, [{ kind: 'move_objects', payload: { moves: [{ objectId: id, transform: { width: -5 } } ] } }])
      expect(badMove.body.error.code).toBe('invalid_object')
      const zIndex = await submit(ws, [{ kind: 'update_object', payload: { objectId: id, zIndex: 2 ** 31 } }])
      expect(zIndex.status).toBe(422)

      // 部分 transform 與既有值 merge
      const partial = await submit(ws, [{ kind: 'move_objects', payload: { moves: [{ objectId: id, transform: { x: 40 } }] } }])
      expect(partial.body.operations[0].payload.moves[0].transform).toEqual(transform({ x: 40 }))
      expect((await state(ws)).objects[0].transform).toEqual(transform({ x: 40 }))
    })

    test('objectType replacement is an explicit unsupported change (BAI-011)', async () => {
      const ws = await workspace()
      const id = uuid()
      await submit(ws, [text(id)])
      const res = await submit(ws, [{ kind: 'update_object', payload: { objectId: id, objectType: 'group' } }])
      expect(res.body.error).toMatchObject({ code: 'unsupported_change', details: { field: 'objectType' } })
    })
  })

  describe('BAI-007 group hierarchy', () => {
    test('parent must be an active group of the same workspace', async () => {
      const ws = await workspace()
      const [t, child] = [uuid(), uuid()]
      await submit(ws, [text(t)])

      const nonGroup = await submit(ws, [text(child, { parentId: t })])
      expect(nonGroup.body.error).toMatchObject({ code: 'invalid_parent', details: { parentType: 'text' } })

      const otherWs = await workspace()
      const foreign = uuid()
      await submit(otherWs, [group(foreign)])
      expect((await submit(ws, [text(uuid(), { parentId: foreign })])).body.error.code).toBe('invalid_parent')

      const g = uuid()
      await submit(ws, [group(g)])
      await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [g] } }])
      expect((await submit(ws, [reparent(t, g)])).body.error.code).toBe('invalid_parent')

      // 同一 transaction 先建立 group 再當 parent：合法
      const [g2, c2] = [uuid(), uuid()]
      expect((await submit(ws, [group(g2), text(c2, { parentId: g2 })])).body.status).toBe('accepted')
    })

    test('rejects direct, indirect and same-transaction cycles', async () => {
      const ws = await workspace()
      const [a, b, c] = [uuid(), uuid(), uuid()]
      await submit(ws, [group(a), group(b, { parentId: a }), group(c, { parentId: b })])

      expect((await submit(ws, [reparent(a, a)])).body.error.code).toBe('invalid_parent')
      expect((await submit(ws, [reparent(a, b)])).body.error.code).toBe('parent_cycle') // A → B → A
      expect((await submit(ws, [reparent(a, c)])).body.error.code).toBe('parent_cycle') // A → C → B → A

      const [d, e] = [uuid(), uuid()]
      await submit(ws, [group(d), group(e)])
      const sameTx = await submit(ws, [reparent(d, e), reparent(e, d)])
      expect(sameTx.body.error.code).toBe('parent_cycle')

      // 拒絕後狀態不變
      const objects = (await state(ws)).objects
      expect(objects.find((o: { objectId: string }) => o.objectId === d).parentId).toBeNull()
      expect(objects.find((o: { objectId: string }) => o.objectId === a).parentId).toBeNull()
    })

    test('deleting a group requires deleting or reparenting its children', async () => {
      const ws = await workspace()
      const [g, child, nested, grandchild] = [uuid(), uuid(), uuid(), uuid()]
      await submit(ws, [group(g), text(child, { parentId: g }), group(nested, { parentId: g }), text(grandchild, { parentId: nested })])

      const orphaning = await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [g] } }])
      expect(orphaning.body.error.code).toBe('hierarchy_cascade_mismatch')
      expect(orphaning.body.error.details.objectIds.sort()).toEqual([child, nested].sort())

      const partial = await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [g, child, nested] } }])
      expect(partial.body.error.details.objectIds).toEqual([grandchild])

      // ungroup：先 reparent children，再刪 group（APP ungroup 流程）
      const ungroup = await submit(ws, [reparent(child, null), reparent(nested, null), { kind: 'delete_objects', payload: { objectIds: [g] } }])
      expect(ungroup.body.status).toBe('accepted')
      expect((await state(ws)).objects.every((o: { parentId: string | null }) => o.parentId !== g)).toBe(true)

      // 連同所有 descendants 一起刪除
      expect((await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [nested, grandchild] } }])).body.status).toBe('accepted')
    })
  })

  describe('BAI-001 / BAI-011 restore', () => {
    test('delete → restore keeps the stable ID, last state and cascaded relations', async () => {
      const ws = await workspace()
      const [a, b, rel] = [uuid(), uuid(), uuid()]
      await submit(ws, [
        text(a, { properties: { text: 'keep me', colorToken: 'mint' } }),
        text(b),
        { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: a, targetObjectId: b, direction: 'forward', style: { colorToken: 'relation' } } },
      ])
      const del = await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [a] }, expectedObjectVersions: { [a]: 1 } }])
      expect(del.body.operations[0].payload.cascadedRelationIds).toEqual([rel])

      // create 不兼任 restore：回報可辨識的提示
      const recreate = await submit(ws, [text(a)])
      expect(recreate.body.error).toMatchObject({ code: 'object_already_exists', details: { deleted: true, restoreWith: 'restore_objects' } })

      const restore = await submit(ws, [
        { kind: 'restore_objects', payload: { objectIds: [a] }, expectedObjectVersions: { [a]: 2 } },
        { kind: 'restore_relations', payload: { relationIds: [rel] } },
      ])
      expect(restore.body.status).toBe('accepted')
      expect(restore.body.operations[0].payload.restoredVersions).toEqual({ [a]: 3 })

      const s = await state(ws)
      const restored = s.objects.find((o: { objectId: string }) => o.objectId === a)
      expect(restored).toMatchObject({ objectVersion: 3, properties: { text: 'keep me', colorToken: 'mint' } })
      expect(s.relations.map((r: { relationId: string }) => r.relationId)).toEqual([rel])
    })

    test('create → undo (delete) → redo (restore)', async () => {
      const ws = await workspace()
      const id = uuid()
      await submit(ws, [text(id)])
      await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [id] } }])
      expect((await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [id] } }])).body.status).toBe('accepted')
      expect((await state(ws)).objects).toHaveLength(1)
    })

    test('restore rejects stale tombstone versions, active objects and unknown IDs', async () => {
      const ws = await workspace()
      const id = uuid()
      await submit(ws, [text(id)])

      expect((await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [id] } }])).body.error.code).toBe('object_not_deleted')
      expect((await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [uuid()] } }])).body.error.code).toBe('object_not_found')

      await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [id] } }])
      const stale = await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [id] }, expectedObjectVersions: { [id]: 1 } }])
      expect(stale.status).toBe(409)
      expect(stale.body.conflicts[0]).toEqual({ id, expectedVersion: 1, actualVersion: 2 })
    })

    test('restore enforces group dependencies and relation endpoints atomically', async () => {
      const ws = await workspace()
      const [g, child, other, rel] = [uuid(), uuid(), uuid(), uuid()]
      await submit(ws, [
        group(g),
        text(child, { parentId: g }),
        text(other),
        { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: child, targetObjectId: other, direction: 'none' } },
      ])
      await submit(ws, [{ kind: 'delete_objects', payload: { objectIds: [g, child] } }])

      // parent 仍在 tombstone：不可只還原 child
      expect((await submit(ws, [{ kind: 'restore_objects', payload: { objectIds: [child] } }])).body.error.code).toBe('invalid_parent')
      // 端點尚未還原：relation 不可還原
      expect((await submit(ws, [{ kind: 'restore_relations', payload: { relationIds: [rel] } }])).body.error.code).toBe('relation_endpoint_missing')

      const all = await submit(ws, [
        { kind: 'restore_objects', payload: { objectIds: [child, g] } },
        { kind: 'restore_relations', payload: { relationIds: [rel] } },
      ])
      expect(all.body.status).toBe('accepted')
      const s = await state(ws)
      expect(s.objects).toHaveLength(3)
      expect(s.relations).toHaveLength(1)
    })

    test('relation endpoint changes are an explicit unsupported change', async () => {
      const ws = await workspace()
      const [a, b, c, rel] = [uuid(), uuid(), uuid(), uuid()]
      await submit(ws, [
        text(a),
        text(b),
        text(c),
        { kind: 'create_relation', payload: { relationId: rel, sourceObjectId: a, targetObjectId: b, direction: 'forward' } },
      ])
      const res = await submit(ws, [{ kind: 'update_relation', payload: { relationId: rel, targetObjectId: c } }])
      expect(res.body.error).toMatchObject({ code: 'unsupported_change', details: { field: 'targetObjectId' } })
      const badStyle = await submit(ws, [{ kind: 'update_relation', payload: { relationId: rel, style: { width: 3 } } }])
      expect(badStyle.body.error.code).toBe('invalid_relation')
    })
  })

  describe('BAI-012 receipts and fences', () => {
    test('receipt returns the complete transaction boundary for any of its IDs', async () => {
      const ws = await workspace()
      const env1 = envelope(0, [text(), text(), text()])
      const committed = await api.post(`/workspaces/${ws}/operations`, env1)
      expect(committed.body.status).toBe('accepted')

      // 只查其中一個 operation ID（partial）：仍回傳完整 receipt
      const res = await api.post(`/workspaces/${ws}/operations/receipts`, { operationIds: [env1.operations[1]!.operationId] })
      expect(res.status).toBe(200)
      expect(res.body.lookups).toEqual([
        { type: 'operation', id: env1.operations[1]!.operationId, status: 'committed', transactionId: env1.transactionId, workspaceVersion: 1 },
      ])
      expect(res.body.receipts).toHaveLength(1)
      expect(res.body.receipts[0]).toMatchObject({
        transactionId: env1.transactionId,
        clientId: env1.clientId,
        fromServerSeq: 1,
        toServerSeq: 3,
        workspaceVersion: 1,
      })
      expect(res.body.receipts[0].operations.map((o: { operationId: string }) => o.operationId)).toEqual(
        env1.operations.map((o) => o.operationId),
      )
      expect(res.body.receipts[0]).not.toHaveProperty('payload')
      expect(res.body).toMatchObject({ headServerSeq: 3, headWorkspaceVersion: 1 })
    })

    test('the same IDs submitted by another actor are not claimable', async () => {
      const ws = await workspace()
      const editorId = await createUser(env.app, 'Editor')
      await api.put(`/workspaces/${ws}/members/${editorId}`, { role: 'editor' })
      const editor = client(env.app, editorId)
      const original = envelope(0, [text()])
      await editor.post(`/workspaces/${ws}/operations`, original)

      const mine = await api.post(`/workspaces/${ws}/operations/receipts`, {
        transactionIds: [original.transactionId],
        operationIds: [original.operations[0]!.operationId],
      })
      expect(mine.body.receipts).toEqual([])
      expect(mine.body.lookups.every((l: { status: string }) => l.status === 'unknown')).toBe(true)

      const theirs = await editor.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [original.transactionId] })
      expect(theirs.body.lookups[0].status).toBe('committed')
    })

    test('fence guarantees a late original request never commits', async () => {
      const ws = await workspace()
      const late = envelope(0, [text()])

      const fence = await api.post(`/workspaces/${ws}/operations/fences`, { transactionIds: [late.transactionId] })
      expect(fence.status).toBe(200)
      expect(fence.body.lookups[0]).toMatchObject({ status: 'fenced' })

      const original = await api.post(`/workspaces/${ws}/operations`, late)
      expect(original.status).toBe(409)
      expect(original.body.error.code).toBe('transaction_fenced')
      expect((await state(ws)).objects).toHaveLength(0)

      // operation ID 也可 fence（換新 transactionId 重送同一 operation 仍被擋）
      const byOp = envelope(0, [text()])
      await api.post(`/workspaces/${ws}/operations/fences`, { operationIds: [byOp.operations[0]!.operationId] })
      expect((await api.post(`/workspaces/${ws}/operations`, { ...byOp, transactionId: uuid() })).body.error.code).toBe('transaction_fenced')

      // fence 冪等，查詢也顯示 fenced
      expect((await api.post(`/workspaces/${ws}/operations/fences`, { transactionIds: [late.transactionId] })).body.lookups[0].status).toBe('fenced')
      expect((await api.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [late.transactionId] })).body.lookups[0].status).toBe('fenced')
    })

    test('fencing an already committed transaction returns its receipt instead', async () => {
      const ws = await workspace()
      const done = envelope(0, [text()])
      await api.post(`/workspaces/${ws}/operations`, done)
      const res = await api.post(`/workspaces/${ws}/operations/fences`, { transactionIds: [done.transactionId] })
      expect(res.body.lookups[0].status).toBe('committed')
      expect(res.body.receipts).toHaveLength(1)
      // 已提交的重送仍是 duplicate
      expect((await api.post(`/workspaces/${ws}/operations`, done)).body.status).toBe('duplicate')
    })

    test('concurrent fence and original submit: exactly one outcome', async () => {
      for (let i = 0; i < 5; i++) {
        const ws = await workspace()
        const req = envelope(0, [text()])
        const [submitRes, fenceRes] = await Promise.all([
          api.post(`/workspaces/${ws}/operations`, req),
          api.post(`/workspaces/${ws}/operations/fences`, { transactionIds: [req.transactionId] }),
        ])
        const status = fenceRes.body.lookups[0].status
        if (status === 'committed') expect(submitRes.body.status).toBe('accepted')
        else {
          expect(status).toBe('fenced')
          expect(submitRes.body.error.code).toBe('transaction_fenced')
        }
      }
    })

    test('ACL, limits and revoked members', async () => {
      const ws = await workspace()
      expect((await api.post(`/workspaces/${ws}/operations/receipts`, {})).status).toBe(400)
      const tooMany = Array.from({ length: 201 }, () => uuid())
      expect((await api.post(`/workspaces/${ws}/operations/receipts`, { operationIds: tooMany })).status).toBe(400)
      const split = { transactionIds: tooMany.slice(0, 101), operationIds: tooMany.slice(101) }
      expect((await api.post(`/workspaces/${ws}/operations/receipts`, split)).status).toBe(400)

      const viewerId = await createUser(env.app, 'Viewer')
      const viewer = client(env.app, viewerId)
      await api.put(`/workspaces/${ws}/members/${viewerId}`, { role: 'viewer' })
      expect((await viewer.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [uuid()] })).status).toBe(200)
      expect((await viewer.post(`/workspaces/${ws}/operations/fences`, { transactionIds: [uuid()] })).status).toBe(403)
      await api.delete(`/workspaces/${ws}/members/${viewerId}`)
      expect((await viewer.post(`/workspaces/${ws}/operations/receipts`, { transactionIds: [uuid()] })).status).toBe(404)
    })
  })
})
