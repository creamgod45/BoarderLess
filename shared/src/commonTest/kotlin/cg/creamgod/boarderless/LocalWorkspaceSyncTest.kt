package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class LocalWorkspaceSyncTest {
    private class Records : AwaitedDurableLocalRecordStore {
        var value: DurableLocalRecord? = null
        var failConfirm = false
        override suspend fun read(key: String) = value
        override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
            check(value?.generation == generation)
            value = DurableLocalRecord((generation ?: 0) + 1, payload.copyOf())
        }
        override suspend fun confirmDurable(key: String, generation: Long) {
            check(value?.generation == generation)
            check(!failConfirm) { "Unknown confirmation" }
        }
    }
    private fun repo(records: Records) = AwaitedLocalWorkspaceRepository(records) { "2026-10-10T00:00:00Z" }
    private val binding = LocalWorkspaceSyncBinding("a".repeat(64), "remote-owner", WorkspaceId("remote-board"))
    private val node = TextNode(CanvasObjectId("n"), transform = CanvasTransform(Vec2.Zero, CanvasSize(80f, 40f)), text = "同步🙂")
    private fun remote(local: Workspace, version: Long = local.version, seq: Long = version) =
        WorkspaceSession(binding.userId, "remote-client", WorkspaceMemberRole.Owner, version, seq,
            local.copy(id = binding.workspaceId, version = version))

    @Test fun offlineEditsAndPendingSnapshotSurviveReopenWithoutAnyNetwork() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        val operation = CreateObjectsOperation("offline", listOf(node))
        repository.retainDraftAwaited(opened, opened.workspace, operation)
        repository.submit(opened, operation)
        repository.close()
        val reopened = repo(records)
        val saved = reopened.openOrCreateWorkspace()
        val pending = reopened.readSyncSnapshot(saved)
        assertTrue(pending.hasPendingContent)
        assertNull(pending.checkpoint)
        assertEquals(node, pending.local.workspace.objects[node.id])
    }

    @Test fun bindingSurvivesUnknownAckAndDoesNotCopyOrLoseOfflineContent() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        val server = remote(opened.workspace)
        records.failConfirm = true
        assertFails { repository.bindSyncDestination(opened, binding, server) }
        val generation = records.value!!.generation
        records.failConfirm = false
        val recovered = repo(records).bindSyncDestination(opened, binding, server)
        assertEquals(generation, records.value!!.generation)
        assertEquals(opened.workspace, recovered.local.workspace)
        assertFalse(recovered.hasPendingContent)
        assertEquals(binding, recovered.checkpoint!!.binding)
    }

    @Test fun slowSyncAckPreservesNewLocalEditsAndFencesEarlierCheckpoints() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        val initial = repository.bindSyncDestination(opened, binding, remote(opened.workspace))
        val expected = initial.checkpoint!!
        repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("offline", listOf(node)))
        val newerRemote = remote(opened.workspace, 1)
        val advanced = repository.acknowledgeSyncCheckpoint(opened, expected, newerRemote)
        assertEquals(node, advanced.local.workspace.objects[node.id])
        assertTrue(advanced.hasPendingContent)
        val generation = records.value!!.generation
        // The same verified ACK is idempotent even after its original response was lost.
        repository.acknowledgeSyncCheckpoint(opened, expected, newerRemote)
        assertEquals(generation, records.value!!.generation)
        assertFails { repository.acknowledgeSyncCheckpoint(opened, expected, remote(opened.workspace, 2)) }
        assertEquals(generation, records.value!!.generation)
    }

    @Test fun staleLocalSnapshotsForeignAccountsAndRebindingCannotReplaceDestination() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("offline", listOf(node)))
        assertFails { repository.bindSyncDestination(opened, binding, remote(opened.workspace)) }
        val current = repository.refresh(opened)
        assertFails { repository.bindSyncDestination(current, binding, remote(current.workspace).copy(userId = "foreign")) }
        repository.bindSyncDestination(current, binding, remote(current.workspace))
        val other = binding.copy(serverScope = "b".repeat(64))
        assertFails { repository.bindSyncDestination(current, other, remote(current.workspace)) }
    }

    @Test fun conflictComparisonProducesThreeFrozenVersionsWithoutWritingEitherSide() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("initial", listOf(node)))
        val baseline = repository.refresh(opened)
        repository.bindSyncDestination(baseline, binding, remote(baseline.workspace))
        val localOperation = EditTextOperation("local-edit", listOf(TextChange(node.id, 1, node.text, "本機")))
        repository.retainDraftAwaited(baseline, baseline.workspace, localOperation)
        val snapshot = repository.readSyncSnapshot(baseline)
        val remoteNode = node.copy(text = "伺服器", version = 2)
        val server = remote(baseline.workspace.copy(objects = mapOf(node.id to remoteNode)), 2)
        val before = records.value!!
        val conflict = assertIs<LocalWorkspaceSyncComparison.Conflict>(compareLocalWorkspaceSync(snapshot, server))
        val row = assertIs<WorkspaceDraftDifference.ObjectChange>(conflict.review.differences().single())
        assertEquals(node, row.before)
        assertEquals("本機", (row.draft as TextNode).text)
        assertEquals(remoteNode, row.remote)
        assertEquals(snapshot.local.workspace.id, conflict.review.current.id)
        assertEquals(before, records.value)
    }

    @Test fun oneSidedChangesAndVersionOnlyUpdatesAreDistinguished() {
        val base = Workspace(WorkspaceId("local"), "title", objects = mapOf(node.id to node))
        val local = WorkspaceSession("local", "client", WorkspaceMemberRole.Owner, 0, 0, base)
        val checkpoint = LocalWorkspaceSyncCheckpoint(binding, base, 0, 0)
        val snapshot = LocalWorkspaceSyncSnapshot(local, checkpoint)
        val versions = remote(base.copy(objects = mapOf(node.id to node.copy(version = 9))), 9)
        assertIs<LocalWorkspaceSyncComparison.Unchanged>(compareLocalWorkspaceSync(snapshot, versions))
        val changed = base.copy(objects = mapOf(node.id to node.copy(text = "changed")))
        assertIs<LocalWorkspaceSyncComparison.DownloadPending>(compareLocalWorkspaceSync(snapshot, remote(changed, 1)))
        assertIs<LocalWorkspaceSyncComparison.UploadPending>(compareLocalWorkspaceSync(snapshot.copy(local = local.copy(workspace = changed)), remote(base)))
        assertFails { compareLocalWorkspaceSync(snapshot, remote(base).copy(userId = "foreign")) }
    }

    @Test fun firstPairingDifferentExistingContentsCannotTurnIntoASilentUploadOrAcknowledgement() = runTest {
        val records = Records(); val repository = repo(records)
        val opened = repository.openOrCreateWorkspace()
        repository.retainDraftAwaited(opened, opened.workspace, CreateObjectsOperation("offline", listOf(node)))
        val local = repository.refresh(opened)
        val server = remote(local.workspace.copy(objects = mapOf(node.id to node.copy(text = "既有伺服器內容"))))
        val paired = repository.bindSyncDestination(local, binding, server)
        assertTrue(paired.checkpoint!!.requiresInitialReview)
        assertIs<LocalWorkspaceSyncComparison.Conflict>(compareLocalWorkspaceSync(paired, server))
        val generation = records.value!!.generation
        assertFails { repository.acknowledgeSyncCheckpoint(local, paired.checkpoint!!, server) }
        assertEquals(generation, records.value!!.generation)
        assertEquals(node, repository.refresh(local).workspace.objects[node.id])
    }
}
