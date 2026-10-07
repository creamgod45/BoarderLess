package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.bytedeco.ffmpeg.global.avcodec.*
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Java2DFrameConverter
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.ShortBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*

class DesktopVideoPlaybackTest {
    @Test fun h264AacNativeFramesClockSeekAndPcmOutput() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-h264-test-")
            val output = FakeAudio()
            var player: DesktopVideoPlayback? = null
            try {
                val local = fixture(directory, "mp4", audio = true, width = 64, height = 64, codec = AV_CODEC_ID_H264)
                val opened = DesktopVideoPlayback.open(local, directory) { output }.also { player = it }
                assertTrue(opened.state.value.durationMs >= 1900)
                opened.seekTo(1200)
                val pixel = IntArray(1)
                checkNotNull(opened.image.value).readPixels(pixel, width = 1, height = 1)
                assertTrue((pixel[0] and 255) > 200 && ((pixel[0] shr 16) and 255) < 30)
                opened.setMuted(false)
                opened.attach(true)
                opened.setPlaying(true)
                withTimeout(5000) { opened.state.first { it.positionMs > 1300 && it.playing } }
                withTimeout(5000) { opened.state.first { it.ended } }
                assertTrue(output.written > 0)
                assertFalse(opened.state.value.failed)
            } finally {
                if (player != null) player.release() else clearDesktopVideoDirectory(directory)
            }
            assertFalse(Files.exists(directory))
        }

    @Test fun realMp4ClockPixelsSeekPauseReplayAndIdempotentRelease() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-video-test-")
            val local = fixture(directory, "mp4", audio = true)
            val output = FakeAudio()
            val player = DesktopVideoPlayback.open(local, directory) { output }
            try {
                assertTrue(player.state.value.muted)
                assertFalse(player.state.value.playing)
                assertEquals(32, player.state.value.width)
                assertEquals(32, player.state.value.height)
                val first = checkNotNull(player.image.value)
                val white = IntArray(1)
                first.readPixels(white, width = 1, height = 1)
                assertTrue((white[0] and 255) > 240)
                player.setPlaying(true)
                assertFalse(player.state.value.playing)
                player.attach(true)
                withTimeout(5000) { player.state.first { it.positionMs > 100 && it.playing } }
                player.setPlaying(false)
                assertFalse(player.state.value.playing)
                val paused = player.state.value.positionMs
                delay(150)
                assertEquals(paused, player.state.value.positionMs)
                player.seekTo(1200)
                val blue = IntArray(1)
                checkNotNull(player.image.value).readPixels(blue, width = 1, height = 1)
                assertTrue((blue[0] and 255) > 200 && ((blue[0] shr 16) and 255) < 30)
                first.readPixels(white, width = 1, height = 1)
                assertTrue((white[0] and 255) > 240)
                player.setMuted(false)
                assertFalse(player.state.value.muted)
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.ended } }
                assertTrue(output.written > 0)
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.positionMs > 100 && !it.ended } }
                player.attach(false)
                assertFalse(player.state.value.playing)
                player.setMuted(true)
            } finally {
                player.release()
            }
            player.release()
            assertTrue(output.closed)
            assertFalse(Files.exists(directory))
            assertTrue(player.state.value.released)
            assertNull(player.image.value)
            assertFailsWith<IllegalStateException> { player.setPlaying(true) }
            Unit
        }

    @Test fun webmHasRealFramesAndFiniteDuration() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-webm-test-")
            val player = DesktopVideoPlayback.open(fixture(directory, "webm"), directory)
            try {
                assertTrue(player.state.value.durationMs > 0)
                player.seekTo(1200)
                val pixel = IntArray(1)
                checkNotNull(player.image.value).readPixels(pixel, width = 1, height = 1)
                assertTrue((pixel[0] and 255) > 200 && ((pixel[0] shr 16) and 255) < 30)
            } finally {
                player.release()
            }
            assertFalse(Files.exists(directory))
        }

    @Test fun corruptNativeInputClearsDecoderAndPrivateDirectory() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-corrupt-video-")
            val file = directory.resolve("invalid.mp4")
            Files.write(file, ByteArray(32))
            assertFails { DesktopVideoPlayback.open(LocalAssetReference(file.toString(), "video/mp4"), directory) }
            assertFalse(Files.exists(directory))
        }

    @Test fun audioCloseFailureStillReleasesDecoderWorkerAndPrivateFile() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-audio-close-test-")
            val player =
                DesktopVideoPlayback.open(fixture(directory, "mp4", audio = true), directory) {
                    object : DesktopAudioOutput {
                        override fun write(
                            bytes: ByteArray,
                            offset: Int,
                        ) = bytes.size - offset

                        override fun pause() = Unit

                        override fun resume() = Unit

                        override fun close(): Unit = error("Audio device close failed")
                    }
                }
            player.setMuted(false)
            assertFailsWith<IllegalStateException> { player.release() }
            assertTrue(player.state.value.released)
            assertNull(player.image.value)
            assertFalse(Files.exists(directory))
            player.release()
            assertFailsWith<IllegalStateException> { player.seekTo(0) }
            Unit
        }

    @Test fun audioWriteAndPauseFailureStillPublishesFailureForOwnerCleanup() =
        runBlocking {
            val directory = Files.createTempDirectory("desktop-audio-failure-test-")
            val player =
                DesktopVideoPlayback.open(fixture(directory, "mp4", audio = true), directory) {
                    object : DesktopAudioOutput {
                        var disconnected = false

                        override fun write(
                            bytes: ByteArray,
                            offset: Int,
                        ): Int {
                            disconnected = true
                            error("Audio device disconnected")
                        }

                        override fun pause() {
                            if (disconnected) error("Cannot pause disconnected device")
                        }

                        override fun resume() = Unit

                        override fun close() = Unit
                    }
                }
            try {
                player.setMuted(false)
                player.attach(true)
                player.setPlaying(true)
                withTimeout(5000) { player.state.first { it.failed } }
                assertFalse(player.state.value.playing)
            } finally {
                player.release()
            }
            assertTrue(player.state.value.released)
            assertFalse(Files.exists(directory))
        }

    @Test fun readyAssetDownloadsOnceAndFileLivesUntilPlayerRelease() =
        runBlocking {
            val source = Files.createTempDirectory("desktop-video-source-")
            val bytes =
                try {
                    Files.readAllBytes(Path.of(fixture(source, "mp4").token))
                } finally {
                    clearDesktopVideoDirectory(source)
                }
            val gateway = Gateway(bytes)
            val before = temporaryFiles()
            val player = loadDesktopVideo(gateway, session(), "video")
            try {
                assertEquals(1, gateway.downloads)
                assertEquals(1, gateway.authorizations)
                assertTrue(temporaryFiles().size > before.size)
            } finally {
                player.release()
            }
            assertEquals(before, temporaryFiles())
        }

    @Test fun hashCancellationTruncationAndForeignMetadataCannotLeaveFiles() =
        runBlocking {
            val before = temporaryFiles()
            assertFails { loadDesktopVideo(Gateway(ByteArray(32), badHash = true), session(), "video") }
            assertFailsWith<CancellationException> { loadDesktopVideo(Gateway(ByteArray(32), cancel = true), session(), "video") }
            assertFails { loadDesktopVideo(Gateway(ByteArray(32), truncate = true), session(), "video") }
            listOf(
                Gateway(ByteArray(32), foreign = true),
                Gateway(ByteArray(32), pending = true),
                Gateway(ByteArray(32), mime = "image/png"),
            ).forEach { gateway ->
                assertFails { loadDesktopVideo(gateway, session(), "video") }
                assertEquals(0, gateway.downloads)
            }
            assertEquals(before, temporaryFiles())
        }

    @Test fun nativeDisplayMatrixRotatesPixelsDimensionsAndSeekFrames() =
        runBlocking {
            val expected =
                mapOf(
                    90.0 to listOf(Color.GREEN, Color.WHITE, Color.RED, Color.BLUE),
                    -90.0 to listOf(Color.BLUE, Color.RED, Color.WHITE, Color.GREEN),
                    180.0 to listOf(Color.WHITE, Color.BLUE, Color.GREEN, Color.RED),
                )
            for ((rotation, colors) in expected) {
                val directory = Files.createTempDirectory("desktop-video-rotation-")
                val player = DesktopVideoPlayback.open(fixture(directory, "mp4", width = 64, height = 32, rotation = rotation), directory)
                try {
                    val width = if (rotation == 180.0) 64 else 32
                    val height = if (rotation == 180.0) 32 else 64
                    assertEquals(width, player.state.value.width)
                    assertEquals(height, player.state.value.height)
                    repeat(2) { pass ->
                        if (pass > 0) player.seekTo(1200)
                        val bitmap = checkNotNull(player.image.value)
                        assertEquals(width, bitmap.width)
                        assertEquals(height, bitmap.height)
                        colors.forEachIndexed { index, color ->
                            val pixel = IntArray(1)
                            bitmap.readPixels(
                                pixel,
                                startX = width * (if (index % 2 == 0) 1 else 3) / 4,
                                startY = height * (if (index < 2) 1 else 3) / 4,
                                width = 1,
                                height = 1,
                            )
                            listOf(16 to color.red, 8 to color.green, 0 to color.blue).forEach { (shift, component) ->
                                assertTrue(
                                    kotlin.math.abs(((pixel[0] shr shift) and 255) - component) < 35,
                                    "rotation=$rotation quadrant=$index pixel=${pixel[0]} expected=$color",
                                )
                            }
                        }
                    }
                } finally {
                    player.release()
                }
                assertFalse(Files.exists(directory))
            }
        }

    @Test fun nativeNonSquareSamplesDisplayCorrectRatioAndCombineWithRotation() =
        runBlocking {
            for ((ratio, rotation) in listOf(2.0 to 0.0, .5 to 0.0, 2.0 to 90.0)) {
                val directory = Files.createTempDirectory("desktop-video-sar-")
                val player =
                    DesktopVideoPlayback.open(
                        fixture(
                            directory,
                            "mp4",
                            width = 64,
                            height = 32,
                            rotation = rotation,
                            sampleAspectRatio = ratio,
                            quadrants = true,
                        ),
                        directory,
                    )
                try {
                    val width = if (rotation == 0.0) (64 * ratio).toInt() else 32
                    val height = if (rotation == 0.0) 32 else (64 * ratio).toInt()
                    assertEquals(width, player.state.value.width)
                    assertEquals(height, player.state.value.height)
                    val colors =
                        if (rotation == 0.0) {
                            listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE)
                        } else {
                            listOf(Color.GREEN, Color.WHITE, Color.RED, Color.BLUE)
                        }
                    repeat(2) { pass ->
                        if (pass > 0) player.seekTo(1200)
                        val bitmap = checkNotNull(player.image.value)
                        assertEquals(width, bitmap.width)
                        assertEquals(height, bitmap.height)
                        colors.forEachIndexed { index, color ->
                            val pixel = IntArray(1)
                            bitmap.readPixels(
                                pixel,
                                startX = width * (if (index % 2 == 0) 1 else 3) / 4,
                                startY = height * (if (index < 2) 1 else 3) / 4,
                                width = 1,
                                height = 1,
                            )
                            listOf(16 to color.red, 8 to color.green, 0 to color.blue).forEach { (shift, component) ->
                                assertTrue(
                                    kotlin.math.abs(((pixel[0] shr shift) and 255) - component) < 35,
                                    "SAR=$ratio rotation=$rotation quadrant=$index",
                                )
                            }
                        }
                    }
                } finally {
                    player.release()
                }
                assertFalse(Files.exists(directory))
            }
        }

    private fun fixture(
        directory: Path,
        format: String,
        audio: Boolean = false,
        width: Int = 32,
        height: Int = 32,
        rotation: Double = 0.0,
        sampleAspectRatio: Double = 1.0,
        quadrants: Boolean = rotation != 0.0,
        codec: Int = if (format == "webm") AV_CODEC_ID_VP8 else AV_CODEC_ID_MPEG4,
    ): LocalAssetReference {
        val path = directory.resolve("fixture.$format")
        val recorder = FFmpegFrameRecorder(path.toFile(), width, height, if (audio) 2 else 0)
        val converter = Java2DFrameConverter()
        try {
            recorder.format = format
            recorder.frameRate = 10.0
            recorder.aspectRatio = sampleAspectRatio
            recorder.videoCodec = codec
            recorder.gopSize = 10
            recorder.maxBFrames = 0
            if (rotation != 0.0) recorder.setDisplayRotation(rotation)
            if (audio) {
                recorder.audioCodec = AV_CODEC_ID_AAC
                recorder.sampleRate = 48000
            }
            recorder.start()
            repeat(20) { index ->
                val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
                image.createGraphics().apply {
                    if (!quadrants) {
                        color = if (index < 10) Color.WHITE else Color.BLUE
                        fillRect(0, 0, width, height)
                    } else {
                        listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE).forEachIndexed { quadrant, value ->
                            color = value
                            fillRect((quadrant % 2) * width / 2, (quadrant / 2) * height / 2, width / 2, height / 2)
                        }
                    }
                    dispose()
                }
                recorder.timestamp = index * 100_000L
                recorder.record(converter.convert(image))
                if (audio) recorder.recordSamples(48000, 2, ShortBuffer.wrap(ShortArray(9600) { 1000 }))
            }
            recorder.stop()
        } finally {
            recorder.close()
            converter.close()
        }
        return LocalAssetReference(path.toString(), if (format == "webm") "video/webm" else "video/mp4")
    }

    private class FakeAudio : DesktopAudioOutput {
        @Volatile var written = 0

        @Volatile var closed = false

        override fun write(
            bytes: ByteArray,
            offset: Int,
        ): Int {
            val count = bytes.size - offset
            written += count
            return count
        }

        override fun pause() = Unit

        override fun resume() = Unit

        override fun close() {
            closed = true
        }
    }

    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Video"))

    private class Gateway(
        private val bytes: ByteArray,
        private val badHash: Boolean = false,
        private val cancel: Boolean = false,
        private val foreign: Boolean = false,
        private val pending: Boolean = false,
        private val truncate: Boolean = false,
        private val mime: String = "video/mp4",
    ) : AssetDownloadGateway {
        var downloads = 0
        var authorizations = 0

        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(
                WorkspaceAsset(
                    assetId,
                    if (foreign) WorkspaceId("other") else session.workspace.id,
                    "owner",
                    mime,
                    bytes.size.toLong(),
                    if (badHash) {
                        "sha256:" + "a".repeat(64)
                    } else {
                        "sha256:" +
                            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                    },
                    32,
                    32,
                    null,
                    if (pending) AssetStatus.Pending else AssetStatus.Ready,
                    "2026-10-02",
                ),
                "https://storage.invalid/video",
            )
        }

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            downloads++
            onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            if (!truncate) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }

    private fun temporaryFiles(): Set<String> =
        Files.list(Path.of(System.getProperty("java.io.tmpdir"))).use { files ->
            files
                .filter { it.fileName.toString().startsWith("boarderless-video-") }
                .map { it.fileName.toString() }
                .toList()
                .toSet()
        }
}
