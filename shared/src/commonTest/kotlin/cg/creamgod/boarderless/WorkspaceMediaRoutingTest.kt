package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class WorkspaceMediaRoutingTest {
    private fun documents(): CanvasWorkspaceRepository = PersistentLocalWorkspaceRepository(
        { object : DurableLocalRecordStore {
            override fun read(key: String): DurableLocalRecord? = null
            override fun compareAndSet(key: String, generation: Long?, payload: ByteArray) = error("unused")
            override fun confirmDurable(key: String, generation: Long) = error("unused")
        } },
        object : LocalDocumentLock { override fun <T> withLock(block: () -> T): T = block() }, { "unused" })
    private class Gateway : AssetTransferGateway, AssetDownloadGateway {
        var closes = 0
        override fun close() { closes++ }
        override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket = error("unused")
        override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) = error("unused")
        override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset = error("unused")
        override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit): WorkspaceAsset = error("unused")
        override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? = null
        override suspend fun abandon(session: WorkspaceSession, assetId: String) = error("unused")
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket = error("unused")
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = error("unused")
    }
    @Test fun localDownloadBorrowsSameGatewayAndDoesNotCloseItOnSuccessOrFailure() = runTest {
        val local = Gateway()
        val repo = object : CanvasWorkspaceRepository by documents() { override val localAssetGateway = local }
        assertEquals(42, withWorkspaceAssetDownloadGateway(repo) { assertSame(local, it); 42 })
        assertFails { withWorkspaceAssetDownloadGateway(repo) { assertSame(local, it); error("decoder failed") } }
        assertEquals(0, local.closes)
    }
    @Test fun missingLocalDownloadCapabilityNeverFallsBackToRemote() = runTest {
        var called = false
        assertFails { withWorkspaceAssetDownloadGateway(documents()) { called = true } }
        val uploadOnly = object : AssetTransferGateway by Gateway() {}
        val repo = object : CanvasWorkspaceRepository by documents() { override val localAssetGateway = uploadOnly }
        assertFails { withWorkspaceAssetDownloadGateway(repo) { called = true } }
        assertFalse(called)
    }
}
