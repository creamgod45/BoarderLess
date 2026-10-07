package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GiphyMediaClientTest {
    private val url = "https://media2.giphy.com/media/example/200w_s.gif?cid=keep%20this&rid=200w_s.gif"

    @Test fun directMediaPreservesUrlAndHasNoWorkspaceCredentialsOrReusableCache() = runTest {
        val bytes = ByteArray(40_000) { (it % 251).toByte() }
        var calls = 0
        val client = GiphyMediaClient(HttpClient(MockEngine { request ->
            calls++
            assertEquals(url, request.url.toString())
            assertNull(request.headers[HttpHeaders.Authorization])
            assertNull(request.headers[HttpHeaders.Cookie])
            assertNull(request.url.parameters["api_key"])
            respond(bytes, headers = headersOf(HttpHeaders.ContentType, "image/gif"))
        }))
        try {
            repeat(2) { assertContentEquals(bytes, client.downloadStill(url)) }
            assertEquals(2, calls)
        } finally { client.close() }
    }

    @Test fun rejectsForeignUrlsBeforeNetwork() = runTest {
        var calls = 0
        val client = GiphyMediaClient(HttpClient(MockEngine { calls++; respond(byteArrayOf(1)) }))
        try {
            listOf("http://media2.giphy.com/a", "https://media2.giphy.com.evil.test/a", "https://user:pass@media2.giphy.com/a").forEach {
                assertFailsWith<IllegalArgumentException> { client.downloadStill(it) }
            }
            assertEquals(0, calls)
        } finally { client.close() }
    }

    @Test fun boundsUnknownLengthBodyAndRejectsOversizeDeclarationOrNonImages() = runTest {
        val bodies = listOf(
            ByteArray(GiphyMediaClient.MaxStillBytes + 1) to headersOf(),
            byteArrayOf(1) to headersOf(HttpHeaders.ContentLength, "${GiphyMediaClient.MaxStillBytes + 1}"),
            byteArrayOf(1) to headersOf(HttpHeaders.ContentType, "text/html"),
            byteArrayOf() to headersOf(),
        )
        bodies.forEach { (body, headers) ->
            val client = GiphyMediaClient(HttpClient(MockEngine { respond(body, headers = headers) }))
            try {
                assertEquals(GiphyIssue.InvalidResponse, assertFailsWith<GiphyException> { client.downloadStill(url) }.issue)
            } finally { client.close() }
        }
    }

    @Test fun httpFailuresAreSanitizedAndCancellationDoesNotRetry() = runTest {
        var calls = 0
        val client = GiphyMediaClient(HttpClient(MockEngine {
            calls++; respond("private upstream details", HttpStatusCode.Forbidden)
        }))
        try {
            val error = assertFailsWith<GiphyException> { client.downloadStill(url) }
            assertEquals(GiphyIssue.Unavailable, error.issue)
            assertFalse(error.message.orEmpty().contains("cid="))
            assertEquals(1, calls)
        } finally { client.close() }
        val cancelled = GiphyMediaClient(HttpClient(MockEngine { throw CancellationException("obsolete") }))
        try { assertFailsWith<CancellationException> { cancelled.downloadStill(url) } }
        finally { cancelled.close() }
    }

    @Test fun redirectResponseIsNotAcceptedAsAnImage() = runTest {
        var calls = 0
        val client = GiphyMediaClient(HttpClient(MockEngine {
            calls++; respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://foreign.test/image"))
        }) { followRedirects = false })
        try {
            assertEquals(GiphyIssue.Unavailable, assertFailsWith<GiphyException> { client.downloadStill(url) }.issue)
            assertEquals(1, calls)
        } finally { client.close() }
    }
}
