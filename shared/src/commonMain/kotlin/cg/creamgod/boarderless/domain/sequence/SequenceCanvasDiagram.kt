package cg.creamgod.boarderless.domain.sequence

import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.Serializable

@Serializable data class SequenceParticipantBinding(
    val participant: SequenceParticipant,
    val objectId: CanvasObjectId,
)

@Serializable data class SequenceStepPosition(
    val blockId: String? = null,
    val branchIndex: Int = 0,
    val order: Int,
) {
    init {
        blockId?.let(::requireSequenceId)
        require(branchIndex in 0..15 && order in 0..511)
        require(blockId != null || branchIndex == 0)
    }
}

@Serializable data class SequenceMessageBinding(
    val message: SequenceMessage,
    val relationId: RelationId,
    val position: SequenceStepPosition,
)

@Serializable data class SequenceBlockBinding(
    val id: String,
    val kind: SequenceBlockKind,
    val branchLabels: List<String>,
    val objectId: CanvasObjectId,
    val position: SequenceStepPosition,
) {
    init {
        requireSequenceId(id)
        require(branchLabels.size in 1..16)
        branchLabels.forEach(::requireSequenceText)
    }
}

/** Flat durable metadata preserves explicit tree order and canvas identity without recursive JSON
 * depth growing with semantic nesting. The Mermaid source is never the authoritative state. */
@Serializable data class SequenceCanvasDiagram(
    val containerId: CanvasObjectId,
    val participants: List<SequenceParticipantBinding>,
    val messages: List<SequenceMessageBinding>,
    val blocks: List<SequenceBlockBinding> = emptyList(),
    val schema: String = "boarderless.sequence-canvas.v1",
) {
    init {
        require(schema == "boarderless.sequence-canvas.v1")
        require(participants.size in 1..32 && messages.size in 1..512 && blocks.size <= 128)
        require((allObjectIds().map { it.value } + allRelationIds().map { it.value }).all { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) })
        require(allObjectIds().size == 1 + participants.size + blocks.size)
        require(messages.map { it.relationId }.distinct().size == messages.size)
        toDraft()
    }

    fun allObjectIds(): Set<CanvasObjectId> = setOf(containerId) + participants.map { it.objectId } + blocks.map { it.objectId }

    fun allRelationIds(): Set<RelationId> = messages.mapTo(mutableSetOf()) { it.relationId }

    fun toDraft(): SequenceDiagramDraft {
        val blockById = blocks.associateBy { it.id }
        require(blockById.size == blocks.size)
        require((messages.map { it.message.id } + blocks.map { it.id }).distinct().size == messages.size + blocks.size)

        fun check(position: SequenceStepPosition) {
            if (position.blockId != null) {
                val parent = requireNotNull(blockById[position.blockId]) { "Missing containing block" }
                require(position.branchIndex in parent.branchLabels.indices)
            }
        }
        messages.forEach { check(it.position) }
        blocks.forEach { check(it.position) }
        val messageGroups = messages.groupBy { it.position.blockId to it.position.branchIndex }
        val blockGroups = blocks.groupBy { it.position.blockId to it.position.branchIndex }
        val usedMessages = mutableSetOf<String>()
        val usedBlocks = mutableSetOf<String>()

        fun items(
            parent: String?,
            branch: Int,
            depth: Int,
        ): List<SequenceStep> {
            require(depth <= 16) { "Sequence nesting limit" }
            val key = parent to branch
            val ordered = mutableListOf<Pair<Int, SequenceStep>>()
            messageGroups[key].orEmpty().forEach { binding ->
                require(usedMessages.add(binding.message.id))
                ordered.add(binding.position.order to binding.message)
            }
            blockGroups[key].orEmpty().forEach { binding ->
                require(usedBlocks.add(binding.id)) { "Sequence block cycle" }
                val children =
                    binding.branchLabels.mapIndexed {
                        index,
                        label,
                        ->
                        SequenceBranch(label, items(binding.id, index, depth + 1))
                    }
                ordered.add(binding.position.order to SequenceBlock(binding.id, binding.kind, children))
            }
            val sorted = ordered.sortedBy { it.first }
            require(sorted.map { it.first } == sorted.indices.toList()) { "Sequence order must be complete and unique" }
            return sorted.map { it.second }
        }
        val root = items(null, 0, 0)
        require(usedMessages.size == messages.size && usedBlocks.size == blocks.size) { "Unreachable sequence steps" }
        return SequenceDiagramDraft(participants.map { it.participant }, root).validatedSnapshot()
    }

    fun validatedSnapshot(): SequenceCanvasDiagram {
        toDraft()
        return copy(
            participants = participants.map { it.copy(participant = it.participant.copy()) },
            messages = messages.map { it.copy(message = it.message.copy(), position = it.position.copy()) },
            blocks = blocks.map { it.copy(branchLabels = it.branchLabels.toList(), position = it.position.copy()) },
        )
    }

    companion object {
        fun bind(
            source: SequenceDiagramDraft,
            containerId: CanvasObjectId,
            participantIds: Map<String, CanvasObjectId>,
            messageIds: Map<String, RelationId>,
            blockIds: Map<String, CanvasObjectId>,
        ): SequenceCanvasDiagram {
            val draft = source.validatedSnapshot()
            val messages = mutableListOf<SequenceMessageBinding>()
            val blocks = mutableListOf<SequenceBlockBinding>()

            fun visit(
                steps: List<SequenceStep>,
                parent: String?,
                branch: Int,
            ) {
                steps.forEachIndexed { index, step ->
                    val position = SequenceStepPosition(parent, branch, index)
                    when (step) {
                        is SequenceMessage -> {
                            messages.add(SequenceMessageBinding(step, messageIds.getValue(step.id), position))
                        }

                        is SequenceBlock -> {
                            blocks.add(
                                SequenceBlockBinding(
                                    step.id,
                                    step.kind,
                                    step.branches.map { it.label },
                                    blockIds.getValue(step.id),
                                    position,
                                ),
                            )
                            step.branches.forEachIndexed { n, child -> visit(child.steps, step.id, n) }
                        }
                    }
                }
            }
            visit(draft.steps, null, 0)
            require(participantIds.keys == draft.participants.map { it.id }.toSet())
            require(messageIds.keys == messages.map { it.message.id }.toSet())
            require(blockIds.keys == blocks.map { it.id }.toSet())
            return SequenceCanvasDiagram(
                containerId,
                draft.participants.map {
                    SequenceParticipantBinding(it, participantIds.getValue(it.id))
                },
                messages,
                blocks,
            )
        }
    }
}

/** Validate the whole published graph; ordinary operations cannot silently orphan metadata.
 * Object/relation versions are not duplicated here, so CAS/history restoration may advance them. */
fun Workspace.hasValidSequenceBindings(): Boolean =
    runCatching {
        require(sequenceDiagrams.size <= 64)
        val ownedObjects = mutableSetOf<CanvasObjectId>()
        val ownedRelations = mutableSetOf<RelationId>()
        for ((key, raw) in sequenceDiagrams) {
            val diagram = raw.validatedSnapshot()
            require(key == diagram.containerId)
            require(diagram.allObjectIds().all { ownedObjects.add(it) })
            require(diagram.allRelationIds().all { ownedRelations.add(it) })
            val container = objects[key] as? GroupFrame ?: error("Missing sequence container")
            require(container.id == key)
            var parent = container.parentId
            val ancestors = mutableSetOf(key)
            while (parent != null) {
                require(ancestors.add(parent)) { "Sequence ancestor cycle" }
                parent = (objects[parent] as? GroupFrame ?: error("Missing sequence ancestor")).parentId
            }
            require(container.transform.size.width > 0f && container.transform.size.height > 0f)
            val participants = diagram.participants.associate { it.participant.id to it.objectId }
            diagram.participants.forEach { binding ->
                val node = objects[binding.objectId] as? TextNode ?: error("Missing sequence participant")
                require(
                    node.id == binding.objectId && node.parentId == key && node.text == binding.participant.label && node.vectorPath == null,
                )
                require(node.transform.size.width > 0f && node.transform.size.height > 0f)
            }
            diagram.blocks.forEach { binding ->
                val block = objects[binding.objectId] as? GroupFrame ?: error("Missing sequence block")
                require(
                    block.id == binding.objectId && block.parentId == key && block.transform.size.width > 0f &&
                        block.transform.size.height > 0f,
                )
            }
            diagram.messages.forEach { binding ->
                val relation = relations[binding.relationId] ?: error("Missing sequence message")
                require(relation.id == binding.relationId)
                require(relation.sourceObjectId == participants.getValue(binding.message.sourceId))
                require(relation.targetObjectId == participants.getValue(binding.message.targetId))
                require(
                    relation.direction ==
                        if (binding.message.kind == SequenceMessageKind.Signal) RelationDirection.None else RelationDirection.Forward,
                )
                require(relation.label == binding.message.text)
                require((relation.geometry?.route is RelationRoute.Loop) == binding.message.isSelfMessage)
            }
        }
    }.isSuccess
