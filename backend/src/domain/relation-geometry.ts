import { Type } from 'typebox'
import { Compile } from 'typebox/compile'
import { RELATION_GEOMETRY_SCHEMA, relationGeometryValid, Vec2Schema, type RelationGeometry } from './selection.ts'

/**
 * Relation.style.geometry（boarderless.relation-geometry.v1）的 server 寫入格式：完整 canonical 形狀，
 * 所有欄位必填（APP kotlinx encodeDefaults 的輸出），判別聯集各分支不接受不相干欄位。
 * 重設用完整 auto，不用 null；update 時整個子物件替換。
 */
const strict = { additionalProperties: false } as const
const Num = Type.Number()
export const RelationGeometryWireSchema = Type.Object(
  {
    schema: Type.String(),
    route: Type.Union([
      Type.Object({ mode: Type.Literal('auto') }, strict),
      Type.Object({ mode: Type.Literal('manual'), waypoints: Type.Array(Vec2Schema, { maxItems: 32 }) }, strict),
      Type.Object({ mode: Type.Literal('loop'), side: Type.String(), extent: Num }, strict),
    ]),
    labelPlacement: Type.Union([
      Type.Object({ mode: Type.Literal('auto') }, strict),
      Type.Object({ mode: Type.Literal('manual'), pathFraction: Num, tangentOffset: Num, normalOffset: Num }, strict),
    ]),
  },
  strict,
)
const wireValidator = Compile(RelationGeometryWireSchema)

export type GeometryIssue = { code: 'unsupported_geometry_schema' | 'invalid_relation_geometry'; message: string; errors?: { path: string; message: string }[] }

/** 驗證寫入的 geometry；selfLoop 決定允許的 route（自循環：auto / loop；普通線：auto / manual） */
export function relationGeometryIssue(raw: unknown, selfLoop: boolean): GeometryIssue | null {
  if (raw && typeof raw === 'object' && 'schema' in raw && (raw as { schema: unknown }).schema !== RELATION_GEOMETRY_SCHEMA) {
    return { code: 'unsupported_geometry_schema', message: `Supported geometry schema is ${RELATION_GEOMETRY_SCHEMA}` }
  }
  if (!wireValidator.Check(raw)) {
    return {
      code: 'invalid_relation_geometry',
      message: 'style.geometry does not match the geometry schema',
      errors: [...wireValidator.Errors(raw)].slice(0, 10).map((e) => ({ path: `/style/geometry${e.instancePath}`, message: e.message })),
    }
  }
  if (!relationGeometryValid(raw as RelationGeometry, selfLoop)) {
    return {
      code: 'invalid_relation_geometry',
      message: selfLoop
        ? 'self-loop geometry must use an auto or loop route with valid bounds'
        : 'relation geometry must use an auto or manual route with valid bounds',
    }
  }
  return null
}
