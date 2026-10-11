import { Type } from 'typebox'
import { DateTime } from './common.schema.ts'

const Binding = {
  assetId: Type.String(),
  sourceWorkspaceId: Type.String(),
  kind: Type.Union([Type.Literal('original'), Type.Literal('thumbnail')]),
}

/** 方案資源（resourceVersion 2）；payload 是 boarderless/selection 物件（不是字串） */
export const QuickSchemeResourceSchema = Type.Object({
  id: Type.String(),
  ownerId: Type.String(),
  resourceVersion: Type.Literal(2),
  name: Type.String(),
  schemaVersion: Type.Integer({ minimum: 1, maximum: 9 }),
  payload: Type.Record(Type.String(), Type.Unknown()),
  assetBindings: Type.Array(Type.Object(Binding)),
  revision: Type.Integer({ minimum: 1 }),
  createdAt: DateTime,
  updatedAt: DateTime,
  etag: Type.String({ description: '與 HTTP ETag 相同的 opaque strong validator（含雙引號）' }),
  assetAvailability: Type.Array(
    Type.Object({
      ...Binding,
      status: Type.Union([Type.Literal('available'), Type.Literal('unavailable'), Type.Literal('access_denied')]),
    }),
    { description: '讀取時計算的素材狀態；不是授權、不含 URL' },
  ),
})

export const QuickSchemeListSchema = Type.Object({
  items: Type.Array(QuickSchemeResourceSchema),
  nextCursor: Type.Union([Type.String(), Type.Null()]),
  collectionRevision: Type.Integer(),
})

export const QuickSchemeCapabilitiesSchema = Type.Object({
  resourceVersions: Type.Array(Type.Integer()),
  selectionSchemaVersions: Type.Array(Type.Integer()),
  limits: Type.Object({
    maxRequestBytes: Type.Integer(),
    maxObjects: Type.Integer(),
    maxRelations: Type.Integer(),
    maxSequenceDiagrams: Type.Integer(),
    maxSchemesPerUser: Type.Integer(),
    nameMaxScalars: Type.Integer(),
    pageLimitMax: Type.Integer(),
    pageLimitDefault: Type.Integer(),
    maxPageBytes: Type.Integer(),
  }),
  idempotencyKeyTtlSeconds: Type.Integer(),
  cursorTtlSeconds: Type.Integer(),
  materializationEnabled: Type.Boolean(),
})
