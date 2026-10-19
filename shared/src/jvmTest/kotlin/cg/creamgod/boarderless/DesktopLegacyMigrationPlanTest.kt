package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.remote.PendingWorkspaceSubmission
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopLegacyMigrationPlanTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val node = TextNode(CanvasObjectId("a"), transform = CanvasTransform(Vec2.Zero, CanvasSize(20f, 20f)), text = "Draft")
    private val operation = CreateObjectsOperation("head", listOf(node))

    private fun prefs() =
        SessionPreferences(InMemorySettings().apply { putString("backend.clientId", "client") }).apply {
            clientSequence =
                15
        }

    private fun wire(
        target: PendingSubmissionScope = scope,
        tx: String = "tx",
        seq: Long = 20,
    ) = PendingWorkspaceSubmission(
        target,
        "head",
        SubmitOperationsRequest(
            clientId = target.clientId,
            transactionId = tx,
            baseVersion = 8,
            operations =
                operation.toExpandedDtos(seq - 1),
        ),
    )

    private fun journal(
        prefs: SessionPreferences,
        target: PendingSubmissionScope = scope,
    ) {
        val before = Workspace(WorkspaceId(target.workspaceId), "Fixture")
        prefs.draftJournals.append(
            target,
            WorkspaceSession(target.userId, target.clientId, WorkspaceMemberRole.Editor, 8, 19, before),
            before,
            operation,
        )
    }

    private fun capture(
        prefs: SessionPreferences,
        scopes: List<PendingSubmissionScope>,
    ) = prefs.captureLegacySequenceForMigration(scope, scopes, emptyList(), emptyList())

    @Test fun completePlanPreservesUnsentDraftsAllArchivesAndSafetyWithoutWrites() {
        val prefs = prefs()
        val other = scope.copy(workspaceId = "unsent")
        journal(prefs)
        journal(prefs, other)
        val pending = wire()
        prefs.draftPersistence.stage(pending)
        // Stop phase 1 published the archive, but pending/journal are still active.
        prefs.pendingSubmissions.archiveStopped(pending)
        val older = wire(tx = "older", seq = 100).copy(localOperationId = "historic")
        prefs.pendingSubmissions.archiveStopped(older)
        prefs.fenceAttempts.begin(pending)
        val captured = capture(prefs, listOf(scope, other))
        val plan = DesktopLegacyMigrationPlanner.prepare(scope, captured, emptyList())
        val primary = plan.scopes.single { it.scope == scope }
        assertEquals(pending, primary.draft!!.stoppedPending)
        assertNull(primary.draft.pending)
        assertTrue(primary.draft.journal!!.quarantined)
        assertNull(primary.draft.journal.headTransactionId)
        assertEquals(captured.journals.single { it.scope == scope }.operations, primary.draft.journal.operations)
        assertEquals(listOf(older), primary.draft.retainedStopped.map { it.submitted })
        assertTrue(primary.retryQuarantined)
        assertEquals(100L, primary.minimumClientSequence)
        assertEquals(captured.fenceAttempts, primary.safety.fences)
        assertEquals(
            captured.journals.single { it.scope == other },
            plan.scopes
                .single { it.scope == other }
                .draft!!
                .journal,
        )
        assertEquals(captured, capture(prefs, listOf(scope, other))) // Source is untouched.
    }

    @Test fun stoppedHeadWithoutPendingRetainsExactWireButMissingOrConflictingHeadsFail() {
        val prefs = prefs()
        journal(prefs)
        val pending = wire()
        prefs.draftPersistence.stage(pending)
        val active = capture(prefs, listOf(scope))
        assertFails {
            DesktopLegacyMigrationPlanner.prepare(
                scope,
                active.copy(
                    journals =
                        active.journals.map {
                            it.copy(headTransactionId = "different")
                        },
                ),
                emptyList(),
            )
        }
        prefs.draftPersistence.stop(scope, "tx")
        val stopped = capture(prefs, listOf(scope))
        val plan = DesktopLegacyMigrationPlanner.prepare(scope, stopped, emptyList())
        assertEquals(
            pending,
            plan.scopes
                .single()
                .draft!!
                .stoppedPending,
        )
        assertFails {
            DesktopLegacyMigrationPlanner.prepare(
                scope,
                stopped.copy(stopped = emptyList(), floor = stopped.counter),
                emptyList(),
            )
        }
        assertFails { DesktopLegacyMigrationPlanner.prepare(scope, stopped, listOf(plan.scopes.single().draft!!)) }
        assertEquals(stopped, capture(prefs, listOf(scope)))
    }

    @Test fun digestIsOrderStableButBindsOriginalStopPhaseAndCounterNotOnlyNormalizedOutput() {
        val prefs = prefs()
        journal(prefs)
        val pending = wire()
        prefs.draftPersistence.stage(pending)
        prefs.pendingSubmissions.archiveStopped(pending)
        prefs.pendingSubmissions.archiveStopped(wire(tx = "older", seq = 40).copy(localOperationId = "historic"))
        val before = capture(prefs, listOf(scope))
        val first = DesktopLegacyMigrationPlanner.prepare(scope, before, emptyList())
        assertEquals(first, DesktopLegacyMigrationPlanner.prepare(scope, before.copy(stopped = before.stopped.reversed()), emptyList()))
        prefs.draftPersistence.stop(scope, "tx")
        val after = capture(prefs, listOf(scope))
        val second = DesktopLegacyMigrationPlanner.prepare(scope, after, emptyList())
        assertNotEquals(first.sourceDigest, second.sourceDigest)
        assertEquals(
            first.scopes
                .single()
                .draft!!
                .stoppedPending,
            second.scopes
                .single()
                .draft!!
                .stoppedPending,
        )
        assertNotEquals(
            second.sourceDigest,
            DesktopLegacyMigrationPlanner
                .prepare(
                    scope,
                    after.copy(counter = 41, floor = 41),
                    emptyList(),
                ).sourceDigest,
        )
    }

    @Test fun unboundOrContradictoryFenceAndFabricatedFloorCannotProducePlan() {
        val prefs = prefs()
        val pending = wire()
        prefs.pendingSubmissions.save(pending)
        prefs.fenceAttempts.begin(pending)
        val bound = capture(prefs, listOf(scope))
        assertFails { DesktopLegacyMigrationPlanner.prepare(scope, bound.copy(floor = 999), emptyList()) }
        assertFails {
            DesktopLegacyMigrationPlanner.prepare(
                scope,
                bound.copy(
                    fenceAttempts =
                        bound.fenceAttempts.map {
                            it.copy(wireDigest = "0".repeat(64))
                        },
                ),
                emptyList(),
            )
        }
        prefs.fenceAttempts.begin(wire(tx = "orphan", seq = 500))
        val orphan = capture(prefs, listOf(scope))
        assertFails { DesktopLegacyMigrationPlanner.prepare(scope, orphan, emptyList()) }
        assertFails { DesktopLegacyMigrationPlanner.prepare(scope, orphan.copy(unboundFenceAttempts = emptyList()), emptyList()) }
    }

    @Test fun namespaceFloorsSafetyOnlyScopesAndFrozenPlansPreserveEverySource() {
        val prefs = prefs()
        val foreign = scope.copy(userId = "foreign")
        prefs.pendingSubmissions.save(wire())
        prefs.pendingSubmissions.save(wire(foreign, "foreign", 800))
        val source = capture(prefs, listOf(scope, foreign))
        val deletionScope = scope.copy(workspaceId = "safety-only")
        val proof = CommittedDeletionEvidence(deletionScope, "object:a", 2, "deleted", 9000, "a".repeat(64))
        val captured = source.copy(deletionEvidence = listOf(proof))
        val plan = DesktopLegacyMigrationPlanner.prepare(scope, captured, emptyList())
        assertEquals(3, plan.scopes.size)
        assertEquals(20L, plan.scopes.single { it.scope == scope }.minimumClientSequence)
        assertEquals(800L, plan.scopes.single { it.scope == foreign }.minimumClientSequence)
        val safety = plan.scopes.single { it.scope == deletionScope }
        assertNull(safety.draft)
        assertEquals(listOf(proof), safety.safety.deletions)
        assertEquals(20L, safety.minimumClientSequence) // serverSeq is not clientSeq; same namespace sees workspace's 20.
        assertFails { DesktopLegacyMigrationPlanner.prepare(scope, captured.copy(deletionEvidence = listOf(proof, proof)), emptyList()) }
        assertFails {
            DesktopLegacyMigrationPlanner.prepare(
                scope,
                captured.copy(
                    deletionEvidence =
                        (0..256).map {
                            proof.copy(scope = deletionScope.copy(workspaceId = "scope-$it"))
                        },
                ),
                emptyList(),
            )
        }
    }
}
