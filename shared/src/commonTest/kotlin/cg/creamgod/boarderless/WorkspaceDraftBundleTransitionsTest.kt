package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class WorkspaceDraftBundleTransitionsTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val baseline = Workspace(WorkspaceId("workspace"), "Fixture")
    private val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, baseline)
    private val head =
        CreateObjectsOperation(
            "head",
            listOf(
                TextNode(
                    CanvasObjectId("node"),
                    transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)),
                    text = "Draft",
                ),
            ),
        )
    private val tail = EditTextOperation("tail", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))
    private val wire =
        PendingWorkspaceSubmission(
            scope,
            "head",
            SubmitOperationsRequest(
                clientId = "client",
                transactionId = "tx",
                baseVersion = 8,
                operations =
                    listOf(
                        OperationDto(
                            "wire-head",
                            20,
                            "create_object",
                            payload = buildJsonObject { put("text", "Draft") },
                        ),
                    ),
            ),
        )

    private fun initial() = WorkspaceDraftBundleTransitions.append(null, scope, session, baseline, head, "draft")

    private fun afterHead() = (head.applyTo(baseline) as OperationResult.Applied).workspace

    @Test fun submittedHeadKeepsFifoTailAndAckAdvancesBothAtomically() {
        val staged = WorkspaceDraftBundleTransitions.stage(initial(), wire)
        val queued = WorkspaceDraftBundleTransitions.append(staged, scope, session, afterHead(), tail, "unused-id")
        assertEquals("draft", queued.journal!!.id)
        assertEquals(wire, queued.pending)
        val acknowledged = WorkspaceDraftBundleTransitions.acknowledge(queued, wire, 9, 20)
        assertNull(acknowledged.pending)
        assertNull(acknowledged.journal!!.headTransactionId)
        assertEquals(listOf(tail), acknowledged.journal.operations)
        assertEquals(afterHead(), acknowledged.journal.baseWorkspace)
        assertEquals(queued.journal.replay(), acknowledged.journal.replay())
        assertEquals(acknowledged, WorkspaceDraftBundleTransitions.acknowledge(acknowledged, wire, 9, 20))
        assertEquals(LocalDraftAcknowledgement(wire, 9, 20), acknowledged.acknowledged)
        assertFails {
            WorkspaceDraftBundleTransitions.acknowledge(
                acknowledged,
                wire.copy(localOperationId = "different-but-same-tx"),
                9,
                20,
            )
        }
    }

    @Test fun unrelatedOrUnsafeAcknowledgementCannotChangeSavedState() {
        val staged = WorkspaceDraftBundleTransitions.stage(initial(), wire)
        for (invalid in listOf(
            wire.copy(localOperationId = "other"),
            wire.copy(request = wire.request.copy(transactionId = "other")),
            wire.copy(
                request =
                    wire.request.copy(
                        operations =
                            listOf(
                                wire.request.operations
                                    .single()
                                    .copy(clientSeq = 21),
                            ),
                    ),
            ),
        )) {
            assertFails { WorkspaceDraftBundleTransitions.acknowledge(staged, invalid, 9, 20) }
        }
        assertFails { WorkspaceDraftBundleTransitions.acknowledge(staged, wire, 8, 20) }
        assertFails { WorkspaceDraftBundleTransitions.acknowledge(staged, wire, 9, 9_007_199_254_740_992L) }
        assertEquals(wire, staged.pending)
        assertEquals(listOf(head), staged.journal!!.operations)
    }

    @Test fun stagingIsIdempotentButNeverReplacesUnresolvedWire() {
        val staged = WorkspaceDraftBundleTransitions.stage(initial(), wire)
        assertEquals(staged, WorkspaceDraftBundleTransitions.stage(staged, wire))
        assertFails { WorkspaceDraftBundleTransitions.stage(staged, wire.copy(request = wire.request.copy(transactionId = "new"))) }
        assertFails { WorkspaceDraftBundleTransitions.stage(initial().copy(journal = initial().journal!!.copy(quarantined = true)), wire) }
        assertFails { WorkspaceDraftBundleTransitions.append(staged, scope, session, afterHead(), head, "ignored") }
        assertFails {
            WorkspaceDraftBundleTransitions.append(
                WorkspaceDraftScopeBundle(scope = scope, pending = wire),
                scope,
                session,
                baseline,
                head,
                "invented",
            )
        }
    }

    @Test fun quarantineRetainsExactWireAndPreventsEvenIdenticalStaging() {
        val staged = WorkspaceDraftBundleTransitions.stage(initial(), wire)
        val quarantined = WorkspaceDraftBundleTransitions.quarantine(staged, "draft")
        assertTrue(quarantined.journal!!.quarantined)
        assertEquals(wire, quarantined.pending)
        assertEquals("tx", quarantined.journal.headTransactionId)
        assertFails { WorkspaceDraftBundleTransitions.stage(quarantined, wire) }
        assertFails { WorkspaceDraftBundleTransitions.requireRemovable(quarantined, "draft") }
        assertFails { WorkspaceDraftBundleTransitions.quarantine(staged, "other-draft") }
        // A validated late outcome may be recorded; it must not silently remove quarantine.
        val acknowledged = WorkspaceDraftBundleTransitions.acknowledge(quarantined, wire, 9, 20)
        assertTrue(acknowledged.journal!!.quarantined)
        assertEquals(LocalDraftAcknowledgement(wire, 9, 20), acknowledged.acknowledged)
    }

    @Test fun restoreRequiresFreshScopeRoleVersionSequenceAndBaseline() {
        val draft = initial()
        assertEquals(draft, WorkspaceDraftBundleTransitions.restore(draft, "draft", session))
        for (invalid in listOf(
            session.copy(userId = "other"),
            session.copy(clientId = "other"),
            session.copy(role = WorkspaceMemberRole.Viewer),
            session.copy(workspaceVersion = 9),
            session.copy(lastServerSeq = 20),
            session.copy(workspace = afterHead()),
        )) {
            assertFails { WorkspaceDraftBundleTransitions.restore(draft, "draft", invalid) }
        }
        assertFails { WorkspaceDraftBundleTransitions.restore(draft, "wrong-draft", session) }
        assertFails { WorkspaceDraftBundleTransitions.restore(WorkspaceDraftBundleTransitions.stage(draft, wire), "draft", session) }
        assertFails {
            WorkspaceDraftBundleTransitions.restore(
                WorkspaceDraftBundleTransitions.quarantine(draft, "draft"),
                "draft",
                session,
            )
        }
        WorkspaceDraftBundleTransitions.requireRemovable(draft, "draft")
    }

    @Test fun stopArchivesUnconfirmedRequestInsteadOfDeletingEvidence() {
        val staged = WorkspaceDraftBundleTransitions.stage(initial(), wire)
        val stopped = WorkspaceDraftBundleTransitions.stop(staged, "tx")
        assertNull(stopped.pending)
        assertEquals(wire, stopped.stoppedPending)
        assertTrue(stopped.journal!!.quarantined)
        assertNull(stopped.journal.headTransactionId)
        assertFails { WorkspaceDraftBundleTransitions.stage(stopped, wire) }
        assertFails { WorkspaceDraftBundleTransitions.requireRemovable(stopped, "draft") }
        assertFails { WorkspaceDraftBundleTransitions.stop(staged, "other") }
        assertEquals(
            wire,
            WorkspaceDraftBundleTransitions.stop(WorkspaceDraftScopeBundle(scope = scope, pending = wire), "tx").stoppedPending,
        )
    }
}
