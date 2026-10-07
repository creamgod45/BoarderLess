package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AiUnderstandingProbeTest {
    private val server =
        AiServerProfile(
            "https://fixture.invalid/chat",
            AiServerLocation.Remote,
            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "fixture-model", 4096),
        )

    @Test fun explicitApprovalDispatchesExactlyOnceAndReturnsOnlyCompletedText() =
        runTest {
            var calls = 0
            var approved = 0
            val output = mutableListOf<String>()
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond(
                            "data: {\"id\":\"c\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"{}\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "text/event-stream"),
                        )
                    },
                ) {
                    followRedirects = false
                    install(HttpTimeout)
                }
            assertEquals(
                "{}",
                runAiUnderstandingProbe(
                    server,
                    Headers.Empty,
                    "Reviewed structural JSON",
                    {
                        approved++
                        true
                    },
                    { output += it },
                    client,
                ),
            )
            assertEquals(1, approved)
            assertEquals(1, calls)
            assertEquals(listOf("{}"), output)
        }

    @Test fun revokedApprovalNeverSendsNetworkRequestOrOutput() =
        runTest {
            var calls = 0
            var output = 0
            val client =
                HttpClient(
                    MockEngine {
                        calls++
                        respond("unexpected")
                    },
                ) {
                    followRedirects = false
                    install(HttpTimeout)
                }
            assertFails { runAiUnderstandingProbe(server, Headers.Empty, "Reviewed JSON", { false }, { output++ }, client) }
            assertEquals(0, calls)
            assertEquals(0, output)
        }
}
