/** APP NodeShape token（49 種：原 25 + 擴充 24；依規格 SHAPE_LIBRARY_SPEC.md 順序）；canvas-objects 與 selection 共用，避免互相 import 造成循環 */
export const NODE_SHAPES = [
  'rounded', 'rectangle', 'ellipse', 'diamond', 'pill', 'parallelogram', 'hexagon', 'document', 'database',
  'plain-text', 'triangle', 'pentagon', 'octagon', 'trapezoid', 'plus', 'arrow-right', 'arrow-left', 'arrow-up',
  'arrow-down', 'triangle-down', 'right-triangle', 'chevron', 'double-arrow', 'star', 'manual-input',
  // 擴充形狀 v1：A 基本幾何
  'semicircle', 'ring', 'heptagon', 'heart', 'lightning', 'crescent',
  // B 流程圖
  'predefined-process', 'internal-storage', 'delay', 'or-junction', 'summing-junction', 'off-page-connector', 'display',
  'multi-document',
  // C 標註
  'callout-round', 'callout-rect', 'note', 'brace-left', 'brace-right',
  // D 容器／架構／拓樸
  'folder', 'window', 'cloud', 'server', 'person',
] as const
