import { afterAll, beforeAll, describe, expect, test } from 'bun:test'
import { Value } from 'typebox/value'
import { SERVER_EVENT_SCHEMAS } from '../../src/realtime/messages.ts'
import { envelope, setupTestDatabase, uuid } from '../helpers.ts'
import { http, startGateway, TestSocket, type Gateway, type ServerEvent } from '../realtime-helpers.ts'

const sql = await setupTestDatabase()
afterAll(async () => {
  await sql?.end()
})

/** 每則 server 事件都必須符合正式 schema */
function expectValid(event: ServerEvent) {
  const schema = SERVER_EVENT_SCHEMAS[event.type]
  expect(schema).toBeDefined()
  expect([...Value.Errors(schema!, event)].map((e) => `${event.type}${e.instancePath} ${e.message}`)).toEqual([])
}

describe.skipIf(!sql)('realtime collaboration (batch A)', () => {
  let a: Gateway
  let b: Gateway

  beforeAll(async () => {
    // 兩個 gateway：各自的連線池與 LISTEN，模擬兩個 API process
    a = await startGateway()
    b = await startGateway()
  })

  afterAll(async () => {
    await a?.close()
    await b?.close()
  })

  async function user(name = 'User') {
    return (await http(a.baseUrl).post('/users', { displayName: name })).body.id as string
  }

  async function workspace(ownerId: string) {
    return (await http(a.baseUrl, ownerId).post('/workspaces', { title: 'Realtime' })).body.id as string
  }

  async function ticket(gw: Gateway, userId: string, ws: string, clientId: string) {
    const res = await http(gw.baseUrl, userId).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId })
    expect(res.status).toBe(201)
    return res.body.ticket as string
  }

  interface Joined {
    socket: TestSocket
    ws: string
    epoch: string
    clientId: string
    userId: string
    joined: ServerEvent
    env: (extra: object) => object
  }

  async function join(gw: Gateway, userId: string, ws: string, opts: { lastSeen?: number; clientId?: string; ticketOverride?: string } = {}): Promise<Joined> {
    const clientId = opts.clientId ?? uuid()
    const epoch = uuid()
    const t = opts.ticketOverride ?? (await ticket(gw, userId, ws, clientId))
    const socket = new TestSocket(gw.wsUrl)
    await socket.opened()
    socket.send({
      type: 'join_workspace',
      protocolVersion: 1,
      schemaVersion: 1,
      workspaceId: ws,
      clientId,
      connectionEpoch: epoch,
      lastSeenServerSeq: opts.lastSeen ?? 0,
      metadataRevision: 0,
      ticket: t,
    })
    const joined = await socket.next()
    const env = (extra: object) => ({ protocolVersion: 1, workspaceId: ws, connectionEpoch: epoch, ...extra })
    return { socket, ws, epoch, clientId, userId, joined, env }
  }

  /** join → catch-up → catch_up_applied → live_ready → presence_snapshot */
  async function live(gw: Gateway, userId: string, ws: string) {
    const j = await join(gw, userId, ws)
    expect(j.joined.type).toBe('join_accepted')
    const barrier = j.joined.barrierServerSeq as number
    let applied = 0
    while (applied < barrier) applied = (await j.socket.next('catch_up_transaction')).toServerSeq
    j.socket.send({ type: 'catch_up_applied', ...j.env({ appliedServerSeq: applied, workspaceVersion: 0, metadataRevision: 1 }) })
    const ready = await j.socket.next('live_ready')
    const presence = await j.socket.next('presence_snapshot')
    return { ...j, ready, presence }
  }

  const textOp = (objectId = uuid()) => ({ kind: 'create_object', payload: { objectId, objectType: 'text' } })
  async function commitRest(userId: string, ws: string, ops = [textOp()]) {
    const version = (await http(a.baseUrl, userId).get(`/workspaces/${ws}`)).body.currentVersion
    const res = await http(a.baseUrl, userId).post(`/workspaces/${ws}/operations`, envelope(version, ops))
    expect(res.body.status).toBe('accepted')
    return res.body
  }

  test('capabilities and ticket: membership gated, no-store, global flag', async () => {
    const owner = await user('Owner')
    const outsider = await user('Outsider')
    const ws = await workspace(owner)
    const caps = await http(a.baseUrl, owner).get(`/workspaces/${ws}/realtime-capabilities`)
    expect(caps.status).toBe(200)
    expect(caps.body).toMatchObject({
      protocolVersions: [1],
      schemaVersions: [1],
      webSocketUrl: a.wsUrl,
      subprotocol: 'boarderless.v1',
      operationFanOut: true,
      presence: true,
      metadataRevision: 1,
      maxMessageBytes: 1048576,
      heartbeatIntervalMs: 15000,
      presenceTtlMs: 45000,
      ticketTtlSeconds: 60,
    })
    expect((await http(a.baseUrl, outsider).get(`/workspaces/${ws}/realtime-capabilities`)).status).toBe(404)
    expect((await http(a.baseUrl).get(`/workspaces/${ws}/realtime-capabilities`)).status).toBe(401)

    const t = await http(a.baseUrl, owner).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId: uuid() })
    expect(t.status).toBe(201)
    expect(t.headers.get('cache-control')).toBe('no-store')
    expect(t.body).toMatchObject({ protocolVersion: 1, webSocketUrl: a.wsUrl })
    expect(t.body.ticket.length).toBeGreaterThan(30)
    expect((await http(a.baseUrl, owner).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 2, clientId: uuid() })).status).toBe(400)
    expect((await http(a.baseUrl, outsider).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId: uuid() })).status).toBe(404)

    const global = await http(a.baseUrl).get('/capabilities')
    expect(global.body.realtime).toEqual({ supported: true, protocolVersions: [1], subprotocol: 'boarderless.v1' })
    expect(global.body.assetPreparation).toBeDefined()
    expect(global.body.quickSchemes).toEqual({ supported: false })
  })

  test('join on an empty workspace → live, presence snapshot includes self', async () => {
    const owner = await user('Owner')
    const ws = await workspace(owner)
    const s = await live(a, owner, ws)
    expect(s.joined).toMatchObject({ type: 'join_accepted', userId: owner, role: 'owner', barrierServerSeq: 0, workspaceVersion: 0, metadataRevision: 1 })
    expect(s.ready).toMatchObject({ throughServerSeq: 0, connectionId: s.joined.connectionId })
    expect(s.presence.peers.map((p: any) => p.connectionId)).toEqual([s.joined.connectionId])
    expect(s.presence.peers[0]).toMatchObject({ userId: owner, displayName: 'Owner', role: 'owner', cursor: null, selectedObjectIds: [] })
    for (const m of s.socket.messages) expectValid(m)
    s.socket.close()
  })

  test('ticket failures: unknown, reused, expired, scope mismatch, timeout, non-join first', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    const other = await workspace(owner)

    const unknown = await join(a, owner, ws, { ticketOverride: 'not-a-real-ticket' })
    expect(unknown.joined).toMatchObject({ type: 'protocol_error', code: 'authentication_required', workspaceId: null, connectionEpoch: null })
    expect(await unknown.socket.closed()).toBe(4401)

    const clientId = uuid()
    const t = await ticket(a, owner, ws, clientId)
    const first = await join(a, owner, ws, { clientId, ticketOverride: t })
    expect(first.joined.type).toBe('join_accepted')
    const reused = await join(a, owner, ws, { clientId, ticketOverride: t })
    expect(reused.joined.code).toBe('ticket_used')

    const expiredClient = uuid()
    const expired = await ticket(a, owner, ws, expiredClient)
    await sql!`UPDATE realtime_tickets SET expires_at = now() - interval '1 second' WHERE workspace_id = ${ws} AND client_id = ${expiredClient}`
    expect((await join(a, owner, ws, { clientId: expiredClient, ticketOverride: expired })).joined.code).toBe('ticket_expired')

    const scoped = await ticket(a, owner, ws, clientId)
    expect((await join(a, owner, other, { clientId, ticketOverride: scoped })).joined.code).toBe('scope_mismatch')
    const wrongClient = await ticket(a, owner, ws, uuid())
    expect((await join(a, owner, ws, { clientId: uuid(), ticketOverride: wrongClient })).joined.code).toBe('scope_mismatch')

    const idle = new TestSocket(a.wsUrl)
    await idle.opened()
    expect((await idle.next()).code).toBe('authentication_required')
    expect(await idle.closed()).toBe(4408)

    const early = new TestSocket(a.wsUrl)
    await early.opened()
    early.send({ type: 'heartbeat', protocolVersion: 1, workspaceId: ws, connectionEpoch: uuid(), heartbeatSeq: 1 })
    expect((await early.next()).code).toBe('authentication_required')
    first.socket.close()
  })

  test('upgrade rejects disallowed Origin and missing subprotocol', async () => {
    const strict = await startGateway({ allowedOrigins: ['https://app.example'] })
    try {
      const bad = new TestSocket(strict.wsUrl, { origin: 'https://evil.example' })
      await expect(bad.opened()).rejects.toThrow()
      const good = new TestSocket(strict.wsUrl, { origin: 'https://app.example' })
      await good.opened()
      good.close()
      const noProtocol = new TestSocket(strict.wsUrl, { protocols: [] })
      await expect(noProtocol.opened()).rejects.toThrow()
    } finally {
      await strict.close()
    }
  })

  test('invalid frames fail closed: binary, unknown type, oversized, unsupported versions', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    const binary = new TestSocket(a.wsUrl)
    await binary.opened()
    binary.ws.send(new Uint8Array([1, 2, 3]))
    expect((await binary.next()).code).toBe('invalid_message')
    expect(await binary.closed()).toBe(4400)

    const unknown = new TestSocket(a.wsUrl)
    await unknown.opened()
    unknown.send({ type: 'teleport' })
    expect((await unknown.next()).code).toBe('invalid_message')

    const tiny = await startGateway({ maxMessageBytes: 2048 })
    try {
      const big = new TestSocket(tiny.wsUrl)
      await big.opened()
      big.send('x'.repeat(4096))
      expect((await big.next()).code).toBe('message_too_large')
      expect(await big.closed()).toBe(4413)
    } finally {
      await tiny.close()
    }

    const t = await ticket(a, owner, ws, uuid())
    const v2 = new TestSocket(a.wsUrl)
    await v2.opened()
    v2.send({ type: 'join_workspace', protocolVersion: 2, schemaVersion: 1, workspaceId: ws, clientId: uuid(), connectionEpoch: uuid(), lastSeenServerSeq: 0, metadataRevision: 0, ticket: t })
    expect((await v2.next()).code).toBe('unsupported_protocol_version')
    expect(await v2.closed()).toBe(4426)

    const clientId = uuid()
    const s2 = new TestSocket(a.wsUrl)
    await s2.opened()
    s2.send({ type: 'join_workspace', protocolVersion: 1, schemaVersion: 2, workspaceId: ws, clientId, connectionEpoch: uuid(), lastSeenServerSeq: 0, metadataRevision: 0, ticket: await ticket(a, owner, ws, clientId) })
    expect((await s2.next()).code).toBe('unsupported_schema_version')

    const extra = new TestSocket(a.wsUrl)
    await extra.opened()
    extra.send({ type: 'join_workspace', protocolVersion: 1, schemaVersion: 1, workspaceId: ws, clientId, connectionEpoch: uuid(), lastSeenServerSeq: '0', metadataRevision: 0, ticket: 'x' })
    expect((await extra.next()).code).toBe('invalid_message')
  })

  test('catch-up delivers complete transactions in order, then live', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    await commitRest(owner, ws)
    await commitRest(owner, ws, [textOp(), textOp()])
    await commitRest(owner, ws)
    const j = await join(a, owner, ws)
    expect(j.joined.barrierServerSeq).toBe(4)
    const txs = [await j.socket.next('catch_up_transaction'), await j.socket.next('catch_up_transaction'), await j.socket.next('catch_up_transaction')]
    expect(txs.map((t) => [t.fromServerSeq, t.toServerSeq, t.operations.length, t.workspaceVersion])).toEqual([
      [1, 1, 1, 1],
      [2, 3, 2, 2],
      [4, 4, 1, 3],
    ])
    for (const t of txs) {
      expectValid(t)
      expect(new Set(t.operations.map((o: any) => o.transactionId))).toEqual(new Set([t.transactionId]))
    }
    j.socket.send({ type: 'catch_up_applied', ...j.env({ appliedServerSeq: 4, workspaceVersion: 3, metadataRevision: 1 }) })
    expect((await j.socket.next('live_ready')).throughServerSeq).toBe(4)
    j.socket.close()
  })

  test('snapshot_required: mid-transaction / ahead / too large; snapshot ahead of barrier is honoured', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    await commitRest(owner, ws, [textOp(), textOp()])

    const mid = await join(a, owner, ws, { lastSeen: 1 })
    expect((await mid.socket.next()).reason).toBe('checkpoint_not_at_transaction_boundary')
    const ahead = await join(a, owner, ws, { lastSeen: 99 })
    expect((await ahead.socket.next()).reason).toBe('checkpoint_ahead_of_server')

    // snapshot 路徑：join 之後又提交，client 取得比 barrier 更新的 REST state
    const j = await join(a, owner, ws, { lastSeen: 1 })
    const required = await j.socket.next('snapshot_required')
    expectValid(required)
    expect(required.barrierServerSeq).toBe(2)
    await commitRest(owner, ws)
    const state = (await http(a.baseUrl, owner).get(`/workspaces/${ws}/state`)).body
    expect(state.throughServerSeq).toBe(3)
    j.socket.send({ type: 'catch_up_applied', ...j.env({ appliedServerSeq: 3, workspaceVersion: state.workspaceVersion, metadataRevision: 1 }) })
    const ready = await j.socket.next('live_ready')
    expect(ready.throughServerSeq).toBe(3)
    // 已由 snapshot 覆蓋的 transaction 不重送
    expect(j.socket.messages.filter((m) => m.type.endsWith('_transaction'))).toEqual([])

    // 不在邊界的 applied → sequence_gap + resync
    const bad = await join(a, owner, ws, { lastSeen: 1 })
    await bad.socket.next('snapshot_required')
    bad.socket.send({ type: 'catch_up_applied', ...bad.env({ appliedServerSeq: 1, workspaceVersion: 1, metadataRevision: 1 }) })
    expect((await bad.socket.next('protocol_error')).code).toBe('sequence_gap')
    expect((await bad.socket.next('resync_required')).reason).toBe('sequence_gap')
    expect(await bad.socket.closed()).toBe(4409)

    const small = await startGateway({ maxCatchUpOperations: 1 })
    try {
      expect((await (await join(small, owner, ws)).socket.next('snapshot_required')).reason).toBe('catch_up_too_large')
    } finally {
      await small.close()
    }
    for (const s of [mid, ahead, j]) s.socket.close()
  })

  test('cross-gateway fan-out: WS submit on A reaches B as the same complete transaction; REST too', async () => {
    const owner = await user('Owner')
    const editor = await user('Editor')
    const ws = await workspace(owner)
    await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
    const onA = await live(a, owner, ws)
    const onB = await live(b, editor, ws)

    const ops = Array.from({ length: 200 }, () => textOp())
    const tx = envelope(0, ops, onA.clientId)
    onA.socket.send({ type: 'submit_operations', ...onA.env(tx) })
    const ack = await onA.socket.next('operation_ack')
    expectValid(ack)
    expect(ack).toMatchObject({ status: 'accepted', transactionId: tx.transactionId, fromServerSeq: 1, toServerSeq: 200, workspaceVersion: 1 })
    expect(ack.operationIds).toEqual(tx.operations.map((o) => o.operationId))

    const remote = await onB.socket.next('committed_transaction', 5000)
    expectValid(remote)
    expect(remote).toMatchObject({ transactionId: tx.transactionId, fromServerSeq: 1, toServerSeq: 200, workspaceVersion: 1 })
    expect(remote.operations).toEqual(ack.operations)
    // 提交者自己也收到同一筆（broadcast 可早於或晚於 ack）
    expect((await onA.socket.next('committed_transaction', 5000)).transactionId).toBe(tx.transactionId)

    const rest = await commitRest(owner, ws)
    const viaRest = await onB.socket.next('committed_transaction', 5000)
    expect(viaRest.operations).toEqual(rest.operations)
    onA.socket.close()
    onB.socket.close()
  })

  test('acks: duplicate, conflict, rejected, partial_duplicate; not_live before live', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    const pre = await join(a, owner, ws)
    pre.socket.send({ type: 'submit_operations', ...pre.env(envelope(0, [textOp()], pre.clientId)) })
    expect((await pre.socket.next('protocol_error')).code).toBe('not_live')
    pre.socket.close()

    const s = await live(a, owner, ws)
    const objectId = uuid()
    const tx = envelope(0, [textOp(objectId)], s.clientId)
    s.socket.send({ type: 'submit_operations', ...s.env(tx) })
    const accepted = await s.socket.next('operation_ack')
    s.socket.send({ type: 'submit_operations', ...s.env(tx) })
    const duplicate = await s.socket.next('operation_ack')
    expect(duplicate).toMatchObject({ status: 'duplicate', fromServerSeq: accepted.fromServerSeq, toServerSeq: accepted.toServerSeq })

    const stale = envelope(0, [{ kind: 'update_object', payload: { objectId, properties: { text: 'x' } }, expectedObjectVersions: { [objectId]: 9 } }], s.clientId)
    s.socket.send({ type: 'submit_operations', ...s.env(stale) })
    const conflict = await s.socket.next('operation_ack')
    expectValid(conflict)
    expect(conflict).toMatchObject({ status: 'conflict', conflicts: [{ id: objectId, expectedVersion: 9, actualVersion: 1 }] })
    expect(conflict.missingOperations.length).toBe(1)

    const invalid = envelope(1, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'future' } }], s.clientId)
    s.socket.send({ type: 'submit_operations', ...s.env(invalid) })
    const rejected = await s.socket.next('operation_ack')
    expectValid(rejected)
    expect(rejected).toMatchObject({ status: 'rejected', code: 'unsupported_object_type', details: { supportedObjectTypes: ['text', 'group', 'media'] } })

    const partial = { ...envelope(1, [textOp(), textOp()], s.clientId) }
    partial.operations[0]!.operationId = tx.operations[0]!.operationId
    s.socket.send({ type: 'submit_operations', ...s.env(partial) })
    const pd = await s.socket.next('operation_ack')
    expect(pd).toMatchObject({ status: 'rejected', code: 'partial_duplicate', details: { committedOperationIds: [tx.operations[0]!.operationId] } })
    expect((await http(a.baseUrl, owner).get(`/workspaces/${ws}`)).body.currentVersion).toBe(1)

    // clientId 必須與 ticket 相同
    s.socket.send({ type: 'submit_operations', ...s.env(envelope(1, [textOp()], uuid())) })
    expect((await s.socket.next('protocol_error')).code).toBe('scope_mismatch')
    expect(await s.socket.closed()).toBe(4400)
  })

  test('viewer reads and sees presence but cannot mutate; revoke closes and removes presence', async () => {
    const owner = await user('Owner')
    const viewer = await user('Viewer')
    const ws = await workspace(owner)
    await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${viewer}`, { role: 'viewer' })
    const o = await live(a, owner, ws)
    const v = await live(b, viewer, ws)
    expect(v.joined.role).toBe('viewer')
    await o.socket.next((m) => m.type === 'presence_delta' && m.upserts.some((p: any) => p.userId === viewer), 5000)

    v.socket.send({ type: 'submit_operations', ...v.env(envelope(0, [textOp()], v.clientId)) })
    expect(await v.socket.next('operation_ack')).toMatchObject({ status: 'rejected', code: 'forbidden' })

    await http(a.baseUrl, owner).delete(`/workspaces/${ws}/members/${viewer}`)
    const revoked = await v.socket.next('access_revoked', 5000)
    expectValid(revoked)
    expect(revoked.reason).toBe('membership_revoked')
    expect(await v.socket.closed()).toBe(4403)
    const removal = await o.socket.next((m) => m.type === 'presence_delta' && m.removedConnectionIds.includes(v.joined.connectionId), 5000)
    expect(removal.roomRevision).toBeGreaterThan(o.presence.roomRevision)
    // 撤權後不再收到內容
    await commitRest(owner, ws)
    await o.socket.next('committed_transaction', 5000)
    expect(v.socket.pending('committed_transaction')).toEqual([])
    o.socket.close()
  })

  test('metadata: rename and role change push workspace_metadata with a new revision', async () => {
    const owner = await user('Owner')
    const editor = await user('Editor')
    const ws = await workspace(owner)
    await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
    const e = await live(b, editor, ws)
    const before = e.joined.metadataRevision as number
    await http(a.baseUrl, owner).patch(`/workspaces/${ws}`, { title: 'Renamed' })
    // join 前的成員變更事件可能晚到，會以「目前 revision」重送（同 revision 內容一致）；等待新的 revision
    const meta = await e.socket.next((m) => m.type === 'workspace_metadata' && m.metadataRevision === before + 1, 5000)
    expectValid(meta)
    expect(meta).toMatchObject({ metadataRevision: before + 1, title: 'Renamed', selfRole: 'editor' })
    expect((await http(a.baseUrl, editor).get(`/workspaces/${ws}`)).body.metadataRevision).toBe(before + 1)
    await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'viewer' })
    expect(await e.socket.next((m) => m.type === 'workspace_metadata' && m.metadataRevision === before + 2, 5000)).toMatchObject({ selfRole: 'viewer' })
    e.socket.close()
  })

  test('presence: cursor / selection deltas across gateways, unknown selection dropped, rate limited', async () => {
    const owner = await user('Owner')
    const editor = await user('Editor')
    const ws = await workspace(owner)
    await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
    const objectId = uuid()
    await commitRest(owner, ws, [textOp(objectId)])
    const o = await live(a, owner, ws)
    const e = await live(b, editor, ws)

    o.socket.send({ type: 'presence_update', ...o.env({ cursor: { x: 12.5, y: -40 }, selectedObjectIds: [objectId, uuid()], presenceSeq: 1 }) })
    const delta = await e.socket.next((m) => m.type === 'presence_delta' && m.upserts[0]?.connectionId === o.joined.connectionId && m.upserts[0]?.presenceSeq === 1, 5000)
    expectValid(delta)
    expect(delta.upserts[0]).toMatchObject({ cursor: { x: 12.5, y: -40 }, selectedObjectIds: [objectId], role: 'owner', displayName: 'Owner' })
    expect(delta.roomEpoch).toBe(e.presence.roomEpoch)

    for (let i = 2; i < 20; i++) o.socket.send({ type: 'presence_update', ...o.env({ cursor: { x: i, y: i }, selectedObjectIds: [], presenceSeq: i }) })
    const limited = await o.socket.next((m) => m.type === 'protocol_error' && m.code === 'rate_limited', 5000)
    expect(limited.retryAfterMs).toBeGreaterThan(0)
    expect(o.socket.closeCode).toBeNull()

    // heartbeat 只回 ack，不推進 revision
    o.socket.send({ type: 'heartbeat', ...o.env({ heartbeatSeq: 7 }) })
    expect((await o.socket.next('heartbeat_ack')).heartbeatSeq).toBe(7)
    o.socket.close()
    e.socket.close()
  })

  test('presence TTL and roomEpoch rebuild after transient state loss', async () => {
    const quick = await startGateway({ presenceTtlMs: 600 })
    try {
      const owner = await user('Owner')
      const editor = await user('Editor')
      const ws = await workspace(owner)
      await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
      const watcher = await live(a, owner, ws)
      const silent = await live(quick, editor, ws)
      // 不送 heartbeat：gateway 依 TTL 關閉並移除 presence
      expect((await silent.socket.next('protocol_error', 5000)).code).toBe('heartbeat_timeout')
      await watcher.socket.next((m) => m.type === 'presence_delta' && m.removedConnectionIds.includes(silent.joined.connectionId), 5000)

      // 暫態資料遺失（例如 DB crash 清空 UNLOGGED 表）：重建 room，新的 roomEpoch
      await sql!`DELETE FROM presence_rooms WHERE workspace_id = ${ws}`
      const again = await live(b, editor, ws)
      expect(again.presence.roomEpoch).not.toBe(watcher.presence.roomEpoch)
      const delta = await watcher.socket.next((m) => m.type === 'presence_delta' && m.roomEpoch === again.presence.roomEpoch, 5000)
      expect(delta.upserts[0].userId).toBe(editor)
      watcher.socket.close()
      again.socket.close()
    } finally {
      await quick.close()
    }
  })

  test('oversized transaction → resync_required instead of splitting it', async () => {
    const small = await startGateway({ maxMessageBytes: 4096 })
    try {
      const owner = await user()
      const ws = await workspace(owner)
      const s = await live(small, owner, ws)
      await commitRest(owner, ws, Array.from({ length: 20 }, () => textOp()))
      const resync = await s.socket.next('resync_required', 5000)
      expectValid(resync)
      expect(resync).toMatchObject({ reason: 'transaction_too_large', headServerSeq: 20 })
      expect(await s.socket.closed()).toBe(4409)
    } finally {
      await small.close()
    }
  })

  test('outbox recovery: unpublished rows are re-published by any gateway without duplicate delivery', async () => {
    const owner = await user()
    const ws = await workspace(owner)
    const s = await live(b, owner, ws)
    const committed = await commitRest(owner, ws)
    // 模擬「commit 後、發布前 process crash」：把 outbox 標回未發布
    await sql!`UPDATE transaction_outbox SET published_at = NULL WHERE workspace_id = ${ws}`
    const delivered = await s.socket.next('committed_transaction', 5000)
    expect(delivered.operations).toEqual(committed.operations)
    // 未發布的 outbox 會被任一 gateway 補發；重發不會造成重複送達（依 cursor 去重）
    for (let i = 0; i < 40; i++) {
      const [row] = (await sql!`SELECT count(*)::int AS n FROM transaction_outbox WHERE workspace_id = ${ws} AND published_at IS NULL`) as any
      if (row.n === 0) break
      await Bun.sleep(50)
    }
    const [row] = (await sql!`SELECT count(*)::int AS n FROM transaction_outbox WHERE workspace_id = ${ws} AND published_at IS NULL`) as any
    expect(row.n).toBe(0)
    await Bun.sleep(400)
    expect(s.socket.messages.filter((m) => m.type === 'committed_transaction')).toHaveLength(1)
    s.socket.close()
  })
})
