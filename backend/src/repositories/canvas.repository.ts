import { json, type Db } from '../db/client.ts'
import type { CanvasObject, JsonObject, Relation, RelationDirection } from '../models/canvas.ts'

export interface NewCanvasObject {
  objectId: string
  objectType: string
  parentId: string | null
  zIndex: number
  locked: boolean
  transform: JsonObject
  properties: JsonObject
}

/** undefined 表示不修改；parentId 可明確設為 null */
export interface CanvasObjectPatch {
  parentId?: string | null
  zIndex?: number
  locked?: boolean
  transform?: JsonObject
  /** 淺層 merge 到既有 properties */
  properties?: JsonObject
}

export interface NewRelation {
  relationId: string
  sourceObjectId: string
  targetObjectId: string
  direction: RelationDirection
  intent: string | null
  label: string | null
  style: JsonObject
}

export interface RelationPatch {
  direction?: RelationDirection
  intent?: string | null
  label?: string | null
  /** 淺層 merge 到既有 style */
  style?: JsonObject
}

/** 目前 projection（canvas_objects / relations）的讀寫 */
export class CanvasRepository {
  constructor(private readonly db: Db) {}

  // ---- objects ----

  async findObjects(workspaceId: string, objectIds: string[]): Promise<CanvasObject[]> {
    if (objectIds.length === 0) return []
    return this.db<CanvasObject[]>`
      SELECT * FROM canvas_objects
      WHERE workspace_id = ${workspaceId} AND object_id = ANY(${objectIds}::uuid[])
    `
  }

  async listActiveObjects(workspaceId: string): Promise<CanvasObject[]> {
    return this.db<CanvasObject[]>`
      SELECT * FROM canvas_objects
      WHERE workspace_id = ${workspaceId} AND deleted_at IS NULL
      ORDER BY z_index, created_at
    `
  }

  async insertObject(workspaceId: string, actorId: string, obj: NewCanvasObject): Promise<CanvasObject> {
    const [row] = await this.db<CanvasObject[]>`
      INSERT INTO canvas_objects (
        workspace_id, object_id, object_type, parent_id, z_index, locked,
        transform, properties, created_by, updated_by
      ) VALUES (
        ${workspaceId}, ${obj.objectId}, ${obj.objectType}, ${obj.parentId}, ${obj.zIndex}, ${obj.locked},
        ${json(this.db, obj.transform)}, ${json(this.db, obj.properties)}, ${actorId}, ${actorId}
      )
      RETURNING *
    `
    return row!
  }

  async updateObject(
    workspaceId: string,
    objectId: string,
    actorId: string,
    patch: CanvasObjectPatch,
  ): Promise<CanvasObject | null> {
    const [row] = await this.db<CanvasObject[]>`
      UPDATE canvas_objects SET
        parent_id  = CASE WHEN ${patch.parentId !== undefined} THEN ${patch.parentId ?? null}::uuid ELSE parent_id END,
        z_index    = COALESCE(${patch.zIndex ?? null}::int, z_index),
        locked     = COALESCE(${patch.locked ?? null}::boolean, locked),
        transform  = CASE WHEN ${patch.transform !== undefined} THEN ${json(this.db, patch.transform ?? {})}::jsonb ELSE transform END,
        properties = properties || ${json(this.db, patch.properties ?? {})}::jsonb,
        object_version = object_version + 1,
        updated_by = ${actorId},
        updated_at = now()
      WHERE workspace_id = ${workspaceId} AND object_id = ${objectId} AND deleted_at IS NULL
      RETURNING *
    `
    return row ?? null
  }

  async softDeleteObjects(workspaceId: string, objectIds: string[], actorId: string): Promise<string[]> {
    const rows = await this.db<{ objectId: string }[]>`
      UPDATE canvas_objects
      SET deleted_at = now(), updated_at = now(), updated_by = ${actorId}, object_version = object_version + 1
      WHERE workspace_id = ${workspaceId} AND object_id = ANY(${objectIds}::uuid[]) AND deleted_at IS NULL
      RETURNING object_id
    `
    return rows.map((r) => r.objectId)
  }

  // ---- relations ----

  async findRelations(workspaceId: string, relationIds: string[]): Promise<Relation[]> {
    if (relationIds.length === 0) return []
    return this.db<Relation[]>`
      SELECT * FROM relations
      WHERE workspace_id = ${workspaceId} AND relation_id = ANY(${relationIds}::uuid[])
    `
  }

  async listActiveRelations(workspaceId: string): Promise<Relation[]> {
    return this.db<Relation[]>`
      SELECT * FROM relations
      WHERE workspace_id = ${workspaceId} AND deleted_at IS NULL
      ORDER BY created_at
    `
  }

  async insertRelation(workspaceId: string, rel: NewRelation): Promise<Relation> {
    const [row] = await this.db<Relation[]>`
      INSERT INTO relations (
        workspace_id, relation_id, source_object_id, target_object_id, direction, intent, label, style
      ) VALUES (
        ${workspaceId}, ${rel.relationId}, ${rel.sourceObjectId}, ${rel.targetObjectId},
        ${rel.direction}, ${rel.intent}, ${rel.label}, ${json(this.db, rel.style)}
      )
      RETURNING *
    `
    return row!
  }

  async updateRelation(workspaceId: string, relationId: string, patch: RelationPatch): Promise<Relation | null> {
    const [row] = await this.db<Relation[]>`
      UPDATE relations SET
        direction = COALESCE(${patch.direction ?? null}::text, direction),
        intent    = CASE WHEN ${patch.intent !== undefined} THEN ${patch.intent ?? null}::text ELSE intent END,
        label     = CASE WHEN ${patch.label !== undefined} THEN ${patch.label ?? null}::text ELSE label END,
        style     = style || ${json(this.db, patch.style ?? {})}::jsonb,
        relation_version = relation_version + 1,
        updated_at = now()
      WHERE workspace_id = ${workspaceId} AND relation_id = ${relationId} AND deleted_at IS NULL
      RETURNING *
    `
    return row ?? null
  }

  async softDeleteRelations(workspaceId: string, relationIds: string[]): Promise<string[]> {
    const rows = await this.db<{ relationId: string }[]>`
      UPDATE relations
      SET deleted_at = now(), updated_at = now(), relation_version = relation_version + 1
      WHERE workspace_id = ${workspaceId} AND relation_id = ANY(${relationIds}::uuid[]) AND deleted_at IS NULL
      RETURNING relation_id
    `
    return rows.map((r) => r.relationId)
  }

  /** 刪除 Node 時一併刪除連到它的 relation，避免半連線（§5.1 relations） */
  async softDeleteRelationsTouching(workspaceId: string, objectIds: string[]): Promise<string[]> {
    const rows = await this.db<{ relationId: string }[]>`
      UPDATE relations
      SET deleted_at = now(), updated_at = now(), relation_version = relation_version + 1
      WHERE workspace_id = ${workspaceId}
        AND deleted_at IS NULL
        AND (source_object_id = ANY(${objectIds}::uuid[]) OR target_object_id = ANY(${objectIds}::uuid[]))
      RETURNING relation_id
    `
    return rows.map((r) => r.relationId)
  }
}
