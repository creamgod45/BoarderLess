import { Type } from 'typebox'
import { DateTime, ErrorEnvelope, errorResponses, WorkspaceParams } from './common.schema.ts'

const LowerUuid = Type.String({ pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' })

export const RealtimeCapabilitiesSchema = Type.Object({
  protocolVersions: Type.Array(Type.Integer()),
  schemaVersions: Type.Array(Type.Integer()),
  webSocketUrl: Type.String(),
  subprotocol: Type.String(),
  operationFanOut: Type.Boolean(),
  presence: Type.Boolean(),
  metadataRevision: Type.Integer(),
  maxMessageBytes: Type.Integer(),
  maxPendingTransactions: Type.Integer(),
  maxCatchUpOperations: Type.Integer(),
  maxOperationsPerTransaction: Type.Integer(),
  heartbeatIntervalMs: Type.Integer(),
  presenceTtlMs: Type.Integer(),
  ticketTtlSeconds: Type.Integer(),
  authTimeoutMs: Type.Integer(),
  maxSelectionIds: Type.Integer(),
  cursorUpdatesPerSecond: Type.Integer(),
  selectionUpdatesPerSecond: Type.Integer(),
})

export const RealtimeTicketSchema = Type.Object({
  ticket: Type.String(),
  expiresAt: DateTime,
  webSocketUrl: Type.String(),
  protocolVersion: Type.Integer(),
})

export const realtimeCapabilitiesSchema = {
  tags: ['realtime'],
  summary: '即時協作能力（需 read membership；不含 ticket）',
  params: WorkspaceParams,
  response: { 200: RealtimeCapabilitiesSchema, ...errorResponses },
}

export const realtimeTicketSchema = {
  tags: ['realtime'],
  summary: '一次性 join ticket（約 60 秒、綁定 user + client + workspace；Cache-Control: no-store）',
  params: WorkspaceParams,
  body: Type.Object({ protocolVersion: Type.Integer({ minimum: 1 }), clientId: LowerUuid }, { additionalProperties: false }),
  response: { 201: RealtimeTicketSchema, ...errorResponses, 409: ErrorEnvelope },
}
