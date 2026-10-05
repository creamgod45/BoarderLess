export const ASSET_STATUSES = ['pending', 'ready', 'rejected', 'missing'] as const
export type AssetStatus = (typeof ASSET_STATUSES)[number]

/** 伺服器內部 phase；不可直接作為公開 status（見 migrations/002） */
export const ASSET_PHASES = ['awaiting_upload', 'accepted', 'ready', 'rejected', 'missing', 'abandoned', 'expired'] as const
export type AssetPhase = (typeof ASSET_PHASES)[number]

/** 精確 MIME allowlist 與 storage 副檔名 */
export const ASSET_MEDIA_TYPES = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'video/mp4': 'mp4',
  'video/webm': 'webm',
} as const
export type AssetMediaType = keyof typeof ASSET_MEDIA_TYPES

export const MAX_ASSET_BYTES = 209_715_200 // 200 MiB
export const CHECKSUM_PATTERN = /^sha256:[0-9a-f]{64}$/

export function isAssetMediaType(value: string): value is AssetMediaType {
  return Object.hasOwn(ASSET_MEDIA_TYPES, value)
}

/** MediaNode.mediaKind（APP `MediaKind`）與 MIME 的對應 */
export function mediaKindOf(mediaType: string): 'image' | 'gif' | 'video' | null {
  if (mediaType === 'image/gif') return 'gif'
  if (mediaType.startsWith('image/')) return 'image'
  if (mediaType.startsWith('video/')) return 'video'
  return null
}

export interface Asset {
  id: string
  workspaceId: string
  ownerId: string
  storageKey: string
  mediaType: string
  byteSize: number
  checksum: string
  width: number | null
  height: number | null
  durationMs: number | null
  status: AssetStatus
  phase: AssetPhase
  uploadKey: string | null
  uploadExpiresAt: Date | null
  completionAcceptedAt: Date | null
  verifiedAt: Date | null
  rejectionReason: string | null
  thumbnailAssetId: string | null
  sourceAssetId: string | null
  derivativeType: string | null
  derivativeVersion: number | null
  createdAt: Date
  updatedAt: Date
  deletedAt: Date | null
}

/** 對 APP 公開的 metadata（BACKEND_MEDIA_API_SPEC §3）；不含 upload key 等內部欄位 */
export interface PublicAsset {
  id: string
  workspaceId: string
  ownerId: string
  storageKey: string
  mediaType: string
  byteSize: number
  checksum: string
  status: AssetStatus
  createdAt: Date
  updatedAt: Date
  width: number | null
  height: number | null
  durationMs: number | null
  thumbnailAssetId: string | null
  rejectionReason: string | null
}

export function toPublicAsset(asset: Asset): PublicAsset {
  return {
    id: asset.id,
    workspaceId: asset.workspaceId,
    ownerId: asset.ownerId,
    storageKey: asset.storageKey,
    mediaType: asset.mediaType,
    byteSize: asset.byteSize,
    checksum: asset.checksum,
    status: asset.status,
    createdAt: asset.createdAt,
    updatedAt: asset.updatedAt,
    width: asset.width,
    height: asset.height,
    durationMs: asset.durationMs,
    thumbnailAssetId: asset.thumbnailAssetId,
    rejectionReason: asset.rejectionReason,
  }
}

/** 簽名傳輸票券（§4.1）；只存在 API 回應中，不寫入 DB / Node / log */
export interface TransferDirective {
  method: 'PUT' | 'GET'
  url: string
  headers: Record<string, string>
  expiresAt: Date
}
