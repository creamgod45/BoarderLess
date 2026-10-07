import type { Db } from '../db/client.ts'

export type FenceKind = 'transaction' | 'operation'

export interface FenceKey {
  kind: FenceKind
  id: string
}

/** 已 fence 的原 transaction / operation ID（只限同一 actor） */
export class FenceRepository {
  constructor(private readonly db: Db) {}

  async insert(workspaceId: string, actorId: string, keys: FenceKey[]): Promise<void> {
    for (const key of keys) {
      await this.db`
        INSERT INTO transaction_fences (workspace_id, actor_id, fence_kind, fenced_id)
        VALUES (${workspaceId}, ${actorId}, ${key.kind}, ${key.id})
        ON CONFLICT DO NOTHING
      `
    }
  }

  async findFenced(workspaceId: string, actorId: string, transactionIds: string[], operationIds: string[]): Promise<FenceKey[]> {
    const rows = await this.db<{ fenceKind: FenceKind; fencedId: string }[]>`
      SELECT fence_kind, fenced_id FROM transaction_fences
      WHERE workspace_id = ${workspaceId} AND actor_id = ${actorId}
        AND (
          (fence_kind = 'transaction' AND fenced_id = ANY(${transactionIds}::uuid[]))
          OR (fence_kind = 'operation' AND fenced_id = ANY(${operationIds}::uuid[]))
        )
    `
    return rows.map((r) => ({ kind: r.fenceKind, id: r.fencedId }))
  }
}
