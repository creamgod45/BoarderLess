import type { JsonObject } from './canvas.ts'

export const QUICK_SCHEME_RESOURCE_VERSION = 2

export interface AssetBinding {
  assetId: string
  sourceWorkspaceId: string
  kind: 'original' | 'thumbnail'
}

export interface QuickScheme {
  id: string
  ownerId: string
  resourceVersion: number
  name: string
  schemaVersion: number
  payload: JsonObject
  assetBindings: AssetBinding[]
  revision: number
  etag: string
  collectionRevision: number
  createdAt: Date
  updatedAt: Date
  deletedAt: Date | null
}

export interface QuickSchemeReceipt {
  ownerId: string
  idempotencyKey: string
  method: 'PUT' | 'DELETE'
  schemeId: string
  fingerprint: string
  responseStatus: number
  responseBody: JsonObject | null
  responseEtag: string | null
  createdAt: Date
  expiresAt: Date
}

/** 讀取時計算的素材可用狀態（不是授權；不含 URL） */
export type BindingStatus = 'available' | 'unavailable' | 'access_denied'

export interface PublicQuickScheme {
  id: string
  ownerId: string
  resourceVersion: number
  name: string
  schemaVersion: number
  payload: JsonObject
  assetBindings: AssetBinding[]
  revision: number
  createdAt: Date
  updatedAt: Date
  etag: string
  assetAvailability: (AssetBinding & { status: BindingStatus })[]
}
