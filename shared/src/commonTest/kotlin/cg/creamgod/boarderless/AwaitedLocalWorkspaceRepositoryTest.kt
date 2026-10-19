package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class AwaitedLocalWorkspaceRepositoryTest {
    private class Store : AwaitedDurableLocalRecordStore {
        var record: DurableLocalRecord? = null
        var writes = 0
        var beforeCas: suspend () -> Unit = {}
        var beforeConfirm: suspend () -> Unit = {}
        override suspend fun read(key: String) = record?.let { it.copy(payload = it.payload.copyOf()) }
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            beforeCas()
            check(record?.generation == generation) { "Competing durable generation" }
            record = DurableLocalRecord((generation ?: 0L) + 1, payload.copyOf())
            writes++
        }
        override suspend fun confirmDurable(key: String, generation: Long) {
            beforeConfirm()
            check(record?.generation == generation)
        }
    }
    private fun repository(store: Store) = AwaitedLocalWorkspaceRepository(store) { "2026-10-10T00:00:00Z" }
    private fun node(text: String = "中文🙂") = TextNode(CanvasObjectId("node"),
        transform = CanvasTransform(Vec2(20f, 30f), CanvasSize(80f, 40f)), text = text)

    @Test fun noSessionEscapesBeforeBothCasAndDurableConfirmationComplete() = runTest {
        val store = Store()
        val cas = CompletableDeferred<Unit>(); val confirm = CompletableDeferred<Unit>()
        store.beforeCas = { cas.await() }; store.beforeConfirm = { confirm.await() }
        val opened = async { repository(store).openOrCreateWorkspace() }
        runCurrent()
        assertFalse(opened.isCompleted); assertNull(store.record)
        cas.complete(Unit); runCurrent()
        assertNotNull(store.record); assertFalse(opened.isCompleted)
        confirm.complete(Unit)
        val session = opened.await()
        assertEquals(session.workspace, repository(store).openOrCreateWorkspace().workspace)
    }

    @Test fun unknownSaveConfirmationRetriesTheExactReceiptWithoutAnotherWriteAndReopensUndoRedo() = runTest {
        val store = Store(); val repo = repository(store); val session = repo.openOrCreateWorkspace()
        val operation = CreateObjectsOperation("same-id", listOf(node()))
        val created = WorkspaceHistory(session.workspace).execute(operation)
        store.beforeConfirm = { error("Confirmation unavailable after committed CAS") }
        assertFailsWith<IllegalStateException> { repo.retainDraftAwaited(session, session.workspace, operation) }
        val committedWrites = store.writes
        store.beforeConfirm = {}
        repo.retainDraftAwaited(session, session.workspace, operation)
        assertEquals(committedWrites, store.writes)
        var current = repo.refresh(session)
        assertEquals(created.history.workspace, current.workspace)
        assertIs<cg.creamgod.boarderless.data.SubmitOutcome.Accepted>(repo.submit(session, operation))
        assertFailsWith<IllegalArgumentException> {
            repo.retainDraftAwaited(session, session.workspace, CreateObjectsOperation("same-id", listOf(node("changed"))))
        }
        assertEquals(committedWrites, store.writes)
        val undone = created.history.undo(); assertTrue(undone.succeeded)
        repo.retainDraftAwaited(current, current.workspace, assertNotNull(undone.appliedOperation))
        current = repo.refresh(current)
        val redone = undone.history.redo(); assertTrue(redone.succeeded)
        repo.retainDraftAwaited(current, current.workspace, assertNotNull(redone.appliedOperation))
        assertEquals(redone.history.workspace, repository(store).openOrCreateWorkspace().workspace)
        assertFailsWith<IllegalStateException> { repo.retainDraft(current, current.workspace, operation) }
    }

    @Test fun competingRepositoriesCannotOverwriteACommittedDocument() = runTest {
        val store = Store(); val initial = repository(store).openOrCreateWorkspace()
        val release = CompletableDeferred<Unit>()
        store.beforeCas = { release.await() }
        val first = async { runCatching { repository(store).createWorkspace(initial, "One") } }
        val second = async { runCatching { repository(store).createWorkspace(initial, "Two") } }
        runCurrent(); release.complete(Unit)
        val outcomes = listOf(first.await(), second.await())
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.isFailure })
        val reopened = repository(store).openOrCreateWorkspace()
        assertEquals(2, repository(store).listWorkspaces(reopened).size)
        assertEquals(outcomes.single { it.isSuccess }.getOrThrow().workspace, reopened.workspace)
    }

    @Test fun cancellationClosedScopeAndInvalidUtf8NeverReturnUncommittedStateOrResetDocuments() = runTest {
        val store = Store(); val repo = repository(store); val initial = repo.openOrCreateWorkspace()
        val writes = store.writes
        val release = CompletableDeferred<Unit>(); store.beforeCas = { release.await() }
        val cancelled = async { repo.createWorkspace(initial, "Cancelled") }
        runCurrent(); cancelled.cancelAndJoin()
        assertEquals(writes, store.writes)
        store.beforeCas = {}; repo.close()
        assertFailsWith<IllegalStateException> { repo.refresh(initial) }
        val good = assertNotNull(store.record)
        store.record = good.copy(payload = byteArrayOf(0xff.toByte()))
        assertFails { repository(store).openOrCreateWorkspace() }
        assertEquals(writes, store.writes)
        store.record = good
        assertEquals(initial.workspace, repository(store).openOrCreateWorkspace().workspace)
    }
}
