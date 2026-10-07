import type { JsonObject } from './canvas.ts'

/** workspace_operations 中已提交的正式 operation */
export interface CommittedOperation {
  workspaceId: string
  serverSeq: number
  operationId: string
  transactionId: string
  actorId: string
  clientId: string
  clientSeq: number
  baseVersion: number
  workspaceVersion: number
  operationType: string
  payload: JsonObject
  schemaVersion: number
  committedAt: Date
}

export interface OutboxEvent {
  id: number
  workspaceId: string
  serverSeq: number
  eventType: string
  payload: JsonObject
  createdAt: Date
  publishedAt: Date | null
  attemptCount: number
}
