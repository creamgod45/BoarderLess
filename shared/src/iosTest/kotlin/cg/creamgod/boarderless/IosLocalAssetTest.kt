@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.IosFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.mediaNodeFromReadyAsset
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import platform.posix.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class IosLocalAssetTest {
    @Test fun documentMappingReopensBeforeOriginalUploadAndKeepsReviewedCanvasUnapplied() = fixture { root ->
        val repo = IosFileWorkspaceRepository("$root/documents"); val selected = source(root)
        try {
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "reader", WorkspaceId("remote"))
            val remote = WorkspaceAsset("remote-image", binding.workspaceId, "creator", selected.mediaType,
                selected.byteSize, selected.checksum, selected.width, selected.height, selected.durationMs, AssetStatus.Ready,
                "2026-10-11T00:00:00Z")
            val reviewed = savedRemoteMediaReview(repo, binding, remote)
            val mapping = newLocalAssetMaterialization(reviewed, remote, "00112233-4455-6677-8899-aabbccddeeff", "2026-10-11T01:00:00Z")
            val saved = repo.localSyncStore!!.stageSyncMaterialization(reviewed.local, reviewed.checkpoint!!, mapping)
            assertTrue(repo.listAssets(saved.local).isEmpty())
            val reopened = IosFileWorkspaceRepository("$root/documents")
            val current = reopened.openOrCreateWorkspace()
            assertEquals(saved, reopened.localSyncStore!!.readSyncSnapshot(current))
            val server = MaterializationTestServer(reopened, saved.proposal!!.remoteSession(), remote, png)
            val ready = materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true })
            assertEquals(AssetStatus.Ready, ready.status)
            assertEquals(saved, reopened.localSyncStore!!.readSyncSnapshot(current))
            assertNull(saved.applied); assertTrue(saved.local.workspace.objects.isEmpty())
            assertEquals(ready, materializeRemoteAsset(reopened, current, server, server.current, server, mapping, { true }))
            assertEquals(1, server.downloads); assertEquals(4, server.authorizations)
        } finally { selected.release() }
    }
    @Test fun pinnedOriginalIdentityReopensWithoutResettingReadyMetadata() = fixture { root ->
        val repo = IosFileWorkspaceRepository("$root/documents"); val owner = repo.openOrCreateWorkspace(); val selected = source(root)
        try {
            val pending = WorkspaceAsset("00112233-4455-6677-8899-aabbccddeeff", owner.workspace.id, owner.userId,
                selected.mediaType, selected.byteSize, selected.checksum, selected.width, selected.height, selected.durationMs,
                AssetStatus.Pending, "2026-10-11T00:00:00Z")
            val ticket = repo.localAssetGateway.preparePinned(owner, pending)
            val reopened = IosFileWorkspaceRepository("$root/documents")
            val gateway = reopened.localAssetGateway
            assertEquals(ticket, gateway.preparePinned(owner, pending))
            assertFails { gateway.preparePinned(owner, pending.copy(width = 2)) }
            gateway.upload(ticket, selected) {}
            val ready = gateway.confirm(owner, ticket)
            assertEquals(ready, gateway.preparePinned(owner, pending).asset)
            assertEquals(listOf(ready), reopened.listAssets(owner))
        } finally { selected.release() }
    }
    private val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
    private fun fixture(block: suspend (String) -> Unit) = runBlocking {
        val parent = checkNotNull(NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path).trimEnd('/')
        val root = "$parent/boarderless-local-assets-${NSUUID().UUIDString}"
        check(mkdir(root, 448u) == 0)
        try { block(root) } finally { NSFileManager.defaultManager.removeItemAtPath(root, null) }
    }
    private fun write(path: String, bytes: ByteArray) {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        check(NSFileManager.defaultManager.createFileAtPath(path, data, null))
    }
    private suspend fun source(root: String): IosFileAssetTransferSource {
        val path = "$root/中文🙂.png"
        write(path, png)
        return IosFileAssetTransferSource.fromUrl(NSURL.fileURLWithPath(path))
    }
    private fun bytes(path: String): ByteArray {
        val file = checkNotNull(NSFileHandle.fileHandleForReadingAtPath(path))
        try {
            val data = file.readDataToEndOfFile()
            return ByteArray(data.length.toInt()).also { result ->
                if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        } finally { file.closeFile() }
    }
    @Test fun originalAndMediaNodeReopenWithoutPickerFileAndDownloadDecodes() = fixture { root ->
        val repo = IosFileWorkspaceRepository("$root/documents")
        val session = repo.openOrCreateWorkspace()
        val selected = source(root)
        val imported = AssetImportCoordinator(repo.localAssetGateway).import(session, selected)
        val node = mediaNodeFromReadyAsset(imported.asset, session.workspace.id, CanvasObjectId("image"), Vec2(200f, 200f), 1f, 1, "中文🙂")
        repo.retainDraft(session, session.workspace, CreateObjectsOperation("image-create", listOf(node)))
        check(NSFileManager.defaultManager.removeItemAtPath("$root/中文🙂.png", null))
        val reopened = IosFileWorkspaceRepository("$root/documents")
        val restored = reopened.openOrCreateWorkspace()
        assertEquals(node, restored.workspace.objects.getValue(node.id))
        assertEquals(listOf(imported.asset), reopened.listAssets(restored))
        val sink = IosFileAssetDownloadSink.create(imported.asset.mediaType)
        try {
            val local = AssetDownloadCoordinator(reopened.localAssetGateway).download(restored, imported.asset.id, sink)
            assertContentEquals(png, bytes(local.token))
            val preview = IosAssetPreviewLoader(reopened.localAssetGateway).load(restored, imported.asset.id)
            assertEquals(1, preview.width); assertEquals(1, preview.height)
        } finally { sink.release() }
        memScoped {
            val info = alloc<stat>()
            check(lstat("$root/documents/assets/${imported.asset.id}.original", info.ptr) == 0)
            assertEquals(384, info.st_mode.toInt() and 0x1ff)
        }
        assertEquals(imported.asset, reopened.localAssetGateway.confirm(restored, AssetUploadTicket(imported.asset.copy(status = AssetStatus.Pending), "boarderless-local:${imported.asset.id}")))
        assertFails { reopened.localAssetGateway.authorize(restored.copy(clientId = "foreign"), imported.asset.id) }
        val other = reopened.createWorkspace(restored, "Other")
        assertFails { reopened.localAssetGateway.authorize(other, imported.asset.id) }
    }
    @Test fun publishedPendingOriginalRecoversSameIdAndTruncatedOrCorruptContentFails() = fixture { root ->
        val repo = IosFileWorkspaceRepository("$root/documents")
        val session = repo.openOrCreateWorkspace()
        val selected = source(root)
        try {
            val ticket = repo.localAssetGateway.prepare(session, selected)
            repo.localAssetGateway.upload(ticket, selected) {}
            val reopened = IosFileWorkspaceRepository("$root/documents")
            val restored = reopened.openOrCreateWorkspace()
            assertEquals(AssetStatus.Pending, reopened.listAssets(restored).single().status)
            val ready = reopened.getAsset(restored, ticket.asset.id)
            assertEquals(ticket.asset.copy(status = AssetStatus.Ready), ready)
            val truncated = object : AssetTransferSource by selected { override suspend fun readChunk(offset: Long, maximumBytes: Int) = byteArrayOf() }
            assertFails { AssetImportCoordinator(reopened.localAssetGateway).import(restored, truncated) }
            assertEquals(listOf(ready), reopened.listAssets(restored))
            assertFalse(NSFileManager.defaultManager.contentsOfDirectoryAtPath("$root/documents/assets", null)!!.filterIsInstance<String>().any { it.endsWith(".part") })
            val original = "$root/documents/assets/${ready.id}.original"
            val corrupt = png.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            val fd = open(original, O_WRONLY or O_TRUNC or O_NOFOLLOW)
            check(fd >= 0)
            try { corrupt.usePinned { check(platform.posix.write(fd, it.addressOf(0), corrupt.size.toULong()) == corrupt.size.toLong()) } }
            finally { check(close(fd) == 0) }
            val sink = IosFileAssetDownloadSink.create(ready.mediaType)
            try { assertFails { AssetDownloadCoordinator(reopened.localAssetGateway).download(restored, ready.id, sink) } }
            finally { sink.release() }
            assertFails { reopened.getAsset(restored, "../../escape") }
        } finally { selected.release() }
    }
}
