import { Type, type Static, type TSchema } from 'typebox'
import { Compile } from 'typebox/compile'
import { WORKSPACE_ROLES } from '../models/workspace.ts'
import { CommittedOperationSchema, ConflictResponse } from '../schemas/operation.schema.ts'

/**
 * 即時協作 wire 契約（protocol v1，subprotocol `boarderless.v1`，JSON text frame）。
 * 所有 client → server 訊息嚴格驗證（不轉型、不接受未知欄位）；server → client 訊息同樣以此 schema 錄製 fixtures。
 */
export const REALTIME_SUBPROTOCOL = 'boarderless.v1'
export const REALTIME_PROTOCOL_VERSION = 1
export const REALTIME_SCHEMA_VERSION = 1

const MAX_SAFE = Number.MAX_SAFE_INTEGER
const LowerUuid = Type.String({ pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' })
const Seq = Type.Integer({ minimum: 0, maximum: MAX_SAFE })
const Role = Type.Enum(WORKSPACE_ROLES)
const strict = { additionalProperties: false } as const

/** 固定錯誤碼；不回顯任意 server message 或原始 frame */
export const REALTIME_ERROR_CODES = [
  'authentication_required',
  'ticket_expired',
  'ticket_used',
  'scope_mismatch',
  'unsupported_protocol_version',
  'unsupported_schema_version',
  'access_revoked',
  'invalid_message',
  'message_too_large',
  'rate_limited',
  'sequence_gap',
  'resync_required',
  'not_live',
  'heartbeat_timeout',
] as const
export type RealtimeErrorCode = (typeof REALTIME_ERROR_CODES)[number]

export const SNAPSHOT_REASONS = ['checkpoint_not_at_transaction_boundary', 'checkpoint_ahead_of_server', 'catch_up_too_large'] as const
export const RESYNC_REASONS = [
  'too_many_pending_transactions',
  'transaction_too_large',
  'slow_consumer',
  'sequence_gap',
] as const
export const REVOKE_REASONS = ['membership_revoked', 'workspace_deleted'] as const

// WebSocket close codes（4xxx：應用層）
export const CLOSE = {
  goingAway: 1001,
  invalidMessage: 4400,
  authentication: 4401,
  accessRevoked: 4403,
  authTimeout: 4408,
  resync: 4409,
  tooLarge: 4413,
  unsupported: 4426,
  heartbeatTimeout: 4000,
  tooManyUnauthenticated: 4429,
} as const

// ---- client → server ----

const clientEnvelope = {
  protocolVersion: Type.Integer({ minimum: 1 }),
  workspaceId: LowerUuid,
  connectionEpoch: LowerUuid,
}

export const JoinWorkspaceMessage = Type.Object(
  {
    type: Type.Literal('join_workspace'),
    ...clientEnvelope,
    schemaVersion: Type.Integer({ minimum: 1 }),
    clientId: LowerUuid,
    /** client 已原子套用的 durable checkpoint（不是最後收到的廣播 head） */
    lastSeenServerSeq: Seq,
    metadataRevision: Seq,
    ticket: Type.String({ minLength: 1, maxLength: 256 }),
  },
  strict,
)

export const CatchUpAppliedMessage = Type.Object(
  {
    type: Type.Literal('catch_up_applied'),
    ...clientEnvelope,
    appliedServerSeq: Seq,
    workspaceVersion: Seq,
    metadataRevision: Seq,
  },
  strict,
)

export const HeartbeatMessage = Type.Object(
  { type: Type.Literal('heartbeat'), ...clientEnvelope, heartbeatSeq: Seq },
  strict,
)

const WORLD = 10_000_000
export const CursorSchema = Type.Object(
  { x: Type.Number({ minimum: -WORLD, maximum: WORLD }), y: Type.Number({ minimum: -WORLD, maximum: WORLD }) },
  { ...strict, description: 'world coordinates；null 表示離開 / 隱藏' },
)

export const PresenceUpdateMessage = Type.Object(
  {
    type: Type.Literal('presence_update'),
    ...clientEnvelope,
    cursor: Type.Union([CursorSchema, Type.Null()]),
    selectedObjectIds: Type.Array(LowerUuid, { maxItems: 128 }),
    presenceSeq: Seq,
  },
  strict,
)

export const SubmitOperationsMessage = Type.Object(
  {
    type: Type.Literal('submit_operations'),
    ...clientEnvelope,
    clientId: LowerUuid,
    transactionId: LowerUuid,
    baseVersion: Seq,
    operations: Type.Array(
      Type.Object(
        {
          operationId: LowerUuid,
          clientSeq: Seq,
          kind: Type.String(),
          expectedObjectVersions: Type.Optional(Type.Record(Type.String(), Type.Integer({ minimum: 1 }))),
          payload: Type.Unknown(),
        },
        strict,
      ),
      { minItems: 1, maxItems: 200 },
    ),
  },
  strict,
)

export const ClientMessage = Type.Union([
  JoinWorkspaceMessage,
  CatchUpAppliedMessage,
  HeartbeatMessage,
  PresenceUpdateMessage,
  SubmitOperationsMessage,
])
export type JoinWorkspace = Static<typeof JoinWorkspaceMessage>
export type CatchUpApplied = Static<typeof CatchUpAppliedMessage>
export type Heartbeat = Static<typeof HeartbeatMessage>
export type PresenceUpdate = Static<typeof PresenceUpdateMessage>
export type SubmitOperations = Static<typeof SubmitOperationsMessage>

export const clientValidators = {
  join_workspace: Compile(JoinWorkspaceMessage),
  catch_up_applied: Compile(CatchUpAppliedMessage),
  heartbeat: Compile(HeartbeatMessage),
  presence_update: Compile(PresenceUpdateMessage),
  submit_operations: Compile(SubmitOperationsMessage),
}
export type ClientMessageType = keyof typeof clientValidators

// ---- server → client ----

const serverEnvelope = {
  protocolVersion: Type.Literal(REALTIME_PROTOCOL_VERSION),
  workspaceId: Type.String(),
  connectionEpoch: Type.String(),
}

export const JoinAcceptedEvent = Type.Object({
  type: Type.Literal('join_accepted'),
  ...serverEnvelope,
  connectionId: Type.String(),
  userId: Type.String(),
  role: Role,
  barrierServerSeq: Seq,
  workspaceVersion: Seq,
  metadataRevision: Seq,
})

export const SnapshotRequiredEvent = Type.Object({
  type: Type.Literal('snapshot_required'),
  ...serverEnvelope,
  barrierServerSeq: Seq,
  workspaceVersion: Seq,
  metadataRevision: Seq,
  reason: Type.Enum(SNAPSHOT_REASONS),
})

const transactionFields = {
  ...serverEnvelope,
  transactionId: Type.String(),
  fromServerSeq: Seq,
  toServerSeq: Seq,
  workspaceVersion: Seq,
  operations: Type.Array(CommittedOperationSchema, { minItems: 1, maxItems: 200 }),
}
export const CatchUpTransactionEvent = Type.Object({ type: Type.Literal('catch_up_transaction'), ...transactionFields })
export const CommittedTransactionEvent = Type.Object({ type: Type.Literal('committed_transaction'), ...transactionFields })

export const LiveReadyEvent = Type.Object({
  type: Type.Literal('live_ready'),
  ...serverEnvelope,
  throughServerSeq: Seq,
  workspaceVersion: Seq,
  metadataRevision: Seq,
  connectionId: Type.String(),
})

export const HeartbeatAckEvent = Type.Object({ type: Type.Literal('heartbeat_ack'), ...serverEnvelope, heartbeatSeq: Seq })

export const PresencePeerSchema = Type.Object({
  connectionId: Type.String(),
  userId: Type.String(),
  clientId: Type.String(),
  displayName: Type.String({ maxLength: 100 }),
  role: Role,
  cursor: Type.Union([CursorSchema, Type.Null()]),
  selectedObjectIds: Type.Array(Type.String(), { maxItems: 128 }),
  presenceSeq: Seq,
})
export type PresencePeer = Static<typeof PresencePeerSchema>

export const PresenceSnapshotEvent = Type.Object({
  type: Type.Literal('presence_snapshot'),
  ...serverEnvelope,
  roomEpoch: Type.String(),
  roomRevision: Seq,
  peers: Type.Array(PresencePeerSchema),
})

export const PresenceDeltaEvent = Type.Object({
  type: Type.Literal('presence_delta'),
  ...serverEnvelope,
  roomEpoch: Type.String(),
  roomRevision: Seq,
  upserts: Type.Array(PresencePeerSchema),
  removedConnectionIds: Type.Array(Type.String()),
})

export const WorkspaceMetadataEvent = Type.Object({
  type: Type.Literal('workspace_metadata'),
  ...serverEnvelope,
  metadataRevision: Seq,
  title: Type.String(),
  selfRole: Role,
})

export const AccessRevokedEvent = Type.Object({
  type: Type.Literal('access_revoked'),
  ...serverEnvelope,
  reason: Type.Enum(REVOKE_REASONS),
})

export const ResyncRequiredEvent = Type.Object({
  type: Type.Literal('resync_required'),
  ...serverEnvelope,
  reason: Type.Enum(RESYNC_REASONS),
  headServerSeq: Seq,
  workspaceVersion: Seq,
})

/** 驗證前的錯誤無法確認 workspace / epoch，因此這兩個欄位可為 null */
export const ProtocolErrorEvent = Type.Object({
  type: Type.Literal('protocol_error'),
  protocolVersion: Type.Literal(REALTIME_PROTOCOL_VERSION),
  workspaceId: Type.Union([Type.String(), Type.Null()]),
  connectionEpoch: Type.Union([Type.String(), Type.Null()]),
  code: Type.Enum(REALTIME_ERROR_CODES),
  retryAfterMs: Type.Optional(Seq),
})

const ackBase = {
  type: Type.Literal('operation_ack'),
  ...serverEnvelope,
  transactionId: Type.String(),
  operationIds: Type.Array(Type.String()),
}
export const OperationAckEvent = Type.Union([
  Type.Object({
    ...ackBase,
    status: Type.Union([Type.Literal('accepted'), Type.Literal('duplicate')]),
    fromServerSeq: Seq,
    toServerSeq: Seq,
    workspaceVersion: Seq,
    operations: Type.Array(CommittedOperationSchema),
  }),
  Type.Object({
    ...ackBase,
    status: Type.Literal('conflict'),
    conflicts: ConflictResponse.properties.conflicts,
    workspaceVersion: Seq,
    lastServerSeq: Seq,
    missingOperations: Type.Array(CommittedOperationSchema),
  }),
  Type.Object({
    ...ackBase,
    status: Type.Literal('rejected'),
    code: Type.String(),
    retryAfterMs: Type.Optional(Seq),
    details: Type.Optional(Type.Unknown()),
  }),
])

export const SERVER_EVENT_SCHEMAS: Record<string, TSchema> = {
  join_accepted: JoinAcceptedEvent,
  snapshot_required: SnapshotRequiredEvent,
  catch_up_transaction: CatchUpTransactionEvent,
  committed_transaction: CommittedTransactionEvent,
  live_ready: LiveReadyEvent,
  heartbeat_ack: HeartbeatAckEvent,
  presence_snapshot: PresenceSnapshotEvent,
  presence_delta: PresenceDeltaEvent,
  workspace_metadata: WorkspaceMetadataEvent,
  access_revoked: AccessRevokedEvent,
  resync_required: ResyncRequiredEvent,
  protocol_error: ProtocolErrorEvent,
  operation_ack: OperationAckEvent,
}

export const CLIENT_MESSAGE_SCHEMAS: Record<ClientMessageType, TSchema> = {
  join_workspace: JoinWorkspaceMessage,
  catch_up_applied: CatchUpAppliedMessage,
  heartbeat: HeartbeatMessage,
  presence_update: PresenceUpdateMessage,
  submit_operations: SubmitOperationsMessage,
}
