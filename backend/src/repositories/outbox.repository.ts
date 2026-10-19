import { json, type Db } from '../db/client.ts'
import type { JsonObject } from '../models/canvas.ts'

/** transaction outbox：與 operation 同一 transaction 寫入，之後由 publisher/worker 發布（B2） */
export class OutboxRepository {
  constructor(private readonly db: Db) {}

  async enqueue(workspaceId: string, serverSeq: number, eventType: string, payload: JsonObject): Promise<void> {
    await this.db`
      INSERT INTO transaction_outbox (workspace_id, server_seq, event_type, payload)
      VALUES (${workspaceId}, ${serverSeq}, ${eventType}, ${json(this.db, payload)})
    `
  }

  async countUnpublished(): Promise<number> {
    const [row] = await this.db<{ count: number }[]>`
      SELECT count(*)::int AS count FROM transaction_outbox WHERE published_at IS NULL
    `
    return row!.count
  }
}
