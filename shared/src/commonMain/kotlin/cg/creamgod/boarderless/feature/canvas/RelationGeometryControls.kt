package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

internal fun geometryFromWaypointText(
    before: RelationGeometry?,
    text: String,
): RelationGeometry {
    require(text.length <= 8192)
    val parts = text.split(';')
    require(parts.size in 1..32)
    val points =
        parts.map { part ->
            val xy = part.trim().split(',')
            require(xy.size == 2)
            Vec2(xy[0].trim().toFloat(), xy[1].trim().toFloat())
        }
    return (before ?: RelationGeometry()).copy(route = RelationRoute.Manual(points))
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RelationGeometryControls(
    relation: Relation,
    nodes: Map<CanvasObjectId, CanvasObject>,
    enabled: Boolean,
    onApply: (RelationGeometry) -> Unit,
    onEditingChange: (Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val geometry = relation.geometry ?: RelationGeometry()
    val loop = relation.sourceObjectId == relation.targetObjectId
    var text by remember(relation.id, relation.version) {
        mutableStateOf((geometry.route as? RelationRoute.Manual)?.waypoints?.joinToString("; ") { "${it.x},${it.y}" }.orEmpty())
    }
    var resetDraft by remember(relation.id, relation.version) { mutableStateOf(false) }
    var selectedPoint by remember(relation.id, relation.version) { mutableStateOf<Int?>(null) }
    var pointEditPending by remember(relation.id, relation.version) { mutableStateOf(false) }
    var batchFocused by remember { mutableStateOf(false) }
    var xFocused by remember { mutableStateOf(false) }
    var yFocused by remember { mutableStateOf(false) }
    val editing by rememberUpdatedState(onEditingChange)
    DisposableEffect(Unit) { onDispose { batchFocused = false; xFocused = false; yFocused = false; editing(false) } }
    val draft = if (loop) null else if (resetDraft) geometry.copy(route = RelationRoute.Auto)
        else runCatching { geometryFromWaypointText(relation.geometry, text) }.getOrNull()
    BasicText(Strings.relationGeometry.title(), style = TextStyle(color = colors.contentText, fontSize = 13.sp))
    BasicText(Strings.relationGeometry.dragHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
    if (loop) {
        val path = geometry.route as? RelationRoute.Loop ?: RelationRoute.Loop()
        listOf(
            "top" to Strings.relationGeometry.top(),
            "right" to Strings.relationGeometry.right(),
            "bottom" to Strings.relationGeometry.bottom(),
            "left" to Strings.relationGeometry.left(),
        ).forEach { (side, label) ->
            ShellButton(
                label = label,
                enabled = enabled,
                accent = path.side == side,
                onClick = { onApply(geometry.copy(route = path.copy(side = side))) },
            )
        }
        BasicText(Strings.relationGeometry.extent(path.extent.toInt()), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        ShellButton(
            label = Strings.relationGeometry.smaller(),
            enabled = enabled && path.extent > 16,
            onClick = { onApply(geometry.copy(route = path.copy(extent = (path.extent - 16).coerceAtLeast(16f)))) },
        )
        ShellButton(
            label = Strings.relationGeometry.larger(),
            enabled = enabled && path.extent < 4096,
            onClick = { onApply(geometry.copy(route = path.copy(extent = (path.extent + 16).coerceAtMost(4096f)))) },
        )
    } else {
        BasicText(Strings.relationGeometry.waypointHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        BasicTextField(
            text,
            { text = it.take(8192); resetDraft = false; selectedPoint = null; pointEditPending = false },
            enabled = enabled,
            modifier = Modifier.width(244.dp).heightIn(min = 48.dp).onFocusChanged {
                batchFocused = it.isFocused; editing(batchFocused || xFocused || yFocused)
            },
            textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
        )
        val manualPoints = (draft?.route as? RelationRoute.Manual)?.waypoints.orEmpty()
        if (manualPoints.isNotEmpty()) {
            BasicText(Strings.relationGeometry.pointDraftHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                manualPoints.indices.forEach { index ->
                    ShellButton(Strings.relationGeometry.pointNumber(index + 1), compact = true, enabled = enabled && !pointEditPending,
                        accent = selectedPoint == index, onClick = { selectedPoint = index })
                }
            }
            selectedPoint?.takeIf { it in manualPoints.indices }?.let { index ->
                val point = manualPoints[index]
                var pointX by remember(relation.id, relation.version, index, text) { mutableStateOf(point.x.toString()) }
                var pointY by remember(relation.id, relation.version, index, text) { mutableStateOf(point.y.toString()) }
                DisposableEffect(index) { onDispose { xFocused = false; yFocused = false; editing(batchFocused) } }
                BasicText("X", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                BasicTextField(pointX, { pointX = it.take(24); pointEditPending = true }, enabled = enabled,
                    modifier = Modifier.width(244.dp).heightIn(min = 36.dp).onFocusChanged {
                        xFocused = it.isFocused; editing(batchFocused || xFocused || yFocused)
                    }, textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp))
                BasicText("Y", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                BasicTextField(pointY, { pointY = it.take(24); pointEditPending = true }, enabled = enabled,
                    modifier = Modifier.width(244.dp).heightIn(min = 36.dp).onFocusChanged {
                        yFocused = it.isFocused; editing(batchFocused || xFocused || yFocused)
                    }, textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp))
                val edited = runCatching {
                    (checkNotNull(draft).route as RelationRoute.Manual).copy(waypoints = manualPoints.mapIndexed { i, existing ->
                        if (i == index) Vec2(pointX.toFloat(), pointY.toFloat()) else existing
                    })
                }.getOrNull()
                ShellButton(Strings.relationGeometry.stagePoint(), enabled = enabled && edited != null, onClick = {
                    edited?.let { text = it.waypoints.joinToString("; ") { p -> "${p.x},${p.y}" }; resetDraft = false; pointEditPending = false }
                })
                if (pointEditPending) ShellButton(Strings.common.cancel(), enabled = enabled, onClick = {
                    pointX = point.x.toString(); pointY = point.y.toString(); pointEditPending = false
                })
                ShellButton(Strings.relationGeometry.removePoint(index + 1), enabled = enabled, onClick = {
                    val remaining = manualPoints.filterIndexed { i, _ -> i != index }
                    text = remaining.joinToString("; ") { p -> "${p.x},${p.y}" }
                    resetDraft = remaining.isEmpty()
                    selectedPoint = null
                    pointEditPending = false
                    xFocused = false; yFocused = false; editing(batchFocused)
                })
            }
        }
        if (resetDraft) BasicText(Strings.relationGeometry.lastPointReset(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        if (draft != null) {
            val points = runCatching { relationWorldRoute(relation.copy(geometry = draft), nodes) }.getOrNull()
            if (!points.isNullOrEmpty()) {
                Canvas(Modifier.width(244.dp).height(100.dp)) {
                    val minX = points.minOf { it.x }
                    val minY = points.minOf { it.y }
                    val spanX = (points.maxOf { it.x } - minX).coerceAtLeast(1f)
                    val spanY = (points.maxOf { it.y } - minY).coerceAtLeast(1f)
                    val scale = minOf((size.width - 20f) / spanX, (size.height - 20f) / spanY)

                    fun screen(point: Vec2) = Offset(10f + (point.x - minX) * scale, 10f + (point.y - minY) * scale)
                    points.zipWithNext().forEach { (a, b) -> drawLine(colors.accent, screen(a), screen(b), 2f) }
                    drawCircle(colors.contentText, 3f, screen(points.first()))
                    drawCircle(colors.accent, 3f, screen(points.last()))
                }
            }
        }
        ShellButton(
            label = Strings.relationGeometry.applyRouteDraft(),
            enabled = enabled && draft != null && !pointEditPending,
            onClick = { draft?.let(onApply) },
        )
    }
    val originalText = (geometry.route as? RelationRoute.Manual)?.waypoints?.joinToString("; ") { "${it.x},${it.y}" }.orEmpty()
    val routeDraftDirty = !loop && (resetDraft || text != originalText || pointEditPending)
    if (routeDraftDirty) ShellButton(Strings.relationGeometry.discardRouteDraft(), enabled = enabled, onClick = {
        text = originalText; resetDraft = false; selectedPoint = null; pointEditPending = false
    })
    ShellButton(
        label = Strings.relationGeometry.resetRoute(),
        enabled = enabled && geometry.route != RelationRoute.Auto,
        onClick = { onApply(geometry.copy(route = RelationRoute.Auto)) },
    )
    val manual = geometry.labelPlacement is RelationLabelPlacement.Manual
    ShellButton(
        label = if (manual) Strings.relationGeometry.resetLabel() else Strings.relationGeometry.offsetLabel(),
        enabled = enabled && !routeDraftDirty,
        onClick = {
            onApply(
                geometry.copy(labelPlacement = if (manual) RelationLabelPlacement.Auto else RelationLabelPlacement.Manual(0.5f)),
            )
        },
    )
    if (manual) {
        val placement = geometry.labelPlacement as RelationLabelPlacement.Manual
        ShellButton(
            label = Strings.relationGeometry.labelNearer(),
            enabled = enabled && !routeDraftDirty && placement.normalOffset > -4096f,
            onClick = {
                onApply(
                    geometry.copy(labelPlacement = placement.copy(normalOffset = (placement.normalOffset - 16f).coerceAtLeast(-4096f))),
                )
            },
        )
        ShellButton(
            label = Strings.relationGeometry.labelFarther(),
            enabled = enabled && !routeDraftDirty && placement.normalOffset < 4096f,
            onClick = {
                onApply(
                    geometry.copy(labelPlacement = placement.copy(normalOffset = (placement.normalOffset + 16f).coerceAtMost(4096f))),
                )
            },
        )
    }
}
