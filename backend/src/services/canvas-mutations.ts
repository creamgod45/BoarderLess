import {
  completeTransform,
  OBJECT_SCHEMA_VERSION,
  OBJECT_TYPES,
  validateObjectState,
  validateRelationStyle,
} from '../domain/canvas-objects.ts'
import {
  RELATION_OPERATION_KINDS,
  RESTORE_OPERATION_KINDS,
  touchedObjectIds,
  type TypedOperation,
} from '../domain/operations.ts'
import { mediaKindOf } from '../models/asset.ts'
import type { CanvasObject, JsonObject } from '../models/canvas.ts'
import type { Repositories } from '../repositories/index.ts'
import { unprocessable } from '../utils/errors.ts'

/**
 * Projection 的 invariant 與套用（在 Workspace row lock transaction 內執行）。
 * 每個 operation 依序套用，後面的檢查看得到同一 transaction 先前的變更。
 */

export interface VersionConflict {
  id: string
  expectedVersion: number
  /** null 表示物件不存在 */
  actualVersion: number | null
}

/** 偵測到版本衝突時丟出，使整個 transaction rollback */
export class VersionConflictSignal extends Error {
  constructor(readonly conflicts: VersionConflict[]) {
    super('version conflict')
  }
}

const rejected = (code: string, message: string, details?: unknown) => unprocessable(code, message, details)

const GROUP = 'group'
const MEDIA = 'media'
const MEDIA_KEYS = ['assetId', 'mediaKind', 'thumbnailAssetId']

/** 以 expectedObjectVersions 偵測同物件的並行修改；不靜默 last-write-wins（§6.4） */
export async function checkExpectedVersions(
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
  // restore 比對 tombstone 版本；其他 operation 只比對 active row
  const wantDeleted = RESTORE_OPERATION_KINDS.has(op.kind)
  const actual = new Map<string, number>()
  if (RELATION_OPERATION_KINDS.has(op.kind)) {
    for (const r of await repos.canvas.findRelations(workspaceId, ids)) {
      if (Boolean(r.deletedAt) === wantDeleted) actual.set(r.relationId, r.relationVersion)
    }
  } else {
    for (const o of await repos.canvas.findObjects(workspaceId, ids)) {
      if (Boolean(o.deletedAt) === wantDeleted) actual.set(o.objectId, o.objectVersion)
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
export async function applyOperation(
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
      if (existing) {
        throw rejected('object_already_exists', `Object ${p.objectId} already exists`, {
          deleted: Boolean(existing.deletedAt),
          ...(existing.deletedAt ? { restoreWith: 'restore_objects' } : {}),
        })
      }
      const transform = completeTransform(p.transform ?? {})
      const properties = p.properties ?? {}
      assertValidState(p.objectType, transform, properties)
      if (p.parentId) await assertValidParent(repos, workspaceId, p.objectId, p.parentId)
      if (p.objectType === MEDIA) await validateMediaReferences(repos, workspaceId, properties)
      await canvas.insertObject(workspaceId, actorId, {
        objectId: p.objectId,
        objectType: p.objectType,
        parentId: p.parentId ?? null,
        zIndex: p.zIndex ?? 0,
        locked: p.locked ?? false,
        transform,
        properties,
      })
      // 正式 payload 記錄補齊後的 transform，replay 不依賴伺服器預設值
      return { ...p, transform }
    }

    case 'update_object': {
      const p = op.payload
      const [obj] = await requireActiveObjects(repos, workspaceId, [p.objectId])
      if (p.objectType !== undefined && p.objectType !== obj!.objectType) {
        throw rejected('unsupported_change', 'Changing objectType is not supported; it requires a dedicated operation', {
          field: 'objectType',
        })
      }
      const onlyTogglesLock = Object.keys(p).every((k) => k === 'objectId' || k === 'locked' || k === 'objectType')
      if (obj!.locked && !onlyTogglesLock) throw rejected('object_locked', `Object ${p.objectId} is locked`)

      const transform = p.transform ? { ...obj!.transform, ...p.transform } : undefined
      const properties = { ...obj!.properties, ...p.properties }
      assertValidState(obj!.objectType, transform ?? obj!.transform, properties)
      if (p.parentId) await assertValidParent(repos, workspaceId, p.objectId, p.parentId)
      if (obj!.objectType === MEDIA && p.properties && MEDIA_KEYS.some((k) => k in p.properties!)) {
        await validateMediaReferences(repos, workspaceId, properties)
      }
      await canvas.updateObject(workspaceId, p.objectId, actorId, {
        parentId: p.parentId,
        zIndex: p.zIndex,
        locked: p.locked,
        transform,
        properties: p.properties,
      })
      return transform ? { ...p, transform } : p
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
      const byId = new Map(objects.map((o) => [o.objectId, o]))
      const moves = p.moves.map((move) => {
        const current = byId.get(move.objectId)!
        const transform = { ...current.transform, ...move.transform }
        assertValidState(current.objectType, transform, current.properties)
        return { objectId: move.objectId, transform }
      })
      for (const move of moves) {
        await canvas.updateObject(workspaceId, move.objectId, actorId, { transform: move.transform })
      }
      return { moves }
    }

    case 'delete_objects': {
      const p = op.payload
      const objects = await requireActiveObjects(repos, workspaceId, p.objectIds)
      const locked = objects.filter((o) => o.locked).map((o) => o.objectId)
      if (locked.length > 0) throw rejected('object_locked', 'Some objects are locked', { objectIds: locked })
      // 不留下 dangling parent：children 必須一起刪除，或先 reparent（與 APP HierarchyCascadeMismatch 一致）
      const deleting = new Set(p.objectIds)
      const orphaned = (await canvas.findActiveChildren(workspaceId, p.objectIds)).filter((c) => !deleting.has(c.objectId))
      if (orphaned.length > 0) {
        throw rejected('hierarchy_cascade_mismatch', 'Deleting a group requires deleting or reparenting its children', {
          objectIds: orphaned.map((c) => c.objectId),
        })
      }
      // 同一 transaction 處理連線，並記錄在正式 payload 以便 replay / restore
      const cascadedRelationIds = await canvas.softDeleteRelationsTouching(workspaceId, p.objectIds)
      await canvas.softDeleteObjects(workspaceId, p.objectIds, actorId)
      return { ...p, cascadedRelationIds }
    }

    case 'restore_objects': {
      const p = op.payload
      const ids = [...new Set(p.objectIds)]
      const found = await canvas.findObjects(workspaceId, ids)
      const missing = ids.filter((id) => !found.some((o) => o.objectId === id))
      if (missing.length > 0) {
        throw rejected('object_not_found', 'Some objects have never existed in this workspace', { objectIds: missing })
      }
      const active = found.filter((o) => !o.deletedAt).map((o) => o.objectId)
      if (active.length > 0) throw rejected('object_not_deleted', 'Some objects are not deleted', { objectIds: active })

      const restored = await canvas.restoreObjects(workspaceId, ids, actorId)
      // 還原後的狀態也必須合法：型別 schema、parent 為 active group 且無循環、媒體引用
      for (const obj of restored) {
        assertValidState(obj.objectType, obj.transform, obj.properties)
        if (obj.parentId) await assertValidParent(repos, workspaceId, obj.objectId, obj.parentId)
        if (obj.objectType === MEDIA) await validateMediaReferences(repos, workspaceId, obj.properties)
      }
      return { ...p, restoredVersions: Object.fromEntries(restored.map((o) => [o.objectId, o.objectVersion])) }
    }

    case 'create_relation': {
      const p = op.payload
      if (p.sourceObjectId === p.targetObjectId) {
        throw rejected('invalid_relation', 'Relation source and target must differ')
      }
      const [existing] = await canvas.findRelations(workspaceId, [p.relationId])
      if (existing) {
        throw rejected('relation_already_exists', `Relation ${p.relationId} already exists`, {
          deleted: Boolean(existing.deletedAt),
          ...(existing.deletedAt ? { restoreWith: 'restore_relations' } : {}),
        })
      }
      assertValidStyle(p.style ?? {})
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
      const [relation] = await requireActiveRelations(repos, workspaceId, [p.relationId])
      const changedEndpoint = (['sourceObjectId', 'targetObjectId'] as const).find(
        (field) => p[field] !== undefined && p[field] !== relation![field],
      )
      if (changedEndpoint) {
        throw rejected('unsupported_change', 'Changing relation endpoints is not supported; it requires a dedicated operation', {
          field: changedEndpoint,
        })
      }
      assertValidStyle({ ...relation!.style, ...p.style })
      await canvas.updateRelation(workspaceId, p.relationId, p)
      return p
    }

    case 'delete_relations': {
      const p = op.payload
      await requireActiveRelations(repos, workspaceId, p.relationIds)
      await canvas.softDeleteRelations(workspaceId, p.relationIds)
      return p
    }

    case 'restore_relations': {
      const p = op.payload
      const ids = [...new Set(p.relationIds)]
      const found = await canvas.findRelations(workspaceId, ids)
      const missing = ids.filter((id) => !found.some((r) => r.relationId === id))
      if (missing.length > 0) {
        throw rejected('relation_not_found', 'Some relations have never existed in this workspace', { relationIds: missing })
      }
      const active = found.filter((r) => !r.deletedAt).map((r) => r.relationId)
      if (active.length > 0) throw rejected('relation_not_deleted', 'Some relations are not deleted', { relationIds: active })

      const endpoints = [...new Set(found.flatMap((r) => [r.sourceObjectId, r.targetObjectId]))]
      const activeEndpoints = new Set(
        (await canvas.findObjects(workspaceId, endpoints)).filter((o) => !o.deletedAt).map((o) => o.objectId),
      )
      const blocked = found.filter((r) => !activeEndpoints.has(r.sourceObjectId) || !activeEndpoints.has(r.targetObjectId))
      if (blocked.length > 0) {
        throw rejected('relation_endpoint_missing', 'Relation endpoints must be active (restore objects first)', {
          relationIds: blocked.map((r) => r.relationId),
        })
      }
      const restored = await canvas.restoreRelations(workspaceId, ids)
      return { ...p, restoredVersions: Object.fromEntries(restored.map((r) => [r.relationId, r.relationVersion])) }
    }
  }
}

function assertValidState(objectType: string, transform: unknown, properties: unknown): void {
  const result = validateObjectState(objectType, transform, properties)
  if (result.ok) return
  if (result.code === 'unsupported_object_type') {
    // 舊 client 可辨識的升級提示：回報伺服器支援的型別與 schema 版本
    throw rejected('unsupported_object_type', `Object type "${objectType}" is not supported`, {
      supportedObjectTypes: OBJECT_TYPES,
      objectSchemaVersion: OBJECT_SCHEMA_VERSION,
    })
  }
  throw rejected('invalid_object', 'Object state does not match its type schema', result.errors)
}

function assertValidStyle(style: unknown): void {
  const errors = validateRelationStyle(style)
  if (errors.length > 0) throw rejected('invalid_relation', 'Relation style is invalid', errors)
}

/**
 * Group 階層 invariant（BAI-007）：parent 必須是同 Workspace、active 的 group，
 * 且以 transaction 內目前狀態檢查完整 ancestor chain 不形成循環。
 */
async function assertValidParent(repos: Repositories, workspaceId: string, objectId: string, parentId: string) {
  if (parentId === objectId) throw rejected('invalid_parent', 'An object cannot be its own parent')
  const [parent] = await repos.canvas.findObjects(workspaceId, [parentId])
  if (!parent || parent.deletedAt) {
    throw rejected('invalid_parent', 'Parent must be an active object in this workspace', { parentId })
  }
  if (parent.objectType !== GROUP) {
    throw rejected('invalid_parent', 'Parent must be a group', { parentId, parentType: parent.objectType })
  }
  if (await repos.canvas.ancestorChainContains(workspaceId, parentId, objectId)) {
    throw rejected('parent_cycle', 'Parent change would create a cycle', { objectId, parentId })
  }
}

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

async function requireActiveObjects(repos: Repositories, workspaceId: string, ids: string[]): Promise<CanvasObject[]> {
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
