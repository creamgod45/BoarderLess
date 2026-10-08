package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.test.*

class AiWireDiagnosticTest {
    @Test fun toolReasoningRejectionIsRecordedWithoutProviderEchoOrCredentials() = runTest { withContext(Dispatchers.Default) {
        var calls = 0
        val events = mutableListOf<AiProbeDiagnostic>()
        val client = HttpClient(MockEngine { request ->
            calls++
            assertEquals("Bearer secret-key", request.headers[HttpHeaders.Authorization])
            respond(
                """{"error":{"type":"invalid_request_error","code":"unsupported_value","param":"reasoning_effort","message":"Function tools with reasoning_effort are not supported in /v1/chat/completions. secret-key secret-canvas https://private.invalid"}}""",
                HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { followRedirects = false; install(HttpTimeout) }
        assertFails {
            runAiUnderstandingProbe(
                AiServerProfile("https://private.invalid/v1/chat/completions", AiServerLocation.Remote,
                    AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "gpt-5.6-luna", 4096)),
                headersOf(HttpHeaders.Authorization, "Bearer secret-key"), "secret-canvas", { true }, {}, client, events::add,
            )
        }
        assertEquals(1, calls)
        val sent = events.single { it.stage == AiProbeStage.Connecting }.request!!
        assertEquals(AiWireEndpoint.ChatCompletions, sent.endpoint)
        assertEquals(AiWireModel.Gpt56Luna, sent.model)
        assertEquals(4096, sent.maxCompletionTokens)
        assertEquals(AiWireReasoning.Default, sent.reasoning)
        val failure = events.last()
        assertEquals(400, failure.httpStatus)
        assertEquals(AiProbeFailure.HttpRejected, failure.failure)
        assertEquals(AiWireParameter.ReasoningEffort, failure.providerError?.parameter)
        assertEquals(AiWireRejection.ReasoningToolsInChat, failure.providerError?.rejection)
        val log = events.joinToString("\n") { it.reportLine() }
        for (secret in listOf("secret-key", "secret-canvas", "private.invalid", "Function tools with")) assertFalse(secret in log)
    } }

    @Test fun malformedOversizedAndPrivateProviderFieldsHaveClosedOutputs() {
        assertEquals(AiWireErrorBody.Empty, aiWireErrorDiagnostic(byteArrayOf()).body)
        assertEquals(AiWireErrorBody.InvalidUtf8, aiWireErrorDiagnostic(byteArrayOf(0xc3.toByte(), 0x28)).body)
        assertEquals(AiWireErrorBody.TooLarge, aiWireErrorDiagnostic(ByteArray(16385)).body)
        assertEquals(AiWireErrorBody.InvalidJson, aiWireErrorDiagnostic("not JSON".encodeToByteArray()).body)
        val error = aiWireErrorDiagnostic("""{"error":{"type":"secret-type","code":"secret-code","param":"secret-parameter","message":"secret-message"}}""".encodeToByteArray())
        assertEquals(AiWireErrorType.Other, error.type)
        assertEquals(AiWireErrorCode.Other, error.code)
        assertEquals(AiWireParameter.Other, error.parameter)
        assertFalse("secret" in error.report())
        assertEquals(AiWireParameter.Tools, aiWireErrorDiagnostic("""{"error":{"param":"tools[2].function.parameters"}}""".encodeToByteArray()).parameter)
    }

    @Test fun requestSummaryContainsOptionsAndCountsButNeverMessageOrSchemaText() {
        val summary = aiWireRequestDiagnostic("""{"model":"private-model","messages":[{"content":"secret-canvas"}],"tools":[{"description":"secret-tool"}],"stream":true,"max_completion_tokens":4096,"reasoning_effort":"none","secret-key":"private-value"}""", "/private-path")!!
        assertEquals(1, summary.tools)
        assertEquals(1, summary.messages)
        assertEquals(AiWireReasoning.None, summary.reasoning)
        assertEquals(AiWireEndpoint.Other, summary.endpoint)
        assertEquals(AiWireModel.Other, summary.model)
        for (secret in listOf("secret", "private")) assertFalse(secret in summary.report())
    }

    @Test fun invalidDiagnosticBodyStillPreservesHttpFailureAndMakesOnlyOneRequest() = runTest { withContext(Dispatchers.Default) {
        for (body in listOf(ByteArray(16385) { 65 }, byteArrayOf(0xc3.toByte(), 0x28))) {
            var calls = 0
            val events = mutableListOf<AiProbeDiagnostic>()
            val client = HttpClient(MockEngine {
                calls++
                respond(body, HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, "application/json"))
            }) { followRedirects = false; install(HttpTimeout) }
            assertFails {
                runAiUnderstandingProbe(
                    AiServerProfile("https://fixture.invalid/v1/chat/completions", AiServerLocation.Remote,
                        AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "gpt-5.6-luna", 4096)),
                    headersOf(HttpHeaders.Authorization, "Bearer key"), "prompt", { true }, {}, client, events::add,
                )
            }
            assertEquals(1, calls)
            assertEquals(400, events.last().httpStatus)
            assertEquals(AiProbeFailure.HttpRejected, events.last().failure)
            assertEquals(if (body.size > 16384) AiWireErrorBody.TooLarge else AiWireErrorBody.InvalidUtf8,
                events.last().providerError?.body)
        }
    } }
}
