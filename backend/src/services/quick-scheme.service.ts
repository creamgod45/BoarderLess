import { createHash, randomUUID } from 'node:crypto'
import { Type } from 'typebox'
import { Compile } from 'typebox/compile'
import {
  SELECTION_LIMITS,
  SELECTION_SCHEMA_VERSIONS,
  selectionAssetReferences,
  validateSelectionPayload,
  type SelectionPayload,
} from '../domain/selection.ts'
import { mediaKindOf } from '../models/asset.ts'
import type { JsonObject } from '../models/canvas.ts'
import {
  QUICK_SCHEME_RESOURCE_VERSION,
  type AssetBinding,
  type BindingStatus,
  type PublicQuickScheme,
  type QuickScheme,
} from '../models/quick-scheme.ts'
import type { Database, Repositories } from '../repositories/index.ts'
import { AppError } from '../utils/errors.ts'

export const QUICK_SCHEME_LIMITS = {
  maxRequestBytes: 1_048_576,
  maxObjects: SELECTION_LIMITS.maxObjects,
  maxRelations: SELECTION_LIMITS.maxRelations,
  maxSequenceDiagrams: SELECTION_LIMITS.maxSequenceDiagrams,
  maxSchemesPerUser: 1000,
  nameMaxScalars: 80,
  pageLimitMax: 100,
  pageLimitDefault: 50,
  maxPageBytes: 4 * 1_048_576,
} as const
export const IDEMPOTENCY_TTL_SECONDS = 30 * 24 * 3600
export const CURSOR_TTL_SECONDS = 600

const err = (status: number, code: string, message: string, details?: unknown) => new AppError(status, code, message, details)
const LOWER_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/

const BodySchema = Type.Object(
  {
    resourceVersion: Type.Integer(),
    name: Type.String(),
    schemaVersion: Type.Integer(),
    payload: Type.Record(Type.String(), Type.Unknown()),
    assetBindings: Type.Array(
      Type.Object(
        {
          assetId: Type.String({ pattern: LOWER_UUID.source }),
          sourceWorkspaceId: Type.String({ pattern: LOWER_UUID.source }),
          kind: Type.Union([Type.Literal('original'), Type.Literal('thumbnail')]),
        },
        { additionalProperties: false },
      ),
      { maxItems: 2 * SELECTION_LIMITS.maxObjects },
    ),
  },
  { additionalProperties: false },
)
const bodyValidator = Compile(BodySchema)

export interface MutationHeaders {
  ifMatch?: string
  ifNoneMatch?: string
  idempotencyKey?: string
}

/** 回應：status、body、ETag；replay 時與原回應完全相同 */
export interface MutationResult {
  status: 200 | 201 | 204
  body: PublicQuickScheme | null
  etag: string | null
  replayed: boolean
}

/** 遞迴排序 key 的 JSON（fingerprint 用；與 key 順序無關） */
function stableStringify(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((k) => `${JSON.stringify(k)}:${stableStringify((value as Record<string, unknown>)[k])}`)
      .join(',')}}`
  }
  return JSON.stringify(value)
}
const sha256 = (s: string) => createHash('sha256').update(s, 'utf8').digest('base64url')
const newEtag = () => `"${sha256(randomUUID()).slice(0, 27)}"`
const scalarCount = (s: string) => [...s].length

/** 單一強 ETag（`"..."`）；`*`、weak、多值都不接受 */
function strongEtag(header: string | undefined): string | null {
  if (!header) return null
  const value = header.trim()
  return /^"[^"]*"$/.test(value) ? value : null
}

interface CursorState {
  o: string
  r: number
  a: string | null
  e: number
}
const encodeCursor = (c: CursorState) => Buffer.from(JSON.stringify(c)).toString('base64url')
function decodeCursor(raw: string): CursorState | null {
  try {
    const c = JSON.parse(Buffer.from(raw, 'base64url').toString('utf8')) as CursorState
    return typeof c.o === 'string' && typeof c.r === 'number' && (c.a === null || typeof c.a === 'string') && typeof c.e === 'number' ? c : null
  } catch {
    return null
  }
}

/**
 * 使用者層級快速方案簿（BACKEND_QUICK_SCHEME_API_SPEC、MISSING_IMPLEMENTATION_SPEC §4）。
 * owner 一律來自認證；陌生 owner 的資源回 404。所有 mutation 在 owner 的 collection row lock 內：
 * receipt → precondition → 驗證 → quota → 素材 ACL → 保存資源 / revision / collectionRevision / pin / receipt（同一 transaction）。
 */
export class QuickSchemeService {
  constructor(private readonly db: Database) {}

  capabilities() {
    return {
      resourceVersions: [QUICK_SCHEME_RESOURCE_VERSION],
      selectionSchemaVersions: [...SELECTION_SCHEMA_VERSIONS],
      limits: { ...QUICK_SCHEME_LIMITS },
      idempotencyKeyTtlSeconds: IDEMPOTENCY_TTL_SECONDS,
      cursorTtlSeconds: CURSOR_TTL_SECONDS,
      /** 跨 workspace 素材 materialization 尚未交付：引用其他 workspace 素材的方案不可直接插入 */
      materializationEnabled: false,
    }
  }

  async get(ownerId: string, id: string): Promise<PublicQuickScheme> {
    const scheme = await this.db.repos.quickSchemes.findById(id)
    if (!scheme || scheme.ownerId !== ownerId || scheme.deletedAt) throw err(404, 'not_found', 'Quick scheme not found')
    return this.toPublic(this.db.repos, ownerId, scheme)
  }

  async list(ownerId: string, cursorRaw: string | undefined, limit: number) {
    const repos = this.db.repos
    let afterId: string | null = null
    let snapshot: number
    if (cursorRaw) {
      const cursor = decodeCursor(cursorRaw)
      if (!cursor || cursor.o !== ownerId) throw err(400, 'invalid_cursor', 'Cursor is invalid')
      const current = await repos.quickSchemes.collectionRevision(ownerId)
      // 快照期間 collection 有任何 create / update / delete：明確要求重新列舉，不讓 client 把漏掉的項目當成已刪除
      if (cursor.e < Date.now() || cursor.r !== current) {
        throw err(410, 'cursor_expired', 'The listing snapshot changed or expired; restart from the first page', {
          collectionRevision: current,
        })
      }
      afterId = cursor.a
      snapshot = cursor.r
    } else {
      snapshot = await repos.quickSchemes.collectionRevision(ownerId)
    }
    const rows = await repos.quickSchemes.listPage(ownerId, afterId, limit + 1)
    const items: PublicQuickScheme[] = []
    let bytes = 0
    for (const row of rows.slice(0, limit)) {
      const item = await this.toPublic(repos, ownerId, row)
      const size = Buffer.byteLength(JSON.stringify(item))
      if (items.length > 0 && bytes + size > QUICK_SCHEME_LIMITS.maxPageBytes) break // 頁面總 bytes 上限
      items.push(item)
      bytes += size
    }
    // 讀完頁面後再確認快照仍一致（避免讀取期間的變更混入同一頁）
    const after = await repos.quickSchemes.collectionRevision(ownerId)
    if (after !== snapshot) {
      throw err(410, 'cursor_expired', 'The collection changed during listing; restart from the first page', { collectionRevision: after })
    }
    const more = items.length < rows.length
    return {
      items,
      nextCursor: more ? encodeCursor({ o: ownerId, r: snapshot, a: items.at(-1)!.id, e: Date.now() + CURSOR_TTL_SECONDS * 1000 }) : null,
      collectionRevision: snapshot,
      collectionEtag: this.collectionEtag(ownerId, snapshot),
    }
  }

  async collectionEtagFor(ownerId: string): Promise<string> {
    return this.collectionEtag(ownerId, await this.db.repos.quickSchemes.collectionRevision(ownerId))
  }

  private collectionEtag(ownerId: string, revision: number): string {
    return `"c${sha256(`${ownerId}:${revision}`).slice(0, 22)}"`
  }

  /** PUT：If-None-Match: * 建立；If-Match: <ETag> 完整替換可寫欄位 */
  async put(ownerId: string, id: string, headers: MutationHeaders, body: unknown): Promise<MutationResult> {
    const key = this.requireKey(headers)
    const create = headers.ifNoneMatch !== undefined
    if (create && headers.ifMatch !== undefined) throw err(428, 'precondition_required', 'Send either If-None-Match: * or If-Match, not both')
    if (create && headers.ifNoneMatch!.trim() !== '*') throw err(428, 'precondition_required', 'Create requires If-None-Match: *')
    const ifMatch = create ? null : strongEtag(headers.ifMatch)
    if (!create && !ifMatch) throw err(428, 'precondition_required', 'Update requires If-Match with the latest strong ETag')
    const fingerprint = sha256(stableStringify({ method: 'PUT', id, precondition: create ? '*' : ifMatch, body }))

    return this.db.transaction(async (repos) => {
      const { activeCount } = await repos.quickSchemes.lockCollection(ownerId)
      const replay = await this.replay(repos, ownerId, key, 'PUT', id, fingerprint)
      if (replay) return replay
      const input = this.validateBody(body)

      const existing = await repos.quickSchemes.findById(id)
      if (existing && existing.ownerId !== ownerId) throw err(404, 'not_found', 'Quick scheme not found')
      if (create) {
        if (existing) {
          throw err(412, 'revision_conflict', 'A quick scheme with this id already exists', { exists: true, deleted: Boolean(existing.deletedAt) })
        }
      } else {
        if (!existing) throw err(404, 'not_found', 'Quick scheme not found')
        if (existing.deletedAt) throw err(412, 'revision_conflict', 'The quick scheme was deleted', { deleted: true })
        if (existing.etag !== ifMatch) throw err(412, 'revision_conflict', 'If-Match does not match the latest ETag', { revision: existing.revision })
      }
      if (create && activeCount >= QUICK_SCHEME_LIMITS.maxSchemesPerUser) {
        throw err(409, 'quota_exceeded', `At most ${QUICK_SCHEME_LIMITS.maxSchemesPerUser} quick schemes per user`)
      }
      await this.checkBindings(repos, ownerId, input.payload, input.assetBindings)

      const collectionRevision = await repos.quickSchemes.advanceCollection(ownerId, create ? 1 : 0)
      const row = {
        id,
        ownerId,
        name: input.name,
        schemaVersion: input.schemaVersion,
        payload: input.payload as unknown as JsonObject,
        assetBindings: input.assetBindings,
        revision: create ? 1 : existing!.revision + 1,
        etag: newEtag(),
        collectionRevision,
      }
      const saved = create ? await repos.quickSchemes.insert(row) : await repos.quickSchemes.update(row)
      await repos.quickSchemes.replacePins(id, input.assetBindings)
      const result: MutationResult = {
        status: create ? 201 : 200,
        body: await this.toPublic(repos, ownerId, saved),
        etag: saved.etag,
        replayed: false,
      }
      await this.saveReceipt(repos, ownerId, key, 'PUT', id, fingerprint, result)
      return result
    })
  }

  /** DELETE：If-Match；保留 tombstone，移除自己的 pin，不刪素材 */
  async delete(ownerId: string, id: string, headers: MutationHeaders): Promise<MutationResult> {
    const key = this.requireKey(headers)
    const ifMatch = strongEtag(headers.ifMatch)
    if (!ifMatch) throw err(428, 'precondition_required', 'Delete requires If-Match with the latest strong ETag')
    const fingerprint = sha256(stableStringify({ method: 'DELETE', id, precondition: ifMatch }))

    return this.db.transaction(async (repos) => {
      await repos.quickSchemes.lockCollection(ownerId)
      const replay = await this.replay(repos, ownerId, key, 'DELETE', id, fingerprint)
      if (replay) return replay
      const existing = await repos.quickSchemes.findById(id)
      if (!existing || existing.ownerId !== ownerId) throw err(404, 'not_found', 'Quick scheme not found')
      if (existing.deletedAt) throw err(412, 'revision_conflict', 'The quick scheme was already deleted', { deleted: true })
      if (existing.etag !== ifMatch) throw err(412, 'revision_conflict', 'If-Match does not match the latest ETag', { revision: existing.revision })
      const collectionRevision = await repos.quickSchemes.advanceCollection(ownerId, -1)
      await repos.quickSchemes.tombstone(id, collectionRevision, newEtag())
      await repos.quickSchemes.replacePins(id, [])
      const result: MutationResult = { status: 204, body: null, etag: null, replayed: false }
      await this.saveReceipt(repos, ownerId, key, 'DELETE', id, fingerprint, result)
      return result
    })
  }

  // ---- 內部 ----

  private requireKey(headers: MutationHeaders): string {
    const key = headers.idempotencyKey?.trim()
    if (!key) throw err(428, 'precondition_required', 'Idempotency-Key header is required')
    if (!LOWER_UUID.test(key)) throw err(400, 'invalid_idempotency_key', 'Idempotency-Key must be a lowercase UUID')
    return key
  }

  /** 同 key：相同 fingerprint 回原結果（即使之後 ETag 已改變）；不同 method / 資源 / 內容回 409 */
  private async replay(repos: Repositories, ownerId: string, key: string, method: 'PUT' | 'DELETE', id: string, fingerprint: string): Promise<MutationResult | null> {
    const receipt = await repos.quickSchemes.findReceipt(ownerId, key)
    if (!receipt) return null
    if (receipt.method !== method || receipt.schemeId !== id || receipt.fingerprint !== fingerprint) {
      throw err(409, 'idempotency_mismatch', 'Idempotency-Key was already used for a different request')
    }
    return {
      status: receipt.responseStatus as MutationResult['status'],
      body: receipt.responseBody as unknown as PublicQuickScheme | null,
      etag: receipt.responseEtag,
      replayed: true,
    }
  }

  private async saveReceipt(repos: Repositories, ownerId: string, key: string, method: 'PUT' | 'DELETE', id: string, fingerprint: string, result: MutationResult) {
    await repos.quickSchemes.purgeExpiredReceipts(ownerId)
    await repos.quickSchemes.insertReceipt({
      ownerId,
      idempotencyKey: key,
      method,
      schemeId: id,
      fingerprint,
      responseStatus: result.status,
      responseBody: result.body ? (JSON.parse(JSON.stringify(result.body)) as JsonObject) : null,
      responseEtag: result.etag,
      expiresAt: new Date(Date.now() + IDEMPOTENCY_TTL_SECONDS * 1000),
    })
  }

  private validateBody(body: unknown): { name: string; schemaVersion: number; payload: SelectionPayload; assetBindings: AssetBinding[] } {
    if (!bodyValidator.Check(body)) {
      const errors = [...bodyValidator.Errors(body)].slice(0, 20).map((e) => ({ path: e.instancePath, message: e.message }))
      throw err(422, 'invalid_payload', 'Request body does not match the quick scheme schema', { errors })
    }
    if (body.resourceVersion !== QUICK_SCHEME_RESOURCE_VERSION) {
      throw err(422, 'unsupported_resource_version', `Supported resourceVersion is ${QUICK_SCHEME_RESOURCE_VERSION}`, {
        supported: [QUICK_SCHEME_RESOURCE_VERSION],
      })
    }
    if (!(SELECTION_SCHEMA_VERSIONS as readonly number[]).includes(body.schemaVersion)) {
      throw err(422, 'unsupported_selection_schema', 'Unsupported selection schemaVersion', { supported: [...SELECTION_SCHEMA_VERSIONS] })
    }
    // payload.version 必須明示且與 schemaVersion 一致（不降版、不讓 client 以缺省值繼承）
    if (body.payload.version !== body.schemaVersion) {
      throw err(422, 'unsupported_selection_schema', 'payload.version must be present and equal schemaVersion', { schemaVersion: body.schemaVersion })
    }
    const name = body.name.trim()
    const scalars = scalarCount(name)
    if (scalars < 1 || scalars > QUICK_SCHEME_LIMITS.nameMaxScalars) {
      throw err(422, 'invalid_payload', `name must be 1..${QUICK_SCHEME_LIMITS.nameMaxScalars} Unicode scalar values after trimming`, {
        errors: [{ path: '/name', message: 'length' }],
      })
    }
    const validation = validateSelectionPayload(body.payload)
    if (!validation.ok) {
      throw err(422, 'invalid_payload', 'payload is not a valid boarderless/selection', { issue: validation.issue, errors: validation.errors })
    }
    return { name, schemaVersion: body.schemaVersion, payload: body.payload as unknown as SelectionPayload, assetBindings: body.assetBindings }
  }

  /**
   * assetBindings 必須精確覆蓋 payload 的原檔 / 縮圖引用（無缺、無多、無重複）；
   * 每個來源 workspace 由 authenticated user 重新授權，素材必須 ready、kind 相符、縮圖由原檔 metadata 證明是其 derivative。
   */
  private async checkBindings(repos: Repositories, ownerId: string, payload: SelectionPayload, bindings: AssetBinding[]) {
    const refs = selectionAssetReferences(payload)
    const keyOf = (b: { kind: string; assetId: string }) => `${b.kind}:${b.assetId}`
    const bindingKeys = bindings.map(keyOf)
    const refKeys = new Set(refs.map(keyOf))
    if (
      new Set(bindingKeys).size !== bindingKeys.length ||
      bindingKeys.length !== refKeys.size ||
      !bindingKeys.every((k) => refKeys.has(k)) ||
      !refs.every((r) => LOWER_UUID.test(r.assetId))
    ) {
      throw err(422, 'invalid_asset_binding', 'assetBindings must cover exactly the media references in payload')
    }
    const bySource = new Map<string, AssetBinding[]>()
    for (const b of bindings) bySource.set(b.sourceWorkspaceId, [...(bySource.get(b.sourceWorkspaceId) ?? []), b])
    const assets = new Map<string, Awaited<ReturnType<typeof repos.assets.findVisible>>>()
    for (const [workspaceId, group] of bySource) {
      const workspace = await repos.workspaces.findById(workspaceId)
      const member = workspace ? await repos.members.findActive(workspaceId, ownerId) : null
      if (!member) throw err(403, 'asset_access_denied', 'No read access to a source workspace of the referenced assets')
      for (const b of group) {
        const asset = await repos.assets.findVisible(workspaceId, b.assetId)
        if (!asset || asset.status !== 'ready') {
          throw err(409, 'asset_unavailable', 'A referenced asset is not ready in its source workspace', { assetId: b.assetId, status: asset?.status ?? 'not_found' })
        }
        assets.set(b.assetId, asset)
      }
    }
    for (const media of payload.media ?? []) {
      const original = assets.get(media.assetId)!
      if (mediaKindOf(original.mediaType) !== media.mediaKind || original.sourceAssetId !== null) {
        throw err(422, 'invalid_asset_binding', 'mediaKind does not match the referenced original asset', { assetId: media.assetId })
      }
      if (media.thumbnailAssetId) {
        const thumb = assets.get(media.thumbnailAssetId)!
        const derived = thumb.sourceAssetId === original.id || original.thumbnailAssetId === thumb.id
        if (!derived || mediaKindOf(thumb.mediaType) !== 'image' || thumb.workspaceId !== original.workspaceId) {
          throw err(422, 'invalid_asset_binding', 'thumbnail is not a derivative of the referenced original', { assetId: media.thumbnailAssetId })
        }
      }
    }
  }

  private async toPublic(repos: Repositories, ownerId: string, s: QuickScheme): Promise<PublicQuickScheme> {
    const availability: PublicQuickScheme['assetAvailability'] = []
    const access = new Map<string, boolean>()
    for (const b of s.assetBindings) {
      if (!access.has(b.sourceWorkspaceId)) {
        const workspace = await repos.workspaces.findById(b.sourceWorkspaceId)
        access.set(b.sourceWorkspaceId, Boolean(workspace && (await repos.members.findActive(b.sourceWorkspaceId, ownerId))))
      }
      let status: BindingStatus = 'access_denied'
      if (access.get(b.sourceWorkspaceId)) {
        const asset = await repos.assets.findVisible(b.sourceWorkspaceId, b.assetId)
        status = asset?.status === 'ready' ? 'available' : 'unavailable'
      }
      availability.push({ ...b, status })
    }
    return {
      id: s.id,
      ownerId: s.ownerId,
      resourceVersion: s.resourceVersion,
      name: s.name,
      schemaVersion: s.schemaVersion,
      payload: s.payload,
      assetBindings: s.assetBindings,
      revision: s.revision,
      createdAt: s.createdAt,
      updatedAt: s.updatedAt,
      etag: s.etag,
      assetAvailability: availability,
    }
  }
}
