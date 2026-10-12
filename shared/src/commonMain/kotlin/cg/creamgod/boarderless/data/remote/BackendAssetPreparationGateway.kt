package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import org.kotlincrypto.hash.sha2.SHA256

/** Explicit v2 operations only. No retry, fallback to v1, polling or ID allocation.
 * Callers must persist request/ticket identity and obtain consent before network writes.
 * Signed directives are ephemeral and must never enter the durable document. */
class BackendAssetPreparationGateway(
    baseUrl: String,
    private val client: HttpClient = createAssetHttpClient(),
) {
    private val protocolJson = Json { ignoreUnknownKeys = true }
    private val apiBase = "${baseUrl.trimEnd('/')}/api/v1"
    val serverScope = SHA256().digest(apiBase.encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    suspend fun capabilities(): AssetPreparationCapabilities =
        client.prepareGet("$apiBase/capabilities").execute {
            it.checked().readPreparationBody<CapabilitiesEnvelope>().assetPreparation
        }

    suspend fun prepare(request: PreparedAssetUploadRequest): AssetPreparationReceipt = prepare(request.wireRequest())

    internal suspend fun prepare(request: AssetPreparationWireRequest, beforeWrite: () -> Unit = {}): AssetPreparationReceipt {
        requireScope(request)
        capabilities().requireSupport(request)
        beforeWrite()
        return client.preparePost(collection(request)) {
            beforeWrite()
            contentType(ContentType.Application.Json)
            header("x-user-id", request.userId)
            // Already encoded with explicit nulls and canonical field types, unlike v1 JSON.
            setBody(request.requestJson())
        }.execute { it.checked().readPreparationBody<PreparationEnvelope>().receipt(request) }
    }

    /** 404 is not proof that a concurrent/late prepare cannot commit. Preserve requestId. */
    suspend fun lookup(request: PreparedAssetUploadRequest): AssetPreparationReceipt = lookup(request.wireRequest())

    internal suspend fun lookup(request: AssetPreparationWireRequest): AssetPreparationReceipt {
        requireScope(request)
        return client.prepareGet(identityUrl(request)) {
            header("x-user-id", request.userId)
        }.execute { it.checked().readPreparationBody<PreparationEnvelope>().receipt(request) }
    }

    suspend fun issueTicket(preparation: LocalAssetUploadPreparation, ticketRequestId: String): PreparedAssetUploadTicket =
        issueTicket(preparation.transportPreparation(), ticketRequestId)

    internal suspend fun issueTicket(preparation: PickedAssetUploadPreparation, ticketRequestId: String,
        beforeWrite: () -> Unit = {}): PreparedAssetUploadTicket {
        val request = preparation.request
        requireScope(request); requireUuid(ticketRequestId)
        requireNotNull(preparation.remoteIdentity) { "Persist prepare receipt before requesting a lease" }
        require(preparation.ticketRequestIds.lastOrNull() == ticketRequestId) { "Persist ticket identity before HTTP" }
        val envelope = client.preparePost("${identityUrl(request)}/upload-tickets") {
            beforeWrite()
            header("x-user-id", request.userId)
            contentType(ContentType.Application.Json)
            setBody(TicketRequest(ticketRequestId))
        }.execute { it.checked().readPreparationBody<PreparationEnvelope>() }
        val receipt = envelope.receipt(request)
        require(receipt.identity == preparation.remoteIdentity && receipt.phase == "prepared")
        val lease = requireNotNull(receipt.lease)
        val upload = requireNotNull(envelope.upload)
        require(lease.ticketRequestId == ticketRequestId && upload.leaseId == lease.leaseId)
        require(upload.method == "PUT" && upload.url.isNotBlank() && upload.expiresAt.isNotBlank())
        return PreparedAssetUploadTicket(receipt,
            AssetUploadTicket(envelope.asset.toDomain(), upload.url, upload.headers))
    }

    suspend fun complete(preparation: LocalAssetUploadPreparation, lease: PreparedAssetUploadLease): AssetPreparationReceipt =
        complete(preparation.transportPreparation(), lease)

    internal suspend fun complete(preparation: PickedAssetUploadPreparation, lease: PreparedAssetUploadLease,
        beforeWrite: () -> Unit = {}): AssetPreparationReceipt {
        val request = preparation.request
        requireScope(request)
        requireNotNull(preparation.remoteIdentity)
        require(lease in preparation.leases && lease.ticketRequestId == preparation.ticketRequestIds.lastOrNull())
        val receipt = client.preparePost("${identityUrl(request)}/complete") {
            beforeWrite()
            header("x-user-id", request.userId)
            contentType(ContentType.Application.Json)
            setBody(CompletionRequest(request.requestId, lease.leaseId, request.metadata.byteSize, request.metadata.checksum))
        }.execute { it.checked().readPreparationBody<PreparationEnvelope>().receipt(request) }
        require(receipt.identity == preparation.remoteIdentity && receipt.lease == lease)
        require(receipt.phase != "prepared")
        return receipt
    }

    fun close() = client.close()
    private fun requireScope(request: AssetPreparationWireRequest) {
        require(request.serverScope == serverScope) { "Upload destination changed" }
    }
    private fun collection(request: AssetPreparationWireRequest): String {
        requireUuid(request.workspaceId.value)
        return "$apiBase/workspaces/${request.workspaceId.value}/assets/preparations"
    }
    private fun identityUrl(request: AssetPreparationWireRequest) = "${collection(request)}/${request.requestId}"
    private fun PreparationEnvelope.receipt(request: AssetPreparationWireRequest): AssetPreparationReceipt {
        require(schema == "boarderless.asset-preparation.v1" && requestId == request.requestId && requestDigest == request.requestDigest)
        require(phase in setOf("prepared", "completionAccepted", "ready", "rejected", "missing", "abandoned"))
        val asset = asset.toDomain()
        if (declared != request.metadata || asset.mediaType != declared.mediaType ||
            asset.byteSize != declared.byteSize || asset.checksum != declared.checksum) {
            throw AssetPreparationMetadataChangedException()
        }
        val identity = MaterializationSourceDescriptor(asset.id, asset.workspaceId, asset.ownerId, asset.mediaType,
            asset.byteSize, asset.checksum, declared.width, declared.height, declared.durationMs, asset.createdAt)
        // Validate identity even for terminal rejection/missing receipts; status is not authorization.
        require(identity.workspaceId == request.workspaceId && identity.ownerId == request.userId)
        requireUuid(identity.id)
        require(when (phase) {
            "prepared", "completionAccepted" -> asset.status == AssetStatus.Pending
            "ready" -> asset.status == AssetStatus.Ready && asset.rejectionReason == null
            "rejected" -> asset.status == AssetStatus.Rejected
            "missing" -> asset.status == AssetStatus.Missing
            "abandoned" -> asset.status == AssetStatus.Pending
            else -> false
        }) { "Preparation phase and asset status disagree" }
        verified?.let {
            require(it.width > 0 && it.height > 0)
            require(it.durationMs == null || it.durationMs in 0..9_007_199_254_740_991L)
        }
        val readyIdentity = if (phase == "ready") {
            val measured = requireNotNull(verified) { "Ready preparation lacks verified metadata" }
            require(asset.width == measured.width && asset.height == measured.height && asset.durationMs == measured.durationMs)
            require(declared.width == null || declared.width == measured.width)
            require(declared.height == null || declared.height == measured.height)
            declared.durationMs?.let { expected ->
                val actual = requireNotNull(measured.durationMs)
                require(kotlin.math.abs(expected - actual).toDouble() <= maxOf(100.0, actual.toDouble() * 0.01))
            }
            MaterializationSourceDescriptor.fromAsset(asset)
        } else null
        require(phase == "prepared" || upload == null) { "Non-prepared receipt contains an upload directive" }
        return AssetPreparationReceipt(requestId, requestDigest, phase, identity, lease, readyIdentity, asset.rejectionReason)
    }
    private suspend fun HttpResponse.checked(): HttpResponse {
        if (status.value !in 200..299) {
            // Read only a bounded error envelope. Never expose message/details, arbitrary
            // provider text, signed URLs or headers. Classification does not trigger retry.
            val code = withTimeoutOrNull(2_000) {
                val channel = bodyAsChannel()
                val bytes = ByteArray(16 * 1024 + 1)
                var size = 0
                try {
                    while (size < bytes.size) {
                        val count = channel.readAvailable(bytes, size, bytes.size - size)
                        if (count < 0) break
                        size += count
                    }
                    if (size == bytes.size) null else {
                        val text = bytes.copyOf(size).decodeToString(throwOnInvalidSequence = true)
                        val token = Json.parseToJsonElement(text).jsonObject["error"]?.jsonObject
                            ?.get("code")?.jsonPrimitive?.contentOrNull
                        AssetPreparationFailureCode.entries.firstOrNull { it.token == token }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Throwable) { null }
                finally { channel.cancel(null) }
            }
            throw AssetPreparationHttpException(status.value, code)
        }
        return this
    }

    /** Small protocol response only, never an unrestricted saved body. Reject duplicate
     * keys and malformed Unicode before deserialization; a failed response changes no IDs. */
    private suspend inline fun <reified T> HttpResponse.readPreparationBody(): T = withTimeout(15_000) {
        require(headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase() == "application/json") {
            "Preparation response is not JSON"
        }
        val channel = bodyAsChannel()
        val bytes = ByteArray(64 * 1024 + 1)
        var size = 0
        try {
            while (size < bytes.size) {
                val count = channel.readAvailable(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 1..64 * 1024) { "Preparation response size is invalid" }
            val text = bytes.copyOf(size).decodeToString(throwOnInvalidSequence = true)
            parseStrictJsonObject(text)
            protocolJson.decodeFromString<T>(text)
        } finally { channel.cancel(null) }
    }
}

/** Transport-only adapter; local document review and durable binding remain owned
 * by LocalAssetUploadCoordinator, never inferred from this picker-shaped record. */
private fun LocalAssetUploadPreparation.transportPreparation() = PickedAssetUploadPreparation(
    request.wireRequest(), remoteIdentity, ticketRequestIds, leases,
)

/** No response body, URL, headers or credentials in user-facing exceptions. */
class AssetPreparationHttpException(val httpStatus: Int, val code: AssetPreparationFailureCode? = null) :
    IllegalStateException("Asset preparation HTTP $httpStatus${code?.let { " ${it.token}" } ?: ""}")

class AssetPreparationMetadataChangedException : IllegalStateException("Prepared asset metadata changed")

enum class AssetPreparationFailureCode(val token: String) {
    LeaseExpired("upload_lease_expired"), LeaseSuperseded("upload_lease_superseded"),
    LeaseMismatch("upload_lease_mismatch"), UploadMissing("upload_not_found"),
    PreparationMissing("preparation_not_found"), PreparationMismatch("preparation_mismatch"),
    CompletionMismatch("completion_mismatch"), AssetAbandoned("asset_abandoned"),
    UploadNotAllowed("upload_not_allowed"),
}

@Serializable data class AssetPreparationCapabilities(
    val requestSchema: String,
    val responseSchema: String,
    val maxByteSize: Long,
    val mediaTypes: List<String>,
) {
    fun requireSupport(request: PreparedAssetUploadRequest) {
        requireSupport(request.wireRequest())
    }
    internal fun requireSupport(request: AssetPreparationWireRequest) {
        require(requestSchema == "boarderless.asset-prepare.v1" && responseSchema == "boarderless.asset-preparation.v1")
        require(request.metadata.byteSize in 1..maxByteSize && request.metadata.mediaType in mediaTypes)
    }
}
data class AssetPreparationReceipt(
    val requestId: String, val requestDigest: String, val phase: String,
    val identity: MaterializationSourceDescriptor, val lease: PreparedAssetUploadLease?,
    val readyIdentity: MaterializationSourceDescriptor? = null,
    val rejectionReason: AssetRejectionReason? = null,
)
/** Intentionally not serializable: signed URLs must remain request-local. */
data class PreparedAssetUploadTicket(val receipt: AssetPreparationReceipt, val ticket: AssetUploadTicket)
private fun requireUuid(value: String) {
    require(value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
}
@Serializable private data class CapabilitiesEnvelope(val assetPreparation: AssetPreparationCapabilities)
@Serializable private data class TicketRequest(val ticketRequestId: String)
@Serializable private data class CompletionRequest(val requestId: String, val leaseId: String, val byteSize: Long, val checksum: String)
@Serializable private data class UploadDirective(val method: String, val url: String, val headers: Map<String, String>, val expiresAt: String, val leaseId: String)
@Serializable private data class PreparationEnvelope(
    val schema: String, val requestId: String, val requestDigest: String, val phase: String,
    val asset: AssetDto, val lease: PreparedAssetUploadLease?, val upload: UploadDirective? = null,
    val declared: AssetPreparationMetadata,
    val verified: VerifiedPreparationMetadata?,
)
@Serializable private data class VerifiedPreparationMetadata(val width: Int, val height: Int, val durationMs: Long?)
