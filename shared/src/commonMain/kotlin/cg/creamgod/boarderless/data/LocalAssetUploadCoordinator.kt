package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.AssetPreparationReceipt
import cg.creamgod.boarderless.data.remote.BackendAssetPreparationGateway
import cg.creamgod.boarderless.data.remote.PreparedAssetUploadTicket
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.kotlincrypto.hash.sha2.SHA256

/** Explicit workflow steps, not an automatic retry loop. UI obtains approval for each
 * network-writing step. Identity writes are awaited before HTTP; unknown ACK retains
 * the caller's exact request/ticket identity. No step inserts nodes or advances baseline. */
class LocalAssetUploadCoordinator(
    private val store: LocalWorkspaceSyncStore,
    private val gateway: BackendAssetPreparationGateway,
) {
    suspend fun reserve(snapshot: LocalWorkspaceSyncSnapshot, source: WorkspaceAsset,
        requestId: String): LocalWorkspaceSyncSnapshot {
        val request = prepareLocalAssetUploadRequest(snapshot, source, requestId)
        require(request.binding.serverScope == gateway.serverScope)
        return store.stageAssetUploadPreparation(snapshot.local, null, LocalAssetUploadPreparation(request))
    }

    /** Only after approval to prepare this exact saved source/destination. */
    suspend fun prepare(snapshot: LocalWorkspaceSyncSnapshot, requestId: String): AssetPreparationReceipt {
        val current = current(snapshot, requestId)
        current.request.requireReview(snapshot)
        require(current.remoteIdentity == null) { "Use lookup for a known preparation" }
        return saveReceipt(snapshot, current, gateway.prepare(current.request))
    }

    /** A failed lookup leaves the reservation untouched; 404 never allocates another ID. */
    suspend fun lookup(snapshot: LocalWorkspaceSyncSnapshot, requestId: String): AssetPreparationReceipt {
        val current = current(snapshot, requestId)
        return saveReceipt(snapshot, current, gateway.lookup(current.request))
    }

    /** Explicitly reserve replacement identity before asking the server to supersede a lease. */
    suspend fun reserveTicket(snapshot: LocalWorkspaceSyncSnapshot, requestId: String,
        ticketRequestId: String): LocalWorkspaceSyncSnapshot {
        val current = current(snapshot, requestId)
        current.request.requireReview(snapshot)
        return store.stageAssetUploadPreparation(snapshot.local, current, current.reserveTicket(ticketRequestId))
    }

    suspend fun issueTicket(snapshot: LocalWorkspaceSyncSnapshot, requestId: String): PreparedAssetUploadTicket {
        val current = current(snapshot, requestId)
        current.request.requireReview(snapshot)
        val ticket = gateway.issueTicket(current, requireNotNull(current.ticketRequestIds.lastOrNull()))
        // If durable ACK fails, no PUT is started; reissue the same ticketRequestId later.
        store.stageAssetUploadPreparation(snapshot.local, current, current.bindLease(requireNotNull(ticket.receipt.lease)))
        return ticket
    }

    /** One explicitly approved PUT only. Cancellation leaves reservation/lease intact.
     * The wrapper deliberately avoids DirectAssetUploadSource: bounded reads also verify
     * the original bytes instead of trusting a File's cached metadata. No automatic complete. */
    suspend fun upload(snapshot: LocalWorkspaceSyncSnapshot, requestId: String,
        ticket: PreparedAssetUploadTicket, transfer: AssetTransferGateway,
        source: AssetTransferSource, canContinue: () -> Boolean,
        onProgress: (Long) -> Unit) {
        val context = currentCoroutineContext()
        fun active() { context.ensureActive(); check(canContinue()) { "Upload scope changed" } }
        active()
        val current = current(snapshot, requestId)
        current.request.requireReview(snapshot)
        require((transfer as? AssetDownloadGateway)?.downloadServerScope == gateway.serverScope)
        val lease = requireNotNull(ticket.receipt.lease)
        require(lease in current.leases && lease.ticketRequestId == current.ticketRequestIds.lastOrNull())
        require(ticket.receipt.requestId == requestId && ticket.receipt.requestDigest == current.request.requestDigest)
        require(ticket.receipt.identity == current.remoteIdentity && ticket.receipt.phase == "prepared")
        require(MaterializationSourceDescriptor.fromAsset(ticket.ticket.asset.copy(status = AssetStatus.Ready)) == current.remoteIdentity)
        val expected = current.request.source
        require(source.mediaType == expected.mediaType && source.byteSize == expected.byteSize &&
            source.checksum.lowercase() == expected.checksum.lowercase() && source.width == expected.width &&
            source.height == expected.height && source.durationMs == expected.durationMs)
        val observed = gateway.lookup(current.request)
        active()
        require(observed.identity == current.remoteIdentity && observed.phase == "prepared" && observed.lease == lease)
        require(store.readSyncSnapshot(snapshot.local) == snapshot)
        var offset = 0L
        val hash = SHA256()
        val verifiedSource = object : AssetTransferSource by source {
            override suspend fun readChunk(requestedOffset: Long, maximumBytes: Int): ByteArray {
                active(); require(requestedOffset == offset && maximumBytes in 1..DefaultAssetUploadChunkBytes)
                require(store.readSyncSnapshot(snapshot.local) == snapshot) { "Upload document changed" }
                val bytes = source.readChunk(requestedOffset, minOf(maximumBytes.toLong(), expected.byteSize - offset).toInt())
                active(); require(bytes.isNotEmpty() && bytes.size <= maximumBytes && offset + bytes.size <= expected.byteSize)
                hash.update(bytes); offset += bytes.size
                if (offset == expected.byteSize) {
                    val digest = "sha256:" + hash.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                    require(digest == expected.checksum.lowercase()) { "Local original changed" }
                    require(source.readChunk(offset, 1).isEmpty()) { "Local original exceeds declared size" }
                }
                return bytes
            }
        }
        transfer.upload(ticket.ticket, verifiedSource) { bytes -> active(); onProgress(bytes) }
        active(); require(offset == expected.byteSize)
        require(store.readSyncSnapshot(snapshot.local) == snapshot)
    }

    /** Caller must have uploaded with the durable lease; never use v1 complete. */
    suspend fun complete(snapshot: LocalWorkspaceSyncSnapshot, requestId: String): AssetPreparationReceipt {
        val current = current(snapshot, requestId)
        current.request.requireReview(snapshot)
        val lease = requireNotNull(current.leases.singleOrNull { it.ticketRequestId == current.ticketRequestIds.lastOrNull() })
        return saveReceipt(snapshot, current, gateway.complete(current, lease))
    }

    private suspend fun current(snapshot: LocalWorkspaceSyncSnapshot, requestId: String): LocalAssetUploadPreparation {
        val fresh = store.readSyncSnapshot(snapshot.local)
        require(fresh == snapshot) { "Upload review changed; reload before continuing" }
        return fresh.assetUploadPreparations.single { it.request.requestId == requestId }.also {
            require(it.request.binding.serverScope == gateway.serverScope)
        }
    }

    private suspend fun saveReceipt(snapshot: LocalWorkspaceSyncSnapshot, current: LocalAssetUploadPreparation,
        receipt: AssetPreparationReceipt): AssetPreparationReceipt {
        require(receipt.requestId == current.request.requestId && receipt.requestDigest == current.request.requestDigest)
        val bound = current.copy(remoteIdentity = receipt.identity)
        bound.requireSuccessorOf(current)
        // A lease observed via GET is only bound if its ticket was already reserved locally.
        val next = receipt.lease?.takeIf { it.ticketRequestId == current.ticketRequestIds.lastOrNull() }
            ?.let { bound.bindLease(it) } ?: bound
        store.stageAssetUploadPreparation(snapshot.local, current, next)
        return receipt
    }
}
