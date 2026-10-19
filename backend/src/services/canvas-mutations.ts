import type { MediaReference } from '../repositories/provenance.repository.ts'
import { canonicalDiagrams, diagramListErrors, firstInvalidDiagram, sameDiagrams } from '../domain/sequence-diagrams.ts'
import { relationGeometryIssue } from '../domain/relation-geometry.ts'
import { canonicalCanvasStyle, canvasStyleIssue } from '../domain/canvas-style.ts'
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
/** update_sequence_diagrams 的 CAS / before 不一致 */
export class SequenceDiagramsConflictSignal extends Error {
  constructor(
    readonly expectedVersion: number,
    readonly actualVersion: number,
  ) {
    super('sequence diagrams conflict')
  }
}

/** update_canvas_style 的 CAS 失敗（不放進 expectedObjectVersions 的 conflicts） */
export class CanvasStyleConflictSignal extends Error {
  constructor(
    readonly expectedVersion: number,
    readonly actualVersion: number,
  ) {
    super('canvas style version conflict')
  }
}

export class VersionConflictSignal extends Error {
  constructor(readonly conflicts: VersionConflict[]) {
    super('version conflict')
  }
}

const rejected = (code: string, message: string, details?: unknown) => unprocessable(code, message, details)

const GROUP = 'group'
const MEDIA = 'media'
const MEDIA_KEYS = ['assetId', 'mediaKind', 'thumbnailAssetId']
const MEDIA_REF_KEYS = ['assetId', 'thumbnailAssetId'] as const

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
/** 原交易身分（provenance 記錄用） */
export interface OperationContext {
  transactionId: string
  operationId: string
}

const mediaReferenceOf = (properties: JsonObject): MediaReference => ({
  assetId: properties.assetId as string,
  thumbnailAssetId: (properties.thumbnailAssetId as string | null | undefined) ?? null,
})
const sameReference = (a: MediaReference, b: MediaReference) => a.assetId === b.assetId && a.thumbnailAssetId === b.thumbnailAssetId

export async function applyOperation(
  repos: Repositories,
  workspaceId: string,
  actorId: string,
  op: TypedOperation,
  context: OperationContext,
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
      if (p.objectType === MEDIA) await validateMediaReferences(repos, workspaceId, properties, { requireReady: 'all' })
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
        // 替換素材時新引用必須 ready；只改 mediaKind / 其他欄位時沿用既有引用
        const changed = MEDIA_REF_KEYS.filter((k) => k in p.properties! && p.properties![k] !== obj!.properties[k])
        await validateMediaReferences(repos, workspaceId, properties, { requireReady: changed })
        // 批次 F：引用實際改變時，同交易保存完整 before / after（不是只存 patch）
        const before = mediaReferenceOf(obj!.properties)
        const after = mediaReferenceOf(properties)
        if (!sameReference(before, after)) {
          await repos.provenance.record({ workspaceId, actorId, ...context, objectId: p.objectId, before, after })
        }
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
        // 還原原本的引用：不要求 ready（素材可能已 missing / rejected，由 APP 顯示不可用），也不讓素材復活
        if (obj.objectType === MEDIA) await validateMediaReferences(repos, workspaceId, obj.properties, { requireReady: [] })
      }
      return { ...p, restoredVersions: Object.fromEntries(restored.map((o) => [o.objectId, o.objectVersion])) }
    }

    case 'create_relation': {
      const p = op.payload
      const selfLoop = p.sourceObjectId === p.targetObjectId
      // 批次 D：自循環只在明確 geometry（auto / loop）時允許；不用零長度直線冒充
      if (selfLoop && p.style?.geometry === undefined) {
        throw rejected('invalid_relation_geometry', 'Self-loops require an explicit style.geometry (auto or loop route)')
      }
      const [existing] = await canvas.findRelations(workspaceId, [p.relationId])
      if (existing) {
        throw rejected('relation_already_exists', `Relation ${p.relationId} already exists`, {
          deleted: Boolean(existing.deletedAt),
          ...(existing.deletedAt ? { restoreWith: 'restore_relations' } : {}),
        })
      }
      assertValidStyle(p.style ?? {}, selfLoop)
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
      // style 淺層 merge：geometry 是整個子物件替換（不 deep merge）；驗證 merge 後的完整 relation
      assertValidStyle({ ...relation!.style, ...p.style }, relation!.sourceObjectId === relation!.targetObjectId)
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

    case 'update_canvas_style': {
      const p = op.payload
      // Workspace row 已在 commit 開頭鎖定；同一 transaction 內前一個 style operation 的結果也會讀到
      const workspace = (await repos.workspaces.findById(workspaceId))!
      if (workspace.canvasStyleVersion !== p.expectedCanvasStyleVersion) {
        throw new CanvasStyleConflictSignal(p.expectedCanvasStyleVersion, workspace.canvasStyleVersion)
      }
      const issue = canvasStyleIssue(p.canvasStyle)
      if (issue) throw rejected(issue.code, `canvasStyle.${issue.field} is not supported`, { field: issue.field })
      if (workspace.canvasStyleVersion >= Number.MAX_SAFE_INTEGER) {
        throw rejected('canvas_style_version_exhausted', 'canvasStyleVersion reached the safe integer limit')
      }
      const canvasStyle = canonicalCanvasStyle(p.canvasStyle)
      const canvasStyleVersion = workspace.canvasStyleVersion + 1
      await repos.workspaces.updateCanvasStyle(workspaceId, canvasStyle, canvasStyleVersion)
      // 正式 payload 帶權威的新版本：replay / fan-out 依此設定，不由 client 自行推算
      return { expectedCanvasStyleVersion: p.expectedCanvasStyleVersion, canvasStyle, canvasStyleVersion }
    }

    case 'restore_media_references': {
      const p = op.payload
      // 原交易 provenance：同 workspace、目前 actor、已 commit；陌生 actor 或沒有 provenance（含 migration 前的舊變更）一律拒絕
      const provenance = await repos.provenance.find(workspaceId, actorId, p.originalOperationId)
      if (!provenance || provenance.transactionId !== p.originalTransactionId) {
        throw rejected('provenance_not_found', 'No media reference provenance for this original transaction by the current actor')
      }
      if (provenance.objectId !== p.objectId) throw rejected('provenance_mismatch', 'The original operation did not change this object')
      const [obj] = await canvas.findObjects(workspaceId, [p.objectId])
      if (!obj || obj.deletedAt) throw rejected('object_not_found', 'Some objects do not exist', { objectIds: [p.objectId] })
      if (obj.objectType !== MEDIA) throw rejected('unsupported_change', 'Only media nodes have media references')
      if (obj.objectVersion !== p.expectedObjectVersion) {
        throw new VersionConflictSignal([{ id: p.objectId, expectedVersion: p.expectedObjectVersion, actualVersion: obj.objectVersion }])
      }
      if (obj.locked) throw rejected('object_locked', `Object ${p.objectId} is locked`)
      const target = p.targetSide === 'before' ? provenance.before : provenance.after
      const opposite = p.targetSide === 'before' ? provenance.after : provenance.before
      // 目前引用必須恰為原交易的另一側；已被其他變更改寫則拒絕
      if (!sameReference(mediaReferenceOf(obj.properties), opposite)) {
        throw rejected('media_reference_changed', 'The current media reference is not the other side of the original transaction')
      }
      // 歷史引用可以是 missing / rejected：不要求 ready、不讓素材復活或授予下載；只確認仍屬同一 Workspace
      const ids = [target.assetId, ...(target.thumbnailAssetId ? [target.thumbnailAssetId] : [])]
      for (const id of ids) {
        const asset = await repos.assets.findById(id)
        if (!asset || asset.workspaceId !== workspaceId) throw rejected('asset_not_found', 'Referenced assets do not exist in this workspace', { assetIds: [id] })
      }
      const patch = { assetId: target.assetId, thumbnailAssetId: target.thumbnailAssetId }
      await canvas.updateObject(workspaceId, p.objectId, actorId, { properties: patch })
      await repos.provenance.record({ workspaceId, actorId, ...context, objectId: p.objectId, before: opposite, after: target })
      // 正式 payload 帶還原後的引用，replay / fan-out 依此套用
      return { ...p, restoredReference: patch, objectVersion: obj.objectVersion + 1 }
    }

    case 'update_sequence_diagrams': {
      const p = op.payload
      const workspace = (await repos.workspaces.findById(workspaceId))!
      // before 必須與目前完整一致 + version CAS；graph bindings 在整個 transaction 結束時驗證
      if (workspace.sequenceDiagramsVersion !== p.expectedSequenceDiagramsVersion || !sameDiagrams(p.before, workspace.sequenceDiagrams)) {
        throw new SequenceDiagramsConflictSignal(p.expectedSequenceDiagramsVersion, workspace.sequenceDiagramsVersion)
      }
      const errors = diagramListErrors(p.after)
      if (errors.length > 0) throw rejected('invalid_sequence_diagram', 'after is not a valid sequence diagram list', errors)
      if (workspace.sequenceDiagramsVersion >= Number.MAX_SAFE_INTEGER) {
        throw rejected('sequence_diagrams_version_exhausted', 'sequenceDiagramsVersion reached the safe integer limit')
      }
      const after = canonicalDiagrams(p.after)
      const sequenceDiagramsVersion = workspace.sequenceDiagramsVersion + 1
      await repos.workspaces.updateSequenceDiagrams(workspaceId, after, sequenceDiagramsVersion)
      return {
        expectedSequenceDiagramsVersion: p.expectedSequenceDiagramsVersion,
        before: canonicalDiagrams(p.before),
        after,
        sequenceDiagramsVersion,
      }
    }
  }
}

/**
 * Transaction 結束時驗證全部時序圖 bindings（APP hasValidSequenceBindings）：
 * 刪除 / 修改 participant 節點或訊息 relation 而沒有同交易更新 metadata 時，整個 transaction 拒絕。
 */
export async function assertSequenceBindings(repos: Repositories, workspaceId: string): Promise<void> {
  const workspace = (await repos.workspaces.findById(workspaceId))!
  const diagrams = workspace.sequenceDiagrams
  if (diagrams.length === 0) return
  const objectIds = diagrams.flatMap((d) => [d.containerId.value, ...d.participants.map((x) => x.objectId.value), ...d.blocks.map((x) => x.objectId.value)])
  const relationIds = diagrams.flatMap((d) => d.messages.map((m) => m.relationId.value))
  const objects = new Map((await repos.canvas.findObjects(workspaceId, objectIds)).filter((o) => !o.deletedAt).map((o) => [o.objectId, o]))
  const relations = new Map((await repos.canvas.findRelations(workspaceId, relationIds)).filter((r) => !r.deletedAt).map((r) => [r.relationId, r]))
  const groupCache = new Map(objects)
  const loadGroup = async (id: string) => {
    if (!groupCache.has(id)) {
      const [o] = await repos.canvas.findObjects(workspaceId, [id])
      if (o && !o.deletedAt) groupCache.set(id, o)
    }
  }
  // container 的祖先鏈（parent 必須都是 active group）
  for (const d of diagrams) {
    const seen = new Set<string>()
    for (let id: string | null = d.containerId.value; id !== null && !seen.has(id); ) {
      seen.add(id)
      await loadGroup(id)
      id = (groupCache.get(id)?.parentId as string | null | undefined) ?? null
    }
  }
  const size = (o: { transform: JsonObject }) => ({ width: Number(o.transform.width), height: Number(o.transform.height) })
  const capital = (d: string) => d.charAt(0).toUpperCase() + d.slice(1)
  const invalid = firstInvalidDiagram(diagrams, {
    group: (id) => {
      const o = groupCache.get(id)
      return o && o.objectType === GROUP ? { parentId: o.parentId, ...size(o) } : null
    },
    text: (id) => {
      const o = objects.get(id)
      return o && o.objectType === 'text'
        ? { parentId: o.parentId, text: String(o.properties.text ?? ''), hasVectorPath: o.properties.vectorPath != null, ...size(o) }
        : null
    },
    relation: (id) => {
      const r = relations.get(id)
      const route = (r?.style.geometry as { route?: { mode?: string } } | undefined)?.route?.mode ?? null
      return r ? { sourceId: r.sourceObjectId, targetId: r.targetObjectId, direction: capital(r.direction), label: r.label, routeMode: route } : null
    },
  })
  if (invalid) {
    throw rejected('invalid_sequence_binding', 'Sequence diagram metadata does not match the graph after this transaction', { containerId: invalid })
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

function assertValidStyle(style: Record<string, unknown>, selfLoop: boolean): void {
  const errors = validateRelationStyle(style)
  if (errors.length > 0) throw rejected('invalid_relation', 'Relation style is invalid', errors)
  if (style.geometry !== undefined) {
    const issue = relationGeometryIssue(style.geometry, selfLoop)
    if (issue) throw rejected(issue.code, issue.message, issue.errors)
  } else if (selfLoop) {
    throw rejected('invalid_relation_geometry', 'Self-loops require an explicit style.geometry (auto or loop route)')
  }
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
 * ASSET-07：新建 Node 與替換素材的新引用必須 ready；restore_objects 還原原引用不要求 ready
 * （素材可能已 missing / rejected，還原不是重新授權，也不讓素材復活）。
 */
async function validateMediaReferences(
  repos: Repositories,
  workspaceId: string,
  properties: JsonObject,
  options: { requireReady: 'all' | readonly (typeof MEDIA_REF_KEYS)[number][] },
) {
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
  const keys = options.requireReady === 'all' ? MEDIA_REF_KEYS : options.requireReady
  const mustBeReady = keys.map((k) => properties[k]).filter((id): id is string => typeof id === 'string')
  const notReady = assets.filter((a) => mustBeReady.includes(a.id) && a.status !== 'ready')
  if (notReady.length > 0) {
    throw rejected('asset_not_ready', 'New media references must point to ready assets', {
      assets: notReady.map((a) => ({ assetId: a.id, status: a.status })),
    })
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
