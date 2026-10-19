@file:OptIn(kotlin.time.ExperimentalTime::class)
package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.Clock

/** Local originals and metadata are independently confirmed. A sealed original with an unknown
 * catalog ACK is recovered without reuploading; partial originals remain Pending.
 */
internal class BrowserLocalAssetGateway(
    private val validateSession: suspend (WorkspaceSession) -> Unit,
    private val catalog: AwaitedLocalAssetCatalog = browserLocalAssetCatalog(),
    private val originals: BrowserOriginalStore = BrowserOriginalStore(),
) : PinnedLocalAssetGateway, AssetDownloadGateway {
    private var closed = false
    private fun active() { check(!closed) { "Local asset gateway closed" } }
    private suspend fun validated(session: WorkspaceSession) {
        active(); validateSession(session); currentCoroutineContext().ensureActive(); active()
    }
    suspend fun get(session: WorkspaceSession, id: String): WorkspaceAsset {
        validated(session)
        return catalog.get(id).also {
            require(it.workspaceId == session.workspace.id && it.ownerId == session.userId)
            currentCoroutineContext().ensureActive(); active()
        }
    }
    suspend fun list(session: WorkspaceSession): List<WorkspaceAsset> {
        validated(session)
        return catalog.list(session).also { active() }
    }
    suspend fun recover(session: WorkspaceSession, id: String): WorkspaceAsset {
        val current = get(session, id)
        if (current.status != AssetStatus.Pending) return current
        val state = originals.state(id, current.byteSize, current.checksum)
        validated(session)
        if (state != BrowserOriginalState.Published && state != BrowserOriginalState.CompletionRequested) return current
        if (state == BrowserOriginalState.CompletionRequested) originals.publish(id, id, current.byteSize, current.checksum)
        originals.download(id, current.byteSize, current.checksum) { active() }
        validated(session)
        return catalog.ready(current).also { active() }
    }
    override suspend fun prepare(session: WorkspaceSession, source: AssetTransferSource): AssetUploadTicket {
        validated(session); require(session.canEditContent); validateAssetTransferSource(source)
        val asset = WorkspaceAsset(randomUuid(), session.workspace.id, session.userId,
            source.mediaType.trim().lowercase(), source.byteSize, source.checksum, source.width, source.height,
            source.durationMs, AssetStatus.Pending, Clock.System.now().toString())
        return preparePinned(session, asset)
    }
    override suspend fun preparePinned(session: WorkspaceSession, pending: WorkspaceAsset): AssetUploadTicket {
        validated(session); validatePinnedLocalAsset(session, pending)
        val stored = catalog.ensurePinned(pending)
        validated(session)
        return AssetUploadTicket(stored, "boarderless-local:${stored.id}")
    }
    private fun checkUpload(ticket: AssetUploadTicket) {
        active(); validateLocalAssetId(ticket.asset.id)
        require(ticket.uploadUrl == "boarderless-local:${ticket.asset.id}" && ticket.requiredHeaders.isEmpty())
    }
    override suspend fun upload(ticket: AssetUploadTicket, source: AssetTransferSource, onProgress: (Long) -> Unit) {
        checkUpload(ticket); validateAssetTransferSource(source)
        val asset = ticket.asset
        require(asset.status == AssetStatus.Pending && catalog.get(asset.id) == asset)
        require(asset.byteSize == source.byteSize && asset.checksum == source.checksum && asset.mediaType == source.mediaType.trim().lowercase() &&
            asset.width == source.width && asset.height == source.height && asset.durationMs == source.durationMs)
        active(); val prefix = originals.resumePrefix(asset.id, asset.id, asset.byteSize)
        var offset = 0L
        while (offset < asset.byteSize) {
            currentCoroutineContext().ensureActive(); active()
            val limit = minOf(DefaultAssetUploadChunkBytes.toLong(), asset.byteSize - offset).toInt()
            val bytes = source.readChunk(offset, limit)
            require(bytes.isNotEmpty() && bytes.size <= limit)
            currentCoroutineContext().ensureActive(); active()
            if (offset < prefix) {
                require(offset + bytes.size <= prefix) { "Original prefix chunk boundary differs" }
                originals.verifyPrefixChunk(asset.id, asset.id, asset.byteSize, offset, bytes)
            } else originals.write(asset.id, asset.id, asset.byteSize, offset, bytes)
            currentCoroutineContext().ensureActive(); active()
            offset += bytes.size; onProgress(offset)
        }
        // Verify bytes while still private; durable completion intent and publication belong to confirm.
        originals.verifyPending(asset.id, asset.id, asset.byteSize, asset.checksum)
        active()
    }
    override suspend fun confirm(session: WorkspaceSession, ticket: AssetUploadTicket): WorkspaceAsset {
        checkUpload(ticket); require(session.canEditContent)
        val current = get(session, ticket.asset.id)
        require(current == ticket.asset || current == ticket.asset.copy(status = AssetStatus.Ready))
        originals.requestCompletion(current.id, current.id, current.byteSize, current.checksum)
        originals.publish(current.id, current.id, current.byteSize, current.checksum)
        validated(session)
        return catalog.ready(current).also { active() }
    }
    override suspend fun awaitReady(session: WorkspaceSession, assetId: String, onStatus: (AssetStatus) -> Unit): WorkspaceAsset =
        recover(session, assetId).also { onStatus(it.status) }
    override suspend fun thumbnailAssetId(session: WorkspaceSession, assetId: String): String? { get(session, assetId); return null }
    override suspend fun abandon(session: WorkspaceSession, assetId: String) {
        require(session.canEditContent)
        val asset = get(session, assetId)
        if (asset.status != AssetStatus.Pending) return
        // An uncertain seal can already be complete: never delete it as a pending upload.
        val state = originals.state(assetId, asset.byteSize, asset.checksum)
        validated(session)
        if (state == BrowserOriginalState.Published || state == BrowserOriginalState.CompletionRequested) return
        originals.abandon(assetId, assetId, asset.byteSize)
        validated(session); catalog.abandon(asset); active()
    }
    override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
        val asset = recover(session, assetId)
        require(asset.status == AssetStatus.Ready)
        return AssetDownloadTicket(asset, "boarderless-local:$assetId")
    }
    override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
        active()
        require(ticket.downloadUrl == "boarderless-local:${ticket.asset.id}" && ticket.requiredHeaders.isEmpty())
        require(ticket.asset.status == AssetStatus.Ready && catalog.get(ticket.asset.id) == ticket.asset)
        originals.download(ticket.asset.id, ticket.asset.byteSize, ticket.asset.checksum) {
            active(); onChunk(it); currentCoroutineContext().ensureActive(); active()
        }
        active()
    }
    override fun close() { closed = true }
}
