package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.test.*

class MaterializedAssetProjectionTest {
    private val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-reader", WorkspaceId("server"))
    private val node = MediaNode(CanvasObjectId("media"), version = 7,
        transform = CanvasTransform(Vec2(3f, 5f), CanvasSize(120f, 90f)), assetId = "original", mediaKind = MediaKind.Image,
        altText = "exact text", thumbnailAssetId = "thumbnail")
    private val workspace = Workspace(id = WorkspaceId("local"), title = "Board", objects = mapOf(node.id to node))
    private val session = WorkspaceSession("local-owner", "local-client", WorkspaceMemberRole.Editor, workspace.version, 0, workspace)
    private fun mapping(source: String, destination: String) = LocalAssetMaterialization(binding, session.userId, session.clientId,
        workspace.id, "review", MaterializationSourceDescriptor(source, binding.workspaceId, "creator", "image/png", 10,
            "sha256:" + "b".repeat(64), 10, 10, null, "2026-10-11"), destination, "2026-10-11")
    private val first = mapping("original", "00112233-4455-6677-8899-aabbccddeeff")
    private val second = mapping("thumbnail", "11223344-5566-7788-99aa-bbccddeeff00")

    @Test fun roundTripRetainsWholeDocumentAndUnmappedThumbnailWithoutInventingBytes() {
        val projection = MaterializedAssetProjection(session, binding, listOf(first, second))
        val local = projection.toLocal(workspace)
        assertEquals(node.copy(assetId = first.localAssetId, thumbnailAssetId = second.localAssetId), local.objects[node.id])
        assertEquals(workspace, projection.toCanonical(local))
        assertEquals(workspace, session.workspace)
        val primaryOnly = MaterializedAssetProjection(session, binding, listOf(first)).toLocal(workspace)
        assertEquals("thumbnail", (primaryOnly.objects[node.id] as MediaNode).thumbnailAssetId)
        assertEquals(AssetStatus.Pending, first.pendingAsset().status)
    }

    @Test fun rejectsForeignScopeDuplicateAndAmbiguousNamespaces() {
        assertFails { MaterializedAssetProjection(session, binding, listOf(first.copy(userId = "other"))) }
        assertFails { MaterializedAssetProjection(session, binding, listOf(first.copy(clientId = "other"))) }
        assertFails { MaterializedAssetProjection(session, binding.copy(serverScope = "c".repeat(64)), listOf(first)) }
        assertFails { MaterializedAssetProjection(session, binding, listOf(first, first)) }
        assertFails { MaterializedAssetProjection(session, binding, listOf(first, second.copy(localAssetId = first.localAssetId))) }
        assertFails { MaterializedAssetProjection(session, binding, listOf(first, second.copy(source = second.source.copy(id = first.localAssetId)))) }
        val projection = MaterializedAssetProjection(session, binding, listOf(first))
        assertFails { projection.toLocal(workspace.copy(id = WorkspaceId("other"))) }
        assertFails { projection.toLocal(projection.toLocal(workspace)) }
        assertFails { projection.toCanonical(workspace) }
    }

    @Test fun rejectsWrongPrimaryAndThumbnailKindsRatherThanDroppingReferences() {
        val projection = MaterializedAssetProjection(session, binding, listOf(first, second))
        assertFails { projection.toLocal(workspace.copy(objects = mapOf(node.id to node.copy(mediaKind = MediaKind.Video)))) }
        val videoThumbnail = second.copy(source = second.source.copy(mediaType = "video/mp4"))
        assertFails { MaterializedAssetProjection(session, binding, listOf(first, videoThumbnail)).toLocal(workspace) }
    }
}
