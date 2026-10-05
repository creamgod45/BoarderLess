package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ConfiguredAiProviderTest {
    private val request = AiCoworkRequest("request", "素材🙂",
        AiContextSnapshot("private-workspace", 1, AiContextScope.PromptOnly, emptyList(), emptyList()))
    private val server = AiServerProfile("https://fixture.invalid/explicit-endpoint", AiServerLocation.Remote,
        AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "explicit-model", 100))
    private val chat = "data: {\"id\":\"c\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"ok🙂\"},\"finish_reason\":\"stop\"}]}\n\ndata: [DONE]\n\n"
    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) { followRedirects = false; install(HttpTimeout) }
    private fun responseHeaders() = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private fun wire(body: OutgoingContent) = when (body) {
        is TextContent -> body.text
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> error("Unexpected body")
    }

    @Test fun chatCompositionIsColdApprovesExactBodyAndObtainsHeadersPerCollection() = runTest {
        val order = mutableListOf<String>()
        var approvedBody = ""
        val channels = mutableListOf<ByteReadChannel>()
        val provider = ConfiguredAiProvider("chat", server, client { outgoing ->
            order += "request"
            assertEquals(server.endpoint, outgoing.url.toString())
            assertEquals(HttpMethod.Post, outgoing.method)
            assertEquals("text/event-stream", outgoing.headers[HttpHeaders.Accept])
            assertEquals("application/json", outgoing.body.contentType?.toString())
            assertEquals("Bearer fixture-secret", outgoing.headers[HttpHeaders.Authorization])
            assertEquals(approvedBody, wire(outgoing.body))
            respond(ByteReadChannel(chat).also { channels += it }, HttpStatusCode.OK, responseHeaders())
        }, approve = { review ->
            order += "approve"; assertEquals(request, review.request); assertEquals(server, review.server)
            approvedBody = review.body; assertFalse(review.body.contains("private-workspace")); true
        }, credentialHeaders = { order += "credentials"; headersOf(HttpHeaders.Authorization, "Bearer fixture-secret") })
        try {
            val flow = provider.stream(request)
            assertTrue(order.isEmpty())
            repeat(2) { assertEquals(listOf(AiCoworkEvent.TextDelta("ok🙂"), AiCoworkEvent.Completed), flow.toList()) }
            assertEquals(List(2) { listOf("approve", "credentials", "request") }.flatten(), order)
            assertTrue(channels.all { it.isClosedForRead })
        } finally { provider.close() }
    }

    @Test fun messagesAndExplicitInsecureLocalEndpointUseNamedLifecycleWithoutFallback() = runTest {
        val frames = listOf(
            "message_start" to "\"message\":{\"id\":\"m\",\"type\":\"message\",\"role\":\"assistant\",\"content\":[]}",
            "content_block_start" to "\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"ok\"}",
            "content_block_stop" to "\"index\":0",
            "message_delta" to "\"delta\":{\"stop_reason\":\"end_turn\"}",
            "message_stop" to "")
        val named = frames.joinToString("") { (type, fields) -> "event: $type\ndata: {\"type\":\"$type\"${if (fields.isEmpty()) "" else ",$fields"}}\n\n" }
        var calls = 0
        val local = server.copy(endpoint = "http://localhost:4567/user-selected/messages", location = AiServerLocation.Local,
            body = server.body.copy(dialect = AiRequestDialect.AnthropicMessages), anthropicVersion = "2023-06-01", allowInsecureLocalHttp = true)
        val provider = ConfiguredAiProvider("local-messages", local, client { outgoing ->
            calls++; assertEquals(local.endpoint, outgoing.url.toString())
            assertEquals("2023-06-01", outgoing.headers["anthropic-version"])
            assertEquals("fixture", outgoing.headers["x-api-key"])
            val body = Json.parseToJsonElement(wire(outgoing.body)).jsonObject
            assertEquals(100, body.getValue("max_tokens").jsonPrimitive.int)
            assertFalse("max_completion_tokens" in body)
            respond(named, HttpStatusCode.OK, responseHeaders())
        }, { true }, { headersOf("x-api-key", "fixture") })
        try {
            assertEquals(listOf(AiCoworkEvent.TextDelta("ok"), AiCoworkEvent.Completed), provider.stream(request).toList())
            assertEquals(1, calls)
        } finally { provider.close() }
    }

    @Test fun deniedInvalidAndFailedPreflightNeverDispatchOrLeakSecrets() = runTest {
        for (mode in listOf("deny", "invalid", "approval-failure", "credentials-failure", "forbidden-header")) {
            var calls = 0; var credentials = 0; var approvals = 0
            val provider = ConfiguredAiProvider("chat", server, client { calls++; respond(chat) }, {
                approvals++; if (mode == "approval-failure") error("private URL and key")
                mode != "deny"
            }, {
                credentials++; if (mode == "credentials-failure") error("fixture-secret")
                if (mode == "forbidden-header") headersOf(HttpHeaders.Host, "other.invalid") else Headers.Empty
            })
            try {
                val failed = assertIs<AiCoworkEvent.Failed>(provider.stream(if (mode == "invalid") request.copy(prompt = "") else request).toList().single())
                assertFalse(failed.retryable); assertFalse(failed.message.contains("private")); assertFalse(failed.message.contains("fixture-secret"))
                assertEquals(0, calls)
                if (mode in listOf("deny", "invalid", "approval-failure")) assertEquals(0, credentials)
                if (mode == "invalid") assertEquals(0, approvals)
            } finally { provider.close() }
        }
    }

    @Test fun invalidEndpointProfilesAreRejectedWithSafeMessage() {
        val invalid = listOf("/relative", "http://fixture.invalid/path", "https://user:secret@fixture.invalid/path",
            "https://fixture.invalid/path?key=secret", "https://fixture.invalid/path#secret", "ftp://fixture.invalid/path")
            .map { server.copy(endpoint = it) } + listOf(server.copy(endpoint = "http://localhost/path", location = AiServerLocation.Local),
                server.copy(body = server.body.copy(dialect = AiRequestDialect.AnthropicMessages)),
                server.copy(anthropicVersion = "2023-06-01"))
        invalid.forEach { profile ->
            val http = client { error("must not dispatch") }
            try {
                assertEquals("AI request is invalid", assertFailsWith<AiRequestValidationException> {
                    ConfiguredAiProvider("test", profile, http, { true }, { Headers.Empty })
                }.message)
            } finally { http.close() }
        }
    }

    @Test fun cancellationDuringConsentAndConsumerFailureRemainTransparent() = runTest {
        var calls = 0; var credentials = 0
        val waiting = CompletableDeferred<Unit>()
        val provider = ConfiguredAiProvider("chat", server, client { calls++; respond(chat, HttpStatusCode.OK, responseHeaders()) }, {
            waiting.complete(Unit); awaitCancellation()
        }, { credentials++; Headers.Empty })
        try {
            val job = launch { provider.stream(request).collect {} }
            waiting.await(); job.cancelAndJoin()
            assertEquals(0, calls); assertEquals(0, credentials)
        } finally { provider.close() }
        val channel = ByteReadChannel(chat)
        val approved = ConfiguredAiProvider("chat", server, client { respond(channel, HttpStatusCode.OK, responseHeaders()) }, { true }, { Headers.Empty })
        val failure = IllegalStateException("consumer")
        try {
            assertSame(failure, assertFailsWith<IllegalStateException> { approved.stream(request).collect { throw failure } })
            assertTrue(channel.isClosedForRead)
        } finally { approved.close() }
    }

    @Test fun collectionSnapshotSurvivesSourceMutationAndHttpFailureDoesNotRetry() = runTest {
        val objects = mutableListOf(AiContextObject("a", "text", 1, "approved-text"))
        val selected = request.copy(context = request.context.copy(scope = AiContextScope.Selection, objects = objects))
        var calls = 0
        val provider = ConfiguredAiProvider("chat", server, client { outgoing ->
            calls++; assertTrue(wire(outgoing.body).contains("approved-text")); assertFalse(wire(outgoing.body).contains("changed-text"))
            respond("secret-server-error", HttpStatusCode.ServiceUnavailable, headersOf(HttpHeaders.ContentType, "application/json"))
        }, { objects[0] = objects[0].copy(content = "changed-text"); true }, { Headers.Empty })
        try {
            val failed = assertIs<AiCoworkEvent.Failed>(provider.stream(selected).toList().single())
            assertTrue(failed.retryable); assertFalse(failed.message.contains("secret-server-error")); assertEquals(1, calls)
        } finally { provider.close() }
    }

    @Test fun cancellationWhileReadingResponseClosesBodyWithoutTerminalOrRetry() = runTest {
        val channel = ByteChannel()
        val received = CompletableDeferred<Unit>()
        var calls = 0
        channel.writeStringUtf8(chat.substringBefore("\n\n").replace("\"finish_reason\":\"stop\"", "\"finish_reason\":null") + "\n\n")
        channel.flush()
        val provider = ConfiguredAiProvider("chat", server, client {
            calls++
            respond(channel, HttpStatusCode.OK, responseHeaders())
        }, { true }, { Headers.Empty })
        val seen = mutableListOf<AiCoworkEvent>()
        try {
            val job = launch { provider.stream(request).collect { seen += it; received.complete(Unit) } }
            received.await()
            job.cancelAndJoin()
            assertEquals(listOf<AiCoworkEvent>(AiCoworkEvent.TextDelta("ok🙂")), seen); assertEquals(1, calls)
            assertTrue(channel.isClosedForRead); assertTrue(channel.isClosedForWrite)
        } finally { provider.close() }
    }
}
