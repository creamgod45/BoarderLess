package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.*

/** UI-safe reference. Private request metadata never becomes a signed directive. */
class PreparedMediaImportReservation internal constructor(internal val request: AssetPreparationWireRequest) {
    val requestId: String get() = request.requestId
    val mediaType: String get() = request.metadata.mediaType
    val byteSize: Long get() = request.metadata.byteSize
}
class PreparedMediaImportTicket internal constructor(internal val value: PreparedAssetUploadTicket)
data class PreparedMediaImportStatus(val requestId: String, val phase: String, val assetId: String,
    val readyAsset: WorkspaceAsset? = null, val rejectionReason: AssetRejectionReason? = null)
data class PreparedMediaImportRecovery(val hasKnownIdentity: Boolean, val latestTicketRequestId: String?,
    val prepareAttempted: Boolean, val uploadAttempted: Boolean, val completionAttempted: Boolean)

/** One scoped modal owns these clients. Each write requires an explicit step or new-upload flow authorization;
 * persisted reservations remain recoverable after closing, with no retained source. */
class PreparedMediaImportController internal constructor(
    private val session: WorkspaceSession,
    private val store: PickedAssetUploadStore,
    private val gateway: BackendAssetPreparationGateway,
    private val transfer: AssetTransferGateway,
    private val assets: AssetRepository,
    private val canContinue: () -> Boolean,
) {
    private var closed = false
    private val coordinator = PickedAssetUploadCoordinator(store, gateway) {
        !closed && canContinue() && it.userId == session.userId && it.clientId == session.clientId &&
            it.workspaceId == session.workspace.id && session.canEditContent
    }
    fun isActive(): Boolean = !closed && canContinue() && session.canEditContent
    private fun active() { check(isActive()) }
    private fun request(ref: PreparedMediaImportReservation): AssetPreparationWireRequest {
        active()
        return ref.request.also {
            require(it.serverScope == gateway.serverScope && it.userId == session.userId &&
                it.clientId == session.clientId && it.workspaceId == session.workspace.id)
        }
    }
    suspend fun discover(archived: Boolean = false): List<PreparedMediaImportReservation> {
        active()
        return store.discover(PickedAssetUploadScope(gateway.serverScope, session.userId, session.clientId,
            session.workspace.id), archived).map(::PreparedMediaImportReservation).also { active() }
    }
    /** Fresh lookup, not a cached UI phase, must prove terminal before hiding a record. */
    suspend fun archive(ref: PreparedMediaImportReservation) {
        val wire = request(ref)
        val receipt = coordinator.lookup(wire)
        require(receipt.phase in setOf("ready", "rejected", "missing", "abandoned")) { "Unresolved upload cannot be archived" }
        active()
        store.setArchived(wire, true)
        active()
    }
    /** Local visibility restore. No prepare, ticket, PUT or asset revival. */
    suspend fun unarchive(ref: PreparedMediaImportReservation) {
        store.setArchived(request(ref), false)
        active()
    }
    suspend fun reserve(source: AssetTransferSource, requestId: String): PreparedMediaImportReservation {
        active()
        return PreparedMediaImportReservation(coordinator.reserve(session, source, requestId).value.request).also { active() }
    }
    /** Rebuild a locally interrupted index-only reservation using its exact identity. */
    suspend fun recoverReservation(ref: PreparedMediaImportReservation): PreparedMediaImportRecovery {
        val wire = request(ref)
        val saved = store.readDurable(wire) ?: store.save(null, PickedAssetUploadPreparation(wire))
        active()
        return PreparedMediaImportRecovery(saved.value.remoteIdentity != null, saved.value.ticketRequestIds.lastOrNull(),
            saved.value.prepareAttempted, saved.value.uploadAttemptLeaseIds.isNotEmpty(), saved.value.completionAttemptLeaseIds.isNotEmpty())
    }
    suspend fun validateReselected(ref: PreparedMediaImportReservation, source: AssetTransferSource) {
        val saved = requireNotNull(store.readDurable(request(ref)))
        saved.value.requireReselectedSource(source)
        active()
    }
    suspend fun prepare(ref: PreparedMediaImportReservation) = status(coordinator.prepare(request(ref)))
    suspend fun lookup(ref: PreparedMediaImportReservation) = status(coordinator.lookup(request(ref)))
    /** Explicit bounded observation of the ORIGINAL preparation. No prepare/ticket/PUT,
     * complete, abandonment or canvas insertion. A failed read stops rather than retries.
     * A timeout returns only a previously verified status, never an invented ready phase. */
    suspend fun watchProcessing(ref: PreparedMediaImportReservation,
        onStatus: (PreparedMediaImportStatus, Int) -> Unit): PreparedMediaImportStatus? {
        request(ref)
        var latest: PreparedMediaImportStatus? = null
        kotlinx.coroutines.withTimeoutOrNull(60_000) {
            repeat(20) { index ->
                active()
                val observed = lookup(ref)
                active()
                latest = observed
                onStatus(observed, index + 1)
                active()
                if (observed.phase != "completionAccepted") return@withTimeoutOrNull
                if (index < 19) kotlinx.coroutines.delay(3_000)
            }
        }
        active()
        return latest
    }
    suspend fun ticket(ref: PreparedMediaImportReservation, ticketRequestId: String): PreparedMediaImportTicket {
        val wire = request(ref)
        coordinator.reserveTicket(wire, ticketRequestId)
        return PreparedMediaImportTicket(coordinator.issueTicket(wire)).also { active() }
    }
    suspend fun upload(ref: PreparedMediaImportReservation, ticket: PreparedMediaImportTicket,
        source: AssetTransferSource, progress: (Long) -> Unit) =
        coordinator.upload(request(ref), ticket.value, source, transfer, progress)
    suspend fun complete(ref: PreparedMediaImportReservation) = status(coordinator.complete(request(ref)))
    /** One explicit authorization for a never-attempted original only. No replay/replacement,
     * polling, abandonment or canvas insertion. Every step retains its existing durable intent. */
    suspend fun uploadNewOriginal(
        ref: PreparedMediaImportReservation,
        source: AssetTransferSource,
        onStatus: (PreparedMediaImportStatus) -> Unit,
        onTicketRequest: (String) -> Unit,
        onTicket: (PreparedMediaImportTicket) -> Unit,
        onProgress: (Long) -> Unit,
    ): PreparedMediaImportStatus {
        val original = recoverReservation(ref)
        require(!original.hasKnownIdentity && !original.prepareAttempted && original.latestTicketRequestId == null &&
            !original.uploadAttempted && !original.completionAttempted) { "Original upload requires recovery" }
        validateReselected(ref, source)
        active()
        val prepared = prepare(ref)
        active(); onStatus(prepared); active()
        if (prepared.phase != "prepared") return prepared
        val ticketId = randomUuid()
        onTicketRequest(ticketId); active()
        val lease = ticket(ref, ticketId)
        active(); onTicket(lease); active()
        upload(ref, lease, source, onProgress)
        active()
        return complete(ref).also { active(); onStatus(it); active() }
    }
    private suspend fun status(receipt: AssetPreparationReceipt): PreparedMediaImportStatus {
        active()
        val ready = receipt.readyIdentity?.let { expected ->
            assets.getAsset(session, expected.id).also {
                active()
                require(MaterializationSourceDescriptor.fromAsset(it) == expected)
            }
        }
        active()
        return PreparedMediaImportStatus(receipt.requestId, receipt.phase, receipt.identity.id, ready, receipt.rejectionReason)
    }
    fun close() {
        if (closed) return
        closed = true
        try { gateway.close() } finally { transfer.close() }
    }
}
