import { json, type Db } from '../db/client.ts'
import type { AssetPreparation, PreparationMetadata, UploadLease, VerifiedMetadata } from '../models/asset-preparation.ts'

export interface NewPreparation {
  workspaceId: string
  actorId: string
  requestId: string
  assetId: string
  requestDigest: string
  metadata: PreparationMetadata
}

export interface NewUploadLease {
  leaseId: string
  assetId: string
  ticketRequestId: string
  generation: number
  uploadKey: string
  expiresAt: Date
}

/** Preparation v2 的原 prepare receipt 與 upload ticket lease */
export class PreparationRepository {
  constructor(private readonly db: Db) {}

  /**
   * 保留 receipt。並行的同 key 請求在主鍵上等待先到者 commit，之後 DO NOTHING；
   * 回傳 false 代表 key 已存在（呼叫端應回滾並讀取既有 receipt）。
   */
  async reserve(prep: NewPreparation): Promise<boolean> {
    const rows = await this.db`
      INSERT INTO asset_preparations (workspace_id, actor_id, request_id, asset_id, request_digest, metadata)
      VALUES (
        ${prep.workspaceId}, ${prep.actorId}, ${prep.requestId}, ${prep.assetId},
        ${prep.requestDigest}, ${json(this.db, prep.metadata)}
      )
      ON CONFLICT DO NOTHING
      RETURNING request_id
    `
    return rows.length > 0
  }

  async find(workspaceId: string, actorId: string, requestId: string): Promise<AssetPreparation | null> {
    const [row] = await this.db<AssetPreparation[]>`
      SELECT * FROM asset_preparations
      WHERE workspace_id = ${workspaceId} AND actor_id = ${actorId} AND request_id = ${requestId}
    `
    return row ?? null
  }

  async findByAsset(assetId: string): Promise<AssetPreparation | null> {
    const [row] = await this.db<AssetPreparation[]>`SELECT * FROM asset_preparations WHERE asset_id = ${assetId}`
    return row ?? null
  }

  /** worker 解析結果；declared（metadata）不受影響 */
  async recordVerified(assetId: string, verified: VerifiedMetadata): Promise<void> {
    await this.db`
      UPDATE asset_preparations SET verified = ${json(this.db, verified)}, verified_at = now()
      WHERE asset_id = ${assetId}
    `
  }

  async findLease(assetId: string, leaseId: string): Promise<UploadLease | null> {
    const [row] = await this.db<UploadLease[]>`
      SELECT * FROM asset_upload_leases WHERE asset_id = ${assetId} AND lease_id = ${leaseId}
    `
    return row ?? null
  }

  async findLeaseByTicket(assetId: string, ticketRequestId: string): Promise<UploadLease | null> {
    const [row] = await this.db<UploadLease[]>`
      SELECT * FROM asset_upload_leases WHERE asset_id = ${assetId} AND ticket_request_id = ${ticketRequestId}
    `
    return row ?? null
  }

  /** 最新 generation 的 lease（唯一可以 complete 的 lease） */
  async latestLease(assetId: string): Promise<UploadLease | null> {
    const [row] = await this.db<UploadLease[]>`
      SELECT * FROM asset_upload_leases WHERE asset_id = ${assetId} ORDER BY generation DESC LIMIT 1
    `
    return row ?? null
  }

  async insertLease(lease: NewUploadLease): Promise<UploadLease> {
    const [row] = await this.db<UploadLease[]>`
      INSERT INTO asset_upload_leases (lease_id, asset_id, ticket_request_id, generation, upload_key, expires_at)
      VALUES (
        ${lease.leaseId}, ${lease.assetId}, ${lease.ticketRequestId}, ${lease.generation},
        ${lease.uploadKey}, ${lease.expiresAt}
      )
      RETURNING *
    `
    return row!
  }

  /**
   * GC：過期超過 grace、且不是被 complete 接受的 lease，回收其 staging object。
   * 與 complete 鎖同一 asset row（SKIP LOCKED），不會回收正在被接受的 lease。
   */
  async collectExpired(graceSeconds: number, limit: number): Promise<{ leaseId: string; uploadKey: string }[]> {
    return this.db<{ leaseId: string; uploadKey: string }[]>`
      UPDATE asset_upload_leases SET collected_at = now()
      WHERE lease_id IN (
        SELECT l.lease_id FROM asset_upload_leases l
        JOIN assets a ON a.id = l.asset_id
        WHERE l.collected_at IS NULL
          AND l.expires_at < now() - make_interval(secs => ${graceSeconds})
          AND a.upload_lease_id IS DISTINCT FROM l.lease_id
        ORDER BY l.expires_at
        LIMIT ${limit}
        FOR UPDATE OF a, l SKIP LOCKED
      )
      RETURNING lease_id, upload_key
    `
  }
}
