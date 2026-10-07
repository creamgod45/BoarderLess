package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.domain.ai.AiCoworkRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

internal class AiSseHttpResponseException(val retryable: Boolean = false) : IllegalStateException("AI streaming response is invalid")

internal fun aiTransportFailureIsRetryable(failure: Throwable): Boolean = when (failure) {
    is AiSseHttpResponseException -> failure.retryable
    is AiSseFormatException -> false
    is AiRequestValidationException -> false
    else -> true
}

private data class AiSseDelivery(val event: AiSseDataEvent, val consumed: CompletableDeferred<Unit>)

internal fun requireSafeAiStreamingClient(client: HttpClient) {
    require(client.pluginOrNull(HttpRedirect) == null) { "AI streaming requires redirects disabled" }
    require(client.pluginOrNull(HttpRequestRetry) == null) { "AI streaming requires automatic retry disabled" }
    require(client.pluginOrNull(HttpTimeout) != null) { "AI streaming requires HttpTimeout" }
}

/** Owns a dedicated client. No endpoint, credential, serializer, retry or fallback is invented.
 * The caller supplies the approved request and must not install secret-bearing logging/plugins.
 * Each collection makes a NEW request; UI retries still require explicit user action.
 */
internal class AiSseHttpTransport(
    private val client: HttpClient = HttpClient {
        followRedirects = false
        expectSuccess = false
        install(HttpTimeout)
    },
    private val buildRequest: HttpRequestBuilder.(AiCoworkRequest) -> Unit,
) {
    init {
        requireSafeAiStreamingClient(client)
    }

    fun events(request: AiCoworkRequest): Flow<AiSseDataEvent> = flow {
        var consumerFailure: Throwable? = null
        try {
        channelFlow {
        currentCoroutineContext().ensureActive()
        client.prepareRequest {
            buildRequest(request)
            // Override the builder's timeout/Accept; no optional unlimited stream.
            headers.remove(HttpHeaders.Accept)
            headers.append(HttpHeaders.Accept, "text/event-stream")
            expectSuccess = false
            timeout {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }.execute { response ->
            val channel = response.bodyAsChannel()
            try {
                currentCoroutineContext().ensureActive()
                if (response.status.value != 200) throw AiSseHttpResponseException(
                    response.status.value == 429 || response.status.value in 500..599)
                val types = response.headers.getAll(HttpHeaders.ContentType)
                if (types?.size != 1) throw AiSseHttpResponseException()
                val type = try { ContentType.parse(types.single()) }
                catch (_: Exception) { throw AiSseHttpResponseException() }
                if (!type.contentType.equals("text", true) || !type.contentSubtype.equals("event-stream", true) ||
                    type.parameters.filter { it.name.equals("charset", true) }.let { charsets ->
                        charsets.size > 1 || charsets.any { !it.value.equals("utf-8", true) }
                    }) throw AiSseHttpResponseException()
                val lengths = response.headers.getAll(HttpHeaders.ContentLength)
                if (lengths != null && (lengths.size != 1 || lengths.single().let { raw ->
                        raw.isEmpty() || raw.any { it !in '0'..'9' } ||
                            raw.toLongOrNull()?.let { it > 8L * 1024 * 1024 } != false
                    })) throw AiSseHttpResponseException()
                channel.aiSseEvents().collect { event ->
                    // A rendezvous send alone is insufficient: it returns before the
                    // consumer processes a terminal. Explicit acknowledgement prevents
                    // parsing any next event until downstream emit has returned normally.
                    val delivery = AiSseDelivery(event, CompletableDeferred())
                    send(delivery)
                    delivery.consumed.await()
                }
            } finally {
                channel.cancel(null)
            }
        }
        }.buffer(Channel.RENDEZVOUS).collect { delivery ->
            try { emit(delivery.event) }
            catch (failure: Throwable) { consumerFailure = failure; throw failure }
            delivery.consumed.complete(Unit)
        }
        } catch (failure: Throwable) {
            // Coroutine stack-trace recovery can copy a consumer exception across the
            // producer boundary. Preserve the exact downstream failure after cleanup.
            throw consumerFailure ?: failure
        }
    }

    fun close() = client.close()
}
