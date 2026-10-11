import { Type, type Static } from 'typebox'

/**
 * 共用 Canvas style（schema 1）。viewport、showGrid、snapToGrid、theme 是裝置偏好，不在這裡。
 * 不接受 alpha、漸層、URL、任意 CSS / SVG；hex 必須是小寫 `#rrggbb`（不靜默修正）。
 */
export const CANVAS_STYLE_SCHEMA_VERSIONS = [1] as const
export const BACKGROUND_TOKENS = ['default', 'warm', 'cool'] as const
export const GRID_PATTERNS = ['none', 'lines', 'dots'] as const
const HEX = /^#[0-9a-f]{6}$/

export const CanvasStyleSchema = Type.Object(
  {
    schemaVersion: Type.Integer(),
    backgroundToken: Type.String({ description: 'default / warm / cool / 小寫 #rrggbb' }),
    gridStyle: Type.Object(
      {
        pattern: Type.String({ description: 'none / lines / dots' }),
        colorToken: Type.String({ description: 'auto / 小寫 #rrggbb' }),
      },
      { additionalProperties: false },
    ),
  },
  { additionalProperties: false },
)
export type CanvasStyle = Static<typeof CanvasStyleSchema>

export const DEFAULT_CANVAS_STYLE: CanvasStyle = {
  schemaVersion: 1,
  backgroundToken: 'default',
  gridStyle: { pattern: 'lines', colorToken: 'auto' },
}

export type CanvasStyleIssue = 'unsupported_canvas_style_schema' | 'invalid_canvas_style'

/** 結構已通過 TypeBox 後的 token 規則 */
export function canvasStyleIssue(style: CanvasStyle): { code: CanvasStyleIssue; field: string } | null {
  if (!(CANVAS_STYLE_SCHEMA_VERSIONS as readonly number[]).includes(style.schemaVersion)) {
    return { code: 'unsupported_canvas_style_schema', field: 'schemaVersion' }
  }
  if (!(BACKGROUND_TOKENS as readonly string[]).includes(style.backgroundToken) && !HEX.test(style.backgroundToken)) {
    return { code: 'invalid_canvas_style', field: 'backgroundToken' }
  }
  if (!(GRID_PATTERNS as readonly string[]).includes(style.gridStyle.pattern)) return { code: 'invalid_canvas_style', field: 'gridStyle.pattern' }
  if (style.gridStyle.colorToken !== 'auto' && !HEX.test(style.gridStyle.colorToken)) {
    return { code: 'invalid_canvas_style', field: 'gridStyle.colorToken' }
  }
  return null
}

/** canonical 形狀（固定欄位順序；保存 / 回應 / checksum 一致） */
export const canonicalCanvasStyle = (s: CanvasStyle): CanvasStyle => ({
  schemaVersion: s.schemaVersion,
  backgroundToken: s.backgroundToken,
  gridStyle: { pattern: s.gridStyle.pattern, colorToken: s.gridStyle.colorToken },
})
