package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.test.*

class BrowserVideoPlaybackTest {
    @Test fun nativeWebmClockFramesPauseCompletionReplayAndRelease() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            val gateway = Gateway(fixture())
            val player = loadBrowserVideo(gateway, session(), "video") as BrowserVideoPlayback
            try {
                assertEquals(1, gateway.downloads, "One verified download"); assertEquals(1, gateway.authorizations, "One authorization")
                assertEquals(32, player.state.value.width, "Prepared width"); assertEquals(32, player.state.value.height, "Prepared height")
                assertTrue(player.state.value.durationMs > 0); assertTrue(player.state.value.muted); assertFalse(player.state.value.playing, "Initial player must be paused")
                assertEquals(0L, player.state.value.positionMs, "Duration probe must restore the initial position")
                val first = player.frame(); val white = IntArray(1); first.readPixels(white, width = 1, height = 1)
                assertEquals(0xffffffff.toInt(), white[0], "Initial paused frame must be white")
                coroutineScope {
                    val capture = async(start = CoroutineStart.UNDISPATCHED) { player.frame() }
                    assertFalse(capture.isCompleted, "Capture cancellation must target a pending native frame")
                    capture.cancelAndJoin()
                }
                player.setPlaying(true); assertFalse(player.state.value.playing, "Detached player must stay paused")
                player.attached(true)
                withTimeout(5000) { player.state.first { it.playing && it.positionMs > 0 } }
                player.setPlaying(false); assertFalse(player.state.value.playing, "Pause command must publish paused state")
                player.setMuted(false); assertFalse(player.state.value.muted); player.setMuted(true)
                player.seekTo(player.state.value.durationMs / 2)
                delay(150)
                val next = player.frame(); val blue = IntArray(1); next.readPixels(blue, width = 1, height = 1)
                assertTrue(blue[0] != white[0], "Seek must capture a later blue frame")
                first.readPixels(white, width = 1, height = 1); assertEquals(0xffffffff.toInt(), white[0], "Previous frame must remain immutable")
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.ended } }
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.playing && !it.ended } }
                player.attached(false); assertFalse(player.state.value.playing, "Detach must publish paused state")
            } finally { player.release() }
            player.release(); assertTrue(player.state.value.released)
            assertEquals("{\"created\":1,\"revoked\":1}", browserVideoAccounting(false), "Release must revoke the verified Blob once")
            assertFails { player.frame() }
        }
    }
    @Test fun hashTruncationAndCancellationNeverPublishBlobUrl() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            assertFails { loadBrowserVideo(Gateway(ByteArray(32), badHash = true), session(), "video") }
            assertFails { loadBrowserVideo(Gateway(ByteArray(32), truncate = true), session(), "video") }
            assertFailsWith<CancellationException> { loadBrowserVideo(Gateway(ByteArray(32), cancel = true), session(), "video") }
            assertEquals("{\"created\":0,\"revoked\":0}", browserVideoAccounting(false))
        }
    }
    @Test fun validHashButCorruptVideoRevokesItsUrl() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            assertFails { loadBrowserVideo(Gateway(ByteArray(32)), session(), "video") }
            assertEquals("{\"created\":1,\"revoked\":1}", browserVideoAccounting(false))
        }
    }
    @Test fun pageHideClosesRealPlayerAndRevokesBlob() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            val player = loadBrowserVideo(Gateway(fixture()), session(), "video")
            try {
                hideBrowserVideoPage()
                assertTrue(player.state.value.released)
                assertFalse(player.state.value.playing)
            } finally { player.release() }
            assertEquals("{\"created\":1,\"revoked\":1}", browserVideoAccounting(false))
        }
    }
    @Test fun foreignPendingAndImageMetadataNeverDownloads() = runTest {
        withContext(Dispatchers.Default) {
            listOf(Gateway(ByteArray(32), foreign = true), Gateway(ByteArray(32), pending = true), Gateway(ByteArray(32), mime = "image/png")).forEach { gateway ->
                assertFails { loadBrowserVideo(gateway, session(), "video") }; assertEquals(0, gateway.downloads)
            }
        }
    }
    @Test fun rejectedPlayDoesNotPublishPlayingAndCanRetry() = runTest {
        withContext(Dispatchers.Default) {
            val player = loadBrowserVideo(Gateway(fixture()), session(), "video") as BrowserVideoPlayback
            try {
                player.attached(true)
                rejectNextBrowserVideoPlay()
                assertFails { player.setPlaying(true) }
                assertFalse(player.state.value.playing)
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.playing } }
            } finally { player.release() }
        }
    }
    @Test fun pendingPreparationCancellationRevokesUnhandedUrl() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            val sink = BrowserVideoSink("pending-test", "video/webm")
            try {
                val bytes = ByteArray(32)
                sink.writeChunk(0, bytes)
                sink.commit(32, "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
                coroutineScope {
                    val opening = async(start = CoroutineStart.UNDISPATCHED) { BrowserVideoPlayback.open(sink.id) }
                    assertFalse(opening.isCompleted, "Preparation cancellation must target a live pending request")
                    opening.cancelAndJoin()
                }
                assertEquals("{\"created\":1,\"revoked\":1}", browserVideoAccounting(false))
            } finally { sink.abort() }
        }
    }
    @Test fun networkChunksLargerThanBridgeLimitAreSplitWithoutPublishing() = runTest {
        withContext(Dispatchers.Default) {
            browserVideoAccounting(true)
            val sink = BrowserVideoSink("large-chunk-test", "video/mp4")
            try {
                val bytes = ByteArray(DefaultAssetUploadChunkBytes + 1) { (it % 251).toByte() }
                sink.writeChunk(0, bytes)
                val hash = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                val local = sink.commit(bytes.size.toLong(), hash)
                assertEquals("video/mp4", local.mediaType)
                assertEquals("{\"created\":0,\"revoked\":0}", browserVideoAccounting(false))
            } finally { sink.abort() }
        }
    }
    private suspend fun fixture(): ByteArray = suspendCancellableCoroutine<ByteArray> { pending ->
        recordBrowserVideoFixture { bytes, error ->
            if (pending.isActive) {
                if (bytes != null) pending.resume(Base64.decode(bytes)) else pending.resumeWithException(IllegalStateException(error))
            }
        }
    }.also { bytes ->
        assertEquals(730, bytes.size, "Fixed codec fixture byte size")
        val hash = SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        assertEquals("ee13b6c257d11c080e0489977bfa9779ad1220f2552189ca8052dec11b7431ab", hash,
            "Known white/blue input must not depend on recorder scheduling")
    }
    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Video"))
    private class Gateway(private val bytes: ByteArray, private val badHash: Boolean = false, private val cancel: Boolean = false,
        private val foreign: Boolean = false, private val pending: Boolean = false, private val truncate: Boolean = false, private val mime: String = "video/webm") : AssetDownloadGateway {
        var downloads = 0; var authorizations = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", mime, bytes.size.toLong(),
                if (badHash) "sha256:" + "a".repeat(64) else "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') },
                32, 32, null, if (pending) AssetStatus.Pending else AssetStatus.Ready, "2026-10-02"), "https://storage.invalid/video")
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            if (!truncate) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }
}
internal expect fun recordBrowserVideoFixture(done: (String?, String?) -> Unit)
internal expect fun browserVideoAccounting(reset: Boolean): String
internal expect fun hideBrowserVideoPage()
internal expect fun rejectNextBrowserVideoPlay()
