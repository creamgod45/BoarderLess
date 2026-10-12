package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.model.*

/** Uses the same paint components/order as the live canvas; no handles or editor state. */
@Composable internal fun CanvasExportScene(
    workspace: Workspace, viewport: Viewport, labels: Map<RelationId, MeasuredRelationLabel>,
    mediaFrames: Map<CanvasObjectId, ImageBitmap>, background: Color, modifier: Modifier,
    readOnlyMediaPlaceholders: Boolean = false,
) {
    val media = workspace.objects.values.filterIsInstance<MediaNode>()
    if (!readOnlyMediaPlaceholders) require(media.map { it.id }.toSet() == mediaFrames.keys) { "Every exported media object needs a verified frozen frame" }
    val membership = SequenceCanvasMembership(workspace)
    val density = LocalDensity.current.density
    Box(modifier.background(background)) {
        membership.visibleGroups(workspace).sortedBy { it.zIndex }.forEach { group ->
            GroupFrameCard(group = group, titleVisible = group.id !in workspace.sequenceDiagrams,
                viewport = viewport, previewDelta = Vec2.Zero, selected = false, dropTarget = false,
                reduceMotion = true, interactionEnabled = false, mutationEnabled = false, selectionOnly = false,
                onAccessibilitySelect = {}, onKeyboardFocus = {}, onSelect = { _, _ -> },
                onDragPreview = {}, onDragCancel = {}, onDragCommit = {})
        }
        RelationCanvas(membership.visibleRelations(workspace), workspace.objects.values.connectableNodes().associateBy { it.id },
            viewport, density, emptyMap(), emptyMap(), null)
        labels.values.forEach { label -> RelationLabel(label.text, label.position + viewport.pan, false) {} }
        WorkspaceSequenceScenes(workspace, viewport, emptyMap(), emptyMap())
        membership.visibleNodes(workspace).sortedBy { it.zIndex }.forEach { node ->
            TextNodeCard(node, viewport, Vec2.Zero, null, false, true, false, false, false, false, false,
                onKeyboardFocus = {}, onSelect = {}, onStartEditing = {}, onDragPreview = { _, _ -> },
                onDragCancel = {}, onDragCommit = { _, _ -> }, onTextCommit = {})
        }
        if (readOnlyMediaPlaceholders) {
            workspace.objects.values.filter { it is MediaNode || it is ExternalMediaNode }.sortedBy { it.zIndex }.forEach { node ->
                ReadOnlyMediaPlaceholder(node, viewport)
            }
        } else media.sortedBy { it.zIndex }.forEach { node -> CanvasExportMediaCard(node, mediaFrames.getValue(node.id), viewport) }
    }
}

/** Historical metadata only: no source URL, ticket, playback, image decode or network access. */
@Composable private fun ReadOnlyMediaPlaceholder(node: CanvasObject, viewport: Viewport) {
    val density = LocalDensity.current
    val colors = BoarderLessTheme.colors
    val label = when (node) {
        is MediaNode -> "${node.mediaKind}: ${node.altText.ifBlank { node.assetId }}"
        is ExternalMediaNode -> "${node.reference.provider}: ${node.altText.ifBlank { node.reference.id }}"
        else -> error("Not a media object")
    }
    Box(Modifier.mediaPaintBounds(viewport.worldToScreen(node.transform.position),
        with(density) { (node.transform.size.width * viewport.zoom).toDp() },
        with(density) { (node.transform.size.height * viewport.zoom).toDp() },
        node.transform.rotationDegrees, false, colors)) {
        BasicText(label, Modifier.padding(6.dp), TextStyle(color = colors.contentMuted, fontSize = 10.sp), maxLines = 3)
    }
}
