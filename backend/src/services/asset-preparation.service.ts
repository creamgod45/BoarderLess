import { randomUUID } from 'node:crypto'
import { canonicalMetadata, preparationDigest } from '../domain/asset-preparation.ts'
import {
  ASSET_MEDIA_TYPES,
  isAssetMediaType,
  MAX_ASSET_BYTES,
  toPublicAsset,
  type Asset,
} from '../models/asset.ts'
import {
  PREPARATION_RESPONSE_SCHEMA,
  preparationPhaseOf,
  toPublicLease,
  type AssetPreparation,
  type PreparationEnvelope,
  type PreparationMetadata,
  type UploadLease,
} from '../models/asset-preparation.ts'
import { jobKeys } from '../models/job.ts'
import type { Database } from '../repositories/index.ts'
import type { ObjectStorage } from '../storage/types.ts'
import { AppError, badRequest, conflict, payloadTooLarge, unsupportedMediaType } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'
import { storageKeyFor, type AssetServiceOptions } from './asset.service.ts'

export interface PrepareInput {
  requestId: string
  metadata: PreparationMetadata
}

export interface PreparationCompleteInput {
  requestId: string
  leaseId: string
  byteSize: number
  checksum: string
}

/** 首次建立（201）或冪等重送 / 查詢（200） */
export interface PreparationResult {
  created: boolean
  envelope: PreparationEnvelope
}

const preparationNotFound = () => new AppError(404, 'preparation_not_found', 'Preparation not found')

/**
 * Asset preparation v2（docs/BACKEND_ASSET_PREPARATION_V2_SPEC.md）。
 * 固定 identity：authenticated user + workspace + requestId → 同一個 asset，永久保留。
 * 票券以 lease（generation + 獨立 staging key）簽發；complete 只接受當前 lease。
 * 每個呼叫都重新檢查 membership / role，不信任 body 內的 user / owner。
 */
export class AssetPreparationService {
  constructor(
    private readonly db: Database,
    private readonly storage: ObjectStorage,
    private readonly options: AssetServiceOptions,
  ) {}

  async prepare(workspaceId: string, userId: string, input: PrepareInput): Promise<PreparationResult> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    const metadata = canonicalMetadata(input.metadata)
    const { mediaType } = metadata
    if (!isAssetMediaType(mediaType)) {
      throw unsupportedMediaType('unsupported_media_type', `Media type ${mediaType} is not allowed`, {
        allowed: Object.keys(ASSET_MEDIA_TYPES),
      })
    }
    if (metadata.byteSize > MAX_ASSET_BYTES) {
      throw payloadTooLarge('asset_too_large', `Asset exceeds ${MAX_ASSET_BYTES} bytes`, { maxBytes: MAX_ASSET_BYTES })
    }
    const requestDigest = preparationDigest(metadata)

    const existing = await this.db.repos.preparations.find(workspaceId, userId, input.requestId)
    if (existing) return { created: false, envelope: await this.replay(existing, requestDigest) }

    const assetId = randomUUID()
    const created = await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'assets.upload')
      // 先保留 receipt：並行的同 key 請求在主鍵上序列化，輸家不建立任何 asset
      const reserved = await repos.preparations.reserve({
        workspaceId,
        actorId: userId,
        requestId: input.requestId,
        assetId,
        requestDigest,
        metadata,
      })
      if (!reserved) return false
      await repos.assets.createPrepared({
        id: assetId,
        workspaceId,
        ownerId: userId,
        storageKey: storageKeyFor(workspaceId, assetId, mediaType),
        mediaType,
        byteSize: metadata.byteSize,
        checksum: metadata.checksum,
        width: metadata.width,
        height: metadata.height,
        durationMs: metadata.durationMs,
      })
      return true
    })

    const preparation = await this.db.repos.preparations.find(workspaceId, userId, input.requestId)
    if (!preparation) throw new Error('preparation disappeared after reservation')
    return { created, envelope: await this.replay(preparation, requestDigest) }
  }

  /** 查詢原 prepare；只限本人在此 Workspace 的 request，不洩漏他人的 request 是否存在 */
  async get(workspaceId: string, userId: string, requestId: string): Promise<PreparationEnvelope> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    return this.envelopeFor(await this.requirePreparation(workspaceId, userId, requestId))
  }

  /**
   * 簽發 upload ticket lease。同 ticketRequestId 冪等回同一 lease（重新簽名同一 staging key、同到期時間）；
   * 過期或已被新 lease 取代時不自動 renew，APP 必須另存新的 ticketRequestId。
   */
  async issueTicket(
    workspaceId: string,
    userId: string,
    requestId: string,
    ticketRequestId: string,
  ): Promise<PreparationResult> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    const preparation = await this.requirePreparation(workspaceId, userId, requestId)

    const { lease, created } = await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'assets.upload')
      const asset = await repos.assets.lockForUpdate(workspaceId, preparation.assetId)
      if (!asset) throw preparationNotFound()
      assertUploadAllowed(asset)

      const latest = await repos.preparations.latestLease(asset.id)
      const existing = await repos.preparations.findLeaseByTicket(asset.id, ticketRequestId)
      if (existing) {
        assertCurrentLease(existing, latest)
        assertLeaseUsable(existing)
        return { lease: existing, created: false }
      }
      const leaseId = randomUUID()
      const lease = await repos.preparations.insertLease({
        leaseId,
        assetId: asset.id,
        ticketRequestId,
        generation: (latest?.generation ?? 0) + 1,
        uploadKey: `uploads/${workspaceId}/${asset.id}/${leaseId}.${ASSET_MEDIA_TYPES[asset.mediaType as keyof typeof ASSET_MEDIA_TYPES]}`,
        expiresAt: new Date(Date.now() + this.options.media.uploadUrlTtlSeconds * 1000),
      })
      return { lease, created: true }
    })

    // 簽名在 transaction 之外；失敗時 lease 已 durable，同 ticketRequestId 重試會拿回同一 lease
    const remainingSeconds = Math.floor((lease.expiresAt.getTime() - Date.now()) / 1000)
    if (remainingSeconds < 1) throw leaseExpired(lease)
    const directive = await this.storage.presignPut(lease.uploadKey, {
      contentType: preparation.metadata.mediaType,
      expiresInSeconds: remainingSeconds,
      maxBytes: preparation.metadata.byteSize,
    })

    const envelope = await this.envelopeFor(preparation)
    // 簽名期間被 abandon 時不交出可用的 PUT
    if (envelope.phase !== 'prepared') throw uploadNotAllowed(envelope.phase)
    return {
      created,
      envelope: {
        ...envelope,
        upload: {
          method: 'PUT',
          url: directive.url,
          headers: directive.headers,
          expiresAt: directive.expiresAt,
          leaseId: lease.leaseId,
        },
      },
    }
  }

  /**
   * 接受上傳完成：只接受當前 lease、相同 size / checksum；同一 transaction 固定 lease 並寫入 durable verify job。
   * 已接受後重送同 lease 回目前 phase，不重建工作；其他 lease 一律 409。
   */
  async complete(
    workspaceId: string,
    userId: string,
    requestId: string,
    input: PreparationCompleteInput,
  ): Promise<PreparationEnvelope> {
    if (input.requestId !== requestId) {
      throw badRequest('request_id_mismatch', 'Body requestId must match the path requestId')
    }
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    const preparation = await this.requirePreparation(workspaceId, userId, requestId)
    if (preparation.metadata.byteSize !== input.byteSize || preparation.metadata.checksum !== input.checksum) {
      throw conflict('completion_mismatch', 'byteSize / checksum do not match the prepared metadata')
    }
    const assetId = preparation.assetId
    const lease = await this.db.repos.preparations.findLease(assetId, input.leaseId)
    if (!lease) throw conflict('upload_lease_mismatch', 'leaseId does not belong to this preparation')

    // storage 呼叫放在 transaction 之外，不在持有 row lock 時等待網路
    const current = await this.db.repos.assets.findById(assetId)
    const uploaded =
      current?.phase === 'awaiting_upload' && lease.collectedAt === null && (await this.storage.head(lease.uploadKey)) !== null

    await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'assets.upload')
      const row = await repos.assets.lockForUpdate(workspaceId, assetId)
      if (!row) throw preparationNotFound()
      switch (row.phase) {
        case 'abandoned':
        case 'expired':
          throw conflict('asset_abandoned', 'The preparation was abandoned and can no longer be completed', {
            phase: 'abandoned',
          })
        case 'awaiting_upload': {
          const fresh = await repos.preparations.findLease(assetId, lease.leaseId)
          assertCurrentLease(fresh!, await repos.preparations.latestLease(assetId))
          if (fresh!.collectedAt) throw leaseExpired(fresh!)
          if (!uploaded) throw conflict('upload_not_found', 'No uploaded object was found for this lease')
          await repos.assets.markAcceptedWithLease(row.id, fresh!)
          await repos.jobs.enqueue(
            { jobKey: jobKeys.verifyOriginal(row.id), jobType: 'verify_original', assetId: row.id },
            this.options.jobMaxAttempts,
          )
          return
        }
        default:
          if (row.uploadLeaseId !== lease.leaseId) {
            throw conflict('upload_lease_mismatch', 'Completion was accepted for a different lease', {
              phase: preparationPhaseOf(row.phase),
            })
          }
      }
    })
    return this.envelopeFor(preparation)
  }

  private async requirePreparation(workspaceId: string, userId: string, requestId: string): Promise<AssetPreparation> {
    const preparation = await this.db.repos.preparations.find(workspaceId, userId, requestId)
    if (!preparation) throw preparationNotFound()
    return preparation
  }

  /** 既有 receipt：metadata 相同才回原 identity；不同則 409，不另建資產 */
  private async replay(preparation: AssetPreparation, requestDigest: string): Promise<PreparationEnvelope> {
    if (preparation.requestDigest !== requestDigest) {
      throw conflict('preparation_mismatch', 'requestId was already prepared with different metadata', {
        requestDigest: preparation.requestDigest,
      })
    }
    return this.envelopeFor(preparation)
  }

  private async envelopeFor(preparation: AssetPreparation): Promise<PreparationEnvelope> {
    const repos = this.db.repos
    const asset = await repos.assets.findById(preparation.assetId)
    if (!asset) throw new Error(`asset ${preparation.assetId} missing for preparation`)
    const lease = asset.uploadLeaseId
      ? await repos.preparations.findLease(asset.id, asset.uploadLeaseId)
      : await repos.preparations.latestLease(asset.id)
    return {
      schema: PREPARATION_RESPONSE_SCHEMA,
      requestId: preparation.requestId,
      requestDigest: preparation.requestDigest,
      phase: preparationPhaseOf(asset.phase),
      declared: canonicalMetadata(preparation.metadata),
      verified: preparation.verified
        ? { width: preparation.verified.width, height: preparation.verified.height, durationMs: preparation.verified.durationMs }
        : null,
      asset: toPublicAsset(asset),
      lease: lease ? toPublicLease(lease) : null,
    }
  }
}

function assertUploadAllowed(asset: Asset): void {
  if (asset.phase !== 'awaiting_upload') throw uploadNotAllowed(preparationPhaseOf(asset.phase))
}

function uploadNotAllowed(phase: string): AppError {
  return conflict('upload_not_allowed', 'Upload tickets are only issued while the preparation is still prepared', { phase })
}

function assertCurrentLease(lease: UploadLease, latest: UploadLease | null): void {
  if (latest && latest.leaseId !== lease.leaseId) {
    throw conflict('upload_lease_superseded', 'A newer upload lease replaced this one', {
      leaseId: lease.leaseId,
      currentLeaseId: latest.leaseId,
    })
  }
}

function assertLeaseUsable(lease: UploadLease): void {
  if (lease.collectedAt || lease.expiresAt.getTime() - Date.now() < 1000) throw leaseExpired(lease)
}

function leaseExpired(lease: UploadLease): AppError {
  return conflict('upload_lease_expired', 'The upload lease has expired; request a new ticket with a new ticketRequestId', {
    leaseId: lease.leaseId,
    expiresAt: lease.expiresAt,
  })
}
