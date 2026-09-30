import { json, type Db } from '../db/client.ts'
import type { JsonObject } from '../models/canvas.ts'
import type { CommittedOperation } from '../models/operation.ts'

export type NewOperation = Omit<CommittedOperation, 'committedAt' | 'payload'> & { payload: JsonObject }

/** append-only operation log */
export class OperationRepository {
  constructor(private readonly db: Db) {}

  async append(op: NewOperation): Promise<CommittedOperation> {
    const [row] = await this.db<CommittedOperation[]>`
      INSERT INTO workspace_operations (
        workspace_id, server_seq, operation_id, transaction_id, actor_id, client_id, client_seq,
        base_version, workspace_version, operation_type, payload, schema_version
      ) VALUES (
        ${op.workspaceId}, ${op.serverSeq}, ${op.operationId}, ${op.transactionId}, ${op.actorId},
        ${op.clientId}, ${op.clientSeq}, ${op.baseVersion}, ${op.workspaceVersion}, ${op.operationType},
        ${json(this.db, op.payload)}, ${op.schemaVersion}
      )
      RETURNING *
    `
    return row!
  }

  async findByOperationIds(workspaceId: string, actorId: string, operationIds: string[]): Promise<CommittedOperation[]> {
    return this.db<CommittedOperation[]>`
      SELECT * FROM workspace_operations
      WHERE workspace_id = ${workspaceId} AND actor_id = ${actorId} AND operation_id = ANY(${operationIds}::uuid[])
      ORDER BY server_seq
    `
  }

  async listAfterSeq(workspaceId: string, afterSeq: number, limit: number): Promise<CommittedOperation[]> {
    return this.db<CommittedOperation[]>`
      SELECT * FROM workspace_operations
      WHERE workspace_id = ${workspaceId} AND server_seq > ${afterSeq}
      ORDER BY server_seq
      LIMIT ${limit}
    `
  }

  async listAfterVersion(workspaceId: string, afterVersion: number, limit: number): Promise<CommittedOperation[]> {
    return this.db<CommittedOperation[]>`
      SELECT * FROM workspace_operations
      WHERE workspace_id = ${workspaceId} AND workspace_version > ${afterVersion}
      ORDER BY server_seq
      LIMIT ${limit}
    `
  }

  async count(): Promise<number> {
    const [row] = await this.db<{ count: number }[]>`SELECT count(*)::int AS count FROM workspace_operations`
    return row!.count
  }
}
