package cg.creamgod.boarderless.domain.ai

import cg.creamgod.boarderless.domain.history.OperationError
import cg.creamgod.boarderless.domain.history.OperationResult
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

enum class AiContextScope {
    Selection,
    PromptOnly,
}

data class AiContextObject(
    val objectId: String,
    val objectType: String,
    val version: Long,
    val content: String,
)

data class AiContextRelation(
    val relationId: String,
    val sourceObjectId: String,
    val targetObjectId: String,
    val direction: String,
    val intent: String?,
    val label: String?,
)

data class AiContextSnapshot(
    val workspaceId: String,
    val workspaceVersion: Long,
    val scope: AiContextScope,
    val objects: List<AiContextObject>,
    val relations: List<AiContextRelation>,
) {
    companion object {
        fun fromSelection(
            workspace: Workspace,
            selectedObjectIds: Set<CanvasObjectId>,
        ): AiContextSnapshot {
            val objects = selectedObjectIds.mapNotNull(workspace::objectById)
            val includedIds = objects.mapTo(mutableSetOf()) { it.id }
            return AiContextSnapshot(
                workspaceId = workspace.id.value,
                workspaceVersion = workspace.version,
                scope = if (objects.isEmpty()) AiContextScope.PromptOnly else AiContextScope.Selection,
                objects =
                    objects.map { canvasObject ->
                        when (canvasObject) {
                            is TextNode -> {
                                AiContextObject(
                                    objectId = canvasObject.id.value,
                                    objectType = "text",
                                    version = canvasObject.version,
                                    content = canvasObject.text,
                                )
                            }

                            is GroupFrame -> {
                                AiContextObject(
                                    objectId = canvasObject.id.value,
                                    objectType = "group",
                                    version = canvasObject.version,
                                    content = canvasObject.title,
                                )
                            }

                            is MediaNode -> {
                                AiContextObject(
                                    objectId = canvasObject.id.value,
                                    objectType = "media",
                                    version = canvasObject.version,
                                    content = canvasObject.altText.ifBlank { canvasObject.mediaKind.token },
                                )
                            }
                        }
                    },
                relations =
                    workspace.relations.values
                        .filter { it.sourceObjectId in includedIds && it.targetObjectId in includedIds }
                        .map { relation ->
                            AiContextRelation(
                                relationId = relation.id.value,
                                sourceObjectId = relation.sourceObjectId.value,
                                targetObjectId = relation.targetObjectId.value,
                                direction = relation.direction.name,
                                intent = relation.intent,
                                label = relation.label,
                            )
                        },
            )
        }
    }
}

data class AiCoworkRequest(
    val requestId: String,
    val prompt: String,
    val context: AiContextSnapshot,
)

sealed interface AiCoworkEvent {
    data class TextDelta(
        val text: String,
    ) : AiCoworkEvent

    data class ProposedOperation(
        val item: AiProposalItem,
    ) : AiCoworkEvent

    data object Completed : AiCoworkEvent

    data class Failed(
        val message: String,
        val retryable: Boolean,
    ) : AiCoworkEvent
}

interface AiCoworkProvider {
    val providerId: String

    fun stream(request: AiCoworkRequest): Flow<AiCoworkEvent>
}

enum class AiProposalStatus {
    Ready,
    Accepted,
    PartiallyAccepted,
    Rejected,
    Conflict,
}

data class AiProposalItem(
    val itemId: String,
    val summary: String,
    val rationale: String,
    val operation: WorkspaceOperation,
    val included: Boolean = true,
)

data class AiProposal(
    val proposalId: String,
    val contextWorkspaceVersion: Long,
    val items: List<AiProposalItem>,
    val status: AiProposalStatus = AiProposalStatus.Ready,
) {
    init {
        require(items.isNotEmpty()) { "AI proposal must contain at least one item" }
        require(items.map(AiProposalItem::itemId).distinct().size == items.size) {
            "AI proposal item IDs must be unique"
        }
    }

    fun withItemIncluded(
        itemId: String,
        included: Boolean,
    ): AiProposal =
        copy(
            items = items.map { item -> if (item.itemId == itemId) item.copy(included = included) else item },
        )

    fun selectedOperation(): TransactionOperation? {
        val selected = items.filter(AiProposalItem::included).map(AiProposalItem::operation)
        if (selected.isEmpty()) return null
        return TransactionOperation(
            operationId = "ai-proposal-$proposalId",
            operations = selected,
        )
    }

    fun preview(workspace: Workspace): AiProposalPreview {
        if (workspace.version != contextWorkspaceVersion) {
            return AiProposalPreview.Conflict(
                error =
                    AiProposalConflict.WorkspaceVersion(
                        expected = contextWorkspaceVersion,
                        actual = workspace.version,
                    ),
            )
        }
        val operation = selectedOperation() ?: return AiProposalPreview.Empty
        return when (val result = operation.applyTo(workspace)) {
            is OperationResult.Applied -> {
                AiProposalPreview.Ready(
                    workspace = result.workspace,
                    operation = operation,
                )
            }

            is OperationResult.Rejected -> {
                AiProposalPreview.Conflict(
                    error = AiProposalConflict.Operation(result.error),
                )
            }
        }
    }

    fun markCommitted(): AiProposal {
        val includedCount = items.count(AiProposalItem::included)
        return copy(
            status =
                if (includedCount == items.size) {
                    AiProposalStatus.Accepted
                } else {
                    AiProposalStatus.PartiallyAccepted
                },
        )
    }

    fun reject(): AiProposal = copy(status = AiProposalStatus.Rejected)
}

sealed interface AiProposalConflict {
    data class WorkspaceVersion(
        val expected: Long,
        val actual: Long,
    ) : AiProposalConflict

    data class Operation(
        val error: OperationError,
    ) : AiProposalConflict
}

sealed interface AiProposalPreview {
    data class Ready(
        val workspace: Workspace,
        val operation: TransactionOperation,
    ) : AiProposalPreview

    data class Conflict(
        val error: AiProposalConflict,
    ) : AiProposalPreview

    data object Empty : AiProposalPreview
}
