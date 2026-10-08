@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.skia.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.ImageIO.*
import platform.Foundation.*
import kotlin.test.*

/** Real ImageIO + Skia pixels on simulator, not an APP/file-picker or backend acceptance test. */
class IosExportImageTest {
    @Test fun exifClockwiseOrientationSwapsBoundsAndRotatesImageContent() = runBlocking {
        withContext(Dispatchers.Default) {
            val surface = Surface.makeRasterN32Premul(200, 100); val paint = Paint()
            val png = try {
                paint.color = 0xffff0000.toInt(); surface.canvas.drawRect(Rect.makeWH(100f, 100f), paint)
                paint.color = 0xff0000ff.toInt(); surface.canvas.drawRect(Rect.makeXYWH(100f, 0f, 100f, 100f), paint)
                val image = surface.makeImageSnapshot()
                try {
                    val data = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
                    try { data.bytes } finally { data.close() }
                } finally { image.close() }
            } finally { paint.close(); surface.close() }
            val jpeg = orientedJpeg(png)
            val before = previews(); val gateway = gateway(jpeg)
            gateway.metadata = gateway.metadata.copy(mediaType = "image/jpeg", width = 200, height = 100)
            val frame = loadIosExportImage(gateway, session(), "a", 400, 400)
            assertEquals(100, frame.width); assertEquals(200, frame.height)
            val top = IntArray(1); val bottom = IntArray(1)
            frame.readPixels(top, startX = 50, startY = 20, width = 1, height = 1)
            frame.readPixels(bottom, startX = 50, startY = 180, width = 1, height = 1)
            assertTrue(((top[0] ushr 16) and 255) > 240 && (top[0] and 255) < 10)
            assertTrue((bottom[0] and 255) > 240 && ((bottom[0] ushr 16) and 255) < 10)
            assertEquals(before, previews())
        }
    }

    private fun orientedJpeg(png: ByteArray): ByteArray = memScoped {
        val input = png.usePinned { checkNotNull(CFDataCreate(null, it.addressOf(0).reinterpret(), png.size.toLong())) }
        val source = try { checkNotNull(CGImageSourceCreateWithData(input, null)) } finally { CFRelease(input) }
        val image = try { checkNotNull(CGImageSourceCreateImageAtIndex(source, 0UL, null)) } finally { CFRelease(source) }
        try {
            val data = checkNotNull(CFDataCreateMutable(null, 0))
            try {
                val type = checkNotNull(CFStringCreateWithCString(null, "public.jpeg", kCFStringEncodingUTF8))
                val destination = try { checkNotNull(CGImageDestinationCreateWithData(data, type, 1UL, null)) } finally { CFRelease(type) }
                try {
                    val orientation = alloc<IntVar>(); orientation.value = 6
                    val number = checkNotNull(CFNumberCreate(null, kCFNumberIntType, orientation.ptr))
                    try {
                        val keys = allocArray<COpaquePointerVar>(1); keys[0] = kCGImagePropertyOrientation
                        val values = allocArray<COpaquePointerVar>(1); values[0] = number
                        val properties = checkNotNull(CFDictionaryCreate(null, keys, values, 1, null, null))
                        try {
                            CGImageDestinationAddImage(destination, image, properties)
                            check(CGImageDestinationFinalize(destination))
                        } finally { CFRelease(properties) }
                    } finally { CFRelease(number) }
                } finally { CFRelease(destination) }
                ByteArray(CFDataGetLength(data).toInt()).also { bytes ->
                    val inputBytes = checkNotNull(CFDataGetBytePtr(data))
                    for (index in bytes.indices) bytes[index] = inputBytes[index].toByte()
                }
            } finally { CFRelease(data) }
        } finally { CGImageRelease(image) }
    }

    private fun session() = WorkspaceSession("u", "c", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Test"))
    private fun previews() = checkNotNull(NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), null))
        .filterIsInstance<String>().filter { it.startsWith("boarderless-preview-") }.sorted()
    private fun checksum(bytes: ByteArray): String = memScoped {
        val output = allocArray<UByteVar>(32)
        bytes.usePinned { CC_SHA256(it.addressOf(0), bytes.size.toUInt(), output) }
        "sha256:" + (0 until 32).joinToString("") { output[it].toString(16).padStart(2, '0') }
    }
    private fun fixture(): ByteArray {
        val surface = Surface.makeRasterN32Premul(6000, 2400)
        val paint = Paint()
        try {
            surface.canvas.clear(0)
            paint.color = 0x8000ff00.toInt(); surface.canvas.drawRect(Rect.makeWH(6000f, 2400f), paint)
            paint.color = 0xffff0000.toInt(); surface.canvas.drawRect(Rect.makeXYWH(0f, 0f, 64f, 64f), paint)
            paint.color = 0xff0000ff.toInt(); surface.canvas.drawRect(Rect.makeXYWH(0f, 2336f, 64f, 64f), paint)
            for (stripe in 0 until 16) {
                paint.color = if (stripe % 2 == 0) 0xff000000.toInt() else -1
                surface.canvas.drawRect(Rect.makeXYWH((5872 + stripe * 8).toFloat(), 2272f, 8f, 128f), paint)
            }
            val image = surface.makeImageSnapshot()
            try {
                val encoded = checkNotNull(image.encodeToData(EncodedImageFormat.PNG))
                try { return encoded.bytes } finally { encoded.close() }
            } finally { image.close() }
        } finally { paint.close(); surface.close() }
    }
    private class Gateway(val bytes: ByteArray) : AssetDownloadGateway {
        var allowed = true; var corrupt = false; var downloads = 0
        lateinit var metadata: WorkspaceAsset
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            check(allowed)
            return AssetDownloadTicket(metadata, "https://storage.invalid/private")
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++
            val sent = bytes.copyOf()
            if (corrupt) sent[sent.lastIndex] = (sent.last().toInt() xor 1).toByte()
            var offset = 0
            while (offset < sent.size) {
                val end = minOf(offset + 65536, sent.size); onChunk(sent.copyOfRange(offset, end)); offset = end
            }
        }
    }
    private fun gateway(bytes: ByteArray) = Gateway(bytes).also {
        it.metadata = WorkspaceAsset("a", WorkspaceId("w"), "u", "image/png", bytes.size.toLong(), checksum(bytes), 6000, 2400,
            null, AssetStatus.Ready, "2026-10-08")
    }

    @Test fun fullOriginalKeepsEdgeDetailTransparencyAndImageDirection() = runBlocking {
        withContext(Dispatchers.Default) {
            val before = previews(); val gateway = gateway(fixture())
            val frame = loadIosExportImage(gateway, session(), "a", 2500, 1000)
            assertEquals(2500, frame.width); assertEquals(1000, frame.height)
            val pixel = IntArray(1)
            frame.readPixels(pixel, startX = 4, startY = 4, width = 1, height = 1)
            assertEquals(0xffff0000.toInt(), pixel[0])
            frame.readPixels(pixel, startX = 4, startY = 996, width = 1, height = 1)
            assertEquals(0xff0000ff.toInt(), pixel[0])
            frame.readPixels(pixel, startX = 1200, startY = 400, width = 1, height = 1)
            assertEquals(0x8000ff00.toInt(), pixel[0])
            val row = IntArray(53); frame.readPixels(row, startX = 2447, startY = 980, width = 53, height = 1)
            val transitions = row.map { ((it ushr 16) and 255) >= 128 }.zipWithNext().count { it.first != it.second }
            assertTrue(transitions >= 14, "Full-image edge detail lost: $transitions transitions")
            assertEquals(before, previews())
            gateway.allowed = false
            assertFailsWith<IllegalStateException> { loadIosExportImage(gateway, session(), "a", 2500, 1000) }
            assertEquals(1, gateway.downloads); assertEquals(before, previews())
        }
    }

    @Test fun checksumFailureAndForeignWorkspaceCannotPublishPixelsOrLeaveCache() = runBlocking {
        withContext(Dispatchers.Default) {
            val before = previews(); val gateway = gateway(fixture())
            gateway.metadata = gateway.metadata.copy(workspaceId = WorkspaceId("other"))
            assertFailsWith<IllegalArgumentException> { loadIosExportImage(gateway, session(), "a", 2500, 1000) }
            assertEquals(0, gateway.downloads); assertEquals(before, previews())
            gateway.metadata = gateway.metadata.copy(workspaceId = WorkspaceId("w")); gateway.corrupt = true
            val failure = assertFailsWith<IllegalStateException> { loadIosExportImage(gateway, session(), "a", 2500, 1000) }
            assertTrue(failure.message.orEmpty().contains("checksum"))
            assertEquals(before, previews())
        }
    }
}
