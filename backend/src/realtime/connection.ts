import { randomUUID } from 'node:crypto'
import type { WebSocket } from 'ws'
import type { RealtimeConfig } from '../config/env.ts'
import type { CommittedOperation } from '../models/operation.ts'
import type { WorkspaceRole } from '../models/workspace.ts'
import type { PresenceNotification } from '../repositories/realtime.repository.ts'
import type { Database } from '../repositories/index.ts'
import { hashTicket } from '../services/realtime.service.ts'
import type { OperationService } from '../services/operation.service.ts'
import { AppError } from '../utils/errors.ts'
import {
  CLOSE,
  clientValidators,
  REALTIME_PROTOCOL_VERSION,
  REALTIME_SCHEMA_VERSION,
  type CatchUpApplied,
  type ClientMessageType,
  type Heartbeat,
  type JoinWorkspace,
  type PresenceUpdate,
  type RealtimeErrorCode,
  type SubmitOperations,
} from './messages.ts'

export interface ConnectionContext {
  db: Database
  operations: OperationService
  config: RealtimeConfig
  gatewayId: string
  log: { info(obj: object, msg?: string): void; warn(obj: object, msg?: string): void; error(obj: object, msg?: string): void }
  register(connection: RealtimeConnection): void
  unregister(connection: RealtimeConnection): void
  /** 本 process 剛提交：立即發布 outbox，縮短其他 gateway 的延遲 */
  kickPublisher(): void
}

type State = 'authenticating' | 'catching_up' | 'live' | 'closed'

/** 已提交 operation → wire 格式（與 REST CommittedOperationSchema 相同欄位） */
export function toWireOperation(op: CommittedOperation) {
  return {
    serverSeq: op.serverSeq,
    operationId: op.operationId,
    transactionId: op.transactionId,
    actorId: op.actorId,
    clientId: op.clientId,
    clientSeq: op.clientSeq,
    baseVersion: op.baseVersion,
    workspaceVersion: op.workspaceVersion,
    operationType: op.operationType,
    payload: op.payload,
    schemaVersion: op.schemaVersion,
    committedAt: op.committedAt instanceof Date ? op.committedAt.toISOString() : op.committedAt,
  }
}

/** 1 秒滑動視窗的簡單限流 */
class RateWindow {
  private stamps: number[] = []
  constructor(private readonly perSecond: number) {}
  /** 允許則記錄並回傳 0，否則回傳需等待的毫秒數 */
  take(now = Date.now()): number {
    this.stamps = this.stamps.filter((t) => now - t < 1000)
    if (this.stamps.length >= this.perSecond) return 1000 - (now - this.stamps[0]!)
    this.stamps.push(now)
    return 0
  }
}

/**
 * 單一 WebSocket 連線（一條連線只加入一個 Workspace）。
 * 狀態：authenticating → catching_up → live；所有訊息與 pump 經同一佇列序列化，確保事件順序。
 * Durable 內容一律從 operation log 依 cursor 讀完整 transaction；NOTIFY 只是喚醒。
 */
export class RealtimeConnection {
  readonly connectionId = randomUUID()
  state: State = 'authenticating'
  workspaceId: string | null = null
  epoch: string | null = null
  userId: string | null = null
  clientId: string | null = null
  role: WorkspaceRole | null = null
  private displayName = ''
  /** 已送出（catch-up / committed）的 serverSeq；snapshot 路徑在 catch_up_applied 前為 null */
  private cursor: number | null = null
  private wantLive = false
  private queue: Promise<void> = Promise.resolve()
  private pumpQueued = false
  private presenceSeq = -1
  private lastCursorKey = 'null'
  private lastSelectionKey = ''
  private readonly cursorRate: RateWindow
  private readonly selectionRate: RateWindow
  private lastActivity = Date.now()
  private readonly authTimer: ReturnType<typeof setTimeout>
  private readonly livenessTimer: ReturnType<typeof setInterval>

  constructor(
    private readonly socket: WebSocket,
    private readonly ctx: ConnectionContext,
  ) {
    this.cursorRate = new RateWindow(ctx.config.cursorUpdatesPerSecond)
    this.selectionRate = new RateWindow(ctx.config.selectionUpdatesPerSecond)
    this.authTimer = setTimeout(() => {
      if (this.state === 'authenticating') this.fail('authentication_required', CLOSE.authTimeout)
    }, ctx.config.authTimeoutMs)
    this.livenessTimer = setInterval(() => {
      if (this.state !== 'authenticating' && Date.now() - this.lastActivity > ctx.config.presenceTtlMs) {
        this.fail('heartbeat_timeout', CLOSE.heartbeatTimeout)
      }
    }, Math.min(5000, ctx.config.presenceTtlMs))
    socket.on('message', (data: Buffer, isBinary: boolean) => this.onFrame(data, isBinary))
    socket.on('close', () => void this.onClosed())
    socket.on('error', () => void this.onClosed())
  }

  // ---- 入口 ----

  private onFrame(data: Buffer, isBinary: boolean): void {
    if (this.state === 'closed') return
    // Bun 的 ws 相容層不強制 maxPayload，這裡自行限制
    if (data.length > this.ctx.config.maxMessageBytes) return this.fail('message_too_large', CLOSE.tooLarge)
    if (isBinary) return this.fail('invalid_message', CLOSE.invalidMessage)
    let message: unknown
    try {
      message = JSON.parse(data.toString('utf8'))
    } catch {
      return this.fail('invalid_message', CLOSE.invalidMessage)
    }
    this.lastActivity = Date.now()
    this.enqueue(() => this.dispatch(message))
  }

  private enqueue(task: () => Promise<void>): void {
    this.queue = this.queue.then(task).catch((err) => {
      this.ctx.log.error({ err, connectionId: this.connectionId }, 'realtime connection task failed')
      this.fail('invalid_message', CLOSE.invalidMessage)
    })
  }

  private async dispatch(message: unknown): Promise<void> {
    if (this.state === 'closed') return
    const type = (message as { type?: unknown } | null)?.type
    if (typeof type !== 'string' || !Object.hasOwn(clientValidators, type)) return this.fail('invalid_message', CLOSE.invalidMessage)
    const version = (message as { protocolVersion?: unknown }).protocolVersion
    if (typeof version === 'number' && version !== REALTIME_PROTOCOL_VERSION) {
      return this.fail('unsupported_protocol_version', CLOSE.unsupported)
    }
    if (!clientValidators[type as ClientMessageType].Check(message)) return this.fail('invalid_message', CLOSE.invalidMessage)

    if (this.state === 'authenticating') {
      if (type !== 'join_workspace') return this.fail('authentication_required', CLOSE.authentication)
      return this.join(message as JoinWorkspace)
    }
    const scoped = message as { workspaceId: string; connectionEpoch: string }
    if (scoped.workspaceId !== this.workspaceId || scoped.connectionEpoch !== this.epoch) {
      return this.fail('scope_mismatch', CLOSE.invalidMessage)
    }
    switch (type as ClientMessageType) {
      case 'join_workspace':
        return this.fail('invalid_message', CLOSE.invalidMessage)
      case 'catch_up_applied':
        return this.catchUpApplied(message as CatchUpApplied)
      case 'heartbeat':
        return this.heartbeat(message as Heartbeat)
      case 'presence_update':
        return this.presenceUpdate(message as PresenceUpdate)
      case 'submit_operations':
        return this.submit(message as SubmitOperations)
    }
  }

  // ---- join / catch-up / live barrier ----

  private async join(message: JoinWorkspace): Promise<void> {
    if (message.schemaVersion !== REALTIME_SCHEMA_VERSION) return this.fail('unsupported_schema_version', CLOSE.unsupported)
    const repos = this.ctx.db.repos
    const hash = hashTicket(message.ticket)
    const ticket = await repos.realtime.consumeTicket(hash)
    if (!ticket) {
      const existing = await repos.realtime.findTicket(hash)
      const code: RealtimeErrorCode = !existing ? 'authentication_required' : existing.consumedAt ? 'ticket_used' : 'ticket_expired'
      return this.fail(code, CLOSE.authentication)
    }
    if (
      ticket.workspaceId !== message.workspaceId ||
      ticket.clientId !== message.clientId ||
      ticket.protocolVersion !== message.protocolVersion
    ) {
      return this.fail('scope_mismatch', CLOSE.authentication)
    }
    const access = await this.loadAccess(message.workspaceId, ticket.userId)
    if (!access) return this.fail('access_revoked', CLOSE.accessRevoked)

    clearTimeout(this.authTimer)
    this.workspaceId = message.workspaceId
    this.epoch = message.connectionEpoch
    this.userId = ticket.userId
    this.clientId = ticket.clientId
    this.role = access.role
    this.displayName = access.displayName
    this.state = 'catching_up'
    // 先註冊訂閱再讀 head：之後的 commit 一定會喚醒 pump，不會漏掉 join 途中提交
    this.ctx.register(this)

    const workspace = access.workspace
    const barrier = workspace.lastServerSeq
    this.send({
      type: 'join_accepted',
      ...this.envelope(),
      connectionId: this.connectionId,
      userId: this.userId,
      role: this.role,
      barrierServerSeq: barrier,
      workspaceVersion: workspace.currentVersion,
      metadataRevision: workspace.metadataRevision,
    })

    const lastSeen = message.lastSeenServerSeq
    const reason =
      lastSeen > barrier
        ? 'checkpoint_ahead_of_server'
        : barrier - lastSeen > this.ctx.config.maxCatchUpOperations
          ? 'catch_up_too_large'
          : !(await repos.realtime.isTransactionBoundary(this.workspaceId, lastSeen))
            ? 'checkpoint_not_at_transaction_boundary'
            : null
    if (reason) {
      this.send({
        type: 'snapshot_required',
        ...this.envelope(),
        barrierServerSeq: barrier,
        workspaceVersion: workspace.currentVersion,
        metadataRevision: workspace.metadataRevision,
        reason,
      })
      return // cursor 等 client 以 REST snapshot 的 throughServerSeq 回報
    }
    this.cursor = lastSeen
    await this.pumpNow()
  }

  private async catchUpApplied(message: CatchUpApplied): Promise<void> {
    if (this.state !== 'catching_up') return this.fail('invalid_message', CLOSE.invalidMessage)
    const workspace = await this.ctx.db.repos.workspaces.findById(this.workspaceId!)
    if (!workspace) return this.revoke('workspace_deleted')
    const applied = message.appliedServerSeq
    if (applied > workspace.lastServerSeq || !(await this.ctx.db.repos.realtime.isTransactionBoundary(this.workspaceId!, applied))) {
      this.send({ type: 'protocol_error', ...this.envelope(), code: 'sequence_gap' })
      return this.resync('sequence_gap', workspace.lastServerSeq, workspace.currentVersion)
    }
    // snapshot 可能比 barrier 新：以 client 實際套用的 checkpoint 為準，只補其後的 log，不降回 barrier
    if (this.cursor === null || applied > this.cursor) this.cursor = applied
    if (workspace.lastServerSeq - this.cursor > this.ctx.config.maxCatchUpOperations) {
      return this.resync('too_many_pending_transactions', workspace.lastServerSeq, workspace.currentVersion)
    }
    this.wantLive = true
    await this.pumpNow()
  }

  /** 由 hub（NOTIFY / 輪詢）呼叫；合併重複喚醒 */
  requestPump(): void {
    if (this.pumpQueued || this.state === 'closed' || this.state === 'authenticating') return
    this.pumpQueued = true
    this.enqueue(async () => {
      this.pumpQueued = false
      await this.pumpNow()
    })
  }

  /**
   * 從 operation log 依 cursor 送出完整 transaction；每次都重新檢查 ACL（撤權後不再送任何內容）。
   * 落後過多、單筆過大或慢 consumer → resync_required 並關閉，不切半 transaction、不無限緩衝。
   */
  private async pumpNow(): Promise<void> {
    if (this.state === 'closed' || this.cursor === null) return
    const access = await this.loadAccess(this.workspaceId!, this.userId!)
    if (!access) return this.revoke(access === null ? 'membership_revoked' : 'workspace_deleted')
    const { workspace } = access
    const head = workspace.lastServerSeq

    while (this.cursor < head && !this.isClosed()) {
      const ops = await this.ctx.db.repos.operations.listAfterSeq(this.workspaceId!, this.cursor, 1000)
      const groups = groupTransactions(ops)
      if (ops.length === 1000 && groups.length > 1) groups.pop() // 尾端可能被 LIMIT 截斷
      if (groups.length === 0) break
      if (this.state === 'live' && countTransactionsAhead(groups, head) > this.ctx.config.maxPendingTransactions) {
        return this.resync('too_many_pending_transactions', head, workspace.currentVersion)
      }
      for (const group of groups) {
        const event = {
          type: this.state === 'live' ? 'committed_transaction' : 'catch_up_transaction',
          ...this.envelope(),
          transactionId: group[0]!.transactionId,
          fromServerSeq: group[0]!.serverSeq,
          toServerSeq: group.at(-1)!.serverSeq,
          workspaceVersion: group[0]!.workspaceVersion,
          operations: group.map(toWireOperation),
        }
        const frame = JSON.stringify(event)
        if (Buffer.byteLength(frame) > this.ctx.config.maxMessageBytes) {
          return this.resync('transaction_too_large', head, workspace.currentVersion)
        }
        if (!this.sendRaw(frame)) return
        this.cursor = event.toServerSeq
        if (this.socket.bufferedAmount > this.ctx.config.maxBufferedBytes) {
          return this.resync('slow_consumer', head, workspace.currentVersion)
        }
      }
    }

    if (this.wantLive && this.state === 'catching_up' && this.cursor >= head) {
      this.state = 'live'
      this.send({
        type: 'live_ready',
        ...this.envelope(),
        throughServerSeq: this.cursor,
        workspaceVersion: workspace.currentVersion,
        metadataRevision: workspace.metadataRevision,
        connectionId: this.connectionId,
      })
      await this.joinPresence()
    }
  }

  // ---- submit ----

  private async submit(message: SubmitOperations): Promise<void> {
    if (this.state !== 'live') return void this.send({ type: 'protocol_error', ...this.envelope(), code: 'not_live' })
    if (message.clientId !== this.clientId) return this.fail('scope_mismatch', CLOSE.invalidMessage)
    const operationIds = message.operations.map((o) => o.operationId)
    const ack = { type: 'operation_ack', ...this.envelope(), transactionId: message.transactionId, operationIds }
    try {
      // 與 REST 同一個 service：ACL、schema、invariant、冪等、交易完全相同
      const result = await this.ctx.operations.submit(this.userId!, {
        protocolVersion: message.protocolVersion,
        workspaceId: this.workspaceId!,
        clientId: message.clientId,
        transactionId: message.transactionId,
        baseVersion: message.baseVersion,
        operations: message.operations,
      })
      if (result.status === 'conflict') {
        this.send({ ...ack, ...result, missingOperations: result.missingOperations.map(toWireOperation) })
      } else {
        this.send({ ...ack, ...result, operations: result.operations.map(toWireOperation) })
        if (result.status === 'accepted') this.ctx.kickPublisher()
      }
    } catch (error) {
      if (!(error instanceof AppError)) {
        this.ctx.log.error({ err: error, connectionId: this.connectionId }, 'realtime submit failed')
        return void this.send({ ...ack, status: 'rejected', code: 'internal_error' })
      }
      this.send({
        ...ack,
        status: 'rejected',
        code: error.code,
        // 與 REST 422 / 409 回給同一 actor 的 details 相同（ID、欄位路徑、支援清單），不含原始 frame
        ...(error.details !== undefined ? { details: error.details } : {}),
      })
      if (error.statusCode === 404) await this.revoke('membership_revoked')
    }
  }

  // ---- presence ----

  private presenceExpiry(): Date {
    return new Date(Date.now() + this.ctx.config.presenceTtlMs)
  }

  private async joinPresence(): Promise<void> {
    await this.ctx.db.transaction((repos) =>
      repos.realtime.upsertPeer({
        connectionId: this.connectionId,
        workspaceId: this.workspaceId!,
        userId: this.userId!,
        clientId: this.clientId!,
        displayName: this.displayName,
        role: this.role!,
        cursor: null,
        selectedObjectIds: [],
        presenceSeq: 0,
        gatewayId: this.ctx.gatewayId,
        expiresAt: this.presenceExpiry(),
      }),
    )
    const { room, peers } = await this.ctx.db.repos.realtime.snapshot(this.workspaceId!)
    this.send({ type: 'presence_snapshot', ...this.envelope(), roomEpoch: room.roomEpoch, roomRevision: room.roomRevision, peers })
  }

  private async presenceUpdate(message: PresenceUpdate): Promise<void> {
    if (this.state !== 'live') return void this.send({ type: 'protocol_error', ...this.envelope(), code: 'not_live' })
    if (message.presenceSeq <= this.presenceSeq) return // 舊的 / 重複的更新直接丟棄
    if (message.selectedObjectIds.length > this.ctx.config.maxSelectionIds) return this.fail('invalid_message', CLOSE.invalidMessage)
    const cursorKey = JSON.stringify(message.cursor)
    const selectionKey = message.selectedObjectIds.join(',')
    const waits = [
      cursorKey !== this.lastCursorKey ? this.cursorRate.take() : 0,
      selectionKey !== this.lastSelectionKey ? this.selectionRate.take() : 0,
    ]
    const wait = Math.max(...waits)
    if (wait > 0) return void this.send({ type: 'protocol_error', ...this.envelope(), code: 'rate_limited', retryAfterMs: wait })

    this.presenceSeq = message.presenceSeq
    this.lastCursorKey = cursorKey
    this.lastSelectionKey = selectionKey
    const access = await this.loadAccess(this.workspaceId!, this.userId!)
    if (!access) return this.revoke('membership_revoked')
    const selected = await this.ctx.db.repos.realtime.filterActiveObjectIds(this.workspaceId!, message.selectedObjectIds)
    await this.ctx.db.transaction((repos) =>
      repos.realtime.upsertPeer({
        connectionId: this.connectionId,
        workspaceId: this.workspaceId!,
        userId: this.userId!,
        clientId: this.clientId!,
        displayName: this.displayName,
        role: access.role,
        cursor: message.cursor,
        selectedObjectIds: selected,
        presenceSeq: message.presenceSeq,
        gatewayId: this.ctx.gatewayId,
        expiresAt: this.presenceExpiry(),
      }),
    )
  }

  private async heartbeat(message: Heartbeat): Promise<void> {
    this.send({ type: 'heartbeat_ack', ...this.envelope(), heartbeatSeq: message.heartbeatSeq })
    if (this.state === 'live') await this.ctx.db.repos.realtime.touchPeer(this.connectionId, this.presenceExpiry())
  }

  /** 由 hub 轉送 PRESENCE_CHANNEL 的 delta（只送 live 連線） */
  deliverPresence(notification: PresenceNotification): void {
    if (this.state !== 'live') return
    this.enqueue(async () => {
      this.send({
        type: 'presence_delta',
        ...this.envelope(),
        roomEpoch: notification.roomEpoch,
        roomRevision: notification.roomRevision,
        upserts: notification.upserts,
        removedConnectionIds: notification.removedConnectionIds,
      })
    })
  }

  // ---- metadata / revoke ----

  /** metadataRevision 變更：重新授權；撤權即關閉，否則送授權範圍內的 metadata */
  requestMetadataRefresh(): void {
    if (this.state === 'closed' || this.state === 'authenticating') return
    this.enqueue(async () => {
      const access = await this.loadAccess(this.workspaceId!, this.userId!)
      if (!access) return this.revoke(access === null ? 'membership_revoked' : 'workspace_deleted')
      const roleChanged = access.role !== this.role
      this.role = access.role
      this.send({
        type: 'workspace_metadata',
        ...this.envelope(),
        metadataRevision: access.workspace.metadataRevision,
        title: access.workspace.title,
        selfRole: access.role,
      })
      if (roleChanged && this.state === 'live') {
        await this.ctx.db.transaction((repos) =>
          repos.realtime.upsertPeer({
            connectionId: this.connectionId,
            workspaceId: this.workspaceId!,
            userId: this.userId!,
            clientId: this.clientId!,
            displayName: this.displayName,
            role: access.role,
            cursor: null,
            selectedObjectIds: [],
            presenceSeq: Math.max(this.presenceSeq, 0),
            gatewayId: this.ctx.gatewayId,
            expiresAt: this.presenceExpiry(),
          }),
        )
      }
    })
  }

  /**
   * 回傳 null = 非成員（撤權）、undefined = Workspace 已刪除。
   */
  private async loadAccess(workspaceId: string, userId: string) {
    const repos = this.ctx.db.repos
    const workspace = await repos.workspaces.findById(workspaceId)
    if (!workspace) return undefined
    const member = await repos.members.findActive(workspaceId, userId)
    const user = member ? await repos.users.findActiveById(userId) : null
    if (!member || !user) return null
    return { workspace, role: member.role, displayName: user.displayName.slice(0, 100) }
  }

  private async revoke(reason: 'membership_revoked' | 'workspace_deleted'): Promise<void> {
    if (this.state === 'closed') return
    this.send({ type: 'access_revoked', ...this.envelope(), reason })
    await this.close(CLOSE.accessRevoked)
  }

  private async resync(reason: string, headServerSeq: number, workspaceVersion: number): Promise<void> {
    this.send({ type: 'resync_required', ...this.envelope(), reason, headServerSeq, workspaceVersion })
    await this.close(CLOSE.resync)
  }

  // ---- 傳送 / 關閉 ----

  /** await 之後狀態可能已改變（避免 TS 依先前判斷縮小型別） */
  private isClosed(): boolean {
    return this.state === 'closed'
  }

  private envelope() {
    return { protocolVersion: REALTIME_PROTOCOL_VERSION, workspaceId: this.workspaceId!, connectionEpoch: this.epoch! }
  }

  private send(event: object): boolean {
    return this.sendRaw(JSON.stringify(event))
  }

  private sendRaw(frame: string): boolean {
    if (this.state === 'closed' || this.socket.readyState !== this.socket.OPEN) return false
    this.socket.send(frame)
    return true
  }

  /** 錯誤碼 + 關閉。驗證前不確定 workspace / epoch，這兩個欄位為 null */
  fail(code: RealtimeErrorCode, closeCode: number): void {
    if (this.state === 'closed') return
    this.send({
      type: 'protocol_error',
      protocolVersion: REALTIME_PROTOCOL_VERSION,
      workspaceId: this.state === 'authenticating' ? null : this.workspaceId,
      connectionEpoch: this.state === 'authenticating' ? null : this.epoch,
      code,
    })
    void this.close(closeCode)
  }

  async close(code: number): Promise<void> {
    if (this.state === 'closed') return
    const wasLive = this.state === 'live'
    this.state = 'closed'
    clearTimeout(this.authTimer)
    clearInterval(this.livenessTimer)
    this.ctx.unregister(this)
    try {
      this.socket.close(code)
    } catch {}
    if (wasLive) await this.leavePresence()
  }

  private async onClosed(): Promise<void> {
    if (this.state === 'closed') return
    await this.close(CLOSE.goingAway)
  }

  private async leavePresence(): Promise<void> {
    try {
      await this.ctx.db.transaction((repos) => repos.realtime.removePeers(this.workspaceId!, [this.connectionId]))
    } catch (err) {
      // 失敗時由 TTL 收斂
      this.ctx.log.warn({ err, connectionId: this.connectionId }, 'presence leave failed')
    }
  }
}

/** 連續 serverSeq 依 transactionId 分組（同一 transaction 的 operation 在 log 中連續） */
export function groupTransactions(ops: CommittedOperation[]): CommittedOperation[][] {
  const groups: CommittedOperation[][] = []
  for (const op of ops) {
    const last = groups.at(-1)
    if (last && last[0]!.transactionId === op.transactionId) last.push(op)
    else groups.push([op])
  }
  return groups
}

function countTransactionsAhead(groups: CommittedOperation[][], head: number): number {
  // 本批之後仍可能有更多；本批數量已足以判斷是否超過上限
  return groups.length + (groups.at(-1)!.at(-1)!.serverSeq < head ? 1 : 0)
}
