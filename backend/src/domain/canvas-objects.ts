import { Type, type Static, type TSchema } from 'typebox'
import { Value } from 'typebox/value'

/**
 * Canvas object 的型別化 schema（BAI-008）。對齊 APP `CanvasObject` / `BackendWorkspaceRepository`：
 *   text  → TextNode、group → GroupFrame、media → MediaNode
 *
 * 規則：
 * - 未知 objectType 一律拒絕；新增型別時提升 OBJECT_SCHEMA_VERSION 並在錯誤中回報支援清單。
 * - 欄位「不存在」才套用預設；錯誤型別、未知 token、explicit null 都拒絕。
 * - update 驗證 merge 後的完整狀態，而不只驗證 patch。
 */
export const OBJECT_SCHEMA_VERSION = 1

/** APP `NodeShape.token` */
export const NODE_SHAPES = [
  'rounded', 'rectangle', 'ellipse', 'diamond', 'pill', 'parallelogram', 'hexagon', 'document', 'database',
  'plain-text', 'triangle', 'pentagon', 'octagon', 'trapezoid', 'plus', 'arrow-right', 'arrow-left', 'arrow-up',
  'arrow-down', 'triangle-down', 'right-triangle', 'chevron', 'double-arrow', 'star', 'manual-input',
] as const

/** 主題色 token（APP NodeColor.kt）或使用者自選的 `#rrggbb` */
export const NAMED_COLOR_TOKENS = ['paper', 'lilac', 'amber', 'mint', 'group', 'relation'] as const
const COLOR_TOKEN_PATTERN = `^(${NAMED_COLOR_TOKENS.join('|')}|#[0-9a-fA-F]{6})$`

export const MEDIA_KINDS = ['image', 'gif', 'video'] as const

export const LIMITS = {
  coordinate: 10_000_000,
  size: 1_000_000,
  rotation: 36_000,
  text: 50_000,
  title: 500,
  altText: 2_000,
} as const

const Uuid = Type.String({ format: 'uuid' })
const ColorToken = Type.String({ pattern: COLOR_TOKEN_PATTERN })
const Coordinate = Type.Number({ minimum: -LIMITS.coordinate, maximum: LIMITS.coordinate })
const Size = Type.Number({ minimum: 0, maximum: LIMITS.size })

/** APP `CanvasTransform`：position + size + rotationDegrees */
export const TransformSchema = Type.Object(
  {
    x: Coordinate,
    y: Coordinate,
    width: Size,
    height: Size,
    rotationDegrees: Type.Number({ minimum: -LIMITS.rotation, maximum: LIMITS.rotation }),
  },
  { additionalProperties: false },
)
export type Transform = Static<typeof TransformSchema>

/** 與 APP projection reader 的缺省值相同 */
export const DEFAULT_TRANSFORM: Transform = { x: 0, y: 0, width: 260, height: 132, rotationDegrees: 0 }

const OBJECT_PROPERTIES = {
  text: Type.Object(
    {
      text: Type.Optional(Type.String({ maxLength: LIMITS.text })),
      colorToken: Type.Optional(ColorToken),
      shapeToken: Type.Optional(Type.Enum(NODE_SHAPES)),
    },
    { additionalProperties: false },
  ),
  group: Type.Object(
    {
      title: Type.Optional(Type.String({ maxLength: LIMITS.title })),
      colorToken: Type.Optional(ColorToken),
    },
    { additionalProperties: false },
  ),
  media: Type.Object(
    {
      assetId: Uuid,
      mediaKind: Type.Enum(MEDIA_KINDS),
      altText: Type.Optional(Type.String({ maxLength: LIMITS.altText })),
      // explicit null：清除舊縮圖（APP UpdateMediaReferenceOperation）
      thumbnailAssetId: Type.Optional(Type.Union([Uuid, Type.Null()])),
    },
    { additionalProperties: false },
  ),
} satisfies Record<string, TSchema>

export type ObjectType = keyof typeof OBJECT_PROPERTIES
export const OBJECT_TYPES = Object.keys(OBJECT_PROPERTIES) as ObjectType[]

export function isObjectType(value: string): value is ObjectType {
  return Object.hasOwn(OBJECT_PROPERTIES, value)
}

/** Relation.style（APP 目前只送 colorToken） */
export const RelationStyleSchema = Type.Object({ colorToken: Type.Optional(ColorToken) }, { additionalProperties: false })

export interface StateError {
  path: string
  message: string
}

function errorsOf(schema: TSchema, value: unknown, prefix: string): StateError[] {
  if (Value.Check(schema, value)) return []
  return [...Value.Errors(schema, value)]
    .filter((e) => e.keyword !== 'boolean')
    .map((e) => ({ path: `${prefix}${e.instancePath}`, message: e.message }))
}

/** 把缺少的 transform 欄位補上預設值（只在欄位不存在時） */
export function completeTransform(partial: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = { ...partial }
  for (const [key, value] of Object.entries(DEFAULT_TRANSFORM)) {
    if (!(key in result)) result[key] = value
  }
  return result
}

/** 驗證物件完整狀態（create 後或 merge 後） */
export function validateObjectState(
  objectType: string,
  transform: unknown,
  properties: unknown,
): { ok: true } | { ok: false; code: 'unsupported_object_type' | 'invalid_object'; errors: StateError[] } {
  if (!isObjectType(objectType)) {
    return {
      ok: false,
      code: 'unsupported_object_type',
      errors: [{ path: '/objectType', message: `unsupported object type "${objectType}"` }],
    }
  }
  const errors = [
    ...errorsOf(TransformSchema, transform, '/transform'),
    ...errorsOf(OBJECT_PROPERTIES[objectType], properties, '/properties'),
  ]
  return errors.length === 0 ? { ok: true } : { ok: false, code: 'invalid_object', errors }
}

export function validateRelationStyle(style: unknown): StateError[] {
  return errorsOf(RelationStyleSchema, style, '/style')
}
