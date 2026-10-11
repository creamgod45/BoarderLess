import { Type } from 'typebox'
import { CanvasStyleSchema } from '../domain/canvas-style.ts'
import { DateTime, errorResponses, JsonObject, Nullable, WorkspaceParams } from './common.schema.ts'

export const CanvasObjectSchema = Type.Object({
  objectId: Type.String(),
  objectType: Type.String(),
  objectVersion: Type.Integer(),
  parentId: Nullable(Type.String()),
  zIndex: Type.Integer(),
  locked: Type.Boolean(),
  transform: JsonObject,
  properties: JsonObject,
  createdBy: Type.String(),
  updatedBy: Type.String(),
  createdAt: DateTime,
  updatedAt: DateTime,
})

export const RelationSchema = Type.Object({
  relationId: Type.String(),
  relationVersion: Type.Integer(),
  sourceObjectId: Type.String(),
  targetObjectId: Type.String(),
  direction: Type.String(),
  intent: Nullable(Type.String()),
  label: Nullable(Type.String()),
  style: JsonObject,
  createdAt: DateTime,
  updatedAt: DateTime,
})

export const SnapshotContentSchema = Type.Object({
  workspaceId: Type.String(),
  workspaceVersion: Type.Integer(),
  throughServerSeq: Type.Integer(),
  objects: Type.Array(CanvasObjectSchema),
  relations: Type.Array(RelationSchema),
  /** 與 objects / relations 同一 throughServerSeq 的共用背景（snapshot schema 2 起） */
  canvasStyle: CanvasStyleSchema,
  canvasStyleVersion: Type.Integer({ minimum: 0 }),
})

/** snapshot schema 1（批次 C 之前建立）：沒有 canvasStyle，定義上等於 default / 0；原樣回傳，不改寫已簽 checksum 的內容 */
export const SnapshotContentV1Schema = Type.Object({
  workspaceId: Type.String(),
  workspaceVersion: Type.Integer(),
  throughServerSeq: Type.Integer(),
  objects: Type.Array(CanvasObjectSchema),
  relations: Type.Array(RelationSchema),
})

export const SnapshotSchema = Type.Object({
  workspaceId: Type.String(),
  throughServerSeq: Type.Integer(),
  schemaVersion: Type.Integer({ description: 'snapshot content schema：1 = 無 canvasStyle（視為 default / 0），2 = 含 canvasStyle' }),
  snapshot: Nullable(Type.Union([SnapshotContentSchema, SnapshotContentV1Schema])),
  storageKey: Nullable(Type.String()),
  checksum: Type.String(),
  createdAt: DateTime,
})

export const getStateSchema = {
  tags: ['snapshots'],
  summary: '目前 Workspace 狀態（projection）',
  params: WorkspaceParams,
  response: { 200: SnapshotContentSchema, ...errorResponses },
}

export const createSnapshotSchema = {
  tags: ['snapshots'],
  summary: '建立 checkpoint snapshot',
  params: WorkspaceParams,
  response: { 201: SnapshotSchema, ...errorResponses },
}

export const latestSnapshotSchema = {
  tags: ['snapshots'],
  summary: '最新 snapshot',
  params: WorkspaceParams,
  response: { 200: SnapshotSchema, ...errorResponses },
}
