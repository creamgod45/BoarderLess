package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.*

@Composable internal fun PreparedMediaImportDialog(controller: PreparedMediaImportController,
    picker: suspend () -> AssetTransferSource?, onReady: (WorkspaceAsset) -> Unit, onDismiss: () -> Unit) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<AssetTransferSource?>(null) }
    val latestSource by rememberUpdatedState(source)
    val pendingReleases = remember { mutableListOf<AssetTransferSource>() }
    var cleanupPending by remember { mutableStateOf(0) }
    var reservations by remember { mutableStateOf<List<PreparedMediaImportReservation>>(emptyList()) }
    var archivedView by remember { mutableStateOf(false) }
    var selectedArchived by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    val pageCount = ((reservations.size + 7) / 8).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    var selected by remember { mutableStateOf<PreparedMediaImportReservation?>(null) }
    var status by remember { mutableStateOf<PreparedMediaImportStatus?>(null) }
    var ticket by remember { mutableStateOf<PreparedMediaImportTicket?>(null) }
    var ticketRequestId by remember { mutableStateOf<String?>(null) }
    var knownIdentity by remember { mutableStateOf(false) }
    var needsLookup by remember { mutableStateOf(false) }
    var originalRetryAllowed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf("") }
    suspend fun releaseSource(handle: AssetTransferSource): Boolean {
        if (pendingReleases.none { it === handle }) pendingReleases += handle
        val released = withContext(NonCancellable) {
            withTimeoutOrNull(AssetImportCleanupTimeoutMillis) { runCatching { handle.release() }.isSuccess } ?: false
        }
        if (released) pendingReleases.removeAll { it === handle }
        cleanupPending = pendingReleases.size
        return released
    }
    suspend fun detachSource(): Boolean {
        val previous = source
        // Clear the actionable handle BEFORE a suspending/throwing native release.
        source = null
        return previous == null || releaseSource(previous)
    }
    fun run(networkWrite: Boolean = false, localVisibilityChange: Boolean = false, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = Strings.media.preparing()
        job = scope.launch {
            fun unknownWrite() {
                if (networkWrite) {
                    needsLookup = true; originalRetryAllowed = false; status = null; ticket = null
                }
            }
            try { action(); if (networkWrite) { needsLookup = false; originalRetryAllowed = false } }
            catch (cancelled: CancellationException) { unknownWrite(); message = Strings.media.v2Stopped(); throw cancelled }
            catch (_: PickedAssetOriginalChangedException) { unknownWrite(); message = Strings.media.v2SourceFailure() }
            catch (error: AssetImportException) {
                unknownWrite()
                message = mediaImportFailure(error, null).message() +
                    if (networkWrite) "\n" + Strings.media.v2UnknownOutcome() else ""
            }
            catch (error: cg.creamgod.boarderless.data.remote.AssetPreparationHttpException) {
                unknownWrite()
                needsLookup = true
                originalRetryAllowed = error.httpStatus == 404 && !knownIdentity
                val hint = when {
                    error.httpStatus == 401 || error.httpStatus == 403 -> Strings.media.v2AccessFailure()
                    error.httpStatus == 404 -> Strings.media.v2LookupUnknown()
                    error.code in setOf(cg.creamgod.boarderless.data.remote.AssetPreparationFailureCode.LeaseExpired,
                        cg.creamgod.boarderless.data.remote.AssetPreparationFailureCode.LeaseSuperseded,
                        cg.creamgod.boarderless.data.remote.AssetPreparationFailureCode.LeaseMismatch) -> Strings.media.v2LeaseFailure()
                    else -> Strings.media.v2UnknownOutcome()
                }
                ticket = null
                if (error.httpStatus == 401 || error.httpStatus == 403 || error.httpStatus == 404) status = null
                message = "HTTP ${error.httpStatus}" + (error.code?.token?.let { " · $it" } ?: "") + "\n" + hint
            }
            catch (_: Exception) {
                unknownWrite()
                message = if (localVisibilityChange) Strings.media.v2ArchiveUnconfirmed()
                    else Strings.media.transferUnavailable() + "\n" + Strings.media.v2UnknownOutcome()
            }
            finally { busy = false; job = null }
        }
    }
    DisposableEffect(controller) {
        onDispose {
            val running = job
            running?.cancel()
            runCatching { controller.close() }
            val release = latestSource
            scope.launch(NonCancellable) {
                running?.join()
                if (release != null) releaseSource(release)
                // A failed handoff may already have detached its handle from UI state.
                pendingReleases.toList().forEach { releaseSource(it) }
            }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 740.dp).fillMaxWidth(.94f).fillMaxHeight(.9f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.media.v2Title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.media.v2Hint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    BasicText(message, style = TextStyle(color = colors.contentText, fontSize = 13.sp))
                    if (cleanupPending > 0) {
                        BasicText(Strings.media.v2CleanupPending(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                        ShellButton(Strings.media.v2RetryCleanup(), enabled = !busy, onClick = { run {
                            pendingReleases.toList().forEach { releaseSource(it) }
                            message = if (cleanupPending == 0) Strings.media.v2CleanupDone() else Strings.media.v2CleanupPending()
                        } })
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShellButton(label = Strings.media.v2Discover(), enabled = !busy, onClick = { run {
                            reservations = controller.discover(archivedView)
                            page = 0
                            message = Strings.media.v2Indexed(reservations.size.toString())
                        } })
                        ShellButton(label = if (archivedView) Strings.media.v2ShowActive() else Strings.media.v2ShowArchived(),
                            enabled = !busy, onClick = { run {
                                val nextView = !archivedView
                                val loaded = controller.discover(nextView)
                                reservations = loaded; archivedView = nextView; page = 0
                                message = Strings.media.v2Indexed(loaded.size.toString())
                            } })
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ShellButton(label = Strings.media.previousAssets(), enabled = !busy && currentPage > 0,
                                onClick = { page = currentPage - 1 })
                            BasicText("${currentPage + 1}/$pageCount", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            ShellButton(label = Strings.media.nextAssets(), enabled = !busy && currentPage + 1 < pageCount,
                                onClick = { page = currentPage + 1 })
                        }
                        reservations.asReversed().drop(currentPage * 8).take(8).forEach { ref ->
                            ShellButton(label = "${ref.requestId} · ${ref.mediaType} · ${ref.byteSize}", enabled = !busy && cleanupPending == 0,
                                onClick = { run {
                                    val recovered = controller.recoverReservation(ref)
                                    val released = detachSource()
                                    currentCoroutineContext().ensureActive()
                                    check(controller.isActive())
                                    selected = ref; status = null; ticket = null
                                    selectedArchived = archivedView
                                    knownIdentity = recovered.hasKnownIdentity; ticketRequestId = recovered.latestTicketRequestId
                                    needsLookup = recovered.hasKnownIdentity || recovered.prepareAttempted ||
                                        recovered.latestTicketRequestId != null || recovered.uploadAttempted || recovered.completionAttempted
                                    originalRetryAllowed = false
                                    message = Strings.media.v2Recovered() + (if (needsLookup) "\n" + Strings.media.v2AttemptHint() else "") +
                                        (if (released) "" else "\n" + Strings.media.v2CleanupPending())
                                } })
                        }
                        if (selected != null) ShellButton(label = Strings.media.v2New(), enabled = !busy && cleanupPending == 0,
                            onClick = { run {
                                val released = detachSource()
                                currentCoroutineContext().ensureActive()
                                selected = null; status = null; ticket = null; ticketRequestId = null; knownIdentity = false
                                selectedArchived = false
                                needsLookup = false; originalRetryAllowed = false
                                message = if (released) Strings.media.v2Stopped() else Strings.media.v2CleanupPending()
                            } })
                        ShellButton(label = Strings.media.v2Pick(), enabled = !busy && cleanupPending == 0, onClick = { run {
                            val picked = picker()
                            if (picked == null) { message = Strings.media.importCancelled(); return@run }
                            try {
                                val ref = selected ?: controller.reserve(picked, randomUuid())
                                controller.validateReselected(ref, picked)
                                val released = detachSource()
                                currentCoroutineContext().ensureActive()
                                check(controller.isActive())
                                source = picked; selected = ref
                                if (!knownIdentity && status == null) selectedArchived = false
                                message = Strings.media.v2Reserved(ref.requestId) +
                                    (if (released) "" else "\n" + Strings.media.v2CleanupPending())
                            } catch (error: Throwable) {
                                if (source === picked) source = null
                                releaseSource(picked)
                                throw error
                            }
                        } })
                        selected?.let { ref ->
                            BasicText(ref.requestId, style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                            source?.let { BasicText(it.displayName, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                            status?.let {
                                val hint = when (it.phase) {
                                    "prepared" -> Strings.media.v2PreparedHint()
                                    "completionAccepted" -> Strings.media.v2ProcessingHint()
                                    "ready" -> Strings.media.v2ReadyHint()
                                    else -> Strings.media.v2TerminalHint()
                                }
                                BasicText("${it.phase} · ${it.assetId}\n$hint" +
                                    (it.rejectionReason?.token?.let { code -> "\n$code" } ?: ""),
                                    style = TextStyle(color = colors.contentText, fontSize = 13.sp))
                            }
                            if (needsLookup) BasicText(Strings.media.v2AttemptHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            BasicText(Strings.media.v2UploadFlowHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            ShellButton(label = Strings.media.v2UploadFlow(), enabled = !busy && cleanupPending == 0 &&
                                !selectedArchived && !needsLookup && !knownIdentity && status == null &&
                                ticketRequestId == null && source != null,
                                onClick = { run(networkWrite = true) {
                                    val result = controller.uploadNewOriginal(ref, checkNotNull(source),
                                        onStatus = { status = it; knownIdentity = true },
                                        onTicketRequest = { ticketRequestId = it; ticket = null },
                                        onTicket = { ticket = it },
                                        onProgress = { bytes -> message = Strings.media.uploading((bytes * 100 / ref.byteSize).toString()) })
                                    status = result
                                    val released = if (result.phase != "prepared") detachSource() else true
                                    currentCoroutineContext().ensureActive()
                                    check(controller.isActive())
                                    message = when (result.phase) {
                                        "ready" -> Strings.media.v2ReadyHint()
                                        "completionAccepted" -> Strings.media.v2ProcessingHint()
                                        "prepared" -> Strings.media.v2PreparedHint()
                                        else -> Strings.media.v2TerminalHint()
                                    } + if (released) "" else "\n" + Strings.media.v2CleanupPending()
                                } })
                            ShellButton(label = if (originalRetryAllowed) Strings.media.v2RetryOriginalPrepare() else Strings.media.v2Prepare(),
                                enabled = !busy && status == null && !knownIdentity && (!needsLookup || originalRetryAllowed),
                                onClick = { run(networkWrite = true) {
                                    status = controller.prepare(ref); knownIdentity = true; message = Strings.media.v2StepDone()
                                } })
                            ShellButton(label = Strings.media.reviewRecovery(), enabled = !busy,
                                onClick = { run {
                                    status = null
                                    status = controller.lookup(ref); knownIdentity = true
                                    needsLookup = false; originalRetryAllowed = false; message = Strings.media.v2StepDone()
                                } })
                            if (selectedArchived) ShellButton(label = Strings.media.v2Unarchive(), enabled = !busy,
                                onClick = { run(localVisibilityChange = true) {
                                    controller.unarchive(ref); selectedArchived = false
                                    reservations = controller.discover(archivedView); page = 0
                                    message = Strings.media.v2Unarchived()
                                } })
                            else ShellButton(label = Strings.media.v2Archive(), enabled = !busy && !needsLookup &&
                                status?.phase in setOf("ready", "rejected", "missing", "abandoned"),
                                onClick = { run(localVisibilityChange = true) {
                                    controller.archive(ref); selectedArchived = true; ticket = null
                                    reservations = controller.discover(archivedView); page = 0
                                    message = Strings.media.v2Archived()
                                } })
                            ShellButton(label = Strings.media.v2Ticket(), enabled = !busy && !needsLookup && status?.phase == "prepared",
                                onClick = { run(networkWrite = true) {
                                    val id = ticketRequestId ?: randomUuid().also { ticketRequestId = it }
                                    ticket = controller.ticket(ref, id); message = Strings.media.v2StepDone()
                                } })
                            ShellButton(label = Strings.media.v2ReplaceTicket(), enabled = !busy && !needsLookup && status?.phase == "prepared",
                                onClick = { run(networkWrite = true) {
                                    ticket = null
                                    val id = randomUuid().also { ticketRequestId = it }
                                    ticket = controller.ticket(ref, id); message = Strings.media.v2StepDone()
                                } })
                            ShellButton(label = Strings.media.v2Upload(), enabled = !busy && cleanupPending == 0 && !needsLookup && source != null && ticket != null && status?.phase == "prepared",
                                onClick = { run(networkWrite = true) {
                                    controller.upload(ref, checkNotNull(ticket), checkNotNull(source)) { bytes ->
                                        message = Strings.media.uploading((bytes * 100 / ref.byteSize).toString())
                                    }
                                    message = Strings.media.v2StepDone()
                                } })
                            ShellButton(label = Strings.media.v2Complete(), enabled = !busy && !needsLookup && status?.phase == "prepared",
                                onClick = { run(networkWrite = true) { status = controller.complete(ref); message = Strings.media.v2StepDone() } })
                            status?.readyAsset?.let { asset ->
                                ShellButton(label = Strings.media.v2UseReady(), enabled = !busy, onClick = { onReady(asset); onDismiss() })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (busy) ShellButton(label = Strings.common.cancel(), onClick = { job?.cancel() })
                        ShellButton(label = Strings.common.close(), enabled = !busy, onClick = onDismiss)
                    }
                }
            }
        }
    }
}
