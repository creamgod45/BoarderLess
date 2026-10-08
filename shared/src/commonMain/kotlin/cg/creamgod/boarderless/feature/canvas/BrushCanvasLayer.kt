package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.designsystem.color.*
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.hypot

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun BrushCanvasLayer(viewport: Viewport, readLive: () -> PenCanvasLive,
    commit: (WorkspaceOperation, CanvasObjectId) -> Boolean, onClose: () -> Unit,
    onToolbarHeight: (Int) -> Unit) {
    val colors = BoarderLessTheme.colors
    val latestLive by rememberUpdatedState(readLive)
    val latestCommit by rememberUpdatedState(commit)
    val latestToolbarHeight by rememberUpdatedState(onToolbarHeight)
    DisposableEffect(Unit) { onDispose { latestToolbarHeight(0) } }
    var points by remember { mutableStateOf<List<Vec2>>(emptyList()) }
    var pending by remember { mutableStateOf<BrushCanvasDraft?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var width by remember { mutableStateOf(4f) }
    var color by remember { mutableStateOf("#1976d2") }
    var picker by remember { mutableStateOf<String?>(null) }
    var colorModel by remember { mutableStateOf(ColorModel.Hsb) }
    val drawing = points.isNotEmpty()
    fun save(draft: BrushCanvasDraft) {
        if (!draft.isCurrent(latestLive())) { pending = draft; problem = Strings.brush.changed(); return }
        if (runCatching { latestCommit(draft.operation, draft.objectId) }.getOrDefault(false)) {
            pending = null; problem = null
        } else { pending = draft; problem = Strings.brush.failed() }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(viewport, width, color, picker) {
            // A save result must not restart the input coroutine while it handles release.
            // Restarting against the same mouse event can turn a held stroke into repeated taps.
            awaitEachGesture {
                val down = awaitFirstDown()
                if (pending != null || picker != null) { down.consume(); return@awaitEachGesture }
                val live = latestLive(); val owner = live.session
                if (!live.ready || owner == null) return@awaitEachGesture
                val samples = mutableListOf(viewport.screenToWorld(Vec2(down.position.x, down.position.y)))
                points = samples.toList(); problem = null; down.consume()
                var completed = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }) break
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val world = viewport.screenToWorld(Vec2(change.position.x, change.position.y))
                        val delta = world - samples.last()
                        if (!change.pressed || hypot(delta.x, delta.y) * viewport.zoom >= 1f) {
                            if (world != samples.last()) samples.add(world)
                            if (samples.size > 8192) { problem = Strings.brush.limit(); break }
                            points = samples.toList()
                        }
                        change.consume()
                        if (!change.pressed) { completed = true; break }
                    }
                    if (completed) {
                        val draft = runCatching { prepareBrushStroke(owner, live.workspace, samples, width, color, .75f / viewport.zoom, randomUuid(), randomUuid()) }.getOrNull()
                        if (draft == null) problem = Strings.brush.limit() else save(draft)
                    }
                } finally { points = emptyList() }
            }
        }) {
            val stroke = pending?.operation?.objects?.singleOrNull() as? TextNode
            if (stroke != null) {
                val path = requireNotNull(stroke.vectorPath)
                val p = viewport.worldToScreen(stroke.transform.position)
                withTransform({ translate(p.x, p.y); scale(viewport.zoom, viewport.zoom, Offset.Zero) }) {
                    drawVectorPath(path) { nodeFillColor(it, colors) }
                }
            } else if (points.isNotEmpty()) {
                val path = Path(); points.forEachIndexed { i, world ->
                    val p = viewport.worldToScreen(world); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                if (points.size == 1) {
                    val p = viewport.worldToScreen(points.single())
                    drawCircle(nodeFillColor(color, colors), width * viewport.zoom / 2f, Offset(p.x, p.y))
                } else drawPath(path, nodeFillColor(color, colors), style = Stroke(width * viewport.zoom, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        GlassSurface(Modifier.align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp).widthIn(max = 640.dp)
            .onSizeChanged { latestToolbarHeight(it.height) }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(Strings.brush.hint(), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap), verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
                    listOf(2f, 4f, 8f, 16f).forEach { value -> ShellButton(value.toInt().toString(), enabled = !drawing && pending == null, accent = width == value, onClick = { width = value }) }
                    ShellButton(Strings.colorPicker.title(), enabled = !drawing && pending == null, onClick = { picker = color })
                    ShellButton(Strings.common.close(), enabled = !drawing, onClick = onClose)
                }
                problem?.let { BasicText(it, style = TextStyle(color = colors.danger, fontSize = 12.sp)) }
                pending?.let { draft -> ShellButton(Strings.brush.retry(), enabled = draft.isCurrent(latestLive()), onClick = { save(draft) }) }
            }
        }
        picker?.let { before -> ColorPickerWindow(before, requireNotNull(customNodeColor(before)), emptyList(), emptyList(), colorModel,
            onModelChange = { colorModel = it }, onPreview = { color = it }, onApply = { color = it; picker = null },
            onCancel = { color = before; picker = null }, onEditingChange = {}) }
    }
}
