package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiChatEventAdapterTest {
    private val request =
        AiCoworkRequest(
            "request",
            "prompt",
            AiContextSnapshot("workspace", 1, AiContextScope.PromptOnly, emptyList(), emptyList()),
        )

    private fun chunk(
        text: String? = null,
        reason: String? = null,
        extra: JsonObject = buildJsonObject {},
    ): String =
        buildJsonObject {
            put("id", "completion")
            put("object", "chat.completion.chunk")
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("index", 0)
                            put("delta", JsonObject(extra + if (text == null) emptyMap() else mapOf("content" to JsonPrimitive(text))))
                            put("finish_reason", reason?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                },
            )
        }.toString()

    @Test fun textStopUsageAndDoneProduceExactlyOneCompletion() =
        runTest {
            val adapter =
                OpenAiChatEventAdapter("local-openai-compatible") { seen ->
                    assertEquals(request, seen)
                    flowOf(
                        chunk(extra = buildJsonObject { put("role", "assistant") }),
                        chunk("素材🙂"),
                        chunk("ok", "stop"),
                        """{"id":"completion","object":"chat.completion.chunk","choices":[],"usage":{}}""",
                        "[DONE]",
                    )
                }
            assertEquals(
                listOf(AiCoworkEvent.TextDelta("素材🙂"), AiCoworkEvent.TextDelta("ok"), AiCoworkEvent.Completed),
                adapter.stream(request).toList(),
            )
        }

    @Test fun eofEvenAfterStopIsNotSuccessAndDoneCannotStartStream() {
        for (stopped in listOf(false, true)) {
            val decoder = OpenAiChatEventDecoder()
            decoder.accept(chunk("partial", if (stopped) "stop" else null))
            assertIs<AiCoworkEvent.Failed>(decoder.finish().single()).also { assertTrue(it.retryable) }
            assertTrue(decoder.accept("[DONE]").isEmpty())
        }
        assertIs<AiCoworkEvent.Failed>(OpenAiChatEventDecoder().accept("[DONE]").single())
    }

    @Test fun refusalToolsAndAbnormalFinishNeverProduceProposalsOrCompletion() {
        for (data in listOf(
            chunk("partial", "length"),
            chunk(reason = "content_filter"),
            chunk(reason = "tool_calls"),
            chunk(extra = buildJsonObject { put("refusal", "private refusal") }),
            chunk(extra = buildJsonObject { put("tool_calls", buildJsonArray { add(buildJsonObject {}) }) }),
        )) {
            val decoder = OpenAiChatEventDecoder()
            assertIs<AiCoworkEvent.Failed>(decoder.accept(data).single())
            assertTrue(decoder.accept("[DONE]").isEmpty())
        }
    }

    @Test fun malformedMixedIdentityAndOversizedEventsFailWithoutLeakingRawContent() {
        for (data in listOf(
            "secret-key-invalid-json",
            """{"error":{"message":"secret-key"}}""",
            chunk().replace("\"index\":0", "\"index\":1"),
            chunk().replace("chat.completion.chunk", "response.created"),
            chunk("x".repeat(262144)),
            "[".repeat(70) + "0" + "]".repeat(70),
        )) {
            val result = assertIs<AiCoworkEvent.Failed>(OpenAiChatEventDecoder().accept(data).single())
            assertFalse(result.message.contains("secret-key"))
        }
        val decoder = OpenAiChatEventDecoder()
        decoder.accept(chunk("first"))
        assertIs<AiCoworkEvent.Failed>(decoder.accept(chunk("second").replace("\"completion\"", "\"different\"")).single())
    }

    @Test fun terminalStopsUpstreamAndTransportErrorIsSanitized() =
        runTest {
            var closed = false
            val adapter =
                OpenAiChatEventAdapter("test") {
                    flow {
                        try {
                            emit(chunk("ok", "stop"))
                            emit("[DONE]")
                            error("must not collect beyond terminal")
                        } finally {
                            closed = true
                        }
                    }
                }
            assertEquals(listOf(AiCoworkEvent.TextDelta("ok"), AiCoworkEvent.Completed), adapter.stream(request).toList())
            assertTrue(closed)
            val failed = OpenAiChatEventAdapter("test") { flow { error("secret transport URL/key") } }
            assertEquals(listOf(AiCoworkEvent.Failed("AI stream connection failed", true)), failed.stream(request).toList())
        }

    @Test fun cancellationClosesTransportWithoutConvertingToFailedOrCompleted() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var closed = false
            val seen = mutableListOf<AiCoworkEvent>()
            val adapter =
                OpenAiChatEventAdapter("test") {
                    flow {
                        try {
                            emit(chunk("partial"))
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
        }

    @Test fun accumulatedTextAndEventsAreBoundedAndConsumerFailureIsNotRetried() =
        runTest {
            val decoder = OpenAiChatEventDecoder()
            repeat(5) { assertIs<AiCoworkEvent.TextDelta>(decoder.accept(chunk("x".repeat(200000))).single()) }
            assertIs<AiCoworkEvent.Failed>(decoder.accept(chunk("x".repeat(200000))).single())
            val many = OpenAiChatEventDecoder()
            repeat(8192) { assertTrue(many.accept(chunk()).isEmpty()) }
            assertIs<AiCoworkEvent.Failed>(many.accept(chunk()).single())
            val consumerFailure = IllegalStateException("consumer")
            var calls = 0
            val adapter =
                OpenAiChatEventAdapter("test") {
                    calls++
                    flowOf(chunk("first"), chunk("second", "stop"), "[DONE]")
                }
            val caught = assertFailsWith<IllegalStateException> { adapter.stream(request).collect { throw consumerFailure } }
            assertSame(consumerFailure, caught)
            assertEquals(1, calls)
        }
}
