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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.contentDescription
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
    keyAvailable: Boolean, resolution: GiphyReferenceResolution, runtime: MediaImportRuntime, reduceMotion: Boolean, selected: Boolean,
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
    val shape = RoundedCornerShape(10.dp)
    // Full-bleed media; the whole object is the drag/select target. Provider mark and playback control float on it.
    Box(Modifier.canvasObjectBounds(position, naturalWidth * viewport.zoom, naturalHeight * viewport.zoom)
        .graphicsLayer { rotationZ = transform.rotationDegrees }
        .zIndex(node.zIndex.toFloat()).clip(shape).background(colors.contentSurface)
        .border(if (selected) 2.dp else 1.dp, if (selected) colors.accent else colors.contentBorder, shape)
        .semantics {
            role = Role.Button
            contentDescription = node.altText.ifBlank { "GIPHY" }
            onClick(label = Strings.a11y.selectThought()) {
                if (interactionEnabled) { currentSelect(); true } else false
            }
        }
        .pointerInput(interactionEnabled, mutationEnabled, node.locked, node.id, node.version, viewport.zoom, transform.rotationDegrees) {
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
                        delta = objectLocalDragToWorld(motion.displacement, transform.rotationDegrees, viewport.zoom)
                        currentPreview(delta)
                    }
                }
                when {
                    !completed -> currentCancel()
                    moved -> currentCommit(delta)
                    motion.canTap -> currentSelect()
                }
            }
        }) {
        val hint = when {
            !keyAvailable -> Strings.giphy.invalidKey()
            else -> when (resolution) {
                GiphyReferenceResolution.Pending -> Strings.giphy.loadingReference()
                GiphyReferenceResolution.Missing -> Strings.giphy.referenceMissing()
                is GiphyReferenceResolution.Failed -> when (resolution.issue) {
                    GiphyIssue.RateLimited -> Strings.giphy.rateLimit()
                    GiphyIssue.MissingKey, GiphyIssue.Unauthorized -> Strings.giphy.invalidKey()
                    else -> Strings.giphy.previewUnavailable()
                }
                is GiphyReferenceResolution.Ready -> null
            }
        }
        if (keyAvailable && visible && latestCanPublish() && item != null) {
            GiphyAnimationPreview(item, bitmap, runtime, reduceMotion, Modifier.fillMaxSize(), overlayControl = true)
        } else if (hint != null) {
            BasicText(hint, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp), modifier = Modifier.align(Alignment.Center).padding(8.dp))
        }
        // Provider mark: 8.dp high, width follows the approved asset's aspect ratio.
        Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) { GiphyAttribution(width = 8.dp * 200f / 42f) }
    }
}

/** Default node box follows the GIF's own aspect ratio (about 260 dp on the long side) instead of a fixed portrait card. */
internal fun giphyInsertionSize(item: GiphyItem, density: Float): CanvasSize {
    val rendition = listOf("fixed_width", "downsized", "original").firstNotNullOfOrNull { name ->
        item.images[name]?.let { r -> r.width.toFloatOrNull()?.let { w -> r.height.toFloatOrNull()?.let { h -> w to h } } }
            ?.takeIf { (w, h) -> w > 0f && h > 0f }
    }
    val (w, h) = rendition ?: (1f to 1f)
    val longSide = 260f
    val ratio = (w / h).coerceIn(0.4f, 2.5f)
    val width = if (ratio >= 1f) longSide else longSide * ratio
    val height = width / ratio
    return CanvasSize(width * density, height * density)
}
