package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssetPreviewPolicyTest {
    private val session = WorkspaceSession(
        "viewer", "client", WorkspaceMemberRole.Viewer, 0, 0,
        Workspace(WorkspaceId("workspace-1"), "Preview"),
    )
    private val asset = WorkspaceAsset(
        "asset-1", session.workspace.id, "owner", "image/png", 100, "sha256:abc",
        2, 3, null, AssetStatus.Ready, "2026-10-01",
    )

    @Test
    fun viewerCanPreviewEverySupportedImageType() {
        listOf("image/png", "image/jpeg", "image/webp", "image/gif").forEach { mime ->
            AssetPreviewPolicy.validate(ticket(asset.copy(mediaType = mime)), session, asset.id)
        }
    }

    @Test
    fun foreignAssetWorkspaceOrBlankReferenceRejected() {
        assertFailsWith<IllegalArgumentException> { AssetPreviewPolicy.validate(ticket(asset), session, "") }
        assertFailsWith<IllegalArgumentException> { AssetPreviewPolicy.validate(ticket(asset), session, "foreign") }
        assertFailsWith<IllegalArgumentException> {
            AssetPreviewPolicy.validate(ticket(asset.copy(workspaceId = WorkspaceId("foreign"))), session, asset.id)
        }
    }

    @Test
    fun onlyReadyImagesAllowed() {
        AssetStatus.entries.filter { it != AssetStatus.Ready }.forEach { status ->
            assertFailsWith<IllegalArgumentException> {
                AssetPreviewPolicy.validate(ticket(asset.copy(status = status)), session, asset.id)
            }
        }
        listOf("video/mp4", "video/webm", "image/svg+xml", "text/html").forEach { mime ->
            assertFailsWith<IllegalArgumentException> {
                AssetPreviewPolicy.validate(ticket(asset.copy(mediaType = mime)), session, asset.id)
            }
        }
    }

    @Test
    fun encodedSizeHasAnExactUpperBound() {
        AssetPreviewPolicy.validate(ticket(asset.copy(byteSize = AssetPreviewPolicy.MaxEncodedBytes)), session, asset.id)
        assertFailsWith<IllegalArgumentException> {
            AssetPreviewPolicy.validate(ticket(asset.copy(byteSize = AssetPreviewPolicy.MaxEncodedBytes + 1)), session, asset.id)
        }
    }

    @Test
    fun realDecodedDimensionsAreCheckedWithoutIntegerOverflow() {
        AssetPreviewPolicy.validateDimensions(4000, 3000)
        listOf(4000 to 3001, 0 to 2, 2 to -1, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (w, h) ->
            assertFailsWith<IllegalArgumentException> { AssetPreviewPolicy.validateDimensions(w, h) }
        }
    }

    private fun ticket(asset: WorkspaceAsset) = AssetDownloadTicket(asset, "https://storage.invalid/signed")

    @Test
    fun sampledPreviewFitsEdgeEvenForOddDimensions() {
        assertEquals(1, AssetPreviewPolicy.sampleSize(1024, 800, 1024))
        assertEquals(2, AssetPreviewPolicy.sampleSize(1025, 800, 1024))
        assertEquals(4, AssetPreviewPolicy.sampleSize(4000, 3000, 1024))
        assertEquals(16, AssetPreviewPolicy.sampleSize(1, 12000, 1024))
        assertFailsWith<IllegalArgumentException> { AssetPreviewPolicy.sampleSize(1, 1, 0) }
    }
}
