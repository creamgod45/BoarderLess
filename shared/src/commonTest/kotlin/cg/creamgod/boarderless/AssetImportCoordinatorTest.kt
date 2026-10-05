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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertSame

class AssetImportCoordinatorTest {
    @Test fun failureBeforeFirstUploadByteStillReportsUploadBoundaryAndCleansPendingOnly() = runTest {
        val failure = IllegalStateException("storage connection failed")
        val gateway = FakeGateway(AssetStatus.Ready, uploadFailure = failure)
        val stages = mutableListOf<AssetImportStage>()
        assertSame(failure, assertFailsWith<IllegalStateException> {
            AssetImportCoordinator(gateway).import(session(), source()) { stages += it }
        })
        assertEquals(listOf(AssetImportStage.Validating, AssetImportStage.Preparing, AssetImportStage.Uploading(0, 4)), stages)
        assertEquals(0, gateway.confirmCalls)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertTrue(stages.none { it is AssetImportStage.Ready || it is AssetImportStage.RecoveryRequired })
    }
    @Test fun nonCooperativeConfirmationCannotReturnReadyAfterItsOwnerWasCancelled() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready, cancelOnConfirm = true)
        val stages = mutableListOf<AssetImportStage>()
        val job = async { AssetImportCoordinator(gateway).import(session(), source()) { stages += it } }
        assertFailsWith<CancellationException> { job.await() }
        assertTrue(stages.none { it is AssetImportStage.Ready })
        assertEquals(AssetImportStage.RecoveryRequired("asset-1"), stages.last())
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test fun lostCompletionResponsePreservesAssetAndOriginalErrorForReconciliation() = runTest {
        val failure = IllegalStateException("Completion response lost")
        val gateway = FakeGateway(AssetStatus.Ready, confirmFailure = failure)
        val stages = mutableListOf<AssetImportStage>()
        assertSame(failure, assertFailsWith<IllegalStateException> {
            AssetImportCoordinator(gateway).import(session(), source()) { stages += it }
        })
        assertEquals(1, gateway.confirmCalls)
        assertTrue(gateway.abandoned.isEmpty())
        assertEquals(AssetImportStage.RecoveryRequired("asset-1"), stages.last())
        assertTrue(stages.none { it is AssetImportStage.Ready })
    }

    @Test fun completionCancellationRemainsCancellationWithoutDestructiveCleanup() = runTest {
        val cancellation = CancellationException("Cancelled after sending complete")
        val gateway = FakeGateway(AssetStatus.Ready, confirmFailure = cancellation)
        val stages = mutableListOf<AssetImportStage>()
        assertSame(cancellation, assertFailsWith<CancellationException> {
            AssetImportCoordinator(gateway).import(session(), source()) { stages += it }
        })
        assertTrue(gateway.abandoned.isEmpty())
        assertEquals(AssetImportStage.RecoveryRequired("asset-1"), stages.last())
    }

    @Test fun cancellationDuringProcessingPreservesPossiblyAcceptedCompletion() = runTest {
        val gateway = FakeGateway(AssetStatus.Pending, awaitFailure = CancellationException("Processing wait cancelled"))
        val stages = mutableListOf<AssetImportStage>()
        assertFailsWith<CancellationException> { AssetImportCoordinator(gateway).import(session(), source()) { stages += it } }
        assertEquals(1, gateway.awaitCalls)
        assertTrue(gateway.abandoned.isEmpty())
        assertEquals(AssetImportStage.RecoveryRequired("asset-1"), stages.last())
    }

    @Test fun processingTimeoutDoesNotDeleteUploadOrDeclareItReady() = runTest {
        val gateway = FakeGateway(AssetStatus.Pending, awaitedStatus = AssetStatus.Pending)
        val stages = mutableListOf<AssetImportStage>()
        val failure = assertFailsWith<AssetImportException> { AssetImportCoordinator(gateway).import(session(), source()) { stages += it } }
        assertEquals(AssetImportIssue.NotReady, failure.issue)
        assertTrue(gateway.abandoned.isEmpty())
        assertTrue(stages.none { it is AssetImportStage.Ready })
        assertEquals(AssetImportStage.RecoveryRequired("asset-1"), stages.last())
    }

    @Test fun recoveryNotificationFailureCannotMaskCompletionError() = runTest {
        val failure = IllegalArgumentException("Lost reply")
        val gateway = FakeGateway(AssetStatus.Ready, confirmFailure = failure)
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            AssetImportCoordinator(gateway).import(session(), source()) {
                if (it is AssetImportStage.RecoveryRequired) error("UI failed")
            }
        })
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test fun invalidOrAlreadyReadyPrepareResponseIsNotAuthorityToDeleteIt() = runTest {
        listOf(FakeGateway(AssetStatus.Ready, preparedStatus = AssetStatus.Ready),
            FakeGateway(AssetStatus.Ready, preparedWorkspace = WorkspaceId("other"))).forEach { gateway ->
            assertFailsWith<AssetImportException> { AssetImportCoordinator(gateway).import(session(), source()) }
            assertTrue(gateway.abandoned.isEmpty())
            assertEquals(0, gateway.confirmCalls)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun pendingCleanupTimeoutPreservesTruncationErrorAndDoesNotHangImport() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready, uploadBytes = 2, hangAbandon = true)
        val failure = assertFailsWith<AssetImportException> { AssetImportCoordinator(gateway).import(session(), source()) }
        assertEquals(AssetImportIssue.TruncatedSource, failure.issue)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test fun cancellationBeforeCompleteStillCleansValidatedPendingPreparation() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready)
        val stages = mutableListOf<AssetImportStage>()
        assertFailsWith<CancellationException> { AssetImportCoordinator(gateway).import(session(), source()) {
            stages += it
            if (it == AssetImportStage.Confirming) throw CancellationException("Cancelled before request")
        } }
        assertEquals(0, gateway.confirmCalls)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertTrue(stages.none { it is AssetImportStage.RecoveryRequired })
    }

    @Test fun recoveryReminderIsRecordedBeforeCompletionTransport() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready)
        val recorded = mutableListOf<String>()
        AssetImportCoordinator(gateway, beforeComplete = { assetId ->
            assertEquals(0, gateway.confirmCalls)
            recorded += assetId
        }).import(session(), source())
        assertEquals(listOf("asset-1"), recorded)
        assertEquals(1, gateway.confirmCalls)
    }

    @Test fun suspendingRecoveryBarrierIsAwaitedAndCancellationDoesNotComplete() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready)
        val reached = kotlinx.coroutines.CompletableDeferred<Unit>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()
        val stages = mutableListOf<AssetImportStage>()
        val job = async {
            AssetImportCoordinator(gateway, beforeComplete = {
                reached.complete(Unit)
                release.await()
            }).import(session(), source()) { stages += it }
        }
        reached.await()
        assertEquals(0, gateway.confirmCalls)
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }
        job.join()
        assertEquals(0, gateway.confirmCalls)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertTrue(stages.none { it is AssetImportStage.Ready || it is AssetImportStage.RecoveryRequired })
    }

    @Test fun recoveryStorageFailureStopsCompletionAndCleansOnlyPendingUpload() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready)
        val failure = IllegalStateException("storage full")
        val stages = mutableListOf<AssetImportStage>()
        val thrown = assertFailsWith<IllegalStateException> {
            AssetImportCoordinator(gateway, beforeComplete = { throw failure }).import(session(), source()) { stages += it }
        }
        assertTrue(thrown === failure)
        assertEquals(0, gateway.confirmCalls)
        assertEquals(listOf("asset-1"), gateway.abandoned)
        assertTrue(stages.none { it is AssetImportStage.Ready || it is AssetImportStage.RecoveryRequired })
    }

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
                AssetImportStage.Uploading(0, 4),
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
    fun readyConfirmationThumbnailDoesNotRequireAnotherMetadataRequest() = runTest {
        val gateway = FakeGateway(AssetStatus.Ready, readyThumbnail = "confirmed-thumb", thumbnailThrows = true)
        val imported = AssetImportCoordinator(gateway).import(session(), source())
        assertEquals("confirmed-thumb", imported.thumbnailAssetId)
        assertEquals(0, gateway.thumbnailCalls)
        assertEquals(0, gateway.awaitCalls)
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun processingResultThumbnailIsUsedWithoutAnotherMetadataRequest() = runTest {
        val gateway = FakeGateway(AssetStatus.Pending, readyThumbnail = "processed-thumb", thumbnailThrows = true)
        val imported = AssetImportCoordinator(gateway).import(session(), source())
        assertEquals("processed-thumb", imported.thumbnailAssetId)
        assertEquals(1, gateway.awaitCalls)
        assertEquals(0, gateway.thumbnailCalls)
        assertTrue(gateway.abandoned.isEmpty())
    }

    @Test
    fun selfReferenceAndInvalidLookupDoNotBecomeThumbnailReferences() = runTest {
        for (lookup in listOf<String?>(null, "", " ", "asset-1", "late-thumb")) {
            val gateway = FakeGateway(AssetStatus.Ready, readyThumbnail = "asset-1", lookupThumbnail = lookup)
            val imported = AssetImportCoordinator(gateway).import(session(), source())
            assertEquals(lookup.takeIf { it == "late-thumb" }, imported.thumbnailAssetId)
            assertEquals(1, gateway.thumbnailCalls)
            assertTrue(gateway.abandoned.isEmpty())
        }
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
    fun confirmationForAnotherAssetFailsWithoutDeletingUncertainPreparedAsset() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Ready, confirmedAssetId = "another-asset")

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), source())
        }

        assertEquals(AssetImportIssue.NotReady, error.issue)
        assertTrue(gateway.abandoned.isEmpty())
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
    fun rejectedAssetNeverBecomesImportedAndIsRetainedForStatusInspection() = runTest {
        val gateway = FakeGateway(confirmStatus = AssetStatus.Rejected)

        val error = assertFailsWith<AssetImportException> {
            AssetImportCoordinator(gateway).import(session(), source())
        }

        assertEquals(AssetImportIssue.Rejected, error.issue)
        assertTrue(gateway.abandoned.isEmpty())
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
        private val confirmFailure: Exception? = null,
        private val awaitFailure: Exception? = null,
        private val preparedStatus: AssetStatus = AssetStatus.Pending,
        private val preparedWorkspace: WorkspaceId = WorkspaceId("workspace-1"),
        private val hangAbandon: Boolean = false,
        private val cancelOnConfirm: Boolean = false,
        private val readyThumbnail: String? = null,
        private val lookupThumbnail: String? = "thumb-1",
        private val uploadFailure: Exception? = null,
    ) : AssetTransferGateway {
        var prepareCalls = 0
        var confirmCalls = 0
        var awaitCalls = 0
        var thumbnailCalls = 0
        val abandoned = mutableListOf<String>()
        private lateinit var preparedSource: AssetTransferSource

        override suspend fun prepare(
            session: WorkspaceSession,
            source: AssetTransferSource,
        ): AssetUploadTicket {
            prepareCalls += 1
            preparedSource = source
            return AssetUploadTicket(asset(source, preparedStatus).copy(workspaceId = preparedWorkspace), "https://upload.invalid/signed")
        }

        override suspend fun upload(
            ticket: AssetUploadTicket,
            source: AssetTransferSource,
            onProgress: (Long) -> Unit,
        ) {
            uploadFailure?.let { throw it }
            onProgress(uploadBytes)
        }

        override suspend fun confirm(
            session: WorkspaceSession,
            ticket: AssetUploadTicket,
        ): WorkspaceAsset {
            confirmCalls += 1
            if (cancelOnConfirm) currentCoroutineContext().cancel(CancellationException("Owner left while confirming"))
            confirmFailure?.let { throw it }
            return ticket.asset.copy(id = confirmedAssetId, status = confirmStatus,
                thumbnailAssetId = readyThumbnail.takeIf { confirmStatus == AssetStatus.Ready })
        }

        override suspend fun awaitReady(
            session: WorkspaceSession,
            assetId: String,
            onStatus: (AssetStatus) -> Unit,
        ): WorkspaceAsset {
            awaitCalls += 1
            awaitFailure?.let { throw it }
            onStatus(awaitedStatus)
            return asset(preparedSource, awaitedStatus).copy(
                thumbnailAssetId = readyThumbnail.takeIf { awaitedStatus == AssetStatus.Ready })
        }

        override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? {
            thumbnailCalls += 1
            if (thumbnailCancelled) throw CancellationException("Import cancelled")
            if (thumbnailThrows) error("thumbnail service unavailable")
            return lookupThumbnail
        }

        override suspend fun abandon(session: WorkspaceSession, assetId: String) {
            abandoned += assetId
            if (hangAbandon) awaitCancellation()
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
