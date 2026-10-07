import type { JsonObject } from './canvas.ts'

export const JOB_TYPES = ['verify_original', 'generate_thumbnail', 'delete_object'] as const
export type JobType = (typeof JOB_TYPES)[number]
export type JobStatus = 'queued' | 'running' | 'done' | 'dead'

export interface AssetJob {
  id: number
  jobKey: string
  jobType: JobType
  assetId: string | null
  payload: JsonObject
  status: JobStatus
  attempts: number
  maxAttempts: number
  runAfter: Date
  leaseOwner: string | null
  leaseExpiresAt: Date | null
  lastError: string | null
  createdAt: Date
  updatedAt: Date
  completedAt: Date | null
}

export interface NewJob {
  jobKey: string
  jobType: JobType
  assetId: string | null
  payload?: JsonObject
}

export const jobKeys = {
  verifyOriginal: (assetId: string) => `verify_original:${assetId}`,
  thumbnail: (assetId: string, version: number) => `generate_thumbnail:${assetId}:v${version}`,
  deleteObject: (key: string) => `delete_object:${key}`,
}
