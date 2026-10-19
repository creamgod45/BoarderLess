package cg.creamgod.boarderless.domain.model

import cg.creamgod.boarderless.domain.sequence.SequenceCanvasDiagram
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceId(
    val value: String,
) {
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
    val canvasStyle: CanvasStyle = CanvasStyle(),
    val canvasStyleVersion: Long = 0,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val sequenceDiagrams: Map<CanvasObjectId, SequenceCanvasDiagram> = emptyMap(),
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val sequenceDiagramsVersion: Long = 0,
) {
    init {
        require(canvasStyleVersion in 0..MaximumCanvasVersion) { "Invalid canvas style version" }
        require(sequenceDiagramsVersion in 0..MaximumCanvasVersion)
        require(sequenceDiagrams.size <= 64 && sequenceDiagrams.all { (id, diagram) -> id == diagram.containerId })
        require(version >= 0) { "workspace version must not be negative" }
    }

    fun objectById(id: CanvasObjectId): CanvasObject? = objects[id]

    fun relationById(id: RelationId): Relation? = relations[id]
}
