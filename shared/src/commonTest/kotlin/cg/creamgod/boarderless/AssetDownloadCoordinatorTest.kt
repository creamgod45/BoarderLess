package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AssetDownloadCleanupTimeoutMillis
import cg.creamgod.boarderless.data.AssetDownloadCoordinator
import cg.creamgod.boarderless.data.AssetDownloadException
import cg.creamgod.boarderless.data.AssetDownloadGateway
import cg.creamgod.boarderless.data.AssetDownloadIssue
import cg.creamgod.boarderless.data.AssetDownloadSink
import cg.creamgod.boarderless.data.AssetDownloadStage
import cg.creamgod.boarderless.data.AssetDownloadTicket
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.LocalAssetReference
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssetDownloadCoordinatorTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun stalledCleanupTimesOutWithoutMaskingTruncatedDownload() =
        runTest {
            val sink = RecordingSink()
            val stalled =
                object : AssetDownloadSink by sink {
                    override suspend fun abort() {
                        sink.abort()
                        awaitCancellation()
                    }
                }
            val stages = mutableListOf<AssetDownloadStage>()
            val start = testScheduler.currentTime
            val error =
                assertFailsWith<AssetDownloadException> {
                    AssetDownloadCoordinator(FakeGateway(listOf(byteArrayOf(1, 2))))
                        .download(session(), "asset-1", stalled) { stages += it }
                }
            assertEquals(AssetDownloadIssue.Truncated, error.issue)
            assertEquals(AssetDownloadCleanupTimeoutMillis, testScheduler.currentTime - start)
            assertEquals(1, sink.abortCalls)
            assertEquals(0, sink.commitCalls)
            assertTrue(stages.none { it is AssetDownloadStage.Ready })
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledDownloadStillFinishesWhenCleanupSuspendsForever() =
        runTest {
            val sink = RecordingSink()
            val stalled =
                object : AssetDownloadSink by sink {
                    override suspend fun abort() {
                        sink.abort()
                        awaitCancellation()
                    }
                }
            val cancellation = CancellationException("Consumer left")
            val base = FakeGateway(emptyList())
            val gateway =
                object : AssetDownloadGateway by base {
                    override suspend fun download(
                        ticket: AssetDownloadTicket,
                        onChunk: suspend (ByteArray) -> Unit,
                    ): Unit = throw cancellation
                }
            val start = testScheduler.currentTime
            assertSame(
                cancellation,
                assertFailsWith<CancellationException> {
                    AssetDownloadCoordinator(gateway).download(session(), "asset-1", stalled)
                },
            )
            assertEquals(AssetDownloadCleanupTimeoutMillis, testScheduler.currentTime - start)
            assertEquals(1, sink.abortCalls)
            assertEquals(0, sink.commitCalls)
        }

    @Test fun failedCleanupDoesNotMaskVerificationFailure() =
        runTest {
            val sink = RecordingSink()
            val failure = IllegalStateException("Checksum mismatch")
            val failing =
                object : AssetDownloadSink by sink {
                    override suspend fun commit(
                        expectedByteSize: Long,
                        expectedChecksum: String,
                    ): LocalAssetReference = throw failure

                    override suspend fun abort() {
                        sink.abort()
                        error("Cleanup unavailable")
                    }
                }
            val stages = mutableListOf<AssetDownloadStage>()
            assertSame(
                failure,
                assertFailsWith<IllegalStateException> {
                    AssetDownloadCoordinator(FakeGateway(listOf(byteArrayOf(1, 2, 3, 4))))
                        .download(session(), "asset-1", failing) { stages += it }
                },
            )
            assertEquals(1, sink.abortCalls)
            assertTrue(stages.none { it is AssetDownloadStage.Ready })
        }

    @Test fun cancellationAtNativeReadBoundariesNeverPublishesReady() =
        runTest {
            listOf("authorize", "chunk", "non-cancellable chunk", "write", "download", "verifying", "commit").forEach { boundary ->
                val sink = RecordingSink()
                val stages = mutableListOf<AssetDownloadStage>()
                var returned = false
                var cancelled = false
                val base = FakeGateway(listOf(byteArrayOf(1, 2, 3, 4)))
                val gateway =
                    object : AssetDownloadGateway {
                        override suspend fun authorize(
                            session: WorkspaceSession,
                            assetId: String,
                        ): AssetDownloadTicket {
                            val ticket = base.authorize(session, assetId)
                            if (boundary == "authorize") currentCoroutineContext().cancel()
                            return ticket
                        }

                        override suspend fun download(
                            ticket: AssetDownloadTicket,
                            onChunk: suspend (ByteArray) -> Unit,
                        ) {
                            if (boundary == "non-cancellable chunk") {
                                currentCoroutineContext().cancel()
                                withContext(NonCancellable) { base.download(ticket, onChunk) }
                                return
                            }
                            if (boundary == "chunk") currentCoroutineContext().cancel()
                            base.download(ticket, onChunk)
                            if (boundary == "download") currentCoroutineContext().cancel()
                        }
                    }
                val wrappedSink =
                    object : AssetDownloadSink by sink {
                        override suspend fun writeChunk(
                            offset: Long,
                            bytes: ByteArray,
                        ) {
                            sink.writeChunk(offset, bytes)
                            if (boundary == "write") currentCoroutineContext().cancel()
                        }

                        override suspend fun commit(
                            expectedByteSize: Long,
                            expectedChecksum: String,
                        ): LocalAssetReference {
                            val local = sink.commit(expectedByteSize, expectedChecksum)
                            if (boundary == "commit") currentCoroutineContext().cancel()
                            return local
                        }
                    }
                launch {
                    val ownerContext = currentCoroutineContext()
                    try {
                        AssetDownloadCoordinator(gateway).download(session(), "asset-1", wrappedSink) {
                            stages += it
                            if (boundary == "verifying" && it == AssetDownloadStage.Verifying) ownerContext.cancel()
                        }
                        returned = true
                    } catch (_: CancellationException) {
                        cancelled = true
                    }
                }.join()
                assertTrue(cancelled, boundary)
                assertTrue(!returned && stages.none { it is AssetDownloadStage.Ready }, boundary)
                assertEquals(if (boundary == "commit") 1 else 0, sink.commitCalls, boundary)
                assertEquals(if (boundary == "commit") 0 else 1, sink.abortCalls, boundary)
                if (boundary == "authorize" || boundary == "chunk" ||
                    boundary == "non-cancellable chunk"
                ) {
                    assertTrue(sink.bytes.isEmpty(), boundary)
                }
            }
        }

    @Test fun alreadyCancelledDownloadNeverAuthorizesAndAbortsUncommittedSink() =
        runTest {
            var authorized = false
            val sink = RecordingSink()
            val base = FakeGateway(emptyList())
            val gateway =
                object : AssetDownloadGateway by base {
                    override suspend fun authorize(
                        session: WorkspaceSession,
                        assetId: String,
                    ): AssetDownloadTicket {
                        authorized = true
                        return base.authorize(session, assetId)
                    }
                }
            launch {
                currentCoroutineContext().cancel()
                try {
                    AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink)
                } catch (
                    _: CancellationException,
                ) {
                    // Expected.
                }
            }.join()
            assertTrue(!authorized)
            assertEquals(1, sink.abortCalls)
        }

    @Test
    fun readyAssetStreamsToSinkThenCommits() =
        runTest {
            val sink = RecordingSink()
            val stages = mutableListOf<AssetDownloadStage>()
            val gateway = FakeGateway(listOf(byteArrayOf(1, 2), byteArrayOf(3, 4)))

            val local = AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink) { stages += it }

            assertEquals("cache:asset-1", local.token)
            assertContentEquals(byteArrayOf(1, 2, 3, 4), sink.bytes.toByteArray())
            assertEquals(listOf(0L, 2L), sink.offsets)
            assertEquals(1, sink.commitCalls)
            assertEquals(0, sink.abortCalls)
            assertIs<AssetDownloadStage.Ready>(stages.last())
        }

    @Test
    fun truncatedDownloadAbortsAndNeverCommits() =
        runTest {
            val sink = RecordingSink()
            val error =
                assertFailsWith<AssetDownloadException> {
                    AssetDownloadCoordinator(FakeGateway(listOf(byteArrayOf(1, 2)))).download(
                        session(),
                        "asset-1",
                        sink,
                    )
                }

            assertEquals(AssetDownloadIssue.Truncated, error.issue)
            assertEquals(0, sink.commitCalls)
            assertEquals(1, sink.abortCalls)
        }

    @Test
    fun oversizedOrEmptyChunksFailClosed() =
        runTest {
            listOf(
                FakeGateway(listOf(byteArrayOf())) to AssetDownloadIssue.EmptyChunk,
                FakeGateway(listOf(byteArrayOf(1, 2, 3, 4, 5))) to AssetDownloadIssue.TooManyBytes,
            ).forEach { (gateway, issue) ->
                val sink = RecordingSink()
                val error =
                    assertFailsWith<AssetDownloadException> {
                        AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink)
                    }
                assertEquals(issue, error.issue)
                assertEquals(1, sink.abortCalls)
            }
        }

    @Test
    fun pendingAssetIsRejectedBeforeAnyBytesAreWritten() =
        runTest {
            val sink = RecordingSink()
            val gateway = FakeGateway(listOf(byteArrayOf(1, 2, 3, 4)), status = AssetStatus.Pending)

            val error =
                assertFailsWith<AssetDownloadException> {
                    AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink)
                }

            assertEquals(AssetDownloadIssue.NotReady, error.issue)
            assertTrue(sink.bytes.isEmpty())
            assertEquals(1, sink.abortCalls)
        }

    private fun session() =
        WorkspaceSession(
            userId = "user-1",
            clientId = "client-1",
            role = WorkspaceMemberRole.Owner,
            workspaceVersion = 0,
            lastServerSeq = 0,
            workspace = Workspace(WorkspaceId("workspace-1"), "Media"),
        )

    private class FakeGateway(
        private val chunks: List<ByteArray>,
        private val status: AssetStatus = AssetStatus.Ready,
    ) : AssetDownloadGateway {
        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ) = AssetDownloadTicket(
            asset =
                WorkspaceAsset(
                    id = assetId,
                    workspaceId = session.workspace.id,
                    ownerId = session.userId,
                    mediaType = "image/png",
                    byteSize = 4,
                    checksum = "sha256:test",
                    width = 10,
                    height = 10,
                    durationMs = null,
                    status = status,
                    createdAt = "2026-10-01T00:00:00Z",
                ),
            downloadUrl = "https://download.invalid/signed",
        )

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            chunks.forEach { onChunk(it) }
        }
    }

    private class RecordingSink : AssetDownloadSink {
        val bytes = mutableListOf<Byte>()
        val offsets = mutableListOf<Long>()
        var commitCalls = 0
        var abortCalls = 0

        override suspend fun writeChunk(
            offset: Long,
            bytes: ByteArray,
        ) {
            offsets += offset
            this.bytes += bytes.toList()
        }

        override suspend fun commit(
            expectedByteSize: Long,
            expectedChecksum: String,
        ): LocalAssetReference {
            commitCalls += 1
            assertEquals(expectedByteSize, bytes.size.toLong())
            return LocalAssetReference("cache:asset-1", "image/png")
        }

        override suspend fun abort() {
            abortCalls += 1
        }
    }
}
