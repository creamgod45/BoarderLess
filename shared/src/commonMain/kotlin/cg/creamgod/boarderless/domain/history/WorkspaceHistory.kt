package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.withVersion

data class HistoryResult(
    val history: WorkspaceHistory,
    val error: OperationError? = null,
    val appliedOperation: WorkspaceOperation? = null,
) {
    val succeeded: Boolean get() = error == null
}

data class WorkspaceHistory(
    val workspace: Workspace,
    private val undoStack: List<WorkspaceOperation> = emptyList(),
    private val redoStack: List<WorkspaceOperation> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoOperations: List<WorkspaceOperation> get() = undoStack
    val redoOperations: List<WorkspaceOperation> get() = redoStack

    fun execute(operation: WorkspaceOperation): HistoryResult = when (val result = operation.applyTo(workspace)) {
        is OperationResult.Applied -> HistoryResult(
            history = copy(
                workspace = result.workspace,
                undoStack = undoStack + operation.inverse(),
                redoStack = emptyList(),
            ),
            appliedOperation = operation,
        )

        is OperationResult.Rejected -> HistoryResult(this, result.error)
    }

    fun undo(): HistoryResult {
        val storedOperation = undoStack.lastOrNull() ?: return HistoryResult(this)
        val operation = storedOperation.rebasedFor(workspace)
        return when (val result = operation.applyTo(workspace)) {
            is OperationResult.Applied -> HistoryResult(
                history = copy(
                    workspace = result.workspace,
                    undoStack = undoStack.dropLast(1),
                    redoStack = redoStack + operation.inverse(),
                ),
                appliedOperation = operation,
            )

            is OperationResult.Rejected -> HistoryResult(this, result.error)
        }
    }

    fun redo(): HistoryResult {
        val storedOperation = redoStack.lastOrNull() ?: return HistoryResult(this)
        val operation = storedOperation.rebasedFor(workspace)
        return when (val result = operation.applyTo(workspace)) {
            is OperationResult.Applied -> HistoryResult(
                history = copy(
                    workspace = result.workspace,
                    undoStack = undoStack + operation.inverse(),
                    redoStack = redoStack.dropLast(1),
                ),
                appliedOperation = operation,
            )

            is OperationResult.Rejected -> HistoryResult(this, result.error)
        }
    }
}

private fun WorkspaceOperation.rebasedFor(workspace: Workspace): WorkspaceOperation = when (this) {
    is CreateObjectsOperation -> this
    is CreateRelationsOperation -> this
    is DeleteObjectsOperation -> copy(
        objects = objects.map { snapshot ->
            workspace.objects[snapshot.id]?.let { snapshot.withVersion(it.version) } ?: snapshot
        },
        relations = relations.map { snapshot ->
            workspace.relations[snapshot.id]?.let { snapshot.copy(version = it.version) } ?: snapshot
        },
    )

    is DeleteRelationsOperation -> copy(
        relations = relations.map { snapshot ->
            workspace.relations[snapshot.id]?.let { snapshot.copy(version = it.version) } ?: snapshot
        },
    )

    is UpdateRelationAttributesOperation -> copy(
        changes = changes.map { change ->
            change.copy(
                expectedVersion = workspace.relations[change.relationId]?.version ?: change.expectedVersion,
            )
        },
    )

    is TransformObjectsOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is ReparentObjectsOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is EditTextOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is UpdateTextNodeAttributesOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is UpdateGroupFrameAttributesOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is UpdateMediaReferenceOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is UpdateMediaNodeAttributesOperation -> copy(
        changes = changes.map { change ->
            change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
        },
    )

    is TransactionOperation -> {
        var simulatedWorkspace = workspace
        val rebasedOperations = buildList {
            for (child in operations) {
                val rebased = child.rebasedFor(simulatedWorkspace)
                add(rebased)
                val result = rebased.applyTo(simulatedWorkspace)
                if (result is OperationResult.Applied) {
                    simulatedWorkspace = result.workspace
                }
            }
        }
        copy(operations = rebasedOperations)
    }
}
