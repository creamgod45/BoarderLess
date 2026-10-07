import type { WorkspaceRole } from '../models/workspace.ts'

export type WorkspaceAction =
  | 'workspace.read'
  | 'workspace.update'
  | 'workspace.delete'
  | 'members.read'
  | 'members.manage'
  | 'operations.submit'
  | 'snapshots.create'
  | 'assets.upload'
  | 'assets.delete'

// TODO(contract §12-6)：commenter 可提交哪些 operation（例如留言）待定稿
const MATRIX: Record<WorkspaceRole, ReadonlySet<WorkspaceAction>> = {
  owner: new Set<WorkspaceAction>([
    'workspace.read',
    'workspace.update',
    'workspace.delete',
    'members.read',
    'members.manage',
    'operations.submit',
    'snapshots.create',
    'assets.upload',
    'assets.delete',
  ]),
  editor: new Set<WorkspaceAction>([
    'workspace.read',
    'workspace.update',
    'members.read',
    'operations.submit',
    'snapshots.create',
    'assets.upload',
    'assets.delete',
  ]),
  commenter: new Set<WorkspaceAction>(['workspace.read', 'members.read']),
  viewer: new Set<WorkspaceAction>(['workspace.read', 'members.read']),
}

export function can(role: WorkspaceRole, action: WorkspaceAction): boolean {
  return MATRIX[role].has(action)
}
