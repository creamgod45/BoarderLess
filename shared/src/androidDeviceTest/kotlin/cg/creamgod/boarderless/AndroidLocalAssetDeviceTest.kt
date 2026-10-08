package cg.creamgod.boarderless

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.AndroidFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.mediaNodeFromReadyAsset
import kotlinx.coroutines.runBlocking
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import kotlin.test.*

class AndroidLocalAssetDeviceTest {
    private fun fixture(block: suspend (File) -> Unit) = runBlocking {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir.canonicalFile, "local-assets-test-${UUID.randomUUID()}")
        check(root.mkdir())
        try { block(root) } finally { root.deleteRecursively() }
    }
    private fun source(root: File): AssetTransferSource {
        val original = File(root, "中文🙂.png")
        val bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888)
        try { original.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
        val hash = "sha256:" + MessageDigest.getInstance("SHA-256").digest(original.readBytes()).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return object : AssetTransferSource {
            override val displayName = original.name
            override val mediaType = "image/png"
            override val byteSize = original.length()
            override val checksum = hash
            override val width = 32
            override val height = 24
            override val durationMs: Long? = null
            override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = RandomAccessFile(original, "r").use {
                it.seek(offset)
                ByteArray(minOf(maximumBytes.toLong(), byteSize - offset).toInt()).also(it::readFully)
            }
        }
    }
    @Test fun originalCanvasAndMetadataSurviveReopenAndDownloadWithoutPickerFile() = fixture { root ->
        val repo = AndroidFileWorkspaceRepository(File(root, "documents"))
        val session = repo.openOrCreateWorkspace()
        val selected = source(root)
        val bytes = File(root, selected.displayName).readBytes()
        val imported = AssetImportCoordinator(repo.localAssetGateway).import(session, selected)
        val node = mediaNodeFromReadyAsset(imported.asset, session.workspace.id, CanvasObjectId("image"), Vec2(200f, 200f), 1f, 1, "中文🙂")
        repo.retainDraft(session, session.workspace, CreateObjectsOperation("image-create", listOf(node)))
        check(File(root, selected.displayName).delete())
        val reopened = AndroidFileWorkspaceRepository(File(root, "documents"))
        val restored = reopened.openOrCreateWorkspace()
        assertEquals(node, restored.workspace.objects.getValue(node.id))
        assertEquals(listOf(imported.asset), reopened.listAssets(restored))
        val cache = File(root, "cache").also { check(it.mkdir()) }
        val sink = AndroidFileAssetDownloadSink.create(cache, imported.asset.mediaType)
        val downloaded = AssetDownloadCoordinator(reopened.localAssetGateway).download(restored, imported.asset.id, sink)
        assertContentEquals(bytes, File(downloaded.token).readBytes())
        val bitmap = assertNotNull(BitmapFactory.decodeFile(downloaded.token))
        try { assertEquals(32, bitmap.width); assertEquals(24, bitmap.height) } finally { bitmap.recycle() }
        val stored = File(root, "documents/assets/${imported.asset.id}.original")
        assertEquals(0x180, Os.lstat(stored.path).st_mode and 0x1ff)
        assertEquals(imported.asset, reopened.localAssetGateway.confirm(restored, AssetUploadTicket(imported.asset.copy(status = AssetStatus.Pending), "boarderless-local:${imported.asset.id}")))
        assertFails { reopened.localAssetGateway.authorize(restored.copy(clientId = "foreign"), imported.asset.id) }
        val other = reopened.createWorkspace(restored, "Other")
        assertFails { reopened.localAssetGateway.authorize(other, imported.asset.id) }
    }
    @Test fun restartBeforeReadyConfirmsSameOriginalAndBadSourceOrCorruptionIsNotDownloaded() = fixture { root ->
        val repo = AndroidFileWorkspaceRepository(File(root, "documents"))
        val session = repo.openOrCreateWorkspace()
        val source = source(root)
        val ticket = repo.localAssetGateway.prepare(session, source)
        repo.localAssetGateway.upload(ticket, source) {}
        val reopened = AndroidFileWorkspaceRepository(File(root, "documents"))
        val restored = reopened.openOrCreateWorkspace()
        assertEquals(AssetStatus.Pending, reopened.listAssets(restored).single().status)
        val ready = reopened.getAsset(restored, ticket.asset.id)
        assertEquals(ticket.asset.copy(status = AssetStatus.Ready), ready)
        assertEquals(listOf(ready), reopened.listAssets(restored))
        val truncated = object : AssetTransferSource by source { override suspend fun readChunk(offset: Long, maximumBytes: Int) = byteArrayOf() }
        assertFails { AssetImportCoordinator(reopened.localAssetGateway).import(restored, truncated) }
        assertEquals(listOf(ready), reopened.listAssets(restored))
        assertFalse(File(root, "documents/assets").listFiles()!!.any { it.name.endsWith(".part") })
        val original = File(root, "documents/assets/${ready.id}.original")
        val bytes = original.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        original.writeBytes(bytes)
        val cache = File(root, "cache").also { check(it.mkdir()) }
        val sink = AndroidFileAssetDownloadSink.create(cache, ready.mediaType)
        assertFails { AssetDownloadCoordinator(reopened.localAssetGateway).download(restored, ready.id, sink) }
        assertTrue(cache.listFiles()!!.isEmpty())
        assertFails { reopened.getAsset(restored, "../../escape") }
    }
}
