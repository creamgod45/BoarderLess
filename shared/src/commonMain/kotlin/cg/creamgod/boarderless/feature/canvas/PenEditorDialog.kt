package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.designsystem.color.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

/** Authoring stays private until an explicit insert succeeds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PenEditorDialog(
    onDismiss: () -> Unit,
    onInsert: ((VectorPath) -> PenCanvasInsertResult)? = null,
    initialDraft: PenPathDraft = PenPathDraft(CanvasSize(400f, 300f)),
    editingExisting: Boolean = false,
) {
    val colors = BoarderLessTheme.colors
    var history by remember { mutableStateOf(PenPathHistory(initialDraft)) }
    var gesture by remember { mutableStateOf<PenEditorGesture?>(null) }
    var selected by remember { mutableStateOf<Int?>(if (editingExisting) 0 else null) }
    var addMode by remember { mutableStateOf(!editingExisting) }
    var panMode by remember { mutableStateOf(false) }
    var panning by remember { mutableStateOf(false) }
    var viewport by remember {
        mutableStateOf(if (editingExisting) PenEditorViewport.fit(initialDraft) else PenEditorViewport.reset(history.draft.viewBox))
    }
    var discardArmed by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var colorEdit by remember { mutableStateOf<PenStyleColorEdit?>(null) }
    var colorModel by remember { mutableStateOf(ColorModel.Hsb) }
    var widthInput by remember { mutableStateOf("2") }
    LaunchedEffect(history.draft.style.strokeWidth) {
        widthInput =
            history.draft.style.strokeWidth
                .toString()
    }
    val latestHistory by rememberUpdatedState(history)
    val latestSelected by rememberUpdatedState(selected)
    val latestAdd by rememberUpdatedState(addMode)
    val latestViewport by rememberUpdatedState(viewport)
    var attemptedPath by remember { mutableStateOf<VectorPath?>(null) }
    val cameraBusy = gesture != null || panning || colorEdit != null || attemptedPath != null
    val draft = colorEdit?.preview() ?: gesture?.working ?: history.draft
    val path = remember(draft) { if (draft.anchors.size >= 2) draft.toVectorPath() else null }

    fun edit(after: PenPathDraft) {
        history = history.edit(after)
        discardArmed = false
    }

    fun close() {
        if (colorEdit !=
            null
        ) {
            colorEdit = null
        } else if (history.draft == initialDraft || discardArmed) {
            onDismiss()
        } else {
            discardArmed = true
        }
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 960.dp).fillMaxWidth(.95f).fillMaxHeight(.9f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(
                        if (editingExisting) Strings.penDraft.editTitle() else Strings.penDraft.title(),
                        style = TextStyle(color = colors.contentText, fontSize = 18.sp),
                    )
                    BasicText(
                        if (onInsert ==
                            null
                        ) {
                            Strings.penDraft.warning()
                        } else if (editingExisting) {
                            Strings.penDraft.editHint()
                        } else {
                            Strings.penDraft.insertHint()
                        },
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                    )
                    if (onInsert !=
                        null
                    ) {
                        ShellButton(
                            if (editingExisting) Strings.penDraft.applyPath() else Strings.penDraft.insert(),
                            enabled =
                                (attemptedPath != null || path != null) && gesture == null && !panning && colorEdit == null,
                            onClick = {
                                val candidate = attemptedPath ?: path
                                if (candidate != null) {
                                    when (onInsert(candidate)) {
                                        PenCanvasInsertResult.Saved -> {
                                            onDismiss()
                                        }

                                        PenCanvasInsertResult.Invalid -> {
                                            attemptedPath = null
                                            problem = Strings.penDraft.invalidPath()
                                        }

                                        else -> {
                                            attemptedPath = candidate
                                            problem = Strings.penDraft.insertFailed()
                                        }
                                    }
                                }
                            },
                        )
                    }
                    // Bound tools to half the remaining area; long/mobile controls scroll rather
                    // than consume the drawing surface. The picker overlays without closing it.
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(Strings.penDraft.add(), icon = ShellIcon.Add, accent = addMode && !panMode, enabled = !cameraBusy, onClick = {
                                addMode =
                                    true
                                ; panMode = false
                            })
                            ShellButton(Strings.penDraft.edit(), icon = ShellIcon.Rename, accent = !addMode && !panMode, enabled = !cameraBusy, onClick = {
                                addMode =
                                    false
                                ; panMode = false
                            })
                            ShellButton(
                                Strings.penDraft.undo(),
                                icon = ShellIcon.Undo,
                                enabled =
                                    !cameraBusy && history.undoStack.isNotEmpty(),
                                onClick = {
                                    history =
                                        history.undo()
                                    ; selected = null
                                    discardArmed = false
                                },
                            )
                            ShellButton(
                                Strings.penDraft.redo(),
                                icon = ShellIcon.Redo,
                                enabled =
                                    !cameraBusy && history.redoStack.isNotEmpty(),
                                onClick = {
                                    history =
                                        history.redo()
                                    ; selected = null
                                    discardArmed = false
                                },
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(
                                if (draft.closed) Strings.penDraft.open() else Strings.penDraft.closePath(),
                                enabled =
                                    !cameraBusy && draft.anchors.size >= 2,
                                onClick = { edit(history.draft.copy(closed = !history.draft.closed)) },
                            )
                            ShellButton(
                                Strings.penDraft.fill(),
                                accent = draft.style.fillColorToken != null,
                                enabled = !cameraBusy && (draft.style.fillColorToken == null || draft.style.strokeColorToken != null),
                                onClick = {
                                    edit(
                                        history.draft.copy(
                                            style =
                                                history.draft.style.withPaint(
                                                    PenPaint.Fill,
                                                    if (draft.style.fillColorToken ==
                                                        null
                                                    ) {
                                                        "lilac"
                                                    } else {
                                                        null
                                                    },
                                                ),
                                        ),
                                    )
                                },
                            )
                            ShellButton(
                                Strings.penDraft.stroke(),
                                accent = draft.style.strokeColorToken != null,
                                enabled = !cameraBusy && (draft.style.strokeColorToken == null || draft.style.fillColorToken != null),
                                onClick = {
                                    edit(
                                        history.draft.copy(
                                            style =
                                                history.draft.style.withPaint(
                                                    PenPaint.Stroke,
                                                    if (draft.style.strokeColorToken ==
                                                        null
                                                    ) {
                                                        "ink"
                                                    } else {
                                                        null
                                                    },
                                                ),
                                        ),
                                    )
                                },
                            )
                            ShellButton(
                                Strings.penDraft.fillColor(),
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = {
                                    colorEdit =
                                        PenStyleColorEdit(history.draft, PenPaint.Fill, history.draft.style.fillColorToken ?: "lilac")
                                },
                            )
                            ShellButton(
                                Strings.penDraft.strokeColor(),
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = {
                                    colorEdit =
                                        PenStyleColorEdit(history.draft, PenPaint.Stroke, history.draft.style.strokeColorToken ?: "ink")
                                },
                            )
                            listOf(
                                2f to Strings.penDraft.strokeThin(),
                                4f to Strings.penDraft.strokeMedium(),
                                8f to Strings.penDraft.strokeThick(),
                            ).forEach { (width, label) ->
                                ShellButton(
                                    label,
                                    compact = true,
                                    accent = draft.style.strokeWidth == width,
                                    enabled = !cameraBusy,
                                    onClick = { edit(history.draft.copy(style = history.draft.style.copy(strokeWidth = width))) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val widthLabel = Strings.penDraft.strokeWidth()
                            BasicTextField(
                                widthInput,
                                onValueChange = { if (it.length <= 16) widthInput = it },
                                singleLine = true,
                                enabled = !cameraBusy,
                                textStyle = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                cursorBrush = SolidColor(colors.accent),
                                modifier =
                                    Modifier
                                        .weight(
                                            1f,
                                        ).semantics { contentDescription = widthLabel }
                                        .background(colors.canvas)
                                        .padding(8.dp),
                                decorationBox = { field ->
                                    Column {
                                        BasicText(widthLabel, style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                                        field()
                                    }
                                },
                            )
                            ShellButton(
                                Strings.penDraft.applyWidth(),
                                compact = true,
                                enabled =
                                    !cameraBusy && parsePenStrokeWidth(widthInput) != null,
                                onClick = {
                                    parsePenStrokeWidth(
                                        widthInput,
                                    )?.let { edit(history.draft.copy(style = history.draft.style.copy(strokeWidth = it))) }
                                },
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                VectorFillRule.NonZero to Strings.penDraft.nonZero(),
                                VectorFillRule.EvenOdd to Strings.penDraft.evenOdd(),
                            ).forEach { (rule, label) ->
                                ShellButton(
                                    label,
                                    compact = true,
                                    accent = draft.style.fillRule == rule,
                                    enabled = !cameraBusy,
                                    onClick = { edit(history.draft.copy(style = history.draft.style.copy(fillRule = rule))) },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(
                                Strings.penDraft.delete(),
                                icon = ShellIcon.Delete,
                                enabled =
                                    !cameraBusy && selected?.let { it in draft.anchors.indices } == true,
                                onClick = {
                                    selected?.let { edit(history.draft.remove(it)) }
                                    selected = null
                                },
                            )
                            ShellButton(
                                Strings.penDraft.corner(),
                                enabled =
                                    !cameraBusy && selected?.let { it in draft.anchors.indices } == true,
                                onClick = {
                                    selected?.let {
                                        edit(
                                            history.draft.replace(it, history.draft.anchors[it].copy(incoming = null, outgoing = null)),
                                        )
                                    }
                                },
                            )
                            ShellButton(
                                Strings.penDraft.curve(),
                                enabled =
                                    !cameraBusy && selected?.let {
                                        it in draft.anchors.indices
                                    } == true,
                                onClick = {
                                    selected?.let { index ->
                                        runCatching { curvePenAnchor(history.draft, index) }
                                            .onSuccess {
                                                edit(it)
                                                problem = null
                                            }.onFailure { problem = Strings.penDraft.invalidCoordinates() }
                                    }
                                },
                            )
                            ShellButton(if (discardArmed) Strings.penDraft.confirmDiscard() else Strings.common.close(), onClick = ::close)
                            if (discardArmed) ShellButton(Strings.common.cancel(), onClick = { discardArmed = false })
                        }
                        BasicText(Strings.penDraft.instructions(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(
                                Strings.penDraft.addCenterAnchor(),
                                compact = true,
                                enabled = !cameraBusy && !draft.closed && draft.anchors.size < 1024,
                                onClick = {
                                    val original = history.draft
                                    edit(original.append(PenAnchor(Vec2(original.viewBox.width / 2, original.viewBox.height / 2))))
                                    selected = original.anchors.size
                                    addMode = false
                                    panMode = false
                                },
                            )
                            BasicText(
                                selected?.takeIf { it in draft.anchors.indices }?.let {
                                    Strings.penDraft.anchorPosition(
                                        it + 1,
                                        draft.anchors.size,
                                    )
                                }
                                    ?: Strings.penDraft.selectAnchor(draft.anchors.size),
                                style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                            )
                            ShellButton(
                                Strings.penDraft.previousAnchor(),
                                compact = true,
                                enabled =
                                    !cameraBusy && selected?.let { it > 0 && it in draft.anchors.indices } == true,
                                onClick = { selected = selected!! - 1 },
                            )
                            ShellButton(
                                Strings.penDraft.nextAnchor(),
                                compact = true,
                                enabled =
                                    !cameraBusy && draft.anchors.isNotEmpty() && (selected == null || selected!! < draft.anchors.lastIndex),
                                onClick = { selected = selected?.plus(1) ?: 0 },
                            )
                            ShellButton(
                                Strings.penDraft.focusAnchor(),
                                compact = true,
                                enabled =
                                    !cameraBusy && selected?.let { it in draft.anchors.indices } == true,
                                onClick = { selected?.let { viewport = viewport.copy(center = history.draft.anchors[it].point) } },
                            )
                        }
                        selected?.takeIf { it in history.draft.anchors.indices }?.let { index ->
                            PenCoordinatePanel(history.draft, index, enabled = !cameraBusy, onApply = ::edit)
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(Strings.penDraft.pan(), compact = true, accent = panMode, enabled = !cameraBusy, onClick = {
                                panMode =
                                    !panMode
                            })
                            ShellButton(
                                Strings.penDraft.zoomOut(),
                                icon = ShellIcon.ZoomOut,
                                compact = true,
                                enabled =
                                    !cameraBusy && viewport.zoom > PenEditorViewport.MIN_ZOOM,
                                onClick = { viewport = viewport.zoomBy(.5f) },
                            )
                            ShellButton(
                                Strings.penDraft.zoomIn(),
                                icon = ShellIcon.ZoomIn,
                                compact = true,
                                enabled =
                                    !cameraBusy && viewport.zoom < PenEditorViewport.MAX_ZOOM,
                                onClick = { viewport = viewport.zoomBy(2f) },
                            )
                            ShellButton(Strings.penDraft.fit(), compact = true, enabled = !cameraBusy, onClick = {
                                viewport =
                                    PenEditorViewport.fit(history.draft)
                            })
                            ShellButton(
                                Strings.penDraft.resetView(),
                                icon = ShellIcon.ZoomReset,
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = { viewport = PenEditorViewport.reset(history.draft.viewBox) },
                            )
                        }
                        problem?.let { BasicText(it, style = TextStyle(color = colors.accent, fontSize = 11.sp)) }
                    }
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(colors.canvas)
                            .onSizeChanged { previewSize = it }
                            .pointerInput(previewSize, panMode, colorEdit != null, attemptedPath != null) {
                                if (colorEdit != null || attemptedPath != null) return@pointerInput
                                val padding = 24.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val initialViewport = latestViewport
                                    val transform =
                                        penEditorTransform(
                                            latestHistory.draft.viewBox,
                                            initialViewport,
                                            previewSize.width.toFloat(),
                                            previewSize.height.toFloat(),
                                            padding,
                                        ) ?: return@awaitEachGesture

                                    fun local(point: Offset) = transform.local(Vec2(point.x, point.y))
                                    if (panMode) {
                                        panning = true
                                        down.consume()
                                        var committed = false
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                val delta = change.position - down.position
                                                viewport = transform.panFrom(initialViewport, Vec2(delta.x, delta.y))
                                                change.consume()
                                                if (!change.pressed) {
                                                    committed = true
                                                    break
                                                }
                                            }
                                        } finally {
                                            if (!committed) viewport = initialViewport
                                            panning = false
                                        }
                                        return@awaitEachGesture
                                    }
                                    val start = local(down.position)
                                    val created =
                                        beginPenEditorGesture(
                                            latestHistory,
                                            start,
                                            14.dp.toPx() / transform.scale,
                                            latestAdd,
                                            latestSelected,
                                        )
                                    problem = null
                                    selected = created?.index
                                    if (created != null) {
                                        gesture = created
                                        down.consume()
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } > 1) break
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.position != change.previousPosition &&
                                                    (
                                                        created.handle != PenHandle.NewAnchor ||
                                                            (change.position - down.position).getDistance() >= viewConfiguration.touchSlop
                                                    )
                                                ) {
                                                    gesture = gesture!!.move(local(change.position))
                                                }
                                                if (!change.pressed) {
                                                    history = gesture!!.commit()
                                                    discardArmed = false
                                                    change.consume()
                                                    break
                                                }
                                                change.consume()
                                            }
                                        } catch (
                                            _: IllegalArgumentException,
                                        ) {
                                            problem = Strings.penDraft.invalid()
                                        } finally {
                                            gesture = null
                                        } // Cancel/resize/multitouch drops preview without history mutation.
                                    }
                                }
                            },
                    ) {
                        val padding = 24.dp.toPx()
                        val transform = penEditorTransform(draft.viewBox, viewport, size.width, size.height, padding)
                        val scale = transform?.scale ?: 1f
                        if (transform != null) {
                            withTransform({
                                translate(transform.origin.x, transform.origin.y)
                                scale(scale, scale, Offset.Zero)
                            }) {
                                path?.let {
                                    drawVectorPath(it) { token ->
                                        if (token ==
                                            "ink"
                                        ) {
                                            colors.contentText
                                        } else {
                                            nodeFillColor(token, colors)
                                        }
                                    }
                                }
                                draft.anchors.forEachIndexed { index, anchor ->
                                    fun offset(point: Vec2) = Offset(point.x, point.y)
                                    if (index == selected) {
                                        listOfNotNull(anchor.incoming, anchor.outgoing).forEach { handle ->
                                            drawLine(colors.accent, offset(anchor.point), offset(handle), strokeWidth = 1.dp.toPx() / scale)
                                            drawCircle(colors.accent, radius = 4.dp.toPx() / scale, center = offset(handle))
                                        }
                                    }
                                    drawCircle(
                                        if (index == selected) colors.accent else colors.contentText,
                                        radius = 5.dp.toPx() / scale,
                                        center = offset(anchor.point),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            colorEdit?.let { session ->
                val initialToken =
                    when (session.target) {
                        PenPaint.Fill -> {
                            session.before.style.fillColorToken ?: "lilac"
                        }

                        PenPaint.Stroke -> {
                            session.before.style.strokeColorToken
                                ?: "ink"
                        }
                    }
                val initial = if (initialToken == "ink") colors.contentText else nodeFillColor(initialToken, colors)
                key(session.before, session.target) {
                    ColorPickerWindow(
                        initialToken = initialToken,
                        initialColor = RgbColor(initial.red, initial.green, initial.blue),
                        presets =
                            (listOf("ink") + NodeColorPresetTokens).map { token ->
                                ColorSwatchOption(
                                    token,
                                    nodeColorLabel(token),
                                    if (token == "ink") colors.contentText else presetNodeColor(token, colors),
                                )
                            },
                        recentColors =
                            (listOf(history.draft) + history.undoStack.asReversed())
                                .flatMap {
                                    listOfNotNull(it.style.fillColorToken, it.style.strokeColorToken).mapNotNull(::customNodeColor)
                                }.distinctBy { it.toHex() }
                                .take(12),
                        model = colorModel,
                        onModelChange = { colorModel = it },
                        onPreview = { token -> colorEdit = colorEdit?.copy(token = token) },
                        onApply = { token ->
                            colorEdit?.copy(token = token)?.let {
                                history = it.commit(history)
                                discardArmed = false
                            }
                            colorEdit = null
                        },
                        onCancel = { colorEdit = null },
                        onEditingChange = {},
                        title = if (session.target == PenPaint.Fill) Strings.penDraft.fillColor() else Strings.penDraft.strokeColor(),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .widthIn(max = 420.dp)
                                .fillMaxWidth(.95f)
                                .heightIn(max = 540.dp),
                        fieldSize = 140.dp,
                    )
                }
            }
        }
    }
}
