import { Type } from 'typebox'
import { ASSET_STATUSES } from '../models/asset.ts'
import { DateTime, errorResponses, Nullable, Uuid, WorkspaceParams } from './common.schema.ts'

export const AssetSchema = Type.Object({
  id: Type.String(),
  workspaceId: Type.String(),
  ownerId: Type.String(),
  storageKey: Type.String(),
  mediaType: Type.String(),
  byteSize: Type.Integer(),
  checksum: Type.String(),
  width: Nullable(Type.Integer()),
  height: Nullable(Type.Integer()),
  durationMs: Nullable(Type.Integer()),
  status: Type.Enum(ASSET_STATUSES),
  createdAt: DateTime,
})

export const AssetParams = Type.Object({ workspaceId: Uuid, assetId: Uuid })

export const createAssetSchema = {
  tags: ['assets'],
  summary: '建立 pending asset metadata（signed upload URL 尚未實作）',
  params: WorkspaceParams,
  body: Type.Object(
    {
      mediaType: Type.String(),
      byteSize: Type.Integer({ minimum: 1 }),
      checksum: Type.String({ minLength: 1, maxLength: 200 }),
      width: Type.Optional(Type.Integer({ minimum: 1 })),
      height: Type.Optional(Type.Integer({ minimum: 1 })),
      durationMs: Type.Optional(Type.Integer({ minimum: 0 })),
    },
    { additionalProperties: false },
  ),
  response: { 201: Type.Object({ asset: AssetSchema, uploadUrl: Nullable(Type.String()) }), ...errorResponses },
}

export const listAssetsSchema = {
  tags: ['assets'],
  summary: 'Asset 列表',
  params: WorkspaceParams,
  response: { 200: Type.Object({ assets: Type.Array(AssetSchema) }), ...errorResponses },
}

export const getAssetSchema = {
  tags: ['assets'],
  summary: 'Asset metadata',
  params: AssetParams,
  response: { 200: AssetSchema, ...errorResponses },
}

export const deleteAssetSchema = {
  tags: ['assets'],
  summary: 'Soft delete asset',
  params: AssetParams,
  response: { 204: Type.Null(), ...errorResponses },
}
