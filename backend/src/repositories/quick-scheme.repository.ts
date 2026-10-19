import { json, type Db } from '../db/client.ts'
import type { JsonObject } from '../models/canvas.ts'
import type { AssetBinding, QuickScheme, QuickSchemeReceipt } from '../models/quick-scheme.ts'

export interface NewQuickScheme {
  id: string
  ownerId: string
  name: string
  schemaVersion: number
  payload: JsonObject
  assetBindings: AssetBinding[]
  revision: number
  etag: string
  collectionRevision: number
}

export class QuickSchemeRepository {
  constructor(private readonly db: Db) {}

  /** 取得（必要時建立）使用者的 collection row lock；同一使用者的 mutation 由此序列化。須在 transaction 內呼叫。 */
  async lockCollection(ownerId: string): Promise<{ collectionRevision: number; activeCount: number }> {
    await this.db`INSERT INTO quick_scheme_collections (owner_id) VALUES (${ownerId}) ON CONFLICT DO NOTHING`
    const [row] = await this.db<{ collectionRevision: number; activeCount: number }[]>`
      SELECT collection_revision, active_count FROM quick_scheme_collections WHERE owner_id = ${ownerId} FOR UPDATE
    `
    return row!
  }

  async advanceCollection(ownerId: string, activeDelta: number): Promise<number> {
    const [row] = await this.db<{ collectionRevision: number }[]>`
      UPDATE quick_scheme_collections
      SET collection_revision = collection_revision + 1, active_count = active_count + ${activeDelta}
      WHERE owner_id = ${ownerId}
      RETURNING collection_revision
    `
    return row!.collectionRevision
  }

  async collectionRevision(ownerId: string): Promise<number> {
    const [row] = await this.db<{ collectionRevision: number }[]>`
      SELECT collection_revision FROM quick_scheme_collections WHERE owner_id = ${ownerId}
    `
    return row?.collectionRevision ?? 0
  }

  /** 任何 owner、含 tombstone（呼叫端負責 owner 檢查，陌生 owner 一律 404） */
  async findById(id: string): Promise<QuickScheme | null> {
    const [row] = await this.db<QuickScheme[]>`SELECT * FROM quick_schemes WHERE id = ${id}`
    return row ?? null
  }

  async insert(s: NewQuickScheme): Promise<QuickScheme> {
    const [row] = await this.db<QuickScheme[]>`
      INSERT INTO quick_schemes (
        id, owner_id, resource_version, name, schema_version, payload, asset_bindings, revision, etag, collection_revision
      ) VALUES (
        ${s.id}, ${s.ownerId}, 2, ${s.name}, ${s.schemaVersion}, ${json(this.db, s.payload)}, ${json(this.db, s.assetBindings)},
        ${s.revision}, ${s.etag}, ${s.collectionRevision}
      )
      RETURNING *
    `
    return row!
  }

  async update(s: NewQuickScheme): Promise<QuickScheme> {
    const [row] = await this.db<QuickScheme[]>`
      UPDATE quick_schemes SET
        name = ${s.name}, schema_version = ${s.schemaVersion}, payload = ${json(this.db, s.payload)},
        asset_bindings = ${json(this.db, s.assetBindings)}, revision = ${s.revision}, etag = ${s.etag},
        collection_revision = ${s.collectionRevision}, updated_at = now()
      WHERE id = ${s.id} AND deleted_at IS NULL
      RETURNING *
    `
    return row!
  }

  async tombstone(id: string, collectionRevision: number, etag: string): Promise<void> {
    await this.db`
      UPDATE quick_schemes SET deleted_at = now(), updated_at = now(), revision = revision + 1, etag = ${etag},
        collection_revision = ${collectionRevision}
      WHERE id = ${id} AND deleted_at IS NULL
    `
  }

  /** keyset 分頁：依 id 排序；呼叫端以 collectionRevision 判斷快照是否仍有效 */
  async listPage(ownerId: string, afterId: string | null, limit: number): Promise<QuickScheme[]> {
    return afterId
      ? this.db<QuickScheme[]>`
          SELECT * FROM quick_schemes WHERE owner_id = ${ownerId} AND deleted_at IS NULL AND id > ${afterId}
          ORDER BY id LIMIT ${limit}
        `
      : this.db<QuickScheme[]>`
          SELECT * FROM quick_schemes WHERE owner_id = ${ownerId} AND deleted_at IS NULL ORDER BY id LIMIT ${limit}
        `
  }

  // ---- receipts ----

  async findReceipt(ownerId: string, key: string): Promise<QuickSchemeReceipt | null> {
    const [row] = await this.db<QuickSchemeReceipt[]>`
      SELECT * FROM quick_scheme_receipts WHERE owner_id = ${ownerId} AND idempotency_key = ${key} AND expires_at > now()
    `
    return row ?? null
  }

  async insertReceipt(r: Omit<QuickSchemeReceipt, 'createdAt'>): Promise<void> {
    await this.db`
      INSERT INTO quick_scheme_receipts (
        owner_id, idempotency_key, method, scheme_id, fingerprint, response_status, response_body, response_etag, expires_at
      ) VALUES (
        ${r.ownerId}, ${r.idempotencyKey}, ${r.method}, ${r.schemeId}, ${r.fingerprint}, ${r.responseStatus},
        ${r.responseBody === null ? null : json(this.db, r.responseBody)}, ${r.responseEtag}, ${r.expiresAt}
      )
      ON CONFLICT (owner_id, idempotency_key) DO UPDATE SET
        method = EXCLUDED.method, scheme_id = EXCLUDED.scheme_id, fingerprint = EXCLUDED.fingerprint,
        response_status = EXCLUDED.response_status, response_body = EXCLUDED.response_body,
        response_etag = EXCLUDED.response_etag, created_at = now(), expires_at = EXCLUDED.expires_at
    `
  }

  async purgeExpiredReceipts(ownerId: string): Promise<void> {
    await this.db`DELETE FROM quick_scheme_receipts WHERE owner_id = ${ownerId} AND expires_at <= now()`
  }

  // ---- asset pins ----

  async replacePins(schemeId: string, bindings: AssetBinding[]): Promise<void> {
    await this.db`DELETE FROM quick_scheme_asset_pins WHERE scheme_id = ${schemeId}`
    for (const b of bindings) {
      await this.db`
        INSERT INTO quick_scheme_asset_pins (scheme_id, asset_id, source_workspace_id, kind)
        VALUES (${schemeId}, ${b.assetId}, ${b.sourceWorkspaceId}, ${b.kind})
      `
    }
  }
}
