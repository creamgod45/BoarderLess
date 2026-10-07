package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AndroidVideoDownloadTest {
    @Test fun checksumAndCancellationNeverLeavePlayerFiles() = runBlocking {
        withDirectory { directory ->
            assertFails { loadAndroidVideo(Gateway(), session(), "video", directory) }
            assertEquals(0, directory.listFiles()!!.size)
            assertFailsWith<CancellationException> { loadAndroidVideo(Gateway(cancel = true), session(), "video", directory) }
            assertEquals(0, directory.listFiles()!!.size)
        }
    }
    @Test fun unsupportedMimeAndForeignWorkspaceCannotDownloadOrCreateDirectory() = runBlocking {
        withDirectory { directory ->
            listOf(Gateway(mime = "image/png"), Gateway(foreign = true)).forEach { gateway ->
                assertFails { loadAndroidVideo(gateway, session(), "video", directory) }
                assertEquals(0, gateway.downloads)
                assertEquals(0, directory.listFiles()!!.size)
            }
        }
    }
    @Test fun directoryCleanupIsIdempotentAndDoesNotTouchSibling() = runBlocking {
        withDirectory { directory ->
            val own = java.io.File(directory, "own").apply { mkdir() }
            val sibling = java.io.File(directory, "sibling").apply { mkdir() }
            clearAndroidVideoDirectory(own)
            clearAndroidVideoDirectory(own)
            assertTrue(sibling.isDirectory)
            check(sibling.delete())
        }
    }
    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Video"))
    private class Gateway(private val cancel: Boolean = false, private val mime: String = "video/mp4", private val foreign: Boolean = false) : AssetDownloadGateway {
        var downloads = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(
            WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", mime, 2,
                "sha256:" + "a".repeat(64), 16, 16, null, AssetStatus.Ready, "2026-10-02"), "https://storage.invalid/video")
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(byteArrayOf(1))
            if (cancel) throw CancellationException("cancelled")
            onChunk(byteArrayOf(2))
        }
    }
    private suspend fun withDirectory(block: suspend (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("android-video-test-").toFile()
        try { block(directory) } finally {
            directory.listFiles().orEmpty().forEach { it.listFiles().orEmpty().forEach { file -> check(file.delete()) }; check(it.delete()) }
            check(directory.delete())
        }
    }
}
