package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.domain.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*

internal enum class AiServerLocation { Remote, Local }

/** Complete user-configured endpoint. No path joining, default model, key or cloud fallback. */
internal data class AiServerProfile(
    val endpoint: String,
    val location: AiServerLocation,
    val body: AiRequestBodyProfile,
    val anthropicVersion: String? = null,
    val allowInsecureLocalHttp: Boolean = false,
)

/** Exact immutable snapshot and body to be reviewed before retrieving credentials or dispatch. */
internal data class AiRequestReview(val server: AiServerProfile, val request: AiCoworkRequest, val body: String)

/** Dedicated client ownership transfers to this provider. Credential headers are obtained only
 * after per-collection approval; callers must use an approved gateway/secret store, not shared prefs.
 * Client/plugin trust requirements from AiSseHttpTransport still apply. No UI is auto-approved.
 */
internal class ConfiguredAiProvider(
    override val providerId: String,
    private val server: AiServerProfile,
    private val client: HttpClient,
    private val approve: suspend (AiRequestReview) -> Boolean,
    private val credentialHeaders: suspend () -> Headers,
) : AiCoworkProvider {
    private val endpoint: Url = try {
        if (providerId.isBlank() || providerId.length > 256 || server.endpoint.length > 4096 ||
            !server.endpoint.contains("://") || server.endpoint.any { it.isWhitespace() || it.code < 32 })
            throw AiRequestValidationException()
        val parsed = Url(server.endpoint)
        if (parsed.host.isBlank() || parsed.user != null || parsed.password != null ||
            parsed.parameters.entries().isNotEmpty() || parsed.fragment.isNotEmpty() ||
            '?' in server.endpoint || '#' in server.endpoint ||
            (parsed.protocol != URLProtocol.HTTPS && !(parsed.protocol == URLProtocol.HTTP &&
                server.location == AiServerLocation.Local && server.allowInsecureLocalHttp)))
            throw AiRequestValidationException()
        if (server.body.dialect == AiRequestDialect.AnthropicMessages &&
            server.anthropicVersion?.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) != true)
            throw AiRequestValidationException()
        if (server.body.dialect == AiRequestDialect.OpenAiChat && server.anthropicVersion != null)
            throw AiRequestValidationException()
        parsed
    } catch (_: Exception) { throw AiRequestValidationException() }

    init { requireSafeAiStreamingClient(client) }

    override fun stream(request: AiCoworkRequest): Flow<AiCoworkEvent> = flow {
        // Copy collections before suspension so mutations cannot change the approved wire body.
        val snapshot = request.copy(context = request.context.copy(
            objects = request.context.objects.toList(), relations = request.context.relations.toList()))
        val prepared = try {
            val body = aiRequestBody(server.body, snapshot)
            if (!approve(AiRequestReview(server, snapshot, body))) {
                null
            } else {
                currentCoroutineContext().ensureActive()
                val credentials = credentialHeaders()
                validateCredentials(credentials)
                currentCoroutineContext().ensureActive()
                body to credentials
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            // Preflight/consent/secret failures must not disclose URLs, keys or prompt text.
            emit(AiCoworkEvent.Failed("AI request could not be prepared", false))
            return@flow
        }
        if (prepared == null) {
            emit(AiCoworkEvent.Failed("AI request was not approved", false))
            return@flow
        }
        val (body, credentials) = prepared
        val transport = AiSseHttpTransport(client) {
            url.takeFrom(endpoint)
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            credentials.entries().forEach { (name, values) -> values.forEach { headers.append(name, it) } }
            server.anthropicVersion?.let { headers.append("anthropic-version", it) }
            setBody(body)
        }
        val adapter: AiCoworkProvider = when (server.body.dialect) {
            AiRequestDialect.OpenAiChat -> OpenAiChatEventAdapter(providerId) { approved ->
                transport.events(approved).map { frame ->
                    if (frame.event != "message") throw AiSseFormatException()
                    frame.data
                }
            }
            AiRequestDialect.AnthropicMessages -> AnthropicEventAdapter(providerId, transport::events)
        }
        // No close here: each response is scoped; provider owns client across requests.
        emitAll(adapter.stream(snapshot))
    }

    fun close() = client.close()

    private fun validateCredentials(headers: Headers) {
        val allowed = setOf("authorization", "x-api-key")
        if (headers.entries().size > 2 || headers.entries().any { (name, values) ->
                name.lowercase() !in allowed || values.size != 1 || values.single().let {
                    it.isBlank() || it.length > 8192 || it.any { char -> char.code < 32 || char.code == 127 }
                }
            }) throw AiRequestValidationException()
    }
}
