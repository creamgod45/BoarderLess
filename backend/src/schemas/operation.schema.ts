import { Type } from 'typebox'
import { OPERATION_KINDS } from '../domain/operations.ts'
import { DateTime, ErrorEnvelope, errorResponses, JsonObject, Uuid, WorkspaceParams } from './common.schema.ts'

export const CommittedOperationSchema = Type.Object({
  serverSeq: Type.Integer(),
  operationId: Type.String(),
  transactionId: Type.String(),
  actorId: Type.String(),
  clientId: Type.String(),
  clientSeq: Type.Integer(),
  baseVersion: Type.Integer(),
  workspaceVersion: Type.Integer(),
  operationType: Type.String(),
  payload: JsonObject,
  schemaVersion: Type.Integer(),
  committedAt: DateTime,
})

/** §6.2 operation envelope（REST 版本；WebSocket submit_operations 共用同一結構） */
export const SubmitOperationsBody = Type.Object(
  {
    type: Type.Optional(Type.Literal('submit_operations')),
    protocolVersion: Type.Integer({ minimum: 1 }),
    clientId: Uuid,
    transactionId: Uuid,
    baseVersion: Type.Integer({ minimum: 0 }),
    operations: Type.Array(
      Type.Object(
        {
          operationId: Uuid,
          clientSeq: Type.Integer({ minimum: 0 }),
          kind: Type.String({ description: `One of: ${OPERATION_KINDS.join(', ')}` }),
          expectedObjectVersions: Type.Optional(Type.Record(Type.String(), Type.Integer({ minimum: 1 }))),
          payload: Type.Unknown(),
        },
        { additionalProperties: false },
      ),
      { minItems: 1, maxItems: 200 },
    ),
  },
  { additionalProperties: false },
)

export const CommittedResponse = Type.Object({
  status: Type.Union([Type.Literal('accepted'), Type.Literal('duplicate')]),
  fromServerSeq: Type.Integer(),
  toServerSeq: Type.Integer(),
  workspaceVersion: Type.Integer(),
  operations: Type.Array(CommittedOperationSchema),
})

export const ConflictResponse = Type.Object({
  status: Type.Literal('conflict'),
  conflicts: Type.Array(
    Type.Object({
      id: Type.String(),
      expectedVersion: Type.Integer(),
      actualVersion: Type.Union([Type.Integer(), Type.Null()]),
    }),
  ),
  workspaceVersion: Type.Integer(),
  lastServerSeq: Type.Integer(),
  missingOperations: Type.Array(CommittedOperationSchema),
})

export const submitOperationsSchema = {
  tags: ['operations'],
  summary: '提交 WorkspaceOperation transaction',
  description:
    '200 accepted/duplicate；409 conflict（含 missingOperations 供 rebase）；422 rejected（正式資料未改變）。',
  params: WorkspaceParams,
  body: SubmitOperationsBody,
  response: { 200: CommittedResponse, 409: Type.Union([ConflictResponse, ErrorEnvelope]), ...errorResponses },
}

export const listOperationsSchema = {
  tags: ['operations'],
  summary: '依 serverSeq 追回 operations（斷線 catch-up）',
  params: WorkspaceParams,
  querystring: Type.Object({
    afterSeq: Type.Integer({ minimum: 0, default: 0 }),
    limit: Type.Integer({ minimum: 1, maximum: 1000, default: 500 }),
  }),
  response: {
    200: Type.Object({
      operations: Type.Array(CommittedOperationSchema),
      lastServerSeq: Type.Integer(),
      hasMore: Type.Boolean(),
    }),
    ...errorResponses,
  },
}

// ---- BAI-012 receipts / fences ----

const ReceiptQueryBody = Type.Object(
  {
    transactionIds: Type.Optional(Type.Array(Uuid, { maxItems: 200 })),
    operationIds: Type.Optional(Type.Array(Uuid, { maxItems: 200 })),
  },
  { additionalProperties: false },
)

export const ReceiptResponse = Type.Object({
  workspaceId: Type.String(),
  headServerSeq: Type.Integer(),
  headWorkspaceVersion: Type.Integer(),
  receipts: Type.Array(
    Type.Object({
      transactionId: Type.String(),
      actorId: Type.String(),
      clientId: Type.String(),
      workspaceVersion: Type.Integer(),
      fromServerSeq: Type.Integer(),
      toServerSeq: Type.Integer(),
      committedAt: DateTime,
      operations: Type.Array(
        Type.Object({
          operationId: Type.String(),
          clientSeq: Type.Integer(),
          serverSeq: Type.Integer(),
          kind: Type.String(),
        }),
      ),
    }),
  ),
  lookups: Type.Array(
    Type.Object({
      type: Type.Enum(['transaction', 'operation'] as const),
      id: Type.String(),
      status: Type.Enum(['committed', 'fenced', 'unknown'] as const),
      transactionId: Type.Optional(Type.String()),
      workspaceVersion: Type.Optional(Type.Integer()),
    }),
  ),
})

export const lookupReceiptsSchema = {
  tags: ['operations'],
  summary: '查詢自己原提交的 receipt（最多 200 個 ID；unknown 不排除晚到的原請求）',
  params: WorkspaceParams,
  body: ReceiptQueryBody,
  response: { 200: ReceiptResponse, ...errorResponses },
}

export const fenceTransactionsSchema = {
  tags: ['operations'],
  summary: 'Fence 尚未提交的原 transaction / operation，保證之後不會 commit',
  params: WorkspaceParams,
  body: ReceiptQueryBody,
  response: { 200: ReceiptResponse, ...errorResponses },
}
