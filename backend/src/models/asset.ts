export const ASSET_STATUSES = ['pending', 'ready', 'rejected', 'missing'] as const
export type AssetStatus = (typeof ASSET_STATUSES)[number]

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
  createdAt: Date
  deletedAt: Date | null
}
