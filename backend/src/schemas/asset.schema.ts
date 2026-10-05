import { Type } from 'typebox'
import { ASSET_STATUSES } from '../models/asset.ts'
import { DateTime, ErrorEnvelope, errorResponses, Nullable, Uuid, WorkspaceParams } from './common.schema.ts'

/** BACKEND_MEDIA_API_SPEC §3 公開 metadata */
export const AssetSchema = Type.Object({
  id: Type.String(),
  workspaceId: Type.String(),
  ownerId: Type.String(),
  storageKey: Type.String({ description: 'Opaque handle；不是下載地址' }),
  mediaType: Type.String(),
  byteSize: Type.Integer(),
  checksum: Type.String(),
  status: Type.Enum(ASSET_STATUSES),
  createdAt: DateTime,
  updatedAt: DateTime,
  width: Nullable(Type.Integer()),
  height: Nullable(Type.Integer()),
  durationMs: Nullable(Type.Integer()),
  thumbnailAssetId: Nullable(Type.String()),
  rejectionReason: Nullable(Type.String()),
})

/** §4.1 transfer directive */
export const TransferDirectiveSchema = Type.Object({
  method: Type.String(),
  url: Type.String(),
  headers: Type.Record(Type.String(), Type.String()),
  expiresAt: DateTime,
})

export const AssetParams = Type.Object({ workspaceId: Uuid, assetId: Uuid })

const Checksum = Type.String({ pattern: '^sha256:[0-9a-f]{64}$', description: '原始內容 SHA-256：sha256:<64 小寫 hex>' })

const mediaErrors = {
  ...errorResponses,
  409: ErrorEnvelope,
  413: ErrorEnvelope,
  415: ErrorEnvelope,
  503: ErrorEnvelope,
}

export const createAssetSchema = {
  tags: ['assets'],
  summary: '準備上傳：建立 pending metadata 並簽發 signed PUT',
  params: WorkspaceParams,
  body: Type.Object(
    {
      mediaType: Type.String({ description: 'image/png、image/jpeg、image/webp、image/gif、video/mp4、video/webm' }),
      byteSize: Type.Integer({ minimum: 1 }),
      checksum: Checksum,
      width: Type.Optional(Nullable(Type.Integer({ minimum: 1 }))),
      height: Type.Optional(Nullable(Type.Integer({ minimum: 1 }))),
      durationMs: Type.Optional(Nullable(Type.Integer({ minimum: 0 }))),
    },
    { additionalProperties: false },
  ),
  response: { 201: Type.Object({ asset: AssetSchema, upload: TransferDirectiveSchema }), ...mediaErrors },
}

export const completeAssetSchema = {
  tags: ['assets'],
  summary: '上傳完成：durable 接受後回 202 pending；已 ready / rejected / missing 時回 200',
  params: AssetParams,
  body: Type.Object({ byteSize: Type.Integer({ minimum: 1 }), checksum: Checksum }, { additionalProperties: false }),
  response: {
    200: Type.Object({ asset: AssetSchema }),
    202: Type.Object({ asset: AssetSchema }),
    ...mediaErrors,
  },
}

export const listAssetsSchema = {
  tags: ['assets'],
  summary: 'Workspace 全部素材（含衍生縮圖；v1 不分頁）',
  params: WorkspaceParams,
  response: { 200: Type.Object({ assets: Type.Array(AssetSchema) }), ...errorResponses },
}

export const getAssetSchema = {
  tags: ['assets'],
  summary: 'Asset metadata（不包裝）',
  params: AssetParams,
  response: { 200: AssetSchema, ...errorResponses },
}

export const assetContentSchema = {
  tags: ['assets'],
  summary: '簽發 signed GET（只限 ready）',
  params: AssetParams,
  response: { 200: Type.Object({ asset: AssetSchema, download: TransferDirectiveSchema }), ...mediaErrors },
}

export const abandonAssetSchema = {
  tags: ['assets'],
  summary: 'Pending-only abandon：放棄尚未完成的上傳（已 complete 的資產回 409）',
  params: AssetParams,
  response: { 204: Type.Null(), ...mediaErrors },
}
