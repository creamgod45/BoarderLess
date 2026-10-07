package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DesktopGifAnimationTest {
    @Test fun actualTwoFrameGifDecodesDifferentPixelsWithDeclaredDurations() = runBlocking {
        val animation = DesktopGifAnimation.decode(gif())
        try {
            assertEquals(2, animation.frameCount); assertEquals(-1, animation.repetitionCount)
            assertEquals(50L, animation.durationMs(0)); assertEquals(100L, animation.durationMs(1))
            val first = animation.frame(0); val second = animation.frame(1)
            val red = IntArray(4); val blue = IntArray(4)
            first.readPixels(red); second.readPixels(blue)
            assertEquals(0xffff0000.toInt(), red[0]); assertEquals(0xff0000ff.toInt(), blue[0])
            first.readPixels(red)
            assertEquals(0xffff0000.toInt(), red[0]) // Frame 1 must not mutate a published frame 0.
            assertFailsWith<IllegalArgumentException> { animation.frame(2) }
        } finally { animation.release() }
        animation.release()
        assertFailsWith<IllegalStateException> { animation.frame(0) }
        Unit
    }
    @Test fun readyGifDownloadsOnceAndTemporaryFilesAreRemoved() = runBlocking {
        val before = temporaryGifs(); val gateway = Gateway(gif())
        val animation = loadDesktopGif(gateway, session(), "gif-1")
        try { assertEquals(2, animation.frameCount); assertEquals(1, gateway.downloads); animation.frame(1) }
        finally { animation.release() }
        assertEquals(before, temporaryGifs())
    }
    @Test fun cancelledOrInvalidChecksumDoesNotLeakTemporaryFiles() = runBlocking {
        val before = temporaryGifs()
        assertFailsWith<CancellationException> { loadDesktopGif(Gateway(gif(), cancel = true), session(), "gif-1") }
        assertFailsWith<IllegalStateException> { loadDesktopGif(Gateway(gif(), badHash = true), session(), "gif-1") }
        assertEquals(before, temporaryGifs())
    }
    @Test fun wrongWorkspaceAndCorruptContentCannotStartPlayback() = runBlocking {
        val gateway = Gateway(gif(), foreign = true)
        assertFailsWith<IllegalArgumentException> { loadDesktopGif(gateway, session(), "gif-1") }
        assertEquals(0, gateway.downloads)
        assertFailsWith<IllegalArgumentException> { DesktopGifAnimation.decode(byteArrayOf(1, 2, 3)) }
        Unit
    }

    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0,
        Workspace(WorkspaceId("w"), "GIF"))
    private class Gateway(private val bytes: ByteArray, private val cancel: Boolean = false,
        private val badHash: Boolean = false, private val foreign: Boolean = false) : AssetDownloadGateway {
        var downloads = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(
            WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", "image/gif", bytes.size.toLong(),
                if (badHash) "sha256:wrong" else "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                2, 2, null, AssetStatus.Ready, "2026-10-01"), "https://storage.invalid/gif")
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }
    private fun temporaryGifs() = Files.list(Paths.get(System.getProperty("java.io.tmpdir"))).use { paths ->
        paths.filter { it.fileName.toString().startsWith("boarderless-gif-") }.iterator().asSequence().toSet()
    }
    private fun gif(): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        val output = ByteArrayOutputStream()
        try {
            MemoryCacheImageOutputStream(output).use { stream ->
                writer.output = stream; writer.prepareWriteSequence(null)
                listOf(0xffff0000.toInt(), 0xff0000ff.toInt()).forEachIndexed { index, color ->
                    val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
                    for (y in 0..1) for (x in 0..1) image.setRGB(x, y, color)
                    val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null)
                    val root = metadata.getAsTree(metadata.nativeMetadataFormatName) as IIOMetadataNode
                    val control = root.getElementsByTagName("GraphicControlExtension").item(0) as IIOMetadataNode
                    control.setAttribute("delayTime", if (index == 0) "5" else "10")
                    control.setAttribute("disposalMethod", "none")
                    if (index == 0) {
                        val extensions = IIOMetadataNode("ApplicationExtensions")
                        val loop = IIOMetadataNode("ApplicationExtension")
                        loop.setAttribute("applicationID", "NETSCAPE"); loop.setAttribute("authenticationCode", "2.0")
                        loop.userObject = byteArrayOf(1, 0, 0); extensions.appendChild(loop); root.appendChild(extensions)
                    }
                    metadata.setFromTree(metadata.nativeMetadataFormatName, root)
                    writer.writeToSequence(IIOImage(image, null, metadata), null)
                }
                writer.endWriteSequence(); stream.flush()
            }
            return output.toByteArray()
        } finally { writer.dispose() }
    }
}
