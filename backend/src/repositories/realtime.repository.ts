import { json, type Db } from '../db/client.ts'
import type { PresencePeer } from '../realtime/messages.ts'

export const OPERATIONS_CHANNEL = 'boarderless_outbox'
export const PRESENCE_CHANNEL = 'boarderless_presence'

export interface RealtimeTicket {
  ticketHash: string
  workspaceId: string
  userId: string
  clientId: string
  protocolVersion: number
  expiresAt: Date
  consumedAt: Date | null
}

export interface NewPresencePeer extends PresencePeer {
  workspaceId: string
  gatewayId: string
  expiresAt: Date
}

export interface PresenceRoom {
  roomEpoch: string
  roomRevision: number
}

/** 發布到 PRESENCE_CHANNEL 的 delta（gateway 轉成 presence_delta） */
export interface PresenceNotification extends PresenceRoom {
  workspaceId: string
  upserts: PresencePeer[]
  removedConnectionIds: string[]
}

/** 發布到 OPERATIONS_CHANNEL 的 outbox 喚醒；內容不是歷史，gateway 一律從 operation log 讀完整 transaction */
export interface OutboxNotification {
  eventType: string
  workspaceId: string
  serverSeq: number
}

interface PeerRow {
  connectionId: string
  userId: string
  clientId: string
  displayName: string
  role: PresencePeer['role']
  cursor: PresencePeer['cursor']
  selectedObjectIds: string[]
  presenceSeq: number
}

const toPeer = (row: PeerRow): PresencePeer => ({
  connectionId: row.connectionId,
  userId: row.userId,
  clientId: row.clientId,
  displayName: row.displayName,
  role: row.role,
  cursor: row.cursor,
  selectedObjectIds: row.selectedObjectIds,
  presenceSeq: row.presenceSeq,
})

export class RealtimeRepository {
  constructor(private readonly db: Db) {}

  // ---- tickets ----

  async insertTicket(ticket: Omit<RealtimeTicket, 'consumedAt'>): Promise<void> {
    await this.db`
      INSERT INTO realtime_tickets (ticket_hash, workspace_id, user_id, client_id, protocol_version, expires_at)
      VALUES (${ticket.ticketHash}, ${ticket.workspaceId}, ${ticket.userId}, ${ticket.clientId}, ${ticket.protocolVersion}, ${ticket.expiresAt})
    `
  }

  /** 原子 consume：只有未使用且未過期的 ticket 會回傳；失敗時呼叫 findTicket 判斷原因 */
  async consumeTicket(ticketHash: string): Promise<RealtimeTicket | null> {
    const [row] = await this.db<RealtimeTicket[]>`
      UPDATE realtime_tickets SET consumed_at = now()
      WHERE ticket_hash = ${ticketHash} AND consumed_at IS NULL AND expires_at > now()
      RETURNING *
    `
    return row ?? null
  }

  async findTicket(ticketHash: string): Promise<RealtimeTicket | null> {
    const [row] = await this.db<RealtimeTicket[]>`SELECT * FROM realtime_tickets WHERE ticket_hash = ${ticketHash}`
    return row ?? null
  }

  async purgeTickets(olderThanSeconds: number): Promise<void> {
    await this.db`DELETE FROM realtime_tickets WHERE expires_at < now() - make_interval(secs => ${olderThanSeconds})`
  }

  // ---- outbox publish ----

  /**
   * Claim 一批未發布的 outbox（SKIP LOCKED：多個 gateway 可同時執行），標記 published，並在同一 DB transaction 內 pg_notify。
   * NOTIFY 只在 commit 時送出，與 published 標記原子一致；commit 前 crash 則下一輪重送。須在 transaction 內呼叫。
   */
  async publishOutbox(limit: number): Promise<number> {
    const rows = await this.db<{ id: number; workspaceId: string; serverSeq: number; eventType: string }[]>`
      UPDATE transaction_outbox SET published_at = now(), attempt_count = attempt_count + 1
      WHERE id IN (
        SELECT id FROM transaction_outbox WHERE published_at IS NULL ORDER BY id LIMIT ${limit} FOR UPDATE SKIP LOCKED
      )
      RETURNING id, workspace_id, server_seq, event_type
    `
    for (const row of rows) {
      const payload: OutboxNotification = { eventType: row.eventType, workspaceId: row.workspaceId, serverSeq: row.serverSeq }
      await this.db`SELECT pg_notify(${OPERATIONS_CHANNEL}, ${JSON.stringify(payload)})`
    }
    return rows.length
  }

  // ---- transaction boundary ----

  /** seq 是否位於完整 transaction 邊界（0、或 seq 與 seq+1 不屬於同一 transaction） */
  async isTransactionBoundary(workspaceId: string, seq: number): Promise<boolean> {
    if (seq === 0) return true
    const rows = await this.db<{ serverSeq: number; transactionId: string }[]>`
      SELECT server_seq, transaction_id FROM workspace_operations
      WHERE workspace_id = ${workspaceId} AND server_seq IN (${seq}, ${seq + 1})
    `
    const at = rows.find((r) => r.serverSeq === seq)
    const next = rows.find((r) => r.serverSeq === seq + 1)
    if (!at) return false
    return !next || next.transactionId !== at.transactionId
  }

  // ---- presence（暫態，UNLOGGED） ----

  /** 推進 room revision（不存在時以新 roomEpoch 建立）；取得 room row lock，序列化同 room 的變更 */
  private async bumpRoom(workspaceId: string): Promise<PresenceRoom> {
    await this.db`
      INSERT INTO presence_rooms (workspace_id, room_epoch) VALUES (${workspaceId}, gen_random_uuid())
      ON CONFLICT DO NOTHING
    `
    const [room] = await this.db<PresenceRoom[]>`
      UPDATE presence_rooms SET room_revision = room_revision + 1 WHERE workspace_id = ${workspaceId}
      RETURNING room_epoch, room_revision
    `
    return room!
  }

  private async notifyPresence(notification: PresenceNotification): Promise<void> {
    await this.db`SELECT pg_notify(${PRESENCE_CHANNEL}, ${JSON.stringify(notification)})`
  }

  /** 新增或更新 peer，room revision +1 並發布 delta。須在 transaction 內呼叫。 */
  async upsertPeer(peer: NewPresencePeer): Promise<PresenceNotification> {
    const room = await this.bumpRoom(peer.workspaceId)
    await this.db`
      INSERT INTO presence_peers (
        connection_id, workspace_id, user_id, client_id, display_name, role, cursor, selected_object_ids,
        presence_seq, gateway_id, expires_at, updated_at
      ) VALUES (
        ${peer.connectionId}, ${peer.workspaceId}, ${peer.userId}, ${peer.clientId}, ${peer.displayName}, ${peer.role},
        ${peer.cursor === null ? null : json(this.db, peer.cursor)}, ${peer.selectedObjectIds}::uuid[],
        ${peer.presenceSeq}, ${peer.gatewayId}, ${peer.expiresAt}, now()
      )
      ON CONFLICT (connection_id) DO UPDATE SET
        role = EXCLUDED.role, cursor = EXCLUDED.cursor, selected_object_ids = EXCLUDED.selected_object_ids,
        presence_seq = EXCLUDED.presence_seq, expires_at = EXCLUDED.expires_at, updated_at = now()
    `
    const notification: PresenceNotification = {
      workspaceId: peer.workspaceId,
      ...room,
      upserts: [toPeer(peer)],
      removedConnectionIds: [],
    }
    await this.notifyPresence(notification)
    return notification
  }

  /** 移除 peers（離開 / 撤權 / TTL），有移除才推進 revision 並發布。須在 transaction 內呼叫。 */
  async removePeers(workspaceId: string, connectionIds: string[]): Promise<PresenceNotification | null> {
    if (connectionIds.length === 0) return null
    const removed = await this.db<{ connectionId: string }[]>`
      DELETE FROM presence_peers WHERE workspace_id = ${workspaceId} AND connection_id = ANY(${connectionIds}::uuid[])
      RETURNING connection_id
    `
    if (removed.length === 0) return null
    const room = await this.bumpRoom(workspaceId)
    const notification: PresenceNotification = {
      workspaceId,
      ...room,
      upserts: [],
      removedConnectionIds: removed.map((r) => r.connectionId),
    }
    await this.notifyPresence(notification)
    return notification
  }

  /** heartbeat：只延長 TTL，不推進 revision */
  async touchPeer(connectionId: string, expiresAt: Date): Promise<void> {
    await this.db`UPDATE presence_peers SET expires_at = ${expiresAt} WHERE connection_id = ${connectionId}`
  }

  async listExpired(limit: number): Promise<{ workspaceId: string; connectionId: string }[]> {
    return this.db<{ workspaceId: string; connectionId: string }[]>`
      SELECT workspace_id, connection_id FROM presence_peers WHERE expires_at < now() ORDER BY expires_at LIMIT ${limit}
    `
  }

  async snapshot(workspaceId: string): Promise<{ room: PresenceRoom; peers: PresencePeer[] }> {
    await this.db`
      INSERT INTO presence_rooms (workspace_id, room_epoch) VALUES (${workspaceId}, gen_random_uuid())
      ON CONFLICT DO NOTHING
    `
    const [room] = await this.db<PresenceRoom[]>`
      SELECT room_epoch, room_revision FROM presence_rooms WHERE workspace_id = ${workspaceId}
    `
    const peers = await this.db<PeerRow[]>`
      SELECT * FROM presence_peers WHERE workspace_id = ${workspaceId} AND expires_at >= now() ORDER BY connection_id
    `
    return { room: room!, peers: peers.map(toPeer) }
  }

  /** 選取 ID 只保留同 Workspace 仍存在的物件；未知 / 已刪除丟棄，不建立或鎖定物件 */
  async filterActiveObjectIds(workspaceId: string, ids: string[]): Promise<string[]> {
    if (ids.length === 0) return []
    const rows = await this.db<{ objectId: string }[]>`
      SELECT object_id FROM canvas_objects
      WHERE workspace_id = ${workspaceId} AND object_id = ANY(${ids}::uuid[]) AND deleted_at IS NULL
    `
    const found = new Set(rows.map((r) => r.objectId))
    return ids.filter((id, i) => found.has(id) && ids.indexOf(id) === i)
  }
}
