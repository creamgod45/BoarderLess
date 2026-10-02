package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class MediaAssetLibraryTest {
    private val workspaceId = WorkspaceId("w")
    private fun asset(id: String = "a", type: String = "image/png", status: AssetStatus = AssetStatus.Ready) =
        WorkspaceAsset(id, workspaceId, "u", type, 123, "sha256:abc", 640, 480, null, status, "2026-10-02")
    private fun node(asset: WorkspaceAsset) = mediaNodeFromReadyAsset(asset, workspaceId, CanvasObjectId("node"), Vec2(500f, 400f), 1f, 4, "Photo")
    private fun session(user: String = "u", client: String = "c", role: WorkspaceMemberRole = WorkspaceMemberRole.Editor,
        id: WorkspaceId = workspaceId) = WorkspaceSession(user, client, role, 0, 0, Workspace(id, "Library"))

    @Test fun catalogIsWorkspaceScopedDeduplicatedSearchableAndStable() {
        val first = asset(); val later = asset("b", "video/mp4").copy(createdAt = "2026-10-03")
        val foreign = asset("foreign").copy(workspaceId = WorkspaceId("other"))
        val assets = listOf(first, later, first, foreign)
        assertEquals(listOf("b", "a"), mediaAssetLibraryEntries(workspaceId, assets, emptyList()).map { it.asset.id })
        assertEquals(listOf("a"), mediaAssetLibraryEntries(workspaceId, assets, listOf(node(first)), "PHOTO png").map { it.asset.id })
        assertEquals(listOf("b"), mediaAssetLibraryEntries(workspaceId, assets, emptyList(), kind = MediaKind.Video).map { it.asset.id })
        assertTrue(mediaAssetLibraryEntries(workspaceId, assets, emptyList(), "not found").isEmpty())
    }

    @Test fun onlyReadySupportedAndWithinSizeLimitIsInsertable() {
        AssetStatus.entries.forEach { status -> assertEquals(status == AssetStatus.Ready, MediaAssetLibraryEntry(asset(status = status), "a").insertable) }
        assertFalse(MediaAssetLibraryEntry(asset(type = "application/pdf"), "a").insertable)
        assertFalse(MediaAssetLibraryEntry(asset().copy(byteSize = MaxWorkspaceAssetBytes + 1), "a").insertable)
        assertTrue(MediaAssetLibraryEntry(asset().copy(byteSize = MaxWorkspaceAssetBytes), "a").insertable)
    }

    @Test fun libraryPreviewNeverFetchesVideoOriginalOrOversizedImage() {
        assertEquals("a", MediaAssetLibraryEntry(asset(), "a").previewAssetId)
        assertEquals("a", MediaAssetLibraryEntry(asset(type = "image/gif"), "a").previewAssetId)
        assertNull(MediaAssetLibraryEntry(asset(type = "video/mp4"), "a").previewAssetId)
        assertNull(MediaAssetLibraryEntry(asset().copy(byteSize = AssetPreviewPolicy.MaxEncodedBytes + 1), "a").previewAssetId)
        assertEquals("poster", MediaAssetLibraryEntry(asset(type = "video/mp4").copy(thumbnailAssetId = "poster"), "a").previewAssetId)
        assertNull(MediaAssetLibraryEntry(asset(status = AssetStatus.Pending).copy(thumbnailAssetId = "poster"), "a").previewAssetId)
    }

    @Test fun reusableNodeReferencesExistingAssetAndThumbnailWithSameSizingAsImport() {
        val stored = asset(type = "video/mp4").copy(thumbnailAssetId = "poster")
        val reused = node(stored)
        assertEquals("a", reused.assetId); assertEquals("poster", reused.thumbnailAssetId)
        assertEquals(MediaKind.Video, reused.mediaKind); assertEquals("Photo", reused.altText)
        assertEquals(CanvasSize(320f, 240f), reused.transform.size)
        assertEquals(Vec2(340f, 280f), reused.transform.position)
        assertEquals(4L, reused.zIndex)
        assertEquals(CanvasSize(320f, 180f), node(stored.copy(width = null, height = null)).transform.size)
        assertEquals(360f, node(stored.copy(width = 1, height = 1000)).transform.size.height)
        assertEquals(80f, node(stored.copy(width = 1000, height = 1)).transform.size.height)
    }

    @Test fun freshMetadataBecomingUnavailableForeignOrUnsupportedNeverCreatesNode() {
        AssetStatus.entries.filter { it != AssetStatus.Ready }.forEach { status ->
            assertFailsWith<IllegalArgumentException> { node(asset(status = status)) }
        }
        assertFailsWith<IllegalArgumentException> { node(asset().copy(workspaceId = WorkspaceId("other"))) }
        assertFailsWith<IllegalArgumentException> { node(asset(type = "application/pdf")) }
        assertFailsWith<IllegalArgumentException> { node(asset().copy(byteSize = MaxWorkspaceAssetBytes + 1)) }
        assertFailsWith<IllegalArgumentException> {
            mediaNodeFromReadyAsset(asset(), workspaceId, CanvasObjectId("n"), Vec2.Zero, 1f, 0, "a", requestedAssetId = "different")
        }
    }

    @Test fun insertionRejectsRevokedIdentityWorkspaceAndOfflineSessionAfterAwait() {
        val opened = session()
        assertTrue(canInsertMediaInSession(opened, session(role = WorkspaceMemberRole.Owner), false))
        assertFalse(canInsertMediaInSession(opened, session(role = WorkspaceMemberRole.Viewer), false))
        assertFalse(canInsertMediaInSession(opened, session(role = WorkspaceMemberRole.Commenter), false))
        assertFalse(canInsertMediaInSession(opened, session(user = "other"), false))
        assertFalse(canInsertMediaInSession(opened, session(client = "other"), false))
        assertFalse(canInsertMediaInSession(opened, session(id = WorkspaceId("other")), false))
        assertFalse(canInsertMediaInSession(opened, opened, true))
        assertFalse(canInsertMediaInSession(opened, null, false))
    }

    @Test fun invalidPlacementIsRejectedWithoutChangingAsset() {
        assertFailsWith<IllegalArgumentException> { mediaNodeFromReadyAsset(asset(), workspaceId, CanvasObjectId("n"), Vec2.Zero, Float.NaN, 0, "a") }
        assertFailsWith<IllegalArgumentException> { mediaNodeFromReadyAsset(asset(), workspaceId, CanvasObjectId("n"), Vec2(Float.POSITIVE_INFINITY, 0f), 1f, 0, "a") }
    }
}
