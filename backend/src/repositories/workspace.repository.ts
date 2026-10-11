import { json, type Db } from '../db/client.ts'
import type { CanvasStyle } from '../domain/canvas-style.ts'
import type { Workspace, WorkspaceWithRole } from '../models/workspace.ts'

export class WorkspaceRepository {
  constructor(private readonly db: Db) {}

  async create(ownerId: string, title: string): Promise<Workspace> {
    const [row] = await this.db<Workspace[]>`
      INSERT INTO workspaces (owner_id, title) VALUES (${ownerId}, ${title}) RETURNING *
    `
    return row!
  }

  async findById(id: string): Promise<Workspace | null> {
    const [row] = await this.db<Workspace[]>`SELECT * FROM workspaces WHERE id = ${id} AND deleted_at IS NULL`
    return row ?? null
  }

  /** 取得 Workspace row lock，序列化同一 Workspace 的正式寫入（§4.3）。須在 transaction 內呼叫。 */
  async lockForUpdate(id: string): Promise<Workspace | null> {
    const [row] = await this.db<Workspace[]>`
      SELECT * FROM workspaces WHERE id = ${id} AND deleted_at IS NULL FOR UPDATE
    `
    return row ?? null
  }

  async listForUser(userId: string): Promise<WorkspaceWithRole[]> {
    return this.db<WorkspaceWithRole[]>`
      SELECT w.*, m.role
      FROM workspaces w
      JOIN workspace_members m ON m.workspace_id = w.id
      WHERE m.user_id = ${userId} AND m.revoked_at IS NULL AND w.deleted_at IS NULL
      ORDER BY w.updated_at DESC
    `
  }

  async updateTitle(id: string, title: string): Promise<Workspace | null> {
    const [row] = await this.db<Workspace[]>`
      UPDATE workspaces SET title = ${title}, updated_at = now()
      WHERE id = ${id} AND deleted_at IS NULL
      RETURNING *
    `
    return row ?? null
  }

  async advance(id: string, version: number, lastServerSeq: number): Promise<void> {
    await this.db`
      UPDATE workspaces
      SET current_version = ${version}, last_server_seq = ${lastServerSeq}, updated_at = now()
      WHERE id = ${id}
    `
  }

  async updateCanvasStyle(id: string, style: CanvasStyle, version: number): Promise<void> {
    await this.db`
      UPDATE workspaces SET canvas_style = ${json(this.db, style)}, canvas_style_version = ${version}, updated_at = now()
      WHERE id = ${id}
    `
  }

  async updateSequenceDiagrams(id: string, diagrams: unknown[], version: number): Promise<void> {
    await this.db`
      UPDATE workspaces SET sequence_diagrams = ${json(this.db, diagrams)}, sequence_diagrams_version = ${version}, updated_at = now()
      WHERE id = ${id}
    `
  }

  /** metadata（title / membership / 角色）變更：revision +1；含已 soft delete 的 Workspace */
  async bumpMetadataRevision(id: string): Promise<{ metadataRevision: number; lastServerSeq: number }> {
    const [row] = await this.db<{ metadataRevision: number; lastServerSeq: number }[]>`
      UPDATE workspaces SET metadata_revision = metadata_revision + 1 WHERE id = ${id}
      RETURNING metadata_revision, last_server_seq
    `
    return row!
  }

  async softDelete(id: string): Promise<boolean> {
    const result = await this.db`
      UPDATE workspaces SET deleted_at = now(), updated_at = now() WHERE id = ${id} AND deleted_at IS NULL
    `
    return result.count > 0
  }

  async count(): Promise<number> {
    const [row] = await this.db<{ count: number }[]>`
      SELECT count(*)::int AS count FROM workspaces WHERE deleted_at IS NULL
    `
    return row!.count
  }
}
