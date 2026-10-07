package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import cg.creamgod.boarderless.domain.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

/** Complete named SSE event, not an arbitrary HTTP byte chunk. */
internal data class AiSseDataEvent(
    val event: String,
    val data: String,
)

/** Text-only Messages lifecycle; no tool execution, proposal, credential or automatic fallback. */
internal class AnthropicEventDecoder {
    private var terminal = false
    private var started = false
    private var messageDelta = false
    private var normalStop = false
    private var activeBlock: Int? = null
    private var nextBlock = 0
    private var count = 0
    private var bytes = 0L
    private var textBytes = 0L
    val isTerminal get() = terminal

    fun accept(frame: AiSseDataEvent): List<AiCoworkEvent> {
        if (terminal) return emptyList()
        return try {
            require(frame.event.length in 1..128)
            val size = frame.data.encodeToByteArray().size
            require(size in 1..262144 && ++count <= 8192)
            bytes += size
            require(bytes <= 4L * 1024 * 1024)
            requireBoundedDraftJsonDepth(frame.data)
            val data = Json.parseToJsonElement(frame.data).jsonObject
            require(data.string("type") == frame.event)
            when (frame.event) {
                "ping" -> {
                    emptyList()
                }

                "error" -> {
                    fail("AI provider returned an error", false)
                }

                "message_start" -> {
                    require(!started)
                    val message = data.getValue("message").jsonObject
                    require(message.string("type") == "message" && message.string("role") == "assistant")
                    require(requireNotNull(message.string("id")).length in 1..256)
                    require(message.getValue("content").jsonArray.isEmpty() && message.string("stop_reason") == null)
                    started = true
                    emptyList()
                }

                "content_block_start" -> {
                    require(started && !messageDelta && activeBlock == null && nextBlock < 64)
                    require(data.index() == nextBlock)
                    val block = data.getValue("content_block").jsonObject
                    if (block.string("type") != "text") return fail("AI response requires an unsupported review mode", false)
                    activeBlock = nextBlock++
                    text(requireNotNull(block.string("text")))
                }

                "content_block_delta" -> {
                    require(started && !messageDelta && activeBlock != null && data.index() == activeBlock)
                    val delta = data.getValue("delta").jsonObject
                    if (delta.string("type") != "text_delta") return fail("AI response requires an unsupported review mode", false)
                    text(requireNotNull(delta.string("text")))
                }

                "content_block_stop" -> {
                    require(started && !messageDelta && activeBlock != null && data.index() == activeBlock)
                    activeBlock = null
                    emptyList()
                }

                "message_delta" -> {
                    require(started && activeBlock == null && !normalStop)
                    messageDelta = true
                    val delta = data.getValue("delta").jsonObject
                    val reason = delta.string("stop_reason")
                    if (reason != null && reason !in listOf("end_turn", "stop_sequence")) {
                        return fail("AI response did not finish normally", false)
                    }
                    if (reason == "stop_sequence") require(requireNotNull(delta.string("stop_sequence")).length in 1..1024)
                    normalStop = reason != null
                    emptyList()
                }

                "message_stop" -> {
                    require(started && activeBlock == null && messageDelta && normalStop)
                    terminal = true
                    listOf(AiCoworkEvent.Completed)
                }

                else -> {
                    emptyList()
                } // Forward-compatible top-level metadata: never changes lifecycle.
            }
        } catch (_: Exception) {
            fail("AI stream format or size is invalid", false)
        }
    }

    fun finish() = if (terminal) emptyList() else fail("AI stream ended before completion", true)

    fun transportFailed(retryable: Boolean = true) = if (terminal) emptyList() else fail("AI stream connection failed", retryable)

    private fun text(value: String): List<AiCoworkEvent> {
        textBytes += value.encodeToByteArray().size
        require(textBytes <= 1024 * 1024)
        return if (value.isEmpty()) emptyList() else listOf(AiCoworkEvent.TextDelta(value))
    }

    private fun fail(
        message: String,
        retryable: Boolean,
    ): List<AiCoworkEvent> {
        terminal = true
        return listOf(AiCoworkEvent.Failed(message, retryable))
    }

    private fun JsonObject.index(): Int {
        val value = getValue("index").jsonPrimitive
        require(!value.isString)
        return value.int.also { require(it >= 0) }
    }

    private fun JsonObject.string(key: String): String? {
        val value = this[key] ?: return null
        if (value == JsonNull) return null
        return value.jsonPrimitive.also { require(it.isString) }.content
    }
}

internal class AnthropicEventAdapter(
    override val providerId: String,
    private val dataEvents: (AiCoworkRequest) -> Flow<AiSseDataEvent>,
) : AiCoworkProvider {
    init {
        require(providerId.isNotBlank())
    }

    override fun stream(request: AiCoworkRequest): Flow<AiCoworkEvent> =
        flow {
            val decoder = AnthropicEventDecoder()
            emitAll(
                flow { emitAll(dataEvents(request)) }
                    .transformWhile { frame ->
                        decoder.accept(frame).forEach { emit(it) }
                        !decoder.isTerminal
                    }.catch { failure ->
                        if (failure is CancellationException) throw failure
                        decoder.transportFailed(aiTransportFailureIsRetryable(failure)).forEach { emit(it) }
                    },
            )
            decoder.finish().forEach { emit(it) }
        }
}
