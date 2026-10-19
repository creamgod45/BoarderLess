/**
 * `boarderless/selection` v1–v9 範例，形狀對齊 APP `ClipboardJson`（kotlinx：encodeDefaults=true、explicitNulls=true；
 * `@EncodeDefault(NEVER)` 欄位為預設值時省略；sealed 類別 discriminator：VectorPath 用 "type"、RelationRoute / Label 用 "mode"；
 * CanvasObjectId / RelationId 序列化為 {"value": ...}）。
 * 注意：這些是依 Kotlin 原始碼手寫的跨端樣本，不是由 Kotlin serializer 實際輸出；APP 端需以 decode → re-encode 核對。
 */

type Json = Record<string, unknown>

const node = (originalId: string, extra: Json = {}): Json => ({
  originalId,
  x: 0,
  y: 0,
  width: 240,
  height: 120,
  rotationDegrees: 0,
  text: 'Plan',
  colorToken: 'paper',
  shapeToken: 'rounded',
  parentOriginalId: null,
  locked: false,
  zOffset: 0,
  vectorPath: null,
  ...extra,
})

const group = (originalId: string, extra: Json = {}): Json => ({
  originalId,
  x: -20,
  y: -20,
  width: 600,
  height: 400,
  rotationDegrees: 0,
  title: 'Group',
  colorToken: 'group',
  parentOriginalId: null,
  locked: false,
  zOffset: 0,
  ...extra,
})

const relation = (sourceId: string, targetId: string, extra: Json = {}): Json => ({
  sourceId,
  targetId,
  direction: 'Forward',
  intent: null,
  label: null,
  geometry: null,
  ...extra,
})

const base = (version: number, parts: Json): Json => ({
  format: 'boarderless/selection',
  version,
  nodes: [],
  groups: [],
  media: [],
  relations: [],
  ...parts,
})

export function selectionV1(): Json {
  return base(1, { nodes: [node('n1')] })
}

/** 舊版 APP 的最小 v1（沒有後續新增欄位；全部依預設值補齊） */
export function selectionV1Legacy(): Json {
  return {
    format: 'boarderless/selection',
    version: 1,
    nodes: [{ originalId: 'template-node-1', x: 0, y: 0, width: 240, height: 120, rotationDegrees: 0, text: 'Plan', colorToken: 'paper' }],
  }
}

export function selectionV2(): Json {
  return base(2, {
    groups: [group('g1'), group('g2', { parentOriginalId: 'g1', zOffset: 1 })],
    nodes: [node('n1', { parentOriginalId: 'g2' }), node('n2', { x: 300, text: 'Do' })],
    relations: [relation('n1', 'n2', { intent: 'next', label: 'then' })],
  })
}

export function selectionV3(): Json {
  return base(3, {
    nodes: [
      node('n1', { shapeToken: 'diamond', rotationDegrees: 37.5, locked: true, zOffset: 2 }),
      node('n2', { shapeToken: 'manual-input', colorToken: '#a1b2c3', zOffset: -1 }),
    ],
    relations: [relation('n1', 'n2', { direction: 'Both' })],
  })
}

/** v4：素材（原檔 + 縮圖），assetId 由呼叫端提供真實 server UUID */
export function selectionV4(assets: { image: string; video: string; videoThumbnail: string }): Json {
  return base(4, {
    nodes: [node('n1')],
    media: [
      { originalId: 'm1', x: 0, y: 200, width: 320, height: 180, rotationDegrees: 0, assetId: assets.image, mediaKind: 'image', altText: 'diagram', thumbnailAssetId: null, parentOriginalId: null, locked: false, zOffset: 1 },
      { originalId: 'm2', x: 400, y: 200, width: 320, height: 180, rotationDegrees: 0, assetId: assets.video, mediaKind: 'video', altText: '', thumbnailAssetId: assets.videoThumbnail, parentOriginalId: null, locked: false, zOffset: 2 },
    ],
    relations: [relation('n1', 'm1')],
  })
}

export function selectionV5(): Json {
  return base(5, {
    nodes: [node('n1'), node('n2', { x: 400 })],
    relations: [
      relation('n1', 'n2', {
        geometry: {
          schema: 'boarderless.relation-geometry.v1',
          route: { mode: 'manual', waypoints: [{ x: 200, y: -80 }, { x: 320, y: -80 }] },
          labelPlacement: { mode: 'manual', pathFraction: 0.5, tangentOffset: 0, normalOffset: 24 },
        },
        label: 'routed',
      }),
      relation('n2', 'n2', {
        direction: 'None',
        geometry: { schema: 'boarderless.relation-geometry.v1', route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } },
      }),
    ],
  })
}

export const SAMPLE_VECTOR_PATH: Json = {
  viewBox: { width: 100, height: 100 },
  commands: [
    { type: 'move', point: { x: 0, y: 0 } },
    { type: 'line', point: { x: 100, y: 0 } },
    { type: 'quadratic', control: { x: 120, y: 50 }, point: { x: 100, y: 100 } },
    { type: 'cubic', control1: { x: 60, y: 120 }, control2: { x: 40, y: 120 }, point: { x: 0, y: 100 } },
    { type: 'close' },
  ],
  style: { fillColorToken: 'paper', strokeColorToken: 'ink', strokeWidth: 2, fillRule: 'NonZero' },
  schemaVersion: 1,
}

export function selectionV6(): Json {
  return base(6, { nodes: [node('n1', { vectorPath: SAMPLE_VECTOR_PATH, text: '' }), node('n2', { x: 300 })] })
}

/** v7：時序圖 metadata + relation originalId（container / participants / loop block / self-message） */
export function selectionV7(): Json {
  const loop = { schema: 'boarderless.relation-geometry.v1', route: { mode: 'loop', side: 'right', extent: 80 }, labelPlacement: { mode: 'auto' } }
  return base(7, {
    groups: [group('seq', { title: 'Login' }), group('blk', { parentOriginalId: 'seq', x: 0, y: 200, width: 500, height: 120, title: 'retry' })],
    nodes: [
      node('pA', { parentOriginalId: 'seq', text: 'Client' }),
      node('pB', { parentOriginalId: 'seq', x: 300, text: 'Server' }),
    ],
    relations: [
      relation('pA', 'pB', { label: 'request', originalId: 'r1' }),
      relation('pB', 'pA', { label: 'response', originalId: 'r2' }),
      relation('pA', 'pA', { label: 'retry later', geometry: loop, originalId: 'r3' }),
    ],
    sequenceDiagrams: [
      {
        containerId: { value: 'seq' },
        participants: [
          { participant: { id: 'client', label: 'Client', role: 'Actor' }, objectId: { value: 'pA' } },
          { participant: { id: 'server', label: 'Server', role: 'Participant' }, objectId: { value: 'pB' } },
        ],
        messages: [
          { message: { id: 'm1', sourceId: 'client', targetId: 'server', text: 'request', kind: 'Call', dashed: false }, relationId: { value: 'r1' }, position: { blockId: null, branchIndex: 0, order: 0 } },
          { message: { id: 'm2', sourceId: 'server', targetId: 'client', text: 'response', kind: 'Return', dashed: true }, relationId: { value: 'r2' }, position: { blockId: null, branchIndex: 0, order: 1 } },
          { message: { id: 'm3', sourceId: 'client', targetId: 'client', text: 'retry later', kind: 'Async', dashed: false }, relationId: { value: 'r3' }, position: { blockId: 'retryLoop', branchIndex: 0, order: 0 } },
        ],
        blocks: [{ id: 'retryLoop', kind: 'Loop', branchLabels: ['until ok'], objectId: { value: 'blk' }, position: { blockId: null, branchIndex: 0, order: 2 } }],
        schema: 'boarderless.sequence-canvas.v1',
      },
    ],
  })
}

export function selectionV8(): Json {
  return base(8, {
    nodes: [
      node('n1', {
        textPlacement: {
          anchor: 'Below',
          offsetX: 0,
          offsetY: 8,
          appearance: { fontSize: 18, weight: 700, color: '#112233', font: 'Serif', italic: true, underline: false, strikethrough: false },
        },
      }),
    ],
  })
}

export function selectionV9(): Json {
  return base(9, {
    nodes: [node('n1')],
    externalMedia: [
      { originalId: 'gif1', x: 300, y: 0, width: 200, height: 200, rotationDegrees: 0, reference: { provider: 'giphy', id: 'xT9IgDEI1iZyb2wqo8' }, altText: 'cat', parentOriginalId: null, locked: false, zOffset: 1 },
    ],
    relations: [relation('n1', 'gif1')],
  })
}

/** 除 v4 外不需要素材的版本 */
export const TEXT_ONLY_SAMPLES: Record<number, () => Json> = {
  1: selectionV1,
  2: selectionV2,
  3: selectionV3,
  5: selectionV5,
  6: selectionV6,
  7: selectionV7,
  8: selectionV8,
  9: selectionV9,
}
