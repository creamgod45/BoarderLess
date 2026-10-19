package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AnthropicEventAdapterTest {
    private val request =
        AiCoworkRequest(
            "request",
            "prompt",
            AiContextSnapshot("workspace", 1, AiContextScope.PromptOnly, emptyList(), emptyList()),
        )

    private fun event(
        type: String,
        fields: JsonObjectBuilder.() -> Unit = {},
    ) = AiSseDataEvent(
        type,
        buildJsonObject {
            put("type", type)
            fields()
        }.toString(),
    )

    private fun start() =
        event("message_start") {
            put(
                "message",
                buildJsonObject {
                    put("id", "message")
                    put("type", "message")
                    put("role", "assistant")
                    put("content", buildJsonArray {})
                },
            )
        }

    private fun block(
        index: Int = 0,
        text: String = "",
        kind: String = "text",
    ) = event("content_block_start") {
        put("index", index)
        put(
            "content_block",
            buildJsonObject {
                put("type", kind)
                put("text", text)
            },
        )
    }

    private fun delta(
        text: String,
        index: Int = 0,
    ) = event("content_block_delta") {
        put("index", index)
        put(
            "delta",
            buildJsonObject {
                put("type", "text_delta")
                put("text", text)
            },
        )
    }

    private fun blockStop(index: Int = 0) = event("content_block_stop") { put("index", index) }

    private fun messageDelta(reason: String? = "end_turn") =
        event("message_delta") {
            put(
                "delta",
                buildJsonObject {
                    put("stop_reason", reason?.let(::JsonPrimitive) ?: JsonNull)
                    if (reason == "stop_sequence") put("stop_sequence", "END")
                },
            )
        }

    @Test fun namedLifecycleMultipleTextBlocksPingAndUnknownMetadataCompleteOnce() =
        runTest {
            for (reason in listOf("end_turn", "stop_sequence")) {
                val adapter =
                    AnthropicEventAdapter("test") { seen ->
                        assertEquals(request, seen)
                        flowOf(
                            event("ping"),
                            start(),
                            block(text = "initial"),
                            delta("素材🙂"),
                            event("future_metadata"),
                            blockStop(),
                            block(1),
                            delta("tail", 1),
                            blockStop(1),
                            messageDelta(null),
                            messageDelta(reason),
                            event("message_stop"),
                        )
                    }
                assertEquals(
                    listOf(
                        AiCoworkEvent.TextDelta("initial"),
                        AiCoworkEvent.TextDelta("素材🙂"),
                        AiCoworkEvent.TextDelta("tail"),
                        AiCoworkEvent.Completed,
                    ),
                    adapter.stream(request).toList(),
                )
            }
        }

    @Test fun invalidLifecycleIndexesAndPrematureStopFailClosed() {
        val invalidStreams =
            listOf(
                listOf(delta("before start")),
                listOf(event("message_stop")),
                listOf(start(), start()),
                listOf(start(), block(1)),
                listOf(start(), block(), delta("wrong index", 1)),
                listOf(start(), block(), block()),
                listOf(start(), block(), messageDelta()),
                listOf(start(), messageDelta(null), event("message_stop")),
                listOf(start(), blockStop()),
            )
        invalidStreams.forEach { frames ->
            val decoder = AnthropicEventDecoder()
            assertIs<AiCoworkEvent.Failed>(frames.flatMap(decoder::accept).last())
            assertTrue(decoder.accept(event("message_stop")).isEmpty())
        }
    }

    @Test fun abnormalReasonsAndUnsupportedBlocksCannotProduceProposalOrSuccess() {
        for (reason in listOf("max_tokens", "tool_use", "refusal", "pause_turn", "model_context_window_exceeded")) {
            val decoder = AnthropicEventDecoder()
            decoder.accept(start())
            assertIs<AiCoworkEvent.Failed>(decoder.accept(messageDelta(reason)).single())
        }
        for (kind in listOf("tool_use", "thinking", "redacted_thinking", "fallback", "future_block")) {
            val decoder = AnthropicEventDecoder()
            decoder.accept(start())
            assertIs<AiCoworkEvent.Failed>(decoder.accept(block(kind = kind)).single())
        }
    }

    @Test fun eventNameMismatchMalformedErrorAndBoundsNeverLeakProviderContent() {
        val bad =
            listOf(
                AiSseDataEvent("message_start", start().data.replace("message_start", "ping")),
                AiSseDataEvent("ping", "secret-key-json"),
                AiSseDataEvent("ping", "x".repeat(262145)),
                AiSseDataEvent("ping", "[".repeat(70) + "0" + "]".repeat(70)),
                event("error") { put("error", buildJsonObject { put("message", "secret-key") }) },
            )
        bad.forEach { frame ->
            val failed = assertIs<AiCoworkEvent.Failed>(AnthropicEventDecoder().accept(frame).single())
            assertFalse(failed.message.contains("secret-key"))
        }
        val decoder = AnthropicEventDecoder()
        decoder.accept(start())
        decoder.accept(block())
        repeat(5) { assertIs<AiCoworkEvent.TextDelta>(decoder.accept(delta("x".repeat(200000))).single()) }
        assertIs<AiCoworkEvent.Failed>(decoder.accept(delta("x".repeat(200000))).single())
    }

    @Test fun eofAfterNormalReasonIsFailureAndTerminalClosesTransport() =
        runTest {
            val incomplete = AnthropicEventAdapter("test") { flowOf(start(), messageDelta()) }
            assertEquals(listOf(AiCoworkEvent.Failed("AI stream ended before completion", true)), incomplete.stream(request).toList())
            var closed = false
            val adapter =
                AnthropicEventAdapter("test") {
                    flow {
                        try {
                            emit(start())
                            emit(messageDelta())
                            emit(event("message_stop"))
                            error("past terminal")
                        } finally {
                            closed = true
                        }
                    }
                }
            assertEquals(listOf<AiCoworkEvent>(AiCoworkEvent.Completed), adapter.stream(request).toList())
            assertTrue(closed)
            val failed = AnthropicEventAdapter("test") { flow { error("secret-key URL") } }
            assertEquals(listOf(AiCoworkEvent.Failed("AI stream connection failed", true)), failed.stream(request).toList())
        }

    @Test fun cancellationStaysCancellationAndConsumerExceptionIsTransparent() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var closed = false
            val seen = mutableListOf<AiCoworkEvent>()
            val adapter =
                AnthropicEventAdapter("test") {
                    flow {
                        try {
                            emit(start())
                            emit(block())
                            emit(delta("partial"))
                            started.complete(Unit)
                            awaitCancellation()
                        } finally {
                            closed = true
                        }
                    }
                }
            val job = launch { adapter.stream(request).collect { seen += it } }
            started.await()
            job.cancelAndJoin()
            assertTrue(closed)
            assertEquals(listOf<AiCoworkEvent>(AiCoworkEvent.TextDelta("partial")), seen)
            val failure = IllegalStateException("consumer")
            val immediate = AnthropicEventAdapter("test") { flowOf(start(), block(text = "first")) }
            assertSame(failure, assertFailsWith<IllegalStateException> { immediate.stream(request).collect { throw failure } })
        }

    @Test fun eventCountAggregateBytesBlockCountAndUnknownDeltaAreBounded() {
        val counted = AnthropicEventDecoder()
        repeat(8192) { assertTrue(counted.accept(event("ping")).isEmpty()) }
        assertIs<AiCoworkEvent.Failed>(counted.accept(event("ping")).single())
        val total = AnthropicEventDecoder()
        val largeMetadata = event("future_metadata") { put("padding", "x".repeat(220000)) }
        repeat(19) { assertTrue(total.accept(largeMetadata).isEmpty()) }
        assertIs<AiCoworkEvent.Failed>(total.accept(largeMetadata).single())
        val blocks = AnthropicEventDecoder()
        blocks.accept(start())
        repeat(64) { index ->
            assertTrue(blocks.accept(block(index)).isEmpty())
            assertTrue(blocks.accept(blockStop(index)).isEmpty())
        }
        assertIs<AiCoworkEvent.Failed>(blocks.accept(block(64)).single())
        val unknown = AnthropicEventDecoder()
        unknown.accept(start())
        unknown.accept(block())
        assertIs<AiCoworkEvent.Failed>(
            unknown
                .accept(
                    event("content_block_delta") {
                        put("index", 0)
                        put(
                            "delta",
                            buildJsonObject {
                                put("type", "future_delta")
                                put("text", "do not adopt")
                            },
                        )
                    },
                ).single(),
        )
    }
}
