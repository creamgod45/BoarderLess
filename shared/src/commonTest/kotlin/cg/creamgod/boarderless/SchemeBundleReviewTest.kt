package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class SchemeBundleReviewTest {
    private val workspace = Workspace(WorkspaceId("w"), "Canvas", 2)
    private val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 2, 3, workspace)
    @Test fun reviewScopeRejectsDifferentIdentityRevisionSnapshotOrRecoveryBarrier() {
        assertTrue(schemeBundleScopeMatches(owner, workspace, PenCanvasLive(owner, workspace, true)))
        for (session in listOf(owner.copy(userId = "other"), owner.copy(clientId = "other"), owner.copy(workspaceVersion = 3),
            owner.copy(lastServerSeq = 4), owner.copy(workspace = workspace.copy(id = WorkspaceId("other"))))) {
            assertFalse(schemeBundleScopeMatches(owner, workspace, PenCanvasLive(session, workspace, true)))
        }
        assertFalse(schemeBundleScopeMatches(owner, workspace, PenCanvasLive(owner, workspace.copy(title = "changed"), true)))
        assertFalse(schemeBundleScopeMatches(owner, workspace, PenCanvasLive(owner, workspace, false)))
        assertFalse(schemeBundleScopeMatches(owner, workspace, PenCanvasLive(null, workspace, true)))
    }
    @Test fun uncertainLibrarySaveReusesExactNormalizedDefinitionAndNeverAllocatesAgain() {
        val prepared = QuickScheme(0, "  Bundle🙂  ", "payload", 7, "w")
        val entries = mutableListOf<QuickScheme>()
        var saves = 0
        val library = object : QuickSchemeRepository {
            override fun list() = entries.toList()
            override fun save(payload: String, name: String?, schemaVersion: Int, sourceWorkspaceId: String?): QuickScheme {
                saves++
                val saved = QuickScheme(8, name!!.trim(), payload, schemaVersion, sourceWorkspaceId)
                entries += saved
                error("After commit")
            }
            override fun rename(id: Int, name: String): QuickScheme? = null
            override fun delete(id: Int) = false
        }
        assertFails { saveMaterializedBundle(library, prepared) }
        assertEquals(1, saves)
        assertEquals(entries.single(), saveMaterializedBundle(library, prepared))
        assertEquals(1, saves)
        entries += entries.single().copy(id = 9)
        assertEquals(8, saveMaterializedBundle(library, prepared).id)
        assertEquals(1, saves)
    }
}
