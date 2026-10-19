package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopLocalSyncCheckpointTest {
    @Test fun startupAndReopenUseRealLocalFilesWhileServerFactoryIsUnavailable() = runTest {
        val root = Files.createTempDirectory("boarderless-local-sync-test-")
        try {
            var serverCalls = 0
            val runtime = WorkspaceStorageRuntime(
                localRepository = { DesktopFileWorkspaceRepository(root) },
                mediaRuntimeFor = { MediaImportRuntime.Unavailable },
                serverRepository = { serverCalls++; error("Server offline") },
            )
            val repository = runtime.createRepository()
            val initial = repository.openOrCreateWorkspace()
            val sync = assertNotNull(repository.localSyncStore)
            val binding = LocalWorkspaceSyncBinding("a".repeat(64), "server-user", WorkspaceId("server-id"))
            val remote = WorkspaceSession("server-user", "server-client", WorkspaceMemberRole.Owner, 0, 0,
                initial.workspace.copy(id = binding.workspaceId))
            sync.bindSyncDestination(initial, binding, remote)
            val node = TextNode(CanvasObjectId("offline-node"),
                transform = CanvasTransform(Vec2.Zero, CanvasSize(120f, 50f)), text = "離線檔案🙂")
            val operation = CreateObjectsOperation("offline-create", listOf(node))
            repository.retainDraft(initial, initial.workspace, operation)
            repository.submit(initial, operation)
            repository.close()
            val reopened = runtime.createRepository()
            val restored = reopened.openOrCreateWorkspace()
            val pending = assertNotNull(reopened.localSyncStore).readSyncSnapshot(restored)
            assertEquals(0, serverCalls)
            assertTrue(pending.hasPendingContent)
            assertEquals(binding, pending.checkpoint!!.binding)
            assertEquals(node, restored.workspace.objects[node.id])
            assertIs<LocalWorkspaceSyncComparison.UploadPending>(compareLocalWorkspaceSync(pending, remote))
            val plan = LocalWorkspaceSyncMergePlan(pending, remote.copy(workspace = remote.workspace.copy(title = "同步標題")))
            val proposal = plan.proposal("native-reviewed-plan", emptyMap())
            assertNotNull(reopened.localSyncStore).stageSyncProposal(restored, pending.checkpoint!!, null, proposal)
            reopened.close()
            val durable = runtime.createRepository()
            val current = durable.openOrCreateWorkspace()
            val recovered = assertNotNull(durable.localSyncStore).readSyncSnapshot(current)
            assertEquals(proposal, recovered.proposal); assertTrue(recovered.proposalIsCurrent)
            assertEquals(restored.workspace, recovered.local.workspace)
            assertEquals(0, serverCalls)
            val merge = LocalWorkspaceContentOperation("native-apply", recovered.local.workspace, proposal.target)
            val applied = durable.localSyncStore!!.applySyncProposal(current, proposal.id, merge).snapshot
            assertEquals("同步標題", applied.local.workspace.title)
            durable.close()
            val finalRepository = runtime.createRepository()
            val finalSession = finalRepository.openOrCreateWorkspace()
            val finalSnapshot = finalRepository.localSyncStore!!.readSyncSnapshot(finalSession)
            assertTrue(finalSnapshot.appliedIsCurrent)
            assertEquals(pending.checkpoint, finalSnapshot.checkpoint)
            val undone = restoredLocalSyncHistory(finalSnapshot).undo()
            assertTrue(undone.succeeded)
            assertEquals(restored.workspace.title, undone.history.workspace.title)
            assertEquals(node, undone.history.workspace.objects[node.id])
            assertTrue(undone.history.redo().succeeded)
            assertEquals(0, serverCalls)
            finalRepository.close()
        } finally {
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
