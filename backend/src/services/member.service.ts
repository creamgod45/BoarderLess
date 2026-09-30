import type { WorkspaceMember, WorkspaceRole } from '../models/workspace.ts'
import type { MemberWithProfile } from '../repositories/member.repository.ts'
import type { Database } from '../repositories/index.ts'
import { notFound, unprocessable } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export type AssignableRole = Exclude<WorkspaceRole, 'owner'>

export class MemberService {
  constructor(private readonly db: Database) {}

  async list(workspaceId: string, userId: string): Promise<MemberWithProfile[]> {
    await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'members.read')
    return this.db.repos.members.listActive(workspaceId)
  }

  /** 新增成員或變更角色；owner 轉移尚未支援 */
  setRole(workspaceId: string, actorId: string, targetUserId: string, role: AssignableRole): Promise<WorkspaceMember> {
    return this.db.transaction(async (repos) => {
      const { workspace } = await requireWorkspaceAccess(repos, workspaceId, actorId, 'members.manage', { lock: true })
      if (targetUserId === workspace.ownerId) {
        throw unprocessable('owner_role_immutable', 'The owner role cannot be changed')
      }
      if (!(await repos.users.findActiveById(targetUserId))) throw notFound('User')
      return repos.members.upsert(workspaceId, targetUserId, role)
    })
  }

  revoke(workspaceId: string, actorId: string, targetUserId: string): Promise<void> {
    return this.db.transaction(async (repos) => {
      const { workspace } = await requireWorkspaceAccess(repos, workspaceId, actorId, 'members.manage', { lock: true })
      if (targetUserId === workspace.ownerId) {
        throw unprocessable('owner_role_immutable', 'The owner cannot be removed')
      }
      if (!(await repos.members.revoke(workspaceId, targetUserId))) throw notFound('Member')
    })
  }
}
