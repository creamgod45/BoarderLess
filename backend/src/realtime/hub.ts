import { randomUUID } from 'node:crypto'
import type postgres from 'postgres'
import type { WebSocket } from 'ws'
import type { RealtimeConfig } from '../config/env.ts'
import type { Database } from '../repositories/index.ts'
import {
  OPERATIONS_CHANNEL,
  PRESENCE_CHANNEL,
  type OutboxNotification,
  type PresenceNotification,
} from '../repositories/realtime.repository.ts'
import type { OperationService } from '../services/operation.service.ts'
import { RealtimeConnection, type ConnectionContext } from './connection.ts'
import { CLOSE } from './messages.ts'

type Log = ConnectionContext['log']

/**
 * 每個 API process 一個 gateway hub。
 *
 * 一致性模型：
 * - 權威歷史是 PostgreSQL operation log；commit 同一 transaction 寫入 transaction_outbox。
 * - publisher（任何 gateway 都可執行，SKIP LOCKED）把 outbox 標為已發布並在同一 DB transaction 內 pg_notify；
 *   commit 後 crash 未發布的事件由下一輪任何 gateway 補發。
 * - 各 gateway LISTEN 後只把 NOTIFY 當喚醒，依每條連線的 cursor 從 log 讀完整 transaction；
 *   LISTEN 重連（onlisten）與定期輪詢會補一次全部連線，避免漏通知。
 * - Presence 存在 UNLOGGED 表，delta 以 NOTIFY 跨 gateway 傳播；TTL 由任何 gateway 的 sweeper 收斂。
 */
export class RealtimeHub {
  readonly gatewayId = randomUUID()
  private readonly byWorkspace = new Map<string, Set<RealtimeConnection>>()
  private readonly all = new Set<RealtimeConnection>()
  private unauthenticated = 0
  private timers: ReturnType<typeof setInterval>[] = []
  private listeners: { unlisten(): Promise<void> }[] = []
  private publishing = false
  private publishAgain = false
  private stopped = false

  constructor(
    private readonly db: Database,
    private readonly sql: postgres.Sql,
    private readonly operations: OperationService,
    readonly config: RealtimeConfig,
    private readonly log: Log,
  ) {}

  async start(): Promise<void> {
    this.listeners.push(
      await this.sql.listen(
        OPERATIONS_CHANNEL,
        (payload) => this.onOutbox(payload),
        () => this.pumpAll(),
      ),
      await this.sql.listen(PRESENCE_CHANNEL, (payload) => this.onPresence(payload)),
    )
    this.timers.push(
      setInterval(() => this.kickPublisher(), this.config.publishIntervalMs),
      // 安全網：漏掉 NOTIFY 時仍會補送，並重新檢查 ACL
      setInterval(() => this.pumpAll(), this.config.pollIntervalMs),
      setInterval(() => void this.sweep(), Math.min(5000, this.config.presenceTtlMs)),
    )
    this.kickPublisher()
  }

  async stop(): Promise<void> {
    this.stopped = true
    for (const t of this.timers) clearInterval(t)
    this.timers = []
    await Promise.all([...this.all].map((c) => c.close(CLOSE.goingAway)))
    await Promise.all(this.listeners.map((l) => l.unlisten().catch(() => {})))
    this.listeners = []
  }

  /** 新的 WebSocket：未驗證連線數有上限 */
  accept(socket: WebSocket): void {
    if (this.stopped) return void socket.close(CLOSE.goingAway)
    if (this.unauthenticated >= this.config.maxUnauthenticatedSockets) return void socket.close(CLOSE.tooManyUnauthenticated)
    this.unauthenticated += 1
    let counted = true
    const release = () => {
      if (counted) {
        counted = false
        this.unauthenticated -= 1
      }
    }
    const connection = new RealtimeConnection(socket, {
      db: this.db,
      operations: this.operations,
      config: this.config,
      gatewayId: this.gatewayId,
      log: this.log,
      register: (c) => {
        release()
        this.register(c)
      },
      unregister: (c) => {
        release()
        this.unregister(c)
      },
      kickPublisher: () => this.kickPublisher(),
    })
    this.all.add(connection)
  }

  private register(connection: RealtimeConnection): void {
    const set = this.byWorkspace.get(connection.workspaceId!) ?? new Set()
    set.add(connection)
    this.byWorkspace.set(connection.workspaceId!, set)
  }

  private unregister(connection: RealtimeConnection): void {
    this.all.delete(connection)
    if (!connection.workspaceId) return
    const set = this.byWorkspace.get(connection.workspaceId)
    set?.delete(connection)
    if (set?.size === 0) this.byWorkspace.delete(connection.workspaceId)
  }

  private onOutbox(payload: string): void {
    let event: OutboxNotification
    try {
      event = JSON.parse(payload) as OutboxNotification
    } catch {
      return
    }
    for (const connection of this.byWorkspace.get(event.workspaceId) ?? []) {
      if (event.eventType === 'workspace_metadata') connection.requestMetadataRefresh()
      else connection.requestPump()
    }
  }

  private onPresence(payload: string): void {
    let event: PresenceNotification
    try {
      event = JSON.parse(payload) as PresenceNotification
    } catch {
      return
    }
    for (const connection of this.byWorkspace.get(event.workspaceId) ?? []) connection.deliverPresence(event)
  }

  private pumpAll(): void {
    for (const set of this.byWorkspace.values()) for (const connection of set) connection.requestPump()
  }

  /** 發布 outbox；同時只跑一輪，期間的呼叫合併成下一輪 */
  kickPublisher(): void {
    if (this.stopped) return
    if (this.publishing) {
      this.publishAgain = true
      return
    }
    this.publishing = true
    void (async () => {
      try {
        do {
          this.publishAgain = false
          while ((await this.db.transaction((repos) => repos.realtime.publishOutbox(100))) === 100) {}
        } while (this.publishAgain && !this.stopped)
      } catch (err) {
        this.log.warn({ err }, 'outbox publish failed; will retry')
      } finally {
        this.publishing = false
      }
    })()
  }

  /** Presence TTL：移除逾時 peer（異常 kill / gateway crash 的收斂路徑）並清掉過期 ticket */
  async sweep(): Promise<void> {
    try {
      const expired = await this.db.repos.realtime.listExpired(500)
      const byWorkspace = new Map<string, string[]>()
      for (const row of expired) byWorkspace.set(row.workspaceId, [...(byWorkspace.get(row.workspaceId) ?? []), row.connectionId])
      for (const [workspaceId, ids] of byWorkspace) {
        await this.db.transaction((repos) => repos.realtime.removePeers(workspaceId, ids))
      }
      await this.db.repos.realtime.purgeTickets(3600)
    } catch (err) {
      this.log.warn({ err }, 'presence sweep failed')
    }
  }
}
