package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetImportCoordinator
import cg.creamgod.boarderless.data.AssetImportException
import cg.creamgod.boarderless.data.AssetImportIssue
import cg.creamgod.boarderless.data.AssetImportStage
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.AssetTransferGateway
import cg.creamgod.boarderless.data.AssetTransferSource
import cg.creamgod.boarderless.data.AssetUploadTicket
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.validateAssetTransferSource
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AssetImportCoordinatorTest {
    @Test
    fun readyUploadEmitsOrderedStagesAndDoesNotCleanup() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready)
        val stages = mutableListOf<AssetImportStage>()

        val imported = AssetImportCoordinator(gateway).import(session(), source()) { stages += it }

        assertEquals(MediaKind.Image, imported.mediaKind)
        assertEquals("thumb-1", imported.thumbnailAssetId)
        assertEquals(
            listOf(
                AssetImportStage.Validating,
                AssetImportStage.Preparing,
                AssetImportStage.Uploading(4, 4),
                AssetImportStage.Confirming,
            ),
            stages.dropLast(1),
        )
        assertIs<AssetImportStage.Ready>(stages.last())
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun pendingConfirmationWaitsForProcessingToBecomeReady() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Pending, awaitedStatus = AssetStatus.Ready)
        val stages = mutableListOf<AssetImportStage>()

        AssetImportCoordinator(gateway).import(session(), source()) { stages += it }

        assertTrue(stages.contains(AssetImportStage.Processing(AssetStatus.Pending)))
        assertTrue(stages.contains(AssetImportStage.Processing(AssetStatus.Ready)))
        assertEquals(1, gateway.awaitCalls)
    }

    @Test
    fun thumbnailLookupFailureDoesNotDeleteReadyOriginal() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready, thumbnailThrows = true)

        val imported = AssetImportCoordinator(gateway).import(session(), source())

        assertEquals(null, imported.thumbnailAssetId)
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun thumbnailCancellationPropagatesWithoutDeletingReadyOriginal() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready, thumbnailCancelled = true)
        val stages = mutableListOf<AssetImportStage>()

        assertFailsWith<CancellationException> {
            AssetImportCoordinator(gateway).import(session(), source()) { stages += it }
        }

        assertTrue(stages.none { it is AssetImportStage.Ready })
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun confirmationForAnotherAssetFailsAndCleansOnlyPreparedAsset() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready, confirmedAssetId = "another-asset")

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), source())
        }

        assertEquals(AssetImportIssue.NotReady, error.issue)
        assertEquals(listOf("asset-1"), gateway.abandoned)
    }

    @Test
    fun truncatedUploadFailsAndCleansPreparedAsset() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready, uploadBytes = 2)

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), source())
        }

        assertEquals(AssetImportIssue.TruncatedSource, error.issue)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertEquals(0, gateway.confirmCalls)
    }

    @Test
    fun rejectedAssetNeverBecomesImportedAndIsCleanedUp() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Rejected)

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), source())
        }

        assertEquals(AssetImportIssue.Rejected, error.issue)
        assertEquals(listOf("asset-1"), gateway.abandoned)
    }

    @Test
    fun invalidSourceFailsBeforeServerMutation() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready)
        val invalid = source(mediaType = "application/pdf")

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), invalid)
        }

        assertEquals(AssetImportIssue.UnsupportedMediaType, error.issue)
        assertEquals(0, gateway.prepareCalls)
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun gifIsDistinctFromStaticImages() {
        assertEquals(MediaKind.Gif, validateAssetTransferSource(source(mediaType = "image/gif")))
        assertEquals(MediaKind.Video, validateAssetTransferSource(source(mediaType = "video/mp4")))
    }

    private fun session() = WorkspaceSession(
        userId = "user-1",
        clientId = "client-1",
        role = WorkspaceMemberRole.Owner,
        workspaceVersion = 0,
        lastServerSeq = 0,
        workspace = Workspace(WorkspaceId("workspace-1"), "Media"),
    )

    private fun source(mediaType: String = "image/png") = ByteArraySource(
        mediaType = mediaType,
        bytes = byteArrayOf(1, 2, 3, 4),
    )

    private class ByteArraySource(
        override val mediaType: String,
        private val bytes: ByteArray,
    ) : AssetTransferSource {
        override val displayName = "asset.bin"
        override val byteSize = bytes.size.toLong()
        override val checksum = "sha256:test"
        override val width: Int? = 100
        override val height: Int? = 80
        override val durationMs: Long? = null

        override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
            if (offset >= bytes.size) return byteArrayOf()
            val end = minOf(bytes.size, offset.toInt() + maximumBytes)
            return bytes.copyOfRange(offset.toInt(), end)
        }
    }

    private class FakeGateway(
        private val confirmStatus: AssetStatus,
        private val awaitedStatus: AssetStatus = AssetStatus.Ready,
        private val uploadBytes: Long = 4,
        private val thumbnailThrows: Boolean = false,
        private val thumbnailCancelled: Boolean = false,
        private val confirmedAssetId: String = "asset-1",
    ) : AssetTransferGateway {
        var prepareCalls = 0
        var confirmCalls = 0
        var awaitCalls = 0
        val abandoned = mutableListOf<String>()
        private lateinit var preparedSource: AssetTransferSource

        override suspend fun prepare(
            session: WorkspaceSession,
            source: AssetTransferSource,
        ): AssetUploadTicket {
            prepareCalls += 1
            preparedSource = source
            return AssetUploadTicket(asset(source, AssetStatus.Pending), "https://upload.invalid/signed")
        }

        override suspend fun upload(
            ticket: AssetUploadTicket,
            source: AssetTransferSource,
            onProgress: (Long) -> Unit,
        ) {
            onProgress(uploadBytes)
        }

        override suspend fun confirm(
            session: WorkspaceSession,
            ticket: AssetUploadTicket,
        ): WorkspaceAsset {
            confirmCalls += 1
            return ticket.asset.copy(id = confirmedAssetId, status = confirmStatus)
        }

        override suspend fun awaitReady(
            session: WorkspaceSession,
            assetId: String,
            onStatus: (AssetStatus) -> Unit,
        ): WorkspaceAsset {
            awaitCalls += 1
            onStatus(awaitedStatus)
            return asset(preparedSource, awaitedStatus)
        }

        override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? {
            if (thumbnailCancelled) throw CancellationException("Import cancelled")
            if (thumbnailThrows) error("thumbnail service unavailable")
            return "thumb-1"
        }

        override suspend fun abandon(session: WorkspaceSession, assetId: String) {
            abandoned += assetId
        }

        private fun asset(source: AssetTransferSource, status: AssetStatus) = WorkspaceAsset(
            id = "asset-1",
            workspaceId = WorkspaceId("workspace-1"),
            ownerId = "user-1",
            mediaType = source.mediaType,
            byteSize = source.byteSize,
            checksum = source.checksum,
            width = source.width,
            height = source.height,
            durationMs = source.durationMs,
            status = status,
            createdAt = "2026-10-01T00:00:00Z",
        )
    }
}
