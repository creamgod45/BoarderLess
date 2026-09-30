import { Type, type Static, type TSchema } from 'typebox'
import { Value } from 'typebox/value'
import { RELATION_DIRECTIONS } from '../models/canvas.ts'

/**
 * WorkspaceOperation 種類與 payload schema。
 *
 * 暫定版本：正式定義應與 KMP client 共用的 `WorkspaceOperation`（docs/BACKEND_ARCHITECTURE.md §12-1）
 * 對齊後以 contract fixtures 驗證；此處只涵蓋 B0 完成標準需要的 create/move/delete 與 relation 基本操作。
 */

const Uuid = Type.String({ format: 'uuid' })
const JsonObject = Type.Record(Type.String(), Type.Unknown())
const Direction = Type.Enum(RELATION_DIRECTIONS)

export const CreateObjectPayload = Type.Object(
  {
    objectId: Uuid,
    objectType: Type.String({ minLength: 1, maxLength: 64 }),
    parentId: Type.Optional(Type.Union([Uuid, Type.Null()])),
    zIndex: Type.Optional(Type.Integer()),
    locked: Type.Optional(Type.Boolean()),
    transform: Type.Optional(JsonObject),
    properties: Type.Optional(JsonObject),
  },
  { additionalProperties: false },
)

/** 部分更新；properties 以淺層 merge 套用（JSONB `||`） */
export const UpdateObjectPayload = Type.Object(
  {
    objectId: Uuid,
    parentId: Type.Optional(Type.Union([Uuid, Type.Null()])),
    zIndex: Type.Optional(Type.Integer()),
    locked: Type.Optional(Type.Boolean()),
    transform: Type.Optional(JsonObject),
    properties: Type.Optional(JsonObject),
  },
  { additionalProperties: false },
)

export const MoveObjectsPayload = Type.Object(
  {
    moves: Type.Array(Type.Object({ objectId: Uuid, transform: JsonObject }, { additionalProperties: false }), {
      minItems: 1,
      maxItems: 500,
    }),
  },
  { additionalProperties: false },
)

export const DeleteObjectsPayload = Type.Object(
  { objectIds: Type.Array(Uuid, { minItems: 1, maxItems: 500 }) },
  { additionalProperties: false },
)

export const CreateRelationPayload = Type.Object(
  {
    relationId: Uuid,
    sourceObjectId: Uuid,
    targetObjectId: Uuid,
    direction: Direction,
    intent: Type.Optional(Type.Union([Type.String({ maxLength: 64 }), Type.Null()])),
    label: Type.Optional(Type.Union([Type.String({ maxLength: 500 }), Type.Null()])),
    style: Type.Optional(JsonObject),
  },
  { additionalProperties: false },
)

export const UpdateRelationPayload = Type.Object(
  {
    relationId: Uuid,
    direction: Type.Optional(Direction),
    intent: Type.Optional(Type.Union([Type.String({ maxLength: 64 }), Type.Null()])),
    label: Type.Optional(Type.Union([Type.String({ maxLength: 500 }), Type.Null()])),
    style: Type.Optional(JsonObject),
  },
  { additionalProperties: false },
)

export const DeleteRelationsPayload = Type.Object(
  { relationIds: Type.Array(Uuid, { minItems: 1, maxItems: 500 }) },
  { additionalProperties: false },
)

export const OPERATION_PAYLOADS = {
  create_object: CreateObjectPayload,
  update_object: UpdateObjectPayload,
  move_objects: MoveObjectsPayload,
  delete_objects: DeleteObjectsPayload,
  create_relation: CreateRelationPayload,
  update_relation: UpdateRelationPayload,
  delete_relations: DeleteRelationsPayload,
} satisfies Record<string, TSchema>

export type OperationKind = keyof typeof OPERATION_PAYLOADS
export const OPERATION_KINDS = Object.keys(OPERATION_PAYLOADS) as OperationKind[]

export type OperationPayloads = { [K in OperationKind]: Static<(typeof OPERATION_PAYLOADS)[K]> }

/** 已驗證 payload 的 operation（discriminated union） */
export type TypedOperation = { [K in OperationKind]: { kind: K; payload: OperationPayloads[K] } }[OperationKind]

export function isOperationKind(kind: string): kind is OperationKind {
  return Object.hasOwn(OPERATION_PAYLOADS, kind)
}

export interface PayloadValidationError {
  path: string
  message: string
}

export function validateOperationPayload(
  kind: string,
  payload: unknown,
): { ok: true; operation: TypedOperation } | { ok: false; errors: PayloadValidationError[] } {
  if (!isOperationKind(kind)) {
    return { ok: false, errors: [{ path: '/kind', message: `unknown operation kind "${kind}"` }] }
  }
  const schema = OPERATION_PAYLOADS[kind]
  if (!Value.Check(schema, payload)) {
    const errors = [...Value.Errors(schema, payload)]
      .filter((e) => e.keyword !== 'boolean')
      .map((e) => ({ path: `/payload${e.instancePath}`, message: e.message }))
    return { ok: false, errors }
  }
  return { ok: true, operation: { kind, payload } as TypedOperation }
}

/** 取得 operation 會改動的物件 ID，用於 expectedObjectVersions 檢查。 */
export function touchedObjectIds(op: TypedOperation): string[] {
  switch (op.kind) {
    case 'create_object':
      return []
    case 'update_object':
      return [op.payload.objectId]
    case 'move_objects':
      return op.payload.moves.map((m) => m.objectId)
    case 'delete_objects':
      return op.payload.objectIds
    case 'create_relation':
      return []
    case 'update_relation':
      return [op.payload.relationId]
    case 'delete_relations':
      return op.payload.relationIds
  }
}
