package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class PreparedSubmissionHistoryTest {
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Baseline")
    private val workspace = Workspace(WorkspaceId("board"), "Board", objects = mapOf(node.id to node))
    private val baseline = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, 0, 0, workspace)
    private fun record(seq: Long, version: Long, actor: String, transaction: String, dto: OperationDto) =
        CommittedWorkspaceOperationDto(seq, dto.operationId, transaction, actor, "$actor-client", dto.clientSeq,
            0, version, dto.kind, dto.payload, 1, "2026-10-10T00:00:00Z")

    @Test fun reconstructsOtherPriorTransactionsButStopsBeforeLaterRemoteEdits() = runTest {
        val peer = node.copy(id = CanvasObjectId("peer"), text = "Peer before original")
        val first = record(1, 1, "peer", "peer-before", CreateObjectsOperation("peer-create", listOf(peer)).toExpandedDtos(0).single())
        val own = record(2, 2, "user", "original", EditTextOperation("own-edit", listOf(TextChange(node.id, 1, node.text, "Ours"))).toExpandedDtos(0).single())
        val later = record(3, 3, "peer", "peer-after", EditTextOperation("peer-edit", listOf(TextChange(peer.id, 1, peer.text, "Later"))).toExpandedDtos(1).single())
        val cursors = mutableListOf<Long>()
        val window = collectCommittedThrough(0, 2) { cursor ->
            cursors += cursor
            if (cursor == 0L) FullCatchUpOperationsDto(listOf(first), 3, true)
            else FullCatchUpOperationsDto(listOf(own, later), 3, false)
        }
        assertEquals(listOf(0L, 1L), cursors)
        assertEquals(2, window.operations.size)
        val recovered = reduceCommittedCatchUpPage(recoveryProjection(baseline), window).toDomainWorkspace(workspace.title)
        assertEquals(2L, recovered.version)
        assertEquals("Ours", (recovered.objects[node.id] as TextNode).text)
        assertEquals(peer, recovered.objects[peer.id])
        assertEquals(2L, recovered.objects[node.id]!!.version)
    }
    @Test fun incompleteGappedOrOversizedHistoryCannotBecomeACommitBaseline() = runTest {
        val dto = EditTextOperation("own-edit", listOf(TextChange(node.id, 1, node.text, "Ours"))).toExpandedDtos(0).single()
        val first = record(1, 1, "user", "original", dto)
        assertFails { collectCommittedThrough(0, 2) { FullCatchUpOperationsDto(listOf(first), 2, false) } }
        assertFails { collectCommittedThrough(0, 2) { FullCatchUpOperationsDto(listOf(first.copy(serverSeq = 2)), 2, false) } }
        var calls = 0
        assertFails { collectCommittedThrough(0, 8001) { calls++; FullCatchUpOperationsDto(emptyList(), 8001, false) } }
        assertEquals(0, calls)
        assertFails { recoveryProjection(baseline.copy(workspace = workspace.copy(canvasStyle = CanvasStyle(backgroundToken = "cool")))) }
        assertEquals(workspace, recoveryProjection(baseline).toDomainWorkspace(workspace.title))
    }
}
