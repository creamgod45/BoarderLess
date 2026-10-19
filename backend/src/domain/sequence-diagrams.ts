import { Type, type Static } from 'typebox'
import { Compile } from 'typebox/compile'
import { assertSequenceDiagram, SEQUENCE_CANVAS_SCHEMA, type SequenceCanvasDiagram, type SequenceGraphView } from './selection.ts'

/**
 * Workspace 時序圖 metadata（boarderless.sequence-canvas.v1）的 server 寫入格式。
 * 欄位與 APP `SequenceCanvasDiagram` 的 kotlinx 輸出相同（CanvasObjectId / RelationId 為 {"value"}），
 * 但所有欄位必填（canonical），graph ID 必須是小寫 UUID；semantic ID 是受限 ASCII，不是 UUID。
 * APP 的 Map<CanvasObjectId, SequenceCanvasDiagram> 在 wire 上是以 containerId 為 key 的陣列（順序無關）。
 */
export const MAX_SEQUENCE_DIAGRAMS = 64
const strict = { additionalProperties: false } as const
const LOWER_UUID = '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
const GraphId = Type.Object({ value: Type.String({ pattern: LOWER_UUID }) }, strict)
const Position = Type.Object({ blockId: Type.Union([Type.String(), Type.Null()]), branchIndex: Type.Integer(), order: Type.Integer() }, strict)

export const SequenceDiagramWireSchema = Type.Object(
  {
    containerId: GraphId,
    participants: Type.Array(
      Type.Object(
        {
          participant: Type.Object(
            { id: Type.String(), label: Type.String(), role: Type.Union([Type.Literal('Participant'), Type.Literal('Actor')]) },
            strict,
          ),
          objectId: GraphId,
        },
        strict,
      ),
      { maxItems: 32 },
    ),
    messages: Type.Array(
      Type.Object(
        {
          message: Type.Object(
            {
              id: Type.String(),
              sourceId: Type.String(),
              targetId: Type.String(),
              text: Type.String(),
              kind: Type.Union([Type.Literal('Call'), Type.Literal('Return'), Type.Literal('Async'), Type.Literal('Signal')]),
              dashed: Type.Boolean(),
            },
            strict,
          ),
          relationId: GraphId,
          position: Position,
        },
        strict,
      ),
      { maxItems: 512 },
    ),
    blocks: Type.Array(
      Type.Object(
        {
          id: Type.String(),
          kind: Type.Union([Type.Literal('Alt'), Type.Literal('Loop'), Type.Literal('Parallel')]),
          branchLabels: Type.Array(Type.String(), { maxItems: 16 }),
          objectId: GraphId,
          position: Position,
        },
        strict,
      ),
      { maxItems: 128 },
    ),
    schema: Type.Literal(SEQUENCE_CANVAS_SCHEMA),
  },
  strict,
)
export type SequenceDiagram = Static<typeof SequenceDiagramWireSchema>
export const SequenceDiagramListSchema = Type.Array(SequenceDiagramWireSchema, { maxItems: MAX_SEQUENCE_DIAGRAMS })
const listValidator = Compile(SequenceDiagramListSchema)

/** 依 containerId 排序的 canonical 陣列（保存 / 比對 / 回應一致） */
export function canonicalDiagrams(list: SequenceDiagram[]): SequenceDiagram[] {
  return [...list]
    .sort((a, b) => a.containerId.value.localeCompare(b.containerId.value))
    .map((d) => ({
      containerId: { value: d.containerId.value },
      participants: d.participants.map((p) => ({
        participant: { id: p.participant.id, label: p.participant.label, role: p.participant.role },
        objectId: { value: p.objectId.value },
      })),
      messages: d.messages.map((m) => ({
        message: { id: m.message.id, sourceId: m.message.sourceId, targetId: m.message.targetId, text: m.message.text, kind: m.message.kind, dashed: m.message.dashed },
        relationId: { value: m.relationId.value },
        position: { blockId: m.position.blockId, branchIndex: m.position.branchIndex, order: m.position.order },
      })),
      blocks: d.blocks.map((b) => ({
        id: b.id,
        kind: b.kind,
        branchLabels: [...b.branchLabels],
        objectId: { value: b.objectId.value },
        position: { blockId: b.position.blockId, branchIndex: b.position.branchIndex, order: b.position.order },
      })),
      schema: SEQUENCE_CANVAS_SCHEMA,
    }))
}

export const sameDiagrams = (a: SequenceDiagram[], b: SequenceDiagram[]) => JSON.stringify(canonicalDiagrams(a)) === JSON.stringify(canonicalDiagrams(b))

export function diagramListErrors(raw: unknown): { path: string; message: string }[] {
  if (listValidator.Check(raw)) {
    const ids = (raw as SequenceDiagram[]).map((d) => d.containerId.value)
    return new Set(ids).size === ids.length ? [] : [{ path: '', message: 'containerId must be unique' }]
  }
  return [...listValidator.Errors(raw)].slice(0, 10).map((e) => ({ path: e.instancePath, message: e.message }))
}

const UUID_RE = new RegExp(LOWER_UUID)

/**
 * 依目前 graph 驗證全部 diagram（APP hasValidSequenceBindings）：container / block 是 group、participant 是同 container 的 text，
 * label = 節點文字、無 vectorPath；message 對應 relation 的端點 / 方向 / label / 自循環；跨 diagram 不共用物件或 relation。
 * 回傳失敗的 containerId（null = 全部合法）。
 */
export function firstInvalidDiagram(diagrams: SequenceDiagram[], graph: Omit<SequenceGraphView, 'idValid'>): string | null {
  const owned = { objects: new Set<string>(), relations: new Set<string>() }
  const view: SequenceGraphView = { ...graph, idValid: (id) => UUID_RE.test(id) }
  for (const diagram of diagrams) {
    try {
      assertSequenceDiagram(diagram as unknown as SequenceCanvasDiagram, view, owned)
    } catch {
      return diagram.containerId.value
    }
  }
  return null
}
