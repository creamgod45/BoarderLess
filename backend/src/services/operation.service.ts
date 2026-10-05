import {
  touchedObjectIds,
  validateOperationPayload,
  type PayloadValidationError,
  type TypedOperation,
} from '../domain/operations.ts'
import { mediaKindOf } from '../models/asset.ts'
import type { JsonObject } from '../models/canvas.ts'
import type { CommittedOperation } from '../models/operation.ts'
import type { Database, Repositories } from '../repositories/index.ts'
import { unprocessable } from '../utils/errors.ts'
import { requireWorkspaceAccess } from './access.service.ts'

export const PROTOCOL_VERSION = 1
export const OPERATION_SCHEMA_VERSION = 1
const MAX_CATCH_UP = 1000

export interface SubmitOperationInput {
  operationId: string
  clientSeq: number
  kind: string
  expectedObjectVersions?: Record<string, number>
  payload: unknown
}

export interface SubmitOperationsInput {
  protocolVersion: number
  workspaceId: string
  clientId: string
  transactionId: string
  baseVersion: number
  operations: SubmitOperationInput[]
}

export interface CommittedResult {
  status: 'accepted' | 'duplicate'
  fromServerSeq: number
  toServerSeq: number
  workspaceVersion: number
  operations: CommittedOperation[]
}

export interface ConflictResult {
  status: 'conflict'
  conflicts: VersionConflict[]
  workspaceVersion: number
  lastServerSeq: number
  /** baseVersion 之後的正式 operations，供 client rebase */
  missingOperations: CommittedOperation[]
}

export type SubmitResult = CommittedResult | ConflictResult

export interface VersionConflict {
  id: string
  expectedVersion: number
  /** null 表示物件不存在 */
  actualVersion: number | null
}

/** 在 transaction 內偵測到版本衝突時丟出，使整個 transaction rollback */
class VersionConflictSignal extends Error {
  constructor(readonly conflicts: VersionConflict[]) {
    super('version conflict')
  }
}

const rejected = (code: string, message: string, details?: unknown) => unprocessable(code, message, details)

export class OperationService {
  constructor(private readonly db: Database) {}

  async submit(actorId: string, input: SubmitOperationsInput): Promise<SubmitResult> {
    if (input.protocolVersion !== PROTOCOL_VERSION) {
      throw rejected('unsupported_protocol_version', `Supported protocolVersion is ${PROTOCOL_VERSION}`)
    }
    const operations = this.validate(input.operations)

    try {
      return await this.db.transaction((repos) => this.commit(repos, actorId, input, operations))
    } catch (error) {
      if (!(error instanceof VersionConflictSignal)) throw error
      return this.buildConflict(input.workspaceId, input.baseVersion, error.conflicts)
    }
  }

  /** client 斷線補資料：依 serverSeq 追回（§8.2） */
  async listAfter(workspaceId: string, userId: string, afterSeq: number, limit: number) {
    const { workspace } = await requireWorkspaceAccess(this.db.repos, workspaceId, userId, 'workspace.read')
    const operations = await this.db.repos.operations.listAfterSeq(workspaceId, afterSeq, Math.min(limit, MAX_CATCH_UP))
    const last = operations.at(-1)?.serverSeq ?? afterSeq
    return {
      operations,
      lastServerSeq: workspace.lastServerSeq,
      hasMore: last < workspace.lastServerSeq,
    }
  }

  private validate(inputs: SubmitOperationInput[]): { input: SubmitOperationInput; op: TypedOperation }[] {
    const errors: (PayloadValidationError & { index: number })[] = []
    const seen = new Set<string>()
    const result: { input: SubmitOperationInput; op: TypedOperation }[] = []

    inputs.forEach((input, index) => {
      if (seen.has(input.operationId)) {
        errors.push({ index, path: '/operationId', message: 'duplicate operationId in transaction' })
      }
      seen.add(input.operationId)
      const validation = validateOperationPayload(input.kind, input.payload)
      if (validation.ok) result.push({ input, op: validation.operation })
      else errors.push(...validation.errors.map((e) => ({ index, ...e })))
    })

    if (errors.length > 0) throw rejected('invalid_operation', 'One or more operations are invalid', errors)
    return result
  }

  private async commit(
    repos: Repositories,
    actorId: string,
    input: SubmitOperationsInput,
    operations: { input: SubmitOperationInput; op: TypedOperation }[],
  ): Promise<CommittedResult> {
    // 1–2. transaction + Workspace row lock；在鎖內重新檢查權限
    const { workspace } = await requireWorkspaceAccess(repos, input.workspaceId, actorId, 'operations.submit', {
      lock: true,
    })

    // 3. idempotency：依 operation ID（不是連線 ID）去重
    const operationIds = operations.map((o) => o.input.operationId)
    const existing = await repos.operations.findByOperationIds(workspace.id, actorId, operationIds)
    if (existing.length === operations.length) return toResult('duplicate', existing)
    if (existing.length > 0) {
      throw rejected('partial_duplicate', 'Some operations in this transaction were already committed', {
        committedOperationIds: existing.map((o) => o.operationId),
      })
    }
    if (input.baseVersion > workspace.currentVersion) {
      throw rejected('invalid_base_version', 'baseVersion is ahead of the server', {
        workspaceVersion: workspace.currentVersion,
      })
    }

    // 4–5. 配發 serverSeq、更新 projection、寫入 operation log 與 outbox
    const workspaceVersion = workspace.currentVersion + 1
    let serverSeq = workspace.lastServerSeq
    const committed: CommittedOperation[] = []

    for (const { input: opInput, op } of operations) {
      await checkExpectedVersions(repos, workspace.id, op, opInput.expectedObjectVersions)
      const storedPayload = await applyOperation(repos, workspace.id, actorId, op)
      serverSeq += 1
      committed.push(
        await repos.operations.append({
          workspaceId: workspace.id,
          serverSeq,
          operationId: opInput.operationId,
          transactionId: input.transactionId,
          actorId,
          clientId: input.clientId,
          clientSeq: opInput.clientSeq,
          baseVersion: input.baseVersion,
          workspaceVersion,
          operationType: op.kind,
          payload: storedPayload,
          schemaVersion: OPERATION_SCHEMA_VERSION,
        }),
      )
    }

    await repos.workspaces.advance(workspace.id, workspaceVersion, serverSeq)
    const result = toResult('accepted', committed)
    await repos.outbox.enqueue(workspace.id, serverSeq, 'operations_committed', {
      transactionId: input.transactionId,
      actorId,
      clientId: input.clientId,
      fromServerSeq: result.fromServerSeq,
      toServerSeq: result.toServerSeq,
      workspaceVersion,
    })
    // 6. commit 由 Database.transaction 完成後才回覆 accepted
    return result
  }

  private async buildConflict(
    workspaceId: string,
    baseVersion: number,
    conflicts: VersionConflict[],
  ): Promise<ConflictResult> {
    return this.db.readConsistent(async (repos) => {
      const workspace = await repos.workspaces.findById(workspaceId)
      const missingOperations = await repos.operations.listAfterVersion(workspaceId, baseVersion, MAX_CATCH_UP)
      return {
        status: 'conflict',
        conflicts,
        workspaceVersion: workspace?.currentVersion ?? 0,
        lastServerSeq: workspace?.lastServerSeq ?? 0,
        missingOperations,
      }
    })
  }
}

function toResult(status: CommittedResult['status'], operations: CommittedOperation[]): CommittedResult {
  return {
    status,
    fromServerSeq: operations[0]!.serverSeq,
    toServerSeq: operations.at(-1)!.serverSeq,
    workspaceVersion: operations.at(-1)!.workspaceVersion,
    operations,
  }
}

/** 以 expectedObjectVersions 偵測同物件的並行修改；不靜默 last-write-wins（§6.4） */
async function checkExpectedVersions(
  repos: Repositories,
  workspaceId: string,
  op: TypedOperation,
  expected: Record<string, number> | undefined,
): Promise<void> {
  if (!expected || Object.keys(expected).length === 0) return
  const touched = new Set(touchedObjectIds(op))
  const unknownKeys = Object.keys(expected).filter((id) => !touched.has(id))
  if (unknownKeys.length > 0) {
    throw rejected('invalid_expected_versions', 'expectedObjectVersions references ids not touched by the operation', {
      ids: unknownKeys,
    })
  }

  const ids = Object.keys(expected)
  const isRelationOp = op.kind === 'update_relation' || op.kind === 'delete_relations'
  const actual = new Map<string, number>()
  if (isRelationOp) {
    for (const r of await repos.canvas.findRelations(workspaceId, ids)) {
      if (!r.deletedAt) actual.set(r.relationId, r.relationVersion)
    }
  } else {
    for (const o of await repos.canvas.findObjects(workspaceId, ids)) {
      if (!o.deletedAt) actual.set(o.objectId, o.objectVersion)
    }
  }

  const conflicts = ids
    .filter((id) => actual.get(id) !== expected[id])
    .map((id) => ({ id, expectedVersion: expected[id]!, actualVersion: actual.get(id) ?? null }))
  if (conflicts.length > 0) throw new VersionConflictSignal(conflicts)
}

/**
 * 套用 operation 到目前 projection，並回傳要寫入 operation log 的正式 payload。
 * 任何 invariant 不成立都丟出 rejected，整個 transaction rollback。
 */
async function applyOperation(
  repos: Repositories,
  workspaceId: string,
  actorId: string,
  op: TypedOperation,
): Promise<JsonObject> {
  const { canvas } = repos

  switch (op.kind) {
    case 'create_object': {
      const p = op.payload
      const [existing] = await canvas.findObjects(workspaceId, [p.objectId])
      if (existing) throw rejected('object_already_exists', `Object ${p.objectId} already exists`)
      if (p.parentId) await requireActiveObjects(repos, workspaceId, [p.parentId])
      if (p.objectType === MEDIA_OBJECT_TYPE) await validateMediaReferences(repos, workspaceId, p.properties ?? {})
      await canvas.insertObject(workspaceId, actorId, {
        objectId: p.objectId,
        objectType: p.objectType,
        parentId: p.parentId ?? null,
        zIndex: p.zIndex ?? 0,
        locked: p.locked ?? false,
        transform: p.transform ?? {},
        properties: p.properties ?? {},
      })
      return p
    }

    case 'update_object': {
      const p = op.payload
      const [obj] = await requireActiveObjects(repos, workspaceId, [p.objectId])
      const onlyTogglesLock = Object.keys(p).every((k) => k === 'objectId' || k === 'locked')
      if (obj!.locked && !onlyTogglesLock) throw rejected('object_locked', `Object ${p.objectId} is locked`)
      if (p.parentId) {
        if (p.parentId === p.objectId) throw rejected('invalid_parent', 'An object cannot be its own parent')
        await requireActiveObjects(repos, workspaceId, [p.parentId])
      }
      if (obj!.objectType === MEDIA_OBJECT_TYPE && p.properties && MEDIA_KEYS.some((k) => k in p.properties!)) {
        await validateMediaReferences(repos, workspaceId, { ...obj!.properties, ...p.properties })
      }
      await canvas.updateObject(workspaceId, p.objectId, actorId, p)
      return p
    }

    case 'move_objects': {
      const p = op.payload
      const objects = await requireActiveObjects(
        repos,
        workspaceId,
        p.moves.map((m) => m.objectId),
      )
      const locked = objects.filter((o) => o.locked).map((o) => o.objectId)
      if (locked.length > 0) throw rejected('object_locked', 'Some objects are locked', { objectIds: locked })
      for (const move of p.moves) {
        await canvas.updateObject(workspaceId, move.objectId, actorId, { transform: move.transform })
      }
      return p
    }

    case 'delete_objects': {
      const p = op.payload
      const objects = await requireActiveObjects(repos, workspaceId, p.objectIds)
      const locked = objects.filter((o) => o.locked).map((o) => o.objectId)
      if (locked.length > 0) throw rejected('object_locked', 'Some objects are locked', { objectIds: locked })
      // 同一 transaction 處理連線，並記錄在正式 payload 以便 replay / inverse
      const cascadedRelationIds = await canvas.softDeleteRelationsTouching(workspaceId, p.objectIds)
      await canvas.softDeleteObjects(workspaceId, p.objectIds, actorId)
      return { ...p, cascadedRelationIds }
    }

    case 'create_relation': {
      const p = op.payload
      if (p.sourceObjectId === p.targetObjectId) {
        throw rejected('invalid_relation', 'Relation source and target must differ')
      }
      const [existing] = await canvas.findRelations(workspaceId, [p.relationId])
      if (existing) throw rejected('relation_already_exists', `Relation ${p.relationId} already exists`)
      await requireActiveObjects(repos, workspaceId, [p.sourceObjectId, p.targetObjectId])
      await canvas.insertRelation(workspaceId, {
        relationId: p.relationId,
        sourceObjectId: p.sourceObjectId,
        targetObjectId: p.targetObjectId,
        direction: p.direction,
        intent: p.intent ?? null,
        label: p.label ?? null,
        style: p.style ?? {},
      })
      return p
    }

    case 'update_relation': {
      const p = op.payload
      await requireActiveRelations(repos, workspaceId, [p.relationId])
      await canvas.updateRelation(workspaceId, p.relationId, p)
      return p
    }

    case 'delete_relations': {
      const p = op.payload
      await requireActiveRelations(repos, workspaceId, p.relationIds)
      await canvas.softDeleteRelations(workspaceId, p.relationIds)
      return p
    }
  }
}

const MEDIA_OBJECT_TYPE = 'media'
const MEDIA_KEYS = ['assetId', 'mediaKind', 'thumbnailAssetId']
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

/**
 * MediaNode 只能引用同一 Workspace 的 asset（BACKEND_MEDIA_API_SPEC §4.4、§7）。
 * 不要求 ready：undo / 還原既有 Node 時，引用的素材可能已轉為 missing。
 */
async function validateMediaReferences(repos: Repositories, workspaceId: string, properties: JsonObject) {
  const { assetId, mediaKind, thumbnailAssetId } = properties
  if (typeof assetId !== 'string' || !UUID_RE.test(assetId)) {
    throw rejected('invalid_media_reference', 'Media nodes require properties.assetId (UUID)')
  }
  if (thumbnailAssetId != null && (typeof thumbnailAssetId !== 'string' || !UUID_RE.test(thumbnailAssetId))) {
    throw rejected('invalid_media_reference', 'properties.thumbnailAssetId must be a UUID')
  }
  const ids = thumbnailAssetId ? [assetId, thumbnailAssetId as string] : [assetId]
  const assets = await repos.assets.findVisibleMany(workspaceId, ids)
  const asset = assets.find((a) => a.id === assetId)
  const missing = ids.filter((id) => !assets.some((a) => a.id === id))
  if (!asset || missing.length > 0) {
    throw rejected('asset_not_found', 'Referenced assets do not exist in this workspace', { assetIds: missing })
  }
  if (mediaKind !== mediaKindOf(asset.mediaType)) {
    throw rejected('media_kind_mismatch', `mediaKind must be "${mediaKindOf(asset.mediaType)}" for ${asset.mediaType}`)
  }
}

async function requireActiveObjects(repos: Repositories, workspaceId: string, ids: string[]) {
  const unique = [...new Set(ids)]
  const found = (await repos.canvas.findObjects(workspaceId, unique)).filter((o) => !o.deletedAt)
  const foundIds = new Set(found.map((o) => o.objectId))
  const missing = unique.filter((id) => !foundIds.has(id))
  if (missing.length > 0) throw rejected('object_not_found', 'Some objects do not exist', { objectIds: missing })
  return found
}

async function requireActiveRelations(repos: Repositories, workspaceId: string, ids: string[]) {
  const unique = [...new Set(ids)]
  const found = (await repos.canvas.findRelations(workspaceId, unique)).filter((r) => !r.deletedAt)
  const foundIds = new Set(found.map((r) => r.relationId))
  const missing = unique.filter((id) => !foundIds.has(id))
  if (missing.length > 0) throw rejected('relation_not_found', 'Some relations do not exist', { relationIds: missing })
  return found
}
