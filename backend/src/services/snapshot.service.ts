import { createHash } from 'node:crypto'
import type { SnapshotContent, WorkspaceSnapshot } from '../models/snapshot.ts'
import type { Workspace } from '../models/workspace.ts'
import type { WorkspaceAction } from '../domain/permissions.ts'
import type { Database } from '../repositories/index.ts'
import { notFound } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export class SnapshotService {
  constructor(private readonly db: Database) {}

  /** 目前 projection（B1 snapshot download） */
  async getState(workspaceId: string, userId: string): Promise<SnapshotContent> {
    return (await this.readState(workspaceId, userId, 'workspace.read')).content
  }

  /**
   * 建立 checkpoint snapshot。之後應由 worker 依 operation 數量 / 時間觸發（B3），
   * 目前先提供手動 API。
   */
  async create(workspaceId: string, userId: string): Promise<WorkspaceSnapshot> {
    const { workspace, content } = await this.readState(workspaceId, userId, 'snapshots.create')
    return this.db.repos.snapshots.insert(
      workspaceId,
      content.throughServerSeq,
      workspace.schemaVersion,
      content,
      checksum(content),
    )
  }

  async latest(workspaceId: string, userId: string): Promise<WorkspaceSnapshot> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const snapshot = await this.db.repos.snapshots.findLatest(workspaceId)
    if (!snapshot) throw notFound('Snapshot')
    return snapshot
  }

  /** version / serverSeq 與 objects / relations 來自同一個一致讀取點 */
  private readState(
    workspaceId: string,
    userId: string,
    action: WorkspaceAction,
  ): Promise<{ workspace: Workspace; content: SnapshotContent }> {
    return this.db.readConsistent(async (repos) => {
      const { workspace } = await requireWorkspaceAccess(repos, workspaceId, userId, action)
      const content: SnapshotContent = {
        workspaceId,
        workspaceVersion: workspace.currentVersion,
        throughServerSeq: workspace.lastServerSeq,
        objects: await repos.canvas.listActiveObjects(workspaceId),
        relations: await repos.canvas.listActiveRelations(workspaceId),
      }
      return { workspace, content }
    })
  }
}

export function checksum(content: unknown): string {
  return `sha256:${createHash('sha256').update(JSON.stringify(content)).digest('hex')}`
}
