import { Type } from 'typebox'
import { CanvasStyleSchema } from '../domain/canvas-style.ts'

export const CapabilitiesSchema = Type.Object({
  canvas: Type.Object({
    protocolVersion: Type.Integer(),
    operationSchemaVersion: Type.Integer(),
    minWritableProtocolVersion: Type.Integer(),
  }),
  assetPreparation: Type.Object({
    requestSchema: Type.String(),
    responseSchema: Type.String(),
    maxByteSize: Type.Integer(),
    mediaTypes: Type.Array(Type.String()),
  }),
  realtime: Type.Union([
    Type.Object({ supported: Type.Literal(true), protocolVersions: Type.Array(Type.Integer()), subprotocol: Type.String() }),
    Type.Object({ supported: Type.Literal(false) }),
  ]),
  quickSchemes: Type.Object({
    supported: Type.Literal(true),
    resourceVersions: Type.Array(Type.Integer()),
    selectionSchemaVersions: Type.Array(Type.Integer()),
    materializationEnabled: Type.Boolean(),
  }),
  canvasStyle: Type.Object({
    supported: Type.Literal(true),
    operationKinds: Type.Array(Type.String()),
    styleSchemaVersions: Type.Array(Type.Integer()),
    snapshotSchemaVersion: Type.Integer(),
    defaultStyle: CanvasStyleSchema,
  }),
  relationGeometry: Type.Object({
    supported: Type.Literal(true),
    schema: Type.String(),
    selfLoop: Type.Boolean(),
    parallelEdges: Type.Boolean(),
    insertNodeTransaction: Type.Boolean(),
    endpointRewire: Type.Boolean(),
    limits: Type.Record(Type.String(), Type.Number()),
  }),
  vectorPath: Type.Object({
    supported: Type.Literal(true),
    objectType: Type.String(),
    property: Type.String(),
    schemaVersions: Type.Array(Type.Integer()),
    commandTypes: Type.Array(Type.String()),
    limits: Type.Record(Type.String(), Type.Number()),
  }),
  /** 後端接受的固定形狀代號（NODE_SHAPES，依序）；APP 缺此欄位時只能使用原有 25 種 */
  nodeShapes: Type.Array(Type.String()),
  textPlacement: Type.Object({ supported: Type.Literal(true), objectType: Type.String(), property: Type.String() }),
  sequenceDiagrams: Type.Object({
    supported: Type.Literal(true),
    schema: Type.String(),
    operationKinds: Type.Array(Type.String()),
    snapshotSchemaVersion: Type.Integer(),
    limits: Type.Record(Type.String(), Type.Integer()),
  }),
  mediaReferenceRestore: Type.Object({ supported: Type.Literal(true), operationKinds: Type.Array(Type.String()), requiresProvenance: Type.Boolean() }),
})

export const capabilitiesSchema = {
  tags: ['meta'],
  summary: '後端宣告的協定能力（未交付者為 supported=false；APP 只啟用已宣告的能力）',
  security: [],
  response: { 200: CapabilitiesSchema },
}
