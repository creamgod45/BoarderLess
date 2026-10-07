package cg.creamgod.boarderless.data.ai

import cg.creamgod.boarderless.data.requireBoundedDraftJsonDepth
import cg.creamgod.boarderless.domain.ai.AiDiagramToolCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

internal enum class AiDiagramStreamFailure { Format, Limit, Provider, Unsupported, Incomplete, EmptyResponse, Transport }

internal sealed interface AiDiagramStreamEvent {
    data class TextDelta(
        val text: String,
    ) : AiDiagramStreamEvent

    /** The only event exposing executable calls, after the entire normal terminal lifecycle. */
    data class Completed(
        val text: String,
        val calls: List<AiDiagramToolCall>,
    ) : AiDiagramStreamEvent

    data class Failed(
        val code: AiDiagramStreamFailure,
    ) : AiDiagramStreamEvent
}

private class InvalidToolStream(
    val code: AiDiagramStreamFailure,
) : IllegalArgumentException()

private fun streamCheck(
    valid: Boolean,
    code: AiDiagramStreamFailure = AiDiagramStreamFailure.Format,
) {
    if (!valid) throw InvalidToolStream(code)
}

private fun JsonObject.str(key: String): String? =
    this[key]?.takeUnless { it == JsonNull }?.let {
        val primitive = it as? JsonPrimitive ?: throw InvalidToolStream(AiDiagramStreamFailure.Format)
        streamCheck(primitive.isString)
        primitive.content
    }

private fun JsonObject.index(): Int {
    val value = getValue("index").jsonPrimitive
    streamCheck(!value.isString)
    return value.int.also { streamCheck(it >= 0) }
}

private fun safeToolId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,128}"))

/** Parse complete JSON objects, rejecting duplicate keys including escaped aliases.
 * No last-key-wins lifecycle or arguments ambiguity may reach either protocol/executor.
 */
internal fun parseAiToolObject(raw: String): JsonObject =
    cg.creamgod.boarderless.data
        .parseStrictJsonObject(raw)

/** One decoder per approved HTTP request. No HTTP, tool execution, retry or persistence. */
internal abstract class AiDiagramToolDecoder(
    private val requestId: String,
    private val sessionId: String,
) {
    protected class PendingCall(
        val id: String,
        val name: String,
    ) {
        val arguments = StringBuilder()
        var bytes = 0L
    }

    protected val calls = mutableListOf<PendingCall>()
    protected val text = StringBuilder()
    private var textBytes = 0L
    private var argumentBytes = 0L
    private var eventCount = 0
    private var wireBytes = 0L
    var isTerminal = false
        private set

    init {
        require(safeToolId(requestId) && safeToolId(sessionId))
    }

    protected fun parse(frame: AiSseDataEvent): JsonObject {
        streamCheck(frame.event.length in 1..128)
        budget(frame.data)
        return parseAiToolObject(frame.data)
    }

    protected fun budget(data: String) {
        val bytes = data.encodeToByteArray().size
        streamCheck(bytes in 1..262144 && ++eventCount <= 8192, AiDiagramStreamFailure.Limit)
        wireBytes += bytes
        streamCheck(wireBytes <= 4L * 1024 * 1024, AiDiagramStreamFailure.Limit)
    }

    protected fun addText(value: String): List<AiDiagramStreamEvent> {
        textBytes += value.encodeToByteArray().size
        streamCheck(textBytes <= 128 * 1024, AiDiagramStreamFailure.Limit)
        text.append(value)
        return if (value.isEmpty()) emptyList() else listOf(AiDiagramStreamEvent.TextDelta(value))
    }

    protected fun newCall(
        id: String,
        name: String,
    ): PendingCall {
        streamCheck(safeToolId(id) && safeToolId(name) && calls.none { it.id == id })
        streamCheck(calls.size < 64, AiDiagramStreamFailure.Limit)
        return PendingCall(id, name).also { calls.add(it) }
    }

    protected fun arguments(
        call: PendingCall,
        fragment: String,
    ) {
        val bytes = fragment.encodeToByteArray().size
        call.bytes += bytes
        argumentBytes += bytes
        streamCheck(call.bytes <= 64 * 1024 && argumentBytes <= 1024 * 1024, AiDiagramStreamFailure.Limit)
        call.arguments.append(fragment)
    }

    protected fun validateCall(call: PendingCall) {
        parseAiToolObject(call.arguments.toString())
    }

    protected fun complete(): List<AiDiagramStreamEvent> {
        streamCheck(calls.isNotEmpty() || text.isNotBlank(), AiDiagramStreamFailure.EmptyResponse)
        val result = text.toString()
        result.encodeToByteArray(throwOnInvalidSequence = true)
        calls.forEach(::validateCall)
        val completed =
            AiDiagramStreamEvent.Completed(
                result,
                calls.map {
                    AiDiagramToolCall(requestId, sessionId, it.id, it.name, it.arguments.toString())
                },
            )
        isTerminal = true
        clear()
        return listOf(completed)
    }

    protected fun guarded(block: () -> List<AiDiagramStreamEvent>): List<AiDiagramStreamEvent> {
        if (isTerminal) return emptyList()
        return try {
            block()
        } catch (
            failure: InvalidToolStream,
        ) {
            fail(failure.code)
        } catch (_: Exception) {
            fail(AiDiagramStreamFailure.Format)
        }
    }

    protected fun fail(code: AiDiagramStreamFailure): List<AiDiagramStreamEvent> {
        if (isTerminal) return emptyList()
        isTerminal = true
        clear()
        return listOf(AiDiagramStreamEvent.Failed(code))
    }

    private fun clear() {
        text.clear()
        calls.forEach { it.arguments.clear() }
        calls.clear()
    }

    abstract fun accept(frame: AiSseDataEvent): List<AiDiagramStreamEvent>

    fun finish() = fail(AiDiagramStreamFailure.Incomplete)

    fun transportFailed() = fail(AiDiagramStreamFailure.Transport)

    fun close() {
        isTerminal = true
        clear()
    }
}

/** Chat completion index/identity is stable; interleaved call fragments preserve call index.
 * Final finish_reason alone is insufficient: [DONE] is required before any calls are exposed.
 */
internal class OpenAiDiagramToolDecoder(
    requestId: String,
    sessionId: String,
) : AiDiagramToolDecoder(requestId, sessionId) {
    private var completionId: String? = null
    private var stopped = false

    override fun accept(frame: AiSseDataEvent): List<AiDiagramStreamEvent> =
        guarded {
            streamCheck(frame.event == "message", AiDiagramStreamFailure.Unsupported)
            if (frame.data == "[DONE]") {
                budget(frame.data)
                streamCheck(stopped)
                return@guarded complete()
            }
            val data = parse(frame)
            streamCheck(data["error"] == null, AiDiagramStreamFailure.Provider)
            streamCheck(data.str("object") == "chat.completion.chunk")
            val id = requireNotNull(data.str("id"))
            streamCheck(id.length in 1..256 && (completionId == null || completionId == id))
            completionId = id
            val choices = data.getValue("choices").jsonArray
            if (choices.isEmpty()) {
                streamCheck(stopped && data["usage"] is JsonObject)
                return@guarded emptyList()
            }
            streamCheck(!stopped && choices.size == 1)
            val choice = choices.single().jsonObject
            streamCheck(choice.index() == 0)
            val delta = choice.getValue("delta").jsonObject
            streamCheck(delta.str("role") in listOf(null, "assistant"))
            streamCheck(
                delta.str("refusal") == null && (delta["function_call"] == null || delta["function_call"] == JsonNull),
                AiDiagramStreamFailure.Unsupported,
            )
            streamCheck(
                listOf("audio", "reasoning_content").none { delta[it] != null && delta[it] != JsonNull },
                AiDiagramStreamFailure.Unsupported,
            )
            val events = delta.str("content")?.let(::addText) ?: emptyList()
            val chunkIndices = mutableSetOf<Int>()
            delta["tool_calls"]?.takeUnless { it == JsonNull }?.jsonArray?.forEach { element ->
                val item = element.jsonObject
                val index = item.index()
                streamCheck(index <= calls.size && index < 64 && chunkIndices.add(index))
                val function = item["function"]?.jsonObject
                if (index == calls.size) {
                    streamCheck(item.str("type") == "function")
                    newCall(requireNotNull(item.str("id")), requireNotNull(function?.str("name")))
                } else {
                    streamCheck(item.str("id") in listOf(null, calls[index].id) && item.str("type") in listOf(null, "function"))
                    streamCheck(function?.str("name") in listOf(null, calls[index].name))
                }
                function?.str("arguments")?.let { arguments(calls[index], it) }
            }
            choice.str("finish_reason")?.let { reason ->
                streamCheck(reason == if (calls.isEmpty()) "stop" else "tool_calls", AiDiagramStreamFailure.Incomplete)
                calls.forEach(::validateCall)
                stopped = true
            }
            events
        }
}

/** Messages lifecycle validates ordered closed blocks and normal stop before message_stop.
 * Standard tool_use only: thinking/server tools and mixed delta types fail explicitly.
 */
internal class ClaudeDiagramToolDecoder(
    requestId: String,
    sessionId: String,
) : AiDiagramToolDecoder(requestId, sessionId) {
    private var started = false
    private var nextIndex = 0
    private var activeIndex: Int? = null
    private var activeCall: PendingCall? = null
    private var messageDelta = false
    private var stopped = false

    override fun accept(frame: AiSseDataEvent): List<AiDiagramStreamEvent> =
        guarded {
            val data = parse(frame)
            streamCheck(data.str("type") == frame.event)
            when (frame.event) {
                "ping" -> {
                    emptyList()
                }

                "error" -> {
                    fail(AiDiagramStreamFailure.Provider)
                }

                "message_start" -> {
                    streamCheck(!started)
                    val message = data.getValue("message").jsonObject
                    streamCheck(message.str("type") == "message" && message.str("role") == "assistant")
                    streamCheck(
                        requireNotNull(message.str("id")).length in 1..256 && message.getValue("content").jsonArray.isEmpty() &&
                            message.str("stop_reason") == null,
                    )
                    started = true
                    emptyList()
                }

                "content_block_start" -> {
                    streamCheck(started && !messageDelta && activeIndex == null && data.index() == nextIndex)
                    streamCheck(nextIndex < 128, AiDiagramStreamFailure.Limit)
                    val block = data.getValue("content_block").jsonObject
                    activeIndex = nextIndex++
                    when (block.str("type")) {
                        "text" -> {
                            activeCall = null
                            addText(requireNotNull(block.str("text")))
                        }

                        "tool_use" -> {
                            streamCheck(block.getValue("input") == JsonObject(emptyMap()))
                            activeCall = newCall(requireNotNull(block.str("id")), requireNotNull(block.str("name")))
                            emptyList()
                        }

                        else -> {
                            fail(AiDiagramStreamFailure.Unsupported)
                        }
                    }
                }

                "content_block_delta" -> {
                    streamCheck(started && !messageDelta && activeIndex != null && data.index() == activeIndex)
                    val delta = data.getValue("delta").jsonObject
                    if (activeCall == null) {
                        streamCheck(delta.str("type") == "text_delta", AiDiagramStreamFailure.Unsupported)
                        addText(requireNotNull(delta.str("text")))
                    } else {
                        streamCheck(delta.str("type") == "input_json_delta", AiDiagramStreamFailure.Unsupported)
                        arguments(activeCall!!, requireNotNull(delta.str("partial_json")))
                        emptyList()
                    }
                }

                "content_block_stop" -> {
                    streamCheck(started && !messageDelta && activeIndex != null && data.index() == activeIndex)
                    activeCall?.let { call ->
                        // A zero-property tool may produce no deltas; initial input={} is then final.
                        if (call.arguments.isEmpty()) arguments(call, "{}")
                        validateCall(call)
                    }
                    activeIndex = null
                    activeCall = null
                    emptyList()
                }

                "message_delta" -> {
                    streamCheck(started && activeIndex == null && !stopped)
                    messageDelta = true
                    val delta = data.getValue("delta").jsonObject
                    delta.str("stop_reason")?.let { reason ->
                        streamCheck(
                            if (calls.isEmpty()) reason in listOf("end_turn", "stop_sequence") else reason == "tool_use",
                            AiDiagramStreamFailure.Incomplete,
                        )
                        if (reason == "stop_sequence") streamCheck(requireNotNull(delta.str("stop_sequence")).length in 1..1024)
                        stopped = true
                    }
                    emptyList()
                }

                "message_stop" -> {
                    streamCheck(started && activeIndex == null && messageDelta && stopped)
                    complete()
                }

                else -> {
                    fail(AiDiagramStreamFailure.Unsupported)
                }
            }
        }
}

/** Cancellation closes and clears the decoder; failed/EOF streams never expose partial calls. */
internal fun diagramToolEvents(
    dialect: AiRequestDialect,
    requestId: String,
    sessionId: String,
    frames: Flow<AiSseDataEvent>,
): Flow<AiDiagramStreamEvent> =
    flow {
        val decoder: AiDiagramToolDecoder =
            when (dialect) {
                AiRequestDialect.OpenAiChat -> OpenAiDiagramToolDecoder(requestId, sessionId)
                AiRequestDialect.AnthropicMessages -> ClaudeDiagramToolDecoder(requestId, sessionId)
            }
        try {
            emitAll(
                frames
                    .transformWhile { frame ->
                        decoder.accept(frame).forEach { emit(it) }
                        !decoder.isTerminal
                    }.catch { failure ->
                        if (failure is CancellationException) throw failure
                        decoder.transportFailed().forEach { emit(it) }
                    },
            )
            decoder.finish().forEach { emit(it) }
        } finally {
            decoder.close()
        }
    }
