package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** Prefer a small GIF rendition for our existing GIF decoder; never download the original implicitly. */
internal fun giphyAnimationUrl(item: GiphyItem): String? =
    listOf("fixed_width", "downsized").firstNotNullOfOrNull { name ->
        item.images[name]?.takeIf { rendition ->
            val size = rendition.size.toLongOrNull()
            isGiphyMediaUrl(rendition.url) && (size == null || size in 1..GiphyMediaClient.MaxStillBytes.toLong())
        }?.url
    }

@Composable internal fun GiphyAnimationPreview(
    item: GiphyItem,
    fallback: ImageBitmap?,
    runtime: MediaImportRuntime,
    reduceMotion: Boolean,
) {
    val colors = BoarderLessTheme.colors
    val url = giphyAnimationUrl(item)
    val activityFlow = remember(runtime) { runtime.playbackActivity ?: MutableStateFlow(MediaPlaybackActivity()) }
    val activity by activityFlow.collectAsState()
    var activated by remember(item.id, url, runtime, activity.epoch, reduceMotion) { mutableStateOf(false) }
    var playing by remember(item.id, url, runtime, activity.epoch, reduceMotion) { mutableStateOf(false) }
    var attempt by remember(item.id, url) { mutableStateOf(0) }
    var frame by remember(item.id, url) { mutableStateOf<ImageBitmap?>(null) }
    var finished by remember(item.id, url) { mutableStateOf(false) }
    var failed by remember(item.id, url) { mutableStateOf(false) }
    var visible by remember(item.id, url) { mutableStateOf(false) }
    val currentPlaying by rememberUpdatedState(playing)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, item.id, url, runtime, activity.epoch, reduceMotion) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { activated = false; playing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(item.id, url, runtime, activated, attempt, visible, reduceMotion, activity) {
        frame = null
        if (!visible || !activity.available || reduceMotion) { activated = false; playing = false; return@LaunchedEffect }
        val loader = runtime.loadGiphyAnimation
        if (!activated || url == null || loader == null) return@LaunchedEffect
        var opened: GifAnimation? = null
        finished = false; failed = false
        try {
            val animation = loader(url).also { opened = it }
            currentCoroutineContext().ensureActive()
            playGifFrames(animation.frameCount, animation.repetitionCount, animation::durationMs,
                awaitPlaying = { snapshotFlow { currentPlaying }.first { it } },
                showFrame = { index ->
                    val decoded = animation.frame(index)
                    currentCoroutineContext().ensureActive()
                    if (index != 0) snapshotFlow { currentPlaying }.first { it }
                    frame = decoded
                })
            playing = false; finished = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { currentCoroutineContext().ensureActive(); frame = null; failed = true; playing = false; finished = true }
        finally {
            withContext(NonCancellable) {
                if (runCatching { opened?.release() }.isFailure) { frame = null; failed = true; playing = false; finished = true }
            }
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp).onGloballyPositioned {
        val bounds = it.boundsInWindow()
        visible = bounds.width > 0f && bounds.height > 0f
    }) {
        (frame ?: fallback)?.let {
            Image(it, contentDescription = item.alt_text.ifBlank { item.title }, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp))
        }
    }
    if (url != null && runtime.loadGiphyAnimation != null) {
        ShellButton(label = if (playing) Strings.media.pauseAnimation() else Strings.media.playAnimation(),
            icon = if (playing) ShellIcon.Pause else ShellIcon.Play, showLabel = false, compact = true,
            enabled = !reduceMotion && activity.available && visible, onClick = {
                if (finished || failed) { attempt++; playing = true; activated = true }
                else { activated = true; playing = !playing }
            })
    }
    if (failed) BasicText(Strings.giphy.previewUnavailable(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
}
