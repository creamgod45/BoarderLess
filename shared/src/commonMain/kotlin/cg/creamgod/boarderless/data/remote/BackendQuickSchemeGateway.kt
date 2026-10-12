package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.feature.canvas.ClipboardPayload
import cg.creamgod.boarderless.feature.canvas.validateClipboardPayload
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.time.TimeSource

@Serializable internal data class CloudSchemeBinding(val assetId: String, val sourceWorkspaceId: String, val kind: String)
@Serializable internal data class CloudSchemeAvailability(val assetId: String, val sourceWorkspaceId: String, val kind: String, val status: String)
@Serializable internal data class CloudSchemeBody(
    val resourceVersion: Int = 2, val name: String, val schemaVersion: Int,
    val payload: JsonObject, val assetBindings: List<CloudSchemeBinding>,
) {
    fun validate() {
        require(resourceVersion == 2 && name.isNotBlank() && name == name.trim())
        name.encodeToByteArray(throwOnInvalidSequence = true)
        require(name.count { it.code !in 0xdc00..0xdfff } in 1..80)
        require(payload["version"] != null && payload["format"] != null)
        val selection = Json.decodeFromJsonElement(ClipboardPayload.serializer(), payload)
        require(schemaVersion == selection.version && validateClipboardPayload(selection) == null)
        val required = selection.media.flatMap {
            listOfNotNull("original:${it.assetId}", it.thumbnailAssetId?.let { id -> "thumbnail:$id" })
        }.toSet()
        val supplied = assetBindings.map { "${it.kind}:${it.assetId}" }
        require(supplied.distinct().size == supplied.size && supplied.toSet() == required)
        assetBindings.forEach { requireCloudUuid(it.assetId); requireCloudUuid(it.sourceWorkspaceId); require(it.kind in setOf("original", "thumbnail")) }
    }
}
@Serializable internal data class CloudSchemeResource(
    val id: String, val ownerId: String, val resourceVersion: Int, val name: String,
    val schemaVersion: Int, val payload: JsonObject, val assetBindings: List<CloudSchemeBinding>,
    val revision: Long, val createdAt: String, val updatedAt: String, val etag: String,
    val assetAvailability: List<CloudSchemeAvailability>,
) {
    fun validate(owner: String) {
        requireCloudUuid(id); require(ownerId == owner && revision in 1..9_007_199_254_740_991L)
        requireStrongSchemeEtag(etag)
        CloudSchemeBody(resourceVersion, name, schemaVersion, payload, assetBindings).validate()
        require(createdAt.isNotBlank() && updatedAt.isNotBlank())
        require(assetAvailability.map { Triple(it.assetId, it.sourceWorkspaceId, it.kind) }.toSet() ==
            assetBindings.map { Triple(it.assetId, it.sourceWorkspaceId, it.kind) }.toSet())
        require(assetAvailability.size == assetBindings.size && assetAvailability.all { it.status in setOf("available", "unavailable", "access_denied") })
    }
}
@Serializable internal data class CloudSchemePage(val items: List<CloudSchemeResource>, val nextCursor: String?, val collectionRevision: Long)
@Serializable internal data class CloudSchemeCapabilities(
    val resourceVersions: List<Int>, val selectionSchemaVersions: List<Int>, val limits: JsonObject,
    val idempotencyKeyTtlSeconds: Long, val cursorTtlSeconds: Long, val materializationEnabled: Boolean,
)
/** A fresh response observation, never reconstructed from the device wall clock. */
internal class CloudSchemeClockObservation(val capabilities: CloudSchemeCapabilities, val serverMillis: Long?) {
    private val received = TimeSource.Monotonic.markNow()
    fun requireFresh(): Long {
        require(received.elapsedNow().inWholeMilliseconds in 0..15_000) { "Cloud server clock observation expired" }
        return requireNotNull(serverMillis) { "Cloud server Date unavailable; recovery disabled" }
    }
}
@Serializable internal data class CloudSchemeMutation(
    val resourceId: String, val idempotencyKey: String, val kind: String,
    val expectedEtag: String? = null, val body: CloudSchemeBody? = null,
) {
    init {
        requireCloudUuid(resourceId); requireCloudUuid(idempotencyKey)
        require(kind in setOf("create", "update", "delete"))
        require((kind == "create") == (expectedEtag == null))
        expectedEtag?.let(::requireStrongSchemeEtag)
        require((kind == "delete") == (body == null)); body?.validate()
    }
}

/** Explicit HTTP adapter. Caller must durably bind mutation+endpoint+actor and obtain
 * approval before execute; no retry, cursor restart, insertion or local-library writes. */
internal class BackendQuickSchemeGateway(baseUrl: String, private val userId: String,
    private val client: HttpClient = createAssetHttpClient()) {
    private val collection = "${baseUrl.trimEnd('/')}/api/v1/users/me/quick-schemes"
    val serverScope = SHA256().digest("${baseUrl.trimEnd('/')}/api/v1".encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    val actorId: String get() = userId
    private val json = Json { encodeDefaults = true }
    init { requireCloudUuid(userId) }

    suspend fun capabilities(): CloudSchemeCapabilities = observeCapabilities().capabilities
    suspend fun observeCapabilities(): CloudSchemeClockObservation = withTimeout(15_000) { client.prepareGet("${collection.removeSuffix("/quick-schemes")}/quick-scheme-capabilities") {
        header("x-user-id", userId)
        header(HttpHeaders.CacheControl, "no-cache, no-store")
    }.execute { response ->
        response.checked()
        val capabilities = json.decodeFromJsonElement(CloudSchemeCapabilities.serializer(), response.boundedJson()).also {
            require(2 in it.resourceVersions && it.selectionSchemaVersions.isNotEmpty() &&
                it.selectionSchemaVersions.all { version -> version in 1..9 })
            require(it.idempotencyKeyTtlSeconds in 1..9_007_199_254_740_991L && it.cursorTtlSeconds in 1..9_007_199_254_740_991L)
        }
        val dates = response.headers.getAll(HttpHeaders.Date)
        val serverMillis = dates?.singleOrNull()?.let { date ->
            runCatching { date.fromHttpToGmtDate().takeIf { it.toHttpDate() == date }?.timestamp }.getOrNull()
        }?.takeIf { it > 0 && response.headers[HttpHeaders.Age] == null }
        CloudSchemeClockObservation(capabilities, serverMillis)
    }
    }

    suspend fun list(cursor: String? = null, limit: Int = 50): CloudSchemePage {
        require(limit in 1..100 && (cursor == null || cursor.length in 1..4096))
        return client.prepareGet(collection) {
            header("x-user-id", userId); parameter("limit", limit); cursor?.let { parameter("cursor", it) }
        }.execute { response ->
            response.checked()
            json.decodeFromJsonElement(CloudSchemePage.serializer(), response.boundedJson()).also { page ->
                require(page.items.size <= limit && page.items.map { it.id }.distinct().size == page.items.size)
                require(page.collectionRevision in 0..9_007_199_254_740_991L)
                require(page.nextCursor == null || page.nextCursor.length in 1..4096)
                page.items.forEach { it.validate(userId) }
            }
        }
    }
    suspend fun get(id: String): CloudSchemeResource {
        requireCloudUuid(id)
        return client.prepareGet("$collection/$id") { header("x-user-id", userId) }.execute { response ->
            response.checked(); resource(response).also { require(it.id == id) }
        }
    }
    suspend fun execute(mutation: CloudSchemeMutation, canContinue: () -> Boolean = { true },
        observation: CloudSchemeClockObservation? = null, beforeDispatch: () -> Unit = {}): CloudSchemeMutationResult {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(canContinue())
        val supported = observation?.capabilities ?: capabilities()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(canContinue())
        require(mutation.body == null || mutation.body.schemaVersion in supported.selectionSchemaVersions)
        val body = mutation.body?.let { json.encodeToString(CloudSchemeBody.serializer(), it) }
        val maximum = (supported.limits["maxRequestBytes"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
            ?: throw BackendContractException("Missing cloud request limit")
        require(maximum > 0)
        require(body == null || body.encodeToByteArray(throwOnInvalidSequence = true).size <= minOf(maximum, 1024 * 1024L))
        beforeDispatch()
        return client.prepareRequest("$collection/${mutation.resourceId}") {
            method = if (mutation.kind == "delete") HttpMethod.Delete else HttpMethod.Put
            header("x-user-id", userId); header("Idempotency-Key", mutation.idempotencyKey)
            if (mutation.kind == "create") header("If-None-Match", "*") else header("If-Match", mutation.expectedEtag!!)
            body?.let { contentType(ContentType.Application.Json); setBody(it) }
        }.execute { response ->
            response.checked()
            if (mutation.kind == "delete") { require(response.status.value == 204); CloudSchemeMutationResult(204, null) }
            else CloudSchemeMutationResult(response.status.value, resource(response).also { require(it.id == mutation.resourceId) })
        }
    }
    fun close() = client.close()
    private suspend fun resource(response: HttpResponse): CloudSchemeResource =
        json.decodeFromJsonElement(CloudSchemeResource.serializer(), response.boundedJson()).also {
            it.validate(userId); require(response.headers[HttpHeaders.ETag] == it.etag)
        }
    private fun HttpResponse.checked() {
        if (status.value !in 200..299) throw CloudSchemeHttpException(status.value)
    }
    private suspend fun HttpResponse.boundedJson(): JsonObject = withTimeout(15_000) {
        require(headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase() == "application/json")
        val channel = bodyAsChannel(); val bytes = ByteArray(4 * 1024 * 1024 + 1); var size = 0
        try {
            while (size < bytes.size) {
                val count = channel.readAvailable(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 1 until bytes.size)
            parseStrictJsonObject(bytes.copyOf(size).decodeToString(throwOnInvalidSequence = true))
        } finally { channel.cancel(null) }
    }
}
internal class CloudSchemeHttpException(val httpStatus: Int) : IllegalStateException("Cloud scheme HTTP $httpStatus")
internal data class CloudSchemeMutationResult(val httpStatus: Int, val resource: CloudSchemeResource?)
private fun requireCloudUuid(value: String) = require(value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
private fun requireStrongSchemeEtag(value: String) = require(value.length in 3..256 && value.startsWith('"') && value.endsWith('"') &&
    value.substring(1, value.lastIndex).all { it.code in 0x21..0x7e && it != '"' })
