package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidFileAssetDownloadSinkTest {
    @Test
    fun chunksBecomeLocalFileOnlyAfterVerification() =
        runBlocking {
            withDirectory { directory ->
                val bytes = byteArrayOf(1, 2, 3, 4)
                val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                sink.writeChunk(0, bytes.copyOfRange(0, 2))
                sink.writeChunk(2, bytes.copyOfRange(2, 4))
                assertTrue(directory.listFiles()!!.all { it.extension == "part" })
                val local = sink.commit(4, checksum(bytes))
                assertEquals("image/png", local.mediaType)
                assertContentEquals(bytes, File(local.token).readBytes())
                assertTrue(directory.listFiles()!!.none { it.extension == "part" })
                sink.abort() // Abort must not remove a committed file handed to its consumer.
                assertTrue(File(local.token).isFile)
            }
        }

    @Test
    fun wrongChecksumOrSizeNeverPublishesReadyFile() =
        runBlocking {
            withDirectory { directory ->
                listOf(3L to "sha256:wrong", 4L to checksum(byteArrayOf(1, 2, 3))).forEach { (size, hash) ->
                    val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                    sink.writeChunk(0, byteArrayOf(1, 2, 3))
                    assertFailsWith<IllegalStateException> { sink.commit(size, hash) }
                    assertTrue(directory.listFiles()!!.none { it.extension == "ready" })
                    sink.abort()
                    assertEquals(0, directory.listFiles()!!.size)
                }
            }
        }

    @Test
    fun modifiedLocalPartIsDetectedByCommitChecksum() =
        runBlocking {
            withDirectory { directory ->
                val bytes = byteArrayOf(1, 2, 3)
                val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                sink.writeChunk(0, bytes)
                directory.listFiles()!!.single().writeBytes(byteArrayOf(9, 8, 7))
                assertFailsWith<IllegalStateException> { sink.commit(3, checksum(bytes)) }
                sink.abort()
                assertEquals(0, directory.listFiles()!!.size)
            }
        }

    @Test
    fun invalidChunksAndUseAfterAbortFail() =
        runBlocking {
            withDirectory { directory ->
                val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                assertFailsWith<IllegalArgumentException> { sink.writeChunk(0, byteArrayOf()) }
                assertFailsWith<IllegalArgumentException> { sink.writeChunk(1, byteArrayOf(1)) }
                sink.abort()
                sink.abort()
                assertFailsWith<IllegalStateException> { sink.writeChunk(0, byteArrayOf(1)) }
                assertFailsWith<IllegalStateException> { sink.commit(1, checksum(byteArrayOf(1))) }
                assertEquals(0, directory.listFiles()!!.size)
            }
        }

    @Test
    fun independentDownloadsNeverOverwriteEachOther() =
        runBlocking {
            withDirectory { directory ->
                val refs =
                    listOf(byteArrayOf(1), byteArrayOf(2)).map { bytes ->
                        val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                        sink.writeChunk(0, bytes)
                        sink.commit(1, checksum(bytes))
                    }
                assertFalse(refs[0].token == refs[1].token)
                assertContentEquals(byteArrayOf(1), File(refs[0].token).readBytes())
                assertContentEquals(byteArrayOf(2), File(refs[1].token).readBytes())
            }
        }

    @Test
    fun coordinatorCancellationClearsPartialDownload() =
        runBlocking {
            withDirectory { directory ->
                val session =
                    WorkspaceSession(
                        "user",
                        "client",
                        WorkspaceMemberRole.Viewer,
                        0,
                        0,
                        Workspace(WorkspaceId("workspace-1"), "Preview"),
                    )
                val gateway =
                    object : AssetDownloadGateway {
                        override suspend fun authorize(
                            session: WorkspaceSession,
                            assetId: String,
                        ) = AssetDownloadTicket(
                            WorkspaceAsset(
                                "asset-1",
                                session.workspace.id,
                                "owner",
                                "image/png",
                                3,
                                checksum(byteArrayOf(1, 2, 3)),
                                null,
                                null,
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
                            onChunk(byteArrayOf(1))
                            throw CancellationException("cancelled")
                        }
                    }
                val sink = AndroidFileAssetDownloadSink.create(directory, "image/png")
                assertFailsWith<CancellationException> { AssetDownloadCoordinator(gateway).download(session, "asset-1", sink) }
                assertEquals(0, directory.listFiles()!!.size)
            }
        }

    private suspend fun withDirectory(block: suspend (File) -> Unit) {
        val directory = Files.createTempDirectory("android-asset-sink-test-").toFile()
        try {
            block(directory)
        } finally {
            directory.listFiles().orEmpty().forEach { check(it.delete()) }
            check(directory.delete())
        }
    }

    private fun checksum(bytes: ByteArray) =
        "sha256:" +
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
