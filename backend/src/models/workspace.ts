import type { CanvasStyle } from '../domain/canvas-style.ts'
import type { SequenceDiagram } from '../domain/sequence-diagrams.ts'

export const WORKSPACE_ROLES = ['owner', 'editor', 'commenter', 'viewer'] as const
export type WorkspaceRole = (typeof WORKSPACE_ROLES)[number]

export interface Workspace {
  id: string
  ownerId: string
  title: string
  currentVersion: number
  lastServerSeq: number
  schemaVersion: number
  /** title / membership / 角色的 durable 版本（與 canvas workspaceVersion 分開） */
  metadataRevision: number
  /** 共用背景（schema 1）與其 CAS 版本 */
  canvasStyle: CanvasStyle
  canvasStyleVersion: number
  /** 時序圖 metadata（canonical，依 containerId 排序）與其 CAS 版本 */
  sequenceDiagrams: SequenceDiagram[]
  sequenceDiagramsVersion: number
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
