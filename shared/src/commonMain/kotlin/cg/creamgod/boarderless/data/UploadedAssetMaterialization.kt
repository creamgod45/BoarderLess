package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.BackendAssetPreparationGateway
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.kotlincrypto.hash.sha2.SHA256

/** Ephemeral publication input, not durable authorization or a cached Ready flag. */
class VerifiedUploadedAssetMaterialization internal constructor(
    val reviewed: LocalWorkspaceSyncSnapshot,
    val mapping: LocalAssetMaterialization,
    val preparation: LocalAssetUploadPreparation,
)

/** Fresh receipt, private original bytes, server metadata and canvas head are checked before
 * approving identity projection. This does not upload/apply/insert a node or change history. */
internal suspend fun verifyUploadedAssetMaterialization(local: CanvasWorkspaceRepository,
    snapshot: LocalWorkspaceSyncSnapshot, server: CanvasWorkspaceRepository, selected: WorkspaceSession,
    gateway: BackendAssetPreparationGateway, requestId: String,
    expectedReadyIdentity: MaterializationSourceDescriptor,
    canContinue: () -> Boolean): VerifiedUploadedAssetMaterialization {
    val context = currentCoroutineContext()
    fun active() { context.ensureActive(); check(canContinue()) }
    active()
    val store = requireNotNull(local.localSyncStore)
    require(store.readSyncSnapshot(snapshot.local) == snapshot)
    require(snapshot.outgoing == null && snapshot.rebaseProposal == null)
    val preparation = snapshot.assetUploadPreparations.single { it.request.requestId == requestId }
    val request = preparation.request
    request.requireReview(snapshot)
    require(server.syncServerScope == request.binding.serverScope && gateway.serverScope == request.binding.serverScope)
    request.binding.requireRemote(selected)
    val expectedRemote = requireNotNull(snapshot.proposal).remoteSession()
    val fresh = server.refresh(selected); active()
    require(fresh == expectedRemote && fresh.canEditContent)
    val receipt = gateway.lookup(request); active()
    require(receipt.phase == "ready" && receipt.identity == preparation.remoteIdentity)
    val readyIdentity = requireNotNull(receipt.readyIdentity)
    require(readyIdentity == expectedReadyIdentity) { "Reviewed server measurements changed; review again" }
    val remote = server.getAsset(fresh, receipt.identity.id); active()
    require(MaterializationSourceDescriptor.fromAsset(remote) == readyIdentity)
    withLocalOriginalUploadSource(local, snapshot, request) { source ->
        var offset = 0L; val hash = SHA256()
        while (offset < source.byteSize) {
            active()
            val maximum = minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt()
            val bytes = source.readChunk(offset, maximum)
            require(bytes.isNotEmpty() && bytes.size <= maximum)
            hash.update(bytes); offset += bytes.size
        }
        require(source.readChunk(offset, 1).isEmpty())
        val checksum = "sha256:" + hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        require(checksum == request.source.checksum.lowercase())
    }
    active()
    require(gateway.lookup(request).let { it.phase == "ready" && it.identity == receipt.identity && it.readyIdentity == readyIdentity })
    require(MaterializationSourceDescriptor.fromAsset(server.getAsset(fresh, remote.id)) == readyIdentity)
    require(server.refresh(fresh) == fresh); active()
    require(store.readSyncSnapshot(snapshot.local) == snapshot)
    val mapping = LocalAssetMaterialization(request.binding, request.userId, request.clientId, request.workspaceId,
        request.reviewId, readyIdentity, request.source.id, request.source.createdAt, localSource = request.source)
    val existing = snapshot.materializations.singleOrNull { it.source.id == mapping.source.id }
    require(existing == null || existing == mapping)
    MaterializedAssetProjection(snapshot.local, request.binding, if (existing == null) snapshot.materializations + mapping else snapshot.materializations)
    return VerifiedUploadedAssetMaterialization(snapshot, mapping, preparation)
}
