package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.*

class AndroidGifAnimationTest {
    @Test fun metadataPreservesDelaysAndLoopSemantics() =
        runBlocking {
            val bytes = twoFrameGifFixture()
            val animation = AndroidGifAnimation.decode(bytes)
            assertEquals(2, animation.frameCount)
            assertEquals(-1, animation.repetitionCount)
            assertEquals(50L, animation.durationMs(0))
            assertEquals(100L, animation.durationMs(1))
            animation.release()
            animation.release()
            assertFailsWith<IllegalStateException> { animation.frame(0) }
            val finite = bytes.copyOf().apply { this[35] = 2 }
            val finiteAnimation = AndroidGifAnimation.decode(finite)
            try {
                assertEquals(2, finiteAnimation.repetitionCount)
            } finally {
                finiteAnimation.release()
            }
            val once = AndroidGifAnimation.decode(bytes.copyOfRange(0, 19) + bytes.copyOfRange(38, bytes.size))
            try {
                assertEquals(0, once.repetitionCount)
            } finally {
                once.release()
            }
        }

    @Test fun structuralValidationRejectsOversizedFrameAndTruncation() {
        val bytes = twoFrameGifFixture()
        validateAndroidGifStructure(bytes)
        assertFails { validateAndroidGifStructure(bytes.copyOf(bytes.size - 1)) }
        assertFails {
            validateAndroidGifStructure(
                bytes.copyOf().apply {
                    this[51] = 255.toByte()
                    this[52] = 127
                    this[53] = 255.toByte()
                    this[54] =
                        127
                },
            )
        }
        assertFails { AndroidGifAnimation.decode(ByteArray(20)) }
    }

    @Test fun verifiedDownloadCleansTemporaryFilesBeforeReturningDecoder() =
        runBlocking {
            withDirectory { directory ->
                val gateway = Gateway(twoFrameGifFixture())
                val animation = loadAndroidGif(gateway, session(), "gif", directory)
                try {
                    assertEquals(1, gateway.downloads)
                    assertEquals(2, animation.frameCount)
                    assertEquals(0, directory.listFiles()!!.size)
                } finally {
                    animation.release()
                }
            }
        }

    @Test fun checksumCancellationAndInvalidContentRemoveFiles() =
        runBlocking {
            withDirectory { directory ->
                assertFails { loadAndroidGif(Gateway(twoFrameGifFixture(), badHash = true), session(), "gif", directory) }
                assertFailsWith<CancellationException> {
                    loadAndroidGif(
                        Gateway(twoFrameGifFixture(), cancel = true),
                        session(),
                        "gif",
                        directory,
                    )
                }
                assertFails { loadAndroidGif(Gateway(ByteArray(20)), session(), "gif", directory) }
                assertEquals(0, directory.listFiles()!!.size)
            }
        }

    @Test fun foreignOrPendingAssetNeverDownloads() =
        runBlocking {
            withDirectory { directory ->
                listOf(Gateway(twoFrameGifFixture(), foreign = true), Gateway(twoFrameGifFixture(), pending = true)).forEach { gateway ->
                    assertFails { loadAndroidGif(gateway, session(), "gif", directory) }
                    assertEquals(0, gateway.downloads)
                }
                assertEquals(0, directory.listFiles()!!.size)
            }
        }

    private fun session() = WorkspaceSession("viewer", "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId("w"), "GIF"))

    private class Gateway(
        private val bytes: ByteArray,
        private val badHash: Boolean = false,
        private val cancel: Boolean = false,
        private val foreign: Boolean = false,
        private val pending: Boolean = false,
    ) : AssetDownloadGateway {
        var downloads = 0

        override suspend fun authorize(
            session: WorkspaceSession,
            assetId: String,
        ) = AssetDownloadTicket(
            WorkspaceAsset(
                assetId,
                if (foreign) WorkspaceId("other") else session.workspace.id,
                "owner",
                "image/gif",
                bytes.size.toLong(),
                if (badHash) {
                    "sha256:wrong"
                } else {
                    "sha256:" +
                        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                },
                1,
                1,
                null,
                if (pending) AssetStatus.Pending else AssetStatus.Ready,
                "2026-10-02",
            ),
            "https://storage.invalid/gif",
        )

        override suspend fun download(
            ticket: AssetDownloadTicket,
            onChunk: suspend (ByteArray) -> Unit,
        ) {
            downloads++
            onChunk(bytes.copyOfRange(0, 1))
            if (cancel) throw CancellationException("cancelled")
            onChunk(bytes.copyOfRange(1, bytes.size))
        }
    }

    private suspend fun withDirectory(block: suspend (java.io.File) -> Unit) {
        val directory = Files.createTempDirectory("android-gif-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.listFiles().orEmpty().forEach {
                it.listFiles().orEmpty().forEach { file ->
                    check(file.delete())
                }
                check(it.delete())
            }
            check(directory.delete())
        }
    }
}
