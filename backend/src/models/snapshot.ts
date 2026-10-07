import type { CanvasObject, Relation } from './canvas.ts'

export interface WorkspaceSnapshot {
  workspaceId: string
  throughServerSeq: number
  schemaVersion: number
  snapshot: SnapshotContent | null
  storageKey: string | null
  checksum: string
  createdAt: Date
}

export interface SnapshotContent {
  workspaceId: string
  workspaceVersion: number
  throughServerSeq: number
  objects: CanvasObject[]
  relations: Relation[]
}
