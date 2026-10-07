import { json, type Db } from '../db/client.ts'
import type { SnapshotContent, WorkspaceSnapshot } from '../models/snapshot.ts'

export class SnapshotRepository {
  constructor(private readonly db: Db) {}

  /** 同一 serverSeq 已有 snapshot 時回傳既有資料（冪等） */
  async insert(
    workspaceId: string,
    throughServerSeq: number,
    schemaVersion: number,
    content: SnapshotContent,
    checksum: string,
  ): Promise<WorkspaceSnapshot> {
    const [row] = await this.db<WorkspaceSnapshot[]>`
      INSERT INTO workspace_snapshots (workspace_id, through_server_seq, schema_version, snapshot, checksum)
      VALUES (${workspaceId}, ${throughServerSeq}, ${schemaVersion}, ${json(this.db, content)}, ${checksum})
      ON CONFLICT (workspace_id, through_server_seq) DO UPDATE SET workspace_id = EXCLUDED.workspace_id
      RETURNING *
    `
    return row!
  }

  async findLatest(workspaceId: string): Promise<WorkspaceSnapshot | null> {
    const [row] = await this.db<WorkspaceSnapshot[]>`
      SELECT * FROM workspace_snapshots
      WHERE workspace_id = ${workspaceId}
      ORDER BY through_server_seq DESC
      LIMIT 1
    `
    return row ?? null
  }
}
