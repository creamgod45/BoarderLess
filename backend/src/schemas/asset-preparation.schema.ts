import { Type } from 'typebox'
import {
  PREPARATION_PHASES,
  PREPARATION_RESPONSE_SCHEMA,
  PREPARE_REQUEST_SCHEMA,
} from '../models/asset-preparation.ts'
import { AssetSchema } from './asset.schema.ts'
import { DateTime, ErrorEnvelope, errorResponses, Nullable, Uuid, WorkspaceParams } from './common.schema.ts'

const MAX_SAFE = Number.MAX_SAFE_INTEGER // 9007199254740991
/** 尺寸存 INTEGER，對應 APP 32-bit Int */
const MAX_DIMENSION = 2_147_483_647

/** APP 先 durable 保存的 lowercase UUID（不接受大寫或其他 UUID 寫法） */
const LowerUuid = Type.String({ pattern: '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' })
const Checksum = Type.String({ pattern: '^sha256:[0-9a-f]{64}$', description: '原始內容 SHA-256：sha256:<64 小寫 hex>' })

export const PreparationMetadataSchema = Type.Object(
  {
    byteSize: Type.Integer({ minimum: 1, maximum: MAX_SAFE }),
    checksum: Checksum,
    durationMs: Nullable(Type.Integer({ minimum: 0, maximum: MAX_SAFE })),
    height: Nullable(Type.Integer({ minimum: 1, maximum: MAX_DIMENSION })),
    mediaType: Type.String({ description: 'image/png、image/jpeg、image/webp、image/gif、video/mp4、video/webm（canonical 小寫）' }),
    width: Nullable(Type.Integer({ minimum: 1, maximum: MAX_DIMENSION })),
  },
  { additionalProperties: false },
)

const PublicLeaseSchema = Type.Object({
  leaseId: Type.String(),
  ticketRequestId: Type.String(),
  generation: Type.Integer(),
  expiresAt: DateTime,
})

const UploadDirectiveSchema = Type.Object({
  method: Type.Literal('PUT'),
  url: Type.String(),
  headers: Type.Record(Type.String(), Type.String()),
  expiresAt: DateTime,
  leaseId: Type.String(),
})

const VerifiedMetadataSchema = Type.Object(
  { width: Type.Integer({ minimum: 1 }), height: Type.Integer({ minimum: 1 }), durationMs: Nullable(Type.Integer({ minimum: 0 })) },
  { description: '伺服器實際解析值：顯示尺寸（已套用 EXIF orientation / 影片 rotation）' },
)

export const PreparationEnvelopeSchema = Type.Object({
  schema: Type.Literal(PREPARATION_RESPONSE_SCHEMA),
  requestId: Type.String(),
  requestDigest: Type.String({ pattern: '^[0-9a-f]{64}$' }),
  phase: Type.Enum(PREPARATION_PHASES),
  declared: PreparationMetadataSchema,
  verified: Nullable(VerifiedMetadataSchema),
  asset: AssetSchema,
  lease: Nullable(PublicLeaseSchema),
  upload: Type.Optional(UploadDirectiveSchema),
})

const PreparationParams = Type.Object({ workspaceId: Uuid, requestId: LowerUuid })

const preparationErrors = {
  ...errorResponses,
  409: ErrorEnvelope,
  413: ErrorEnvelope,
  415: ErrorEnvelope,
  503: ErrorEnvelope,
}

export const createPreparationSchema = {
  tags: ['asset-preparations'],
  summary: 'Preparation v2：以 requestId 固定 asset identity（首次 201、相同重送 200、metadata 不同 409）',
  params: WorkspaceParams,
  body: Type.Object(
    {
      schema: Type.Literal(PREPARE_REQUEST_SCHEMA),
      requestId: LowerUuid,
      metadata: PreparationMetadataSchema,
    },
    { additionalProperties: false },
  ),
  response: { 200: PreparationEnvelopeSchema, 201: PreparationEnvelopeSchema, ...preparationErrors },
}

export const getPreparationSchema = {
  tags: ['asset-preparations'],
  summary: '查詢自己在此 Workspace 的原 prepare（404 preparation_not_found）',
  params: PreparationParams,
  response: { 200: PreparationEnvelopeSchema, ...preparationErrors },
}

export const issueTicketSchema = {
  tags: ['asset-preparations'],
  summary: '簽發 upload ticket lease（同 ticketRequestId 冪等；過期 / 被取代需新的 ticketRequestId）',
  params: PreparationParams,
  body: Type.Object({ ticketRequestId: LowerUuid }, { additionalProperties: false }),
  response: { 200: PreparationEnvelopeSchema, 201: PreparationEnvelopeSchema, ...preparationErrors },
}

export const completePreparationSchema = {
  tags: ['asset-preparations'],
  summary: '以當前 lease 完成上傳：durable 接受回 202 completionAccepted；之後重送回目前 phase',
  params: PreparationParams,
  body: Type.Object(
    {
      requestId: LowerUuid,
      leaseId: LowerUuid,
      byteSize: Type.Integer({ minimum: 1, maximum: MAX_SAFE }),
      checksum: Checksum,
    },
    { additionalProperties: false },
  ),
  response: { 200: PreparationEnvelopeSchema, 202: PreparationEnvelopeSchema, ...preparationErrors },
}

export const capabilitiesSchema = {
  tags: ['meta'],
  summary: '後端宣告的可選協定能力（APP 只在此宣告存在時使用 preparation v2）',
  security: [],
  response: {
    200: Type.Object({
      assetPreparation: Type.Object({
        requestSchema: Type.String(),
        responseSchema: Type.String(),
        maxByteSize: Type.Integer(),
        mediaTypes: Type.Array(Type.String()),
      }),
    }),
  },
}
