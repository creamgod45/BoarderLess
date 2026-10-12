package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Actual IndexedDB commits, with explicit test persistence grants rather than a browser grant claim. */
class BrowserLocalWorkspaceRepositoryTest {
    @Test fun offlineSyncBaselineAndLaterLocalEditsReopenTogetherInIndexedDb() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repository = browserLocalWorkspaceRepository()
            val opened = repository.openOrCreateWorkspace()
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-owner", WorkspaceId("remote-id"))
            val remote = WorkspaceSession(binding.userId, "remote-client", WorkspaceMemberRole.Owner, 0, 0,
                opened.workspace.copy(id = binding.workspaceId))
            assertNotNull(repository.localSyncStore).bindSyncDestination(opened, binding, remote)
            repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("queued-offline", listOf(node())))
            repository.close()
            browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            val saved = reopened.openOrCreateWorkspace()
            val pending = assertNotNull(reopened.localSyncStore).readSyncSnapshot(saved)
            assertEquals(binding, pending.checkpoint!!.binding)
            assertTrue(pending.hasPendingContent)
            assertEquals(node(), saved.workspace.objects[node().id])
            assertIs<LocalWorkspaceSyncComparison.UploadPending>(compareLocalWorkspaceSync(pending, remote))
            reopened.close()
        } finally { browserRecordFixtureRestore() }
    }
    private fun node() = TextNode(CanvasObjectId("utf8-node"),
        transform = CanvasTransform(Vec2(20f, 30f), CanvasSize(80f, 40f)), text = "文件中文🙂")

    @Test fun documentReopensExactContentReceiptsAndUndoRedo() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val repo = browserLocalWorkspaceRepository()
            assertTrue(repo.isLocalOnly); assertTrue(repo.awaitsDraftPublication)
            val session = repo.openOrCreateWorkspace()
            val create = CreateObjectsOperation("browser-create", listOf(node()))
            val created = WorkspaceHistory(session.workspace).execute(create)
            repo.retainDraftAwaited(session, session.workspace, create)
            browserRecordFixtureReopen()
            val reopened = browserLocalWorkspaceRepository()
            val current = reopened.openOrCreateWorkspace()
            assertEquals(created.history.workspace, current.workspace)
            reopened.retainDraftAwaited(session, session.workspace, create) // Same receipt, no extra object.
            val undone = created.history.undo()
            reopened.retainDraftAwaited(current, current.workspace, assertNotNull(undone.appliedOperation))
            val redone = undone.history.redo()
            val undoSession = reopened.refresh(current)
            reopened.retainDraftAwaited(undoSession, undoSession.workspace, assertNotNull(redone.appliedOperation))
            assertEquals(redone.history.workspace, browserLocalWorkspaceRepository().openOrCreateWorkspace().workspace)
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun putSuccessAndCancellationNeverPublishANewSession() = runTest {
        browserRecordFixtureInstall("hold")
        try {
            val put = CompletableDeferred<Unit>()
            browserRecordFixtureWaitForPut { put.complete(Unit) }
            val work = async { browserLocalWorkspaceRepository().openOrCreateWorkspace() }
            put.await(); assertFalse(work.isCompleted)
            assertEquals("0", browserRecordFixtureValue("acknowledged"))
            work.cancelAndJoin(); browserRecordFixtureReopen()
            assertNull(BrowserDurableLocalRecordStore(BrowserRecordScope.Documents).read(PersistentLocalWorkspaceRepository.Key))
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun storageDenialAndAbortNeverReturnAWorkspace() = runTest {
        for (mode in listOf("denied", "abort")) {
            browserRecordFixtureInstall(mode)
            try {
                assertFails { browserLocalWorkspaceRepository().openOrCreateWorkspace() }
                assertEquals("0", browserRecordFixtureValue("acknowledged"))
                browserRecordFixtureReopen()
                assertNull(BrowserDurableLocalRecordStore(BrowserRecordScope.Documents).read(PersistentLocalWorkspaceRepository.Key))
            } finally { browserRecordFixtureRestore() }
        }
    }

    @Test fun sameRecordKeyInReceiptAndLibraryCannotReplaceDocument() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val original = browserLocalWorkspaceRepository().openOrCreateWorkspace()
            val key = PersistentLocalWorkspaceRepository.Key
            BrowserDurableLocalRecordStore().compareAndSet(key, null, "receipt".encodeToByteArray())
            assertContentEquals("receipt".encodeToByteArray(), BrowserDurableLocalRecordStore(BrowserRecordScope.Library).read(key)?.payload)
            browserRecordFixtureReopen()
            assertEquals(original.workspace, browserLocalWorkspaceRepository().openOrCreateWorkspace().workspace)
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun competingDocumentWritersPreserveEveryAcknowledgedWorkspace() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val initial = browserLocalWorkspaceRepository().openOrCreateWorkspace()
            supervisorScope {
                val a = async { runCatching { browserLocalWorkspaceRepository().createWorkspace(initial, "One") } }
                val b = async { runCatching { browserLocalWorkspaceRepository().createWorkspace(initial, "Two") } }
                val successes = listOf(a.await(), b.await()).mapNotNull { it.getOrNull() }
                val repo = browserLocalWorkspaceRepository()
                val all = repo.listWorkspaces(repo.openOrCreateWorkspace())
                assertTrue(all.size in 2..3)
                assertTrue(successes.all { saved -> all.any { it.id == saved.workspace.id } })
                assertEquals(all.size, all.map { it.id }.distinct().size)
            }
        } finally { browserRecordFixtureRestore() }
    }
}
