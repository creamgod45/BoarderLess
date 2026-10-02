@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.cinterop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.*
import kotlin.test.*

class IosGifAnimationTest {
    @Test fun actualGifFramesHaveDifferentPixelsAndDurations() = runBlocking {
        val animation = IosGifAnimation.decode(twoFrameGifFixture())
        try {
            assertEquals(2, animation.frameCount); assertEquals(-1, animation.repetitionCount)
            assertEquals(50L, animation.durationMs(0)); assertEquals(100L, animation.durationMs(1))
            val first = animation.frame(0); val second = animation.frame(1)
            val red = IntArray(1); val blue = IntArray(1)
            first.readPixels(red); second.readPixels(blue)
            assertEquals(0xffff0000.toInt(), red[0]); assertEquals(0xff0000ff.toInt(), blue[0])
            first.readPixels(red); assertEquals(0xffff0000.toInt(), red[0])
            assertFailsWith<IllegalArgumentException> { animation.frame(2) }
        } finally { animation.release() }
        animation.release()
        assertFailsWith<IllegalStateException> { animation.frame(0) }
        Unit
    }
    @Test fun verifiedDownloadRemovesNativeTemporaryFilesBeforePlaying() = runBlocking {
        val before = temporaryFiles(); val gateway = Gateway(twoFrameGifFixture())
        val animation = loadIosGif(gateway, session(), "gif-1")
        try {
            assertEquals(before, temporaryFiles()); assertEquals(1, gateway.downloads)
            assertEquals(2, animation.frameCount); animation.frame(1)
        } finally { animation.release() }
        assertEquals(before, temporaryFiles())
    }
    @Test fun cancellationOrBadChecksumClearsNativeFiles() = runBlocking {
        val before = temporaryFiles()
        assertFailsWith<CancellationException> { loadIosGif(Gateway(twoFrameGifFixture(), cancel = true), session(), "gif-1") }
        assertFailsWith<IllegalStateException> { loadIosGif(Gateway(twoFrameGifFixture(), badHash = true), session(), "gif-1") }
        assertEquals(before, temporaryFiles())
    }
    @Test fun foreignWorkspaceAndCorruptContentCannotStartAnimation() = runBlocking {
        val gateway = Gateway(twoFrameGifFixture(), foreign = true)
        assertFailsWith<IllegalArgumentException> { loadIosGif(gateway, session(), "gif-1") }
        assertEquals(0, gateway.downloads)
        assertFailsWith<IllegalArgumentException> { IosGifAnimation.decode(byteArrayOf(1, 2, 3)) }
        Unit
    }
    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0,
        Workspace(WorkspaceId("w"), "GIF"))
    private inner class Gateway(private val bytes: ByteArray, private val cancel: Boolean = false,
        private val badHash: Boolean = false, private val foreign: Boolean = false) : AssetDownloadGateway {
        var downloads = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(
            WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", "image/gif", bytes.size.toLong(),
                if (badHash) "sha256:wrong" else checksum(bytes), 1, 1, null, AssetStatus.Ready, "2026-10-01"),
            "https://storage.invalid/gif",
        )
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }
    private fun checksum(bytes: ByteArray): String = memScoped {
        val hash = allocArray<UByteVar>(32)
        bytes.usePinned { CC_SHA256(it.addressOf(0), bytes.size.toUInt(), hash) }
        "sha256:" + (0 until 32).joinToString("") { hash[it].toString(16).padStart(2, '0') }
    }
    private fun temporaryFiles() = NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)
        .orEmpty().filterIsInstance<String>().filter { it.startsWith("boarderless-preview-") }.toSet()
}
