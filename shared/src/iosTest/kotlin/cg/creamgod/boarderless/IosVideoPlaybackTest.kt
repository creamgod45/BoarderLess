@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.AVFoundation.*
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.CoreFoundation.*
import platform.CoreMedia.*
import platform.CoreVideo.*
import platform.CoreCrypto.CC_SHA256
import platform.Foundation.*
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import kotlinx.coroutines.flow.first
import platform.posix.memcpy
import platform.posix.memset
import kotlin.test.*

class IosVideoPlaybackTest {
    @Test fun mediaServerResetInvalidatesNativePlayerUntilExplicitReopen() = runPlayerTest {
        val before = temporaryFiles()
        val gateway = Gateway(makeMp4())
        val playback = loadIosVideo(gateway, session(), "video") as IosVideoPlayback
        val layer = AVPlayerLayer(); val owner = Any()
        try {
            playback.attach(layer, owner)
            playback.setPlaying(true)
            withTimeout(5000) { playback.state.first { it.positionMs > 0 } }
            NSNotificationCenter.defaultCenter.postNotificationName(AVAudioSessionMediaServicesWereResetNotification, null)
            assertTrue(playback.state.value.failed)
            assertFalse(playback.state.value.playing)
            assertFailsWith<IllegalStateException> { playback.setPlaying(true) }
        } finally { playback.release() }
        assertNull(layer.player)
        assertEquals(before, temporaryFiles())
        val reopened = loadIosVideo(gateway, session(), "video")
        try {
            assertFalse(reopened.state.value.failed)
            assertFalse(reopened.state.value.playing)
            assertTrue(reopened.state.value.muted)
        } finally { reopened.release() }
        assertEquals(before, temporaryFiles())
    }

    @Test fun silentNativeVideoDoesNotAcquireGlobalAudioSessionEvenWhenUnmuted() = runPlayerTest {
        val before = temporaryFiles()
        val gateway = Gateway(makeMp4())
        val sink = IosFileAssetDownloadSink.create("video/mp4")
        val local = AssetDownloadCoordinator(gateway).download(session(), "video", sink)
        var activations = 0; var deactivations = 0
        val audio = IosVideoAudioSession(object : IosVideoAudioSessionPort {
            override fun activate(onLoss: () -> Unit): Boolean { activations++; return true }
            override fun deactivate() { deactivations++ }
        })
        val playback = IosVideoPlayback.open(local, sink, audio)
        val layer = AVPlayerLayer(); val owner = Any()
        try {
            playback.attach(layer, owner)
            playback.setMuted(false)
            playback.setPlaying(true)
            withTimeout(5000) { playback.state.first { it.positionMs > 0 } }
            assertEquals(0, activations)
            assertFalse(playback.state.value.audioFocusBlocked)
            playback.setPlaying(false)
        } finally { playback.release() }
        assertEquals(0, deactivations)
        assertNull(layer.player)
        assertEquals(before, temporaryFiles())
    }

    @Test fun realMp4PreparesPausedMutedAndReleasesVerifiedFile() = runPlayerTest {
        val before = temporaryFiles()
        val gateway = Gateway(makeMp4())
        val playback = loadIosVideo(gateway, session(), "video")
        try {
            assertEquals(1, gateway.downloads)
            assertEquals(64, playback.state.value.width); assertEquals(64, playback.state.value.height)
            assertTrue(playback.state.value.durationMs > 0)
            assertTrue(playback.state.value.muted); assertFalse(playback.state.value.playing)
            assertTrue(temporaryFiles().size > before.size) // Verified content remains owned until release.
            playback.setMuted(false); assertFalse(playback.state.value.muted)
            playback.setMuted(true)
            playback.seekTo(Long.MAX_VALUE)
            assertEquals(playback.state.value.durationMs, playback.state.value.positionMs)
            playback.seekTo(-100); assertEquals(0L, playback.state.value.positionMs)
            playback.setPlaying(true)
            assertFalse(playback.state.value.playing) // No layer attached: don't play invisible audio.
            playback.setPlaying(false)
        } finally { playback.release() }
        playback.release()
        assertTrue(playback.state.value.released)
        assertEquals(before, temporaryFiles())
        assertFailsWith<IllegalStateException> { playback.setPlaying(true) }
    }

    @Test fun corruptButCorrectlyHashedContentCannotPrepare() = runPlayerTest {
        val before = temporaryFiles()
        assertFails { loadIosVideo(Gateway(ByteArray(32)), session(), "video") }
        assertEquals(before, temporaryFiles())
    }

    @Test fun layerOwnershipPauseCompletionAndReplayUseRealPlayerClock() = runPlayerTest {
        val before = temporaryFiles()
        val playback = loadIosVideo(Gateway(makeMp4()), session(), "video") as IosVideoPlayback
        val first = AVPlayerLayer(); val second = AVPlayerLayer()
        val firstOwner = Any(); val secondOwner = Any()
        try {
            playback.attach(first, firstOwner)
            playback.setPlaying(true)
            withTimeout(5000) { playback.state.first { it.positionMs > 0 } }
            playback.setPlaying(false)
            assertFalse(playback.state.value.playing)
            playback.attach(second, secondOwner)
            playback.setPlaying(true)
            playback.detach(first, firstOwner)
            assertNull(first.player); assertNotNull(second.player)
            assertTrue(playback.state.value.playing)
            playback.seekTo(0)
            playback.setPlaying(true)
            withTimeout(5000) { playback.state.first { it.ended } }
            assertFalse(playback.state.value.playing)
            playback.setPlaying(true)
            assertFalse(playback.state.value.ended)
            withTimeout(5000) { playback.state.first { it.positionMs > 0 && !it.ended } }
        } finally { playback.release() }
        assertNull(first.player); assertNull(second.player)
        assertEquals(before, temporaryFiles())
    }

    @Test fun backgroundNotificationReleasesPlayerObserversAndOwnedFile() = runPlayerTest {
        val before = temporaryFiles()
        val playback = loadIosVideo(Gateway(makeMp4()), session(), "video")
        try {
            NSNotificationCenter.defaultCenter.postNotificationName(UIApplicationDidEnterBackgroundNotification, null)
            withTimeout(5000) { playback.state.first { it.released } }
            playback.release() // Wait for file cleanup after the released state was published.
            assertEquals(before, temporaryFiles())
        } finally { playback.release() }
    }

    @Test fun cancellationDuringNativePreparationReleasesUnhandedPlayer() = runPlayerTest {
        val before = temporaryFiles()
        val gateway = Gateway(makeMp4())
        val sink = IosFileAssetDownloadSink.create("video/mp4")
        val local = AssetDownloadCoordinator(gateway).download(session(), "video", sink)
        coroutineScope {
            val preparation = async(start = CoroutineStart.UNDISPATCHED) { IosVideoPlayback.open(local, sink) }
            assertFalse(preparation.isCompleted, "The cancellation gate must target an actual pending preparation")
            preparation.cancelAndJoin()
        }
        assertEquals(before, temporaryFiles())
    }

    @Test fun checksumAndDownloadCancellationClearTemporaryFiles() = runPlayerTest {
        val before = temporaryFiles()
        assertFails { loadIosVideo(Gateway(ByteArray(32), badHash = true), session(), "video") }
        assertFailsWith<CancellationException> { loadIosVideo(Gateway(ByteArray(32), cancel = true), session(), "video") }
        assertEquals(before, temporaryFiles())
    }

    @Test fun foreignPendingAndUnsupportedMimeNeverDownload() = runPlayerTest {
        val before = temporaryFiles()
        listOf(Gateway(ByteArray(32), foreign = true), Gateway(ByteArray(32), pending = true), Gateway(ByteArray(32), mime = "image/png")).forEach { gateway ->
            assertFails { loadIosVideo(gateway, session(), "video") }
            assertEquals(0, gateway.downloads)
        }
        assertEquals(before, temporaryFiles())
    }

    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "Video"))
    private class Gateway(private val bytes: ByteArray, private val badHash: Boolean = false, private val cancel: Boolean = false,
        private val foreign: Boolean = false, private val pending: Boolean = false, private val mime: String = "video/mp4") : AssetDownloadGateway {
        var downloads = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(
            WorkspaceAsset(assetId, if (foreign) WorkspaceId("other") else session.workspace.id, "owner", mime, bytes.size.toLong(),
                if (badHash) "sha256:" + "a".repeat(64) else checksum(bytes), 64, 64, null,
                if (pending) AssetStatus.Pending else AssetStatus.Ready, "2026-10-02"), "https://storage.invalid/video")
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            downloads++; onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }

    private fun temporaryFiles() = NSFileManager.defaultManager.contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)
        .orEmpty().filterIsInstance<String>().filter { it.startsWith("boarderless-preview-") }.toSet()

    private fun runPlayerTest(block: suspend () -> Unit) {
        // Native CLI tests otherwise block Main with runBlocking, starving AVFoundation callbacks.
        assertTrue(NSThread.isMainThread)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val task = scope.async { withTimeout(40_000) { block() } }
        try {
            val started = NSProcessInfo.processInfo.systemUptime
            while (!task.isCompleted && NSProcessInfo.processInfo.systemUptime - started < 45) {
                NSRunLoop.currentRunLoop.runUntilDate(NSDate.dateWithTimeIntervalSinceNow(.01))
            }
            assertTrue(task.isCompleted, "Native player test did not finish")
            runBlocking { task.await() }
        } finally { scope.cancel() }
    }

    private suspend fun makeMp4(): ByteArray {
        val path = NSTemporaryDirectory() + "boarderless-video-fixture-${NSUUID().UUIDString}.mp4"
        val writer = checkNotNull(AVAssetWriter.assetWriterWithURL(NSURL.fileURLWithPath(path), AVFileTypeMPEG4, null))
        try {
            val input = AVAssetWriterInput.assetWriterInputWithMediaType(AVMediaTypeVideo, mapOf(
                AVVideoCodecKey to AVVideoCodecTypeH264, AVVideoWidthKey to 64, AVVideoHeightKey to 64,
            ))
            val adaptor = AVAssetWriterInputPixelBufferAdaptor.assetWriterInputPixelBufferAdaptorWithAssetWriterInput(input, null)
            writer.addInput(input)
            check(writer.startWriting())
            writer.startSessionAtSourceTime(CMTimeMake(0, 1000))
            repeat(5) { index ->
                while (!input.readyForMoreMediaData) { check(writer.status == AVAssetWriterStatusWriting); delay(10) }
                memScoped {
                    val output = alloc<CVPixelBufferRefVar>()
                    check(CVPixelBufferCreate(kCFAllocatorDefault, 64UL, 64UL, kCVPixelFormatType_32BGRA, null, output.ptr) == kCVReturnSuccess)
                    val buffer = checkNotNull(output.value)
                    try {
                        check(CVPixelBufferLockBaseAddress(buffer, 0UL) == kCVReturnSuccess)
                        try { memset(CVPixelBufferGetBaseAddress(buffer), 255, CVPixelBufferGetBytesPerRow(buffer) * 64UL) }
                        finally { CVPixelBufferUnlockBaseAddress(buffer, 0UL) }
                        check(adaptor.appendPixelBuffer(buffer, CMTimeMake(index * 200L, 1000)))
                    } finally { CFRelease(buffer) }
                }
            }
            input.markAsFinished()
            val finished = CompletableDeferred<Unit>()
            writer.finishWritingWithCompletionHandler { finished.complete(Unit) }
            finished.await()
            check(writer.status == AVAssetWriterStatusCompleted)
            return autoreleasepool {
                val inputFile = checkNotNull(NSFileHandle.fileHandleForReadingAtPath(path))
                try {
                    val data = inputFile.readDataToEndOfFile()
                    ByteArray(data.length.toInt()).also { bytes -> bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) } }
                } finally { inputFile.closeFile() }
            }
        } finally {
            if (writer.status == AVAssetWriterStatusWriting) writer.cancelWriting()
            val manager = NSFileManager.defaultManager
            check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null))
        }
    }
}

private fun checksum(bytes: ByteArray): String = memScoped {
    val hash = allocArray<UByteVar>(32)
    bytes.usePinned { CC_SHA256(it.addressOf(0), bytes.size.toUInt(), hash) }
    "sha256:" + (0 until 32).joinToString("") { hash[it].toString(16).padStart(2, '0') }
}
