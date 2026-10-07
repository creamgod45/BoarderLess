package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import kotlinx.serialization.Serializable

/** Metadata replace participates in the same transaction as its exact object/relation bindings. */
@Serializable data class UpdateSequenceDiagramsOperation(
    override val operationId: String,
    val expectedSequenceDiagramsVersion: Long,
    val before: Map<CanvasObjectId, SequenceCanvasDiagram>,
    val after: Map<CanvasObjectId, SequenceCanvasDiagram>,
) : WorkspaceOperation {
    init {
        require(operationId.isNotBlank() && expectedSequenceDiagramsVersion in 0..MaximumCanvasVersion)
        for (map in listOf(before, after)) {
            require(map.size <= 64)
            map.forEach { (id, diagram) ->
                require(id == diagram.containerId)
                diagram.validatedSnapshot()
            }
        }
    }

    override fun applyTo(workspace: Workspace): OperationResult = applyMetadata(workspace, true)

    internal fun applyMetadata(
        workspace: Workspace,
        validateBindings: Boolean,
    ): OperationResult {
        if (workspace.sequenceDiagramsVersion != expectedSequenceDiagramsVersion || workspace.sequenceDiagrams != before) {
            return OperationResult.Rejected(OperationError.SequenceDiagramStateConflict)
        }
        if (workspace.sequenceDiagramsVersion >= MaximumCanvasVersion || workspace.version >= MaximumCanvasVersion) {
            return OperationResult.Rejected(OperationError.WorkspaceVersionLimit)
        }
        val candidate =
            runCatching {
                workspace.copy(
                    version = workspace.version + 1,
                    sequenceDiagramsVersion = workspace.sequenceDiagramsVersion + 1,
                    sequenceDiagrams = after.mapValues { it.value.validatedSnapshot() },
                )
            }.getOrNull()
                ?: return OperationResult.Rejected(OperationError.SequenceDiagramStateConflict)
        if (validateBindings &&
            !candidate.hasValidSequenceBindings()
        ) {
            return OperationResult.Rejected(OperationError.SequenceDiagramStateConflict)
        }
        return OperationResult.Applied(candidate)
    }

    override fun inverse(): WorkspaceOperation =
        UpdateSequenceDiagramsOperation("$operationId:inverse", expectedSequenceDiagramsVersion + 1, after, before)
}

/** Whole diagram deletion clears semantics first. Partial ordinary deletion is rejected, leaving
 * dedicated sequence editing to choose valid branch/message/participant changes explicitly. */
fun deleteSequenceAware(
    workspace: Workspace,
    operation: DeleteObjectsOperation,
    metadataOperationId: String,
): WorkspaceOperation {
    val deleted = operation.objects.mapTo(mutableSetOf()) { it.id }
    val affected = workspace.sequenceDiagrams.filterValues { it.allObjectIds().any(deleted::contains) }
    if (affected.isEmpty()) return operation
    require(affected.values.all { deleted.containsAll(it.allObjectIds()) }) { "Use sequence editing for a partial diagram" }
    return TransactionOperation(
        operation.operationId + ":sequence",
        listOf(
            UpdateSequenceDiagramsOperation(
                metadataOperationId,
                workspace.sequenceDiagramsVersion,
                workspace.sequenceDiagrams,
                workspace.sequenceDiagrams - affected.keys,
            ),
            operation,
        ),
    )
}
