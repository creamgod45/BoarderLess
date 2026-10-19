package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.test.*

class BrowserLocalAssetGatewayTest {
    @Test fun pinnedIdentityReopensAndReadyOriginalIsNotResetOrDuplicated() = runTest {
        install()
        try {
            val repo = browserLocalWorkspaceRepository(); val owner = repo.openOrCreateWorkspace(); val source = Source(png)
            val pending = WorkspaceAsset("00112233-4455-6677-8899-aabbccddeeff", owner.workspace.id, owner.userId,
                source.mediaType, source.byteSize, source.checksum, source.width, source.height, null,
                AssetStatus.Pending, "2026-10-11T00:00:00Z")
            val ticket = assertIs<PinnedLocalAssetGateway>(repo.localAssetGateway).preparePinned(owner, pending)
            repo.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository(); val current = reopened.openOrCreateWorkspace()
            try {
                val gateway = assertIs<PinnedLocalAssetGateway>(reopened.localAssetGateway)
                assertEquals(ticket, gateway.preparePinned(current, pending))
                assertFails { gateway.preparePinned(current, pending.copy(width = 2)) }
                gateway.upload(ticket, source) {}
                val ready = gateway.confirm(current, ticket)
                assertEquals(ready, gateway.preparePinned(current, pending).asset)
                assertEquals(listOf(ready), reopened.listAssets(current))
            } finally { reopened.close() }
        } finally { restore() }
    }
    private class Source(val bytes: ByteArray) : AssetTransferSource {
        override val displayName = "原始圖片.png"
        override val mediaType = "image/png"
        override val byteSize = bytes.size.toLong()
        override val checksum = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        override val width = 1
        override val height = 1
        override val durationMs: Long? = null
        var released = false
        override suspend fun readChunk(offset: Long, maximumBytes: Int) = bytes.copyOfRange(offset.toInt(), minOf(bytes.size, offset.toInt() + maximumBytes))
        override suspend fun release() { released = true }
    }
    private val png = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGMQMgn7DwAClAGcHVtGXwAAAABJRU5ErkJggg==")
    private fun install() { browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("allow") }
    private fun restore() { browserOriginalFixtureRestore(); browserRecordFixtureRestore() }

    @Test fun coordinatorImportsOriginalAndDocumentReopensWithLocalPreviewAndDownload() = runTest {
        install()
        try {
            val repo = browserLocalWorkspaceRepository(); val session = repo.openOrCreateWorkspace()
            val gateway = assertNotNull(repo.localAssetGateway)
            val source = Source(png)
            val imported = try { AssetImportCoordinator(gateway).import(session, source) } finally { source.release() }
            assertTrue(source.released)
            assertEquals(AssetStatus.Ready, repo.getAsset(session, imported.asset.id).status)
            assertEquals(listOf(imported.asset), repo.listAssets(session))
            val media = MediaNode(CanvasObjectId("saved-image"), transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(100f, 100f)),
                assetId = imported.asset.id, mediaKind = imported.mediaKind)
            repo.retainDraftAwaited(session, session.workspace, CreateObjectsOperation("save-image", listOf(media)))
            repo.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository(); val current = reopened.openOrCreateWorkspace()
            assertEquals(media.assetId, (current.workspace.objects[media.id] as MediaNode).assetId)
            val download = assertIs<AssetDownloadGateway>(reopened.localAssetGateway)
            val ticket = download.authorize(current, media.assetId)
            var actual: ByteArray? = null
            download.download(ticket) { actual = it }
            assertContentEquals(png, actual)
            val runtime = browserMediaImportRuntime(localGateway = download)
            val preview = assertNotNull(runtime.loadPreview).invoke(current, media.assetId)
            assertEquals(1, preview.width); assertEquals(1, preview.height)
            val exported = assertNotNull(runtime.loadExportImage).invoke(current, media.assetId, 1, 1)
            assertEquals(1, exported.width); assertEquals(1, exported.height)
            // Closing a per-preview backend adapter must not close the shared local gateway.
            download.authorize(current, media.assetId)
            assertFails { reopened.getAsset(current.copy(userId = "other"), media.assetId) }
            reopened.close()
            assertFails { download.authorize(current, media.assetId) }
        } finally { restore() }
    }
    @Test fun sealedOriginalWithFailedCatalogMutationRecoversWithoutSecondUpload() = runTest {
        install()
        try {
            val repo = browserLocalWorkspaceRepository(); val session = repo.openOrCreateWorkspace()
            val disk = BrowserDurableLocalRecordStore(BrowserRecordScope.AssetCatalog)
            var failNextWrite = false
            val catalog = AwaitedLocalAssetCatalog(object : AwaitedDurableLocalRecordStore {
                override suspend fun read(key: String) = disk.read(key)
                override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                    if (failNextWrite) { failNextWrite = false; error("Interrupted metadata publication") }
                    disk.compareAndSet(key, generation, payload)
                }
                override suspend fun confirmDurable(key: String, generation: Long) = disk.confirmDurable(key, generation)
            })
            val gateway = BrowserLocalAssetGateway({ repo.refresh(it); Unit }, catalog)
            val source = Source(png); val ticket = gateway.prepare(session, source)
            gateway.upload(ticket, source) {}
            failNextWrite = true
            assertFails { gateway.confirm(session, ticket) }
            assertEquals(AssetStatus.Pending, gateway.get(session, ticket.asset.id).status)
            gateway.abandon(session, ticket.asset.id) // Uncertain completion is preserved.
            browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            val current = reopened.openOrCreateWorkspace()
            val recovered = reopened.getAsset(current, ticket.asset.id)
            assertEquals(ticket.asset.copy(status = AssetStatus.Ready), recovered)
            val download = assertIs<AssetDownloadGateway>(reopened.localAssetGateway)
            download.download(download.authorize(current, ticket.asset.id)) { assertContentEquals(png, it) }
        } finally { restore() }
    }
    @Test fun failedBeforeCompletionCleansPrivateOriginalAndNeverPublishesReady() = runTest {
        install()
        try {
            val repo = browserLocalWorkspaceRepository(); val session = repo.openOrCreateWorkspace()
            val gateway = assertIs<BrowserLocalAssetGateway>(repo.localAssetGateway)
            var id: String? = null
            assertFails {
                // Cleanup's bounded timeout uses real browser time, not runTest's eager virtual clock.
                withContext(Dispatchers.Default) { AssetImportCoordinator(gateway, beforeComplete = { allocated ->
                    id = allocated
                    assertEquals(AssetStatus.Pending, repo.getAsset(session, allocated).status)
                    assertFails { gateway.authorize(session, allocated) }
                    error("Receipt not started")
                }).import(session, Source(png)) }
            }
            assertNotNull(id)
            assertTrue(repo.listAssets(session).isEmpty())
            assertEquals(BrowserOriginalState.Missing, BrowserOriginalStore().state(id!!, png.size.toLong(), Source(png).checksum))
        } finally { restore() }
    }
    @Test fun unknownDurableCompletionIntentRecoversSamePrivateOriginalAfterReopen() = runTest {
        browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("unknownIntentAck")
        try {
            val repo = browserLocalWorkspaceRepository(); val session = repo.openOrCreateWorkspace()
            val gateway = assertIs<BrowserLocalAssetGateway>(repo.localAssetGateway)
            val source = Source(png); val ticket = gateway.prepare(session, source)
            gateway.upload(ticket, source) {}
            assertEquals(BrowserOriginalState.Pending, BrowserOriginalStore().state(ticket.asset.id, source.byteSize, source.checksum))
            assertFails { gateway.confirm(session, ticket) }
            assertEquals(BrowserOriginalState.CompletionRequested, BrowserOriginalStore().state(ticket.asset.id, source.byteSize, source.checksum))
            gateway.abandon(session, ticket.asset.id)
            assertEquals(AssetStatus.Pending, gateway.get(session, ticket.asset.id).status)
            repo.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository(); val owner = reopened.openOrCreateWorkspace()
            val recovered = reopened.getAsset(owner, ticket.asset.id)
            assertEquals(ticket.asset.copy(status = AssetStatus.Ready), recovered)
            assertEquals(1, reopened.listAssets(owner).size)
            val download = assertIs<AssetDownloadGateway>(reopened.localAssetGateway)
            download.download(download.authorize(owner, recovered.id)) { assertContentEquals(png, it) }
        } finally { restore() }
    }
    @Test fun preparedWithoutOriginalCanBeAbandonedAndForgedTicketCannotUpload() = runTest {
        install()
        try {
            val repo = browserLocalWorkspaceRepository(); val session = repo.openOrCreateWorkspace()
            val gateway = assertIs<BrowserLocalAssetGateway>(repo.localAssetGateway)
            val source = Source(png); val ticket = gateway.prepare(session, source)
            assertFails { gateway.upload(ticket.copy(uploadUrl = "https://invalid.example"), source) {} }
            assertFails { gateway.authorize(session, ticket.asset.id) }
            gateway.abandon(session, ticket.asset.id)
            assertTrue(repo.listAssets(session).isEmpty())
            assertFails { gateway.upload(ticket, source) {} }
        } finally { restore() }
    }
}
