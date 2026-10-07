import { randomUUID } from 'node:crypto'
import type { MediaConfig } from '../config/env.ts'
import {
  ASSET_MEDIA_TYPES,
  isAssetMediaType,
  MAX_ASSET_BYTES,
  toPublicAsset,
  type Asset,
  type PublicAsset,
  type TransferDirective,
} from '../models/asset.ts'
import { jobKeys } from '../models/job.ts'
import type { Database, Repositories } from '../repositories/index.ts'
import type { ObjectStorage } from '../storage/types.ts'
import { conflict, notFound, payloadTooLarge, unsupportedMediaType } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export interface PrepareAssetInput {
  mediaType: string
  byteSize: number
  checksum: string
  width?: number | null
  height?: number | null
  durationMs?: number | null
}

export interface CompleteAssetInput {
  byteSize: number
  checksum: string
}

export interface AssetServiceOptions {
  media: Pick<MediaConfig, 'uploadUrlTtlSeconds' | 'downloadUrlTtlSeconds'>
  jobMaxAttempts: number
}

export const storageKeyFor = (workspaceId: string, assetId: string, mediaType: keyof typeof ASSET_MEDIA_TYPES) =>
  `workspaces/${workspaceId}/assets/${assetId}.${ASSET_MEDIA_TYPES[mediaType]}`

/**
 * 素材生命週期（BACKEND_MEDIA_API_SPEC §4、§5）。
 * API 只負責授權、簽發短效票券與狀態轉換；binary 驗證與衍生檔由 worker 處理。
 */
export class AssetService {
  constructor(
    private readonly db: Database,
    private readonly storage: ObjectStorage,
    private readonly options: AssetServiceOptions,
  ) {}

  /** 建立 pending metadata 並簽發 upload ticket */
  async prepare(
    workspaceId: string,
    userId: string,
    input: PrepareAssetInput,
  ): Promise<{ asset: PublicAsset; upload: TransferDirective }> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    if (!isAssetMediaType(input.mediaType)) {
      throw unsupportedMediaType('unsupported_media_type', `Media type ${input.mediaType} is not allowed`, {
        allowed: Object.keys(ASSET_MEDIA_TYPES),
      })
    }
    if (input.byteSize > MAX_ASSET_BYTES) {
      throw payloadTooLarge('asset_too_large', `Asset exceeds ${MAX_ASSET_BYTES} bytes`, { maxBytes: MAX_ASSET_BYTES })
    }

    const id = randomUUID()
    // 每張票券使用獨立的暫存 key；驗證後才寫入不可變的 storageKey，有效票券無法改寫 ready 內容
    const uploadKey = `uploads/${workspaceId}/${id}/${randomUUID()}.${ASSET_MEDIA_TYPES[input.mediaType]}`
    const upload = await this.storage.presignPut(uploadKey, {
      contentType: input.mediaType,
      expiresInSeconds: this.options.media.uploadUrlTtlSeconds,
      maxBytes: input.byteSize,
    })
    const asset = await this.db.repos.assets.createPending({
      id,
      workspaceId,
      ownerId: userId,
      storageKey: storageKeyFor(workspaceId, id, input.mediaType),
      uploadKey,
      uploadExpiresAt: upload.expiresAt,
      mediaType: input.mediaType,
      byteSize: input.byteSize,
      checksum: input.checksum,
      width: input.width ?? null,
      height: input.height ?? null,
      durationMs: input.durationMs ?? null,
    })
    return { asset: toPublicAsset(asset), upload }
  }

  /**
   * 接受上傳完成：同一 transaction 記錄 accepted 並寫入 durable verify job 後才回覆。
   * 相同 size / checksum 的重送回傳目前狀態，不重複建立工作。
   */
  async complete(
    workspaceId: string,
    userId: string,
    assetId: string,
    input: CompleteAssetInput,
  ): Promise<{ asset: PublicAsset }> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    const current = await this.db.repos.assets.findById(assetId)
    if (!current || current.workspaceId !== workspaceId) throw notFound('Asset')
    assertSameTicket(current, input)

    // 網路呼叫放在 transaction 之外，不在持有 row lock 時等待 storage
    if (current.phase === 'awaiting_upload' && !(await this.storage.head(current.uploadKey!))) {
      throw conflict('upload_not_found', 'No uploaded object was found for this asset')
    }

    const asset = await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'assets.upload')
      const row = await repos.assets.lockForUpdate(workspaceId, assetId)
      if (!row) throw notFound('Asset')
      assertSameTicket(row, input)
      switch (row.phase) {
        case 'abandoned':
        case 'expired':
          throw conflict('asset_abandoned', 'The upload was abandoned and can no longer be completed')
        case 'awaiting_upload': {
          const accepted = await repos.assets.markAccepted(row.id)
          await repos.jobs.enqueue(
            { jobKey: jobKeys.verifyOriginal(row.id), jobType: 'verify_original', assetId: row.id },
            this.options.jobMaxAttempts,
          )
          return accepted
        }
        default:
          return row
      }
    })
    return { asset: toPublicAsset(asset) }
  }

  async list(workspaceId: string, userId: string): Promise<PublicAsset[]> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    return (await this.db.repos.assets.listByWorkspace(workspaceId)).map(toPublicAsset)
  }

  async get(workspaceId: string, userId: string, assetId: string): Promise<PublicAsset> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const asset = await this.db.repos.assets.findVisible(workspaceId, assetId)
    if (!asset) throw notFound('Asset')
    return toPublicAsset(asset)
  }

  /** 只對 ready 素材簽發下載票券；正式內容遺失時轉為 missing，不簽發假票券 */
  async content(
    workspaceId: string,
    userId: string,
    assetId: string,
  ): Promise<{ asset: PublicAsset; download: TransferDirective }> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const asset = await this.db.repos.assets.findVisible(workspaceId, assetId)
    if (!asset) throw notFound('Asset')
    assertReady(asset)

    const info = await this.storage.head(asset.storageKey)
    if (!info || info.size !== asset.byteSize) {
      await this.db.repos.assets.markMissing(asset.id)
      throw conflict('asset_missing', 'Asset content is missing', { status: 'missing' })
    }
    const download = await this.storage.presignGet(asset.storageKey, {
      expiresInSeconds: this.options.media.downloadUrlTtlSeconds,
    })
    return { asset: toPublicAsset(asset), download }
  }

  /**
   * Pending-only abandon（§5）：只有尚未接受 complete 的上傳可放棄。
   * 與 complete 鎖同一 row 序列化；已 abandon 的重送冪等成功。
   */
  async abandon(workspaceId: string, userId: string, assetId: string): Promise<void> {
    await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'assets.delete')
      const row = await repos.assets.lockForUpdate(workspaceId, assetId)
      if (!row) throw notFound('Asset')
      switch (row.phase) {
        case 'abandoned':
        case 'expired':
          return
        case 'awaiting_upload':
          await repos.assets.markAbandoned(row.id)
          await enqueueDeleteObject(repos, row.uploadKey, this.options.jobMaxAttempts)
          return
        default:
          throw conflict('asset_not_abandonable', 'Only uploads that have not been completed can be abandoned', {
            status: row.status,
          })
      }
    })
  }
}

function assertSameTicket(asset: Asset, input: CompleteAssetInput): void {
  if (asset.byteSize !== input.byteSize || asset.checksum !== input.checksum) {
    throw conflict('completion_mismatch', 'byteSize / checksum do not match the prepared asset')
  }
}

function assertReady(asset: Asset): void {
  if (asset.status === 'ready') return
  const codes = {
    pending: ['asset_not_ready', 'Asset is still being processed'],
    rejected: ['asset_rejected', 'Asset was rejected'],
    missing: ['asset_missing', 'Asset content is missing'],
  } as const
  const [code, message] = codes[asset.status]
  throw conflict(code, message, { status: asset.status })
}

export async function enqueueDeleteObject(repos: Repositories, key: string | null, maxAttempts: number): Promise<void> {
  if (!key) return
  await repos.jobs.enqueue({ jobKey: jobKeys.deleteObject(key), jobType: 'delete_object', assetId: null, payload: { key } }, maxAttempts)
}
