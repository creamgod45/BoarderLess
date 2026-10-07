package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*

/** Captures the canvas at editor opening. Preview never changes this snapshot. */
internal data class PenCanvasCreation(
    val owner: WorkspaceSession,
    val baseline: Workspace,
    val position: Vec2,
) {
    init {
        require(baseline.id == owner.workspace.id)
        require(kotlin.math.abs(position.x) <= 1_000_000f && kotlin.math.abs(position.y) <= 1_000_000f)
    }

    fun isCurrent(
        session: WorkspaceSession?,
        workspace: Workspace,
    ): Boolean =
        session != null && session.canEditContent &&
            session.userId == owner.userId && session.clientId == owner.clientId && session.workspace.id == owner.workspace.id &&
            session.workspaceVersion == owner.workspaceVersion && session.lastServerSeq == owner.lastServerSeq &&
            session.workspace == owner.workspace &&
            workspace == baseline

    fun operation(
        path: VectorPath,
        objectId: String,
        operationId: String,
    ): CreateObjectsOperation {
        val normalized = normalizePenPath(path).path
        val highest = baseline.objects.values.maxOfOrNull { it.zIndex } ?: 0L
        require(highest < Long.MAX_VALUE)
        return CreateObjectsOperation(
            operationId,
            listOf(
                TextNode(
                    CanvasObjectId(objectId),
                    transform = CanvasTransform(position, normalized.viewBox),
                    zIndex = highest + 1,
                    text = "",
                    shape = NodeShape.Rectangle,
                    vectorPath = normalized,
                ),
            ),
        )
    }
}
