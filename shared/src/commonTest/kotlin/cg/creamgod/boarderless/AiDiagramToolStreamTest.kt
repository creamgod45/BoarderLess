package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AiDiagramToolStreamTest {
    private fun chat(
        text: String? = null,
        tools: JsonArray? = null,
        reason: String? = null,
    ) = AiSseDataEvent(
        "message",
        buildJsonObject {
            put("id", "response1")
            put("object", "chat.completion.chunk")
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("index", 0)
                            put("finish_reason", reason?.let(::JsonPrimitive) ?: JsonNull)
                            put(
                                "delta",
                                buildJsonObject {
                                    text?.let { put("content", it) }
                                    tools?.let { put("tool_calls", it) }
                                },
                            )
                        },
                    )
                },
            )
        }.toString(),
    )

    private fun tool(
        index: Int,
        args: String,
        id: String? = null,
        name: String? = null,
    ) = buildJsonObject {
        put("index", index)
        id?.let {
            put("id", it)
            put("type", "function")
        }
        put(
            "function",
            buildJsonObject {
                name?.let { put("name", it) }
                put("arguments", args)
            },
        )
    }

    private val done = AiSseDataEvent("message", "[DONE]")

    private fun claude(
        type: String,
        fields: JsonObject = buildJsonObject {},
    ) = AiSseDataEvent(
        type,
        JsonObject(mapOf("type" to JsonPrimitive(type)) + fields).toString(),
    )

    private val start =
        claude(
            "message_start",
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("id", "m1")
                        put("type", "message")
                        put("role", "assistant")
                        put("content", JsonArray(emptyList()))
                        put("stop_reason", JsonNull)
                    },
                )
            },
        )

    private fun block(
        index: Int,
        type: String = "tool_use",
        id: String = "c1",
        name: String = "read_approved_graph",
    ) = claude(
        "content_block_start",
        buildJsonObject {
            put("index", index)
            put(
                "content_block",
                buildJsonObject {
                    put("type", type)
                    if (type == "text") {
                        put("text", "")
                    } else {
                        put("id", id)
                        put("name", name)
                        put("input", JsonObject(emptyMap()))
                    }
                },
            )
        },
    )

    private fun delta(
        index: Int,
        value: String,
        text: Boolean = false,
    ) = claude(
        "content_block_delta",
        buildJsonObject {
            put("index", index)
            put(
                "delta",
                buildJsonObject {
                    put("type", if (text) "text_delta" else "input_json_delta")
                    put(if (text) "text" else "partial_json", value)
                },
            )
        },
    )

    private fun stopBlock(index: Int) = claude("content_block_stop", buildJsonObject { put("index", index) })

    private fun stop(reason: String = "tool_use") =
        claude(
            "message_delta",
            buildJsonObject {
                put("delta", buildJsonObject { put("stop_reason", reason) })
            },
        )

    private val messageStop = claude("message_stop")

    @Test fun chatInterleavedFragmentsExposeCallsOnlyAfterDoneAndRetainExactArguments() {
        val decoder = OpenAiDiagramToolDecoder("request", "session")
        assertTrue(
            decoder
                .accept(
                    chat(
                        tools =
                            JsonArray(
                                listOf(tool(0, "{\"schema\":", "c1", "read_approved_graph"), tool(1, "{", "c2", "validate_diagram")),
                            ),
                    ),
                ).isEmpty(),
        )
        assertTrue(decoder.accept(chat(tools = JsonArray(listOf(tool(1, "}"), tool(0, "1}"))), reason = "tool_calls")).isEmpty())
        val complete = assertIs<AiDiagramStreamEvent.Completed>(decoder.accept(done).single())
        assertEquals(listOf("c1", "c2"), complete.calls.map { it.callId })
        assertEquals(listOf("{\"schema\":1}", "{}"), complete.calls.map { it.arguments })
        assertTrue(complete.calls.all { it.requestId == "request" && it.sessionId == "session" })
        assertTrue(decoder.accept(done).isEmpty())
        assertTrue(decoder.finish().isEmpty())
    }

    @Test fun claudeOrderedTextAndToolBlocksRequireFinalMessageStop() {
        val decoder = ClaudeDiagramToolDecoder("request", "session")
        val frames =
            listOf(
                start,
                block(0, "text"),
                delta(0, "方案🙂", true),
                stopBlock(0),
                block(1),
                delta(1, "{\"schema\":"),
                delta(1, "1}"),
                stopBlock(1),
                block(2, id = "c2", name = "validate_diagram"),
                stopBlock(2),
                stop(),
            )
        val interim = frames.flatMap(decoder::accept)
        assertEquals(listOf(AiDiagramStreamEvent.TextDelta("方案🙂")), interim)
        val complete = assertIs<AiDiagramStreamEvent.Completed>(decoder.accept(messageStop).single())
        assertEquals("方案🙂", complete.text)
        assertEquals(listOf("{\"schema\":1}", "{}"), complete.calls.map { it.arguments })
    }

    @Test fun eofAbnormalFinishAndEmptyTextNeverExposeAnyCalls() {
        for (finish in listOf(null, "length", "stop")) {
            val decoder = OpenAiDiagramToolDecoder("r", "s")
            val result =
                decoder.accept(
                    chat(tools = JsonArray(listOf(tool(0, "{}", "c1", "read_approved_graph"))), reason = finish),
                ) + decoder.finish()
            assertTrue(result.any { it is AiDiagramStreamEvent.Failed })
            assertFalse(result.any { it is AiDiagramStreamEvent.Completed })
        }
        val chat = OpenAiDiagramToolDecoder("r", "s")
        chat.accept(chat("   ", reason = "stop"))
        assertEquals(AiDiagramStreamFailure.EmptyResponse, assertIs<AiDiagramStreamEvent.Failed>(chat.accept(done).single()).code)
        val claude = ClaudeDiagramToolDecoder("r", "s")
        listOf(start, block(0), delta(0, "{}"), stopBlock(0), stop()).forEach { claude.accept(it) }
        assertEquals(AiDiagramStreamFailure.Incomplete, assertIs<AiDiagramStreamEvent.Failed>(claude.finish().single()).code)
    }

    @Test fun duplicateIdsKeysIndexChangesAndInvalidArgsFailWithoutLeakingProviderErrors() {
        val invalid =
            listOf(
                chat(tools = JsonArray(listOf(tool(1, "{}", "c1", "read_approved_graph")))),
                chat(tools = JsonArray(listOf(tool(0, "{}", "c1", "read_approved_graph"), tool(1, "{}", "c1", "validate_diagram")))),
                chat(tools = JsonArray(listOf(tool(0, "{}", "c1", "read_approved_graph"), tool(0, "{}")))),
                chat(tools = JsonArray(listOf(tool(0, "{\"x\":1,\"\\u0078\":2}", "c1", "create_nodes"))), reason = "tool_calls"),
                chat(tools = JsonArray(listOf(tool(0, "[]", "c1", "create_nodes"))), reason = "tool_calls"),
                chat(tools = JsonArray(listOf(tool(0, "{", "c1", "create_nodes"))), reason = "tool_calls"),
                chat(tools = JsonArray(listOf(tool(0, "{\"text\":\"\\ud800\"}", "c1", "create_nodes"))), reason = "tool_calls"),
                AiSseDataEvent("message", "{\"error\":{\"message\":\"private-key\"}}"),
                chat("x").copy(data = chat("x").data.replace("\"index\":0", "\"index\":0,\"index\":0")),
            )
        invalid.forEach { frame ->
            val result = OpenAiDiagramToolDecoder("r", "s").accept(frame)
            assertIs<AiDiagramStreamEvent.Failed>(result.single())
            assertFalse("private-key" in result.toString())
        }
        val decoder = OpenAiDiagramToolDecoder("r", "s")
        decoder.accept(chat(tools = JsonArray(listOf(tool(0, "{", "c1", "read_approved_graph")))))
        assertIs<AiDiagramStreamEvent.Failed>(decoder.accept(chat(tools = JsonArray(listOf(tool(0, "}", name = "different"))))).single())
    }

    @Test fun claudeRejectsWrongBlockDeltaLifecycleAndUnsupportedThinking() {
        val sequences =
            listOf(
                listOf(block(0)),
                listOf(start, block(1)),
                listOf(start, block(0), delta(1, "{}")),
                listOf(start, block(0), delta(0, "oops", true)),
                listOf(start, block(0), block(1)),
                listOf(start, block(0), delta(0, "{"), stopBlock(0)),
                listOf(start, block(0), delta(0, "{}"), stopBlock(0), stop("end_turn")),
                listOf(start, block(0, "thinking")),
                listOf(start, messageStop),
                listOf(start, start),
            )
        sequences.forEach { frames ->
            val decoder = ClaudeDiagramToolDecoder("r", "s")
            val results = frames.flatMap(decoder::accept)
            assertTrue(results.any { it is AiDiagramStreamEvent.Failed })
            assertFalse(results.any { it is AiDiagramStreamEvent.Completed })
        }
    }

    @Test fun utf8ArgumentsTextAndEventBudgetsAreEnforced() {
        val decoder = OpenAiDiagramToolDecoder("r", "s")
        assertEquals(
            AiDiagramStreamFailure.Limit,
            assertIs<AiDiagramStreamEvent.Failed>(decoder.accept(chat("中".repeat(44000))).single()).code,
        )
        val tools = OpenAiDiagramToolDecoder("r", "s")
        assertEquals(
            AiDiagramStreamFailure.Limit,
            assertIs<AiDiagramStreamEvent.Failed>(
                tools
                    .accept(
                        chat(tools = JsonArray(listOf(tool(0, "{\"text\":\"${"中".repeat(22000)}\"}", "c1", "create_nodes")))),
                    ).single(),
            ).code,
        )
        val events = ClaudeDiagramToolDecoder("r", "s")
        repeat(8192) { assertTrue(events.accept(claude("ping")).isEmpty()) }
        assertEquals(AiDiagramStreamFailure.Limit, assertIs<AiDiagramStreamEvent.Failed>(events.accept(claude("ping")).single()).code)
    }

    @Test fun flowClosesAtTerminalAndPropagatesCancellationWithoutPartialCalls() =
        runTest {
            var closed = false
            val frames =
                flow {
                    try {
                        emit(chat(tools = JsonArray(listOf(tool(0, "{}", "c1", "read_approved_graph"))), reason = "tool_calls"))
                        emit(done)
                        error("must not consume")
                    } finally {
                        closed =
                            true
                    }
                }
            val result = diagramToolEvents(AiRequestDialect.OpenAiChat, "r", "s", frames).toList()
            assertIs<AiDiagramStreamEvent.Completed>(result.single())
            assertTrue(closed)
            val cancelled =
                flow {
                    emit(chat(tools = JsonArray(listOf(tool(0, "{", "c1", "read_approved_graph")))))
                    throw CancellationException("private")
                }
            assertFailsWith<CancellationException> { diagramToolEvents(AiRequestDialect.OpenAiChat, "r", "s", cancelled).toList() }
            assertEquals(
                AiDiagramStreamFailure.Transport,
                assertIs<AiDiagramStreamEvent.Failed>(
                    diagramToolEvents(
                        AiRequestDialect.OpenAiChat,
                        "r",
                        "s",
                        flow {
                            error("private")
                        },
                    ).single(),
                ).code,
            )
        }

    @Test fun completedCallsRunOnceInRealDraftAndReturnThroughBothWireFormats() =
        runTest {
            val args = """{"nodes":[{"id":"A","text":"Start🙂","x":0,"y":0,"width":100,"height":50,"shape":"rectangle","color":"blue"}]}"""
            val caps = AiDiagramCapabilities(setOf(NodeShape.Rectangle), setOf("blue"), setOf("group"))
            for (dialect in AiRequestDialect.entries) {
                val frames =
                    if (dialect ==
                        AiRequestDialect.OpenAiChat
                    ) {
                        listOf(
                            chat(tools = JsonArray(listOf(tool(0, args.take(30), "c1", "create_nodes")))),
                            chat(tools = JsonArray(listOf(tool(0, args.drop(30)))), reason = "tool_calls"),
                            done,
                        )
                    } else {
                        listOf(
                            start,
                            block(0, name = "create_nodes"),
                            delta(0, args.take(30)),
                            delta(0, args.drop(30)),
                            stopBlock(0),
                            stop(),
                            messageStop,
                        )
                    }
                val complete = assertIs<AiDiagramStreamEvent.Completed>(diagramToolEvents(dialect, "r", "s", frames.asFlow()).single())
                val workspace = Workspace(WorkspaceId("private"), "Canvas")
                val draft = AiDiagramDraft("r", "s", workspace, emptySet(), caps)
                val result = draft.execute(complete.calls.single())
                assertEquals(AiDiagramToolStatus.Proposed, result.status)
                assertEquals(AiDiagramToolFailure.DuplicateCall, draft.execute(complete.calls.single()).failure)
                val wire =
                    aiDiagramToolRequestBody(
                        AiRequestBodyProfile(dialect, "model", 4096),
                        "r",
                        "s",
                        "Build",
                        caps,
                        listOf(AiDiagramToolTurn(complete.text, complete.calls, listOf(result))),
                    )
                assertFalse("private" in wire)
                assertTrue(workspace.objects.isEmpty())
                assertNotNull(draft.proposal())
                draft.close()
            }
        }
}
