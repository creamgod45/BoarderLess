package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class WorkspaceDraftLegacyMigrationTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
    private val head = CreateObjectsOperation("head", listOf(TextNode(CanvasObjectId("node"),
        transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f)), text = "Draft")))
    private val journal = WorkspaceDraftJournal(scope = scope, id = "draft", baseVersion = 8, baseServerSeq = 19,
        baseWorkspace = Workspace(WorkspaceId("workspace"), "Fixture"), operations = listOf(head))
    private val wire = PendingWorkspaceSubmission(scope, "head", SubmitOperationsRequest(clientId = "client",
        transactionId = "tx", baseVersion = 8, operations = listOf(OperationDto("wire", 20, "create_object",
            payload = buildJsonObject { put("text", "Draft") }))))
    private val source = WorkspaceDraftLegacySnapshot(scope, journal, wire)

    @Test fun partialLegacyStageCompletesMatchingHeadWithoutChangingWire() {
        val migrated = WorkspaceDraftLegacyMigration.prepare(source)
        assertEquals("tx", migrated.journal!!.headTransactionId)
        assertEquals(wire, migrated.pending)
        assertNull(source.journal!!.headTransactionId)
        assertEquals(migrated, WorkspaceDraftLegacyMigration.prepare(source))
        assertEquals(migrated.legacyImportDigest, WorkspaceDraftScopeBundleCodec.decode(
            WorkspaceDraftScopeBundleCodec.encode(migrated), scope).legacyImportDigest)
    }

    @Test fun unresolvedAckMismatchAndMissingWireStopWithoutDiscardingSource() {
        for (invalid in listOf(source.copy(journal = journal.copy(lastAcknowledgedTransactionId = "tx")),
            source.copy(journal = journal.copy(headTransactionId = "other")),
            source.copy(pending = wire.copy(localOperationId = "other")),
            source.copy(pending = null, journal = journal.copy(headTransactionId = "tx")),
            source.copy(journal = journal.copy(scope = scope.copy(userId = "other"))))) {
            assertFails { WorkspaceDraftLegacyMigration.prepare(invalid) }
        }
        assertEquals(wire, source.pending)
        assertEquals(journal, source.journal)
        assertEquals(wire, WorkspaceDraftLegacyMigration.prepare(source.copy(journal = null)).pending)
    }

    @Test fun adoptionMarkerSurvivesAppendStageAndAcknowledgement() {
        val migrated = WorkspaceDraftLegacyMigration.prepare(source.copy(pending = null))
        val marker = migrated.legacyImportDigest
        val staged = WorkspaceDraftBundleTransitions.stage(migrated, wire)
        val after = (head.applyTo(journal.baseWorkspace) as OperationResult.Applied).workspace
        val tail = EditTextOperation("tail", listOf(TextChange(CanvasObjectId("node"), 1, "Draft", "Edited")))
        val session = WorkspaceSession("actor", "client", WorkspaceMemberRole.Editor, 8, 19, journal.baseWorkspace)
        val queued = WorkspaceDraftBundleTransitions.append(staged, scope, session, after, tail, "unused")
        val acknowledged = WorkspaceDraftBundleTransitions.acknowledge(queued, wire, 9, 20)
        assertEquals(marker, staged.legacyImportDigest)
        assertEquals(marker, queued.legacyImportDigest)
        assertEquals(marker, acknowledged.legacyImportDigest)
        assertEquals(listOf(tail), acknowledged.journal!!.operations)
    }
}
