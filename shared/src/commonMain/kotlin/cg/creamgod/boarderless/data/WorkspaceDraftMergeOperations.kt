package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*

/** Pure compilation, NOT submission/authorization. Caller still needs fresh ACL, media validation,
 * unconfirmed-submission reconciliation and confirmation. Never replay imported operation IDs.
 */
internal fun buildDraftMergeOperation(plan: WorkspaceDraftMergePlan,
    choices: Map<DraftMergeFieldId, DraftMergeChoice>, current: Workspace,
    newId: () -> String = ::randomUuid): TransactionOperation? {
    require(current == plan.review.current) { "Merge snapshot changed; review again" }
    val target = plan.resolve(choices)
    var state = current
    val commands = mutableListOf<WorkspaceOperation>()
    var expandedCount = 0
    val ids = mutableSetOf<String>()
    fun collect(op: WorkspaceOperation) {
        ids.add(op.operationId)
        if (op is TransactionOperation) op.operations.forEach(::collect)
    }
    plan.review.operations.forEach(::collect)
    fun id(): String = newId().also { require(it.isNotBlank() && ids.add(it)) }
    fun emit(op: WorkspaceOperation) {
        require(commands.size < 200)
        require(current.version < 9_007_199_254_740_991L)
        val cost = when (op) {
            is CreateObjectsOperation -> op.objects.size
            is CreateRelationsOperation -> op.relations.size
            is ReparentObjectsOperation -> op.changes.size
            else -> 1 // Other emitted commands have one changed entity, or one batch transform/delete.
        }
        require(expandedCount + cost <= 200) { "Merge exceeds atomic transaction limit" }
        val incrementedVersions = when (op) {
            is UpdateTextNodeAttributesOperation -> op.changes.map { it.expectedVersion }
            is UpdateGroupFrameAttributesOperation -> op.changes.map { it.expectedVersion }
            is UpdateMediaNodeAttributesOperation -> op.changes.map { it.expectedVersion }
            is UpdateMediaReferenceOperation -> op.changes.map { it.expectedVersion }
            is UpdateRelationAttributesOperation -> op.changes.map { it.expectedVersion }
            is ReparentObjectsOperation -> op.changes.map { it.expectedVersion }
            is TransformObjectsOperation -> op.changes.map { it.expectedVersion }
            is EditTextOperation -> op.changes.map { it.expectedVersion }
            else -> emptyList()
        }
        require(incrementedVersions.all { it in 1 until 9_007_199_254_740_991L })
        state = (op.applyTo(state) as? OperationResult.Applied)?.workspace ?: error("Invalid merge operation")
        commands.add(op)
        expandedCount += cost
    }
    fun attrs(before: CanvasObject, after: CanvasObject) {
        when {
            before is TextNode && after is TextNode -> {
                val a = TextNodeAttributes(before.zIndex, before.locked, before.colorToken, before.shape)
                val b = TextNodeAttributes(after.zIndex, after.locked, after.colorToken, after.shape)
                if (a != b) emit(UpdateTextNodeAttributesOperation(id(), listOf(TextNodeAttributesChange(before.id, before.version, a, b))))
            }
            before is GroupFrame && after is GroupFrame -> {
                val a = GroupFrameAttributes(before.zIndex, before.locked, before.colorToken, before.title)
                val b = GroupFrameAttributes(after.zIndex, after.locked, after.colorToken, after.title)
                if (a != b) emit(UpdateGroupFrameAttributesOperation(id(), listOf(GroupFrameAttributesChange(before.id, before.version, a, b))))
            }
            before is MediaNode && after is MediaNode -> {
                val a = MediaNodeAttributes(before.zIndex, before.locked, before.altText)
                val b = MediaNodeAttributes(after.zIndex, after.locked, after.altText)
                if (a != b) emit(UpdateMediaNodeAttributesOperation(id(), listOf(MediaNodeAttributesChange(before.id, before.version, a, b))))
            }
            else -> error("Unsupported merge type")
        }
    }
    fun unlocked(obj: CanvasObject) = when (obj) {
        is TextNode -> obj.copy(locked = false)
        is GroupFrame -> obj.copy(locked = false)
        is MediaNode -> obj.copy(locked = false)
    }
    fun replacement(a: CanvasObject, b: CanvasObject): Boolean = when {
        a is TextNode && b is TextNode -> false
        a is GroupFrame && b is GroupFrame -> false
        a is MediaNode && b is MediaNode -> false
        else -> true
    }
    val removed = current.objects.keys.filter { key -> target.objects[key]?.let { replacement(current.objects.getValue(key), it) } ?: true }.toSet()
    current.objects.values.filter { it.locked && target.objects[it.id]?.locked == false }.sortedBy { it.id.value }
        .forEach { attrs(state.objects.getValue(it.id), unlocked(it)) }
    val removedEdges = state.relations.values.filter { edge ->
        val desired = target.relations[edge.id]
        desired == null || desired.sourceObjectId != edge.sourceObjectId || desired.targetObjectId != edge.targetObjectId ||
            edge.sourceObjectId in removed || edge.targetObjectId in removed
    }
    if (removedEdges.isNotEmpty()) emit(DeleteRelationsOperation(id(), removedEdges))
    // Detach retained children before deleting old groups or changing hierarchy. Attach only after creation.
    val detach = state.objects.values.filter { it.id !in removed && it.parentId != null &&
        (target.objects[it.id]?.parentId != it.parentId || it.parentId in removed) }
        .map { ParentChange(it.id, it.version, it.parentId, null) }
    if (detach.isNotEmpty()) emit(ReparentObjectsOperation(id(), detach))
    if (removed.isNotEmpty()) emit(DeleteObjectsOperation(id(), removed.sortedBy { it.value }.map { state.objects.getValue(it) }))
    val created = target.objects.values.filter { it.id !in state.objects }.sortedBy { it.id.value }
        .map { unlocked(it).withParentId(null, version = 1) }
    if (created.isNotEmpty()) emit(CreateObjectsOperation(id(), created))
    val attach = target.objects.values.filter { state.objects.getValue(it.id).parentId != it.parentId }
        .map { ParentChange(it.id, state.objects.getValue(it.id).version, state.objects.getValue(it.id).parentId, it.parentId) }
    if (attach.isNotEmpty()) emit(ReparentObjectsOperation(id(), attach))
    target.objects.values.sortedBy { it.id.value }.forEach { desired ->
        var before = state.objects.getValue(desired.id)
        if (before.transform != desired.transform) emit(TransformObjectsOperation(id(),
            listOf(TransformChange(before.id, before.version, before.transform, desired.transform))))
        before = state.objects.getValue(desired.id)
        if (before is TextNode && desired is TextNode && before.text != desired.text)
            emit(EditTextOperation(id(), listOf(TextChange(before.id, before.version, before.text, desired.text))))
        before = state.objects.getValue(desired.id)
        if (before is MediaNode && desired is MediaNode && before.mediaReference() != desired.mediaReference())
            emit(UpdateMediaReferenceOperation(id(), listOf(MediaReferenceChange(before.id, before.version,
                before.mediaReference(), desired.mediaReference()))))
        // Keep lock-only separate so the inverse can unlock before restoring attributes on the server.
        if (desired.locked && !state.objects.getValue(desired.id).locked)
            attrs(state.objects.getValue(desired.id), unlocked(desired))
        attrs(state.objects.getValue(desired.id), desired)
    }
    val newEdges = target.relations.values.filter { it.id !in state.relations }.map { it.copy(version = 1) }
    if (newEdges.isNotEmpty()) emit(CreateRelationsOperation(id(), newEdges))
    target.relations.values.sortedBy { it.id.value }.forEach { desired ->
        val before = state.relations.getValue(desired.id)
        val a = RelationAttributes(before.direction, before.intent, before.label, before.colorToken)
        val b = RelationAttributes(desired.direction, desired.intent, desired.label, desired.colorToken)
        if (a != b) emit(UpdateRelationAttributesOperation(id(), listOf(RelationAttributesChange(before.id, before.version, a, b))))
    }
    check(state.mergeContent() == target.mergeContent())
    return if (commands.isEmpty()) null else TransactionOperation(id(), commands).also {
        val applied = (it.applyTo(current) as? OperationResult.Applied)?.workspace ?: error("Invalid merge transaction")
        check(applied.mergeContent() == target.mergeContent())
    }
}

internal fun Workspace.mergeContent(): Workspace = copy(version = 0,
    objects = objects.mapValues { it.value.withVersion(1) }, relations = relations.mapValues { it.value.copy(version = 1) })

/** Read-only contract assessment, not permission to submit. Even an empty set needs fresh ACL,
 * media validation, tombstone checks, pending-submit reconciliation and explicit confirmation.
 * Current backend create rejects IDs present in tombstones; local replay does not model those rows.
 */
internal enum class DraftMergeContractGap {
    ObjectIdReuse, RelationIdReuse, RestoreForUndoRedo,
}

internal fun draftMergeContractGaps(current: Workspace, operation: TransactionOperation): Set<DraftMergeContractGap> {
    val objectIds = current.objects.keys.toMutableSet()
    val relationIds = current.relations.keys.toMutableSet()
    val gaps = mutableSetOf<DraftMergeContractGap>()
    fun inspect(op: WorkspaceOperation) {
        when (op) {
            is TransactionOperation -> op.operations.forEach(::inspect)
            is CreateObjectsOperation -> {
                op.objects.forEach { if (!objectIds.add(it.id)) gaps.add(DraftMergeContractGap.ObjectIdReuse) }
                gaps.add(DraftMergeContractGap.RestoreForUndoRedo)
            }
            is CreateRelationsOperation -> {
                op.relations.forEach { if (!relationIds.add(it.id)) gaps.add(DraftMergeContractGap.RelationIdReuse) }
                gaps.add(DraftMergeContractGap.RestoreForUndoRedo)
            }
            is DeleteObjectsOperation, is DeleteRelationsOperation -> gaps.add(DraftMergeContractGap.RestoreForUndoRedo)
            else -> Unit
        }
    }
    inspect(operation)
    return gaps.toSet()
}
