package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.i18n.Strings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Host must provide real capture/exclusion/identity checks, and own the activated lease.
 * Opening performs no writes, HTTP, ledger initialization or repository switch. */
@Composable internal fun SubmissionMigrationDialog(target: PendingSubmissionScope,
    review: LegacySubmissionMigrationReview, publisher: AwaitedLegacyMigrationPublisher,
    activation: AwaitedSubmissionActivationManager, recapture: suspend () -> LegacySubmissionMigrationPlan,
    canContinue: () -> Boolean, onActivated: (ActivatedAwaitedSubmission) -> Unit, onDismiss: () -> Unit,
    reopening: Boolean = false) {
    val colors = BoarderLessTheme.colors
    val coroutineScope = rememberCoroutineScope()
    val currentGate by rememberUpdatedState(canContinue)
    val currentCapture by rememberUpdatedState(recapture)
    val currentActivate by rememberUpdatedState(onActivated)
    var open by remember(target, review) { mutableStateOf(true) }
    var busy by remember(target, review) { mutableStateOf(false) }
    var armed by remember(target, review) { mutableStateOf(false) }
    var published by remember(target, review, reopening) { mutableStateOf(reopening) }
    var status by remember(target, review) { mutableStateOf<String?>(null) }
    var job by remember(target, review) { mutableStateOf<Job?>(null) }
    var scopeIndex by remember(target, review) { mutableStateOf(0) }
    var packetPage by remember(target, review, scopeIndex) { mutableStateOf(0) }
    var showPacket by remember(target, review) { mutableStateOf(false) }
    val scopePacket = remember(review, scopeIndex, showPacket) {
        if (showPacket && review.scopes.isNotEmpty()) review.scopePacket(scopeIndex) else null
    }
    fun valid() = open && currentGate()
    fun close() { open = false; job?.cancel(); onDismiss() }
    DisposableEffect(target, review, publisher, activation) {
        onDispose { open = false; job?.cancel() }
    }
    fun perform() {
        if (!valid() || busy) return
        if (!armed) { armed = true; return }
        armed = false; busy = true
        status = Strings.submissionMigration.working()
        job = coroutineScope.launch {
            try {
                ensureActive(); check(valid())
                if (!published) {
                    publisher.publish(target, review) { ensureActive(); check(valid()); currentCapture() }
                    ensureActive(); check(valid())
                    published = true
                    status = Strings.submissionMigration.published()
                } else {
                    val capture: suspend () -> LegacySubmissionMigrationPlan = { ensureActive(); check(valid()); currentCapture() }
                    val lease = if (reopening) activation.reopen(target, review, capture)
                        else activation.confirm(target, review, capture)
                    var transferred = false
                    try {
                        ensureActive(); check(valid())
                        currentActivate(lease) // Host installs the runtime and retains this lease.
                        transferred = true
                        open = false
                        onDismiss()
                    } finally { if (!transferred) lease.close() }
                }
            } catch (cancelled: CancellationException) {
                if (valid()) status = Strings.submissionMigration.cancelled()
                throw cancelled
            } catch (_: Exception) {
                if (valid()) status = Strings.submissionMigration.failed()
            } finally { busy = false; job = null }
        }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.85f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(Strings.submissionMigration.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.submissionMigration.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    status?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 13.sp)) }
                    if (busy) ShellButton(Strings.submissionMigration.stop(), onClick = { job?.cancel() })
                    ShellButton(label = Strings.submissionMigration.close(), onClick = { close() })
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Source SHA-256: ${review.sourceDigest}\nPlan SHA-256: ${review.planDigest}", style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShellButton(Strings.submissionMigration.previousScope(), enabled = !busy && scopeIndex > 0,
                            onClick = { scopeIndex-- })
                        ShellButton(Strings.submissionMigration.nextScope(), enabled = !busy && scopeIndex + 1 < review.scopes.size,
                            onClick = { scopeIndex++ })
                    }
                    review.scopes.getOrNull(scopeIndex)?.let { item ->
                        BasicText(Strings.submissionMigration.scope(scopeIndex + 1, item.scopeHash, item.draftOperationCount, item.hasPendingWire, item.stoppedWireCount, item.fenceCount, item.deletionEvidenceCount, item.minimumClientSequence, item.retryQuarantined), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    }
                    ShellButton(Strings.submissionMigration.inspectPacket(), enabled = !busy,
                        onClick = { showPacket = !showPacket })
                    scopePacket?.let { packet ->
                        BasicText(Strings.submissionMigration.packetWarning(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        val pages = ((packet.length + 4095) / 4096).coerceAtLeast(1)
                        val page = packetPage.coerceIn(0, pages - 1)
                        var start = page * 4096
                        var end = minOf(packet.length, start + 4096)
                        // Adjacent pages meet before a surrogate pair, not inside it.
                        if (start > 0 && packet[start].isLowSurrogate() && packet[start - 1].isHighSurrogate()) start--
                        if (end < packet.length && packet[end - 1].isHighSurrogate() && packet[end].isLowSurrogate()) end--
                        BasicText("${scopeIndex + 1}/${review.scopes.size} · ${page + 1}/$pages",
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ShellButton(Strings.submissionMigration.previousPage(), enabled = !busy && page > 0,
                                onClick = { packetPage = page - 1 })
                            ShellButton(Strings.submissionMigration.nextPage(), enabled = !busy && page + 1 < pages,
                                onClick = { packetPage = page + 1 })
                        }
                        SelectionContainer { BasicText(packet.substring(start, end), style = TextStyle(color = colors.contentText, fontSize = 11.sp)) }
                    }
                    ShellButton(label = when {
                        armed -> Strings.submissionMigration.confirm()
                        reopening -> Strings.submissionMigration.reopen()
                        published -> Strings.submissionMigration.activate()
                        else -> Strings.submissionMigration.publish()
                    }, enabled = valid() && !busy, onClick = { perform() })
                    }
                }
            }
        }
    }
}
