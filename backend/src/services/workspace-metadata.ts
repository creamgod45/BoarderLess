import type { Repositories } from '../repositories/index.ts'

export type MetadataChangeReason = 'title' | 'membership' | 'deleted'

/**
 * Workspace metadata 變更：同一 transaction 推進 metadataRevision 並寫入 durable outbox，
 * gateway 收到後重新檢查每條連線的 ACL（撤權即關閉），再送 workspace_metadata。
 */
export async function recordMetadataChange(repos: Repositories, workspaceId: string, reason: MetadataChangeReason): Promise<number> {
  const { metadataRevision, lastServerSeq } = await repos.workspaces.bumpMetadataRevision(workspaceId)
  await repos.outbox.enqueue(workspaceId, lastServerSeq, 'workspace_metadata', { metadataRevision, reason })
  return metadataRevision
}
