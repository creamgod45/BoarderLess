package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.domain.model.*
import kotlin.math.roundToInt

internal data class RelationLabelDragTarget(
    val id: RelationId,
    val center: Vec2,
    val size: CanvasSize,
)

/** Only small handle/label boxes receive pointer input; the rest of the canvas stays interactive. */
@Composable
internal fun RelationGeometryDragHandles(
    relation: Relation,
    rendered: Relation,
    workspace: Workspace,
    viewport: Viewport,
    density: Float,
    label: RelationLabelDragTarget?,
    enabled: Boolean,
    onBegin: (RelationGeometryHandle, String) -> Boolean,
    onMove: (String, Vec2) -> Unit,
    onEnd: (String) -> Unit,
    onCancel: (String) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val points = remember(rendered, workspace.objects) { runCatching { relationWorldRoute(rendered, workspace.objects) }.getOrNull() }
    if (!enabled || points == null) return
    val route = relation.geometry?.route ?: RelationRoute.Auto
    val handles =
        if (relation.sourceObjectId == relation.targetObjectId) {
            listOf(RelationGeometryHandle.LoopExtent to ((points[1] + points[2]) * 0.5f))
        } else if (route is RelationRoute.Manual) {
            val preview = (rendered.geometry?.route as? RelationRoute.Manual)?.waypoints ?: route.waypoints
            preview.mapIndexed { index, point -> RelationGeometryHandle.Waypoint(index) to point }
        } else {
            listOf(
                RelationGeometryHandle.NewWaypoint to
                    ((rendered.geometry?.route as? RelationRoute.Manual)?.waypoints?.singleOrNull() ?: polylineMidpoint(points)),
            )
        }
    if (label?.id == relation.id) {
        val handle = RelationGeometryHandle.Label(viewport.screenToWorld(label.center))
        // Key by label identity rather than its moving centre, so previews cannot restart a gesture.
        Box(
            Modifier
                .offset {
                    IntOffset(
                        (label.center.x - label.size.width / 2).roundToInt(),
                        (label.center.y - label.size.height / 2).roundToInt(),
                    )
                }.size((label.size.width / density).dp, (label.size.height / density).dp)
                .relationGeometryPointer(relation, viewport, handle, enabled, onBegin, onMove, onEnd, onCancel),
        )
    }
    // Explicit route handles win when a label overlaps the same pointer area.
    handles.forEach { (handle, point) ->
        val center = viewport.worldToScreen(point)
        Box(
            Modifier
                .offset { IntOffset((center.x - 14 * density).roundToInt(), (center.y - 14 * density).roundToInt()) }
                .size(28.dp)
                .relationGeometryPointer(relation, viewport, handle, enabled, onBegin, onMove, onEnd, onCancel),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(colors.canvas, 7 * density)
                drawCircle(colors.selection, 5 * density)
            }
        }
    }
}

@Composable
private fun Modifier.relationGeometryPointer(
    relation: Relation,
    viewport: Viewport,
    handle: RelationGeometryHandle,
    enabled: Boolean,
    onBegin: (RelationGeometryHandle, String) -> Boolean,
    onMove: (String, Vec2) -> Unit,
    onEnd: (String) -> Unit,
    onCancel: (String) -> Unit,
): Modifier {
    val begin by rememberUpdatedState(onBegin)
    val move by rememberUpdatedState(onMove)
    val end by rememberUpdatedState(onEnd)
    val cancel by rememberUpdatedState(onCancel)
    val target by rememberUpdatedState(handle)
    val key = if (handle is RelationGeometryHandle.Label) "label" else handle
    return pointerInput(relation.id, relation.version, viewport, key, enabled) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val token = randomUuid()
            if (!begin(target, token)) return@awaitEachGesture
            down.consume()
            var moved = false
            val motion = ObjectSelectionGesture(false, if (down.type == PointerType.Touch) viewConfiguration.touchSlop else 0f)
            try {
                val completed =
                    drag(down.id) { change ->
                        val amount = change.positionChange()
                        if (motion.move(Vec2(amount.x, amount.y))) {
                            moved = true
                            change.consume()
                            move(token, motion.displacement)
                        }
                    }
                if (completed && moved) end(token) else cancel(token)
            } finally {
                cancel(token)
            }
        }
    }
}
