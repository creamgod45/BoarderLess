/**
 * 錄製即時協作（批次 A）fixtures：REST capabilities / ticket 與真 WebSocket 交換。
 * 使用兩個獨立 gateway（各自連線池與 LISTEN），跨 gateway fan-out 為真實路徑。
 * 需要測試 PostgreSQL。用法：bun run fixtures:realtime
 */
import { join } from 'node:path'
import { envelope, setupTestDatabase, uuid } from '../tests/helpers.ts'
import { http, startGateway, TestSocket, type Gateway, type ServerEvent } from '../tests/realtime-helpers.ts'
import { writeFile } from 'node:fs/promises'
import { CLIENT_MESSAGE_SCHEMAS, SERVER_EVENT_SCHEMAS } from '../src/realtime/messages.ts'
import { FixtureRecorder } from './fixture-recorder.ts'

const OUT = join(import.meta.dirname, '../tests/fixtures/contract/realtime-v1')
const sql = await setupTestDatabase()
if (!sql) throw new Error('test database unavailable (bun run db:up)')
const recorder = new FixtureRecorder(OUT)
await recorder.reset()
const a = await startGateway()
const b = await startGateway()

type Frame = { direction: 'client' | 'server'; frame: unknown }

/** 記錄雙向 frame 的 socket */
class Recorded {
  readonly socket: TestSocket
  readonly transcript: Frame[] = []
  constructor(gw: Gateway, options: { protocols?: string[] } = {}) {
    this.socket = new TestSocket(gw.wsUrl, options)
    this.socket.ws.addEventListener('message', (e) => this.transcript.push({ direction: 'server', frame: JSON.parse(String(e.data)) }))
  }
  send(frame: unknown) {
    this.transcript.push({ direction: 'client', frame })
    this.socket.send(frame)
  }
  /** 從目前位置開始的新 transcript 片段 */
  mark() {
    return this.transcript.length
  }
  since(mark: number) {
    return this.transcript.slice(mark)
  }
}

const createUser = async (name: string) => (await http(a.baseUrl).post('/users', { displayName: name })).body.id as string
const owner = await createUser('Owner')
const editor = await createUser('Editor')
const viewer = await createUser('Viewer')
const ws = (await http(a.baseUrl, owner).post('/workspaces', { title: 'Realtime fixtures' })).body.id as string
for (const id of [owner, ws, editor, viewer]) recorder.normId(id)
await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${editor}`, { role: 'editor' })
await http(a.baseUrl, owner).put(`/workspaces/${ws}/members/${viewer}`, { role: 'viewer' })

const textOp = (objectId = uuid()) => ({ kind: 'create_object', payload: { objectId, objectType: 'text', properties: { text: 'Idea' } } })
async function commitRest(ops = [textOp()]) {
  const version = (await http(a.baseUrl, owner).get(`/workspaces/${ws}`)).body.currentVersion
  return (await http(a.baseUrl, owner).post(`/workspaces/${ws}/operations`, envelope(version, ops))).body
}

async function rest(name: string, description: string, method: 'GET' | 'POST', path: string, userId: string, body?: unknown) {
  const res = method === 'GET' ? await http(a.baseUrl, userId).get(path) : await http(a.baseUrl, userId).post(path, body)
  await recorder.record(name, description, { method, path: `/api/v1${path}`, ...(body === undefined ? {} : { body }) }, res)
  return res
}

const ticketFor = async (userId: string, clientId: string, gw = a) =>
  (await http(gw.baseUrl, userId).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId })).body.ticket as string

interface Session {
  r: Recorded
  epoch: string
  clientId: string
  joined: ServerEvent
  env: (extra: object) => object
}

async function joinSession(gw: Gateway, userId: string, opts: { lastSeen?: number; ticket?: string; clientId?: string } = {}): Promise<Session> {
  const clientId = opts.clientId ?? uuid()
  const epoch = uuid()
  const r = new Recorded(gw)
  await r.socket.opened()
  r.send({
    type: 'join_workspace',
    protocolVersion: 1,
    schemaVersion: 1,
    workspaceId: ws,
    clientId,
    connectionEpoch: epoch,
    lastSeenServerSeq: opts.lastSeen ?? 0,
    metadataRevision: 1,
    ticket: opts.ticket ?? (await ticketFor(userId, clientId, gw)),
  })
  const joined = await r.socket.next()
  return { r, epoch, clientId, joined, env: (extra) => ({ protocolVersion: 1, workspaceId: ws, connectionEpoch: epoch, ...extra }) }
}

async function goLive(s: Session) {
  let applied = (s.joined.barrierServerSeq as number) ?? 0
  const barrier = applied
  applied = 0
  while (applied < barrier) applied = (await s.r.socket.next('catch_up_transaction')).toServerSeq
  s.r.send({ type: 'catch_up_applied', ...s.env({ appliedServerSeq: applied, workspaceVersion: 0, metadataRevision: 1 }) })
  await s.r.socket.next('live_ready')
  await s.r.socket.next('presence_snapshot')
}

// ---- REST ----
await rest('01-capabilities-200', 'realtime-capabilities：需 read membership；不含 ticket', 'GET', `/workspaces/${ws}/realtime-capabilities`, owner)
await rest('02-ticket-201', '一次性 ticket（Cache-Control: no-store；DB 只存 SHA-256）', 'POST', `/workspaces/${ws}/realtime-ticket`, owner, { protocolVersion: 1, clientId: uuid() })
await rest('03-ticket-unsupported-protocol-400', '不支援的 protocolVersion', 'POST', `/workspaces/${ws}/realtime-ticket`, owner, { protocolVersion: 2, clientId: uuid() })
const outsider = await createUser('Outsider')
await rest('04-capabilities-non-member-404', '非成員：404（不洩漏 Workspace 是否存在）', 'GET', `/workspaces/${ws}/realtime-capabilities`, outsider)

// ---- 空 Workspace：join → live ----
{
  const s = await joinSession(a, owner)
  await goLive(s)
  await recorder.recordSocket('10-join-empty-live', 'join → join_accepted（barrier 0）→ catch_up_applied → live_ready → presence_snapshot（含自己）', s.r.transcript)
  s.r.socket.close()
}

// ---- ticket 失敗 ----
{
  const s = await joinSession(a, owner, { ticket: 'unknown-ticket-value' })
  await s.r.socket.closed()
  await recorder.recordSocket('11-join-unknown-ticket', '未知 ticket：authentication_required；驗證前 workspaceId / connectionEpoch 為 null', s.r.transcript, 4401)

  const clientId = uuid()
  const t = await ticketFor(owner, clientId)
  const first = await joinSession(a, owner, { ticket: t, clientId })
  const reused = await joinSession(a, owner, { ticket: t, clientId })
  await reused.r.socket.closed()
  await recorder.recordSocket('12-join-ticket-used', '同一 ticket 第二次使用：ticket_used', reused.r.transcript, 4401)
  first.r.socket.close()

  const expiredClient = uuid()
  const expired = await ticketFor(owner, expiredClient)
  await sql`UPDATE realtime_tickets SET expires_at = now() - interval '1 second' WHERE client_id = ${expiredClient}`
  const e = await joinSession(a, owner, { ticket: expired, clientId: expiredClient })
  await e.r.socket.closed()
  await recorder.recordSocket('13-join-ticket-expired', '過期 ticket：ticket_expired', e.r.transcript, 4401)

  const scoped = await ticketFor(owner, uuid())
  const m = await joinSession(a, owner, { ticket: scoped })
  await m.r.socket.closed()
  await recorder.recordSocket('14-join-scope-mismatch', 'ticket 綁定的 clientId 與 join 不同：scope_mismatch（ticket 已作廢）', m.r.transcript, 4401)
}

// ---- catch-up：單 operation 與多 operation transaction ----
await commitRest()
await commitRest([textOp(), textOp()])
{
  const s = await joinSession(a, owner)
  await goLive(s)
  await recorder.recordSocket('20-join-catch-up', 'lastSeen=0：依完整 transaction 送 catch_up_transaction（單 op、兩個 op 同一 transaction），再 live_ready', s.r.transcript)
  s.r.socket.close()
}
{
  const s = await joinSession(a, owner, { lastSeen: 2 })
  await s.r.socket.next('snapshot_required')
  await recorder.recordSocket('21-snapshot-required-mid-transaction', 'checkpoint 位於 transaction 中段：snapshot_required', s.r.transcript)
  s.r.socket.close()
  const ahead = await joinSession(a, owner, { lastSeen: 999 })
  await ahead.r.socket.next('snapshot_required')
  await recorder.recordSocket('22-snapshot-required-ahead', 'checkpoint 大於 server head：snapshot_required', ahead.r.transcript)
  ahead.r.socket.close()
}
{
  const s = await joinSession(a, owner, { lastSeen: 2 })
  await s.r.socket.next('snapshot_required')
  await commitRest()
  const state = (await http(a.baseUrl, owner).get(`/workspaces/${ws}/state`)).body
  s.r.send({ type: 'catch_up_applied', ...s.env({ appliedServerSeq: state.throughServerSeq, workspaceVersion: state.workspaceVersion, metadataRevision: 1 }) })
  await s.r.socket.next('live_ready')
  await s.r.socket.next('presence_snapshot')
  await recorder.recordSocket('23-snapshot-ahead-of-barrier', 'REST snapshot 比 barrier 新：以實際 throughServerSeq 為 checkpoint，不重送已覆蓋的 transaction', s.r.transcript)
  s.r.socket.close()
}
{
  const s = await joinSession(a, owner, { lastSeen: 2 })
  await s.r.socket.next('snapshot_required')
  s.r.send({ type: 'catch_up_applied', ...s.env({ appliedServerSeq: 2, workspaceVersion: 2, metadataRevision: 1 }) })
  await s.r.socket.closed()
  await recorder.recordSocket('24-catch-up-applied-gap', 'appliedServerSeq 不在 transaction 邊界：sequence_gap + resync_required', s.r.transcript, 4409)
}

// ---- 跨 gateway fan-out 與 ack ----
{
  const onA = await joinSession(a, owner)
  await goLive(onA)
  const onB = await joinSession(b, editor)
  await goLive(onB)
  const markA = onA.r.mark()
  const markB = onB.r.mark()
  const head = (await http(a.baseUrl, owner).get(`/workspaces/${ws}`)).body.currentVersion
  const single = envelope(head, [textOp()], onA.clientId)
  onA.r.send({ type: 'submit_operations', ...onA.env(single) })
  await onA.r.socket.next('operation_ack')
  await onA.r.socket.next('committed_transaction')
  await onB.r.socket.next('committed_transaction', 5000)
  await recorder.recordSocket('30-submit-accepted', 'WS submit：operation_ack accepted（完整 committed operations，只在 DB commit 後）', onA.r.since(markA))
  await recorder.recordSocket('31-fan-out-other-gateway', '另一個 gateway 收到同一完整 transaction（committed_transaction）', onB.r.since(markB))

  let mark = onA.r.mark()
  onA.r.send({ type: 'submit_operations', ...onA.env(single) })
  await onA.r.socket.next('operation_ack')
  await recorder.recordSocket('32-submit-duplicate', '原 envelope 重送：duplicate，回原 commit 結果，不產生新 sequence', onA.r.since(mark))

  const bulk = envelope(head + 1, Array.from({ length: 200 }, () => textOp()), onA.clientId)
  mark = onA.r.mark()
  const markB2 = onB.r.mark()
  onA.r.send({ type: 'submit_operations', ...onA.env(bulk) })
  await onA.r.socket.next('operation_ack')
  await onB.r.socket.next('committed_transaction', 5000)
  await recorder.recordSocket('33-fan-out-200-operations', '200 operations 原子 transaction：一個 committed_transaction frame', onB.r.since(markB2))
  onA.r.socket.next('committed_transaction').catch(() => {})

  const target = single.operations[0]!
  const objectId = (target.payload as { objectId: string }).objectId
  mark = onA.r.mark()
  onA.r.send({
    type: 'submit_operations',
    ...onA.env(envelope(1, [{ kind: 'update_object', payload: { objectId, properties: { text: 'stale' } }, expectedObjectVersions: { [objectId]: 9 } }], onA.clientId)),
  })
  await onA.r.socket.next((m) => m.type === 'operation_ack' && m.status === 'conflict')
  await recorder.recordSocket('34-submit-conflict', '版本衝突：conflict + missingOperations；正式資料不部分寫入', onA.r.since(mark))

  mark = onA.r.mark()
  onA.r.send({ type: 'submit_operations', ...onA.env(envelope(head + 2, [{ kind: 'create_object', payload: { objectId: uuid(), objectType: 'future' } }], onA.clientId)) })
  await onA.r.socket.next((m) => m.type === 'operation_ack' && m.status === 'rejected')
  await recorder.recordSocket('35-submit-rejected', 'rejected：安全 code 與 details；正式資料未改變', onA.r.since(mark))

  const partial = envelope(head + 2, [textOp(), textOp()], onA.clientId)
  partial.operations[0]!.operationId = target.operationId
  mark = onA.r.mark()
  onA.r.send({ type: 'submit_operations', ...onA.env(partial) })
  await onA.r.socket.next((m) => m.type === 'operation_ack' && m.code === 'partial_duplicate')
  await recorder.recordSocket('36-submit-partial-duplicate', '部分 operation 已提交：明確拒絕（不默默接受剩餘部分）', onA.r.since(mark))

  // ---- presence ----
  mark = onA.r.mark()
  const markB3 = onB.r.mark()
  onA.r.send({ type: 'presence_update', ...onA.env({ cursor: { x: 120.5, y: -32 }, selectedObjectIds: [objectId, uuid()], presenceSeq: 1 }) })
  await onB.r.socket.next((m) => m.type === 'presence_delta' && m.upserts[0]?.presenceSeq === 1, 5000)
  await recorder.recordSocket('40-presence-delta', 'presence_update（world 座標；未知 selection 丟棄）→ 其他 gateway 收到 presence_delta', [...onA.r.since(mark).filter((f) => f.direction === 'client'), ...onB.r.since(markB3)])

  mark = onA.r.mark()
  for (let i = 2; i < 16; i++) onA.r.send({ type: 'presence_update', ...onA.env({ cursor: { x: i, y: i }, selectedObjectIds: [], presenceSeq: i }) })
  await onA.r.socket.next((m) => m.type === 'protocol_error' && m.code === 'rate_limited', 5000)
  await recorder.recordSocket(
    '41-presence-rate-limited',
    '超過 cursor 10 次/秒：rate_limited + retryAfterMs，不關閉連線',
    onA.r.since(mark).filter((f) => f.direction === 'server' ? (f.frame as ServerEvent).type === 'protocol_error' : true).slice(-12),
  )

  mark = onA.r.mark()
  onA.r.send({ type: 'heartbeat', ...onA.env({ heartbeatSeq: 1 }) })
  await onA.r.socket.next('heartbeat_ack')
  await recorder.recordSocket('42-heartbeat', 'heartbeat：只延續 presence TTL，不改保存 checkpoint', onA.r.since(mark).filter((f) => f.direction === 'client' || (f.frame as ServerEvent).type === 'heartbeat_ack'))

  // ---- metadata / revoke ----
  const markB4 = onB.r.mark()
  await http(a.baseUrl, owner).patch(`/workspaces/${ws}`, { title: 'Renamed' })
  await onB.r.socket.next((m) => m.type === 'workspace_metadata' && m.title === 'Renamed', 5000)
  await recorder.recordSocket('50-workspace-metadata', '改名：metadataRevision +1，送授權範圍內的 title / selfRole', onB.r.since(markB4).filter((f) => (f.frame as ServerEvent).type === 'workspace_metadata').slice(-1))

  const v = await joinSession(b, viewer)
  await goLive(v)
  let markV = v.r.mark()
  v.r.send({ type: 'submit_operations', ...v.env(envelope(0, [textOp()], v.clientId)) })
  await v.r.socket.next('operation_ack')
  await recorder.recordSocket('51-viewer-submit-forbidden', 'viewer 可讀與 presence，不能 mutation：rejected forbidden', v.r.since(markV))
  markV = v.r.mark()
  const markA2 = onA.r.mark()
  await http(a.baseUrl, owner).delete(`/workspaces/${ws}/members/${viewer}`)
  await v.r.socket.closed(5000)
  await onA.r.socket.next((m) => m.type === 'presence_delta' && m.removedConnectionIds.length > 0, 5000)
  await recorder.recordSocket('52-access-revoked', '撤銷 membership：access_revoked 後關閉（4403），不再收到內容', v.r.since(markV).filter((f) => (f.frame as ServerEvent).type === 'access_revoked'), 4403)
  await recorder.recordSocket('53-presence-removed-on-revoke', '撤權連線的 presence 被移除（新 roomRevision）', onA.r.since(markA2).filter((f) => { const e = f.frame as ServerEvent; return e.type === 'presence_delta' && e.removedConnectionIds.length > 0 }))
  onA.r.socket.close()
  onB.r.socket.close()
}

// ---- 錯誤 / 限制 ----
{
  const r = new Recorded(a)
  await r.socket.opened()
  r.send({ type: 'teleport' })
  await r.socket.closed()
  await recorder.recordSocket('60-unknown-message-type', '未知 type：invalid_message，fail closed', r.transcript, 4400)

  const clientId = uuid()
  const v2 = new Recorded(a)
  await v2.socket.opened()
  v2.send({ type: 'join_workspace', protocolVersion: 2, schemaVersion: 1, workspaceId: ws, clientId, connectionEpoch: uuid(), lastSeenServerSeq: 0, metadataRevision: 1, ticket: await ticketFor(owner, clientId) })
  await v2.socket.closed()
  await recorder.recordSocket('61-unsupported-protocol', '不支援的 protocolVersion：需更新 APP，不作一般 backoff', v2.transcript, 4426)

  const tiny = await startGateway({ maxMessageBytes: 2048 })
  const big = new Recorded(tiny)
  await big.socket.opened()
  big.socket.send('x'.repeat(4096))
  big.transcript.push({ direction: 'client', frame: '<4096-byte text frame>' })
  await big.socket.closed()
  await recorder.recordSocket('62-message-too-large', '超過 maxMessageBytes：message_too_large', big.transcript, 4413)

  const s = await (async () => {
    const clientIdTiny = uuid()
    const t = (await http(tiny.baseUrl, owner).post(`/workspaces/${ws}/realtime-ticket`, { protocolVersion: 1, clientId: clientIdTiny })).body.ticket
    return joinSession(tiny, owner, { ticket: t, clientId: clientIdTiny })
  })()
  // tiny gateway 的 catch-up 會遇到 200-op transaction
  await s.r.socket.closed(5000)
  await recorder.recordSocket(
    '63-transaction-too-large-resync',
    '單一 transaction 超過 frame 上限：resync_required（不切半 transaction），改用 REST state',
    s.r.transcript.filter((f) => f.direction === 'server' && (f.frame as ServerEvent).type === 'resync_required'),
    4409,
  )
  await tiny.close()
}

// ---- client 端序列驗證案例（由真實 frame 衍生；APP reducer 必須照 expected 處理） ----
{
  const s = await joinSession(a, owner)
  const t1 = await s.r.socket.next('catch_up_transaction')
  const t2 = await s.r.socket.next('catch_up_transaction')
  s.r.socket.close()
  const shift = (t: ServerEvent, by: number) => ({
    ...t,
    fromServerSeq: t.fromServerSeq + by,
    toServerSeq: t.toServerSeq + by,
    operations: t.operations.map((o: any) => ({ ...o, serverSeq: o.serverSeq + by })),
  })
  await recorder.recordRaw(
    '70-client-sequence-validation',
    'APP 以 applied checkpoint 驗證 transaction 序列：重複可忽略；gap / 亂序 → 暫停並 resync；同 ID 不同內容 → protocol error',
    {
      checkpointBefore: 0,
      cases: [
        { name: 'in_order', frames: [t1, t2], expected: 'apply', checkpointAfter: t2.toServerSeq },
        { name: 'duplicate_identical', frames: [t1, t1], expected: 'apply_then_ignore_duplicate', checkpointAfter: t1.toServerSeq },
        { name: 'gap', frames: [t1, shift(t2, 5)], expected: 'resync', checkpointAfter: t1.toServerSeq },
        { name: 'out_of_order', frames: [t2, t1], expected: 'resync', checkpointAfter: 0 },
        {
          name: 'same_id_different_content',
          frames: [t1, { ...t1, operations: t1.operations.map((o: any) => ({ ...o, payload: { ...o.payload, properties: { text: 'tampered' } } })) }],
          expected: 'protocol_error',
          checkpointAfter: t1.toServerSeq,
        },
      ],
    },
    200,
  )
}

// 正式訊息 JSON Schema（TypeBox 產生，與 server 驗證使用同一份）
await writeFile(
  join(OUT, 'schemas.json'),
  `${JSON.stringify({ protocolVersion: 1, subprotocol: 'boarderless.v1', client: CLIENT_MESSAGE_SCHEMAS, server: SERVER_EVENT_SCHEMAS }, null, 2)}\n`,
)

const count = await recorder.writeIndex('Realtime v1（batch A）', 'scripts/generate-realtime-fixtures.ts')
console.log(`wrote ${count} fixtures to ${OUT}`)
await a.close()
await b.close()
await sql.end()
