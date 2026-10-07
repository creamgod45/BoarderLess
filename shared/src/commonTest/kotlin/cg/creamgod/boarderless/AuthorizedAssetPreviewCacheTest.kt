package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AuthorizedAssetPreviewCacheTest {
    private fun session(user: String = "viewer", workspace: String = "w") = WorkspaceSession(
        user, "client", WorkspaceMemberRole.Viewer, 0, 0, Workspace(WorkspaceId(workspace), "Preview"))
    private fun cache(bytes: Long = 20, entries: Int = 64) = AuthorizedAssetPreviewCache<String>(bytes, entries) { it.length.toLong() }
    private class Gateway : AssetDownloadGateway {
        var calls = 0
        var deny = false
        var hash = "sha256:abc"
        override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
            calls++
            check(!deny) { "403" }
            return AssetDownloadTicket(WorkspaceAsset(assetId, session.workspace.id, "owner", "image/png", 3,
                hash, 1, 1, null, AssetStatus.Ready, "2026-10-01"), "https://storage.invalid/${calls}")
        }
        override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = error("Unused")
    }

    @Test fun cacheHitStillAuthorizesAndSignedUrlRotationDoesNotMiss() = runTest {
        val c = cache(); val g = Gateway(); var decodes = 0
        repeat(2) { assertEquals("pixels", c.load(g, session(), "a") { decodes++; "pixels" }) }
        assertEquals(2, g.calls); assertEquals(1, decodes)
        g.deny = true
        assertFailsWith<IllegalStateException> { c.load(g, session(), "a") { "bad" } }
        assertEquals(1, decodes)
    }
    @Test fun userWorkspaceAndChecksumAreIsolated() = runTest {
        val c = cache(); val g = Gateway(); var decodes = 0
        suspend fun load(s: WorkspaceSession) = c.load(g, s, "a") { "${++decodes}" }
        assertEquals("1", load(session()))
        assertEquals("2", load(session(user = "other")))
        assertEquals("3", load(session(workspace = "other")))
        g.hash = "sha256:new"
        assertEquals("4", load(session()))
    }
    @Test fun lruUsesBytesAndEntryCount() = runTest {
        val g = Gateway()
        listOf(cache(bytes = 2), cache(entries = 2)).forEach { c ->
            var decodes = 0
            suspend fun load(id: String) = c.load(g, session(), id) { decodes++; id }
            load("a"); load("b"); load("a"); load("c"); load("a")
            assertEquals(3, decodes)
            load("b"); assertEquals(4, decodes)
        }
    }
    @Test fun oversizedValuesAreReturnedWithoutRetention() = runTest {
        val c = cache(bytes = 1); val g = Gateway(); var decodes = 0
        repeat(2) { assertEquals("large", c.load(g, session(), "a") { decodes++; "large" }) }
        assertEquals(2, decodes)
    }
    @Test fun concurrentSameAssetDecodesOnlyOnce() = runTest {
        val c = cache(); val g = Gateway(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        var decodes = 0
        val first = async { c.load(g, session(), "a") { decodes++; started.complete(Unit); finish.await(); "pixels" } }
        started.await()
        val second = async { c.load(g, session(), "a") { decodes++; "pixels" } }
        yield(); finish.complete(Unit)
        assertEquals("pixels", first.await()); assertEquals("pixels", second.await())
        assertEquals(2, g.calls); assertEquals(1, decodes)
    }
    @Test fun failureOrCancellationDoesNotPoisonRetry() = runTest {
        val c = cache(); val g = Gateway()
        assertFailsWith<IllegalStateException> { c.load(g, session(), "a") { error("decode") } }
        assertFailsWith<CancellationException> { c.load(g, session(), "a") { throw CancellationException("cancel") } }
        assertEquals("fresh", c.load(g, session(), "a") { "fresh" })
    }
    @Test fun clearWhileLoadingPreventsLateRepopulation() = runTest {
        val c = cache(); val g = Gateway(); val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val first = async { c.load(g, session(), "a") { started.complete(Unit); finish.await(); "old" } }
        started.await(); c.clear(); finish.complete(Unit); first.await()
        assertEquals("fresh", c.load(g, session(), "a") { "fresh" })
        c.clear()
        assertEquals("again", c.load(g, session(), "a") { "again" })
    }
    @Test fun clearDuringAuthorizationAlsoPreventsLateRepopulation() = runTest {
        val c = cache(); val delegate = Gateway()
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val gateway = object : AssetDownloadGateway by delegate {
            override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
                started.complete(Unit); finish.await()
                return delegate.authorize(session, assetId)
            }
        }
        val first = async { c.load(gateway, session(), "a") { "old" } }
        started.await(); c.clear(); finish.complete(Unit); first.await()
        assertEquals("fresh", c.load(delegate, session(), "a") { "fresh" })
    }
    @Test fun cancelledJobCannotCacheANonCooperativeDecodeResult() = runTest {
        val c = cache(); val g = Gateway()
        val started = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val first = async {
            c.load(g, session(), "a") {
                withContext(NonCancellable) { started.complete(Unit); finish.await() }
                "late"
            }
        }
        started.await(); first.cancel(); finish.complete(Unit); first.join()
        assertEquals("fresh", c.load(g, session(), "a") { "fresh" })
    }
}
