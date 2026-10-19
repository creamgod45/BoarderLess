package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.serialization.json.*
import kotlin.test.*

internal fun sequenceClipboardFixture(): Workspace {
    val w = Workspace(WorkspaceId("source"), "Source")
    val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 0, 0, w)
    val source = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse("""
        sequenceDiagram
        actor A as 使用者🙂
        participant B as 服務
        A->>B: request
        alt ready
        B-->>A: response
        else missing
        loop retry
        B->>B: self
        end
        end
        par first
        A-)B: async
        and second
        B->A: signal
        end
    """.trimIndent())).draft
    var n = 0
    val op = SequenceCanvasCreation(owner, w, Vec2(300f, 400f)).operation(source, "Sequence") { "source_${++n}" }
    return assertIs<OperationResult.Applied>(op.applyTo(w)).workspace
}

class SequenceClipboardTest {
    private fun payload(w: Workspace = sequenceClipboardFixture()) =
        assertNotNull(workspaceClipboardPayload(w, w.sequenceDiagrams.keys))

    @Test fun fullSelectionUsesV7AndPartialManagedSelectionNeverDropsMetadata() {
        val w = sequenceClipboardFixture()
        val diagram = w.sequenceDiagrams.values.single()
        val p = payload(w)
        assertEquals(7, p.version)
        assertEquals(listOf(diagram), p.sequenceDiagrams)
        assertEquals(diagram.allRelationIds(), p.relations.map { RelationId(assertNotNull(it.originalId)) }.toSet())
        assertNull(validateClipboardPayload(p))
        assertNull(workspaceClipboardPayload(w, setOf(diagram.participants.first().objectId)))
        assertNull(workspaceClipboardPayload(w, setOf(diagram.blocks.first().objectId)))
        val encoded = ClipboardJson.encodeToString(ClipboardPayload.serializer(), p)
        assertEquals(p, ClipboardJson.decodeFromString<ClipboardPayload>(encoded))
        val ordinary = ClipboardPayload(nodes = listOf(ClipboardNode("normal", 0f, 0f, 50f, 40f, 0f, "text", "paper")))
        val raw = ClipboardJson.encodeToString(ClipboardPayload.serializer(), ordinary)
        assertEquals(6, ordinary.version)
        assertFalse("sequenceDiagrams" in raw)
        assertFalse("originalId\":null" in raw)
    }

    @Test fun pasteRemapsEveryBindingOffsetsGeometryAndPublishesOnceWithActualUndoRedo() {
        val source = sequenceClipboardFixture()
        val original = source.sequenceDiagrams.values.single()
        val p = payload(source)
        var next = 0
        val insertion = prepareClipboardInsertion(p, source, defaultOffset = Vec2(80f, 90f)) { "copy_${++next}" }
        val operation = assertIs<TransactionOperation>(insertion.operation)
        assertEquals(3, operation.operations.size)
        val first = WorkspaceHistory(source).execute(operation)
        assertTrue(first.succeeded)
        val copied = first.history.workspace.sequenceDiagrams.values.single { it.containerId != original.containerId }
        assertEquals(original.toDraft(), copied.toDraft())
        assertTrue(original.allObjectIds().intersect(copied.allObjectIds()).isEmpty())
        assertTrue(original.allRelationIds().intersect(copied.allRelationIds()).isEmpty())
        assertEquals(setOf(copied.containerId), insertion.selectedIds)
        assertEquals(source.version + 1, first.history.workspace.version)
        assertEquals(source.sequenceDiagramsVersion + 1, first.history.workspace.sequenceDiagramsVersion)
        assertEquals(original, first.history.workspace.sequenceDiagrams.getValue(original.containerId))
        original.participants.zip(copied.participants).forEach { (old, new) ->
            val previous = source.objects.getValue(old.objectId)
            val pasted = first.history.workspace.objects.getValue(new.objectId)
            assertEquals(previous.transform.position + Vec2(80f, 90f), pasted.transform.position)
            assertEquals(copied.containerId, pasted.parentId)
        }
        val oldSelf = original.messages.single { it.message.isSelfMessage }
        val newSelf = copied.messages.single { it.message.isSelfMessage }
        assertEquals(source.relations.getValue(oldSelf.relationId).geometry!!.translated(Vec2(80f, 90f)),
            first.history.workspace.relations.getValue(newSelf.relationId).geometry)
        val undo = first.history.undo(remoteRestoration = true)
        assertTrue(undo.succeeded)
        assertEquals(source.objects, undo.history.workspace.objects)
        assertEquals(source.sequenceDiagrams, undo.history.workspace.sequenceDiagrams)
        val redo = undo.history.redo(remoteRestoration = true)
        assertTrue(redo.succeeded)
        assertEquals(copied, redo.history.workspace.sequenceDiagrams.getValue(copied.containerId))
        assertTrue(redo.history.workspace.hasValidSequenceBindings())
    }

    @Test fun missingDuplicatedOrMismatchedBindingsAndDowngradeAreRejectedBeforePreparation() {
        val p = payload()
        val bad = listOf(
            p.copy(version = 6),
            p.copy(sequenceDiagrams = p.sequenceDiagrams + p.sequenceDiagrams),
            p.copy(nodes = p.nodes.drop(1)),
            p.copy(relations = p.relations.drop(1)),
            p.copy(relations = p.relations.map { it.copy(originalId = null) }),
            p.copy(relations = p.relations.map { it.copy(originalId = "duplicate") }),
            p.copy(nodes = p.nodes.map { it.copy(text = "changed") }),
            p.copy(relations = p.relations.map { it.copy(label = "changed") }),
        )
        bad.forEach { invalid ->
            assertNotNull(validateClipboardPayload(invalid))
            var identities = 0
            assertFails { prepareClipboardInsertion(invalid, Workspace(WorkspaceId("dest"), "Dest")) { "new_${++identities}" } }
            assertEquals(0, identities)
        }
    }

    @Test fun schemeTransferPreservesCompleteMetadataAndInsertsInAnotherWorkspace() {
        val p = payload()
        val scheme = QuickScheme(1, "完整時序🙂", ClipboardJson.encodeToString(ClipboardPayload.serializer(), p), 7, "source")
        val transfer = QuickSchemeTransferCodec.encode(scheme)
        val received = QuickSchemeTransferCodec.decode(transfer)
        assertEquals(0, received.id)
        assertEquals(7, received.schemaVersion)
        assertEquals(p, decodeQuickSchemePayload(received))
        assertTrue(quickSchemeMediaAvailable(received, p, WorkspaceId("dest"), emptyMap()))
        val destination = Workspace(WorkspaceId("dest"), "Destination")
        val insertion = prepareClipboardInsertion(assertNotNull(decodeQuickSchemePayload(received)), destination, Vec2(10f, 20f))
        val pasted = assertIs<OperationResult.Applied>(insertion.operation.applyTo(destination)).workspace
        assertEquals(p.sequenceDiagrams.single().toDraft(), pasted.sequenceDiagrams.values.single().toDraft())
        assertEquals(Vec2(10f, 20f), pasted.objects.getValue(pasted.sequenceDiagrams.keys.single()).transform.position)
        assertTrue(pasted.hasValidSequenceBindings())
        assertFails { QuickSchemeTransferCodec.decode(transfer.replace("\"version\":7", "\"version\":7,\"version\":7")) }
    }

    @Test fun retryRetainsExactOperationAndRejectsScopeRoleOrSnapshotChanges() {
        val source = sequenceClipboardFixture()
        val destination = Workspace(WorkspaceId("dest"), "Dest")
        val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Owner, 0, 3, destination)
        val p = payload(source)
        val insertion = prepareClipboardInsertion(p, destination)
        val draft = ClipboardPasteDraft(owner, destination, p, insertion)
        assertTrue(draft.canRetry(owner, destination, true))
        assertSame(insertion.operation, draft.insertion.operation)
        assertFalse(draft.canRetry(owner.copy(role = WorkspaceMemberRole.Viewer), destination, true))
        assertFalse(draft.canRetry(owner.copy(clientId = "other"), destination, true))
        assertFalse(draft.canRetry(owner.copy(userId = "other"), destination, true))
        assertFalse(draft.canRetry(owner.copy(lastServerSeq = 4), destination, true))
        assertFalse(draft.canRetry(owner, destination.copy(title = "Changed"), true))
        assertFalse(draft.canRetry(owner, destination, false))
    }

    @Test fun wholeCutAndPasteRetainSemanticsWithAtomicDeletionUndo() {
        val w = sequenceClipboardFixture()
        val p = payload(w)
        val ids = p.sequenceDiagrams.single().allObjectIds()
        val delete = DeleteObjectsOperation("delete", ids.map(w.objects::getValue), w.relations.values.toList())
        val cut = WorkspaceHistory(w).execute(deleteSequenceAware(w, delete, "metadata"))
        assertTrue(cut.succeeded)
        assertTrue(cut.history.workspace.objects.isEmpty())
        assertTrue(cut.history.workspace.sequenceDiagrams.isEmpty())
        val undone = cut.history.undo(remoteRestoration = true)
        assertTrue(undone.succeeded)
        assertEquals(w.sequenceDiagrams, undone.history.workspace.sequenceDiagrams)
        val insertion = prepareClipboardInsertion(p, cut.history.workspace)
        val pasted = assertIs<OperationResult.Applied>(insertion.operation.applyTo(cut.history.workspace)).workspace
        assertEquals(w.sequenceDiagrams.values.single().toDraft(), pasted.sequenceDiagrams.values.single().toDraft())
    }
}
