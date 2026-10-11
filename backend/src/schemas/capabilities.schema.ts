import { Type } from 'typebox'
import { CanvasStyleSchema } from '../domain/canvas-style.ts'

const Unsupported = Type.Object({ supported: Type.Literal(false) })

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
    Unsupported,
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
  textPlacement: Type.Object({ supported: Type.Literal(true), objectType: Type.String(), property: Type.String() }),
  sequenceDiagrams: Unsupported,
  mediaReferenceRestore: Unsupported,
})

export const capabilitiesSchema = {
  tags: ['meta'],
  summary: '後端宣告的協定能力（未交付者為 supported=false；APP 只啟用已宣告的能力）',
  security: [],
  response: { 200: CapabilitiesSchema },
}
