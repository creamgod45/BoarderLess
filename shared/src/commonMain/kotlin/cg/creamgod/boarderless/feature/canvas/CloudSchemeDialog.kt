package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** User-scoped cloud library, not canvas publication. Every write is separately reviewed,
 * durably staged, then approved for sending. Opening performs no HTTP. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CloudSchemeDialog(repository: CanvasWorkspaceRepository, library: AwaitedQuickSchemeRepository,
    owner: WorkspaceSession, localScheme: QuickScheme?, canContinue: () -> Boolean, onDismiss: () -> Unit) {
    val runtime = library.cloudSyncRuntime ?: return
    val server = repository.cloudQuickSchemeServer ?: return
    val gateway = remember(server, owner.userId) { server.gateway(owner.userId) }
    val outbox = remember(runtime, gateway, owner.clientId) { runtime.outbox(CloudSchemeOutboxScope(gateway.serverScope, owner.userId, owner.clientId)) }
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val live by rememberUpdatedState(canContinue)
    var open by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf<CloudSchemePage?>(null) }
    var currentCursor by remember { mutableStateOf<String?>(null) }
    var previousCursors by remember { mutableStateOf<List<String?>>(emptyList()) }
    var selected by remember { mutableStateOf<CloudSchemeResource?>(null) }
    var name by remember { mutableStateOf("") }
    var tasks by remember { mutableStateOf<List<CloudSchemeTask>>(emptyList()) }
    var tasksNeedRefresh by remember { mutableStateOf(true) }
    var review by remember { mutableStateOf<CloudSchemeMutation?>(null) }
    var conflict by remember { mutableStateOf<CloudSchemeConflictReview?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var importRequestId by remember { mutableStateOf(randomUuid()) }
    var taskPage by remember { mutableStateOf(0) }
    var mutationReviewPage by remember { mutableStateOf(0) }
    LaunchedEffect(review) { mutationReviewPage = 0 }
    fun valid() = open && live()
    fun invalidateRemoteView() {
        review = null
        conflict = null
        // Both a commit and a version rejection invalidate the selected old ETag.
        // A receipt resource may be historical after an idempotent recovery; do not
        // advertise it as the current collection or silently use it for another write.
        page = null
        selected = null
        currentCursor = null
        previousCursors = emptyList()
    }
    fun recordMutationResult(result: CloudSchemeTask) {
        check(valid())
        invalidateRemoteView()
        status = when (result.phase) {
            CloudSchemeTaskPhase.Committed -> Strings.cloudSchemes.committedReload()
            CloudSchemeTaskPhase.Rejected -> Strings.cloudSchemes.rejectedReload(result.httpStatus?.toString() ?: "—")
            else -> Strings.cloudSchemes.recoveryHint()
        }
    }
    fun run(sending: Boolean = false, action: suspend () -> Unit) {
        if (busy || !valid()) return
        busy = true
        if (sending) {
            // Invalidate before the durable Sending marker or HTTP can be attempted.
            tasksNeedRefresh = true
            invalidateRemoteView()
        }
        status = Strings.cloudSchemes.working()
        job = scope.launch {
            var actionFinished = false
            try {
                action(); actionFinished = true
                if (valid()) { val loaded = outbox.tasks(); check(valid()); tasks = loaded; tasksNeedRefresh = false }
            }
            catch (cancelled: CancellationException) {
                if (valid()) { tasksNeedRefresh = true; status = Strings.cloudSchemes.cancelled() }
                throw cancelled
            }
            catch (error: Exception) {
                if (valid()) {
                    tasksNeedRefresh = true
                    status = if (actionFinished) Strings.cloudSchemes.tasksReloadFailed()
                        else if (error is CloudSchemeHttpException) Strings.cloudSchemes.httpFailed(error.httpStatus.toString()) else Strings.cloudSchemes.failed()
                    runCatching { outbox.tasks() }.getOrNull()?.let {
                        if (valid()) { tasks = it; tasksNeedRefresh = false }
                    }
                }
            } finally { job = null; busy = false }
        }
    }
    fun close() { open = false; job?.cancel(); onDismiss() }
    DisposableEffect(gateway) { onDispose { open = false; job?.cancel(); gateway.close() } }
    val enabled = valid() && !busy && !tasksNeedRefresh
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 760.dp).fillMaxWidth(.94f).fillMaxHeight(.9f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(Strings.cloudSchemes.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.cloudSchemes.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    status?.let { BasicText(it, style = TextStyle(color = colors.contentText, fontSize = 12.sp)) }
                    if (tasksNeedRefresh) BasicText(Strings.cloudSchemes.tasksMustReload(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    ShellButton(Strings.cloudSchemes.tasks(), enabled = valid() && !busy, onClick = { run {
                        // Completion reads the durable task list, without HTTP.
                        status = Strings.cloudSchemes.loaded()
                    } })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (busy) ShellButton(Strings.common.cancel(), onClick = { job?.cancel() })
                        ShellButton(Strings.common.close(), onClick = { close() })
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShellButton(Strings.cloudSchemes.load(), enabled = enabled, onClick = { run {
                        gateway.capabilities(); val loaded = gateway.list(); check(valid())
                        page = loaded; currentCursor = null; previousCursors = emptyList()
                        selected = null; review = null; conflict = null; status = Strings.cloudSchemes.loaded()
                    } })
                    if (previousCursors.isNotEmpty()) ShellButton(Strings.cloudSchemes.previous(), enabled = enabled, onClick = { run {
                        val cursor = previousCursors.last()
                        val loaded = gateway.list(cursor); check(valid())
                        page = loaded; currentCursor = cursor; previousCursors = previousCursors.dropLast(1)
                        selected = null; review = null; conflict = null; status = Strings.cloudSchemes.loaded()
                    } })
                    page?.items?.forEach { resource ->
                        ShellButton(resource.name, compact = true, enabled = enabled, onClick = { run {
                            val fresh = gateway.get(resource.id); check(valid())
                            selected = fresh; name = fresh.name; review = null; conflict = null; importRequestId = randomUuid()
                            status = Strings.cloudSchemes.loaded()
                        } })
                    }
                    page?.nextCursor?.let { cursor -> ShellButton(Strings.cloudSchemes.next(), enabled = enabled, onClick = { run {
                        val next = gateway.list(cursor); check(valid())
                        previousCursors = previousCursors + listOf(currentCursor); currentCursor = cursor
                        page = next; selected = null; review = null; conflict = null
                        status = Strings.cloudSchemes.loaded()
                    } }) }
                    selected?.let { resource ->
                        BasicText("${resource.id} · ${resource.revision}", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        CloudSchemeVisualReview(resource, enabled)
                        CloudSchemePayloadReview(resource.payload.toString(), enabled)
                        BasicTextField(name, { name = it.take(160); review = null }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = colors.contentText, fontSize = 14.sp))
                        ShellButton(Strings.cloudSchemes.reviewRename(), enabled = enabled && name.trim().isNotEmpty(), onClick = { run {
                            review = CloudSchemeMutation(resource.id, randomUuid(), "update", resource.etag,
                                CloudSchemeBody(name = name.trim(), schemaVersion = resource.schemaVersion, payload = resource.payload, assetBindings = resource.assetBindings))
                            status = Strings.cloudSchemes.reviewHint()
                        } })
                        ShellButton(Strings.cloudSchemes.reviewDelete(), enabled = enabled, onClick = {
                            review = CloudSchemeMutation(resource.id, randomUuid(), "delete", resource.etag); status = Strings.cloudSchemes.reviewHint()
                        })
                        localScheme?.let { local ->
                            ShellButton(Strings.cloudSchemes.reviewReplace(local.name), enabled = enabled && name.trim().isNotEmpty(),
                                onClick = { run {
                                    val current = library.list().single { it.id == local.id }; require(current == local)
                                    val body = cloudSchemeBodyFromLocal(current, name.trim())
                                    check(valid())
                                    review = CloudSchemeMutation(resource.id, randomUuid(), "update", resource.etag, body)
                                    status = Strings.cloudSchemes.reviewHint()
                                } })
                        }
                        val localCopy = remember(resource) { runCatching { cloudSchemeLocalCopy(resource) }.getOrNull() }
                        if (localCopy == null) BasicText(Strings.cloudSchemes.multipleSourcesUnsupported(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        ShellButton(Strings.cloudSchemes.importLocal(), enabled = enabled && library.supportsIdentifiedSave && localCopy != null,
                            onClick = { run {
                                check(valid())
                                val copy = cloudSchemeLocalCopy(resource)
                                library.stageIdentifiedSave(QuickSchemeSaveIntent(importRequestId, copy))
                                check(valid())
                                library.saveIdentified(importRequestId, copy.payload, copy.name, copy.schemaVersion, copy.sourceWorkspaceId)
                                status = Strings.cloudSchemes.imported()
                            } })
                    }
                    localScheme?.let { local -> ShellButton(Strings.cloudSchemes.reviewUpload(local.name), enabled = enabled, onClick = { run {
                        val current = library.list().single { it.id == local.id }; require(current == local)
                        val body = cloudSchemeBodyFromLocal(current)
                        check(valid())
                        review = CloudSchemeMutation(randomUuid(), randomUuid(), "create", body = body)
                        status = Strings.cloudSchemes.reviewHint()
                    } }) }
                    review?.let { mutation ->
                        BasicText("${mutation.kind} · ${mutation.resourceId}\n${mutation.body?.name.orEmpty()}", style = TextStyle(color = colors.accent, fontSize = 13.sp))
                        val pages = remember(mutation) { cloudSchemeReviewPages(Json { encodeDefaults = true }.encodeToString(mutation)) }
                        val visible = mutationReviewPage.coerceIn(0, pages.lastIndex)
                        BasicText(Strings.cloudSchemes.fullReview(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        BasicText(pages[visible], style = TextStyle(color = colors.contentText, fontSize = 11.sp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(Strings.cloudSchemes.previous(), enabled = enabled && visible > 0,
                                onClick = { mutationReviewPage = visible - 1 })
                            BasicText("${visible + 1}/${pages.size}", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            ShellButton(Strings.cloudSchemes.next(), enabled = enabled && visible < pages.lastIndex,
                                onClick = { mutationReviewPage = visible + 1 })
                        }
                        ShellButton(Strings.cloudSchemes.stage(), enabled = enabled, onClick = { run {
                            outbox.stage(mutation); check(valid()); review = null; status = Strings.cloudSchemes.staged()
                        } })
                        ShellButton(Strings.common.cancel(), enabled = enabled, onClick = { review = null })
                    }
                    val taskPages = (tasks.size + 15) / 16
                    val visibleTaskPage = taskPage.coerceIn(0, (taskPages - 1).coerceAtLeast(0))
                    if (taskPages > 1) {
                        ShellButton(Strings.localSync.previousMaterializationPage(), enabled = enabled && visibleTaskPage > 0,
                            onClick = { taskPage = visibleTaskPage - 1 })
                        ShellButton(Strings.localSync.nextMaterializationPage(), enabled = enabled && visibleTaskPage + 1 < taskPages,
                            onClick = { taskPage = visibleTaskPage + 1 })
                    }
                    tasks.drop(visibleTaskPage * 16).take(16).forEach { task ->
                        BasicText("${task.mutation.kind} · ${task.mutation.resourceId} · ${task.phase}", style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        if (task.phase == CloudSchemeTaskPhase.Prepared) ShellButton(Strings.cloudSchemes.send(), enabled = enabled, onClick = { run(sending = true) {
                            recordMutationResult(sendCloudSchemeTask(outbox, gateway, task, ::valid))
                        } })
                        if (task.phase in setOf(CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown)) {
                            BasicText(Strings.cloudSchemes.recoveryHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            ShellButton(Strings.cloudSchemes.recover(), enabled = enabled && task.retryWindow != null, onClick = { run(sending = true) {
                                recordMutationResult(sendCloudSchemeTask(outbox, gateway, task, ::valid))
                            } })
                        }
                        if (task.phase in setOf(CloudSchemeTaskPhase.Sending, CloudSchemeTaskPhase.Unknown, CloudSchemeTaskPhase.Rejected))
                            ShellButton(Strings.cloudSchemes.readConflict(), enabled = enabled, onClick = { run {
                                conflict = null; review = null
                                val observed = reviewCloudSchemeConflict(outbox, gateway, task, ::valid)
                                check(valid()); conflict = observed; status = Strings.cloudSchemes.readbackHint()
                            } })
                    }
                    conflict?.let { observed ->
                        BasicText(Strings.cloudSchemes.readbackHint(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                        BasicText(Strings.cloudSchemes.originalTask(observed.task.mutation.body?.name ?: "—"),
                            style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                        CloudSchemePayloadReview(observed.task.mutation.body?.payload?.toString() ?: "—", enabled)
                        val current = observed.current
                        BasicText(if (current == null) Strings.cloudSchemes.remoteUnavailable() else Strings.cloudSchemes.currentRemote(current.name, current.revision.toString()),
                            style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                        current?.let { CloudSchemePayloadReview(it.payload.toString(), enabled) }
                        if (observed.task.phase == CloudSchemeTaskPhase.Rejected) {
                            if (current != null && observed.task.mutation.kind == "update")
                                ShellButton(Strings.cloudSchemes.reapplyName(), enabled = enabled, onClick = { run {
                                    require(outbox.tasks().single { it.mutation.idempotencyKey == observed.task.mutation.idempotencyKey } == observed.task)
                                    review = observed.renameOnCurrent(randomUuid()); status = Strings.cloudSchemes.reviewHint()
                                } })
                            if (current != null && observed.task.mutation.kind == "update")
                                ShellButton(Strings.cloudSchemes.replaceCurrent(), enabled = enabled, onClick = { run {
                                    require(outbox.tasks().single { it.mutation.idempotencyKey == observed.task.mutation.idempotencyKey } == observed.task)
                                    review = observed.replaceOnCurrent(randomUuid()); status = Strings.cloudSchemes.reviewHint()
                                } })
                            if (current != null && observed.task.mutation.kind == "delete")
                                ShellButton(Strings.cloudSchemes.reviewCurrentDelete(), enabled = enabled, onClick = { run {
                                    require(outbox.tasks().single { it.mutation.idempotencyKey == observed.task.mutation.idempotencyKey } == observed.task)
                                    review = observed.deleteCurrent(randomUuid()); status = Strings.cloudSchemes.reviewHint()
                                } })
                            if (observed.task.mutation.kind != "delete")
                                ShellButton(Strings.cloudSchemes.saveAsNew(), enabled = enabled, onClick = { run {
                                    require(outbox.tasks().single { it.mutation.idempotencyKey == observed.task.mutation.idempotencyKey } == observed.task)
                                    review = observed.saveAsNew(randomUuid(), randomUuid()); status = Strings.cloudSchemes.reviewHint()
                                } })
                        }
                        ShellButton(Strings.common.close(), enabled = enabled, onClick = { conflict = null; review = null })
                    }
                    }
                }
            }
        }
    }
}
