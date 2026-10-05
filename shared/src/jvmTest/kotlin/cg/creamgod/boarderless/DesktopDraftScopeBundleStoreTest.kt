package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.*

class DesktopDraftScopeBundleStoreTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val journal = WorkspaceDraftJournal(scope = scope, id = "draft", baseVersion = 8, baseServerSeq = 19,
        baseWorkspace = Workspace(WorkspaceId("workspace"), "🙂 Fixture"), operations = emptyList(),
        lastAcknowledgedTransactionId = "ack-marker")
    private val bundle = WorkspaceDraftScopeBundle(scope = scope, journal = journal)
    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-typed-draft-test-").toRealPath()
        try { action(root) }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }

    @Test fun reopenPreservesJournalAcknowledgementAndGenerationTombstone() = fixture { root ->
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        assertEquals(bundle, store.compareAndSet(scope, null, bundle).bundle)
        val reopened = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        assertEquals(StoredDraftScopeBundle(1, bundle), reopened.read(scope))
        assertFailsWith<AtomicDraftConflictException> { store.compareAndSet(scope, null, bundle) }
        assertEquals(StoredDraftScopeBundle(2, null), store.compareAndSet(scope, 1, null))
        assertEquals(StoredDraftScopeBundle(2, null), reopened.read(scope))
        assertNull(reopened.read(scope.copy(clientId = "other")))
    }

    @Test fun unknownTypedSchemaCannotBeReplacedOrDeleted() = fixture { root ->
        val raw = DesktopAtomicDraftStore(root)
        val bytes = WorkspaceDraftScopeBundleCodec.encode(bundle).decodeToString()
            .replaceFirst("\"version\":1", "\"version\":99").encodeToByteArray()
        val hash = WorkspaceDraftScopeBundleCodec.scopeHash(scope)
        raw.compareAndSet(hash, null, bytes)
        val typed = DesktopDraftScopeBundleStore(raw)
        assertFailsWith<IllegalArgumentException> { typed.read(scope) }
        assertFailsWith<IllegalArgumentException> { typed.compareAndSet(scope, 1, bundle) }
        assertFailsWith<IllegalArgumentException> { typed.compareAndSet(scope, 1, null) }
        assertContentEquals(bytes, raw.read(hash)!!.payload)
        assertEquals(1L, raw.read(hash)!!.generation)
    }

    @Test fun mismatchedScopeHeadAndUnsafeSequencesNeverPublish() = fixture { root ->
        val files = DesktopAtomicDraftStore(root)
        val typed = DesktopDraftScopeBundleStore(files)
        for (invalid in listOf(bundle.copy(scope = scope.copy(userId = "other")),
            bundle.copy(journal = journal.copy(scope = scope.copy(clientId = "other"))),
            bundle.copy(journal = journal.copy(headTransactionId = "missing-wire")),
            bundle.copy(journal = journal.copy(baseServerSeq = -1)),
            bundle.copy(journal = null))) {
            assertFails { typed.compareAndSet(scope, null, invalid) }
            assertNull(files.read(WorkspaceDraftScopeBundleCodec.scopeHash(scope)))
        }
        typed.compareAndSet(scope, null, bundle)
        assertEquals(bundle, typed.read(scope)!!.bundle)
    }

    @Test fun invalidUtf8DepthAndWrongScopeStopBeforeDecode() {
        assertFails { WorkspaceDraftScopeBundleCodec.decode(byteArrayOf(0xc3.toByte(), 0x28), scope) }
        assertFails { WorkspaceDraftScopeBundleCodec.decode(("[".repeat(70) + "0" + "]".repeat(70)).encodeToByteArray(), scope) }
        assertFails { WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(bundle), scope.copy(userId = "other")) }
        assertEquals(bundle, WorkspaceDraftScopeBundleCodec.decode(WorkspaceDraftScopeBundleCodec.encode(bundle), scope))
    }

    @Test fun oneShotTransitionDoesNotRunForStaleGenerationOrPublishOnFailure() = fixture { root ->
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        store.update(scope, null) { assertNull(it); bundle }
        var invoked = false
        assertFailsWith<AtomicDraftConflictException> {
            store.update(scope, null) { invoked = true; bundle }
        }
        assertFalse(invoked)
        assertFailsWith<IllegalStateException> { store.update(scope, 1) { error("fixture transition rejected") } }
        assertEquals(StoredDraftScopeBundle(1, bundle), store.read(scope))
        val updated = bundle.copy(journal = journal.copy(quarantined = true))
        assertEquals(StoredDraftScopeBundle(2, updated), store.update(scope, 1) { updated })
        assertEquals(StoredDraftScopeBundle(2, updated), DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).read(scope))
    }

    @Test fun exactDraftRemovalCannotDeleteNewGenerationOrAnotherDraft() = fixture { root ->
        val store = DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root))
        store.compareAndSet(scope, null, bundle)
        assertFails { store.removeDraft(scope, 1, "other-draft") }
        assertEquals(StoredDraftScopeBundle(1, bundle), store.read(scope))
        val next = bundle.copy(journal = journal.copy(id = "new-draft"))
        store.compareAndSet(scope, 1, next)
        assertFailsWith<AtomicDraftConflictException> { store.removeDraft(scope, 1, "draft") }
        assertFails { store.removeDraft(scope, 2, "draft") }
        assertEquals(StoredDraftScopeBundle(2, next), store.read(scope))
        assertEquals(StoredDraftScopeBundle(3, null), store.removeDraft(scope, 2, "new-draft"))
        assertEquals(StoredDraftScopeBundle(3, null), DesktopDraftScopeBundleStore(DesktopAtomicDraftStore(root)).read(scope))
    }
}
