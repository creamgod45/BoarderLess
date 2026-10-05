package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceId(val value: String) {
    init {
        require(value.isNotBlank()) { "Workspace id must not be blank" }
    }
}

@Serializable
data class Workspace(
    val id: WorkspaceId,
    val title: String,
    val version: Long = 0,
    val objects: Map<CanvasObjectId, CanvasObject> = emptyMap(),
    val relations: Map<RelationId, Relation> = emptyMap(),
) {
    init {
        require(version >= 0) { "workspace version must not be negative" }
    }

    fun objectById(id: CanvasObjectId): CanvasObject? = objects[id]

    fun relationById(id: RelationId): Relation? = relations[id]
}
