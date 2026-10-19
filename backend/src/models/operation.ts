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
  /** 原 envelope 的 request digest（不對外公開；冪等比對用） */
  requestDigest: string | null
  committedAt: Date
}
