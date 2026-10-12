package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.CanvasWorkspaceRepository
import cg.creamgod.boarderless.data.remote.BackendWorkspaceRepository
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Retains the original retired repository. No repository construction, HTTP, data deletion,
 * migration or mutation retry. Removing an entry requires its actual drain acknowledgment. */
@Composable internal fun WriterReleaseRecoveryDialog(original: CanvasWorkspaceRepository,
    onDrained: () -> Unit, onDismiss: () -> Unit) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val backend = original as? BackendWorkspaceRepository
    var open by remember(original) { mutableStateOf(true) }
    var busy by remember(original) { mutableStateOf(false) }
    var failed by remember(original) { mutableStateOf(false) }
    var revision by remember(original) { mutableStateOf(0) }
    var job by remember(original) { mutableStateOf<Job?>(null) }
    val retryAllowed = remember(original, revision) { backend?.ownedWriterReleaseFailed == true }
    fun close() { open = false; job?.cancel(); onDismiss() }
    fun perform(retry: Boolean) {
        if (!open || busy || backend?.supportsOwnedWriterDrain != true) return
        busy = true; failed = false
        job = scope.launch {
            try {
                if (retry) backend.retryOwnedWriterRelease()
                backend.awaitOwnedWriterDrain()
                if (open) onDrained()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (open) failed = true }
            finally { busy = false; job = null; revision++ }
        }
    }
    DisposableEffect(original) { onDispose { open = false; job?.cancel() } }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 620.dp).fillMaxWidth(.94f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(Strings.workspaceStorage.writerRecoveryTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.workspaceStorage.writerRecoveryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    if (backend?.supportsOwnedWriterDrain != true) BasicText(Strings.workspaceStorage.writerDrainUnavailable(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    if (busy) BasicText(Strings.workspaceStorage.writerDraining(), style = TextStyle(color = colors.contentMuted))
                    if (failed) BasicText(Strings.workspaceStorage.writerStillHeld(), style = TextStyle(color = colors.danger))
                    ShellButton(Strings.workspaceStorage.writerReadDrain(), enabled = !busy && backend?.supportsOwnedWriterDrain == true,
                        onClick = { perform(false) })
                    ShellButton(Strings.workspaceStorage.writerRetryRelease(), enabled = !busy && retryAllowed,
                        onClick = { perform(true) })
                    ShellButton(Strings.common.close(), onClick = ::close)
                }
            }
        }
    }
}
