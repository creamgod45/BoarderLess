@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.cinterop.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.io.encoding.Base64
import kotlin.test.*

class IosAssetPreviewTest {
    @Test
    fun verifiedChunksArePublishedThenReleased() =
        runBlocking {
            val bytes = byteArrayOf(97, 98, 99)
            val before = previews()
            val sink = IosFileAssetDownloadSink.create("image/png")
            try {
                sink.writeChunk(0, byteArrayOf(97))
                sink.writeChunk(1, byteArrayOf(98, 99))
                val local = sink.commit(3, checksum(bytes))
                sink.abort()
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(local.token))
                val input = NSFileHandle.fileHandleForReadingAtPath(local.token)!!
                try {
                    val data = input.readDataOfLength(3UL)
                    val actual = ByteArray(3)
                    actual.usePinned { memcpy(it.addressOf(0), data.bytes, 3UL) }
                    assertContentEquals(bytes, actual)
                } finally {
                    input.closeFile()
                }
            } finally {
                sink.release()
            }
            assertEquals(before, previews())
        }

    @Test
    fun wrongHashOrSizeCannotPublish() =
        runBlocking {
            val before = previews()
            listOf(3L to "sha256:wrong", 4L to checksum(byteArrayOf(1, 2, 3))).forEach { (size, hash) ->
                val sink = IosFileAssetDownloadSink.create("image/png")
                try {
                    sink.writeChunk(0, byteArrayOf(1, 2, 3))
                    assertFailsWith<IllegalStateException> { sink.commit(size, hash) }
                    sink.abort()
                } finally {
                    sink.release()
                }
            }
            assertEquals(before, previews())
        }

    @Test
    fun invalidChunkAndUseAfterAbortRejected() =
        runBlocking {
            val before = previews()
            val sink = IosFileAssetDownloadSink.create("image/png")
            try {
                assertFailsWith<IllegalArgumentException> { sink.writeChunk(0, byteArrayOf()) }
                assertFailsWith<IllegalArgumentException> { sink.writeChunk(1, byteArrayOf(1)) }
                sink.abort()
                sink.abort()
                assertFailsWith<IllegalStateException> { sink.writeChunk(0, byteArrayOf(1)) }
            } finally {
                sink.release()
            }
            assertEquals(before, previews())
        }

    @Test
    fun realPngDownloadIsVerifiedDecodedAndReleased() =
        runBlocking {
            val before = previews()
            val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
            val cache = bitmapPreviewCache()
            val image = IosAssetPreviewLoader(gateway(bytes), cache).load(session(), "asset-1")
            assertSame(image, IosAssetPreviewLoader(gateway(bytes), cache).load(session(), "asset-1"))
            assertEquals(1, image.width)
            assertEquals(1, image.height)
            assertEquals(before, previews())
        }

    @Test
    fun cancellationAndCorruptImageCleanAllPreviewFiles() =
        runBlocking {
            val before = previews()
            assertFailsWith<CancellationException> {
                IosAssetPreviewLoader(gateway(byteArrayOf(1, 2, 3), cancel = true)).load(session(), "asset-1")
            }
            assertEquals(before, previews())
            assertFailsWith<IllegalArgumentException> {
                IosAssetPreviewLoader(gateway(byteArrayOf(1, 2, 3))).load(session(), "asset-1")
            }
            assertEquals(before, previews())
        }

    @Test
    fun foreignWorkspaceRejectedWithoutDownloading() =
        runBlocking {
            val before = previews()
            assertFailsWith<IllegalArgumentException> {
                IosAssetPreviewLoader(gateway(byteArrayOf(1), foreign = true)).load(session(), "asset-1")
            }
            assertEquals(before, previews())
        }

    private fun session() =
        WorkspaceSession(
            "viewer",
            "client",
            WorkspaceMemberRole.Viewer,
            0,
            0,
            Workspace(WorkspaceId("workspace-1"), "Preview"),
        )

    private fun gateway(
        bytes: ByteArray,
        cancel: Boolean = false,
        foreign: Boolean = false,
    ) = object : AssetDownloadGateway {
        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ) = AssetDownloadTicket(
            WorkspaceAsset(
                "asset-1",
                if (foreign) WorkspaceId("foreign") else session.workspace.id,
                "owner",
                "image/png",
                bytes.size.toLong(),
                checksum(bytes),
                1,
                1,
                null,
                AssetStatus.Ready,
                "2026-10-01",
            ),
            "https://storage.invalid/signed",
        )

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            check(!foreign) { "Foreign authorization must never start downloading" }
            onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            if (bytes.size > 1) onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }

    private fun checksum(bytes: ByteArray): String =
        memScoped {
            val hash = allocArray<UByteVar>(32)
            bytes.usePinned { CC_SHA256(it.addressOf(0), bytes.size.toUInt(), hash) }
            "sha256:" + (0 until 32).joinToString("") { hash[it].toString(16).padStart(2, '0') }
        }

    private fun previews() =
        NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.startsWith("boarderless-preview-") }
            .toSet()
}
