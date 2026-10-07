package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import com.russhwolf.settings.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AiDiagramReviewTest {
    private val server =
        AiServerProfile(
            "https://fixture.invalid/endpoint",
            AiServerLocation.Remote,
            AiRequestBodyProfile(AiRequestDialect.OpenAiChat, "model", 100),
        )

    private fun request(n: Int) = AiDiagramRequestReview(server, "request", "session", n, "{\"round\":$n}")

    private val workspace = Workspace(WorkspaceId("w"), "Canvas", 7)
    private val owner = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, 7, 10, workspace)
    private val node = TextNode(CanvasObjectId("n"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 50f)), text = "Start")

    private fun proposal() =
        AiProposal("p", 7, listOf(AiProposalItem("create", "create node", "", CreateObjectsOperation("create", listOf(node)))))

    @Test fun approvalIsBoundToExactInstanceAndConsumedOncePerRound() =
        runTest {
            val pending = mutableListOf<AiDiagramRequestReview?>()
            val gate = AiDiagramRoundApproval(pending::add)
            val first = request(1)
            val job = async(start = CoroutineStart.UNDISPATCHED) { gate.await(first) }
            assertSame(first, pending.last())
            assertFalse(job.isCompleted)
            assertFalse(gate.respond(first.copy(), true))
            assertFalse(gate.respond(request(2), true))
            assertTrue(gate.respond(first, true))
            assertFalse(gate.respond(first, true))
            assertTrue(job.await())
            assertNull(pending.last())
            val second = request(2)
            val next = async(start = CoroutineStart.UNDISPATCHED) { gate.await(second) }
            assertFalse(gate.respond(first, true))
            assertTrue(gate.respond(second, false))
            assertFalse(next.await())
            gate.close()
        }

    @Test fun cancellationAndCloseReleaseWaitingReviewsWithoutTransferringConsent() =
        runTest {
            var pending: AiDiagramRequestReview? = null
            val gate = AiDiagramRoundApproval { pending = it }
            val first = request(1)
            val cancelled = async(start = CoroutineStart.UNDISPATCHED) { gate.await(first) }
            cancelled.cancel()
            assertFailsWith<CancellationException> { cancelled.await() }
            assertNull(pending)
            assertFalse(gate.respond(first, true))
            val next = async(start = CoroutineStart.UNDISPATCHED) { gate.await(request(2)) }
            gate.close()
            assertFalse(next.await())
            assertNull(pending)
            assertFailsWith<IllegalStateException> { gate.await(request(3)) }
        }

    @Test fun reviewedSelectionUsesExistingTransactionAndUndoRedoAfterFreshEditorAuthority() {
        val proposal = proposal()
        val review =
            assertIs<AiDiagramApplyReview.Ready>(
                reviewAiDiagramApply(proposal, workspace, workspace, owner, owner, owner.copy(role = WorkspaceMemberRole.Editor)),
            )
        assertEquals("ai-proposal-p", review.operation.operationId)
        val applied = WorkspaceHistory(workspace).execute(review.operation)
        assertTrue(applied.succeeded)
        assertEquals(
            node,
            applied.history.workspace.objects
                .getValue(node.id),
        )
        val undone = applied.history.undo()
        assertTrue(undone.succeeded)
        assertTrue(
            undone.history.workspace.objects
                .isEmpty(),
        )
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        assertEquals(
            node.text,
            (
                redone.history.workspace.objects
                    .getValue(node.id) as TextNode
            ).text,
        )
        assertEquals(AiProposalStatus.Ready, proposal.status)
    }

    @Test fun foreignReadOnlyChangedVersionsSnapshotsAndAlreadyUsedPlansFailClosed() {
        val p = proposal()

        fun check(
            reason: AiDiagramApplyFailure,
            current: Workspace = workspace,
            live: WorkspaceSession = owner,
            fresh: WorkspaceSession = owner,
            proposal: AiProposal = p,
        ) {
            assertEquals(AiDiagramApplyReview.Rejected(reason), reviewAiDiagramApply(proposal, workspace, current, owner, live, fresh))
        }
        check(AiDiagramApplyFailure.Scope, fresh = owner.copy(userId = "foreign"))
        check(AiDiagramApplyFailure.Scope, live = owner.copy(clientId = "foreign"))
        check(AiDiagramApplyFailure.Permission, fresh = owner.copy(role = WorkspaceMemberRole.Viewer))
        check(AiDiagramApplyFailure.Version, fresh = owner.copy(workspaceVersion = 8))
        check(AiDiagramApplyFailure.Version, fresh = owner.copy(lastServerSeq = 9))
        check(AiDiagramApplyFailure.Snapshot, fresh = owner.copy(workspace = workspace.copy(title = "Changed")))
        check(AiDiagramApplyFailure.Snapshot, live = owner.copy(workspace = workspace.copy(title = "Changed")))
        check(AiDiagramApplyFailure.Snapshot, current = workspace.copy(objects = mapOf(node.id to node)))
        check(AiDiagramApplyFailure.Version, proposal = p.markCommitted())
    }

    @Test fun excludedDependenciesAndEmptySelectionCannotQueueOperations() {
        val edge = Relation(RelationId("r"), sourceObjectId = node.id, targetObjectId = node.id)
        val p =
            AiProposal("p", 7, proposal().items + AiProposalItem("edge", "create edge", "", CreateRelationsOperation("edge", listOf(edge))))
        val excluded = p.withItemIncluded("create", false)
        assertEquals(
            AiDiagramApplyReview.Rejected(AiDiagramApplyFailure.Conflict),
            reviewAiDiagramApply(excluded, workspace, workspace, owner, owner, owner),
        )
        val empty = excluded.withItemIncluded("edge", false)
        assertEquals(
            AiDiagramApplyReview.Rejected(AiDiagramApplyFailure.Empty),
            reviewAiDiagramApply(empty, workspace, workspace, owner, owner, owner),
        )
        assertTrue(workspace.objects.isEmpty())
    }

    @Test fun ambiguousDraftPublicationPreservesReviewedAiSelectionAndRejectsChangedPayloadUnderSameId() {
        val memory = InMemorySettings()
        var failAfterPublication = true
        val settings =
            object : Settings by memory {
                override fun putString(
                    key: String,
                    value: String,
                ) {
                    memory.putString(key, value)
                    if (failAfterPublication && key.startsWith("wdj1.")) throw IllegalStateException("publication reported failure")
                }
            }
        val scope = PendingSubmissionScope("https://fixture.invalid", "user", "client", "w")
        val store = WorkspaceDraftJournalStore(settings)
        val second = node.copy(id = CanvasObjectId("second"), text = "下一步🙂")
        val full =
            AiProposal(
                "p",
                7,
                proposal().items + AiProposalItem("second", "create node", "", CreateObjectsOperation("create-second", listOf(second))),
            )
        val reviewed = full.withItemIncluded("second", false)
        val operation =
            assertIs<AiDiagramApplyReview.Ready>(
                reviewAiDiagramApply(reviewed, workspace, workspace, owner, owner, owner),
            ).operation
        assertFails { store.append(scope, owner, workspace, operation) }
        val published = assertNotNull(WorkspaceDraftJournalStore(memory).load(scope))
        assertEquals(listOf(operation), published.operations)
        assertEquals(setOf(node.id), published.replay().objects.keys)
        failAfterPublication = false
        val changed = assertNotNull(full.selectedOperation())
        assertEquals(operation.operationId, changed.operationId)
        assertNotEquals(operation, changed)
        // The original UI baseline is still unchanged after the reported failure. Neither
        // retry may append to or replace the already-published draft from that baseline.
        assertFails { store.append(scope, owner, workspace, changed) }
        assertFails { store.append(scope, owner, workspace, operation) }
        assertEquals(published, WorkspaceDraftJournalStore(memory).load(scope))
        assertEquals(setOf(node.id), published.replay().objects.keys)
        assertTrue(workspace.objects.isEmpty())
    }
}
