import type { Asset } from '../models/asset.ts'
import type { Database } from '../repositories/index.ts'
import { notFound, unprocessable } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export const ALLOWED_MEDIA_TYPES: Record<string, string> = {
  'image/png': 'png',
  'image/jpeg': 'jpg',
  'image/webp': 'webp',
  'image/gif': 'gif',
  'video/mp4': 'mp4',
  'video/webm': 'webm',
}
export const MAX_ASSET_BYTES = 200 * 1024 * 1024

export interface CreateAssetInput {
  mediaType: string
  byteSize: number
  checksum: string
  width?: number
  height?: number
  durationMs?: number
}

export interface PendingUpload {
  asset: Asset
  /** 短效 signed upload URL；object storage 尚未串接（B3），目前為 null */
  uploadUrl: string | null
}

export class AssetService {
  constructor(private readonly db: Database) {}

  /** 先建立 pending metadata，之後 client 以 signed URL 直傳 object storage（§5.1 assets） */
  async createPending(workspaceId: string, userId: string, input: CreateAssetInput): Promise<PendingUpload> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.upload')
    const extension = ALLOWED_MEDIA_TYPES[input.mediaType]
    if (!extension) {
      throw unprocessable('unsupported_media_type', `Media type ${input.mediaType} is not allowed`, {
        allowed: Object.keys(ALLOWED_MEDIA_TYPES),
      })
    }
    if (input.byteSize > MAX_ASSET_BYTES) {
      throw unprocessable('asset_too_large', `Asset exceeds ${MAX_ASSET_BYTES} bytes`)
    }

    const id = crypto.randomUUID()
    const asset = await this.db.repos.assets.createPending({
      id,
      workspaceId,
      ownerId: userId,
      storageKey: `workspaces/${workspaceId}/assets/${id}.${extension}`,
      mediaType: input.mediaType,
      byteSize: input.byteSize,
      checksum: input.checksum,
      width: input.width ?? null,
      height: input.height ?? null,
      durationMs: input.durationMs ?? null,
    })
    return { asset, uploadUrl: null }
  }

  async list(workspaceId: string, userId: string): Promise<Asset[]> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    return this.db.repos.assets.listByWorkspace(workspaceId)
  }

  async get(workspaceId: string, userId: string, assetId: string): Promise<Asset> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const asset = await this.db.repos.assets.findById(workspaceId, assetId)
    if (!asset) throw notFound('Asset')
    return asset
  }

  async delete(workspaceId: string, userId: string, assetId: string): Promise<void> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'assets.delete')
    if (!(await this.db.repos.assets.softDelete(workspaceId, assetId))) throw notFound('Asset')
  }
}
