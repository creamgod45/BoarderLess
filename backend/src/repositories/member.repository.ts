import type { Db } from '../db/client.ts'
import type { WorkspaceMember, WorkspaceRole } from '../models/workspace.ts'

export interface MemberWithProfile extends WorkspaceMember {
  displayName: string
}

export class MemberRepository {
  constructor(private readonly db: Db) {}

  async findActive(workspaceId: string, userId: string): Promise<WorkspaceMember | null> {
    const [row] = await this.db<WorkspaceMember[]>`
      SELECT * FROM workspace_members
      WHERE workspace_id = ${workspaceId} AND user_id = ${userId} AND revoked_at IS NULL
    `
    return row ?? null
  }

  async listActive(workspaceId: string): Promise<MemberWithProfile[]> {
    return this.db<MemberWithProfile[]>`
      SELECT m.*, u.display_name
      FROM workspace_members m
      JOIN users u ON u.id = m.user_id
      WHERE m.workspace_id = ${workspaceId} AND m.revoked_at IS NULL
      ORDER BY m.joined_at
    `
  }

  /** 新增或恢復成員並設定角色 */
  async upsert(workspaceId: string, userId: string, role: WorkspaceRole): Promise<WorkspaceMember> {
    const [row] = await this.db<WorkspaceMember[]>`
      INSERT INTO workspace_members (workspace_id, user_id, role)
      VALUES (${workspaceId}, ${userId}, ${role})
      ON CONFLICT (workspace_id, user_id) DO UPDATE
      SET role = EXCLUDED.role,
          revoked_at = NULL,
          joined_at = CASE WHEN workspace_members.revoked_at IS NULL THEN workspace_members.joined_at ELSE now() END
      RETURNING *
    `
    return row!
  }

  async revoke(workspaceId: string, userId: string): Promise<boolean> {
    const result = await this.db`
      UPDATE workspace_members SET revoked_at = now()
      WHERE workspace_id = ${workspaceId} AND user_id = ${userId} AND revoked_at IS NULL
    `
    return result.count > 0
  }
}
