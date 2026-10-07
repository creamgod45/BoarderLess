package cg.creamgod.boarderless.domain.history

import cg.creamgod.boarderless.domain.model.CanvasStyle
import cg.creamgod.boarderless.domain.model.MaximumCanvasVersion
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.serialization.Serializable

@Serializable
data class UpdateCanvasStyleOperation(
    override val operationId: String,
    val expectedCanvasStyleVersion: Long,
    val before: CanvasStyle,
    val after: CanvasStyle,
) : WorkspaceOperation {
    init {
        require(operationId.isNotBlank())
        require(expectedCanvasStyleVersion in 0..MaximumCanvasVersion)
    }

    override fun applyTo(workspace: Workspace): OperationResult {
        if (workspace.canvasStyleVersion != expectedCanvasStyleVersion) {
            return OperationResult.Rejected(
                OperationError.CanvasStyleVersionConflict(expectedCanvasStyleVersion, workspace.canvasStyleVersion),
            )
        }
        if (workspace.canvasStyle != before) return OperationResult.Rejected(OperationError.CanvasStyleStateConflict)
        if (workspace.canvasStyleVersion >= MaximumCanvasVersion) return OperationResult.Rejected(OperationError.CanvasStyleVersionLimit)
        if (workspace.version >= MaximumCanvasVersion) return OperationResult.Rejected(OperationError.WorkspaceVersionLimit)
        return OperationResult.Applied(
            workspace.copy(
                version = workspace.version + 1,
                canvasStyle = after,
                canvasStyleVersion = workspace.canvasStyleVersion + 1,
            ),
        )
    }

    override fun inverse(): WorkspaceOperation =
        UpdateCanvasStyleOperation("$operationId:inverse", expectedCanvasStyleVersion + 1, after, before)
}
