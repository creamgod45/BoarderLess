package cg.creamgod.boarderless.feature.canvas

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
        status = tr("submissionMigration.working", "Checking and durably saving…")
        job = coroutineScope.launch {
            try {
                ensureActive(); check(valid())
                if (!published) {
                    publisher.publish(target, review) { ensureActive(); check(valid()); currentCapture() }
                    ensureActive(); check(valid())
                    published = true
                    status = tr("submissionMigration.published", "Published locally. Activation still requires a separate confirmation.")
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
                if (valid()) status = tr("submissionMigration.cancelled", "Stopped. Publication or activation may already have been saved. Original records remain; verify the original receipt before another attempt.")
                throw cancelled
            } catch (_: Exception) {
                if (valid()) status = tr("submissionMigration.failed", "Not confirmed. Original records and request IDs are retained; review source, identity and writer exclusion before trying again.")
            } finally { busy = false; job = null }
        }
    }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.85f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(tr("submissionMigration.title", "Review durable draft migration"), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(tr("submissionMigration.warning", "Local storage migration only. No pending transaction is resent, no legacy record is removed, and publication alone does not activate the new writer."), style = TextStyle(color = colors.contentMuted, fontSize = 13.sp))
                    status?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 13.sp)) }
                    if (busy) ShellButton(tr("submissionMigration.stop", "Stop (retain original records)"), onClick = { job?.cancel() })
                    ShellButton(label = tr("submissionMigration.close", "Close (retain records)"), onClick = { close() })
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("Source SHA-256: ${review.sourceDigest}\nPlan SHA-256: ${review.planDigest}", style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShellButton(tr("submissionMigration.previousScope", "Previous scope"), enabled = !busy && scopeIndex > 0,
                            onClick = { scopeIndex-- })
                        ShellButton(tr("submissionMigration.nextScope", "Next scope"), enabled = !busy && scopeIndex + 1 < review.scopes.size,
                            onClick = { scopeIndex++ })
                    }
                    review.scopes.getOrNull(scopeIndex)?.let { item ->
                        BasicText(tr("submissionMigration.scope", "Scope {index}: {hash}\nDraft operations: {operations}; pending: {pending}; stopped: {stopped}\nFences: {fences}; deletion evidence: {deletions}; sequence floor: {floor}; quarantined: {quarantined}",
                            "index" to scopeIndex + 1, "hash" to item.scopeHash, "operations" to item.draftOperationCount,
                            "pending" to item.hasPendingWire, "stopped" to item.stoppedWireCount, "fences" to item.fenceCount,
                            "deletions" to item.deletionEvidenceCount, "floor" to item.minimumClientSequence,
                            "quarantined" to item.retryQuarantined), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    }
                    ShellButton(tr("submissionMigration.inspectPacket", "Show / hide full local review packet"), enabled = !busy,
                        onClick = { showPacket = !showPacket })
                    scopePacket?.let { packet ->
                        BasicText(tr("submissionMigration.packetWarning", "Local review only: this packet includes canvas text, original transaction IDs and recovery evidence. It is not sent to the backend or diagnostic log."),
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
                            ShellButton(tr("submissionMigration.previousPage", "Previous packet page"), enabled = !busy && page > 0,
                                onClick = { packetPage = page - 1 })
                            ShellButton(tr("submissionMigration.nextPage", "Next packet page"), enabled = !busy && page + 1 < pages,
                                onClick = { packetPage = page + 1 })
                        }
                        SelectionContainer { BasicText(packet.substring(start, end), style = TextStyle(color = colors.contentText, fontSize = 11.sp)) }
                    }
                    ShellButton(label = when {
                        armed -> tr("submissionMigration.confirm", "Confirm this step")
                        reopening -> tr("submissionMigration.reopen", "Review reopening (keep current data)")
                        published -> tr("submissionMigration.activate", "Review activation")
                        else -> tr("submissionMigration.publish", "Review local publication")
                    }, enabled = valid() && !busy, onClick = { perform() })
                    }
                }
            }
        }
    }
}
