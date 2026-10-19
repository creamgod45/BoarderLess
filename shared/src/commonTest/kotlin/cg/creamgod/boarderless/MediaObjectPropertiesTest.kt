package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.media.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class MediaObjectPropertiesTest {
    @Test fun mixedMediaInspectorSelectionReordersAndLocksWithoutChangingEitherIdentity() {
        val local = MediaNode(CanvasObjectId("local"), transform = CanvasTransform(Vec2.Zero, CanvasSize(40f, 40f)),
            assetId = "local-asset", mediaKind = MediaKind.Image, altText = "本機圖片", zIndex = 0)
        val reference = ExternalMediaReference(ExternalMediaProvider.Giphy, "gif-id")
        val external = ExternalMediaNode(CanvasObjectId("external"), transform = local.transform,
            reference = reference, altText = "外部動圖", zIndex = 1)
        val text = TextNode(CanvasObjectId("text"), transform = local.transform, text = "文字")
        val workspace = Workspace(WorkspaceId("media-properties"), "Media properties",
            objects = mapOf(local.id to local, external.id to external, text.id to text))
        val media = workspace.objects.values.filter { it.isMediaObject }
        assertEquals(listOf("本機圖片", "外部動圖"), media.map { it.mediaAltText })
        assertFalse(text.isMediaObject)
        val updates = layerZIndexUpdates(media, setOf(external.id), LayerMove.Back)
        assertEquals(mapOf(external.id to 0L, local.id to 1L), updates)
        val changes = media.map { node ->
            val before = MediaNodeAttributes(node.zIndex, node.locked, node.mediaAltText)
            MediaNodeAttributesChange(node.id, node.version, before,
                before.copy(zIndex = updates.getValue(node.id), locked = true, altText = node.mediaAltText + "🙂"))
        }
        val result = WorkspaceHistory(workspace).execute(UpdateMediaNodeAttributesOperation("media-properties", changes))
        assertTrue(result.succeeded)
        val changedLocal = assertIs<MediaNode>(result.history.workspace.objects[local.id])
        val changedExternal = assertIs<ExternalMediaNode>(result.history.workspace.objects[external.id])
        assertEquals(local.assetId, changedLocal.assetId)
        assertEquals(reference, changedExternal.reference)
        assertTrue(changedLocal.locked && changedExternal.locked)
        assertEquals("外部動圖🙂", changedExternal.mediaAltText)
        assertEquals(text, result.history.workspace.objects[text.id])
        val undone = result.history.undo()
        assertTrue(undone.succeeded)
        val restored = assertIs<ExternalMediaNode>(undone.history.workspace.objects[external.id])
        assertEquals(external.altText, restored.altText)
        assertEquals(external.zIndex, restored.zIndex)
        assertFalse(restored.locked)
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        val final = assertIs<ExternalMediaNode>(redone.history.workspace.objects[external.id])
        assertEquals(changedExternal.copy(version = final.version), final)
    }
}
