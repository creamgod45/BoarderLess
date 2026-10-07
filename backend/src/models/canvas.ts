export type JsonObject = Record<string, unknown>

export interface CanvasObject {
  workspaceId: string
  objectId: string
  objectType: string
  objectVersion: number
  parentId: string | null
  zIndex: number
  locked: boolean
  transform: JsonObject
  properties: JsonObject
  createdBy: string
  updatedBy: string
  createdAt: Date
  updatedAt: Date
  deletedAt: Date | null
}

export const RELATION_DIRECTIONS = ['none', 'forward', 'backward', 'both'] as const
export type RelationDirection = (typeof RELATION_DIRECTIONS)[number]

export interface Relation {
  workspaceId: string
  relationId: string
  relationVersion: number
  sourceObjectId: string
  targetObjectId: string
  direction: RelationDirection
  intent: string | null
  label: string | null
  style: JsonObject
  createdAt: Date
  updatedAt: Date
  deletedAt: Date | null
}
