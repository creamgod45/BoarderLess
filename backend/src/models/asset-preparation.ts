import type { AssetPhase, PublicAsset } from './asset.ts'

export const PREPARE_REQUEST_SCHEMA = 'boarderless.asset-prepare.v1'
export const PREPARATION_RESPONSE_SCHEMA = 'boarderless.asset-preparation.v1'

/** 對 APP 公開的 preparation phase；與 Media v1 公開 status 不同，可區分「尚未 complete」與「worker 處理中」 */
export const PREPARATION_PHASES = ['prepared', 'completionAccepted', 'ready', 'rejected', 'missing', 'abandoned'] as const
export type PreparationPhase = (typeof PREPARATION_PHASES)[number]

export function preparationPhaseOf(phase: AssetPhase): PreparationPhase {
  switch (phase) {
    case 'awaiting_upload':
      return 'prepared'
    case 'accepted':
      return 'completionAccepted'
    case 'abandoned':
    case 'expired':
      return 'abandoned'
    default:
      return phase
  }
}

/** prepare body 的 metadata；欄位順序即 digest 序列化順序（字母序），null 明確保留 */
export interface PreparationMetadata {
  byteSize: number
  checksum: string
  durationMs: number | null
  height: number | null
  mediaType: string
  width: number | null
}

/** 伺服器實際解析值（顯示尺寸；已套用 EXIF orientation / 影片 rotation） */
export interface VerifiedMetadata {
  width: number
  height: number
  durationMs: number | null
}

export interface AssetPreparation {
  workspaceId: string
  actorId: string
  requestId: string
  assetId: string
  requestDigest: string
  /** declared：prepare 時的 canonical metadata，永不改寫 */
  metadata: PreparationMetadata
  verified: VerifiedMetadata | null
  verifiedAt: Date | null
  createdAt: Date
}

export interface UploadLease {
  leaseId: string
  assetId: string
  ticketRequestId: string
  generation: number
  uploadKey: string
  expiresAt: Date
  createdAt: Date
  collectedAt: Date | null
}

/** 公開的 lease identity（不含 staging key、URL、headers） */
export interface PublicUploadLease {
  leaseId: string
  ticketRequestId: string
  generation: number
  expiresAt: Date
}

export const toPublicLease = (lease: UploadLease): PublicUploadLease => ({
  leaseId: lease.leaseId,
  ticketRequestId: lease.ticketRequestId,
  generation: lease.generation,
  expiresAt: lease.expiresAt,
})

/** 只在簽發當次回應中存在；不寫入 DB / receipt / log */
export interface UploadDirective {
  method: 'PUT'
  url: string
  headers: Record<string, string>
  expiresAt: Date
  leaseId: string
}

export interface PreparationEnvelope {
  schema: typeof PREPARATION_RESPONSE_SCHEMA
  requestId: string
  requestDigest: string
  phase: PreparationPhase
  /** prepare 時宣告的 metadata（requestDigest 涵蓋的內容），永不改寫 */
  declared: PreparationMetadata
  /** worker 實際解析值；尚未解析或解析失敗時為 null */
  verified: VerifiedMetadata | null
  /** Media v1 metadata：ready 後寬高 / duration 為 verified 值 */
  asset: PublicAsset
  /** 當前（或已被 complete 接受的）lease；尚未簽發票券時為 null */
  lease: PublicUploadLease | null
  upload?: UploadDirective
}
