package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.MediaNodeAttributes
import cg.creamgod.boarderless.domain.history.MediaNodeAttributesChange
import cg.creamgod.boarderless.domain.history.UpdateMediaNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaNodeOperationTest {
    @Test
    fun mediaMetadataChangeIsUndoableAndRedoable() {
        val node =
            MediaNode(
                id = CanvasObjectId("media-1"),
                transform = CanvasTransform(Vec2.Zero, CanvasSize(320f, 180f)),
                assetId = "asset-1",
                mediaKind = MediaKind.Image,
            )
        var history = WorkspaceHistory(Workspace(WorkspaceId("workspace-1"), "Media"))
        history = history.execute(CreateObjectsOperation("create", listOf(node))).history
        history =
            history
                .execute(
                    UpdateMediaNodeAttributesOperation(
                        operationId = "describe",
                        changes =
                            listOf(
                                MediaNodeAttributesChange(
                                    objectId = node.id,
                                    expectedVersion = 1,
                                    before = MediaNodeAttributes(0, false, ""),
                                    after = MediaNodeAttributes(0, true, "Roadmap image"),
                                ),
                            ),
                    ),
                ).history

        val changed = assertIs<MediaNode>(history.workspace.objectById(node.id))
        assertTrue(changed.locked)
        assertEquals("Roadmap image", changed.altText)

        history = history.undo().history
        val undone = assertIs<MediaNode>(history.workspace.objectById(node.id))
        assertFalse(undone.locked)
        assertEquals("", undone.altText)

        history = history.redo().history
        val redone = assertIs<MediaNode>(history.workspace.objectById(node.id))
        assertTrue(redone.locked)
        assertEquals("Roadmap image", redone.altText)
    }
}
