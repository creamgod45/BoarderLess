package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.domain.ai.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AiSseHttpTransportTest {
    private val request = AiCoworkRequest("request", "prompt",
        AiContextSnapshot("workspace", 1, AiContextScope.PromptOnly, emptyList(), emptyList()))
    private fun client(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
        followRedirects = false
        install(HttpTimeout)
    }
    private fun transport(client: HttpClient) = AiSseHttpTransport(client) {
        url("https://fixture.invalid/approved-stream")
        method = HttpMethod.Post
        contentType(ContentType.Application.Json)
        setBody("{}") // Neutral fixture, NOT a provider request serializer.
        headers.append(HttpHeaders.Accept, "application/json")
    }
    private fun sseHeaders(type: String = "text/event-stream") = headersOf(HttpHeaders.ContentType, type)

    @Test fun requestIsColdAndEachCollectionGetsANewStreamingResponse() = runTest {
        var calls = 0
        val channels = mutableListOf<ByteReadChannel>()
        val transport = transport(client { outgoing ->
            calls++
            assertEquals(HttpMethod.Post, outgoing.method)
            assertEquals("/approved-stream", outgoing.url.encodedPath)
            assertEquals(listOf("text/event-stream"), outgoing.headers.getAll(HttpHeaders.Accept))
            val channel = ByteReadChannel("data:素材🙂\n\n").also { channels += it }
            respond(channel, HttpStatusCode.OK, sseHeaders("Text/Event-Stream; charset=UTF-8"))
        })
        try {
            val flow = transport.events(request)
            assertEquals(0, calls)
            repeat(2) { assertEquals(listOf(AiSseDataEvent("message", "素材🙂")), flow.toList()) }
            assertEquals(2, calls)
            assertTrue(channels.all { it.isClosedForRead })
        } finally { transport.close() }
    }

    @Test fun non200AndRedirectResponsesAreRejectedWithoutSecondRequest() = runTest {
        for (status in listOf(204, 301, 302, 307, 401, 403, 429, 500)) {
            var calls = 0
            val channel = ByteChannel()
            val transport = transport(client {
                calls++
                respond(channel, HttpStatusCode.fromValue(status), Headers.build {
                    append(HttpHeaders.ContentType, "text/event-stream")
                    append(HttpHeaders.Location, "https://other.invalid/secret")
                })
            })
            try {
                val failure = assertFailsWith<AiSseHttpResponseException> { transport.events(request).toList() }
                assertEquals("AI streaming response is invalid", failure.message)
                assertEquals(1, calls)
                assertTrue(channel.isClosedForRead)
                assertTrue(channel.isClosedForWrite)
            } finally { transport.close() }
        }
    }

    @Test fun invalidTypeCharsetAndDeclaredSizeFailBeforeBodyRead() = runTest {
        val invalid = listOf(Headers.Empty, sseHeaders("application/json"), sseHeaders("text/event-stream; charset=latin1"),
            headersOf(HttpHeaders.ContentType, listOf("text/event-stream", "text/event-stream")),
            sseHeaders("text/event-stream; charset=utf-8; charset=utf-8")) +
            listOf("-1", "broken", "8388609", "99999999999999999999999").map { length ->
                Headers.build { append(HttpHeaders.ContentType, "text/event-stream"); append(HttpHeaders.ContentLength, length) }
            }
        for (headers in invalid) {
            val channel = ByteChannel()
            val transport = transport(client { respond(channel, HttpStatusCode.OK, headers) })
            try {
                assertFailsWith<AiSseHttpResponseException> { transport.events(request).collect {} }
                assertTrue(channel.isClosedForRead)
            } finally { transport.close() }
        }
    }

    @Test fun providerTerminalStopsHttpBodyWithoutParsingTrailingInvalidBytes() = runTest {
        val stop = """{"id":"completion","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":"stop"}]}"""
        val channel = ByteReadChannel(("data:$stop\n\ndata:[DONE]\n\n").encodeToByteArray() + byteArrayOf(0xff.toByte()))
        val transport = transport(client { respond(channel, HttpStatusCode.OK, sseHeaders()) })
        try {
            val provider = OpenAiChatEventAdapter("fixture") { transport.events(it).map { event -> event.data } }
            assertEquals(listOf(AiCoworkEvent.TextDelta("ok"), AiCoworkEvent.Completed), provider.stream(request).toList())
            assertTrue(channel.isClosedForRead)
        } finally { transport.close() }
    }

    @Test fun cancellationAndConsumerErrorCloseWaitingHttpBody() = runTest {
        val channel = ByteChannel()
        val transport = transport(client { respond(channel, HttpStatusCode.OK, sseHeaders()) })
        try {
            val observed = CompletableDeferred<Unit>()
            val collector = launch { transport.events(request).collect { observed.complete(Unit) } }
            channel.writeFully("data:partial\n\n".encodeToByteArray()); channel.flush()
            observed.await(); collector.cancelAndJoin()
            assertTrue(channel.isClosedForRead)
            assertTrue(channel.isClosedForWrite)
        } finally { transport.close() }
        val other = ByteReadChannel("data:first\n\ndata:second\n\n")
        val second = transport(client { respond(other, HttpStatusCode.OK, sseHeaders()) })
        try {
            val failure = IllegalStateException("consumer")
            assertSame(failure, assertFailsWith<IllegalStateException> { second.events(request).collect { throw failure } })
            assertTrue(other.isClosedForRead)
        } finally { second.close() }
    }

    @Test fun networkAndResponseFailuresAreRedactedByProviderWithoutRetry() = runTest {
        for (networkFailure in listOf(true, false)) {
            var calls = 0
            val transport = transport(client {
                calls++
                if (networkFailure) throw IllegalStateException("https://secret.invalid?key=private")
                respond("private upstream error", HttpStatusCode.Unauthorized, sseHeaders())
            })
            try {
                val provider = OpenAiChatEventAdapter("fixture") { transport.events(it).map { event -> event.data } }
                assertEquals(listOf(AiCoworkEvent.Failed("AI stream connection failed", networkFailure)), provider.stream(request).toList())
                assertEquals(1, calls)
            } finally { transport.close() }
        }
    }

    @Test fun unsafeClientConfigurationIsRejectedBeforeRequests() {
        val redirects = HttpClient(MockEngine { error("Must not request") }) { install(HttpTimeout) }
        val noTimeout = HttpClient(MockEngine { error("Must not request") }) { followRedirects = false }
        val retry = HttpClient(MockEngine { error("Must not request") }) {
            followRedirects = false; install(HttpTimeout); install(HttpRequestRetry)
        }
        try {
            assertFailsWith<IllegalArgumentException> { transport(redirects) }
            assertFailsWith<IllegalArgumentException> { transport(noTimeout) }
            assertFailsWith<IllegalArgumentException> { transport(retry) }
        } finally { redirects.close(); noTimeout.close(); retry.close() }
    }
}
