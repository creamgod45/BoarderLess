package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.sequence.SequenceCanvasDiagram
import kotlinx.serialization.json.*

internal fun sequenceDiagramsWire(diagrams: Map<CanvasObjectId, SequenceCanvasDiagram>): JsonArray {
    require(diagrams.size <= 64)
    val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    val json = Json { encodeDefaults = true }
    return JsonArray(diagrams.entries.sortedBy { it.key.value }.map { (id, diagram) ->
        require(id == diagram.containerId)
        require(diagram.allObjectIds().all { uuid.matches(it.value) } && diagram.allRelationIds().all { uuid.matches(it.value) })
        json.encodeToJsonElement(SequenceCanvasDiagram.serializer(), diagram.validatedSnapshot())
    })
}

internal fun sequenceDiagramsFromWire(value: JsonElement): List<SequenceCanvasDiagram> {
    val list = value as? JsonArray ?: throw BackendContractException("Sequence metadata must be an array")
    if (list.size > 64) throw BackendContractException("Sequence metadata exceeds limit")
    val diagrams = list.map { Json.decodeFromJsonElement(SequenceCanvasDiagram.serializer(), it).validatedSnapshot() }
    if (diagrams.map { it.containerId }.distinct().size != diagrams.size) throw BackendContractException("Duplicate sequence container")
    // Same UUID and semantic validation as outgoing wire; this performs no graph mutation.
    sequenceDiagramsWire(diagrams.associateBy { it.containerId })
    return diagrams
}
