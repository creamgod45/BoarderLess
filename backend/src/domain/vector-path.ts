import { Type } from 'typebox'
import { CanvasSizeSchema, TEXT_ANCHORS, TEXT_FONTS, VectorPathCommandSchema } from './selection.ts'

/**
 * Server 寫入格式（text properties）：與 APP kotlinx encodeDefaults 輸出相同的完整形狀，所有欄位必填。
 * 語意規則（command 順序、subpath、bounds、style）由 selection.ts 的 vectorPathValid / textPlacementValid 檢查。
 */
const strict = { additionalProperties: false } as const
const Nullable = <T extends ReturnType<typeof Type.String>>(s: T) => Type.Union([s, Type.Null()])

export const VECTOR_PATH_LIMITS = {
  minCommands: 2,
  maxCommands: 4096,
  maxSubpaths: 128,
  viewBoxMin: 0.001,
  viewBoxMax: 100_000,
  maxCoordinate: 1_000_000,
  maxStrokeWidth: 128,
} as const

export const VectorPathWireSchema = Type.Object(
  {
    viewBox: CanvasSizeSchema,
    commands: Type.Array(VectorPathCommandSchema, { maxItems: VECTOR_PATH_LIMITS.maxCommands }),
    style: Type.Object(
      {
        fillColorToken: Nullable(Type.String()),
        strokeColorToken: Nullable(Type.String()),
        strokeWidth: Type.Number(),
        fillRule: Type.Union([Type.Literal('NonZero'), Type.Literal('EvenOdd')]),
      },
      strict,
    ),
    schemaVersion: Type.Integer(),
  },
  strict,
)

export const TextPlacementWireSchema = Type.Object(
  {
    anchor: Type.Union([Type.Enum(TEXT_ANCHORS), Type.Null()]),
    offsetX: Type.Number(),
    offsetY: Type.Number(),
    appearance: Type.Object(
      {
        fontSize: Type.Number(),
        weight: Type.Integer(),
        color: Nullable(Type.String()),
        font: Type.Enum(TEXT_FONTS),
        italic: Type.Boolean(),
        underline: Type.Boolean(),
        strikethrough: Type.Boolean(),
      },
      strict,
    ),
  },
  strict,
)
