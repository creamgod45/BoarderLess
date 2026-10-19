package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings
import kotlinx.serialization.Serializable

/** Local-only, explicitly reviewed full-content transaction. Never a server wire operation.
 * Exact before-state fencing prevents replacing edits made after the review. */
@Serializable data class LocalWorkspaceContentOperation(
    override val operationId: String,
    val before: Workspace,
    val after: Workspace,
    val restoreVersions: Boolean = false,
) : WorkspaceOperation {
    init { require(operationId.isNotBlank() && before.id == after.id) }

    override fun applyTo(workspace: Workspace): OperationResult {
        if (workspace != before) return OperationResult.Rejected(OperationError.LocalWorkspaceContentConflict)
        if (workspace.version >= MaximumCanvasVersion) return OperationResult.Rejected(OperationError.WorkspaceVersionLimit)
        val result = runCatching {
            require(after.objects.size <= 10_000 && after.relations.size <= 10_000)
            after.objects.forEach { (id, value) ->
                require(id == value.id)
                var parent = value.parentId
                val seen = mutableSetOf(id)
                while (parent != null) {
                    require(seen.size <= 64 && seen.add(parent))
                    parent = (after.objects[parent] as? GroupFrame ?: error("Missing group")).parentId
                }
            }
            after.relations.forEach { (id, edge) ->
                require(id == edge.id && edge.sourceObjectId in after.objects && edge.targetObjectId in after.objects)
                require(edge.sourceObjectId != edge.targetObjectId || edge.geometry != null)
            }
            require(after.hasValidSequenceBindings())
            before.objects.values.filter { it.locked }.forEach { old ->
                val next = after.objects[old.id]
                require(next?.withVersion(1) == old.withVersion(1) || (next != null && !next.locked))
            }
            val objects = after.objects.mapValues { (id, desired) ->
                val previous = before.objects[id]
                val version = if (previous == null) if (restoreVersions) desired.version else 1 else if (previous.withVersion(1) == desired.withVersion(1)) previous.version
                    else { require(previous.version < MaximumCanvasVersion); previous.version + 1 }
                desired.withVersion(version)
            }
            val relations = after.relations.mapValues { (id, desired) ->
                val previous = before.relations[id]
                val version = if (previous == null) if (restoreVersions) desired.version else 1 else if (previous.withVersion(1) == desired.withVersion(1)) previous.version
                    else { require(previous.version < MaximumCanvasVersion); previous.version + 1 }
                desired.withVersion(version)
            }
            fun revision(value: Long, changed: Boolean): Long {
                if (!changed) return value
                require(value < MaximumCanvasVersion); return value + 1
            }
            after.copy(version = before.version + 1, objects = objects, relations = relations,
                canvasStyleVersion = revision(before.canvasStyleVersion, before.canvasStyle != after.canvasStyle),
                sequenceDiagramsVersion = revision(before.sequenceDiagramsVersion, before.sequenceDiagrams != after.sequenceDiagrams))
        }
        return result.fold({ OperationResult.Applied(it) }, { OperationResult.Rejected(OperationError.LocalWorkspaceContentConflict) })
    }

    override fun inverse(): WorkspaceOperation = LocalWorkspaceContentOperation("$operationId:inverse",
        (applyTo(before) as OperationResult.Applied).workspace, before, restoreVersions = true)
}

internal fun sameLocalWorkspaceContent(first: Workspace, second: Workspace): Boolean {
    fun content(value: Workspace) = value.copy(version = 0, canvasStyleVersion = 0, sequenceDiagramsVersion = 0,
        objects = value.objects.mapValues { it.value.withVersion(1) }, relations = value.relations.mapValues { it.value.withVersion(1) })
    return content(first) == content(second)
}
