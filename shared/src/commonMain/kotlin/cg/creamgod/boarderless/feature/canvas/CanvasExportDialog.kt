package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.export.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CanvasExportDialog(
    initial: CanvasExportCapture, selection: Set<CanvasObjectId>, epoch: String,
    runtime: CanvasExportRuntime, mediaRuntime: MediaImportRuntime, backgroundToken: String,
    readLive: () -> CanvasExportLive, onDismiss: () -> Unit,
) {
    val live by rememberUpdatedState(readLive)
    val scope = rememberCoroutineScope()
    var selectedOnly by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var transparent by remember { mutableStateOf(false) }
    var format by remember { mutableStateOf(CanvasExportFormat.Png) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var savedDestination by remember { mutableStateOf<CanvasExportDestination?>(null) }
    val capture = remember(initial, selectedOnly) { runCatching {
        if (selectedOnly) CanvasExportCapture(initial.owner, initial.baseline, epoch, selection) else initial
    }.getOrNull() }
    var frames by remember(capture, scale, mediaRuntime) { mutableStateOf<Map<CanvasObjectId, ImageBitmap>?>(null) }
    var frameFailed by remember(capture, scale, mediaRuntime) { mutableStateOf(false) }
    LaunchedEffect(capture, scale, mediaRuntime) {
        frames = null; frameFailed = false; message = null;
        if (capture == null) return@LaunchedEffect
        try {
            check(capture.isCurrent(live()))
            val loaded = mutableMapOf<CanvasObjectId, ImageBitmap>()
            for (node in capture.scene.objects.values.filterIsInstance<MediaNode>()) {
                check(capture.isCurrent(live()))
                val id = if (node.mediaKind == MediaKind.Video) checkNotNull(node.thumbnailAssetId) else node.assetId
                loaded[node.id] = awaitActiveMediaRead {
                    val exportLoader = mediaRuntime.loadExportImage
                    if (node.mediaKind == MediaKind.Image && exportLoader != null) {
                        val width = kotlin.math.ceil(node.transform.size.width * scale).toInt()
                        val height = kotlin.math.ceil(node.transform.size.height * scale).toInt()
                        exportLoader(capture.owner, id, width, height)
                    } else checkNotNull(mediaRuntime.loadPreview)(capture.owner, id)
                }
            }
            check(capture.isCurrent(live())); frames = loaded.toMap()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { frameFailed = true }
    }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val colors = BoarderLessTheme.colors
    Dialog(onDismissRequest = { job?.cancel(); onDismiss() }) {
        GlassSurface(Modifier.padding(12.dp).widthIn(max = 680.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(Strings.canvasExport.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShellButton(Strings.canvasExport.whole(), enabled = !busy, accent = !selectedOnly, onClick = { selectedOnly = false; message = null })
                    ShellButton(Strings.canvasExport.selection(), enabled = !busy && selection.isNotEmpty(), accent = selectedOnly, onClick = { selectedOnly = true; message = null })
                    CanvasExportFormat.entries.forEach { value -> ShellButton(value.extension.uppercase(), enabled = !busy, accent = format == value, onClick = { format = value; message = null }) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(.25f, .5f, 1f, 2f).forEach { value -> ShellButton("${value}×", enabled = !busy, accent = scale == value, onClick = { scale = value; message = null }) }
                    ShellButton(if (transparent) Strings.canvasExport.transparent() else Strings.canvasExport.background(), enabled = !busy, accent = transparent, onClick = { transparent = !transparent; message = null })
                }
                BasicText(Strings.canvasExport.mediaHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                var state: CanvasExportPreviewState? = null
                if (capture != null) {
                    val measured = rememberCanvasExportMeasurement(capture.scene, scale, runtime).getOrNull()
                    if (measured != null && frames != null) {
                        state = CanvasExportPreview(capture.scene, measured, checkNotNull(frames),
                            if (transparent) Color.Transparent else canvasBackgroundColor(backgroundToken, colors),
                            Modifier.fillMaxWidth().height(260.dp))
                        BasicText("${measured.layout.pixelWidth} × ${measured.layout.pixelHeight}", style = TextStyle(color = colors.contentMuted))
                    } else BasicText(if (measured == null) Strings.canvasExport.invalid() else if (frameFailed) Strings.canvasExport.mediaFailed() else Strings.canvasExport.loading(), style = TextStyle(color = colors.contentMuted))
                } else BasicText(Strings.canvasExport.invalid(), style = TextStyle(color = colors.contentMuted))
                val latestState by rememberUpdatedState(state)
                val current = capture?.isCurrent(live()) == true
                if (!current) BasicText(Strings.canvasExport.stale(), style = TextStyle(color = colors.danger))
                message?.let { BasicText(it, style = TextStyle(color = colors.contentText)) }
                savedDestination?.savedLocation?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                if (state?.result?.isFailure == true) BasicText(Strings.canvasExport.failed(), style = TextStyle(color = colors.danger))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShellButton(if (busy) Strings.canvasExport.saving() else Strings.canvasExport.save(), enabled = !busy && current && state?.result?.isSuccess == true && runtime.chooseDestination != null,
                        onClick = {
                            val openedState = state ?: return@ShellButton
                            val openedCapture = capture ?: return@ShellButton
                            val raster = openedState.result?.getOrNull() ?: return@ShellButton
                            val chosenFormat = format
                            busy = true; message = null; savedDestination = null
                            job = scope.launch {
                                var publishing = false
                                try {
                                    val guard = { latestState === openedState && openedCapture.isCurrent(live()) }
                                    check(guard())
                                    val destination = runtime.chooseDestination!!.invoke("BoarderLess.${chosenFormat.extension}", chosenFormat, guard) ?: return@launch
                                    publishCanvasExport(raster, chosenFormat, destination, onPublishing = { publishing = true }, canWrite = guard)
                                    savedDestination = destination
                                    message = if (runtime.usesBrowserDownload) Strings.canvasExport.downloadRequested() else Strings.canvasExport.saved()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Throwable) { message = if (publishing) Strings.canvasExport.uncertain() else if (!openedCapture.isCurrent(live())) Strings.canvasExport.stale() else Strings.canvasExport.failed() }
                                finally { busy = false }
                            }
                        })
                    savedDestination?.revealSavedFile?.let { reveal ->
                        ShellButton(Strings.canvasExport.showFile(), enabled = !busy, onClick = {
                            try { reveal() } catch (_: Throwable) { message = Strings.canvasExport.revealFailed() }
                        })
                    }
                    ShellButton(Strings.common.close(), onClick = { job?.cancel(); onDismiss() })
                }
            }
        }
    }
}
