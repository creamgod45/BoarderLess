package cg.creamgod.boarderless.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import cg.creamgod.boarderless.data.parseStrictJsonObject

internal class BackendRealtimeWriter internal constructor(private val write: suspend (JsonObject, suspend () -> Boolean) -> Unit) {
    suspend fun send(message: JsonObject, canWrite: suspend () -> Boolean) = write(message, canWrite)
}

/** Physical socket only, not a live-room claim. One reviewed same-origin endpoint and
 * fresh ticket per call; no reconnect, resend, logging of ticket/frame or unbounded queue.
 * consume must durably apply each complete transaction before returning its response. */
internal class BackendRealtimeSocket(private val apiBase: String,
    private val client: HttpClient = cg.creamgod.boarderless.data.platformHttpClient { followRedirects = false; install(WebSockets) }) {
    suspend fun connectPrepared(prepared: PreparedRealtimeJoin, canContinue: () -> Boolean,
        onWriter: (BackendRealtimeWriter?) -> Unit = {},
        consume: suspend (ByteArray) -> JsonObject?) {
        currentCoroutineContext().ensureActive(); check(canContinue())
        val join = prepared.takeJoin()
        connect(prepared.capabilities.webSocketUrl, join, prepared.capabilities.maxMessageBytes,
            canContinue, prepared::requireUnexpired, prepared.capabilities.heartbeatIntervalMs, onWriter, consume)
    }
    suspend fun connect(webSocketUrl: String, join: JsonObject, maximumFrameBytes: Int,
        canContinue: () -> Boolean, beforeJoin: () -> Unit = {}, heartbeatIntervalMs: Long = 15_000,
        onWriter: (BackendRealtimeWriter?) -> Unit = {},
        consume: suspend (ByteArray) -> JsonObject?) {
        require(maximumFrameBytes in 1024..4 * 1024 * 1024)
        require(heartbeatIntervalMs in 100..60_000)
        requireRealtimeEndpoint(apiBase, webSocketUrl)
        val context = currentCoroutineContext()
        fun active() { context.ensureActive(); check(canContinue()) }
        fun text(value: JsonObject): String = value.toString().also {
            require(it.encodeToByteArray(throwOnInvalidSequence = true).size in 1..maximumFrameBytes)
        }
        active()
        client.webSocket(urlString = webSocketUrl, request = {
            header("Sec-WebSocket-Protocol", "boarderless.v1")
        }) {
            active()
            require(call.response.headers["Sec-WebSocket-Protocol"] == "boarderless.v1")
            beforeJoin(); active()
            send(text(join)) // ticket belongs in this frame only, never URL or headers.
            coroutineScope {
                val writes = Mutex()
                var authenticated = false
                var sentHeartbeat = 0L
                var acknowledgedHeartbeat = 0L
                val writable = MutableStateFlow(true)
                val writer = BackendRealtimeWriter { message, canWrite ->
                    currentCoroutineContext().ensureActive()
                    writes.withLock {
                        active(); currentCoroutineContext().ensureActive()
                        require(writable.value && authenticated && canWrite())
                        active(); check(writable.value && authenticated)
                        require(message["workspaceId"] == join["workspaceId"] && message["connectionEpoch"] == join["connectionEpoch"] &&
                            message["protocolVersion"] == JsonPrimitive(1) &&
                            message["type"] in setOf(JsonPrimitive("presence_update"), JsonPrimitive("submit_operations")))
                        val encoded = text(message)
                        val stillAllowed = canWrite()
                        active(); check(writable.value && authenticated && stillAllowed); send(encoded)
                    }
                }
                val joinWatchdog = launch {
                    try {
                        withTimeout(30_000) {
                            while (!writes.withLock { authenticated }) { delay(50); active() }
                        }
                    } catch (_: TimeoutCancellationException) { error("Realtime join confirmation timed out") }
                }
                val heartbeat = launch {
                    while (true) {
                        delay(heartbeatIntervalMs); active()
                        writes.withLock {
                            if (authenticated) {
                                require(sentHeartbeat - acknowledgedHeartbeat < 2) { "Realtime heartbeat timed out" }
                                require(sentHeartbeat < RealtimeMaxSequence)
                                sentHeartbeat++
                                val message = buildJsonObject {
                                    put("type", "heartbeat"); put("protocolVersion", 1)
                                    put("workspaceId", requireNotNull(join["workspaceId"]))
                                    put("connectionEpoch", requireNotNull(join["connectionEpoch"]))
                                    put("heartbeatSeq", sentHeartbeat)
                                }
                                active(); send(text(message))
                            }
                        }
                    }
                }
                try {
                    onWriter(writer)
                    for (frame in incoming) {
                        active()
                        require(frame is Frame.Text && frame.fin && frame.data.size in 1..maximumFrameBytes)
                        // Strict UTF-8 and duplicate-key validation before liveness or UI.
                        val parsed = parseStrictJsonObject(frame.data.decodeToString(throwOnInvalidSequence = true))
                        if (parsed["type"] == JsonPrimitive("protocol_error") && parsed["workspaceId"] == JsonNull && parsed["connectionEpoch"] == JsonNull)
                            throw readRealtimeProtocolFailure(parsed) // Fixed error only; never authenticated liveness.
                        require(parsed["workspaceId"] == join["workspaceId"] && parsed["connectionEpoch"] == join["connectionEpoch"])
                        require(parsed["protocolVersion"] == JsonPrimitive(1))
                        val type = (parsed["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                            ?: error("Invalid realtime event type")
                        if (type == "heartbeat_ack") writes.withLock {
                            require(authenticated && parsed.keys == setOf("type", "protocolVersion", "workspaceId", "connectionEpoch", "heartbeatSeq"))
                            val sequence = (parsed["heartbeatSeq"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                                ?: error("Invalid heartbeat ACK")
                            require(sequence == acknowledgedHeartbeat + 1 && sequence <= sentHeartbeat)
                            acknowledgedHeartbeat = sequence
                        }
                        val response = consume(frame.data)
                        active()
                        writes.withLock {
                            if (type == "join_accepted") { require(!authenticated); authenticated = true }
                            response?.let { active(); send(text(it)) }
                        }
                    }
                } finally {
                    writable.value = false; onWriter(null)
                    joinWatchdog.cancelAndJoin(); heartbeat.cancelAndJoin()
                }
            }
        }
    }
    fun close() = client.close()
}
