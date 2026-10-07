import type { Workspace, WorkspaceWithRole } from '../models/workspace.ts'
import type { Database } from '../repositories/index.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export class WorkspaceService {
  constructor(private readonly db: Database) {}

  /** 建立 Workspace 並把建立者加入為 owner（同一 transaction） */
  create(ownerId: string, title: string): Promise<WorkspaceWithRole> {
    return this.db.transaction(async (repos) => {
      const workspace = await repos.workspaces.create(ownerId, title.trim())
      await repos.members.upsert(workspace.id, ownerId, 'owner')
      return { ...workspace, role: 'owner' as const }
    })
  }

  listForUser(userId: string): Promise<WorkspaceWithRole[]> {
    return this.db.repos.workspaces.listForUser(userId)
  }

  async get(workspaceId: string, userId: string): Promise<WorkspaceWithRole> {
    const { workspace, member } = await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    return { ...workspace, role: member.role }
  }

  async rename(workspaceId: string, userId: string, title: string): Promise<WorkspaceWithRole> {
    return this.db.transaction(async (repos) => {
      const { member } = await requireWorkspaceAccess(repos, workspaceId, userId, 'workspace.update', { lock: true })
      const updated = (await repos.workspaces.updateTitle(workspaceId, title.trim())) as Workspace
      return { ...updated, role: member.role }
    })
  }

  /** Soft delete；assets / snapshots 清除排程見 PROGRESS.md 待辦 */
  async delete(workspaceId: string, userId: string): Promise<void> {
    await this.db.transaction(async (repos) => {
      await requireWorkspaceAccess(repos, workspaceId, userId, 'workspace.delete', { lock: true })
      await repos.workspaces.softDelete(workspaceId)
    })
  }
}
