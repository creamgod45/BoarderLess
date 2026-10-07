package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

class QuickSchemeBundleTest {
    private val bytes = ByteArray(2 * QuickSchemeBundleCodec.ChunkBytes + 79) { (it % 251).toByte() }
    private val checksum = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private val original = WorkspaceAsset("old_asset", WorkspaceId("source"), "owner", "image/gif", bytes.size.toLong(), checksum, 10, 12, 200, AssetStatus.Ready, "now")
    private val sourceSession = WorkspaceSession("owner", "client", WorkspaceMemberRole.Owner, 0, 0, Workspace(WorkspaceId("source"), "Source"))
    private val destination = sourceSession.copy(workspace = Workspace(WorkspaceId("destination"), "Destination"))
    private val payload = ClipboardPayload(nodes = emptyList(), media = listOf(
        ClipboardMedia("m1", 0f, 0f, 100f, 100f, 0f, original.id, "gif"),
        ClipboardMedia("m2", 120f, 0f, 100f, 100f, 0f, original.id, "gif")))
    private val scheme = QuickScheme(3, "動畫🙂", ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), 6, "source")
    private class File : SchemeBundleDestination {
        val parts = mutableListOf<ByteArray>()
        var committed = false
        var aborted = false
        override suspend fun writeChunk(bytes: ByteArray) { parts += bytes.copyOf() }
        override suspend fun commit() { committed = true }
        override suspend fun abort() { aborted = true; parts.clear() }
        fun bytes() = parts.fold(byteArrayOf()) { all, part -> all + part }
    }
    private class Source(var content: ByteArray, val shortReads: Boolean = false) : SchemeBundleSource {
        override val byteSize get() = content.size.toLong()
        var released = false
        var largestRead = 0
        override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
            check(!released)
            largestRead = maxOf(largestRead, maximumBytes)
            val length = minOf(maximumBytes, if (shortReads) 997 else maximumBytes, content.size - offset.toInt())
            return content.copyOfRange(offset.toInt(), offset.toInt() + length)
        }
        override suspend fun release() { released = true }
    }
    private inner class Download(val asset: WorkspaceAsset = original, val wrongBytes: Boolean = false) : AssetDownloadGateway {
        var authorizations = 0
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            authorizations++
            return AssetDownloadTicket(asset, "https://private.invalid/signed?credential=never-export")
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
            var offset = 0
            while (offset < bytes.size) {
                val next = minOf(offset + QuickSchemeBundleCodec.ChunkBytes, bytes.size)
                val chunk = bytes.copyOfRange(offset, next)
                if (wrongBytes && offset == 0) chunk[0] = 99
                onChunk(chunk)
                offset = next
            }
        }
    }
    private inner class Upload : AssetTransferGateway {
        var prepares = 0
        var confirms = 0
        val abandoned = mutableListOf<String>()
        var failAtPrepare = Int.MAX_VALUE
        override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket {
            if (++prepares == failAtPrepare) error("prepare failed")
            return AssetUploadTicket(original.copy(id = "new_$prepares", workspaceId = session.workspace.id, status = AssetStatus.Pending), "opaque:ticket")
        }
        override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) {
            var offset = 0L
            val digest = SHA256()
            while (offset < source.byteSize) {
                val chunk = source.readChunk(offset, 64 * 1024)
                assertTrue(chunk.size in 1..64 * 1024)
                digest.update(chunk)
                offset += chunk.size
                onProgress(offset)
            }
            assertEquals(checksum, "sha256:" + digest.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
        }
        override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset { confirms++; return ticket.asset.copy(status = AssetStatus.Ready) }
        override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit): WorkspaceAsset = error("unexpected wait")
        override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? = null
        override suspend fun abandon(session: WorkspaceSession, assetId: String) { abandoned += assetId }
    }
    private suspend fun bundle(): File = File().also {
        QuickSchemeBundleCodec.export(scheme, sourceSession, Download(), it, { true }, { scheme })
    }

    @Test fun streamedOriginalRoundTripDeduplicatesSharedMediaAndRemapsBothReferences() = runTest {
        val file = bundle()
        assertTrue(file.committed)
        assertTrue(file.parts.all { it.size <= QuickSchemeBundleCodec.ChunkBytes })
        val source = Source(file.bytes(), true)
        val review = QuickSchemeBundleCodec.review(source) { true }
        assertEquals(1, review.assets.size)
        assertEquals(scheme.copy(id = 0), review.scheme)
        assertFalse("private.invalid" in file.parts.take(3).flatMap { it.asIterable() }.toByteArray().decodeToString())
        val upload = Upload()
        val pending = mutableListOf<String>()
        val ready = mutableMapOf<String, ImportedMedia>()
        val result = QuickSchemeBundleCodec.materialize(review, destination, upload, { true }, { destination },
            { _, id -> pending += id }, { ready[it.sourceId] }, { id, imported -> ready[id] = imported })
        assertEquals(1, upload.prepares)
        assertEquals(listOf("new_1"), pending)
        assertEquals(listOf("new_1", "new_1"), decodeQuickSchemePayload(result)!!.media.map { it.assetId })
        assertEquals("destination", result.sourceWorkspaceId)
        assertEquals(0, destination.workspace.objects.size)
        assertEquals(QuickSchemeBundleCodec.ChunkBytes, source.largestRead)
        val retry = QuickSchemeBundleCodec.materialize(review, destination, upload, { true }, { destination },
            { _, _ -> error("already imported") }, { ready[it.sourceId] }, { _, _ -> })
        assertEquals(result, retry)
        assertEquals(1, upload.prepares)
        review.release()
        assertTrue(source.released)
    }

    @Test fun corruptedOriginalFooterTruncatedOrTrailingBytesNeverReachReview() = runTest {
        val valid = bundle().bytes()
        val variants = listOf(valid.copyOf().also { it[it.size - 33] = (it[it.size - 33].toInt() xor 1).toByte() },
            valid.copyOf().also { it[it.lastIndex] = (it[it.lastIndex].toInt() xor 1).toByte() }, valid.copyOf(valid.size - 1), valid + byteArrayOf(0))
        for (bad in variants) {
            val source = Source(bad)
            assertFails { QuickSchemeBundleCodec.review(source) { true } }
            assertTrue(source.released)
        }
    }

    @Test fun exportRejectsWrongWorkspaceChangedSchemeAndChecksumWithoutPublishing() = runTest {
        for (download in listOf(Download(original.copy(workspaceId = WorkspaceId("wrong"))), Download(wrongBytes = true))) {
            val file = File()
            assertFails { QuickSchemeBundleCodec.export(scheme, sourceSession, download, file, { true }, { scheme }) }
            assertTrue(file.aborted)
            assertFalse(file.committed)
        }
        val file = File()
        val download = Download()
        assertFails { QuickSchemeBundleCodec.export(scheme, sourceSession, download, file, { true }, { scheme.copy(name = "changed") }) }
        assertEquals(0, download.authorizations)
        assertTrue(file.aborted)
    }

    @Test fun revokedScopeCancelsReviewAndExportBeforePublishing() = runTest {
        val source = Source(bundle().bytes())
        assertFails { QuickSchemeBundleCodec.review(source) { false } }
        assertTrue(source.released)
        val file = File()
        assertFails { QuickSchemeBundleCodec.export(scheme, sourceSession, Download(), file, { false }, { scheme }) }
        assertTrue(file.aborted)
        assertFalse(file.committed)
    }

    @Test fun changedSnapshotOrRevokedEditorPreventsAnyAssetPreparation() = runTest {
        val review = QuickSchemeBundleCodec.review(Source(bundle().bytes())) { true }
        val upload = Upload()
        assertFails { QuickSchemeBundleCodec.materialize(review, destination, upload, { true },
            { destination.copy(role = WorkspaceMemberRole.Viewer) }, { _, _ -> }, { null }, { _, _ -> }) }
        assertEquals(0, upload.prepares)
        assertFails { QuickSchemeBundleCodec.materialize(review, destination, upload, { true },
            { destination.copy(workspace = destination.workspace.copy(title = "changed")) }, { _, _ -> }, { null }, { _, _ -> }) }
        assertEquals(0, upload.prepares)
        review.release()
    }

    @Test fun fileChangedAfterReviewIsRejectedBeforeAssetPreparation() = runTest {
        val source = Source(bundle().bytes())
        val review = QuickSchemeBundleCodec.review(source) { true }
        source.content[source.content.size - 33] = 87
        val upload = Upload()
        assertFails { QuickSchemeBundleCodec.materialize(review, destination, upload, { true }, { destination }, { _, _ -> }, { null }, { _, _ -> }) }
        assertEquals(0, upload.prepares)
        assertTrue(source.released)
    }

    @Test fun readyReceiptFailureRetainsPendingIdentityForExplicitRecoveryWithoutDeletingAsset() = runTest {
        val review = QuickSchemeBundleCodec.review(Source(bundle().bytes())) { true }
        val upload = Upload()
        val pending = mutableListOf<String>()
        assertFails { QuickSchemeBundleCodec.materialize(review, destination, upload, { true }, { destination },
            { _, id -> pending += id }, { null }, { _, _ -> error("receipt write failed") }) }
        assertEquals(listOf("new_1"), pending)
        assertEquals(1, upload.confirms)
        assertTrue(upload.abandoned.isEmpty())
        assertEquals("old_asset", decodeQuickSchemePayload(review.scheme)!!.media.first().assetId)
        review.release()
    }
}
