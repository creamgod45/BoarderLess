package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.MaxRecentWorkspaces
import cg.creamgod.boarderless.data.RecentWorkspace
import cg.creamgod.boarderless.data.RecentWorkspaceStore
import cg.creamgod.boarderless.data.WorkspaceStorageMode
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import cg.creamgod.boarderless.data.WorkspaceSummary
import cg.creamgod.boarderless.data.reconciledWith
import cg.creamgod.boarderless.data.withOpened
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentWorkspacesTest {
    @Test
    fun openedWorkspaceMovesToTheFrontWithoutDuplicates() {
        val recent = listOf(RecentWorkspace("a", "A"), RecentWorkspace("b", "B"))

        assertEquals(
            listOf(RecentWorkspace("b", "B renamed"), RecentWorkspace("a", "A")),
            recent.withOpened("b", "B renamed"),
        )
    }

    @Test
    fun keepsOnlyTheFiveMostRecent() {
        val recent = (1..MaxRecentWorkspaces).fold(emptyList<RecentWorkspace>()) { list, n -> list.withOpened("w$n", "W$n") }

        val updated = recent.withOpened("new", "New")

        assertEquals(MaxRecentWorkspaces, updated.size)
        assertEquals(listOf("new", "w5", "w4", "w3", "w2"), updated.map { it.id })
    }

    @Test
    fun reconcileDropsUnavailableWorkspacesAndTakesCurrentTitles() {
        val recent = listOf(RecentWorkspace("a", "Old A"), RecentWorkspace("gone", "Gone"), RecentWorkspace("b", "B"))
        val available = listOf(summary("b", "B"), summary("a", "New A"))

        assertEquals(listOf(RecentWorkspace("a", "New A"), RecentWorkspace("b", "B")), recent.reconciledWith(available))
    }

    @Test
    fun launchRequestsIgnoreIdsThatCannotBeWorkspaceIds() {
        assertTrue(WorkspaceLaunchRequests.isWorkspaceId("0f8fad5b-d9cb-469f-a165-70867728950e"))
        assertFalse(WorkspaceLaunchRequests.isWorkspaceId("../etc/passwd"))
        assertFalse(WorkspaceLaunchRequests.isWorkspaceId(""))

        WorkspaceLaunchRequests.open("bad id")
        assertNull(WorkspaceLaunchRequests.pending.value)

        WorkspaceLaunchRequests.open("w1")
        assertEquals(WorkspaceId("w1"), WorkspaceLaunchRequests.pending.value?.workspaceId)
        WorkspaceLaunchRequests.consume(requireNotNull(WorkspaceLaunchRequests.pending.value))
        assertNull(WorkspaceLaunchRequests.pending.value)
    }

    @Test
    fun identicalIdsRemainIndependentAcrossStorageModesAfterReopenAndReconcile() {
        val settings = InMemorySettings()
        val local = RecentWorkspaceStore(settings, WorkspaceStorageMode.Local)
        val server = RecentWorkspaceStore(settings, WorkspaceStorageMode.Server)
        local.recordOpened(WorkspaceId("same"), "本機")
        server.recordOpened(WorkspaceId("same"), "伺服器")
        local.rename(WorkspaceId("same"), "本機改名")
        server.reconcile(emptyList())
        val reopened = RecentWorkspaceStore(settings, WorkspaceStorageMode.Local)
        assertEquals("本機改名", reopened.items.single().title)
        assertEquals(WorkspaceStorageMode.Local, reopened.items.single().storageMode)
        assertTrue(server.items.isEmpty())
        assertTrue(RecentWorkspaceStore(settings).items.isEmpty())
    }

    @Test
    fun scopedRequestsRejectUnknownModesAndCannotBeConsumedBySameIdInAnotherMode() {
        val mode = WorkspaceStorageMode.Local
        WorkspaceLaunchRequests.open("same", mode.token)
        val local = requireNotNull(WorkspaceLaunchRequests.pending.value)
        assertTrue(local.matches(mode))
        assertFalse(local.matches(WorkspaceStorageMode.Server))
        WorkspaceLaunchRequests.open("replacement", "unknown")
        assertEquals(local, WorkspaceLaunchRequests.pending.value)
        WorkspaceLaunchRequests.open("same", "server")
        WorkspaceLaunchRequests.consume(local)
        val server = requireNotNull(WorkspaceLaunchRequests.pending.value)
        assertEquals(WorkspaceStorageMode.Server, server.storageMode)
        WorkspaceLaunchRequests.consume(server)
        assertNull(WorkspaceLaunchRequests.pending.value)
    }

    private fun summary(
        id: String,
        title: String,
    ) = WorkspaceSummary(WorkspaceId(id), title, role = "owner", workspaceVersion = 1, lastServerSeq = 1)
}
