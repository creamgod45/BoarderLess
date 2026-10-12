package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.kotlincrypto.hash.sha2.SHA256

/** Explicit ordinary-picker v2 steps. The host obtains authorization covering each write
 * (individual steps or the explicitly described never-attempted new-original flow);
 * discovery/lookup cannot grant consent, PUT or node insertion authority. No retries,
 * v1 fallback, automatic completion, abandonment or replacement request allocation. */
internal class PickedAssetUploadCoordinator(
    private val store: PickedAssetUploadStore,
    private val gateway: BackendAssetPreparationGateway,
    private val canContinue: (AssetPreparationWireRequest) -> Boolean,
) {
    private suspend fun guard(request: AssetPreparationWireRequest): () -> Unit {
        val context = currentCoroutineContext()
        return {
            context.ensureActive()
            require(request.serverScope == gateway.serverScope && canContinue(request)) { "Upload scope changed" }
        }
    }

    suspend fun reserve(session: WorkspaceSession, source: AssetTransferSource, requestId: String): DurablePickedAssetUpload {
        val request = preparePickedAssetWireRequest(gateway.serverScope, session, source, requestId)
        val active = guard(request); active()
        // Covers an index-only interrupted reservation without manufacturing another ID.
        val existing = store.readDurable(request)
        active()
        return existing ?: store.save(null, PickedAssetUploadPreparation(request)).also { active() }
    }

    suspend fun discover(session: WorkspaceSession): List<AssetPreparationWireRequest> {
        require(session.canEditContent)
        return store.discover(PickedAssetUploadScope(gateway.serverScope, session.userId,
            session.clientId, session.workspace.id)).also { requests ->
            requests.forEach { guard(it).invoke() }
        }
    }

    suspend fun prepare(request: AssetPreparationWireRequest): AssetPreparationReceipt {
        val active = guard(request); active()
        val current = requireNotNull(store.readDurable(request))
        require(current.value.remoteIdentity == null) { "Use lookup for a known preparation" }
        active()
        val attempted = store.save(current.generation, current.value.copy(prepareAttempted = true))
        active()
        return saveReceipt(attempted, gateway.prepare(request, active)).also { active() }
    }

    suspend fun lookup(request: AssetPreparationWireRequest): AssetPreparationReceipt {
        val active = guard(request); active()
        val current = requireNotNull(store.readDurable(request))
        active()
        return saveReceipt(current, gateway.lookup(request)).also { active() }
    }

    suspend fun reserveTicket(request: AssetPreparationWireRequest, ticketRequestId: String): DurablePickedAssetUpload {
        val active = guard(request); active()
        val current = requireNotNull(store.readDurable(request))
        active()
        return store.save(current.generation, current.value.reserveTicket(ticketRequestId)).also { active() }
    }

    suspend fun issueTicket(request: AssetPreparationWireRequest): PreparedAssetUploadTicket {
        val active = guard(request); active()
        val current = requireNotNull(store.readDurable(request))
        active()
        val result = gateway.issueTicket(current.value, requireNotNull(current.value.ticketRequestIds.lastOrNull()), active)
        active()
        require(result.receipt.identity == current.value.remoteIdentity)
        store.save(current.generation, current.value.bindLease(requireNotNull(result.receipt.lease)))
        active()
        return result // Only after the lease is durably confirmed. Never serialize this ticket.
    }

    suspend fun complete(request: AssetPreparationWireRequest): AssetPreparationReceipt {
        val active = guard(request); active()
        val current = requireNotNull(store.readDurable(request))
        val lease = requireNotNull(current.value.leases.singleOrNull {
            it.ticketRequestId == current.value.ticketRequestIds.lastOrNull()
        })
        active()
        val attempted = store.save(current.generation, current.value.markCompletionAttempt(lease))
        active()
        return saveReceipt(attempted, gateway.complete(attempted.value, lease, active)).also { active() }
    }

    /** One explicitly approved bounded PUT, no automatic complete or node insertion.
     * Bypass direct-source shortcuts to verify the bytes of the reselected original. */
    suspend fun upload(request: AssetPreparationWireRequest, ticket: PreparedAssetUploadTicket,
        source: AssetTransferSource, transfer: AssetTransferGateway, onProgress: (Long) -> Unit) {
        val active = guard(request); active()
        var current = requireNotNull(store.readDurable(request))
        current.value.requireReselectedSource(source)
        require((transfer as? AssetDownloadGateway)?.downloadServerScope == gateway.serverScope)
        val lease = requireNotNull(ticket.receipt.lease)
        require(lease in current.value.leases && lease.ticketRequestId == current.value.ticketRequestIds.lastOrNull())
        require(ticket.receipt.requestId == request.requestId && ticket.receipt.requestDigest == request.requestDigest)
        require(ticket.receipt.identity == current.value.remoteIdentity && ticket.receipt.phase == "prepared")
        val asset = ticket.ticket.asset
        require(MaterializationSourceDescriptor(asset.id, asset.workspaceId, asset.ownerId, asset.mediaType,
            asset.byteSize, asset.checksum, asset.width, asset.height, asset.durationMs, asset.createdAt) == current.value.remoteIdentity)
        active()
        val observed = gateway.lookup(request)
        active()
        require(observed.identity == current.value.remoteIdentity && observed.phase == "prepared" && observed.lease == lease)
        require(store.readDurable(request) == current) { "Upload reservation changed" }
        current = store.save(current.generation, current.value.markUploadAttempt(lease))
        active()
        var offset = 0L
        val hash = SHA256()
        val verifiedSource = object : AssetTransferSource by source {
            override suspend fun readChunk(requestedOffset: Long, maximumBytes: Int): ByteArray {
                active()
                require(requestedOffset == offset && maximumBytes in 1..DefaultAssetUploadChunkBytes)
                require(store.readDurable(request) == current) { "Upload reservation changed" }
                active()
                val remaining = request.metadata.byteSize - offset
                require(remaining > 0)
                val bytes = source.readChunk(offset, minOf(maximumBytes.toLong(), remaining).toInt())
                active()
                require(bytes.isNotEmpty() && bytes.size <= maximumBytes && bytes.size.toLong() <= remaining)
                hash.update(bytes); offset += bytes.size
                if (offset == request.metadata.byteSize) {
                    val digest = "sha256:" + hash.digest().joinToString("") {
                        (it.toInt() and 255).toString(16).padStart(2, '0')
                    }
                    if (digest != request.metadata.checksum) throw PickedAssetOriginalChangedException()
                    require(source.readChunk(offset, 1).isEmpty()) { "Reselected original exceeds declared size" }
                    active()
                }
                return bytes
            }
        }
        transfer.upload(ticket.ticket, verifiedSource) { bytes -> active(); onProgress(bytes) }
        active()
        require(offset == request.metadata.byteSize)
        require(store.readDurable(request) == current) { "Upload reservation changed" }
        active()
    }

    private suspend fun saveReceipt(current: DurablePickedAssetUpload, receipt: AssetPreparationReceipt): AssetPreparationReceipt {
        val request = current.value.request
        val active = guard(request); active()
        require(receipt.requestId == request.requestId && receipt.requestDigest == request.requestDigest)
        val bound = current.value.bindIdentity(receipt.identity)
        // A receipt can observe only an already locally reserved lease, not authorize one.
        val next = receipt.lease?.takeIf { it.ticketRequestId == bound.ticketRequestIds.lastOrNull() }
            ?.let(bound::bindLease) ?: bound
        store.save(current.generation, next)
        active()
        return receipt
    }
}
