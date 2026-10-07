package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AiProbeDiagnosticsTest {
    private val server =
        AiServerProfile(
            "https://fixture.invalid/chat",
            AiServerLocation.Remote,
            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "private-model", 4096),
        )

    private fun client(handler: MockRequestHandler) =
        HttpClient(MockEngine(handler)) {
            followRedirects = false
            install(HttpTimeout)
        }

    private val secrets = "private-prompt private-key https://private-url.invalid/private-path"
    private val credentials = headersOf(HttpHeaders.Authorization, "Bearer private-key")

    private fun assertSafe(events: List<AiProbeDiagnostic>) {
        val report = events.joinToString("\n") { it.reportLine() }
        listOf("private-prompt", "private-key", "private-url", "private-model", "private-path").forEach { assertFalse(report.contains(it)) }
    }

    @Test fun httpRejectionsKeepStatusAndDoNotExposeProviderErrorBodyOrSecrets() =
        runTest {
            for (status in listOf(400, 401, 403, 404, 422, 429, 500, 503)) {
                val events = mutableListOf<AiProbeDiagnostic>()
                val c =
                    client {
                        respond(
                            "{\"error\":{\"message\":\"$secrets\"}}",
                            HttpStatusCode.fromValue(status),
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                assertFails { runAiUnderstandingProbe(server, credentials, secrets, { true }, {}, c, events::add) }
                val final = events.last()
                assertEquals(AiProbeStage.Failed, final.stage)
                assertEquals(AiProbeFailure.HttpRejected, final.failure)
                assertEquals(status, final.httpStatus)
                assertEquals(AiProbeResponseType.Json, final.responseType)
                assertTrue(events.any { it.stage == AiProbeStage.HttpResponse })
                assertSafe(events)
            }
        }

    @Test fun preflightAndConfigurationFailuresNeverClaimAiWasCalled() =
        runTest {
            for (mode in listOf("configuration", "changed", "backend", "denied")) {
                var calls = 0
                val events = mutableListOf<AiProbeDiagnostic>()
                val c =
                    client {
                        calls++
                        respond("unexpected")
                    }
                assertFails {
                    runAiUnderstandingProbe(
                        if (mode == "configuration") server.copy(endpoint = "http://fixture.invalid/chat") else server,
                        credentials,
                        secrets,
                        {
                            when (mode) {
                                "changed" -> throw AiProbePreflightException(AiProbeFailure.WorkspaceChanged)
                                "backend" -> throw AiProbePreflightException(AiProbeFailure.WorkspaceBackend, 403)
                                "denied" -> false
                                else -> true
                            }
                        },
                        {},
                        c,
                        events::add,
                    )
                }
                assertEquals(0, calls)
                assertFalse(events.any { it.stage == AiProbeStage.Connecting })
                assertEquals(
                    when (mode) {
                        "configuration" -> AiProbeFailure.Configuration
                        "changed" -> AiProbeFailure.WorkspaceChanged
                        "backend" -> AiProbeFailure.WorkspaceBackend
                        else -> AiProbeFailure.ConsentDenied
                    },
                    events.last().failure,
                )
                if (mode == "backend") assertEquals(403, events.last().httpStatus)
                assertSafe(events)
            }
        }

    @Test fun wrongContentTypeIsDistinctFromMalformedOrIncompleteStream() =
        runTest {
            for (mode in listOf("json", "html", "malformed", "incomplete")) {
                val events = mutableListOf<AiProbeDiagnostic>()
                val c =
                    client {
                        respond(
                            if (mode == "malformed") "data: {invalid}\n\n" else "",
                            HttpStatusCode.OK,
                            headersOf(
                                HttpHeaders.ContentType,
                                when (mode) {
                                    "json" -> "application/json"
                                    "html" -> "text/html"
                                    else -> "text/event-stream"
                                },
                            ),
                        )
                    }
                assertFails { runAiUnderstandingProbe(server, credentials, secrets, { true }, {}, c, events::add) }
                assertEquals(
                    when (mode) {
                        "json", "html" -> AiProbeFailure.ResponseFormat
                        "malformed" -> AiProbeFailure.StreamFormat
                        else -> AiProbeFailure.Incomplete
                    },
                    events.last().failure,
                )
                assertSafe(events)
            }
        }

    private fun chunk(
        content: String,
        finish: Boolean = false,
    ) =
        "data: {\"id\":\"c\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"$content\"},\"finish_reason\":${if (finish) "\"stop\"" else "null"}}]}\n\n"

    @Test fun completedEmptyOrWhitespaceResponsesAreNotUsableAnswers() =
        runTest {
            for (content in listOf("", "   ")) {
                val events = mutableListOf<AiProbeDiagnostic>()
                val c =
                    client {
                        respond(
                            chunk(content, true) + "data: [DONE]\n\n",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                    }
                assertFails { runAiUnderstandingProbe(server, credentials, secrets, { true }, {}, c, events::add) }
                assertEquals(AiProbeFailure.Incomplete, events.last().failure)
                assertFalse(events.any { it.stage == AiProbeStage.Completed })
            }
        }

    @Test fun outputLimitCountsUtf8BytesAndKeepsOnlyAcceptedPartialOutput() =
        runTest {
            val delta = "漢".repeat(10_000)
            for (count in listOf(4, 5)) {
                val events = mutableListOf<AiProbeDiagnostic>()
                var displayed = ""
                val c =
                    client {
                        respond(
                            (1..count).joinToString("") { chunk(delta, it == count) } + "data: [DONE]\n\n",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                    }
                if (count == 4) {
                    assertEquals(
                        delta.repeat(4),
                        runAiUnderstandingProbe(
                            server,
                            credentials,
                            secrets,
                            { true },
                            { displayed = it },
                            c,
                            events::add,
                        ),
                    )
                    assertEquals(AiProbeStage.Completed, events.last().stage)
                } else {
                    assertFails {
                        runAiUnderstandingProbe(
                            server,
                            credentials,
                            secrets,
                            { true },
                            { displayed = it },
                            c,
                            events::add,
                        )
                    }
                    assertEquals(AiProbeFailure.OutputLimit, events.last().failure)
                    assertFalse(events.any { it.stage == AiProbeStage.Completed })
                }
                assertEquals(120_000, displayed.encodeToByteArray().size)
                assertSafe(events)
            }
        }

    @Test fun successAndCancellationRemainVisibleTerminalStates() =
        runTest {
            val success = mutableListOf<AiProbeDiagnostic>()
            val c =
                client {
                    respond(
                        "data: {\"id\":\"c\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "text/event-stream"),
                    )
                }
            assertEquals("{}", runAiUnderstandingProbe(server, credentials, secrets, { true }, {}, c, success::add))
            assertEquals(AiProbeStage.Completed, success.last().stage)
            assertTrue(success.any { it.stage == AiProbeStage.Receiving })
            assertSafe(success)
            val cancelled = mutableListOf<AiProbeDiagnostic>()
            val waiting = client { awaitCancellation() }
            val job = launch { runAiUnderstandingProbe(server, credentials, secrets, { true }, {}, waiting, cancelled::add) }
            runCurrent()
            job.cancelAndJoin()
            assertEquals(AiProbeStage.Cancelled, cancelled.last().stage)
            assertFalse(cancelled.any { it.stage == AiProbeStage.Completed })
            assertSafe(cancelled)
        }
}
