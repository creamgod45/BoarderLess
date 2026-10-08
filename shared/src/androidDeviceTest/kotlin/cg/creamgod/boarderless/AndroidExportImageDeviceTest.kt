package cg.creamgod.boarderless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID
import java.security.MessageDigest
import kotlin.test.*

/** Real Android decoders are required; Host tests cannot prove native pixel fidelity. */
class AndroidExportImageDeviceTest {
    @Test fun verifiedOriginalLoadsAndFailedAuthorizationOrChecksumLeavesNoPrivateFiles() = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "export-transfer-test-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            val original = File(root, "source.png")
            val source = Bitmap.createBitmap(16, 8, Bitmap.Config.ARGB_8888)
            try {
                source.eraseColor(Color.argb(128, 0, 255, 0))
                original.outputStream().use { check(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { source.recycle() }
            val bytes = original.readBytes()
            val session = WorkspaceSession("u", "c", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Test"))
            var allowed = true; var corrupt = false; var downloads = 0
            val metadata = WorkspaceAsset("a", WorkspaceId("w"), "u", "image/png", bytes.size.toLong(),
                "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
                16, 8, null, AssetStatus.Ready, "2026-10-08")
            val gateway = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
                    check(allowed)
                    return AssetDownloadTicket(metadata, "https://storage.invalid/private")
                }
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
                    downloads++
                    val sent = bytes.copyOf()
                    if (corrupt) sent[sent.lastIndex] = (sent.last().toInt() xor 1).toByte()
                    onChunk(sent)
                }
            }
            val loader = AndroidExportImageLoader(root)
            val frame = loader.load(gateway, session, "a", 128, 128)
            try {
                assertEquals(16, frame.width); assertEquals(8, frame.height)
                val pixels = IntArray(1); frame.readPixels(pixels, width = 1, height = 1)
                assertEquals(Color.argb(128, 0, 255, 0), pixels[0])
            } finally { frame.asAndroidBitmap().recycle() }
            assertEquals(listOf("source.png"), root.listFiles()!!.map { it.name })
            allowed = false
            assertFailsWith<IllegalStateException> { loader.load(gateway, session, "a", 128, 128) }
            assertEquals(1, downloads)
            allowed = true; corrupt = true
            val failure = assertFailsWith<IllegalStateException> { loader.load(gateway, session, "a", 128, 128) }
            assertTrue(failure.message.orEmpty().contains("checksum"))
            assertEquals(listOf("source.png"), root.listFiles()!!.map { it.name })
            assertContentEquals(bytes, original.readBytes())
        } finally { root.deleteRecursively() }
    }

    @Test fun modernAndRegionDecodersKeepFullEdgeDetailAndAlphaAtExportSize() = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "export-image-test-${UUID.randomUUID()}")
        check(root.mkdir())
        try {
            val original = File(root, "original.png")
            val source = Bitmap.createBitmap(6000, 2400, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(source); val paint = Paint().apply { color = Color.argb(128, 0, 255, 0) }
                canvas.drawRect(0f, 0f, 6000f, 2400f, paint)
                for (stripe in 0 until 16) {
                    paint.color = if (stripe % 2 == 0) Color.BLACK else Color.WHITE
                    canvas.drawRect((5872 + stripe * 8).toFloat(), 2272f, (5880 + stripe * 8).toFloat(), 2400f, paint)
                }
                original.outputStream().use { check(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { source.recycle() }
            val decoders = listOf<suspend () -> Bitmap>(
                { decodeAndroidExportImage(original, 2500, 1000) },
                { decodeAndroidExportRegions(original, 2500, 1000) },
            )
            for (decode in decoders) {
                val output = decode()
                try {
                    assertEquals(2500, output.width); assertEquals(1000, output.height)
                    for (x in listOf(3, 850, 1700, 2497)) assertEquals(Color.argb(128, 0, 255, 0), output.getPixel(x, 400))
                    val row = IntArray(53); output.getPixels(row, 0, 53, 2447, 980, 53, 1)
                    val transitions = row.map { Color.red(it) >= 128 }.zipWithNext().count { it.first != it.second }
                    assertTrue(transitions >= 14, "Edge detail lost: $transitions transitions")
                } finally { output.recycle() }
            }
        } finally { root.deleteRecursively() }
    }
}
