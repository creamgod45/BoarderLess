package cg.creamgod.boarderless

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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AssetDownloadCoordinatorTest {
    @Test
    fun readyAssetStreamsToSinkThenCommits() = runTest {
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
    fun truncatedDownloadAbortsAndNeverCommits() = runTest {
        val sink = RecordingSink()
        val error = assertFailsWith<AssetDownloadException> {
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
    fun oversizedOrEmptyChunksFailClosed() = runTest {
        listOf(
            FakeGateway(listOf(byteArrayOf())) to AssetDownloadIssue.EmptyChunk,
            FakeGateway(listOf(byteArrayOf(1, 2, 3, 4, 5))) to AssetDownloadIssue.TooManyBytes,
        ).forEach { (gateway, issue) ->
            val sink = RecordingSink()
            val error = assertFailsWith<AssetDownloadException> {
                AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink)
            }
            assertEquals(issue, error.issue)
            assertEquals(1, sink.abortCalls)
        }
    }

    @Test
    fun pendingAssetIsRejectedBeforeAnyBytesAreWritten() = runTest {
        val sink = RecordingSink()
        val gateway = FakeGateway(listOf(byteArrayOf(1, 2, 3, 4)), status = AssetStatus.Pending)

        val error = assertFailsWith<AssetDownloadException> {
            AssetDownloadCoordinator(gateway).download(session(), "asset-1", sink)
        }

        assertEquals(AssetDownloadIssue.NotReady, error.issue)
        assertTrue(sink.bytes.isEmpty())
        assertEquals(1, sink.abortCalls)
    }

    private fun session() = WorkspaceSession(
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
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(
            asset = WorkspaceAsset(
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

        override suspend fun writeChunk(offset: Long, bytes: ByteArray) {
            offsets += offset
            this.bytes += bytes.toList()
        }

        override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference {
            commitCalls += 1
            assertEquals(expectedByteSize, bytes.size.toLong())
            return LocalAssetReference("cache:asset-1", "image/png")
        }

        override suspend fun abort() {
            abortCalls += 1
        }
    }
}
