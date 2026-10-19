package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*

/** Semantic edit preserves matching participant/block identities and unchanged endpoints.
 * Changed message endpoints use a new relation, not unsupported endpoint rewiring.
 * The complete graph and flat semantics are published in one reversible transaction. */
internal class SequenceCanvasEditing(owner: WorkspaceSession, private val baseline: Workspace,
    val containerId: CanvasObjectId) {
    private val original = requireNotNull(baseline.sequenceDiagrams[containerId]).validatedSnapshot()
    private val container = baseline.objects[containerId] as GroupFrame
    private val capture = PenCanvasCreation(owner, baseline, container.transform.position)
    val initialDraft = original.toDraft()
    fun isCurrent(session: WorkspaceSession?, live: Workspace) = capture.isCurrent(session, live)
    fun isUnchanged(draft: SequenceDiagramDraft) = draft.validatedSnapshot() == initialDraft

    private fun unlocked(id: CanvasObjectId): Boolean {
        val seen = mutableSetOf<CanvasObjectId>()
        var current: CanvasObjectId? = id
        while (current != null) {
            require(seen.add(current)) { "Sequence ancestor cycle" }
            val obj = requireNotNull(baseline.objects[current])
            if (obj.locked) return false
            current = obj.parentId
        }
        return true
    }

    fun operation(draft: SequenceDiagramDraft, layout: SequenceDiagramLayout, newId: () -> String): TransactionOperation {
        require(baseline.sequenceDiagramsVersion < MaximumCanvasVersion - 1)
        require(unlocked(containerId)) { "Sequence container or ancestor is locked" }
        val creationBase = baseline.copy(sequenceDiagrams = baseline.sequenceDiagrams - containerId)
        val creation = SequenceCanvasCreation(capture.owner.copy(workspace = creationBase), creationBase, container.transform.position)
            .operation(draft, container.title.ifBlank { "Sequence" }, layout, newId)
        val freshObjects = (creation.operations[0] as CreateObjectsOperation).objects
        val freshRelations = (creation.operations[1] as CreateRelationsOperation).relations
        val fresh = (creation.operations[2] as UpdateSequenceDiagramsOperation).after.values.single { it.containerId !in creationBase.sequenceDiagrams }
        val objectIds = mutableMapOf(fresh.containerId to containerId)
        fresh.participants.forEach { binding ->
            objectIds[binding.objectId] = original.participants.singleOrNull { it.participant.id == binding.participant.id }?.objectId ?: binding.objectId
        }
        fresh.blocks.forEach { binding ->
            objectIds[binding.objectId] = original.blocks.singleOrNull { it.id == binding.id }?.objectId ?: binding.objectId
        }
        val relationIds = fresh.messages.associate { binding ->
            val candidate = original.messages.singleOrNull { it.message.id == binding.message.id }
            val generated = freshRelations.single { it.id == binding.relationId }
            val old = candidate?.let { baseline.relations[it.relationId] }
            binding.relationId to if (old != null && old.sourceObjectId == objectIds.getValue(generated.sourceObjectId) &&
                old.targetObjectId == objectIds.getValue(generated.targetObjectId)) old.id else binding.relationId
        }
        val desired = fresh.copy(containerId = containerId,
            participants = fresh.participants.map { it.copy(objectId = objectIds.getValue(it.objectId)) },
            blocks = fresh.blocks.map { it.copy(objectId = objectIds.getValue(it.objectId)) },
            messages = fresh.messages.map { it.copy(relationId = relationIds.getValue(it.relationId)) })
        val objects = freshObjects.map { generated ->
            val id = objectIds.getValue(generated.id)
            val old = baseline.objects[id]
            val transform = generated.transform.copy(rotationDegrees = old?.transform?.rotationDegrees ?: generated.transform.rotationDegrees)
            when (generated) {
                is TextNode -> (old as? TextNode)?.copy(text = generated.text, transform = transform)
                    ?: generated.copy(id = id, parentId = containerId)
                is GroupFrame -> (old as? GroupFrame)?.copy(title = if (id == containerId) old.title else generated.title, transform = transform)
                    ?: generated.copy(id = id, parentId = containerId)
                else -> error("Unsupported sequence object")
            }
        }
        val relations = freshRelations.map { generated ->
            val id = relationIds.getValue(generated.id)
            baseline.relations[id]?.copy(label = generated.label, direction = generated.direction)
                ?: generated.copy(id = id, sourceObjectId = objectIds.getValue(generated.sourceObjectId),
                    targetObjectId = objectIds.getValue(generated.targetObjectId))
        }
        val removedObjects = original.allObjectIds() - desired.allObjectIds()
        val removedRelations = original.allRelationIds() - desired.allRelationIds()
        val changedObjects = removedObjects + objects.filter { it.id in baseline.objects && it != baseline.objects[it.id] }.map { it.id }
        require(changedObjects.all(::unlocked)) { "Changed sequence object is locked" }
        original.participants.filter { binding ->
            draft.participants.singleOrNull { it.id == binding.participant.id } != binding.participant
        }.forEach { require(unlocked(it.objectId)) { "Changed participant is locked" } }
        fun blockSteps(source: SequenceDiagramDraft): Map<String, SequenceBlock> = buildMap {
            fun visit(steps: List<SequenceStep>) {
                steps.filterIsInstance<SequenceBlock>().forEach { block ->
                    put(block.id, block); block.branches.forEach { visit(it.steps) }
                }
            }
            visit(source.steps)
        }
        val oldBlocks = blockSteps(initialDraft)
        val newBlocks = blockSteps(draft)
        original.blocks.filter { oldBlocks[it.id] != newBlocks[it.id] }.forEach {
            require(unlocked(it.objectId)) { "Changed sequence block is locked" }
        }
        val changedRelations = removedRelations + relations.filter { it.id in baseline.relations && it != baseline.relations[it.id] }.map { it.id }
        changedRelations.forEach { id ->
            val old = baseline.relations.getValue(id)
            require(unlocked(old.sourceObjectId) && unlocked(old.targetObjectId)) { "Changed message endpoint is locked" }
        }
        original.messages.filter { old -> desired.messages.singleOrNull { it.message.id == old.message.id } != old }.forEach {
            val relation = baseline.relations.getValue(it.relationId)
            require(unlocked(relation.sourceObjectId) && unlocked(relation.targetObjectId)) { "Changed message semantics are locked" }
        }
        relations.filter { it.id !in baseline.relations }.forEach { relation ->
            listOf(relation.sourceObjectId, relation.targetObjectId).filter { it in baseline.objects }.forEach {
                require(unlocked(it)) { "New message endpoint is locked" }
            }
        }
        // Ordinary objects/relations outside this diagram are never cascaded by an edit.
        require(baseline.objects.values.none { it.id !in original.allObjectIds() && it.parentId in removedObjects })
        require(baseline.relations.values.none { it.id !in original.allRelationIds() &&
            (it.sourceObjectId in removedObjects || it.targetObjectId in removedObjects) })
        var simulated = baseline
        val operations = mutableListOf<WorkspaceOperation>()
        fun append(operation: WorkspaceOperation) {
            val applied = operation.applyInTransaction(simulated)
            require(applied is OperationResult.Applied) { "Sequence edit cannot be applied" }
            simulated = applied.workspace
            operations += operation
        }
        append(UpdateSequenceDiagramsOperation(newId(), simulated.sequenceDiagramsVersion, simulated.sequenceDiagrams,
            simulated.sequenceDiagrams - containerId))
        if (removedRelations.isNotEmpty()) append(DeleteRelationsOperation(newId(), removedRelations.map { baseline.relations.getValue(it) }))
        if (removedObjects.isNotEmpty()) append(DeleteObjectsOperation(newId(), removedObjects.map { baseline.objects.getValue(it) }))
        val additions = objects.filter { it.id !in baseline.objects }
        if (additions.isNotEmpty()) append(CreateObjectsOperation(newId(), additions))
        val retained = objects.filter { it.id in baseline.objects }
        val transforms = retained.mapNotNull { wanted ->
            val old = simulated.objects.getValue(wanted.id)
            if (old.transform == wanted.transform) null else TransformChange(old.id, old.version, old.transform, wanted.transform)
        }
        if (transforms.isNotEmpty()) append(TransformObjectsOperation(newId(), transforms))
        val texts = retained.filterIsInstance<TextNode>().mapNotNull { wanted ->
            val old = simulated.objects.getValue(wanted.id) as TextNode
            if (old.text == wanted.text) null else TextChange(old.id, old.version, old.text, wanted.text)
        }
        if (texts.isNotEmpty()) append(EditTextOperation(newId(), texts))
        val groups = retained.filterIsInstance<GroupFrame>().mapNotNull { wanted ->
            val old = simulated.objects.getValue(wanted.id) as GroupFrame
            if (old.title == wanted.title) null else {
                val before = GroupFrameAttributes(old.zIndex, old.locked, old.colorToken, old.title)
                GroupFrameAttributesChange(old.id, old.version, before, before.copy(title = wanted.title))
            }
        }
        if (groups.isNotEmpty()) append(UpdateGroupFrameAttributesOperation(newId(), groups))
        val newRelations = relations.filter { it.id !in baseline.relations }
        if (newRelations.isNotEmpty()) append(CreateRelationsOperation(newId(), newRelations))
        relations.filter { it.id in baseline.relations }.forEach { wanted ->
            val old = simulated.relations.getValue(wanted.id)
            val before = RelationAttributes(old.direction, old.intent, old.label, old.colorToken, old.geometry)
            val after = before.copy(direction = wanted.direction, label = wanted.label)
            if (before != after) append(UpdateRelationAttributesOperation(newId(),
                listOf(RelationAttributesChange(old.id, old.version, before, after))))
        }
        append(UpdateSequenceDiagramsOperation(newId(), simulated.sequenceDiagramsVersion, simulated.sequenceDiagrams,
            simulated.sequenceDiagrams + (containerId to desired)))
        val transaction = TransactionOperation(newId(), operations)
        require(transaction.applyTo(baseline) is OperationResult.Applied)
        return transaction
    }
}
