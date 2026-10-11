import type { Db } from '../db/client.ts'

export interface MediaReference {
  assetId: string
  thumbnailAssetId: string | null
}

export interface MediaReferenceProvenance {
  workspaceId: string
  actorId: string
  transactionId: string
  operationId: string
  objectId: string
  before: MediaReference
  after: MediaReference
}

interface Row {
  workspaceId: string
  actorId: string
  transactionId: string
  operationId: string
  objectId: string
  beforeAssetId: string
  beforeThumbnailAssetId: string | null
  afterAssetId: string
  afterThumbnailAssetId: string | null
}

/** MediaNode 引用變更的原交易證據（批次 F） */
export class ProvenanceRepository {
  constructor(private readonly db: Db) {}

  async record(p: MediaReferenceProvenance): Promise<void> {
    await this.db`
      INSERT INTO media_reference_provenance (
        workspace_id, actor_id, transaction_id, operation_id, object_id,
        before_asset_id, before_thumbnail_asset_id, after_asset_id, after_thumbnail_asset_id
      ) VALUES (
        ${p.workspaceId}, ${p.actorId}, ${p.transactionId}, ${p.operationId}, ${p.objectId},
        ${p.before.assetId}, ${p.before.thumbnailAssetId}, ${p.after.assetId}, ${p.after.thumbnailAssetId}
      )
    `
  }

  /** 只查同 workspace、同 actor 的原 operation（陌生 actor 一律查不到） */
  async find(workspaceId: string, actorId: string, operationId: string): Promise<MediaReferenceProvenance | null> {
    const [row] = await this.db<Row[]>`
      SELECT * FROM media_reference_provenance
      WHERE workspace_id = ${workspaceId} AND actor_id = ${actorId} AND operation_id = ${operationId}
    `
    if (!row) return null
    return {
      workspaceId: row.workspaceId,
      actorId: row.actorId,
      transactionId: row.transactionId,
      operationId: row.operationId,
      objectId: row.objectId,
      before: { assetId: row.beforeAssetId, thumbnailAssetId: row.beforeThumbnailAssetId },
      after: { assetId: row.afterAssetId, thumbnailAssetId: row.afterThumbnailAssetId },
    }
  }
}
