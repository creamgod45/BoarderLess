package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.withVersion
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings

@kotlinx.serialization.Serializable
enum class WorkspaceHistoryIntent { Edit, Undo, Redo }

data class HistoryResult(
    val history: WorkspaceHistory,
    val error: OperationError? = null,
    val appliedOperation: WorkspaceOperation? = null,
) {
    val succeeded: Boolean get() = error == null
}

@kotlinx.serialization.Serializable
data class WorkspaceHistory(
    val workspace: Workspace,
    private val undoStack: List<WorkspaceOperation> = emptyList(),
    private val redoStack: List<WorkspaceOperation> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoOperations: List<WorkspaceOperation> get() = undoStack
    val redoOperations: List<WorkspaceOperation> get() = redoStack

    /** Reconstruct a saved intention using the original stack, not ID heuristics.
     * Exact matching prevents a changed intent from silently selecting another action. */
    fun applyRecorded(operation: WorkspaceOperation, intent: WorkspaceHistoryIntent): HistoryResult {
        val result = when (intent) {
            WorkspaceHistoryIntent.Edit -> execute(operation)
            WorkspaceHistoryIntent.Undo -> undo(remoteRestoration = true)
            WorkspaceHistoryIntent.Redo -> redo(remoteRestoration = true)
        }
        require(result.succeeded && result.appliedOperation == operation) { "Saved history intention does not match its stack" }
        return result
    }

    /** Seed a fully verified committed action without applying it again. Ordinary undo
     * preconditions still check the latest workspace; this is not a forced rollback. */
    fun withRecoveredCommit(submitted: WorkspaceOperation,
        origins: List<MediaReferenceHistoryOrigin>,
        intent: WorkspaceHistoryIntent = WorkspaceHistoryIntent.Edit): WorkspaceHistory {
        require(undoStack.isEmpty() && redoStack.isEmpty())
        val restored = copy(undoStack = listOf(submitted.inverse()))
            .withCommittedMediaReferences(submitted, origins)
        return if (intent == WorkspaceHistoryIntent.Undo) {
            restored.copy(undoStack = emptyList(), redoStack = restored.undoStack)
        } else restored
    }

    /** Bind only a confirmed submitted operation's exact inverse still in history.
     * The backend must revalidate these local hints against its durable original evidence. */
    fun withCommittedMediaReferences(submitted: WorkspaceOperation,
        origins: List<MediaReferenceHistoryOrigin>): WorkspaceHistory {
        if (origins.isEmpty()) return this
        val expected = submitted.inverse()
        fun attach(stored: WorkspaceOperation, original: WorkspaceOperation): WorkspaceOperation {
            require(stored.operationId == original.operationId)
            return when {
                stored is TransactionOperation && original is TransactionOperation -> {
                    require(stored.operations.size == original.operations.size)
                    stored.copy(operations = stored.operations.zip(original.operations).map { (child, expectedChild) -> attach(child, expectedChild) })
                }
                stored is UpdateMediaReferenceOperation && original is UpdateMediaReferenceOperation -> {
                    require(stored.changes.map { it.copy(historyOrigin = null) } == original.changes.map { it.copy(historyOrigin = null) })
                    stored.copy(changes = stored.changes.map { change ->
                        val origin = origins.singleOrNull { it.objectId == change.objectId && it.before == change.after &&
                            it.after == change.before && it.objectVersionBefore + 1 == change.expectedVersion }
                        if (origin == null) change else change.copy(historyOrigin = origin)
                    })
                }
                else -> stored
            }
        }
        return copy(undoStack = undoStack.map { if (it.operationId == expected.operationId) attach(it, expected) else it })
    }

    fun execute(operation: WorkspaceOperation): HistoryResult =
        when (val result = operation.checkedApplyTo(workspace)) {
            is OperationResult.Applied -> {
                HistoryResult(
                    history =
                        copy(
                            workspace = result.workspace,
                            undoStack = undoStack + operation.inverse(),
                            redoStack = emptyList(),
                        ),
                    appliedOperation = operation,
                )
            }

            is OperationResult.Rejected -> {
                HistoryResult(this, result.error)
            }
        }

    fun undo(remoteRestoration: Boolean = false): HistoryResult {
        val storedOperation = undoStack.lastOrNull() ?: return HistoryResult(this)
        val operation = storedOperation.rebasedFor(workspace, remoteRestoration)
        return when (val result = operation.checkedApplyTo(workspace)) {
            is OperationResult.Applied -> {
                HistoryResult(
                    history =
                        copy(
                            workspace = result.workspace,
                            undoStack = undoStack.dropLast(1),
                            redoStack = redoStack + operation.inverse(),
                        ),
                    appliedOperation = operation,
                )
            }

            is OperationResult.Rejected -> {
                HistoryResult(this, result.error)
            }
        }
    }

    fun redo(remoteRestoration: Boolean = false): HistoryResult {
        val storedOperation = redoStack.lastOrNull() ?: return HistoryResult(this)
        val operation = storedOperation.rebasedFor(workspace, remoteRestoration)
        return when (val result = operation.checkedApplyTo(workspace)) {
            is OperationResult.Applied -> {
                HistoryResult(
                    history =
                        copy(
                            workspace = result.workspace,
                            undoStack = undoStack + operation.inverse(),
                            redoStack = redoStack.dropLast(1),
                        ),
                    appliedOperation = operation,
                )
            }

            is OperationResult.Rejected -> {
                HistoryResult(this, result.error)
            }
        }
    }
}

private fun WorkspaceOperation.rebasedFor(
    workspace: Workspace,
    remoteRestoration: Boolean,
): WorkspaceOperation =
    when (this) {
        is LocalWorkspaceContentOperation -> {
            if (sameLocalWorkspaceContent(before, workspace)) copy(before = workspace) else this
        }
        is CreateObjectsOperation -> {
            if (!remoteRestoration || restoreVersions.isNotEmpty()) {
                this
            } else {
                copy(
                    objects = objects.map { it.withVersion(it.version + 2) },
                    restoreVersions = objects.associate { it.id.value to it.version + 1 },
                )
            }
        }

        is CreateRelationsOperation -> {
            if (!remoteRestoration || restoreVersions.isNotEmpty()) {
                this
            } else {
                copy(
                    relations = relations.map { it.copy(version = it.version + 2) },
                    restoreVersions = relations.associate { it.id.value to it.version + 1 },
                )
            }
        }

        is DeleteObjectsOperation -> {
            copy(
                objects =
                    objects.map { snapshot ->
                        workspace.objects[snapshot.id]?.let { snapshot.withVersion(it.version) } ?: snapshot
                    },
                relations =
                    relations.map { snapshot ->
                        workspace.relations[snapshot.id]?.let { snapshot.copy(version = it.version) } ?: snapshot
                    },
            )
        }

        is DeleteRelationsOperation -> {
            copy(
                relations =
                    relations.map { snapshot ->
                        workspace.relations[snapshot.id]?.let { snapshot.copy(version = it.version) } ?: snapshot
                    },
            )
        }

        is UpdateRelationAttributesOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(
                            expectedVersion = workspace.relations[change.relationId]?.version ?: change.expectedVersion,
                        )
                    },
            )
        }

        is TransformObjectsOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is ReparentObjectsOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is EditTextOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is UpdateTextNodeAttributesOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is UpdateGroupFrameAttributesOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is UpdateMediaReferenceOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is UpdateMediaNodeAttributesOperation -> {
            copy(
                changes =
                    changes.map { change ->
                        change.copy(expectedVersion = workspace.objects[change.objectId]?.version ?: change.expectedVersion)
                    },
            )
        }

        is UpdateSequenceDiagramsOperation -> {
            copy(expectedSequenceDiagramsVersion = workspace.sequenceDiagramsVersion)
        }

        is UpdateCanvasStyleOperation -> {
            copy(expectedCanvasStyleVersion = workspace.canvasStyleVersion)
        }

        is TransactionOperation -> {
            var simulatedWorkspace = workspace
            val rebasedOperations =
                buildList {
                    for (child in operations) {
                        val rebased = child.rebasedFor(simulatedWorkspace, remoteRestoration)
                        add(rebased)
                        val result = rebased.applyInTransaction(simulatedWorkspace)
                        if (result is OperationResult.Applied) {
                            simulatedWorkspace = result.workspace
                        }
                    }
                }
            copy(operations = rebasedOperations)
        }
    }

private fun WorkspaceOperation.checkedApplyTo(workspace: Workspace): OperationResult {
    val result = applyTo(workspace)
    return if (result is OperationResult.Applied && !result.workspace.hasValidSequenceBindings()) {
        OperationResult.Rejected(OperationError.SequenceDiagramStateConflict)
    } else {
        result
    }
}
