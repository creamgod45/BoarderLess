package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

private val PrepareUploadJson = Json { encodeDefaults = true }

/** Fixed alphabetical field order, explicit nulls, normalized MIME/checksum; matches v2 spec. */
@Serializable internal data class AssetPreparationMetadata(
    val byteSize: Long,
    val checksum: String,
    val durationMs: Long?,
    val height: Int?,
    val mediaType: String,
    val width: Int?,
)

internal fun MaterializationSourceDescriptor.uploadMetadata() = AssetPreparationMetadata(
    byteSize, checksum.lowercase(), durationMs, height, mediaType, width)

internal fun assetPreparationDigest(metadata: AssetPreparationMetadata): String = SHA256()
    .digest(PrepareUploadJson.encodeToString(metadata).encodeToByteArray(throwOnInvalidSequence = true))
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

private fun preparationDigest(source: MaterializationSourceDescriptor) = assetPreparationDigest(source.uploadMetadata())

/** Shared transport identity. Contains no file path, local asset mapping, signed URL
 * or review authority. Must be reserved durably by the caller before prepare HTTP. */
@Serializable internal data class AssetPreparationWireRequest(
    val serverScope: String,
    val userId: String,
    val clientId: String,
    val workspaceId: WorkspaceId,
    val requestId: String,
    val metadata: AssetPreparationMetadata,
    val requestDigest: String,
) {
    init {
        require(serverScope.matches(Regex("[0-9a-f]{64}")))
        listOf(userId, clientId, workspaceId.value, requestId).forEach {
            require(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        }
        require(metadata.byteSize in 1..9_007_199_254_740_991L)
        require(metadata.mediaType == metadata.mediaType.trim().lowercase() && mediaKindForAssetMediaType(metadata.mediaType) != null)
        require(metadata.checksum.matches(Regex("sha256:[0-9a-f]{64}")))
        require(metadata.width == null || metadata.width > 0)
        require(metadata.height == null || metadata.height > 0)
        require(metadata.durationMs == null || metadata.durationMs in 0..9_007_199_254_740_991L)
        require(requestDigest == assetPreparationDigest(metadata))
    }
    fun requestJson(): String = PrepareUploadJson.encodeToString(AssetPreparationBody(requestId = requestId, metadata = metadata))
}

internal fun PreparedAssetUploadRequest.wireRequest() = AssetPreparationWireRequest(binding.serverScope,
    binding.userId, clientId, binding.workspaceId, requestId, source.uploadMetadata(), requestDigest)

internal fun preparePickedAssetWireRequest(serverScope: String, session: WorkspaceSession,
    source: AssetTransferSource, requestId: String): AssetPreparationWireRequest {
    require(session.canEditContent)
    validateAssetTransferSource(source)
    val metadata = AssetPreparationMetadata(source.byteSize, source.checksum.lowercase(), source.durationMs,
        source.height, source.mediaType.trim().lowercase(), source.width)
    return AssetPreparationWireRequest(serverScope, session.userId, session.clientId, session.workspace.id,
        requestId, metadata, assetPreparationDigest(metadata))
}

internal class PickedAssetOriginalChangedException : IllegalArgumentException("Picked original differs from reservation")

/** Ordinary picker reservation; unlike local-document sync it grants no review or
 * projection authority. Persist every successor before its corresponding HTTP write.
 * File handles and signed directives intentionally cannot be serialized here. */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable internal data class PickedAssetUploadPreparation(
    val request: AssetPreparationWireRequest,
    val remoteIdentity: MaterializationSourceDescriptor? = null,
    val ticketRequestIds: List<String> = emptyList(),
    val leases: List<PreparedAssetUploadLease> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val prepareAttempted: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val uploadAttemptLeaseIds: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val completionAttemptLeaseIds: List<String> = emptyList(),
) {
    init {
        require(ticketRequestIds.size <= 4096 && ticketRequestIds.distinct().size == ticketRequestIds.size)
        ticketRequestIds.forEach {
            require(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        }
        require(ticketRequestIds.isEmpty() || remoteIdentity != null)
        require(leases.size <= ticketRequestIds.size)
        require(leases.map { it.leaseId }.distinct().size == leases.size)
        require(leases.map { it.ticketRequestId }.distinct().size == leases.size)
        require(leases.all { it.ticketRequestId in ticketRequestIds })
        require(leases.zipWithNext().all { (before, after) -> after.generation > before.generation })
        require(leases.zipWithNext().all { (before, after) ->
            ticketRequestIds.indexOf(after.ticketRequestId) > ticketRequestIds.indexOf(before.ticketRequestId)
        })
        listOf(uploadAttemptLeaseIds, completionAttemptLeaseIds).forEach { attempts ->
            require(attempts.size <= leases.size && attempts.distinct().size == attempts.size)
            require(attempts.all { id -> leases.any { it.leaseId == id } })
            require(attempts.zipWithNext().all { (before, after) ->
                leases.indexOfFirst { it.leaseId == after } > leases.indexOfFirst { it.leaseId == before }
            })
        }
        remoteIdentity?.let {
            require(it.workspaceId == request.workspaceId && it.ownerId == request.userId)
            require(it.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            require(it.uploadMetadata() == request.metadata)
        }
    }

    fun requireReselectedSource(source: AssetTransferSource) {
        validateAssetTransferSource(source)
        if (AssetPreparationMetadata(source.byteSize, source.checksum.lowercase(), source.durationMs,
            source.height, source.mediaType.trim().lowercase(), source.width) != request.metadata) {
            throw PickedAssetOriginalChangedException()
        }
    }

    fun bindIdentity(identity: MaterializationSourceDescriptor): PickedAssetUploadPreparation {
        require(remoteIdentity == null || remoteIdentity == identity) { "Prepared identity changed" }
        return copy(remoteIdentity = identity)
    }

    fun reserveTicket(ticketRequestId: String): PickedAssetUploadPreparation {
        requireNotNull(remoteIdentity)
        if (ticketRequestId in ticketRequestIds) {
            require(ticketRequestIds.last() == ticketRequestId) { "Cannot reactivate a superseded ticket" }
            return this
        }
        return copy(ticketRequestIds = ticketRequestIds + ticketRequestId)
    }

    fun bindLease(lease: PreparedAssetUploadLease): PickedAssetUploadPreparation {
        require(lease.ticketRequestId == ticketRequestIds.lastOrNull())
        leases.singleOrNull { it.ticketRequestId == lease.ticketRequestId }?.let {
            require(it == lease) { "Lease identity changed" }
            return this
        }
        require(leases.none { it.leaseId == lease.leaseId || it.generation >= lease.generation })
        return copy(leases = leases + lease)
    }

    fun requireSuccessorOf(previous: PickedAssetUploadPreparation) {
        require(request == previous.request)
        require(previous.remoteIdentity == null || remoteIdentity == previous.remoteIdentity)
        require(ticketRequestIds.take(previous.ticketRequestIds.size) == previous.ticketRequestIds)
        require(leases.take(previous.leases.size) == previous.leases)
        require(ticketRequestIds.size <= previous.ticketRequestIds.size + 1 && leases.size <= previous.leases.size + 1)
        require(!previous.prepareAttempted || prepareAttempted)
        listOf(previous.uploadAttemptLeaseIds to uploadAttemptLeaseIds,
            previous.completionAttemptLeaseIds to completionAttemptLeaseIds).forEach { (before, after) ->
            require(after.take(before.size) == before && after.size <= before.size + 1)
        }
    }

    /** Write-ahead intent only. It does not prove dispatch, successful PUT or completion. */
    fun markUploadAttempt(lease: PreparedAssetUploadLease): PickedAssetUploadPreparation {
        require(lease in leases && lease.ticketRequestId == ticketRequestIds.lastOrNull())
        return if (lease.leaseId in uploadAttemptLeaseIds) this
            else copy(uploadAttemptLeaseIds = uploadAttemptLeaseIds + lease.leaseId)
    }
    fun markCompletionAttempt(lease: PreparedAssetUploadLease): PickedAssetUploadPreparation {
        require(lease in leases && lease.ticketRequestId == ticketRequestIds.lastOrNull())
        return if (lease.leaseId in completionAttemptLeaseIds) this
            else copy(completionAttemptLeaseIds = completionAttemptLeaseIds + lease.leaseId)
    }
}

@Serializable private data class AssetPreparationBody(
    val schema: String = "boarderless.asset-prepare.v1",
    val requestId: String,
    val metadata: AssetPreparationMetadata,
)

/** Compare immutable reservation identity only. Display measurements belong to the
 * verified Ready descriptor, not the prepare digest. Never use this as a Ready/ACL proof. */
internal fun MaterializationSourceDescriptor.hasSamePreparedOriginal(other: MaterializationSourceDescriptor): Boolean =
    copy(width = other.width, height = other.height, durationMs = other.durationMs) == other

/** Must be durably reserved before any prepare HTTP; it is not consent or a server receipt.
 * Local source identity/path/owner are deliberately absent from the provider body. */
@Serializable data class PreparedAssetUploadRequest(
    val requestId: String,
    val binding: LocalWorkspaceSyncBinding,
    val userId: String,
    val clientId: String,
    val workspaceId: WorkspaceId,
    val reviewId: String,
    val source: MaterializationSourceDescriptor,
    val requestDigest: String,
) {
    init {
        require(requestId.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
        require(source.workspaceId == workspaceId && source.ownerId == userId)
        require(source.durationMs == null || source.durationMs in 0..9_007_199_254_740_991L)
        listOf(userId, clientId, workspaceId.value, reviewId).forEach {
            require(it.isNotBlank() && it.length <= 256); it.encodeToByteArray(throwOnInvalidSequence = true)
        }
        require(requestDigest == preparationDigest(source))
    }
    fun requestJson(): String = PrepareUploadJson.encodeToString(AssetPreparationBody(requestId = requestId, metadata = source.uploadMetadata()))

    internal fun requireReview(snapshot: LocalWorkspaceSyncSnapshot) {
        require(snapshot.local.canEditContent && snapshot.local.userId == userId && snapshot.local.clientId == clientId && snapshot.local.workspace.id == workspaceId)
        require(snapshot.checkpoint?.binding == binding)
        val (target, _) = snapshot.materializationReview()
        require(reviewId == (snapshot.rebaseProposal?.id ?: snapshot.proposal?.id))
        target.requireMaterializationReference(source)
        // Upload only an original already referenced by this private local document.
        snapshot.local.workspace.requireMaterializationReference(source)
    }
}

internal fun prepareLocalAssetUploadRequest(snapshot: LocalWorkspaceSyncSnapshot, source: WorkspaceAsset,
    requestId: String): PreparedAssetUploadRequest {
    val descriptor = MaterializationSourceDescriptor.fromAsset(source)
    return PreparedAssetUploadRequest(requestId, requireNotNull(snapshot.checkpoint).binding, snapshot.local.userId,
        snapshot.local.clientId, snapshot.local.workspace.id, snapshot.rebaseProposal?.id ?: requireNotNull(snapshot.proposal).id,
        descriptor, preparationDigest(descriptor)).also { it.requireReview(snapshot) }
}

/** Identity receipt only. No signed ticket, status/Ready marker, authorization or Node rewrite. */
@Serializable data class LocalAssetUploadPreparation(
    val request: PreparedAssetUploadRequest,
    val remoteIdentity: MaterializationSourceDescriptor? = null,
    val ticketRequestIds: List<String> = emptyList(),
    val leases: List<PreparedAssetUploadLease> = emptyList(),
) {
    init {
        require(ticketRequestIds.size <= 4096 && ticketRequestIds.distinct().size == ticketRequestIds.size)
        ticketRequestIds.forEach { require(it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))) }
        require(ticketRequestIds.isEmpty() || remoteIdentity != null)
        require(leases.size <= ticketRequestIds.size && leases.map { it.leaseId }.distinct().size == leases.size)
        require(leases.map { it.ticketRequestId }.distinct().size == leases.size)
        require(leases.all { it.ticketRequestId in ticketRequestIds })
        remoteIdentity?.let {
            require(it.workspaceId == request.binding.workspaceId && it.ownerId == request.binding.userId)
            require(it.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            // Preserve the known receipt even on a namespace collision; graph projection
            // must then stop. A collision is not permission to prepare another asset.
            require(it.uploadMetadata() == request.source.uploadMetadata())
        }
    }

    /** Call and persist before issuing a new ticket. Existing tickets remain auditable. */
    fun reserveTicket(ticketRequestId: String): LocalAssetUploadPreparation {
        requireNotNull(remoteIdentity)
        if (ticketRequestId in ticketRequestIds) {
            require(ticketRequestIds.last() == ticketRequestId) { "Cannot reactivate a superseded ticket" }
            return this
        }
        return copy(ticketRequestIds = ticketRequestIds + ticketRequestId)
    }

    fun bindLease(lease: PreparedAssetUploadLease): LocalAssetUploadPreparation {
        require(lease.ticketRequestId == ticketRequestIds.lastOrNull())
        leases.singleOrNull { it.ticketRequestId == lease.ticketRequestId }?.let {
            require(it == lease) { "Lease identity changed" }
            return this
        }
        require(leases.none { it.leaseId == lease.leaseId || it.generation >= lease.generation })
        return copy(leases = leases + lease)
    }

    internal fun requireSuccessorOf(previous: LocalAssetUploadPreparation) {
        require(request == previous.request)
        require(previous.remoteIdentity == null || remoteIdentity == previous.remoteIdentity)
        require(ticketRequestIds.take(previous.ticketRequestIds.size) == previous.ticketRequestIds)
        require(leases.take(previous.leases.size) == previous.leases)
        require(ticketRequestIds.size <= previous.ticketRequestIds.size + 1 && leases.size <= previous.leases.size + 1)
    }

    fun bindReceipt(requestId: String, requestDigest: String, asset: WorkspaceAsset): LocalAssetUploadPreparation {
        require(requestId == request.requestId && requestDigest == request.requestDigest)
        require(asset.status == AssetStatus.Pending || asset.status == AssetStatus.Ready)
        require(asset.rejectionReason == null)
        val identity = MaterializationSourceDescriptor(asset.id, asset.workspaceId, asset.ownerId, asset.mediaType,
            asset.byteSize, asset.checksum, asset.width, asset.height, asset.durationMs, asset.createdAt)
        require(remoteIdentity == null || remoteIdentity == identity) { "Prepared upload identity cannot change" }
        return copy(remoteIdentity = identity)
    }
}

/** Public lease identity only, never a signed directive or cached authorization. */
@Serializable data class PreparedAssetUploadLease(
    val leaseId: String,
    val ticketRequestId: String,
    val generation: Long,
    val expiresAt: String,
) {
    init {
        val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        require(leaseId.matches(uuid) && ticketRequestId.matches(uuid))
        require(generation > 0 && expiresAt.isNotBlank() && expiresAt.length <= 256)
    }
}
