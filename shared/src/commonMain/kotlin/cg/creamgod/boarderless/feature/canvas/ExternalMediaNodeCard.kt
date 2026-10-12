package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** URLs/frames live only in this visible scope. Reopening always resolves the durable ID again. */
@Composable internal fun ExternalMediaNodeCard(
    node: ExternalMediaNode, viewport: Viewport, canvasSize: IntSize, previewDelta: Vec2, previewTransform: CanvasTransform?,
    keyAvailable: Boolean, resolution: GiphyReferenceResolution, onRetry: () -> Unit, runtime: MediaImportRuntime, reduceMotion: Boolean, selected: Boolean,
    interactionEnabled: Boolean, mutationEnabled: Boolean, canPublish: () -> Boolean, onSelect: () -> Unit, onDragPreview: (Vec2) -> Unit, onDragCancel: () -> Unit, onDragCommit: (Vec2) -> Unit,
) {
    val latestCanPublish by rememberUpdatedState(canPublish)
    val colors = BoarderLessTheme.colors
    val density = LocalDensity.current
    val currentSelect by rememberUpdatedState(onSelect)
    val currentPreview by rememberUpdatedState(onDragPreview)
    val currentCancel by rememberUpdatedState(onDragCancel)
    val currentCommit by rememberUpdatedState(onDragCommit)
    val transform = previewTransform ?: node.transform
    val position = viewport.worldToScreen(transform.position + previewDelta)
    val radius = kotlin.math.hypot(transform.size.width, transform.size.height) * viewport.zoom
    val visible = position.x + radius >= 0 && position.y + radius >= 0 &&
        position.x - radius <= canvasSize.width && position.y - radius <= canvasSize.height
    val item = (resolution as? GiphyReferenceResolution.Ready)?.item
    var bitmap by remember(node.reference) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item, visible, runtime) {
        bitmap = null
        if (!visible || item == null || !latestCanPublish()) return@LaunchedEffect
        val url = giphyStillUrl(item)
        if (url != null && runtime.loadGiphyStill != null) {
            try {
                val decoded = runtime.loadGiphyStill.invoke(url)
                currentCoroutineContext().ensureActive()
                if (latestCanPublish()) bitmap = decoded
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { currentCoroutineContext().ensureActive(); bitmap = null }
        }
    }
    val naturalWidth = with(density) { transform.size.width.toDp() }
    val naturalHeight = with(density) { transform.size.height.toDp() }
    val contentScale = minOf(naturalWidth.value / 240f, naturalHeight.value / 360f)
    Box(Modifier.canvasObjectBounds(position, naturalWidth * viewport.zoom, naturalHeight * viewport.zoom)
        .graphicsLayer { rotationZ = transform.rotationDegrees }
        .zIndex(node.zIndex.toFloat()).background(colors.contentSurface)
        .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.contentBorder)) {
        Column(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(240.dp, 360.dp)
            .graphicsLayer { scaleX = viewport.zoom * contentScale; scaleY = viewport.zoom * contentScale; transformOrigin = TransformOrigin(0f, 0f) }
            .padding(8.dp)) {
            BasicText(node.altText.ifBlank { "GIPHY · ${node.reference.id}" },
                style = TextStyle(color = colors.contentText, fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .semantics {
                        role = Role.Button
                        onClick(label = Strings.a11y.selectThought()) {
                            if (interactionEnabled) { currentSelect(); true } else false
                        }
                    }
                    .pointerInput(interactionEnabled, mutationEnabled, node.locked, node.id, node.version, viewport.zoom, contentScale, transform.rotationDegrees) {
                        if (interactionEnabled) awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            down.consume()
                            val motion = ObjectSelectionGesture(false,
                                if (down.type == PointerType.Touch) viewConfiguration.touchSlop else 0f)
                            var moved = false
                            var delta = Vec2.Zero
                            val completed = drag(down.id) { change ->
                                val amount = change.positionChange()
                                if (motion.move(Vec2(amount.x, amount.y)) && mutationEnabled && !node.locked) {
                                    if (!moved) currentSelect()
                                    moved = true
                                    change.consume()
                                    // The graphics layer already converts pointer pixels to the header's
                                    // natural size. Apply its object scale and rotation once, without zoom twice.
                                    delta = objectLocalDragToWorld(motion.displacement * contentScale, transform.rotationDegrees, 1f)
                                    currentPreview(delta)
                                }
                            }
                            when {
                                !completed -> currentCancel()
                                moved -> currentCommit(delta)
                                motion.canTap -> currentSelect()
                            }
                        }
                    })
            if (!keyAvailable) BasicText(Strings.giphy.invalidKey(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            else {
                val hint = when (resolution) {
                    GiphyReferenceResolution.Pending -> Strings.giphy.loadingReference()
                    GiphyReferenceResolution.Missing -> Strings.giphy.referenceMissing()
                    is GiphyReferenceResolution.Failed -> when (resolution.issue) {
                        GiphyIssue.RateLimited -> Strings.giphy.rateLimit()
                        GiphyIssue.MissingKey, GiphyIssue.Unauthorized -> Strings.giphy.invalidKey()
                        else -> Strings.giphy.previewUnavailable()
                    }
                    is GiphyReferenceResolution.Ready -> null
                }
                hint?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                if (resolution !is GiphyReferenceResolution.Pending) ShellButton(Strings.giphy.refreshPreview(), onClick = onRetry)
            }
            if (visible && latestCanPublish()) item?.let { GiphyAnimationPreview(it, bitmap, runtime, reduceMotion) }
            GiphyAttribution()
        }
    }
}
