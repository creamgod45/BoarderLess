package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.feature.canvas.giphyResultsAfterPage
import cg.creamgod.boarderless.feature.canvas.GiphyGridScrollRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GiphyClientTest {
    @Test fun nextPageUsesProviderOffsetAndTargetsFirstNewResultWithoutDroppingOverlap() = runTest {
        val offsets = mutableListOf<Int>()
        val client = GiphyClient("fixture", HttpClient(MockEngine { request ->
            val offset = requireNotNull(request.url.parameters["offset"]).toInt()
            offsets += offset
            val ids = if (offset == 0) listOf("a", "b") else listOf("b", "c")
            respond("""{"data":[{"id":"${ids[0]}"},{"id":"${ids[1]}"}],"pagination":{"offset":$offset,"count":2,"total_count":4},"meta":{"status":200}}""")
        }), GiphyRequestBudget({ 0 }))
        try {
            val first = client.browse("cat")
            val initial = giphyResultsAfterPage(emptyList(), first)
            val next = client.browse("cat", requireNotNull(first.nextOffset))
            val appended = giphyResultsAfterPage(initial.items, next)
            assertEquals(listOf(0, 2), offsets)
            assertEquals(listOf("a", "b", "b", "c"), appended.items.map { it.id })
            assertEquals(2, appended.firstNewIndex)
            assertNull(next.nextOffset)
        } finally { client.close() }
    }

    @Test fun freshSearchTargetsTopEvenWithSameCountAndEmptyAppendKeepsViewport() {
        val existing = listOf(GiphyItem("a"), GiphyItem("b"))
        val fresh = giphyResultsAfterPage(existing, GiphyPage(listOf(GiphyItem("c"), GiphyItem("d")),
            GiphyPagination(0, 2, 4), 2))
        assertEquals(0, fresh.firstNewIndex)
        assertEquals(listOf("c", "d"), fresh.items.map { it.id })
        assertNotEquals(GiphyGridScrollRequest(1, 0), GiphyGridScrollRequest(2, 0))
        val empty = giphyResultsAfterPage(existing, GiphyPage(emptyList(), GiphyPagination(2, 0, 2), null))
        assertEquals(existing, empty.items)
        assertNull(empty.firstNewIndex)
        assertFailsWith<IllegalArgumentException> {
            giphyResultsAfterPage(existing, GiphyPage(listOf(GiphyItem("c")), GiphyPagination(1, 1, 4), 2))
        }
    }
    private val payload = """{"data":[{"id":"b","title":"B","images":{}},{"id":"a","title":"A","images":{}}],"pagination":{"offset":0,"count":2,"total_count":10},"meta":{"status":200}}"""
    @Test fun directSearchEncodesExactlyOncePreservesQueryOrderAndPagination() = runTest {
        val key = "fixture-key"
        val query = "貓 & dog + @artist"
        val engine = MockEngine { request ->
            assertEquals("api.giphy.com", request.url.host)
            assertEquals("/v1/gifs/search", request.url.encodedPath)
            assertEquals(query, request.url.parameters["q"])
            assertEquals(key, request.url.parameters["api_key"])
            assertEquals("g", request.url.parameters["rating"])
            assertEquals("zh-TW", request.url.parameters["lang"])
            assertEquals("20", request.url.parameters["limit"])
            respond(payload, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = GiphyClient(key, HttpClient(engine), GiphyRequestBudget({ 0 }))
        try {
            val page = client.browse(query, language = "zh-TW")
            assertEquals(listOf("b", "a"), page.items.map { it.id })
            assertEquals(2, page.nextOffset)
        } finally { client.close() }
    }
    @Test fun invalidQueryIsRejectedBeforeNetworkOrBudgetUse() = runTest {
        var calls = 0
        val client = GiphyClient("fixture", HttpClient(MockEngine { calls++; respond(payload) }), GiphyRequestBudget({ 0 }))
        try {
            listOf("", " ", "a".repeat(51)).forEach { query ->
                assertEquals(GiphyIssue.InvalidQuery, assertFailsWith<GiphyException> { client.browse(query) }.issue)
            }
            assertEquals(0, calls)
        } finally { client.close() }
    }
    @Test fun hundredCallRollingBudgetIsSharedByKeyAndExpiresWithoutAutomaticRetry() = runTest {
        var now = 0L
        val budget = GiphyRequestBudget({ now })
        repeat(100) { budget.reserve("same") }
        assertEquals(GiphyIssue.RateLimited, assertFailsWith<GiphyException> { budget.reserve("same") }.issue)
        budget.reserve("other")
        now = 3_600_000
        budget.reserve("same")
    }
    @Test fun rateLimitedResponseStopsLaterNetworkRequestsAndDoesNotExposeKey() = runTest {
        var now = 0L
        var calls = 0
        val engine = MockEngine { calls++; respond("", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "60")) }
        val client = GiphyClient("secret-fixture", HttpClient(engine), GiphyRequestBudget({ now }))
        try {
            repeat(2) {
                val error = assertFailsWith<GiphyException> { client.browse(null) }
                assertEquals(GiphyIssue.RateLimited, error.issue)
                assertFalse(error.message.orEmpty().contains("secret-fixture"))
            }
            assertEquals(1, calls)
            now = 60_000
            assertFailsWith<GiphyException> { client.browse(null) }
            assertEquals(2, calls)
        } finally { client.close() }
    }
    @Test fun metadataMismatchAndCancellationFailClosed() = runTest {
        val malformed = GiphyClient("fixture", HttpClient(MockEngine { respond(payload.replace("\"count\":2", "\"count\":3")) }), GiphyRequestBudget({ 0 }))
        try { assertEquals(GiphyIssue.InvalidResponse, assertFailsWith<GiphyException> { malformed.browse(null) }.issue) }
        finally { malformed.close() }
        val cancelled = GiphyClient("fixture", HttpClient(MockEngine { throw CancellationException("Obsolete query") }), GiphyRequestBudget({ 0 }))
        try { assertFailsWith<CancellationException> { cancelled.browse(null) } }
        finally { cancelled.close() }
    }
    @Test fun providerUrlValidationRejectsProxyCredentialsAndLookalikeDomainsWithoutRewriting() {
        assertTrue(isGiphyMediaUrl("https://media2.giphy.com/media/id/200w_s.gif?cid=test&rid=200w_s.gif"))
        listOf("http://media2.giphy.com/a", "https://media2.giphy.com.evil.test/a", "https://user:pass@media2.giphy.com/a", "https://localhost/a")
            .forEach { assertFalse(isGiphyMediaUrl(it)) }
    }
    @Test fun lookupBatchesReferencesAndKeepsProviderOrderAndMissingIds() = runTest {
        var calls = 0
        val client = GiphyClient("fixture", HttpClient(MockEngine { request ->
            calls++
            assertEquals("api.giphy.com", request.url.host)
            assertEquals("/v1/gifs", request.url.encodedPath)
            assertEquals("a,b,removed", request.url.parameters["ids"])
            assertEquals("g", request.url.parameters["rating"])
            respond("""{"data":[{"id":"b"},{"id":"a"}],"meta":{"status":200}}""")
        }), GiphyRequestBudget({ 0 }))
        try {
            assertEquals(listOf("b", "a"), client.resolve(listOf("a", "b", "removed")).map { it.id })
            assertEquals(1, calls)
        } finally { client.close() }
    }
    @Test fun lookupRejectsInvalidIdsBeforeSpendingBudgetAndAllowsHundredIds() = runTest {
        var calls = 0
        val client = GiphyClient("fixture", HttpClient(MockEngine {
            calls++
            respond("""{"data":[],"meta":{"status":200}}""")
        }), GiphyRequestBudget({ 0 }, limit = 1))
        try {
            listOf(emptyList(), listOf(""), listOf("a,b"), listOf("a\n"), List(101) { "id$it" }).forEach {
                assertEquals(GiphyIssue.InvalidQuery, assertFailsWith<GiphyException> { client.resolve(it) }.issue)
            }
            assertEquals(0, calls)
            assertTrue(client.resolve(List(100) { "id$it" }).isEmpty())
            assertEquals(1, calls)
        } finally { client.close() }
    }
    @Test fun lookupRejectsForeignDuplicateAndFailedMetadata() = runTest {
        listOf(
            """{"data":[{"id":"foreign"}],"meta":{"status":200}}""",
            """{"data":[{"id":"a"},{"id":"a"}],"meta":{"status":200}}""",
            """{"data":[],"meta":{"status":404}}""",
        ).forEach { body ->
            val client = GiphyClient("fixture", HttpClient(MockEngine { respond(body) }), GiphyRequestBudget({ 0 }))
            try {
                assertEquals(GiphyIssue.InvalidResponse, assertFailsWith<GiphyException> { client.resolve(listOf("a", "b")) }.issue)
            } finally { client.close() }
        }
    }
    @Test fun lookupAndBrowseShareRateLimitAndCancelledLookupDoesNotRetry() = runTest {
        var calls = 0
        val budget = GiphyRequestBudget({ 0 })
        val client = GiphyClient("fixture", HttpClient(MockEngine {
            calls++; respond("", HttpStatusCode.TooManyRequests)
        }), budget)
        try {
            assertEquals(GiphyIssue.RateLimited, assertFailsWith<GiphyException> { client.resolve(listOf("a")) }.issue)
            assertEquals(GiphyIssue.RateLimited, assertFailsWith<GiphyException> { client.browse(null) }.issue)
            assertEquals(1, calls)
        } finally { client.close() }
        val cancelled = GiphyClient("fixture2", HttpClient(MockEngine { throw CancellationException("gone") }), budget)
        try { assertFailsWith<CancellationException> { cancelled.resolve(listOf("a")) } }
        finally { cancelled.close() }
    }
}
