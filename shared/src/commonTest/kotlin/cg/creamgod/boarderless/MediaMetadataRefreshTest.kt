package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MediaMetadataRefreshTest {
    private val workspace = WorkspaceId("workspace")
    private val asset =
        WorkspaceAsset(
            "video",
            workspace,
            "owner",
            "video/mp4",
            100,
            "sha256:fixture",
            320,
            180,
            null,
            AssetStatus.Ready,
            "2026-10-03",
        )
    private val node =
        MediaNode(
            CanvasObjectId("node"),
            transform = CanvasTransform(Vec2.Zero, CanvasSize(320f, 180f)),
            assetId = asset.id,
            mediaKind = MediaKind.Video,
        )

    @Test fun pendingAndReferencedLateDerivativesNeedFollowUpButTerminalAndForeignDoNot() {
        fun needs(
            value: WorkspaceAsset,
            nodes: List<MediaNode> = listOf(node),
        ) = needsMediaMetadataFollowUp(workspace, listOf(value), nodes)
        assertTrue(needs(asset))
        assertTrue(needs(asset.copy(status = AssetStatus.Pending), emptyList()))
        assertTrue(needs(asset.copy(thumbnailAssetId = asset.id)))
        assertFalse(needs(asset.copy(thumbnailAssetId = "poster")))
        assertFalse(needs(asset, emptyList()))
        assertFalse(needs(asset.copy(workspaceId = WorkspaceId("foreign"))))
        for (status in listOf(AssetStatus.Rejected, AssetStatus.Missing)) assertFalse(needs(asset.copy(status = status)))
    }

    @Test fun latePosterUpdatesSnapshotAndStopsWithoutChangingNode() =
        runTest {
            var reads = 0
            val snapshots = mutableListOf<List<WorkspaceAsset>>()
            val before = node.copy()
            refreshMediaMetadata(
                read = {
                    reads++
                    listOf(if (reads == 1) asset else asset.copy(thumbnailAssetId = "poster"))
                },
                publish = { snapshots.add(it) },
                needsFollowUp = { needsMediaMetadataFollowUp(workspace, it, listOf(node)) },
            )
            assertEquals(2, reads)
            assertEquals("poster", mediaNodePreviewAssetId(node, snapshots.last().single(), workspace))
            assertEquals(before, node)
        }

    @Test fun legacyMissingThumbnailHasFiniteReadBudgetAndNewerOwnerStopsPolling() =
        runTest {
            var reads = 0
            refreshMediaMetadata({
                reads++
                listOf(asset)
            }, { true }, { true })
            assertEquals(10, reads)
            reads = 0
            refreshMediaMetadata({
                reads++
                listOf(asset)
            }, { false }, { true })
            assertEquals(1, reads)
        }

    @Test fun cancelledReadCannotPublishOrContinue() =
        runTest {
            var published = false
            var reads = 0
            launch {
                refreshMediaMetadata(
                    {
                        reads++
                        currentCoroutineContext().cancel()
                        listOf(asset)
                    },
                    {
                        published = true
                        true
                    },
                    { true },
                )
            }.join()
            assertEquals(1, reads)
            assertFalse(published)
        }
}
