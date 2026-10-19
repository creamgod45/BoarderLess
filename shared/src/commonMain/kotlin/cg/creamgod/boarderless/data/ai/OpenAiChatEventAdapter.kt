package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import cg.creamgod.boarderless.domain.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** One decoder per request. Input is a complete SSE data payload, NOT arbitrary network chunks.
 * Text-only, single-choice Chat Completions dialect; not Responses or Anthropic.
 * No proposal parsing/execution, credentials, persistence, logging or automatic retry.
 */
internal class OpenAiChatEventDecoder {
    private var terminal = false
    private var stopped = false
    private var id: String? = null
    private var events = 0
    private var totalBytes = 0L
    private var textBytes = 0L
    val isTerminal: Boolean get() = terminal

    fun accept(data: String): List<AiCoworkEvent> {
        if (terminal) return emptyList()
        return try {
            val size = data.encodeToByteArray().size
            require(size in 1..262144 && ++events <= 8192)
            totalBytes += size
            require(totalBytes <= 4L * 1024 * 1024)
            if (data == "[DONE]") {
                require(stopped)
                terminal = true
                return listOf(AiCoworkEvent.Completed)
            }
            requireBoundedDraftJsonDepth(data)
            val chunk = Json.parseToJsonElement(data).jsonObject
            if (chunk["error"] != null) return fail("AI provider returned an error", false)
            require(chunk.string("object") == "chat.completion.chunk")
            val nextId = requireNotNull(chunk.string("id"))
            require(nextId.length in 1..256 && (id == null || id == nextId))
            id = nextId
            val choices = chunk.getValue("choices").jsonArray
            if (choices.isEmpty()) {
                require(stopped && chunk["usage"] is JsonObject)
                return emptyList()
            }
            require(!stopped && choices.size == 1)
            val choice = choices.single().jsonObject
            val index = choice.getValue("index").jsonPrimitive
            require(!index.isString && index.int == 0)
            val delta = choice.getValue("delta").jsonObject
            require(delta.string("role") in listOf(null, "assistant"))
            if (delta.string("refusal") != null || delta["function_call"].present() ||
                delta["tool_calls"].let { it.present() && it != JsonArray(emptyList()) }
            ) {
                return fail("AI response requires an unsupported review mode", false)
            }
            val reason = choice.string("finish_reason")
            if (reason != null && reason != "stop") return fail("AI response did not finish normally", false)
            val text = delta.string("content")
            if (text != null) {
                textBytes += text.encodeToByteArray().size
                require(textBytes <= 1024 * 1024)
            }
            if (reason == "stop") stopped = true
            if (text.isNullOrEmpty()) emptyList() else listOf(AiCoworkEvent.TextDelta(text))
        } catch (_: Exception) {
            fail("AI stream format or size is invalid", false) // Never surface raw provider JSON/errors.
        }
    }

    fun finish(): List<AiCoworkEvent> = if (terminal) emptyList() else fail("AI stream ended before completion", true)

    fun transportFailed(retryable: Boolean = true): List<AiCoworkEvent> =
        if (terminal) emptyList() else fail("AI stream connection failed", retryable)

    private fun fail(
        message: String,
        retryable: Boolean,
    ): List<AiCoworkEvent> {
        terminal = true
        return listOf(AiCoworkEvent.Failed(message, retryable))
    }

    private fun JsonElement?.present() = this != null && this != JsonNull

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        val primitive = value.jsonPrimitive
        require(primitive.isString)
        return primitive.content
    }
}

/** Inject a separately configured/authenticated SSE transport; no invented APP gateway endpoint.
 * Upstream collection stops at a terminal outcome; coroutine cancellation remains cancellation.
 */
internal class OpenAiChatEventAdapter(
    override val providerId: String,
    private val dataEvents: (AiCoworkRequest) -> Flow<String>,
) : AiCoworkProvider {
    init {
        require(providerId.isNotBlank())
    }

    override fun stream(request: AiCoworkRequest): Flow<AiCoworkEvent> =
        flow {
            val decoder = OpenAiChatEventDecoder()
            emitAll(
                flow { emitAll(dataEvents(request)) }
                    .transformWhile { data ->
                        decoder.accept(data).forEach { emit(it) }
                        !decoder.isTerminal
                    }.catch { failure ->
                        if (failure is CancellationException) throw failure
                        decoder.transportFailed(aiTransportFailureIsRetryable(failure)).forEach { emit(it) }
                    },
            )
            decoder.finish().forEach { emit(it) }
        }
}
