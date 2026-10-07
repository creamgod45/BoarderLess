import { can, type WorkspaceAction } from '../domain/permissions.ts'
import type { Workspace, WorkspaceMember } from '../models/workspace.ts'
import type { Repositories } from '../repositories/index.ts'
import { forbidden, notFound } from '../utils/errors.ts'

export interface WorkspaceAccess {
  workspace: Workspace
  member: WorkspaceMember
}

/**
 * 每個讀寫路徑都重新檢查 membership 與角色（§9）。
 * 非成員一律回 404，避免洩漏 Workspace 是否存在。
 */
export async function requireWorkspaceAccess(
  repos: Repositories,
  workspaceId: string,
  userId: string,
  action: WorkspaceAction,
  options: { lock?: boolean } = {},
): Promise<WorkspaceAccess> {
  const workspace = options.lock
    ? await repos.workspaces.lockForUpdate(workspaceId)
    : await repos.workspaces.findById(workspaceId)
  if (!workspace) throw notFound('Workspace')

  const member = await repos.members.findActive(workspaceId, userId)
  if (!member) throw notFound('Workspace')
  if (!can(member.role, action)) throw forbidden(`Role "${member.role}" cannot perform ${action}`)

  return { workspace, member }
}
