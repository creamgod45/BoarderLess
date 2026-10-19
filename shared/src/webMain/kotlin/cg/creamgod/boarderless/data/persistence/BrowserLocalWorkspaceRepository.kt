@file:OptIn(kotlin.time.ExperimentalTime::class)
package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import kotlin.time.Clock

/** Persistent local documents and originals; the Web workspace mode UI is wired separately. */
fun browserLocalWorkspaceRepository(): CanvasWorkspaceRepository = BrowserLocalWorkspaceRepository()

private class BrowserLocalWorkspaceRepository(
    private val documents: CanvasWorkspaceRepository = AwaitedLocalWorkspaceRepository(
        BrowserDurableLocalRecordStore(BrowserRecordScope.Documents), { Clock.System.now().toString() },
    ),
) : CanvasWorkspaceRepository by documents {
    override val localAssetGateway = BrowserLocalAssetGateway({ session -> documents.refresh(session); Unit })
    override suspend fun listAssets(session: WorkspaceSession) = localAssetGateway.list(session)
    override suspend fun getAsset(session: WorkspaceSession, assetId: String) = localAssetGateway.recover(session, assetId)
    override fun close() { localAssetGateway.close(); documents.close() }
}
