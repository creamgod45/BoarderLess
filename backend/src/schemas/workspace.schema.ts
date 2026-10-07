import { Type } from 'typebox'
import { WORKSPACE_ROLES } from '../models/workspace.ts'
import { DateTime, errorResponses, Uuid, WorkspaceParams } from './common.schema.ts'

export const Role = Type.Enum(WORKSPACE_ROLES)
export const AssignableRole = Type.Enum(['editor', 'commenter', 'viewer'] as const)

export const WorkspaceSchema = Type.Object({
  id: Type.String(),
  ownerId: Type.String(),
  title: Type.String(),
  currentVersion: Type.Integer(),
  lastServerSeq: Type.Integer(),
  schemaVersion: Type.Integer(),
  role: Role,
  createdAt: DateTime,
  updatedAt: DateTime,
})

const Title = Type.String({ minLength: 1, maxLength: 200 })

export const listWorkspacesSchema = {
  tags: ['workspaces'],
  summary: '我可存取的 Workspaces',
  response: { 200: Type.Object({ workspaces: Type.Array(WorkspaceSchema) }), ...errorResponses },
}

export const createWorkspaceSchema = {
  tags: ['workspaces'],
  summary: '建立 Workspace（建立者為 owner）',
  body: Type.Object({ title: Title }, { additionalProperties: false }),
  response: { 201: WorkspaceSchema, ...errorResponses },
}

export const getWorkspaceSchema = {
  tags: ['workspaces'],
  summary: 'Workspace metadata 與我的角色',
  params: WorkspaceParams,
  response: { 200: WorkspaceSchema, ...errorResponses },
}

export const updateWorkspaceSchema = {
  tags: ['workspaces'],
  summary: '重新命名 Workspace（owner / editor）',
  params: WorkspaceParams,
  body: Type.Object({ title: Title }, { additionalProperties: false }),
  response: { 200: WorkspaceSchema, ...errorResponses },
}

export const deleteWorkspaceSchema = {
  tags: ['workspaces'],
  summary: 'Soft delete Workspace（owner）',
  params: WorkspaceParams,
  response: { 204: Type.Null(), ...errorResponses },
}

// ---- members ----

export const MemberSchema = Type.Object({
  userId: Type.String(),
  displayName: Type.String(),
  role: Role,
  joinedAt: DateTime,
})

export const MemberParams = Type.Object({ workspaceId: Uuid, userId: Uuid })

export const listMembersSchema = {
  tags: ['members'],
  summary: '成員列表',
  params: WorkspaceParams,
  response: { 200: Type.Object({ members: Type.Array(MemberSchema) }), ...errorResponses },
}

export const setMemberSchema = {
  tags: ['members'],
  summary: '新增成員或變更角色（owner）',
  params: MemberParams,
  body: Type.Object({ role: AssignableRole }, { additionalProperties: false }),
  response: {
    200: Type.Object({ workspaceId: Type.String(), userId: Type.String(), role: Role, joinedAt: DateTime }),
    ...errorResponses,
  },
}

export const revokeMemberSchema = {
  tags: ['members'],
  summary: '撤銷成員（owner）',
  params: MemberParams,
  response: { 204: Type.Null(), ...errorResponses },
}
