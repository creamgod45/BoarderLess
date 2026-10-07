package cg.creamgod.boarderless.domain.ai

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import kotlinx.serialization.json.*

/** Only approved aliases leave the APP. Partial scope never expands to hidden sequence text. */
internal fun aiSequenceContexts(
    workspace: Workspace,
    nodes: Map<CanvasObjectId, String>,
    edges: Map<RelationId, String>,
): JsonArray {
    require(workspace.hasValidSequenceBindings())
    return JsonArray(workspace.sequenceDiagrams.values.sortedBy { it.containerId.value }
        .filter { diagram -> diagram.allObjectIds().any { it in nodes } }
        .mapIndexed { index, diagram ->
            val complete = diagram.allObjectIds().all { it in nodes } && diagram.allRelationIds().all { it in edges }
            val blockNames = diagram.blocks.mapIndexed { n, block -> block.id to "S${index + 1}_B${n + 1}" }.toMap()
            fun position(position: SequenceStepPosition) = buildJsonObject {
                put("parentBlock", position.blockId?.let { JsonPrimitive(blockNames.getValue(it)) } ?: JsonNull)
                put("branch", position.branchIndex)
                put("order", position.order)
            }
            buildJsonObject {
                put("id", "S${index + 1}")
                put("complete", complete)
                put("readOnly", true)
                put("container", nodes[diagram.containerId]?.let(::JsonPrimitive) ?: JsonNull)
                put("objectIds", JsonArray(diagram.allObjectIds().mapNotNull { nodes[it] }.sorted().map(::JsonPrimitive)))
                if (complete) {
                    val participantNames = diagram.participants.associate { it.participant.id to nodes.getValue(it.objectId) }
                    put("participants", JsonArray(diagram.participants.map { binding -> buildJsonObject {
                        put("id", nodes.getValue(binding.objectId))
                        put("role", binding.participant.role.name)
                        put("label", binding.participant.label)
                    } }))
                    put("messages", JsonArray(diagram.messages.map { binding -> buildJsonObject {
                        put("id", edges.getValue(binding.relationId))
                        put("source", participantNames.getValue(binding.message.sourceId))
                        put("target", participantNames.getValue(binding.message.targetId))
                        put("kind", binding.message.kind.name)
                        put("dashed", binding.message.dashed)
                        put("label", binding.message.text)
                        put("position", position(binding.position))
                    } }))
                    put("blocks", JsonArray(diagram.blocks.map { binding -> buildJsonObject {
                        put("id", blockNames.getValue(binding.id))
                        put("objectId", nodes.getValue(binding.objectId))
                        put("kind", binding.kind.name)
                        put("branchLabels", JsonArray(binding.branchLabels.map(::JsonPrimitive)))
                        put("position", position(binding.position))
                    } }))
                }
            }
        })
}

internal fun Workspace.aiSequenceManagedIds(): Set<CanvasObjectId> =
    sequenceDiagrams.values.flatMap { it.allObjectIds() }.toSet()

internal fun Workspace.aiSequenceProtected(id: CanvasObjectId): Boolean {
    val managed = aiSequenceManagedIds()
    var current: CanvasObjectId? = id
    val seen = mutableSetOf<CanvasObjectId>()
    while (current != null && seen.add(current)) {
        if (current in managed) return true
        current = objects[current]?.parentId
    }
    return false
}

/** Independent final gate also covers proposals constructed outside the tool executor. */
internal fun aiSequenceUnchanged(before: Workspace, after: Workspace): Boolean {
    if (before.sequenceDiagrams != after.sequenceDiagrams || before.sequenceDiagramsVersion != after.sequenceDiagramsVersion) return false
    val protected = before.aiSequenceManagedIds().toMutableSet()
    before.aiSequenceManagedIds().forEach { id ->
        var parent = before.objects[id]?.parentId
        val seen = mutableSetOf<CanvasObjectId>()
        while (parent != null && seen.add(parent)) {
            protected.add(parent)
            parent = before.objects[parent]?.parentId
        }
    }
    if (protected.any { before.objects[it] != after.objects[it] }) return false
    if ((before.objects.keys + after.objects.keys).any { id ->
        (before.aiSequenceProtected(id) || after.aiSequenceProtected(id)) && before.objects[id] != after.objects[id]
    }) return false
    return (before.relations.keys + after.relations.keys).all { id ->
        val old = before.relations[id]
        val new = after.relations[id]
        val touches = listOfNotNull(old, new).any {
            before.aiSequenceProtected(it.sourceObjectId) || before.aiSequenceProtected(it.targetObjectId) ||
                after.aiSequenceProtected(it.sourceObjectId) || after.aiSequenceProtected(it.targetObjectId)
        }
        !touches || old == new
    }
}
