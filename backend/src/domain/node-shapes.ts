/** APP NodeShape token（25 種）；canvas-objects 與 selection 共用，避免互相 import 造成循環 */
export const NODE_SHAPES = [
  'rounded', 'rectangle', 'ellipse', 'diamond', 'pill', 'parallelogram', 'hexagon', 'document', 'database',
  'plain-text', 'triangle', 'pentagon', 'octagon', 'trapezoid', 'plus', 'arrow-right', 'arrow-left', 'arrow-up',
  'arrow-down', 'triangle-down', 'right-triangle', 'chevron', 'double-arrow', 'star', 'manual-input',
] as const
