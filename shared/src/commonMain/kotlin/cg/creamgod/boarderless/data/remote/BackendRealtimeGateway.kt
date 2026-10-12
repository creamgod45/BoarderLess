@file:OptIn(kotlin.time.ExperimentalTime::class)
package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.parseStrictJsonObject
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Instant
import kotlin.time.TimeSource

@Serializable internal data class BackendRealtimeCapabilities(
    val protocolVersions: List<Int>, val schemaVersions: List<Int>, val webSocketUrl: String,
    val subprotocol: String, val operationFanOut: Boolean, val presence: Boolean,
    val metadataRevision: Long, val maxMessageBytes: Int, val maxPendingTransactions: Int,
    val maxCatchUpOperations: Int, val maxOperationsPerTransaction: Int, val heartbeatIntervalMs: Long,
    val presenceTtlMs: Long, val ticketTtlSeconds: Long, val authTimeoutMs: Long,
    val maxSelectionIds: Int, val cursorUpdatesPerSecond: Int, val selectionUpdatesPerSecond: Int,
) {
    fun validate(apiBase: String) {
        require(1 in protocolVersions && 1 in schemaVersions && subprotocol == "boarderless.v1" && operationFanOut)
        require(metadataRevision in 0..RealtimeMaxSequence && maxMessageBytes in 1024..4 * 1024 * 1024)
        require(maxPendingTransactions in 1..10_000 && maxCatchUpOperations in 1..100_000 && maxOperationsPerTransaction in 1..200)
        require(heartbeatIntervalMs in 100..60_000 && presenceTtlMs in 1_000..120_000)
        require(ticketTtlSeconds in 2..600 && authTimeoutMs in 100..30_000 && maxSelectionIds in 1..128)
        require(cursorUpdatesPerSecond in 1..120 && selectionUpdatesPerSecond in 1..120)
        requireRealtimeEndpoint(apiBase, webSocketUrl)
    }
}
@Serializable private data class RealtimeTicketResponse(val ticket: String, val expiresAt: String,
    val webSocketUrl: String, val protocolVersion: Int)

/** Secret in memory only. No data-class toString/copy or persistence of the ticket. */
internal class PreparedRealtimeJoin internal constructor(val capabilities: BackendRealtimeCapabilities,
    private val join: JsonObject, private val started: TimeSource.Monotonic.ValueTimeMark) {
    private var consumed = false
    fun requireUnexpired() {
        require(started.elapsedNow().inWholeMilliseconds in 0 until capabilities.ticketTtlSeconds * 1000 - 1000) {
            "Realtime ticket preparation expired"
        }
    }
    fun takeJoin(): JsonObject {
        require(!consumed) { "Realtime ticket already consumed" }
        requireUnexpired(); consumed = true
        return join
    }
}

/** Explicit capabilities → one ticket. It never reconnects, submits operations, mutates
 * checkpoints or changes the selected identity. HTTP errors contain only safe status. */
internal class BackendRealtimeGateway(baseUrl: String, private val client: HttpClient =
    cg.creamgod.boarderless.data.platformHttpClient { expectSuccess = false; followRedirects = false }) {
    private val apiBase = "${baseUrl.trimEnd('/')}/api/v1"
    private val strict = Json { encodeDefaults = true }
    suspend fun prepare(workspaceId: String, userId: String, clientId: String, connectionEpoch: String,
        checkpoint: WorkspaceStateDto, metadataRevision: Long, canContinue: () -> Boolean): PreparedRealtimeJoin {
        listOf(workspaceId, userId, clientId, connectionEpoch).forEach(::requireRealtimeUuid)
        require(checkpoint.workspaceId == workspaceId && checkpoint.throughServerSeq in 0..RealtimeMaxSequence &&
            checkpoint.workspaceVersion in 0..RealtimeMaxSequence && metadataRevision in 0..RealtimeMaxSequence)
        val context = currentCoroutineContext()
        fun active() { context.ensureActive(); check(canContinue()) }
        active()
        val capabilities = withTimeout(15_000) {
            client.prepareGet("$apiBase/workspaces/$workspaceId/realtime-capabilities") {
                header("x-user-id", userId); header(HttpHeaders.CacheControl, "no-cache, no-store")
            }.execute { response ->
                strict.decodeFromJsonElement(BackendRealtimeCapabilities.serializer(), response.boundedObject())
                    .also { it.validate(apiBase) }
            }
        }
        active()
        // Starts before the ticket HTTP, conservatively including network preparation.
        val started = TimeSource.Monotonic.markNow()
        val ticket = withTimeout(minOf(15_000L, capabilities.ticketTtlSeconds * 1000 - 1000)) {
            client.preparePost("$apiBase/workspaces/$workspaceId/realtime-ticket") {
                header("x-user-id", userId); contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("protocolVersion", 1); put("clientId", clientId) }.toString())
            }.execute { response ->
                if (response.status.value != 201) throw BackendRealtimeHttpException(response.status.value)
                require(response.headers[HttpHeaders.CacheControl]?.split(',')?.any { it.trim().equals("no-store", true) } == true)
                strict.decodeFromJsonElement(RealtimeTicketResponse.serializer(), response.boundedObject()).also {
                    require(it.protocolVersion == 1 && it.webSocketUrl == capabilities.webSocketUrl)
                    require(it.ticket.matches(Regex("[A-Za-z0-9_-]{43}")))
                    val expiration = Instant.parse(it.expiresAt).toEpochMilliseconds()
                    response.headers[HttpHeaders.Date]?.let { date ->
                        val now = date.fromHttpToGmtDate().timestamp
                        require(expiration > now && expiration - now <= capabilities.ticketTtlSeconds * 1000 + 1000)
                    }
                }
            }
        }
        active()
        return PreparedRealtimeJoin(capabilities, buildJsonObject {
            put("type", "join_workspace"); put("protocolVersion", 1); put("schemaVersion", 1)
            put("workspaceId", workspaceId); put("connectionEpoch", connectionEpoch); put("clientId", clientId)
            put("lastSeenServerSeq", checkpoint.throughServerSeq); put("metadataRevision", metadataRevision); put("ticket", ticket.ticket)
        }, started).also { it.requireUnexpired() }
    }
    fun close() = client.close()
    private suspend fun HttpResponse.boundedObject(): JsonObject {
        if (status.value !in 200..299) throw BackendRealtimeHttpException(status.value)
        require(headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase() == "application/json")
        val channel = bodyAsChannel(); val bytes = ByteArray(16 * 1024 + 1); var size = 0
        try {
            while (size < bytes.size) {
                val count = channel.readAvailable(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            require(size in 1 until bytes.size)
            return parseStrictJsonObject(bytes.copyOf(size).decodeToString(throwOnInvalidSequence = true))
        } finally { channel.cancel(null) }
    }
}
internal class BackendRealtimeHttpException(val status: Int) : IllegalStateException("Realtime HTTP $status")
internal fun requireRealtimeEndpoint(apiBase: String, webSocketUrl: String) {
    val source = Url(apiBase); val target = Url(webSocketUrl)
    require(source.protocol.name in setOf("http", "https"))
    require(target.protocol.name == if (source.protocol.name == "https") "wss" else "ws")
    require(target.host == source.host && target.port == source.port && target.user == null && target.password == null)
    require(target.encodedPath == "${source.encodedPath.trimEnd('/')}/realtime" && target.encodedQuery.isEmpty() && target.fragment.isEmpty())
}
