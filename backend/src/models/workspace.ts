export const WORKSPACE_ROLES = ['owner', 'editor', 'commenter', 'viewer'] as const
export type WorkspaceRole = (typeof WORKSPACE_ROLES)[number]

export interface Workspace {
  id: string
  ownerId: string
  title: string
  currentVersion: number
  lastServerSeq: number
  schemaVersion: number
  createdAt: Date
  updatedAt: Date
  deletedAt: Date | null
}

export interface WorkspaceMember {
  workspaceId: string
  userId: string
  role: WorkspaceRole
  joinedAt: Date
  revokedAt: Date | null
}

/** 列表用：Workspace 加上目前使用者的角色 */
export interface WorkspaceWithRole extends Workspace {
  role: WorkspaceRole
}
