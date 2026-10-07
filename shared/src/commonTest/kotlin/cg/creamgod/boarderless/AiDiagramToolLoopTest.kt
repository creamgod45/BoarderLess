package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class AiDiagramToolLoopTest {
    private val workspace = Workspace(WorkspaceId("private-workspace"), "Canvas")
    private val caps = AiDiagramCapabilities(setOf(NodeShape.Rectangle), setOf("blue"), setOf("group"))
    private val args = """{"nodes":[{"id":"A","text":"Start🙂","x":0,"y":0,"width":100,"height":50,"shape":"rectangle","color":"blue"}]}"""

    private fun previewNodeId(proposal: AiProposal) = (proposal.items.single().operation as CreateObjectsOperation).objects.single().id

    private fun server(dialect: AiRequestDialect) =
        AiServerProfile(
            "https://fixture.invalid/user-selected",
            AiServerLocation.Remote,
            AiRequestBodyProfile(dialect, "explicit-model", 4096),
            anthropicVersion =
                if (dialect ==
                    AiRequestDialect.AnthropicMessages
                ) {
                    "2023-06-01"
                } else {
                    null
                },
        )

    private fun client(handler: MockRequestHandler) =
        HttpClient(MockEngine(handler)) {
            followRedirects = false
            install(HttpTimeout)
        }

    private fun wire(content: OutgoingContent) =
        when (content) {
            is TextContent -> content.text
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            else -> error("unexpected")
        }

    private val sseHeaders = headersOf(HttpHeaders.ContentType, "text/event-stream")

    private fun reply(
        dialect: AiRequestDialect,
        callId: String? = null,
        name: String = "create_nodes",
        arguments: String = args,
        text: String = "Done🙂",
    ): String {
        if (dialect == AiRequestDialect.OpenAiChat) {
            val chunk =
                buildJsonObject {
                    put("id", "completion")
                    put("object", "chat.completion.chunk")
                    put(
                        "choices",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("index", 0)
                                    put("finish_reason", if (callId == null) "stop" else "tool_calls")
                                    put(
                                        "delta",
                                        buildJsonObject {
                                            if (callId ==
                                                null
                                            ) {
                                                put("content", text)
                                            } else {
                                                put(
                                                    "tool_calls",
                                                    buildJsonArray {
                                                        add(
                                                            buildJsonObject {
                                                                put("index", 0)
                                                                put("id", callId)
                                                                put("type", "function")
                                                                put(
                                                                    "function",
                                                                    buildJsonObject {
                                                                        put("name", name)
                                                                        put("arguments", arguments)
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            }
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            return "data: $chunk\n\ndata: [DONE]\n\n"
        }

        fun frame(
            type: String,
            values: JsonObject =
                buildJsonObject {
                },
        ) = "event: $type\ndata: ${JsonObject(mapOf("type" to JsonPrimitive(type)) + values)}\n\n"
        return frame(
            "message_start",
            buildJsonObject {
                put(
                    "message",
                    buildJsonObject {
                        put("id", "m")
                        put("role", "assistant")
                        put("type", "message")
                        put("content", JsonArray(emptyList()))
                    },
                )
            },
        ) +
            frame(
                "content_block_start",
                buildJsonObject {
                    put("index", 0)
                    put(
                        "content_block",
                        buildJsonObject {
                            put("type", if (callId == null) "text" else "tool_use")
                            if (callId == null) {
                                put("text", text)
                            } else {
                                put("id", callId)
                                put("name", name)
                                put("input", JsonObject(emptyMap()))
                            }
                        },
                    )
                },
            ) +
            (
                if (callId ==
                    null
                ) {
                    ""
                } else {
                    frame(
                        "content_block_delta",
                        buildJsonObject {
                            put("index", 0)
                            put(
                                "delta",
                                buildJsonObject {
                                    put("type", "input_json_delta")
                                    put("partial_json", arguments)
                                },
                            )
                        },
                    )
                }
            ) +
            frame("content_block_stop", buildJsonObject { put("index", 0) }) +
            frame(
                "message_delta",
                buildJsonObject {
                    put(
                        "delta",
                        buildJsonObject {
                            put(
                                "stop_reason",
                                if (callId ==
                                    null
                                ) {
                                    "end_turn"
                                } else {
                                    "tool_use"
                                },
                            )
                        },
                    )
                },
            ) +
            frame("message_stop")
    }

    @Test fun bothDialectsApproveExactBodyBeforeFreshCredentialsAndReturnOnlyProposal() =
        runTest {
            for (dialect in AiRequestDialect.entries) {
                val order = mutableListOf<String>()
                var approvedBody = ""
                var requests = 0
                var checks = 0
                val progress = mutableListOf<AiDiagramLoopProgress>()
                val diagnostics = mutableListOf<AiProbeDiagnostic>()
                val http =
                    client { outgoing ->
                        requests++
                        order += "http$requests"
                        assertEquals(approvedBody, wire(outgoing.body))
                        assertEquals(server(dialect).endpoint, outgoing.url.toString())
                        assertEquals("Bearer fixture-$requests", outgoing.headers[HttpHeaders.Authorization])
                        assertEquals("text/event-stream", outgoing.headers[HttpHeaders.Accept])
                        if (dialect == AiRequestDialect.AnthropicMessages) assertEquals("2023-06-01", outgoing.headers["anthropic-version"])
                        if (requests == 2) {
                            assertContains(approvedBody, "proposed")
                            assertFalse("private-workspace" in approvedBody)
                        }
                        respond(reply(dialect, if (requests == 1) "c1" else null), HttpStatusCode.OK, sseHeaders)
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(dialect),
                        "request",
                        "session",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = { review ->
                            order += "approve${review.round}"
                            approvedBody = review.body
                            assertEquals("request", review.requestId)
                            true
                        },
                        validateScope = {
                            checks++
                            order += "check"
                            true
                        },
                        isSessionCurrent = { true },
                        credentialHeaders = {
                            order += "credentials"
                            headersOf(HttpHeaders.Authorization, "Bearer fixture-${requests + 1}")
                        },
                        client = http,
                        onProgress = progress::add,
                        onDiagnostic = { _, event -> diagnostics += event },
                    )
                val ready = assertIs<AiDiagramLoopResult.Ready>(result)
                assertEquals("Done🙂", ready.text)
                assertEquals(1, ready.localReferences.size)
                assertEquals(previewNodeId(ready.proposal!!), ready.localReferences.getValue("A"))
                val preview = assertIs<AiProposalPreview.Ready>(ready.proposal!!.preview(workspace))
                assertEquals(1, preview.workspace.objects.size)
                assertTrue(workspace.objects.isEmpty())
                assertEquals(2, requests)
                assertEquals(6, checks)
                assertTrue(order.indexOf("approve1") < order.indexOf("credentials"))
                assertTrue(order.indexOf("approve2") < order.indexOf("http2"))
                assertEquals(1, progress.filterIsInstance<AiDiagramLoopProgress.Tools>().size)
                assertEquals(AiProbeStage.Completed, diagnostics.last().stage)
                http.coroutineContext[Job]!!.join()
                assertTrue(http.coroutineContext[Job]!!.isCompleted)
            }
        }

    @Test fun denyingSecondRoundDiscardsDraftAndNeverObtainsSecondCredentialsOrDispatches() =
        runTest {
            var requests = 0
            var secrets = 0
            var approvals = 0
            val http =
                client {
                    requests++
                    respond(reply(AiRequestDialect.OpenAiChat, "c1"), HttpStatusCode.OK, sseHeaders)
                }
            val result =
                runAiDiagramToolLoop(
                    server(AiRequestDialect.OpenAiChat),
                    "r",
                    "s",
                    "Build",
                    workspace,
                    emptySet(),
                    caps,
                    approve = {
                        approvals++
                        it.round == 1
                    },
                    validateScope = { true },
                    isSessionCurrent = { true },
                    credentialHeaders = {
                        secrets++
                        Headers.Empty
                    },
                    client = http,
                )
            assertEquals(AiDiagramLoopResult.Failed(AiDiagramLoopFailure.ConsentDenied), result)
            assertEquals(2, approvals)
            assertEquals(1, requests)
            assertEquals(1, secrets)
            assertTrue(workspace.objects.isEmpty())
        }

    @Test fun scopeRevocationAtConsentSecretsFramesToolProgressAndPublicationStops() =
        runTest {
            for (mode in listOf("consent", "fresh", "secrets", "frame", "tools", "completed")) {
                var current = true
                var requests = 0
                var secrets = 0
                var tools = 0
                val diagnostics = mutableListOf<AiProbeDiagnostic>()
                val http =
                    client {
                        requests++
                        if (mode == "frame") current = false
                        respond(
                            reply(
                                AiRequestDialect.OpenAiChat,
                                if (mode == "tools" ||
                                    mode == "frame"
                                ) {
                                    "c1"
                                } else {
                                    null
                                },
                            ),
                            HttpStatusCode.OK,
                            sseHeaders,
                        )
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(AiRequestDialect.OpenAiChat),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = {
                            if (mode == "consent") current = false
                            true
                        },
                        validateScope = { mode != "fresh" },
                        isSessionCurrent = { current },
                        credentialHeaders = {
                            secrets++
                            if (mode == "secrets") current = false
                            Headers.Empty
                        },
                        client = http,
                        onProgress = {
                            if (it is AiDiagramLoopProgress.Tools) {
                                tools++
                                if (mode == "tools") current = false
                            }
                        },
                        onDiagnostic = { _, event ->
                            diagnostics += event
                            if (mode == "completed" &&
                                event.stage == AiProbeStage.Completed
                            ) {
                                current = false
                            }
                        },
                    )
                assertIs<AiDiagramLoopResult.Failed>(result)
                assertEquals(if (mode in listOf("consent", "fresh", "secrets")) 0 else 1, requests, mode)
                assertEquals(if (mode in listOf("consent", "fresh")) 0 else 1, secrets, mode)
                assertEquals(if (mode == "tools") 1 else 0, tools, mode)
                assertEquals(AiProbeStage.Failed, diagnostics.last().stage)
            }
        }

    @Test fun http401IncompleteEmptyAndMalformedStreamsAreTerminalWithSafeDiagnostics() =
        runTest {
            for (mode in listOf("401", "eof", "empty", "malformed")) {
                var requests = 0
                var approvals = 0
                val diagnostics = mutableListOf<AiProbeDiagnostic>()
                val http =
                    client {
                        requests++
                        if (mode ==
                            "401"
                        ) {
                            respond(
                                "private upstream details",
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.ContentType, "text/plain"),
                            )
                        } else {
                            respond(
                                when (mode) {
                                    "eof" -> reply(AiRequestDialect.OpenAiChat, "c1").substringBefore("data: [DONE]")
                                    "empty" -> reply(AiRequestDialect.OpenAiChat, text = "  ")
                                    else -> "data: private-key-invalid\n\n"
                                },
                                HttpStatusCode.OK,
                                sseHeaders,
                            )
                        }
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(AiRequestDialect.OpenAiChat),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = {
                            approvals++
                            true
                        },
                        validateScope = { true },
                        isSessionCurrent = { true },
                        credentialHeaders = { Headers.Empty },
                        client = http,
                        onDiagnostic = { _, event -> diagnostics += event },
                    )
                assertIs<AiDiagramLoopResult.Failed>(result)
                assertEquals(1, requests)
                assertEquals(1, approvals)
                if (mode ==
                    "401"
                ) {
                    assertEquals(AiProbeFailure.HttpRejected, diagnostics.last().failure)
                    assertEquals(401, diagnostics.last().httpStatus)
                    assertEquals(AiProbeResponseType.Other, diagnostics.last().responseType)
                }
                assertFalse("private" in diagnostics.toString())
                assertTrue(workspace.objects.isEmpty())
            }
        }

    @Test fun rejectedSchemaCanBeCorrectedOnlyThroughAnotherExplicitlyApprovedRequest() =
        runTest {
            for (dialect in AiRequestDialect.entries) {
                var requests = 0
                var approvals = 0
                val toolResults = mutableListOf<AiDiagramToolResult>()
                val http =
                    client { outgoing ->
                        requests++
                        if (requests == 2) assertContains(wire(outgoing.body), "rejected")
                        respond(
                            when (requests) {
                                1 -> reply(dialect, "bad", arguments = args.replace("rectangle", "unavailable"))
                                2 -> reply(dialect, "fixed")
                                else -> reply(dialect)
                            },
                            HttpStatusCode.OK,
                            sseHeaders,
                        )
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(dialect),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = {
                            approvals++
                            true
                        },
                        validateScope = { true },
                        isSessionCurrent = { true },
                        credentialHeaders = { Headers.Empty },
                        client = http,
                        onProgress = { if (it is AiDiagramLoopProgress.Tools) toolResults += it.results },
                    )
                assertEquals(3, requests)
                assertEquals(3, approvals)
                assertEquals(listOf(AiDiagramToolStatus.Rejected, AiDiagramToolStatus.Proposed), toolResults.map { it.status })
                assertNotNull(assertIs<AiDiagramLoopResult.Ready>(result).proposal)
            }
        }

    @Test fun repeatedCallIdentityUnknownToolAndEightRequestLimitDoNotSelfRetry() =
        runTest {
            for (mode in listOf("duplicate", "unknown", "limit")) {
                var requests = 0
                var approvals = 0
                var toolCount = 0
                val http =
                    client {
                        requests++
                        respond(
                            reply(
                                AiRequestDialect.OpenAiChat,
                                if (mode == "limit") "c$requests" else "c1",
                                name = if (mode == "unknown") "http_fetch" else "read_approved_graph",
                                arguments = "{}",
                            ),
                            HttpStatusCode.OK,
                            sseHeaders,
                        )
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(AiRequestDialect.OpenAiChat),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = {
                            approvals++
                            true
                        },
                        validateScope = { true },
                        isSessionCurrent = { true },
                        credentialHeaders = { Headers.Empty },
                        client = http,
                        onProgress = { if (it is AiDiagramLoopProgress.Tools) toolCount += it.results.size },
                    )
                assertEquals(
                    AiDiagramLoopResult.Failed(
                        if (mode ==
                            "limit"
                        ) {
                            AiDiagramLoopFailure.Limit
                        } else {
                            AiDiagramLoopFailure.ToolProtocol
                        },
                    ),
                    result,
                )
                assertEquals(
                    when (mode) {
                        "duplicate" -> 2
                        "unknown" -> 1
                        else -> 8
                    },
                    requests,
                )
                assertEquals(requests, approvals)
                assertEquals(
                    when (mode) {
                        "duplicate" -> 1
                        "unknown" -> 0
                        else -> 7
                    },
                    toolCount,
                )
            }
        }

    @Test fun cancellationInApprovalSecretsOrHttpPropagatesAndClosesClient() =
        runTest {
            for (phase in listOf("approval", "secrets", "http")) {
                val entered = CompletableDeferred<Unit>()
                var requests = 0
                val http =
                    client {
                        requests++
                        entered.complete(Unit)
                        awaitCancellation()
                    }
                val job =
                    async {
                        runAiDiagramToolLoop(
                            server(AiRequestDialect.OpenAiChat),
                            "r",
                            "s",
                            "Build",
                            workspace,
                            emptySet(),
                            caps,
                            approve = {
                                if (phase ==
                                    "approval"
                                ) {
                                    entered.complete(Unit)
                                    awaitCancellation()
                                }
                                ; true
                            },
                            validateScope = { true },
                            isSessionCurrent = { true },
                            credentialHeaders = {
                                if (phase == "secrets") {
                                    entered.complete(Unit)
                                    awaitCancellation()
                                }
                                Headers.Empty
                            },
                            client = http,
                        )
                    }
                entered.await()
                job.cancel()
                assertFailsWith<CancellationException> { job.await() }
                assertEquals(
                    if (phase ==
                        "http"
                    ) {
                        1
                    } else {
                        0
                    },
                    requests,
                )
                http.coroutineContext[Job]!!.join()
                assertTrue(http.coroutineContext[Job]!!.isCompleted)
            }
            val http = client { error("must not dispatch") }
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(10) {
                    runAiDiagramToolLoop(
                        server(AiRequestDialect.OpenAiChat),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = {
                            awaitCancellation()
                        },
                        validateScope = { true },
                        isSessionCurrent = { true },
                        credentialHeaders = { Headers.Empty },
                        client = http,
                    )
                }
            }
        }

    @Test fun invalidProfilesHeadersPreflightAndObserverErrorsNeverLeakOrFallback() =
        runTest {
            for (mode in listOf("endpoint", "headers", "preflight", "timeout", "observer")) {
                var requests = 0
                val http =
                    client {
                        requests++
                        respond(reply(AiRequestDialect.OpenAiChat), HttpStatusCode.OK, sseHeaders)
                    }

                suspend fun run() =
                    runAiDiagramToolLoop(
                        if (mode ==
                            "endpoint"
                        ) {
                            server(AiRequestDialect.OpenAiChat).copy(endpoint = "https://user:private@fixture.invalid/path")
                        } else {
                            server(AiRequestDialect.OpenAiChat)
                        },
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = { true },
                        validateScope = {
                            if (mode ==
                                "preflight"
                            ) {
                                error("private")
                            }
                            ; if (mode == "timeout") awaitCancellation()
                            true
                        },
                        isSessionCurrent = { true },
                        credentialHeaders = { if (mode == "headers") headersOf("Host", "other.invalid") else Headers.Empty },
                        client = http,
                        onProgress = { if (mode == "observer") error("observer sentinel") },
                    )
                if (mode == "observer") {
                    assertEquals("observer sentinel", assertFailsWith<IllegalStateException> { run() }.message)
                } else {
                    assertFalse("private" in assertIs<AiDiagramLoopResult.Failed>(run()).toString())
                }
                assertEquals(
                    if (mode ==
                        "observer"
                    ) {
                        1
                    } else {
                        0
                    },
                    requests,
                )
                http.coroutineContext[Job]!!.join()
                assertTrue(http.coroutineContext[Job]!!.isCompleted)
            }
        }

    @Test fun cumulativeCallAndArgumentLimitsRejectTheNextTurnBeforeAnyToolsRun() =
        runTest {
            for (mode in listOf("calls", "arguments")) {
                var requests = 0
                var tools = 0
                val n = if (mode == "calls") 64 else 9
                val arguments = if (mode == "calls") "{}" else JsonObject(mapOf("extra" to JsonPrimitive("x".repeat(62000)))).toString()
                val http =
                    client {
                        requests++
                        val count =
                            if (requests == 1) {
                                n
                            } else if (mode == "calls") {
                                1
                            } else {
                                9
                            }
                        val chunks =
                            (0 until count).joinToString("") { i ->
                                val frame =
                                    buildJsonObject {
                                        put("id", "completion")
                                        put("object", "chat.completion.chunk")
                                        put(
                                            "choices",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("index", 0)
                                                        put("finish_reason", if (i == count - 1) JsonPrimitive("tool_calls") else JsonNull)
                                                        put(
                                                            "delta",
                                                            buildJsonObject {
                                                                put(
                                                                    "tool_calls",
                                                                    buildJsonArray {
                                                                        add(
                                                                            buildJsonObject {
                                                                                put("index", i)
                                                                                put("id", "round${requests}call$i")
                                                                                put("type", "function")
                                                                                put(
                                                                                    "function",
                                                                                    buildJsonObject {
                                                                                        put("name", "read_approved_graph")
                                                                                        put("arguments", arguments)
                                                                                    },
                                                                                )
                                                                            },
                                                                        )
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    }
                                "data: $frame\n\n"
                            } + "data: [DONE]\n\n"
                        respond(chunks, HttpStatusCode.OK, sseHeaders)
                    }
                val result =
                    runAiDiagramToolLoop(
                        server(AiRequestDialect.OpenAiChat),
                        "r",
                        "s",
                        "Build",
                        workspace,
                        emptySet(),
                        caps,
                        approve = { true },
                        validateScope = { true },
                        isSessionCurrent = { true },
                        credentialHeaders = { Headers.Empty },
                        client = http,
                        onProgress = { if (it is AiDiagramLoopProgress.Tools) tools += it.results.size },
                    )
                assertEquals(AiDiagramLoopResult.Failed(AiDiagramLoopFailure.Limit), result)
                assertEquals(2, requests)
                assertEquals(n, tools)
            }
        }

    @Test fun callerMutationDuringApprovalCannotAlterFrozenScopeOrConfirmedCapabilities() =
        runTest {
            val node =
                TextNode(CanvasObjectId("private-object"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "Original")
            val objects = mutableMapOf<CanvasObjectId, CanvasObject>(node.id to node)
            val ids = mutableSetOf(node.id)
            val shapes = mutableSetOf(NodeShape.Rectangle)
            var requests = 0
            val http =
                client { outgoing ->
                    requests++
                    if (requests ==
                        2
                    ) {
                        assertContains(wire(outgoing.body), "Original")
                        assertFalse("private-object" in wire(outgoing.body))
                        assertContains(wire(outgoing.body), "rectangle")
                    }
                    respond(
                        reply(
                            AiRequestDialect.OpenAiChat,
                            if (requests ==
                                1
                            ) {
                                "c1"
                            } else {
                                null
                            },
                            name = "read_approved_graph",
                            arguments = "{}",
                        ),
                        HttpStatusCode.OK,
                        sseHeaders,
                    )
                }
            val result =
                runAiDiagramToolLoop(
                    server(AiRequestDialect.OpenAiChat),
                    "r",
                    "s",
                    "Read",
                    workspace.copy(objects = objects),
                    ids,
                    caps.copy(shapes = shapes),
                    approve = {
                        objects.clear()
                        ids.clear()
                        shapes.clear()
                        true
                    },
                    validateScope = { true },
                    isSessionCurrent = { true },
                    credentialHeaders = { Headers.Empty },
                    client = http,
                )
            assertNull(assertIs<AiDiagramLoopResult.Ready>(result).proposal)
            assertEquals(2, requests)
        }
}
