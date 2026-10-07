package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.Composable
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** Reuse controls share the same canvas mutation gate; keep composition out of the root method. */
@Composable internal fun WorkspaceReuseActions(
    workspace: Workspace,
    selection: Set<CanvasObjectId>,
    enabled: Boolean,
    relationGeometry: Boolean,
    vectorPaths: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDuplicate: () -> Unit,
    onLoop: () -> Unit,
    onPen: () -> Unit,
    onScheme: () -> Unit,
    onDelete: () -> Unit,
) {
    ShellButton(Strings.inspector.copy(), icon = ShellIcon.Copy, enabled = enabled, onClick = onCopy)
    ShellButton(Strings.inspector.cut(), icon = ShellIcon.Cut, enabled = enabled, onClick = onCut)
    ShellButton(Strings.inspector.duplicate(), icon = ShellIcon.Duplicate, enabled = enabled, onClick = onDuplicate)
    val node = selection.singleOrNull()?.let(workspace.objects::get)
    if (relationGeometry && (node is TextNode || node is MediaNode)) {
        ShellButton(Strings.relationGeometry.createLoop(), enabled = enabled, onClick = onLoop)
    }
    PenEditAction(workspace, selection, enabled, vectorPaths, onPen)
    ShellButton(Strings.inspector.saveAsScheme(), icon = ShellIcon.Schemes, enabled = enabled, onClick = onScheme)
    ShellButton(Strings.palette.deleteSelection(), icon = ShellIcon.Delete, enabled = enabled, onClick = onDelete)
}
