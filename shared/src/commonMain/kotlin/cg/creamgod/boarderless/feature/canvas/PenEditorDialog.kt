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
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Authoring stays private until an explicit insert succeeds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PenEditorDialog(
    onDismiss: () -> Unit,
    onInsert: (suspend (VectorPath, String, TextPlacement?) -> PenCanvasInsertResult)? = null,
    initialText: String = "",
    initialTextPlacement: TextPlacement? = null,
    initialDraft: PenPathDraft = PenPathDraft(CanvasSize(400f, 300f)),
    editingExisting: Boolean = false,
    onReviewOriginalSubmission: (() -> Unit)? = null,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()
    var labelText by remember { mutableStateOf(initialText) }
    var textPlacement by remember { mutableStateOf(initialTextPlacement) }
    var history by remember { mutableStateOf(PenPathHistory(initialDraft)) }
    var gesture by remember { mutableStateOf<PenEditorGesture?>(null) }
    var selected by remember { mutableStateOf<Int?>(if (editingExisting) 0 else null) }
    var addMode by remember { mutableStateOf(!editingExisting) }
    var panMode by remember { mutableStateOf(false) }
    var showAlignment by remember { mutableStateOf(true) }
    var snapAlignment by remember { mutableStateOf(true) }
    var alignment by remember { mutableStateOf<PenAlignment?>(null) }
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
    val cameraBusy = saving || gesture != null || panning || colorEdit != null || attemptedPath != null
    val draft = colorEdit?.preview() ?: gesture?.working ?: history.draft
    val path = remember(draft) { if (draft.anchors.size >= 2) draft.toVectorPath() else null }

    fun edit(after: PenPathDraft) {
        history = history.edit(after)
        discardArmed = false
    }

    fun close() {
        if (saving) return
        if (attemptedPath != null) {
            // Closing an unconfirmed publication only closes this editor; it is not rollback.
            onDismiss()
            return
        }
        if (colorEdit !=
            null
        ) {
            colorEdit = null
        } else if ((history.draft == initialDraft && labelText == initialText && textPlacement == initialTextPlacement) || discardArmed) {
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
                            hint = if (editingExisting) Strings.penDraft.hintApplyPath() else Strings.penDraft.hintInsert(),
                            enabled =
                                !saving && attemptedPath == null && path != null && gesture == null && !panning && colorEdit == null,
                            onClick = {
                                val candidate = attemptedPath ?: path
                                if (candidate != null && !saving && attemptedPath == null) {
                                    val frozenText = labelText
                                    val frozenPlacement = textPlacement
                                    saving = true
                                    scope.launch {
                                        try {
                                            when (onInsert(candidate, frozenText, frozenPlacement)) {
                                                PenCanvasInsertResult.Saved -> onDismiss()
                                                PenCanvasInsertResult.Invalid -> {
                                                    attemptedPath = null
                                                    problem = Strings.penDraft.invalidPath()
                                                }
                                                PenCanvasInsertResult.Stale -> {
                                                    problem = Strings.penDraft.sourceChanged()
                                                }
                                                PenCanvasInsertResult.Retry, PenCanvasInsertResult.Unconfirmed -> {
                                                    attemptedPath = candidate
                                                    problem = Strings.penDraft.publicationUnconfirmed()
                                                }
                                            }
                                        } catch (cancelled: CancellationException) {
                                            attemptedPath = candidate
                                            problem = Strings.penDraft.publicationUnconfirmed()
                                            throw cancelled
                                        }
                                        catch (_: Exception) {
                                            attemptedPath = candidate
                                            problem = Strings.penDraft.publicationUnconfirmed()
                                        } finally { saving = false }
                                    }
                                }
                            },
                        )
                    }
                    problem?.let { BasicText(it, style = TextStyle(color = colors.accent, fontSize = 11.sp)) }
                    if (attemptedPath != null) ShellButton(Strings.common.close(), enabled = !saving, onClick = ::close)
                    if (attemptedPath != null && !saving) onReviewOriginalSubmission?.let { reviewOriginal ->
                        ShellButton(Strings.draftReview.originalTransactions(), onClick = { close(); reviewOriginal() })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                        ShellButton(Strings.penDraft.alignmentGuides(), accent = showAlignment, enabled = !cameraBusy,
                            hint = Strings.penDraft.alignmentHint(), onClick = { showAlignment = !showAlignment })
                        ShellButton(Strings.penDraft.alignmentSnap(), accent = snapAlignment, enabled = !cameraBusy,
                            hint = Strings.penDraft.alignmentSnapHint(), onClick = { snapAlignment = !snapAlignment })
                    }
                    // Bound tools to half the remaining area; long/mobile controls scroll rather
                    // than consume the drawing surface. The picker overlays without closing it.
                    Column(
                        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AiProbeField(Strings.textPosition.text(), labelText, { if (it.length <= 16_384) labelText = it }, enabled = !cameraBusy)
                        TextPlacementSettings(textPlacement, !cameraBusy) { textPlacement = it }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(Strings.penDraft.add(), hint = Strings.penDraft.hintAdd(), icon = ShellIcon.Add, accent = addMode && !panMode, enabled = !cameraBusy, onClick = {
                                addMode =
                                    true
                                ; panMode = false
                            })
                            ShellButton(Strings.penDraft.edit(), hint = Strings.penDraft.hintEdit(), icon = ShellIcon.Rename, accent = !addMode && !panMode, enabled = !cameraBusy, onClick = {
                                addMode =
                                    false
                                ; panMode = false
                            })
                            ShellButton(
                                Strings.penDraft.undo(), hint = Strings.penDraft.hintUndo(),
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
                                Strings.penDraft.redo(), hint = Strings.penDraft.hintRedo(),
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
                                hint = if (draft.closed) Strings.penDraft.hintOpen() else Strings.penDraft.hintClosePath(),
                                enabled =
                                    !cameraBusy && draft.anchors.size >= 2,
                                onClick = { edit(history.draft.copy(closed = !history.draft.closed)) },
                            )
                            ShellButton(
                                Strings.penDraft.fill(), hint = Strings.penDraft.hintFill(),
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
                                Strings.penDraft.fillColor(), hint = Strings.penDraft.hintFillColor(),
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = {
                                    colorEdit =
                                        PenStyleColorEdit(history.draft, PenPaint.Fill, history.draft.style.fillColorToken ?: "lilac")
                                },
                            )
                            ShellButton(
                                Strings.penDraft.strokeColor(), hint = Strings.penDraft.hintStrokeColor(),
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = {
                                    colorEdit =
                                        PenStyleColorEdit(history.draft, PenPaint.Stroke, history.draft.style.strokeColorToken ?: "ink")
                                },
                            )
                            listOf(
                                2f to (Strings.penDraft.strokeThin() to Strings.penDraft.hintStrokeThin()),
                                4f to (Strings.penDraft.strokeMedium() to Strings.penDraft.hintStrokeMedium()),
                                8f to (Strings.penDraft.strokeThick() to Strings.penDraft.hintStrokeThick()),
                            ).forEach { (width, texts) ->
                                ShellButton(
                                    texts.first,
                                    hint = texts.second,
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
                                Strings.penDraft.applyWidth(), hint = Strings.penDraft.hintApplyWidth(),
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
                                VectorFillRule.NonZero to (Strings.penDraft.nonZero() to Strings.penDraft.hintNonZero()),
                                VectorFillRule.EvenOdd to (Strings.penDraft.evenOdd() to Strings.penDraft.hintEvenOdd()),
                            ).forEach { (rule, texts) ->
                                ShellButton(
                                    texts.first,
                                    hint = texts.second,
                                    compact = true,
                                    accent = draft.style.fillRule == rule,
                                    enabled = !cameraBusy,
                                    onClick = { edit(history.draft.copy(style = history.draft.style.copy(fillRule = rule))) },
                                )
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(
                                Strings.penDraft.delete(), hint = Strings.penDraft.hintDelete(),
                                icon = ShellIcon.Delete,
                                enabled =
                                    !cameraBusy && selected?.let { it in draft.anchors.indices } == true,
                                onClick = {
                                    selected?.let { edit(history.draft.remove(it)) }
                                    selected = null
                                },
                            )
                            ShellButton(
                                Strings.penDraft.corner(), hint = Strings.penDraft.hintCorner(),
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
                                Strings.penDraft.curve(), hint = Strings.penDraft.hintCurve(),
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
                            ShellButton(if (discardArmed) Strings.penDraft.confirmDiscard() else Strings.common.close(), enabled = !saving, onClick = ::close)
                            if (discardArmed) ShellButton(Strings.common.cancel(), enabled = !saving, onClick = { discardArmed = false })
                        }
                        BasicText(Strings.penDraft.instructions(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(
                                Strings.penDraft.addCenterAnchor(), hint = Strings.penDraft.hintAddCenterAnchor(),
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
                                Strings.penDraft.previousAnchor(), hint = Strings.penDraft.hintPreviousAnchor(),
                                compact = true,
                                enabled =
                                    !cameraBusy && selected?.let { it > 0 && it in draft.anchors.indices } == true,
                                onClick = { selected = selected!! - 1 },
                            )
                            ShellButton(
                                Strings.penDraft.nextAnchor(), hint = Strings.penDraft.hintNextAnchor(),
                                compact = true,
                                enabled =
                                    !cameraBusy && draft.anchors.isNotEmpty() && (selected == null || selected!! < draft.anchors.lastIndex),
                                onClick = { selected = selected?.plus(1) ?: 0 },
                            )
                            ShellButton(
                                Strings.penDraft.focusAnchor(), hint = Strings.penDraft.hintFocusAnchor(),
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
                            ShellButton(Strings.penDraft.pan(), hint = Strings.penDraft.hintPan(), compact = true, accent = panMode, enabled = !cameraBusy, onClick = {
                                panMode =
                                    !panMode
                            })
                            ShellButton(
                                Strings.penDraft.zoomOut(), hint = Strings.penDraft.hintZoomOut(),
                                icon = ShellIcon.ZoomOut,
                                compact = true,
                                enabled =
                                    !cameraBusy && viewport.zoom > PenEditorViewport.MIN_ZOOM,
                                onClick = { viewport = viewport.zoomBy(.5f) },
                            )
                            ShellButton(
                                Strings.penDraft.zoomIn(), hint = Strings.penDraft.hintZoomIn(),
                                icon = ShellIcon.ZoomIn,
                                compact = true,
                                enabled =
                                    !cameraBusy && viewport.zoom < PenEditorViewport.MAX_ZOOM,
                                onClick = { viewport = viewport.zoomBy(2f) },
                            )
                            ShellButton(Strings.penDraft.fit(), hint = Strings.penDraft.hintFit(), compact = true, enabled = !cameraBusy, onClick = {
                                viewport =
                                    PenEditorViewport.fit(history.draft)
                            })
                            ShellButton(
                                Strings.penDraft.resetView(), hint = Strings.penDraft.hintResetView(),
                                icon = ShellIcon.ZoomReset,
                                compact = true,
                                enabled = !cameraBusy,
                                onClick = { viewport = PenEditorViewport.reset(history.draft.viewBox) },
                            )
                        }
                        if (saving) BasicText(Strings.status.savingChanges(1), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    }
                    Canvas(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clipToBounds()
                            .background(colors.canvas)
                            .onSizeChanged { previewSize = it }
                            .pointerInput(previewSize, panMode, showAlignment, snapAlignment, colorEdit != null, attemptedPath != null, saving) {
                                if (saving || colorEdit != null || attemptedPath != null) return@pointerInput
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
                                                if (event.changes.count { it.pressed } > 1) {
                                                    committed = true // Camera only; keep what the pan had shown.
                                                    val base = viewport
                                                    penPinch(base, penEditorTransform(latestHistory.draft.viewBox, base,
                                                        previewSize.width.toFloat(), previewSize.height.toFloat(), padding) ?: transform) { viewport = it }
                                                    break
                                                }
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
                                    val rawStart = local(down.position)
                                    val startAlignment = alignPenPoint(latestHistory.draft, rawStart, null, 6.dp.toPx() / transform.scale, snapAlignment)
                                    val start = if (latestAdd) startAlignment.point else rawStart
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
                                        alignment = if (showAlignment && created.handle == PenHandle.NewAnchor) startAlignment else null
                                        down.consume()
                                        try {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                if (event.changes.count { it.pressed } > 1) {
                                                    gesture = null; alignment = null // Drop the unfinished edit; no history step.
                                                    val base = latestViewport
                                                    penPinch(base, penEditorTransform(latestHistory.draft.viewBox, base,
                                                        previewSize.width.toFloat(), previewSize.height.toFloat(), padding) ?: transform) { viewport = it }
                                                    break
                                                }
                                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                if (change.position != change.previousPosition &&
                                                    (
                                                        created.handle != PenHandle.NewAnchor ||
                                                            (change.position - down.position).getDistance() >= viewConfiguration.touchSlop
                                                    )
                                                ) {
                                                    val (moved, guide) = created.moveAligned(local(change.position), 6.dp.toPx() / transform.scale, snapAlignment)
                                                    gesture = moved
                                                    alignment = if (showAlignment) guide else null
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
                                            alignment = null
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
                                if (labelText.isNotEmpty() && path != null) {
                                    val bounds = normalizePenPath(path)
                                    val label = textMeasurer.measure(labelText, labelTextStyle(textPlacement?.appearance, 1f, colors.contentText),
                                        constraints = androidx.compose.ui.unit.Constraints(maxWidth = bounds.path.viewBox.width.toInt().coerceAtLeast(1)))
                                    val position = textLayerPosition(textPlacement?.anchor ?: TextAnchor.TopLeft,
                                        bounds.path.viewBox, CanvasSize(label.size.width.toFloat(), label.size.height.toFloat()), 8f, 8f) +
                                        bounds.origin + Vec2(textPlacement?.offsetX ?: 0f, textPlacement?.offsetY ?: 0f)
                                    drawText(label, topLeft = Offset(position.x, position.y))
                                }
                                alignment?.let { guide ->
                                    guide.x?.let { x -> drawLine(colors.accent, Offset(x, 0f), Offset(x, draft.viewBox.height), strokeWidth = 1.dp.toPx() / scale) }
                                    guide.y?.let { y -> drawLine(colors.accent, Offset(0f, y), Offset(draft.viewBox.width, y), strokeWidth = 1.dp.toPx() / scale) }
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

/** Runs until fewer than two fingers remain. Pinch changes only the editor camera. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.penPinch(
    start: PenEditorViewport,
    transform: PenEditorTransform,
    onViewport: (PenEditorViewport) -> Unit,
) {
    var first: androidx.compose.ui.input.pointer.PointerId? = null
    var second: androidx.compose.ui.input.pointer.PointerId? = null
    var startA = Vec2.Zero
    var startB = Vec2.Zero
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size < 2) {
            event.changes.forEach { it.consume() }
            return
        }
        if (first == null || second == null || pressed.none { it.id == first } || pressed.none { it.id == second }) {
            val a = pressed[0]
            val b = pressed[1]
            first = a.id; second = b.id
            startA = Vec2(a.position.x, a.position.y); startB = Vec2(b.position.x, b.position.y)
            continue
        }
        val a = pressed.first { it.id == first }
        val b = pressed.first { it.id == second }
        onViewport(transform.pinchFrom(start, startA, startB, Vec2(a.position.x, a.position.y), Vec2(b.position.x, b.position.y)))
        event.changes.forEach { it.consume() }
    }
}
