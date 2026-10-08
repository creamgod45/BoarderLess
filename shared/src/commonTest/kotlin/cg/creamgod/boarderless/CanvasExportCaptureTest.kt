package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.sequence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class CanvasExportCaptureTest {
    private val group = GroupFrame(CanvasObjectId("g"), transform = CanvasTransform(Vec2.Zero, CanvasSize(400f, 200f)), title = "Group")
    private val node = TextNode(CanvasObjectId("n"), parentId = group.id,
        transform = CanvasTransform(Vec2(20f, 20f), CanvasSize(100f, 50f)), text = "Outside", textPlacement = TextPlacement(TextAnchor.Above))
    private val other = node.copy(id = CanvasObjectId("other"), parentId = null)
    private val workspace = Workspace(WorkspaceId("w"), "W", objects = listOf(group, node, other).associateBy { it.id })
    private val owner = WorkspaceSession("u", "c", WorkspaceMemberRole.Viewer, 0, 0, workspace)
    @Test fun groupIncludesDescendantsAndStandaloneSelectionKeepsVersions() {
        val groupExport = CanvasExportCapture(owner, workspace, "epoch", setOf(group.id))
        assertEquals(setOf(group.id, node.id), groupExport.scene.objects.keys)
        assertEquals(node, groupExport.scene.objects[node.id])
        val selected = CanvasExportCapture(owner, workspace, "epoch", setOf(node.id))
        assertEquals(node.copy(parentId = null), selected.scene.objects[node.id])
        assertEquals(workspace, selected.baseline)
        assertFails { CanvasExportCapture(owner, workspace, "epoch", emptySet()) }
        assertFails { CanvasExportCapture(owner, workspace, "epoch", setOf(CanvasObjectId("missing"))) }
    }
    @Test fun fullSnapshotIdentityRoleAndReconnectAreFencedForReadOnlyExport() {
        val capture = CanvasExportCapture(owner, workspace, "epoch")
        val live = CanvasExportLive(owner, workspace, "epoch", true)
        assertTrue(capture.isCurrent(live))
        assertFalse(capture.isCurrent(live.copy(ready = false)))
        assertFalse(capture.isCurrent(live.copy(epoch = "reconnected")))
        assertFalse(capture.isCurrent(live.copy(session = owner.copy(clientId = "new"))))
        assertFalse(capture.isCurrent(live.copy(session = owner.copy(userId = "new"))))
        assertFalse(capture.isCurrent(live.copy(session = owner.copy(role = WorkspaceMemberRole.Editor))))
        assertFalse(capture.isCurrent(live.copy(session = owner.copy(lastServerSeq = 1))))
        assertFalse(capture.isCurrent(live.copy(workspace = workspace.copy(title = "Changed"))))
        val mutable = workspace.objects.toMutableMap()
        val aliased = workspace.copy(objects = mutable)
        val frozen = CanvasExportCapture(owner.copy(workspace = aliased), aliased, "epoch")
        mutable.clear()
        assertEquals(3, frozen.scene.objects.size)
        assertFalse(frozen.isCurrent(live.copy(session = owner.copy(workspace = aliased), workspace = aliased)))
    }
    @Test fun partialManagedSequenceIsRejectedAndRootSelectionKeepsSemanticBindings() {
        val base = Workspace(WorkspaceId("seq"), "Sequence")
        val editor = WorkspaceSession("u", "c", WorkspaceMemberRole.Editor, 0, 0, base)
        val draft = assertIs<SequenceParseResult.Parsed>(MermaidSequenceAdapter.parse("sequenceDiagram\nparticipant A\nparticipant B\nA->>B: Hello")).draft
        var id = 0
        val operation = SequenceCanvasCreation(editor, base, Vec2.Zero).operation(draft, "Sequence") { "seq_${++id}" }
        val saved = WorkspaceHistory(base).execute(operation).history.workspace
        val diagram = saved.sequenceDiagrams.values.single()
        val viewer = editor.copy(role = WorkspaceMemberRole.Viewer, workspace = saved)
        val participant = (diagram.allObjectIds() - diagram.containerId).first()
        assertFails { CanvasExportCapture(viewer, saved, "epoch", setOf(participant)) }
        val complete = CanvasExportCapture(viewer, saved, "epoch", setOf(diagram.containerId))
        assertEquals(saved, complete.scene)
        assertTrue(complete.scene.hasValidSequenceBindings())
    }
}
