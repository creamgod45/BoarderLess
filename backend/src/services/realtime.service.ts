import { createHash, randomBytes } from 'node:crypto'
import type { RealtimeConfig } from '../config/env.ts'
import type { Database } from '../repositories/index.ts'
import { REALTIME_PROTOCOL_VERSION, REALTIME_SCHEMA_VERSION } from '../realtime/messages.ts'
import { badRequest } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export const hashTicket = (ticket: string) => createHash('sha256').update(ticket, 'utf8').digest('hex')

/** REST 端：能力宣告與一次性 join ticket（BACKEND_REALTIME_API_SPEC §2） */
export class RealtimeService {
  constructor(
    private readonly db: Database,
    private readonly config: RealtimeConfig,
  ) {}

  async capabilities(workspaceId: string, userId: string, webSocketUrl: string) {
    const { workspace } = await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const c = this.config
    return {
      protocolVersions: [REALTIME_PROTOCOL_VERSION],
      schemaVersions: [REALTIME_SCHEMA_VERSION],
      webSocketUrl,
      subprotocol: 'boarderless.v1',
      operationFanOut: true,
      presence: true,
      metadataRevision: workspace.metadataRevision,
      maxMessageBytes: c.maxMessageBytes,
      maxPendingTransactions: c.maxPendingTransactions,
      maxCatchUpOperations: c.maxCatchUpOperations,
      maxOperationsPerTransaction: 200,
      heartbeatIntervalMs: c.heartbeatIntervalMs,
      presenceTtlMs: c.presenceTtlMs,
      ticketTtlSeconds: c.ticketTtlSeconds,
      authTimeoutMs: c.authTimeoutMs,
      maxSelectionIds: c.maxSelectionIds,
      cursorUpdatesPerSecond: c.cursorUpdatesPerSecond,
      selectionUpdatesPerSecond: c.selectionUpdatesPerSecond,
    }
  }

  /** 綁定 authenticated user + client + workspace + protocol；只回傳一次明文，DB 只存 SHA-256 */
  async issueTicket(workspaceId: string, userId: string, input: { protocolVersion: number; clientId: string }, webSocketUrl: string) {
    if (input.protocolVersion !== REALTIME_PROTOCOL_VERSION) {
      throw badRequest('unsupported_protocol_version', `Supported protocolVersion is ${REALTIME_PROTOCOL_VERSION}`)
    }
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const ticket = randomBytes(32).toString('base64url')
    const expiresAt = new Date(Date.now() + this.config.ticketTtlSeconds * 1000)
    await this.db.repos.realtime.insertTicket({
      ticketHash: hashTicket(ticket),
      workspaceId,
      userId,
      clientId: input.clientId,
      protocolVersion: input.protocolVersion,
      expiresAt,
    })
    return { ticket, expiresAt, webSocketUrl, protocolVersion: REALTIME_PROTOCOL_VERSION }
  }
}
