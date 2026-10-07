package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.AssetDownloadGateway
import cg.creamgod.boarderless.data.AssetDownloadTicket
import cg.creamgod.boarderless.data.AssetImportCleanupTimeoutMillis
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.AssetTransferGateway
import cg.creamgod.boarderless.data.AssetTransferSource
import cg.creamgod.boarderless.data.AssetUploadTicket
import cg.creamgod.boarderless.data.DefaultAssetUploadChunkBytes
import cg.creamgod.boarderless.data.DirectAssetUploadSource
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.WorkspaceSession
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AssetTransferUnavailableException(
    message: String,
) : IllegalStateException(message)

/** HTTP/object-storage adapter for the BAI-003 v1 contract.
 *
 * Signed URLs and provider headers remain inside this adapter. They never enter MediaNode,
 * Workspace history, clipboard payloads, or preferences.
 */
class BackendAssetTransferGateway(
    baseUrl: String = defaultBackendBaseUrl(),
    private val client: HttpClient = createAssetHttpClient(),
    private val processingPollMillis: Long = 500,
    private val maximumProcessingPolls: Int = 120,
) : AssetTransferGateway,
    AssetDownloadGateway {
    private val apiBase = "${baseUrl.trimEnd('/')}/api/v1"

    init {
        require(processingPollMillis >= 0) { "Processing poll delay must not be negative" }
        require(maximumProcessingPolls > 0) { "Maximum processing polls must be positive" }
    }

    override suspend fun prepare(
        session: WorkspaceSession,
        source: AssetTransferSource,
    ): AssetUploadTicket {
        val response =
            client
                .post(assetCollectionUrl(session)) {
                    contentType(ContentType.Application.Json)
                    header(DevUserHeader, session.userId)
                    setBody(
                        PrepareAssetRequest(
                            mediaType = source.mediaType.trim().lowercase(),
                            byteSize = source.byteSize,
                            checksum = source.checksum,
                            width = source.width,
                            height = source.height,
                            durationMs = source.durationMs,
                        ),
                    )
                }.requireAssetSuccess()
                .body<PrepareAssetResponse>()
        val preparedAsset = response.asset.toDomain()
        val directive =
            response.upload ?: response.uploadUrl?.let { url ->
                AssetTransferDirective(method = "PUT", url = url)
            }
        if (directive == null) {
            // The current backend creates a pending row but returns uploadUrl: null. Attempt
            // bounded cleanup only when metadata validates as this source's pending preparation.
            cleanupPendingPreparation(session, source, preparedAsset)
            throw AssetTransferUnavailableException(
                "Backend created asset ${response.asset.id} without an upload directive",
            )
        }
        if (!directive.method.equals("PUT", ignoreCase = true)) {
            cleanupPendingPreparation(session, source, preparedAsset)
            throw AssetTransferUnavailableException("Unsupported signed upload method '${directive.method}'")
        }
        if (directive.url.isBlank()) {
            cleanupPendingPreparation(session, source, preparedAsset)
            throw AssetTransferUnavailableException("Backend returned a blank signed upload URL")
        }
        return AssetUploadTicket(
            asset = preparedAsset,
            uploadUrl = directive.url,
            requiredHeaders = directive.headers,
        )
    }

    override suspend fun upload(
        ticket: AssetUploadTicket,
        source: AssetTransferSource,
        onProgress: (Long) -> Unit,
    ) {
        if (source is DirectAssetUploadSource) {
            val headers =
                if (ticket.requiredHeaders.keys.any { it.equals("Content-Type", ignoreCase = true) }) {
                    ticket.requiredHeaders
                } else {
                    ticket.requiredHeaders + ("Content-Type" to source.mediaType)
                }
            source.uploadDirect(ticket.copy(requiredHeaders = headers), onProgress)
            return
        }
        val response =
            client.request(ticket.uploadUrl) {
                method = HttpMethod.Put
                ticket.requiredHeaders.forEach { (name, value) -> header(name, value) }
                if (ticket.requiredHeaders.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
                    contentType(ContentType.parse(source.mediaType))
                }
                setBody(
                    AssetSourceContent(
                        source = source,
                        contentTypeValue = ContentType.parse(source.mediaType),
                        onProgress = onProgress,
                    ),
                )
            }
        response.requireAssetSuccess()
    }

    override suspend fun confirm(
        session: WorkspaceSession,
        ticket: AssetUploadTicket,
    ): WorkspaceAsset =
        client
            .post("${assetUrl(session, ticket.asset.id)}/complete") {
                contentType(ContentType.Application.Json)
                header(DevUserHeader, session.userId)
                setBody(CompleteAssetRequest(ticket.asset.byteSize, ticket.asset.checksum))
            }.requireAssetSuccess()
            .body<AssetEnvelope>()
            .asset
            .toDomain()

    override suspend fun awaitReady(
        session: WorkspaceSession,
        assetId: String,
        onStatus: (AssetStatus) -> Unit,
    ): WorkspaceAsset {
        var latest: WorkspaceAsset? = null
        repeat(maximumProcessingPolls) { attempt ->
            val asset = getAssetDto(session, assetId).toDomain()
            latest = asset
            onStatus(asset.status)
            if (asset.status != AssetStatus.Pending) return asset
            if (attempt + 1 < maximumProcessingPolls && processingPollMillis > 0) delay(processingPollMillis)
        }
        return checkNotNull(latest)
    }

    override suspend fun thumbnailAssetId(
        session: WorkspaceSession,
        assetId: String,
    ): String? {
        val asset = getAssetDto(session, assetId).toDomain()
        check(asset.status == AssetStatus.Ready) { "Thumbnail source asset is not ready" }
        return asset.thumbnailAssetId?.takeUnless { it == asset.id }
    }

    override suspend fun abandon(
        session: WorkspaceSession,
        assetId: String,
    ) {
        client
            .delete(assetUrl(session, assetId)) {
                header(DevUserHeader, session.userId)
            }.requireAssetSuccess()
    }

    override suspend fun authorize(
        session: WorkspaceSession,
        assetId: String,
    ): AssetDownloadTicket {
        val response =
            client
                .get("${assetUrl(session, assetId)}/content") {
                    header(DevUserHeader, session.userId)
                }.requireAssetSuccess()
                .body<DownloadAssetResponse>()
        if (!response.download.method.equals("GET", ignoreCase = true)) {
            throw AssetTransferUnavailableException(
                "Unsupported signed download method '${response.download.method}'",
            )
        }
        return AssetDownloadTicket(
            asset = response.asset.toDomain(),
            downloadUrl = response.download.url,
            requiredHeaders = response.download.headers,
        )
    }

    override suspend fun download(
        ticket: AssetDownloadTicket,
        onChunk: suspend (ByteArray) -> Unit,
    ) {
        client
            .prepareRequest(ticket.downloadUrl) {
                method = HttpMethod.Get
                ticket.requiredHeaders.forEach { (name, value) -> header(name, value) }
            }.execute { response ->
                // execute keeps the response streaming; a regular request may save the whole body.
                response.requireAssetSuccess()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (!channel.isClosedForRead) {
                    val count = channel.readAvailable(buffer, 0, buffer.size)
                    when {
                        count < 0 -> break
                        count > 0 -> onChunk(buffer.copyOf(count))
                    }
                }
            }
    }

    override fun close() {
        client.close()
    }

    private suspend fun cleanupPendingPreparation(
        session: WorkspaceSession,
        source: AssetTransferSource,
        asset: WorkspaceAsset,
    ) {
        // A malformed/deduplicated ready response is not authority to delete another resource.
        if (asset.status != AssetStatus.Pending || asset.workspaceId != session.workspace.id ||
            asset.mediaType != source.mediaType.trim().lowercase() || asset.byteSize != source.byteSize ||
            asset.checksum != source.checksum
        ) {
            return
        }
        withContext(NonCancellable) {
            withTimeoutOrNull(AssetImportCleanupTimeoutMillis) { runCatching { abandon(session, asset.id) } }
        }
    }

    private suspend fun getAssetDto(
        session: WorkspaceSession,
        assetId: String,
    ): AssetDto {
        val dto =
            client
                .get(assetUrl(session, assetId)) {
                    header(DevUserHeader, session.userId)
                }.requireAssetSuccess()
                .body<AssetDto>()
        val asset = dto.toDomain()
        require(asset.id == assetId && asset.workspaceId == session.workspace.id) {
            "Asset metadata does not match the requested workspace resource"
        }
        return dto
    }

    private fun assetCollectionUrl(session: WorkspaceSession) = "$apiBase/workspaces/${session.workspace.id.value}/assets"

    private fun assetUrl(
        session: WorkspaceSession,
        assetId: String,
    ) = "${assetCollectionUrl(session)}/$assetId"

    private suspend fun HttpResponse.requireAssetSuccess(): HttpResponse {
        if (status.value in 200..299) return this
        throw BackendHttpException(status, bodyAsText())
    }

    private companion object {
        const val DevUserHeader = "x-user-id"
    }
}

private class AssetSourceContent(
    private val source: AssetTransferSource,
    private val contentTypeValue: ContentType,
    private val onProgress: (Long) -> Unit,
) : OutgoingContent.WriteChannelContent() {
    override val contentType: ContentType = contentTypeValue
    override val contentLength: Long = source.byteSize

    override suspend fun writeTo(channel: ByteWriteChannel) {
        var offset = 0L
        while (offset < source.byteSize) {
            val maximum = minOf(DefaultAssetUploadChunkBytes.toLong(), source.byteSize - offset).toInt()
            val bytes = source.readChunk(offset, maximum)
            if (bytes.isEmpty() || bytes.size > maximum) {
                throw IllegalStateException("Asset source ended before its declared byte size")
            }
            channel.writeFully(bytes)
            offset += bytes.size
            onProgress(offset)
        }
    }
}

@Serializable
private data class PrepareAssetRequest(
    val mediaType: String,
    val byteSize: Long,
    val checksum: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
)

@Serializable
private data class CompleteAssetRequest(
    val byteSize: Long,
    val checksum: String,
)

@Serializable
private data class AssetTransferDirective(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: String? = null,
)

@Serializable
private data class PrepareAssetResponse(
    val asset: AssetDto,
    val upload: AssetTransferDirective? = null,
    val uploadUrl: String? = null,
)

@Serializable
private data class AssetEnvelope(
    val asset: AssetDto,
)

@Serializable
private data class DownloadAssetResponse(
    val asset: AssetDto,
    val download: AssetTransferDirective,
)

private fun createAssetHttpClient(): HttpClient =
    cg.creamgod.boarderless.data.platformHttpClient {
        expectSuccess = false
        install(io.ktor.client.plugins.HttpTimeout) {
            requestTimeoutMillis = 600_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        install(ContentNegotiation) {
            json(
                Json {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
