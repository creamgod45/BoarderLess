package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.MaxRecentWorkspaces
import cg.creamgod.boarderless.data.RecentWorkspace
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
        assertEquals(WorkspaceId("w1"), WorkspaceLaunchRequests.pending.value)
        WorkspaceLaunchRequests.consume(WorkspaceId("w1"))
        assertNull(WorkspaceLaunchRequests.pending.value)
    }

    private fun summary(
        id: String,
        title: String,
    ) = WorkspaceSummary(WorkspaceId(id), title, role = "owner", workspaceVersion = 1, lastServerSeq = 1)
}
