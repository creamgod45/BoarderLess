package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.MediaNode
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.kotlincrypto.hash.sha2.SHA256

internal sealed interface MaterializationStage {
    data object Authorizing : MaterializationStage
    data object Recovering : MaterializationStage
    data class Downloading(val bytes: Long, val total: Long) : MaterializationStage
    data object Verifying : MaterializationStage
    data class Ready(val asset: WorkspaceAsset) : MaterializationStage
}

internal fun LocalWorkspaceSyncSnapshot.materializationReview(): Pair<cg.creamgod.boarderless.domain.model.Workspace, WorkspaceSession> {
    val target = rebaseProposal?.target ?: requireNotNull(proposal).target
    require(if (rebaseProposal != null) rebaseIsCurrent else proposalIsCurrent || appliedIsCurrent)
    return target to (rebaseProposal?.context?.remoteSession() ?: requireNotNull(proposal).remoteSession())
}

private fun LocalWorkspaceSyncSnapshot.materializationRemote(mapping: LocalAssetMaterialization): WorkspaceSession {
    require(materializations.singleOrNull { it.source.id == mapping.source.id } == mapping)
    require(checkpoint?.binding == mapping.binding && local.userId == mapping.userId && local.clientId == mapping.clientId && local.workspace.id == mapping.workspaceId)
    val (target, remote) = materializationReview()
    target.requireMaterializationReference(mapping.source)
    return remote
}

/** Explicit transfer of a saved identity only. No new IDs, abandon, canvas POST or publication.
 * Fresh source ACL is required even for a recovered local original; signed tickets stay ephemeral. */
internal suspend fun materializeRemoteAsset(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, remoteGateway: AssetDownloadGateway,
    mapping: LocalAssetMaterialization, canContinue: () -> Boolean,
    onStage: (MaterializationStage) -> Unit = {}): WorkspaceAsset = coroutineScope {
    val transferContext = currentCoroutineContext()
    fun active() { transferContext.ensureActive(); check(canContinue()) { "Materialization scope changed" } }
    fun stage(value: MaterializationStage) { active(); onStage(value); active() }
    active(); require(local.isLocalOnly && owner.canEditContent)
    require(server.syncServerScope == mapping.binding.serverScope)
    require(remoteGateway.downloadServerScope == mapping.binding.serverScope) { "Download service scope differs" }
    mapping.binding.requireRemote(selected)
    val store = requireNotNull(local.localSyncStore)
    val before = store.readSyncSnapshot(owner)
    val reviewedRemote = before.materializationRemote(mapping)
    val fresh = server.refresh(selected)
    active(); require(fresh == reviewedRemote) { "Remote changed; review again" }
    stage(MaterializationStage.Authorizing)
    val remoteTicket = remoteGateway.authorize(fresh, mapping.source.id)
    active(); require(MaterializationSourceDescriptor.fromAsset(remoteTicket.asset) == mapping.source) { "Original metadata changed" }
    val gateway = local.localAssetGateway as? PinnedLocalAssetGateway ?: error("Pinned local originals unavailable")
    val localDownload = gateway as? AssetDownloadGateway ?: error("Local original verification unavailable")
    validatePinnedLocalAsset(before.local, mapping.pendingAsset())
    require(store.readSyncSnapshot(owner) == before) { "Local review changed" }
    val ticket = gateway.preparePinned(before.local, mapping.pendingAsset())
    active(); require(ticket.asset == mapping.pendingAsset() || ticket.asset == mapping.pendingAsset().copy(status = AssetStatus.Ready))
    stage(MaterializationStage.Recovering)
    val recovered = gateway.awaitReady(before.local, mapping.localAssetId) {}
    active(); require(recovered == ticket.asset || recovered == mapping.pendingAsset().copy(status = AssetStatus.Ready))
    if (recovered.status != AssetStatus.Ready) {
        require(recovered.status == AssetStatus.Pending)
        val chunks = Channel<ByteArray>(1)
        val producer = async {
            var failure: Throwable? = null
            try {
                var received = 0L; val hash = SHA256()
                remoteGateway.download(remoteTicket) { bytes ->
                    active()
                    require(bytes.size in 1..DefaultAssetUploadChunkBytes) { "Invalid original transport chunk" }
                    require(received + bytes.size <= mapping.source.byteSize) { "Original exceeds declared size" }
                    hash.update(bytes); received += bytes.size
                    var offset = 0
                    while (offset < bytes.size) {
                        val end = minOf(offset + 64 * 1024, bytes.size)
                        chunks.send(bytes.copyOfRange(offset, end)); active(); offset = end
                    }
                    stage(MaterializationStage.Downloading(received, mapping.source.byteSize))
                }
                active(); require(received == mapping.source.byteSize) { "Original is truncated" }
                require(materializationHash(hash) == mapping.source.checksum.lowercase()) { "Original checksum mismatch" }
            } catch (error: Throwable) { failure = error; throw error }
            finally { chunks.close(failure) }
        }
        val source = object : AssetTransferSource {
            override val displayName = "workspace-original"
            override val mediaType = mapping.source.mediaType
            override val byteSize = mapping.source.byteSize
            override val checksum = mapping.source.checksum
            override val width = mapping.pendingAsset().width
            override val height = mapping.pendingAsset().height
            override val durationMs = mapping.pendingAsset().durationMs
            var consumed = 0L; var held: ByteArray? = null; var heldOffset = 0
            override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray {
                active(); require(offset == consumed && maximumBytes in 1..DefaultAssetUploadChunkBytes)
                // Fill aligned chunks independent of HTTP callback boundaries, enabling exact
                // persisted-prefix replay without retaining the full original in memory.
                val result = ByteArray(minOf(maximumBytes.toLong(), byteSize - consumed).toInt())
                var written = 0
                while (written < result.size) {
                    active()
                    val bytes = held ?: chunks.receiveCatching().getOrThrow().also { held = it; heldOffset = 0 }
                    active()
                    val count = minOf(result.size - written, bytes.size - heldOffset)
                    bytes.copyInto(result, written, heldOffset, heldOffset + count)
                    written += count; heldOffset += count
                    if (heldOffset == bytes.size) held = null
                }
                active(); consumed += result.size; return result
            }
        }
        try {
            gateway.upload(ticket.copy(asset = recovered), source) {}
            require(source.consumed == mapping.source.byteSize) { "Local sink did not consume the original" }
            active(); producer.await() // No completion before EOF, trailing-byte and hash checks.
        } finally { producer.cancel(); chunks.cancel() }
        stage(MaterializationStage.Verifying)
        val confirmed = gateway.confirm(before.local, ticket.copy(asset = recovered))
        active(); require(confirmed == mapping.pendingAsset().copy(status = AssetStatus.Ready))
    }
    stage(MaterializationStage.Verifying)
    // Re-read/hash the actual private original even when its catalog marker was already Ready.
    val verifiedLocal = verifyMaterializedLocalOriginal(localDownload, before.local, mapping, canContinue)
    active()
    require(server.refresh(fresh) == fresh) { "Remote changed during materialization" }
    active()
    val renewed = remoteGateway.authorize(fresh, mapping.source.id)
    active(); require(MaterializationSourceDescriptor.fromAsset(renewed.asset) == mapping.source)
    require(store.readSyncSnapshot(owner) == before) { "Local review changed during materialization" }
    active(); stage(MaterializationStage.Ready(verifiedLocal)); verifiedLocal
}

/** Read-only original verification: never prepare, download remote bytes, upload or confirm. */
internal suspend fun verifyMaterializedLocalOriginal(gateway: AssetDownloadGateway, owner: WorkspaceSession,
    mapping: LocalAssetMaterialization, canContinue: () -> Boolean): WorkspaceAsset {
    val context = currentCoroutineContext()
    fun active() { context.ensureActive(); check(canContinue()) }
    active()
    require(gateway.downloadServerScope == null) { "Expected private local original gateway" }
    require(owner.userId == mapping.userId && owner.clientId == mapping.clientId && owner.workspace.id == mapping.workspaceId)
    val localTicket = gateway.authorize(owner, mapping.localAssetId)
    active(); require(localTicket.asset == mapping.pendingAsset().copy(status = AssetStatus.Ready))
    var verifiedBytes = 0L; val hash = SHA256()
    gateway.download(localTicket) { bytes ->
        active(); require(bytes.size in 1..DefaultAssetUploadChunkBytes && verifiedBytes + bytes.size <= mapping.source.byteSize)
        hash.update(bytes); verifiedBytes += bytes.size
    }
    active(); require(verifiedBytes == mapping.source.byteSize && materializationHash(hash) == mapping.source.checksum.lowercase())
    return localTicket.asset
}

private fun materializationHash(hash: SHA256): String = "sha256:" + hash.digest().joinToString("") {
    (it.toInt() and 255).toString(16).padStart(2, '0')
}
