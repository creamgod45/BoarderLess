package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.mediaActivityAllowsPublication
import cg.creamgod.boarderless.domain.media.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import androidx.compose.ui.unit.IntSize
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class GiphyVisibleReferencesTest {
    private fun ref(id: String) = ExternalMediaReference(ExternalMediaProvider.Giphy, id)

    @Test fun visibleDuplicatesUseHundredIdBatchesAndUnchangedVisibilityDoesNotSpendAgain() = runTest {
        val requests = mutableListOf<List<String>>()
        val client = GiphyClient("fixture", HttpClient(MockEngine { request ->
            val ids = checkNotNull(request.url.parameters["ids"]).split(',')
            requests.add(ids)
            respond("""{"data":[${ids.reversed().joinToString { "{\"id\":\"$it\"}" }}],"meta":{"status":200}}""")
        }), GiphyRequestBudget({ 0 }))
        try {
            val resolver = GiphyVisibleReferences()
            val references = List(101) { ref("gif$it") }.toSet()
            resolver.replaceVisible(references + ref("gif0"))
            resolver.resolvePending(client::resolveReferences)
            assertEquals(listOf(100, 1), requests.map { it.size })
            assertEquals(references, resolver.entries.value.keys)
            assertTrue(resolver.entries.value.values.all { it is GiphyReferenceResolution.Ready })
            resolver.replaceVisible(references)
            resolver.resolvePending(client::resolveReferences)
            assertEquals(2, requests.size)
            resolver.replaceVisible(setOf(ref("gif0")))
            assertEquals(setOf(ref("gif0")), resolver.entries.value.keys)
            resolver.replaceVisible(emptySet())
            assertTrue(resolver.entries.value.isEmpty())
        } finally { client.close() }
    }

    @Test fun changedVisibilityFencesLateRepliesAndMissingContentOnlyReturnsOnExplicitRetry() = runTest {
        val resolver = GiphyVisibleReferences()
        val a = ref("a"); val b = ref("b"); val c = ref("c")
        resolver.replaceVisible(setOf(a, b))
        val reply = CompletableDeferred<Unit>()
        val old = launch { resolver.resolvePending { reply.await(); listOf(GiphyItem("a"), GiphyItem("b", url = "old")) } }
        runCurrent()
        resolver.replaceVisible(setOf(b, c))
        reply.complete(Unit); old.join()
        assertEquals(setOf(b, c), resolver.entries.value.keys)
        assertTrue(resolver.entries.value.values.all { it == GiphyReferenceResolution.Pending })
        resolver.resolvePending { listOf(GiphyItem("b", url = "fresh")) }
        assertEquals("fresh", (resolver.entries.value[b] as GiphyReferenceResolution.Ready).item.url)
        assertEquals(GiphyReferenceResolution.Missing, resolver.entries.value[c])
        resolver.resolvePending { error("missing content must not automatically retry") }
        resolver.retry(c)
        resolver.resolvePending { batch -> assertEquals(listOf(c), batch); listOf(GiphyItem("c")) }
        assertTrue(resolver.entries.value[c] is GiphyReferenceResolution.Ready)
    }

    @Test fun rateLimitStopsRemainingBatchesAndCancellationIsNotAnUnavailableResult() = runTest {
        val resolver = GiphyVisibleReferences()
        resolver.replaceVisible(List(201) { ref("g$it") }.toSet())
        var calls = 0
        resolver.resolvePending { calls++; throw GiphyException(GiphyIssue.RateLimited) }
        assertEquals(1, calls)
        assertTrue(resolver.entries.value.values.all { it == GiphyReferenceResolution.Failed(GiphyIssue.RateLimited) })
        resolver.resolvePending { error("no automatic retry after failure") }
        resolver.retry(ref("g0"))
        assertFailsWith<CancellationException> { resolver.resolvePending { throw CancellationException("obsolete view") } }
        assertEquals(GiphyReferenceResolution.Pending, resolver.entries.value[ref("g0")])
    }

    @Test fun rapidBackgroundForegroundFencesLateReplyEvenWithoutObservingTheUnavailableState() = runTest {
        val activity = MediaActivityTracker()
        activity.setAvailable(true)
        val resolver = GiphyVisibleReferences()
        val reference = ref("gif")
        resolver.replaceVisible(setOf(reference))
        val openedEpoch = activity.activity.value.epoch
        val reply = CompletableDeferred<Unit>()
        val old = launch {
            resolver.resolvePendingWhenActive({ mediaActivityAllowsPublication(activity.activity.value, openedEpoch) }) {
                reply.await(); listOf(GiphyItem("gif", url = "obsolete"))
            }
        }
        runCurrent()
        activity.setAvailable(false); activity.setAvailable(true)
        reply.complete(Unit); old.join()
        assertTrue(resolver.entries.value.isEmpty())
        resolver.replaceVisible(setOf(reference))
        val resumedEpoch = activity.activity.value.epoch
        resolver.resolvePendingWhenActive({ mediaActivityAllowsPublication(activity.activity.value, resumedEpoch) }) {
            listOf(GiphyItem("gif", url = "fresh"))
        }
        assertEquals("fresh", (resolver.entries.value[reference] as GiphyReferenceResolution.Ready).item.url)
        activity.setAvailable(false)
        resolver.resolvePendingWhenActive({ activity.activity.value.available }) { error("background cannot start lookup") }
        assertTrue(resolver.entries.value.isEmpty())
        val node = ExternalMediaNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(40f, 40f)), reference = reference)
        assertTrue(visibleGiphyReferences(listOf(node), Viewport(), IntSize(100, 100), emptyMap(), activity.activity.value).isEmpty())
        activity.close()
    }

    @Test fun viewportDeduplicatesIdentitiesAndUsesPreviewTransformsBeforeSendingRequests() {
        val reference = ref("same")
        fun node(id: String, x: Float) = ExternalMediaNode(CanvasObjectId(id), transform = CanvasTransform(Vec2(x, 0f), CanvasSize(40f, 40f)), reference = reference)
        val visible = node("visible", 10f); val duplicate = node("duplicate", 20f); val outside = node("outside", 1000f)
        assertEquals(setOf(reference), visibleGiphyReferences(listOf(visible, duplicate, outside), Viewport(), IntSize(100, 100), emptyMap()))
        assertTrue(visibleGiphyReferences(listOf(outside), Viewport(), IntSize(100, 100), emptyMap()).isEmpty())
        assertEquals(setOf(reference), visibleGiphyReferences(listOf(outside), Viewport(), IntSize(100, 100), mapOf(outside.id to visible.transform)))
        assertTrue(visibleGiphyReferences(listOf(visible), Viewport(), IntSize.Zero, emptyMap()).isEmpty())
    }
    @Test fun externalMediaDragUsesTheSamePreviewForVisibilityRelationRenderingAndHitTesting() {
        val source = ExternalMediaNode(CanvasObjectId("source"),
            transform = CanvasTransform(Vec2(10f, 10f), CanvasSize(40f, 40f)), reference = ref("source"))
        val target = source.copy(id = CanvasObjectId("target"),
            transform = source.transform.copy(position = Vec2(70f, 10f)), reference = ref("target"))
        val nodes = mapOf<CanvasObjectId, CanvasObject>(source.id to source, target.id to target)
        val relation = Relation(RelationId("edge"), sourceObjectId = source.id, targetObjectId = target.id)
        val viewport = Viewport()
        val offset = Vec2(500f, 400f)
        val dragging = nodes.keys.associateWith { offset }
        val original = assertNotNull(relationSegment(relation, nodes, viewport)).points
        val preview = assertNotNull(relationSegment(relation, nodes, viewport, dragging)).points
        assertEquals(original.map { it + offset }, preview)
        val midpoint = (preview.first() + preview.last()) * 0.5f
        assertEquals(relation, findRelationAt(midpoint, listOf(relation), nodes, viewport, 2f, dragging))
        assertNull(findRelationAt(midpoint, listOf(relation), nodes, viewport, 2f))
        assertTrue(visibleGiphyReferences(listOf(source, target), viewport, IntSize(150, 150), emptyMap(),
            dragPreviews = dragging).isEmpty())
        // Cancel removes the transient offsets; the durable objects and provider IDs never moved.
        assertEquals(setOf(source.reference, target.reference),
            visibleGiphyReferences(listOf(source, target), viewport, IntSize(150, 150), emptyMap()))
        assertEquals(original, assertNotNull(relationSegment(relation, nodes, viewport)).points)
    }

}
