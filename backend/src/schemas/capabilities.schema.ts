import { Type } from 'typebox'

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
  quickSchemes: Unsupported,
  canvasStyle: Unsupported,
  relationGeometry: Unsupported,
  vectorPath: Unsupported,
  sequenceDiagrams: Unsupported,
  mediaReferenceRestore: Unsupported,
})

export const capabilitiesSchema = {
  tags: ['meta'],
  summary: '後端宣告的協定能力（未交付者為 supported=false；APP 只啟用已宣告的能力）',
  security: [],
  response: { 200: CapabilitiesSchema },
}
