import { Type, type Static, type TSchema } from 'typebox'
import { Compile } from 'typebox/compile'
import { NODE_SHAPES } from './node-shapes.ts'

/**
 * `boarderless/selection` v1–v9（APP `ClipboardPayload.kt` / `validateClipboardPayload`）的後端移植。
 * 結構對齊 kotlinx.serialization（`ignoreUnknownKeys = false`、有預設值的欄位可省略、無預設值必填），
 * 語意規則對齊 APP validator 與各型別 init 的 require。錯誤碼沿用 APP `ClipboardPayloadIssue` 名稱。
 * 共用片段（VectorPath、RelationGeometry、Sequence）也供批次 D / E 使用。
 */
export const SELECTION_FORMAT = 'boarderless/selection'
export const SELECTION_SCHEMA_VERSIONS = [1, 2, 3, 4, 5, 6, 7, 8, 9] as const
export const SELECTION_LIMITS = { maxObjects: 1000, maxRelations: 2000, maxSequenceDiagrams: 64 } as const

const strict = { additionalProperties: false } as const
const Opt = <T extends TSchema>(s: T) => Type.Optional(s)
const Nullable = <T extends TSchema>(s: T) => Type.Union([s, Type.Null()])
const Num = Type.Number()
const Int = Type.Integer({ minimum: -9007199254740991, maximum: 9007199254740991 })
const Str = Type.String()

// ---- 共用幾何 ----
export const Vec2Schema = Type.Object({ x: Num, y: Num }, strict)
export const CanvasSizeSchema = Type.Object({ width: Num, height: Num }, strict)

// ---- VectorPath（APP domain/model/VectorPath.kt；sealed 預設 discriminator "type"） ----
export const VectorPathCommandSchema = Type.Union([
  Type.Object({ type: Type.Literal('move'), point: Vec2Schema }, strict),
  Type.Object({ type: Type.Literal('line'), point: Vec2Schema }, strict),
  Type.Object({ type: Type.Literal('quadratic'), control: Vec2Schema, point: Vec2Schema }, strict),
  Type.Object({ type: Type.Literal('cubic'), control1: Vec2Schema, control2: Vec2Schema, point: Vec2Schema }, strict),
  Type.Object({ type: Type.Literal('close') }, strict),
])
export const VectorPathStyleSchema = Type.Object(
  {
    fillColorToken: Opt(Nullable(Str)),
    strokeColorToken: Opt(Nullable(Str)),
    strokeWidth: Opt(Num),
    fillRule: Opt(Type.Union([Type.Literal('NonZero'), Type.Literal('EvenOdd')])),
  },
  strict,
)
export const VectorPathSchema = Type.Object(
  {
    viewBox: CanvasSizeSchema,
    commands: Type.Array(VectorPathCommandSchema, { maxItems: 4096 }),
    style: Opt(VectorPathStyleSchema),
    schemaVersion: Opt(Type.Integer()),
  },
  strict,
)
export type VectorPath = Static<typeof VectorPathSchema>

// ---- TextPlacement（APP domain/model/TextPlacement.kt） ----
export const TEXT_ANCHORS = ['TopLeft', 'TopCenter', 'TopRight', 'CenterLeft', 'Center', 'CenterRight', 'BottomLeft', 'BottomCenter', 'BottomRight', 'Above', 'Below', 'Left', 'Right'] as const
export const TEXT_FONTS = ['System', 'SansSerif', 'Serif', 'Monospace', 'Cursive'] as const
export const TextAppearanceSchema = Type.Object(
  {
    fontSize: Opt(Num),
    weight: Opt(Type.Integer()),
    color: Opt(Nullable(Str)),
    font: Opt(Type.Enum(TEXT_FONTS)),
    italic: Opt(Type.Boolean()),
    underline: Opt(Type.Boolean()),
    strikethrough: Opt(Type.Boolean()),
  },
  strict,
)
export const TextPlacementSchema = Type.Object(
  { anchor: Opt(Nullable(Type.Enum(TEXT_ANCHORS))), offsetX: Opt(Num), offsetY: Opt(Num), appearance: Opt(TextAppearanceSchema) },
  strict,
)
export type TextPlacement = Static<typeof TextPlacementSchema>

// ---- RelationGeometry（APP domain/model/RelationGeometry.kt；discriminator "mode"） ----
export const RELATION_GEOMETRY_SCHEMA = 'boarderless.relation-geometry.v1'
export const RelationRouteSchema = Type.Union([
  Type.Object({ mode: Type.Literal('auto') }, strict),
  Type.Object({ mode: Type.Literal('manual'), waypoints: Type.Array(Vec2Schema, { maxItems: 32 }) }, strict),
  Type.Object({ mode: Type.Literal('loop'), side: Opt(Str), extent: Opt(Num) }, strict),
])
export const RelationLabelPlacementSchema = Type.Union([
  Type.Object({ mode: Type.Literal('auto') }, strict),
  Type.Object({ mode: Type.Literal('manual'), pathFraction: Num, tangentOffset: Opt(Num), normalOffset: Opt(Num) }, strict),
])
export const RelationGeometrySchema = Type.Object(
  { schema: Opt(Str), route: Opt(RelationRouteSchema), labelPlacement: Opt(RelationLabelPlacementSchema) },
  strict,
)
export type RelationGeometry = Static<typeof RelationGeometrySchema>

// ---- Sequence（APP domain/sequence/*.kt；CanvasObjectId / RelationId 序列化為 {value}） ----
export const SEQUENCE_CANVAS_SCHEMA = 'boarderless.sequence-canvas.v1'
const IdValue = Type.Object({ value: Str }, strict)
export const SequenceParticipantSchema = Type.Object(
  { id: Str, label: Str, role: Opt(Type.Union([Type.Literal('Participant'), Type.Literal('Actor')])) },
  strict,
)
export const SequenceMessageSchema = Type.Object(
  {
    id: Str,
    sourceId: Str,
    targetId: Str,
    text: Str,
    kind: Opt(Type.Union([Type.Literal('Call'), Type.Literal('Return'), Type.Literal('Async'), Type.Literal('Signal')])),
    dashed: Opt(Type.Boolean()),
  },
  strict,
)
export const SequencePositionSchema = Type.Object(
  { blockId: Opt(Nullable(Str)), branchIndex: Opt(Type.Integer()), order: Type.Integer() },
  strict,
)
export const SequenceCanvasDiagramSchema = Type.Object(
  {
    containerId: IdValue,
    participants: Type.Array(Type.Object({ participant: SequenceParticipantSchema, objectId: IdValue }, strict), { maxItems: 32 }),
    messages: Type.Array(
      Type.Object({ message: SequenceMessageSchema, relationId: IdValue, position: SequencePositionSchema }, strict),
      { maxItems: 512 },
    ),
    blocks: Opt(
      Type.Array(
        Type.Object(
          {
            id: Str,
            kind: Type.Union([Type.Literal('Alt'), Type.Literal('Loop'), Type.Literal('Parallel')]),
            branchLabels: Type.Array(Str, { maxItems: 16 }),
            objectId: IdValue,
            position: SequencePositionSchema,
          },
          strict,
        ),
        { maxItems: 128 },
      ),
    ),
    schema: Opt(Str),
  },
  strict,
)
export type SequenceCanvasDiagram = Static<typeof SequenceCanvasDiagramSchema>

// ---- ClipboardPayload ----
const transformFields = { x: Num, y: Num, width: Num, height: Num, rotationDegrees: Num }
const layerFields = { parentOriginalId: Opt(Nullable(Str)), locked: Opt(Type.Boolean()), zOffset: Opt(Int) }

export const SelectionPayloadSchema = Type.Object(
  {
    format: Str,
    version: Type.Integer(),
    nodes: Type.Array(
      Type.Object(
        {
          originalId: Str,
          ...transformFields,
          text: Str,
          colorToken: Str,
          shapeToken: Opt(Str),
          ...layerFields,
          vectorPath: Opt(Nullable(VectorPathSchema)),
          textPlacement: Opt(Nullable(TextPlacementSchema)),
        },
        strict,
      ),
    ),
    groups: Opt(
      Type.Array(Type.Object({ originalId: Str, ...transformFields, title: Str, colorToken: Str, ...layerFields }, strict)),
    ),
    media: Opt(
      Type.Array(
        Type.Object(
          {
            originalId: Str,
            ...transformFields,
            assetId: Str,
            mediaKind: Str,
            altText: Opt(Str),
            thumbnailAssetId: Opt(Nullable(Str)),
            ...layerFields,
          },
          strict,
        ),
      ),
    ),
    externalMedia: Opt(
      Type.Array(
        Type.Object(
          {
            originalId: Str,
            ...transformFields,
            reference: Type.Object({ provider: Type.Literal('giphy'), id: Str }, strict),
            altText: Opt(Str),
            ...layerFields,
          },
          strict,
        ),
      ),
    ),
    relations: Opt(
      Type.Array(
        Type.Object(
          {
            sourceId: Str,
            targetId: Str,
            direction: Str,
            intent: Opt(Nullable(Str)),
            label: Opt(Nullable(Str)),
            geometry: Opt(Nullable(RelationGeometrySchema)),
            originalId: Opt(Nullable(Str)),
          },
          strict,
        ),
      ),
    ),
    // 物件 / 關聯 / 時序圖數量上限由語意檢查回報 Limit（與 APP 相同），body 1 MiB 上限已限制成本
    sequenceDiagrams: Opt(Type.Array(SequenceCanvasDiagramSchema)),
  },
  strict,
)
export type SelectionPayload = Static<typeof SelectionPayloadSchema>
const payloadValidator = Compile(SelectionPayloadSchema)

/** APP ClipboardPayloadIssue + 解碼期（init require）失敗的細分 */
export type SelectionIssue =
  | 'Malformed'
  | 'UnsupportedFormat'
  | 'UnsupportedVersion'
  | 'Empty'
  | 'InvalidObjectId'
  | 'DuplicateObjectId'
  | 'InvalidTransform'
  | 'UnsupportedShape'
  | 'InvalidMedia'
  | 'InvalidParent'
  | 'ParentCycle'
  | 'InvalidRelation'
  | 'InvalidSequence'
  | 'InvalidVectorPath'
  | 'InvalidTextPlacement'
  | 'Limit'

export interface SelectionValidation {
  ok: boolean
  issue?: SelectionIssue
  /** 結構錯誤時的 JSON pointer 與訊息（不含原始內容） */
  errors?: { path: string; message: string }[]
}

class Invalid extends Error {
  constructor(readonly issue: SelectionIssue) {
    super(issue)
  }
}
const require = (condition: boolean, issue: SelectionIssue): void => {
  if (!condition) throw new Invalid(issue)
}

// ---- 共用語意驗證（供 D / E 重用） ----

const COLOR_TOKEN = /^[A-Za-z0-9#_-]{1,64}$/

/** VectorPath init（schemaVersion 1、viewBox、2..4096 commands、≤128 subpaths、move / close 規則、座標上限、style） */
export function vectorPathValid(path: VectorPath): boolean {
  if ((path.schemaVersion ?? 1) !== 1) return false
  const { width, height } = path.viewBox
  if (!(width >= 0.001 && width <= 100_000 && height >= 0.001 && height <= 100_000)) return false
  if (path.commands.length < 2 || path.commands.length > 4096) return false
  const style = path.style ?? {}
  const fill = style.fillColorToken === undefined ? 'paper' : style.fillColorToken
  const stroke = style.strokeColorToken === undefined ? 'ink' : style.strokeColorToken
  if (fill === null && stroke === null) return false
  if (![fill, stroke].every((t) => t === null || COLOR_TOKEN.test(t))) return false
  const strokeWidth = style.strokeWidth ?? 2
  if (!(strokeWidth > 0 && strokeWidth <= 128)) return false
  let open = false
  let segments = 0
  let subpaths = 0
  for (const command of path.commands) {
    const points =
      command.type === 'move' || command.type === 'line'
        ? [command.point]
        : command.type === 'quadratic'
          ? [command.control, command.point]
          : command.type === 'cubic'
            ? [command.control1, command.control2, command.point]
            : []
    if (!points.every((p) => Math.abs(p.x) <= 1_000_000 && Math.abs(p.y) <= 1_000_000)) return false
    if (command.type === 'move') {
      if (open && segments === 0) return false
      if (++subpaths > 128) return false
      open = true
      segments = 0
    } else if (command.type === 'close') {
      if (!(open && segments > 0)) return false
      open = false
    } else {
      if (!open) return false
      segments++
    }
  }
  return !open || segments > 0
}

export function textPlacementValid(p: TextPlacement): boolean {
  const offsetX = p.offsetX ?? 0
  const offsetY = p.offsetY ?? 0
  if (Math.abs(offsetX) > 100_000 || Math.abs(offsetY) > 100_000) return false
  const a = p.appearance ?? {}
  const fontSize = a.fontSize ?? 16
  const weight = a.weight ?? 500
  if (!(fontSize >= 1 && fontSize <= 512)) return false
  if (!(weight >= 100 && weight <= 900)) return false
  return a.color == null || /^#[0-9a-fA-F]{6}$/.test(a.color)
}

/** RelationGeometry init + allowsEndpoints（自循環不接受 manual、普通線不接受 loop） */
export function relationGeometryValid(g: RelationGeometry, selfLoop: boolean): boolean {
  if ((g.schema ?? RELATION_GEOMETRY_SCHEMA) !== RELATION_GEOMETRY_SCHEMA) return false
  const route = g.route ?? { mode: 'auto' as const }
  if (route.mode === 'manual') {
    if (route.waypoints.length < 1 || route.waypoints.length > 32) return false
    if (!route.waypoints.every((p) => Math.abs(p.x) <= 10_000_000 && Math.abs(p.y) <= 10_000_000)) return false
  }
  if (route.mode === 'loop') {
    const side = route.side ?? 'right'
    const extent = route.extent ?? 80
    if (!['top', 'right', 'bottom', 'left'].includes(side) || !(extent >= 16 && extent <= 4096)) return false
  }
  const label = g.labelPlacement ?? { mode: 'auto' as const }
  if (label.mode === 'manual') {
    if (!(label.pathFraction >= 0 && label.pathFraction <= 1)) return false
    if (Math.abs(label.tangentOffset ?? 0) > 4096 || Math.abs(label.normalOffset ?? 24) > 4096) return false
  }
  return selfLoop ? route.mode !== 'manual' : route.mode !== 'loop'
}

const SEQUENCE_ID = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/
const SEQUENCE_RESERVED = new Set(['end', 'else', 'and', 'alt', 'loop', 'par', 'participant', 'actor', 'sequenceDiagram'])
export const sequenceIdValid = (v: string) => SEQUENCE_ID.test(v) && !SEQUENCE_RESERVED.has(v)
const utf8Bytes = (s: string) => Buffer.byteLength(s, 'utf8')
/** requireSequenceText：非空白、≤4096 UTF-8 bytes、純文字（無控制字元、`<>&;\``、反斜線、`%%`、孤立 surrogate） */
export function sequenceTextValid(v: string): boolean {
  if (/^\s*$/u.test(v) || utf8Bytes(v) > 4096 || v.includes('%%') || !v.isWellFormed()) return false
  for (const ch of v) {
    const c = ch.codePointAt(0)!
    if (c < 0x20 || c === 0x7f || '<>&;`\\'.includes(ch)) return false
  }
  return true
}

/** 圖形端提供給 sequence binding 驗證的檢視（clipboard 用 originalId，server state 用 UUID） */
export interface SequenceGraphView {
  group(id: string): { parentId: string | null; width: number; height: number } | null
  text(id: string): { parentId: string | null; text: string; hasVectorPath: boolean; width: number; height: number } | null
  relation(id: string): { sourceId: string; targetId: string; direction: string; label: string | null; routeMode: string | null } | null
  /** 物件 / relation ID 字元規則（clipboard：[A-Za-z0-9_-]{1,128}；server：UUID） */
  idValid(id: string): boolean
}

/**
 * SequenceCanvasDiagram init（toDraft + SequenceDiagramDraft 規則）與 Workspace.hasValidSequenceBindings。
 * owned：跨 diagram 的物件 / relation 不可共用。失敗丟 Invalid('InvalidSequence')。
 */
export function assertSequenceDiagram(diagram: SequenceCanvasDiagram, graph: SequenceGraphView, owned: { objects: Set<string>; relations: Set<string> }): void {
  const bad = () => new Invalid('InvalidSequence')
  if ((diagram.schema ?? SEQUENCE_CANVAS_SCHEMA) !== SEQUENCE_CANVAS_SCHEMA) throw bad()
  const blocks = diagram.blocks ?? []
  const { participants, messages } = diagram
  if (participants.length < 1 || participants.length > 32 || messages.length < 1 || messages.length > 512 || blocks.length > 128) throw bad()
  const objectIds = [diagram.containerId.value, ...participants.map((p) => p.objectId.value), ...blocks.map((b) => b.objectId.value)]
  const relationIds = messages.map((m) => m.relationId.value)
  if (![...objectIds, ...relationIds].every(graph.idValid)) throw bad()
  if (new Set(objectIds).size !== objectIds.length) throw bad()
  if (new Set(relationIds).size !== relationIds.length) throw bad()

  // 參與者
  const participantIds = participants.map((p) => p.participant.id)
  if (new Set(participantIds).size !== participantIds.length) throw bad()
  let textBytes = 0
  for (const { participant } of participants) {
    if (!sequenceIdValid(participant.id) || !sequenceTextValid(participant.label)) throw bad()
    textBytes += utf8Bytes(participant.label)
  }
  // 訊息
  for (const { message, position } of messages) {
    if (![message.id, message.sourceId, message.targetId].every(sequenceIdValid) || !sequenceTextValid(message.text)) throw bad()
    const kind = message.kind ?? 'Call'
    const dashed = message.dashed ?? false
    if ((kind === 'Return' && !dashed) || (kind === 'Call' && dashed)) throw bad()
    if (!participantIds.includes(message.sourceId) || !participantIds.includes(message.targetId)) throw bad()
    assertPosition(position)
  }
  // 區塊
  const blockById = new Map<string, (typeof blocks)[number]>()
  for (const block of blocks) {
    if (!sequenceIdValid(block.id) || blockById.has(block.id)) throw bad()
    if (block.branchLabels.length < 1 || block.branchLabels.length > 16 || !block.branchLabels.every(sequenceTextValid)) throw bad()
    if (block.kind === 'Loop' ? block.branchLabels.length !== 1 : block.branchLabels.length < 2) throw bad()
    assertPosition(block.position)
    blockById.set(block.id, block)
  }
  const stepIds = [...messages.map((m) => m.message.id), ...blocks.map((b) => b.id)]
  if (new Set(stepIds).size !== stepIds.length) throw bad()
  for (const pos of [...messages.map((m) => m.position), ...blocks.map((b) => b.position)]) {
    if (pos.blockId != null) {
      const parent = blockById.get(pos.blockId)
      if (!parent || (pos.branchIndex ?? 0) >= parent.branchLabels.length) throw bad()
    }
  }
  // 樹狀順序：每個 (parent, branch) 的 order 必須 0..n-1 完整唯一；全部可達、無循環、深度 ≤16、每個 branch 非空
  const key = (blockId: string | null | undefined, branch: number | undefined) => `${blockId ?? ''}#${branch ?? 0}`
  const children = new Map<string, { order: number; blockId?: string }[]>()
  for (const m of messages) children.set(key(m.position.blockId, m.position.branchIndex), [...(children.get(key(m.position.blockId, m.position.branchIndex)) ?? []), { order: m.position.order }])
  for (const b of blocks) children.set(key(b.position.blockId, b.position.branchIndex), [...(children.get(key(b.position.blockId, b.position.branchIndex)) ?? []), { order: b.position.order, blockId: b.id }])
  const usedBlocks = new Set<string>()
  let reachedMessages = 0
  const visit = (parent: string | null, branch: number, depth: number): number => {
    if (depth > 16) throw bad()
    const items = (children.get(key(parent, branch)) ?? []).slice().sort((x, y) => x.order - y.order)
    if (!items.every((item, index) => item.order === index)) throw bad()
    for (const item of items) {
      if (item.blockId === undefined) {
        reachedMessages++
        continue
      }
      if (usedBlocks.has(item.blockId)) throw bad()
      usedBlocks.add(item.blockId)
      const block = blockById.get(item.blockId)!
      block.branchLabels.forEach((label, index) => {
        textBytes += utf8Bytes(label)
        if (visit(block.id, index, depth + 1) === 0) throw bad() // branch 不可為空
      })
    }
    return items.length
  }
  if (visit(null, 0, 0) === 0) throw bad()
  if (usedBlocks.size !== blocks.length || reachedMessages !== messages.length) throw bad()
  for (const m of messages) textBytes += utf8Bytes(m.message.text)
  if (textBytes > 262_144) throw bad()

  // graph bindings（hasValidSequenceBindings）
  for (const id of objectIds) if (owned.objects.has(id)) throw bad()
  for (const id of relationIds) if (owned.relations.has(id)) throw bad()
  objectIds.forEach((id) => owned.objects.add(id))
  relationIds.forEach((id) => owned.relations.add(id))
  const containerId = diagram.containerId.value
  const container = graph.group(containerId)
  if (!container || !(container.width > 0 && container.height > 0)) throw bad()
  const ancestors = new Set([containerId])
  for (let parent = container.parentId; parent !== null; ) {
    if (ancestors.has(parent)) throw bad()
    ancestors.add(parent)
    const group = graph.group(parent)
    if (!group) throw bad()
    parent = group.parentId
  }
  const participantObject = new Map<string, string>()
  for (const { participant, objectId } of participants) {
    const node = graph.text(objectId.value)
    if (!node || node.parentId !== containerId || node.text !== participant.label || node.hasVectorPath) throw bad()
    if (!(node.width > 0 && node.height > 0)) throw bad()
    participantObject.set(participant.id, objectId.value)
  }
  for (const block of blocks) {
    const group = graph.group(block.objectId.value)
    if (!group || group.parentId !== containerId || !(group.width > 0 && group.height > 0)) throw bad()
  }
  for (const { message, relationId } of messages) {
    const relation = graph.relation(relationId.value)
    if (!relation) throw bad()
    if (relation.sourceId !== participantObject.get(message.sourceId) || relation.targetId !== participantObject.get(message.targetId)) throw bad()
    const kind = message.kind ?? 'Call'
    if (relation.direction !== (kind === 'Signal' ? 'None' : 'Forward')) throw bad()
    if (relation.label !== message.text) throw bad()
    if ((relation.routeMode === 'loop') !== (message.sourceId === message.targetId)) throw bad()
  }

  function assertPosition(p: { blockId?: string | null; branchIndex?: number; order: number }) {
    const branchIndex = p.branchIndex ?? 0
    if (p.blockId != null && !sequenceIdValid(p.blockId)) throw bad()
    if (!(branchIndex >= 0 && branchIndex <= 15 && p.order >= 0 && p.order <= 511)) throw bad()
    if (p.blockId == null && branchIndex !== 0) throw bad()
  }
}

// ---- 主驗證 ----

const NODE_SHAPE_SET = new Set<string>(NODE_SHAPES)
const MEDIA_KINDS = new Set(['image', 'gif', 'video'])
const DIRECTIONS = new Set(['None', 'Forward', 'Backward', 'Both'])
const CLIPBOARD_GRAPH_ID = /^[A-Za-z0-9_-]{1,128}$/

export function validateSelectionPayload(raw: unknown): SelectionValidation {
  if (!payloadValidator.Check(raw)) {
    const errors = [...payloadValidator.Errors(raw)].slice(0, 20).map((e) => ({ path: e.instancePath, message: e.message }))
    return { ok: false, issue: 'Malformed', errors }
  }
  try {
    assertSelection(raw)
    return { ok: true }
  } catch (error) {
    if (error instanceof Invalid) return { ok: false, issue: error.issue }
    throw error
  }
}

function assertSelection(p: SelectionPayload): void {
  const groups = p.groups ?? []
  const media = p.media ?? []
  const externalMedia = p.externalMedia ?? []
  const relations = p.relations ?? []
  const sequenceDiagrams = p.sequenceDiagrams ?? []
  const { nodes } = p

  // 解碼期 init（APP 在 decode 時就會失敗）
  for (const node of nodes) {
    if (node.vectorPath && !vectorPathValid(node.vectorPath)) throw new Invalid('InvalidVectorPath')
    if (node.textPlacement && !textPlacementValid(node.textPlacement)) throw new Invalid('InvalidTextPlacement')
  }
  for (const item of externalMedia) require(/^[A-Za-z0-9_-]{1,128}$/.test(item.reference.id), 'InvalidMedia')
  for (const r of relations) if (r.geometry) require(relationGeometryValid(r.geometry, false) || relationGeometryValid(r.geometry, true), 'InvalidRelation')

  // validateClipboardPayload（同樣順序）
  require(p.format === SELECTION_FORMAT, 'UnsupportedFormat')
  require(p.version >= 1 && p.version <= 9, 'UnsupportedVersion')
  require(nodes.length + groups.length + media.length + externalMedia.length > 0, 'Empty')
  require(
    nodes.length + groups.length + media.length + externalMedia.length <= SELECTION_LIMITS.maxObjects &&
      relations.length <= SELECTION_LIMITS.maxRelations &&
      sequenceDiagrams.length <= SELECTION_LIMITS.maxSequenceDiagrams,
    'Limit',
  )
  if (p.version < 7) require(sequenceDiagrams.length === 0 && relations.every((r) => r.originalId == null), 'UnsupportedVersion')
  if (p.version < 8) require(nodes.every((n) => n.textPlacement == null), 'UnsupportedVersion')
  if (p.version < 9) require(externalMedia.length === 0, 'UnsupportedVersion')

  const objectIds = [...groups, ...nodes, ...media, ...externalMedia].map((o) => o.originalId)
  require(objectIds.every((id) => id.trim().length > 0), 'InvalidObjectId')
  require(new Set(objectIds).size === objectIds.length, 'DuplicateObjectId')

  const transformOk = (o: { x: number; y: number; width: number; height: number }) =>
    Math.abs(o.x) <= 1_000_000 && Math.abs(o.y) <= 1_000_000 && o.width > 0 && o.height > 0 && o.width <= 100_000 && o.height <= 100_000
  require([...nodes, ...groups, ...externalMedia, ...media].every(transformOk), 'InvalidTransform')
  if (p.version < 6) require(nodes.every((n) => n.vectorPath == null), 'UnsupportedVersion')
  require(nodes.every((n) => NODE_SHAPE_SET.has(n.shapeToken ?? 'rounded')), 'UnsupportedShape')
  require(
    media.every((m) => m.assetId.trim().length > 0 && MEDIA_KINDS.has(m.mediaKind) && (m.thumbnailAssetId == null || m.thumbnailAssetId.trim().length > 0)),
    'InvalidMedia',
  )
  require(externalMedia.every((m) => (m.altText ?? '').length <= 8192), 'InvalidMedia')

  const groupIds = new Set(groups.map((g) => g.originalId))
  require(
    [...nodes, ...externalMedia, ...media].every((o) => o.parentOriginalId == null || groupIds.has(o.parentOriginalId)) &&
      groups.every((g) => g.parentOriginalId == null || (groupIds.has(g.parentOriginalId) && g.parentOriginalId !== g.originalId)),
    'InvalidParent',
  )
  const parentOf = new Map(groups.map((g) => [g.originalId, g.parentOriginalId ?? null]))
  for (const start of groupIds) {
    const seen = new Set<string>()
    for (let id: string | null = start; id !== null; id = parentOf.get(id) ?? null) {
      require(!seen.has(id), 'ParentCycle')
      seen.add(id)
    }
  }

  const endpointIds = new Set([...nodes, ...media, ...externalMedia].map((o) => o.originalId))
  for (const r of relations) {
    const self = r.sourceId === r.targetId
    require(endpointIds.has(r.sourceId) && endpointIds.has(r.targetId), 'InvalidRelation')
    if (self) require(p.version >= 5 && r.geometry != null, 'InvalidRelation')
    if (r.geometry != null) require(p.version >= 5 && relationGeometryValid(r.geometry, self), 'InvalidRelation')
    require(DIRECTIONS.has(r.direction), 'InvalidRelation')
  }
  const relationIds = relations.map((r) => r.originalId).filter((id): id is string => id != null)
  require(relationIds.every((id) => id.trim().length > 0) && new Set(relationIds).size === relationIds.length, 'InvalidRelation')

  if (sequenceDiagrams.length > 0) {
    require(new Set(sequenceDiagrams.map((d) => d.containerId.value)).size === sequenceDiagrams.length, 'InvalidSequence')
    require(relations.every((r) => r.originalId != null), 'InvalidSequence')
    const groupById = new Map(groups.map((g) => [g.originalId, g]))
    const nodeById = new Map(nodes.map((n) => [n.originalId, n]))
    const relationById = new Map(relations.map((r) => [r.originalId!, r]))
    const graph: SequenceGraphView = {
      group: (id) => {
        const g = groupById.get(id)
        return g ? { parentId: g.parentOriginalId ?? null, width: g.width, height: g.height } : null
      },
      text: (id) => {
        const n = nodeById.get(id)
        return n ? { parentId: n.parentOriginalId ?? null, text: n.text, hasVectorPath: n.vectorPath != null, width: n.width, height: n.height } : null
      },
      relation: (id) => {
        const r = relationById.get(id)
        return r ? { sourceId: r.sourceId, targetId: r.targetId, direction: r.direction, label: r.label ?? null, routeMode: r.geometry?.route?.mode ?? null } : null
      },
      idValid: (id) => CLIPBOARD_GRAPH_ID.test(id),
    }
    const owned = { objects: new Set<string>(), relations: new Set<string>() }
    for (const diagram of sequenceDiagrams) assertSequenceDiagram(diagram, graph, owned)
  }
}

/** payload 內所有素材引用（原檔 / 縮圖），用來核對 assetBindings */
export function selectionAssetReferences(p: SelectionPayload): { assetId: string; kind: 'original' | 'thumbnail'; mediaKind: string }[] {
  const refs = new Map<string, { assetId: string; kind: 'original' | 'thumbnail'; mediaKind: string }>()
  for (const m of p.media ?? []) {
    refs.set(`original:${m.assetId}`, { assetId: m.assetId, kind: 'original', mediaKind: m.mediaKind })
    if (m.thumbnailAssetId) refs.set(`thumbnail:${m.thumbnailAssetId}`, { assetId: m.thumbnailAssetId, kind: 'thumbnail', mediaKind: 'image' })
  }
  return [...refs.values()]
}
