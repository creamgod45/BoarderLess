package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.SequenceCanvasCreation
import kotlin.test.*

class WorkspaceContentTransactionTest {
    private val empty = Workspace(WorkspaceId("board"), "Board")
    private fun sequence(label: String): Workspace {
        val draft = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse(
            "sequenceDiagram\nparticipant A as $label\nparticipant B as Target\nA->>B: message")).draft
        val session = WorkspaceSession("user", "client", WorkspaceMemberRole.Owner, 0, 0, empty)
        var id = 0
        val operation = SequenceCanvasCreation(session, empty, Vec2.Zero).operation(draft, "creation") { "node-${++id}" }
        return assertIs<OperationResult.Applied>(operation.applyTo(empty)).workspace
    }
    private fun verify(before: Workspace, target: Workspace) {
        var id = 0
        val operation = assertNotNull(buildWorkspaceContentTransaction(before, target, { "op-${++id}" }))
        val result = WorkspaceHistory(before).execute(operation)
        assertTrue(result.succeeded)
        assertEquals(target.mergeContent(), result.history.workspace.mergeContent())
        assertEquals(before.version + 1, result.history.workspace.version)
        val undo = result.history.undo()
        assertTrue(undo.succeeded)
        assertEquals(before.mergeContent(), undo.history.workspace.mergeContent())
        val redo = undo.history.redo()
        assertTrue(redo.succeeded)
        assertEquals(target.mergeContent(), redo.history.workspace.mergeContent())
        assertTrue(DraftMergeContractGap.SequenceDiagrams in draftMergeContractGaps(before, operation))
    }
    @Test fun sequenceCreationModificationAndDeletionAreAtomicAndUndoable() {
        val first = sequence("First")
        val second = sequence("Second")
        verify(empty, first)
        verify(first, second)
        verify(first, empty)
    }
    @Test fun emptyDiffIgnoresDestinationRevisionsAndUnsupportedTitleIsNotDropped() {
        assertNull(buildWorkspaceContentTransaction(empty, empty.copy(version = 17, sequenceDiagramsVersion = 8)))
        assertFails { buildWorkspaceContentTransaction(empty, empty.copy(title = "Different")) }
        assertFails { buildWorkspaceContentTransaction(empty, empty.copy(id = WorkspaceId("other"))) }
    }
}
