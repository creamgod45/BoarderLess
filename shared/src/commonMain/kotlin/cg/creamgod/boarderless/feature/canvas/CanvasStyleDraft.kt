package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation
import cg.creamgod.boarderless.domain.model.CanvasStyle
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId

/** Picker preview never changes document/history. Apply checks the captured style and scope. */
internal data class CanvasStyleDraft(
    val userId: String,
    val clientId: String,
    val workspaceId: WorkspaceId,
    val before: CanvasStyle,
    val styleVersion: Long,
) {
    fun matches(session: WorkspaceSession): Boolean =
        session.userId == userId &&
            session.clientId == clientId && session.workspace.id == workspaceId && session.canEditContent

    fun operation(
        session: WorkspaceSession,
        live: Workspace,
        token: String,
        operationId: String,
    ): UpdateCanvasStyleOperation? {
        require(matches(session) && live.id == workspaceId) { "Canvas style scope changed" }
        require(live.canvasStyle == before && live.canvasStyleVersion == styleVersion) { "Canvas style changed; reopen the picker" }
        val after = before.copy(backgroundToken = token.lowercase())
        return if (after == before) null else UpdateCanvasStyleOperation(operationId, styleVersion, before, after)
    }

    companion object {
        fun capture(
            session: WorkspaceSession,
            live: Workspace,
        ): CanvasStyleDraft {
            require(session.canEditContent && session.workspace.id == live.id)
            return CanvasStyleDraft(session.userId, session.clientId, live.id, live.canvasStyle, live.canvasStyleVersion)
        }
    }
}
