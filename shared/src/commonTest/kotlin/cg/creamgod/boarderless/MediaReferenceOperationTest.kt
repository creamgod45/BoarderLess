package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.toExpandedDtos
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import kotlin.test.*

class MediaReferenceOperationTest {
    private val node = MediaNode(CanvasObjectId("media"), transform = CanvasTransform(Vec2.Zero, CanvasSize(320f, 180f)),
        assetId = "old", mediaKind = MediaKind.Video, thumbnailAssetId = "poster", version = 7)
    private val base = Workspace(WorkspaceId("workspace"), "Media", objects = mapOf(node.id to node))
    private val change = MediaReferenceChange(node.id, 7, node.mediaReference(), MediaReference("new", MediaKind.Image))

    @Test fun preservesIdentityAndMetadataThroughUndoRedoAndSerialization() {
        val operation: WorkspaceOperation = UpdateMediaReferenceOperation("replace", listOf(change))
        assertEquals(operation, Json.decodeFromString<WorkspaceOperation>(Json.encodeToString(operation)))
        val executed = WorkspaceHistory(base).execute(operation)
        assertTrue(executed.succeeded)
        assertEquals(node.copy(version = 8, assetId = "new", mediaKind = MediaKind.Image, thumbnailAssetId = null),
            executed.history.workspace.objects[node.id])
        val undone = executed.history.undo()
        assertTrue(undone.succeeded)
        assertEquals(node.copy(version = 9), undone.history.workspace.objects[node.id])
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        assertEquals(change.after, (redone.history.workspace.objects.getValue(node.id) as MediaNode).mediaReference())
    }

    @Test fun rejectsLockedStaleWrongTypeAndInvalidReferences() {
        val op = UpdateMediaReferenceOperation("replace", listOf(change))
        assertIs<OperationResult.Rejected>(op.applyTo(base.copy(objects = mapOf(node.id to node.copy(locked = true)))))
        assertIs<OperationResult.Rejected>(op.applyTo(base.copy(objects = mapOf(node.id to node.copy(version = 8)))))
        assertIs<OperationResult.Rejected>(op.applyTo(base.copy(objects = mapOf(node.id to node.copy(assetId = "different")))))
        assertIs<OperationResult.Rejected>(op.applyTo(base.copy(objects = emptyMap())))
        assertIs<OperationResult.Rejected>(op.applyTo(base.copy(objects = mapOf(node.id to TextNode(node.id, transform = node.transform, text = "Text")))))
        assertFails { MediaReference("", MediaKind.Image) }
        assertFails { MediaReference("asset", MediaKind.Image, " ") }
        assertFails { UpdateMediaReferenceOperation("bad", listOf(change, change)) }
    }

    @Test fun batchIsAtomicWhenSecondObjectConflicts() {
        val second = node.copy(id = CanvasObjectId("second"))
        val workspace = base.copy(objects = base.objects + (second.id to second))
        val op = UpdateMediaReferenceOperation("batch", listOf(change, change.copy(objectId = second.id, expectedVersion = 6)))
        assertIs<OperationResult.Rejected>(op.applyTo(workspace))
        assertEquals(node, workspace.objects[node.id])
    }

    @Test fun mapsStableReferencesAndExplicitThumbnailClearWithOrderedBatchVersions() {
        val op = UpdateMediaReferenceOperation("batch", listOf(change, change.copy(objectId = CanvasObjectId("second"),
            after = MediaReference("gif", MediaKind.Gif, "new-poster"))))
        val dtos = op.toExpandedDtos(21)
        assertEquals(listOf(22L, 23L), dtos.map { it.clientSeq })
        assertEquals(2, dtos.map { it.operationId }.distinct().size)
        assertEquals("update_object", dtos.first().kind)
        assertEquals(mapOf("media" to 7L), dtos.first().expectedObjectVersions)
        assertEquals(setOf("objectId", "properties"), dtos.first().payload.keys)
        val properties = dtos.first().payload.getValue("properties").jsonObject
        assertEquals(setOf("assetId", "mediaKind", "thumbnailAssetId"), properties.keys)
        assertEquals(JsonNull, properties["thumbnailAssetId"])
        assertEquals("image", properties.getValue("mediaKind").jsonPrimitive.content)
        assertEquals("new-poster", dtos.last().payload.getValue("properties").jsonObject.getValue("thumbnailAssetId").jsonPrimitive.content)
    }
}
