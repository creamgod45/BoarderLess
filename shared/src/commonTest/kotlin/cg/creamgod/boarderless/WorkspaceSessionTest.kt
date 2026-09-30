package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.hasRemoteChangesComparedTo
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceSessionTest {
    @Test
    fun newerVersionOfSameWorkspaceIsAdopted() {
        assertTrue(session("workspace-a", version = 8).hasRemoteChangesComparedTo(session("workspace-a", version = 7)))
    }

    @Test
    fun equalOrOlderVersionsAreIgnored() {
        val current = session("workspace-a", version = 7)

        assertFalse(session("workspace-a", version = 7).hasRemoteChangesComparedTo(current))
        assertFalse(session("workspace-a", version = 6).hasRemoteChangesComparedTo(current))
    }

    @Test
    fun stateFromAnotherWorkspaceIsNeverAdopted() {
        assertFalse(session("workspace-b", version = 9).hasRemoteChangesComparedTo(session("workspace-a", version = 7)))
    }

    @Test
    fun roleOrTitleChangesAreAdoptedWithoutAContentVersionChange() {
        val current = session("workspace-a", version = 7)

        assertTrue(
            session("workspace-a", version = 7, role = WorkspaceMemberRole.Viewer)
                .hasRemoteChangesComparedTo(current),
        )
        assertTrue(
            session("workspace-a", version = 7, title = "Renamed remotely")
                .hasRemoteChangesComparedTo(current),
        )
        assertFalse(
            session("workspace-a", version = 6, role = WorkspaceMemberRole.Viewer)
                .hasRemoteChangesComparedTo(current),
        )
    }

    private fun session(
        workspaceId: String,
        version: Long,
        role: WorkspaceMemberRole = WorkspaceMemberRole.Owner,
        title: String = "Test workspace",
    ) = WorkspaceSession(
        userId = "user-1",
        clientId = "client-1",
        role = role,
        workspaceVersion = version,
        lastServerSeq = version,
        workspace = Workspace(
            id = WorkspaceId(workspaceId),
            title = title,
            version = version,
        ),
    )
}
