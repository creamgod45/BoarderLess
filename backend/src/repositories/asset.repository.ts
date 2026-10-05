import type { Db } from '../db/client.ts'
import type { Asset, AssetStatus } from '../models/asset.ts'

export interface NewPendingAsset {
  id: string
  workspaceId: string
  ownerId: string
  storageKey: string
  uploadKey: string
  uploadExpiresAt: Date
  mediaType: string
  byteSize: number
  checksum: string
  width: number | null
  height: number | null
  durationMs: number | null
}

export interface NewDerivedAsset {
  id: string
  workspaceId: string
  ownerId: string
  storageKey: string
  mediaType: string
  byteSize: number
  checksum: string
  width: number
  height: number
  sourceAssetId: string
  derivativeType: string
  derivativeVersion: number
}

export interface VerifiedMetadata {
  width: number
  height: number
  durationMs: number | null
}

export class AssetRepository {
  constructor(private readonly db: Db) {}

  async createPending(asset: NewPendingAsset): Promise<Asset> {
    const [row] = await this.db<Asset[]>`
      INSERT INTO assets (
        id, workspace_id, owner_id, storage_key, upload_key, upload_expires_at,
        media_type, byte_size, checksum, width, height, duration_ms, status, phase
      ) VALUES (
        ${asset.id}, ${asset.workspaceId}, ${asset.ownerId}, ${asset.storageKey}, ${asset.uploadKey},
        ${asset.uploadExpiresAt}, ${asset.mediaType}, ${asset.byteSize}, ${asset.checksum},
        ${asset.width}, ${asset.height}, ${asset.durationMs}, 'pending', 'awaiting_upload'
      )
      RETURNING *
    `
    return row!
  }

  /** 公開可見（未 abandon / expire）的資產 */
  async findVisible(workspaceId: string, id: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      SELECT * FROM assets WHERE workspace_id = ${workspaceId} AND id = ${id} AND deleted_at IS NULL
    `
    return row ?? null
  }

  async findVisibleMany(workspaceId: string, ids: string[]): Promise<Asset[]> {
    if (ids.length === 0) return []
    return this.db<Asset[]>`
      SELECT * FROM assets
      WHERE workspace_id = ${workspaceId} AND id = ANY(${ids}::uuid[]) AND deleted_at IS NULL
    `
  }

  /** 生命週期轉換用：鎖定 row（含已 abandon 的），須在 transaction 內呼叫 */
  async lockForUpdate(workspaceId: string, id: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      SELECT * FROM assets WHERE workspace_id = ${workspaceId} AND id = ${id} FOR UPDATE
    `
    return row ?? null
  }

  async findById(id: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`SELECT * FROM assets WHERE id = ${id}`
    return row ?? null
  }

  async listByWorkspace(workspaceId: string): Promise<Asset[]> {
    return this.db<Asset[]>`
      SELECT * FROM assets WHERE workspace_id = ${workspaceId} AND deleted_at IS NULL ORDER BY created_at DESC, id
    `
  }

  async markAccepted(id: string): Promise<Asset> {
    const [row] = await this.db<Asset[]>`
      UPDATE assets SET phase = 'accepted', completion_accepted_at = now(), updated_at = now()
      WHERE id = ${id} AND phase = 'awaiting_upload'
      RETURNING *
    `
    return row!
  }

  async markAbandoned(id: string): Promise<void> {
    await this.db`
      UPDATE assets SET phase = 'abandoned', deleted_at = now(), updated_at = now()
      WHERE id = ${id} AND phase = 'awaiting_upload'
    `
  }

  /** CAS：只有 accepted 才能發布驗證結果，避免多個 worker 發布不同結果 */
  async markReady(id: string, meta: VerifiedMetadata): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      UPDATE assets SET
        phase = 'ready', status = 'ready', verified_at = now(), updated_at = now(),
        width = ${meta.width}, height = ${meta.height}, duration_ms = ${meta.durationMs}
      WHERE id = ${id} AND phase = 'accepted'
      RETURNING *
    `
    return row ?? null
  }

  async markRejected(id: string, reason: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      UPDATE assets SET phase = 'rejected', status = 'rejected', rejection_reason = ${reason}, updated_at = now()
      WHERE id = ${id} AND phase = 'accepted'
      RETURNING *
    `
    return row ?? null
  }

  /** 已 ready 但正式內容遺失 */
  async markMissing(id: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      UPDATE assets SET phase = 'missing', status = 'missing', updated_at = now()
      WHERE id = ${id} AND phase = 'ready'
      RETURNING *
    `
    return row ?? null
  }

  /** 衍生檔以 (source, type, version) 去重；已存在時回傳 null */
  async insertDerived(asset: NewDerivedAsset): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      INSERT INTO assets (
        id, workspace_id, owner_id, storage_key, media_type, byte_size, checksum, width, height,
        status, phase, verified_at, source_asset_id, derivative_type, derivative_version
      ) VALUES (
        ${asset.id}, ${asset.workspaceId}, ${asset.ownerId}, ${asset.storageKey}, ${asset.mediaType},
        ${asset.byteSize}, ${asset.checksum}, ${asset.width}, ${asset.height},
        'ready', 'ready', now(), ${asset.sourceAssetId}, ${asset.derivativeType}, ${asset.derivativeVersion}
      )
      ON CONFLICT (source_asset_id, derivative_type, derivative_version) WHERE source_asset_id IS NOT NULL
      DO NOTHING
      RETURNING *
    `
    return row ?? null
  }

  async findDerived(sourceAssetId: string, derivativeType: string, version: number): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      SELECT * FROM assets
      WHERE source_asset_id = ${sourceAssetId} AND derivative_type = ${derivativeType} AND derivative_version = ${version}
    `
    return row ?? null
  }

  async linkThumbnail(id: string, thumbnailAssetId: string): Promise<void> {
    await this.db`
      UPDATE assets SET thumbnail_asset_id = ${thumbnailAssetId}, updated_at = now()
      WHERE id = ${id} AND thumbnail_asset_id IS NULL
    `
  }

  /** GC：upload ticket 過期且從未接受 complete 的資產（不碰 accepted / processing） */
  async expireStaleUploads(graceSeconds: number, limit: number): Promise<{ id: string; uploadKey: string | null }[]> {
    return this.db<{ id: string; uploadKey: string | null }[]>`
      UPDATE assets SET phase = 'expired', deleted_at = now(), updated_at = now()
      WHERE id IN (
        SELECT id FROM assets
        WHERE phase = 'awaiting_upload' AND upload_expires_at < now() - make_interval(secs => ${graceSeconds})
        ORDER BY upload_expires_at
        LIMIT ${limit}
        FOR UPDATE SKIP LOCKED
      )
      RETURNING id, upload_key
    `
  }

  async countByStatus(): Promise<Record<AssetStatus, number>> {
    const rows = await this.db<{ status: AssetStatus; count: number }[]>`
      SELECT status, count(*)::int AS count FROM assets WHERE deleted_at IS NULL GROUP BY status
    `
    const counts: Record<AssetStatus, number> = { pending: 0, ready: 0, rejected: 0, missing: 0 }
    for (const row of rows) counts[row.status] = row.count
    return counts
  }
}
