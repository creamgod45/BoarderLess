import type { Db } from '../db/client.ts'
import type { Asset } from '../models/asset.ts'

export interface NewAsset {
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
}

export class AssetRepository {
  constructor(private readonly db: Db) {}

  async createPending(asset: NewAsset): Promise<Asset> {
    const [row] = await this.db<Asset[]>`
      INSERT INTO assets (
        id, workspace_id, owner_id, storage_key, media_type, byte_size, checksum, width, height, duration_ms
      ) VALUES (
        ${asset.id}, ${asset.workspaceId}, ${asset.ownerId}, ${asset.storageKey}, ${asset.mediaType}, ${asset.byteSize},
        ${asset.checksum}, ${asset.width}, ${asset.height}, ${asset.durationMs}
      )
      RETURNING *
    `
    return row!
  }

  async findById(workspaceId: string, id: string): Promise<Asset | null> {
    const [row] = await this.db<Asset[]>`
      SELECT * FROM assets WHERE workspace_id = ${workspaceId} AND id = ${id} AND deleted_at IS NULL
    `
    return row ?? null
  }

  async listByWorkspace(workspaceId: string): Promise<Asset[]> {
    return this.db<Asset[]>`
      SELECT * FROM assets WHERE workspace_id = ${workspaceId} AND deleted_at IS NULL ORDER BY created_at DESC
    `
  }

  async softDelete(workspaceId: string, id: string): Promise<boolean> {
    const result = await this.db`
      UPDATE assets SET deleted_at = now() WHERE workspace_id = ${workspaceId} AND id = ${id} AND deleted_at IS NULL
    `
    return result.count > 0
  }
}
