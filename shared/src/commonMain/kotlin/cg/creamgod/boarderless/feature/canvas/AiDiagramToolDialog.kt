package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.ai.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Chat-style tool session: typed progress, per-round consent cards and batch proposal review.
 * Credentials stay in memory; only the OS key vault may persist the token, and only on request. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiDiagramToolDialog(
    workspace: Workspace,
    selectedIds: Set<CanvasObjectId>,
    supportsRelationGeometry: Boolean,
    validateScope: suspend () -> Boolean,
    isSessionCurrent: () -> Boolean,
    onApply: suspend (AiProposal) -> AiDiagramApplyOutcome,
    onDismiss: () -> Unit,
    initialChangesExpanded: Boolean = true,
    onChangesExpandedChange: (Boolean) -> Unit = {},
    onReviewOriginalSubmission: (() -> Unit)? = null,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val validate by rememberUpdatedState(validateScope)
    val sessionCurrent by rememberUpdatedState(isSessionCurrent)
    val apply by rememberUpdatedState(onApply)
    val selection = remember { selectedIds.toSet() }
    // This is the established APP wire subset, not an assertion of Router tool support.
    val caps = remember(supportsRelationGeometry) { aiCanvasToolCapabilities(supportsRelationGeometry) }
    var whole by remember { mutableStateOf(false) }
    var showScope by remember { mutableStateOf(false) }
    val approved = if (whole) workspace.objects.keys.toSet() else selection
    val scopeReferences = remember(approved) { aiDiagramObjectReferences(approved) }
    val scopeRelations = remember(workspace, approved) { aiDiagramRelationReferences(workspace, approved) }
    val store = remember { aiSettingsStore() }
    var conn by remember { mutableStateOf(AiConnectionSettings()) }
    var token by remember { mutableStateOf("") }
    var storedToken by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsProblem by remember { mutableStateOf<String?>(null) }
    val requestParameters = remember(conn.maxTokens, conn.temperature) {
        runCatching { parseAiRequestParameters(conn.maxTokens, conn.temperature) }
    }
    var task by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var appliedSuccessfully by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableStateOf<AiDiagramRequestReview?>(null) }
    val entries = remember { mutableStateListOf<AiChatEntry>() }
    var nextEntryId by remember { mutableStateOf(0) }
    var references by remember { mutableStateOf<Map<String, CanvasObjectId>>(emptyMap()) }
    var plan by remember { mutableStateOf<AiProposal?>(null) }
    var changesExpanded by remember { mutableStateOf(initialChangesExpanded) }
    var applyConsent by remember { mutableStateOf(false) }
    // A failed retainDraft can have published the journal before reporting failure. Never
    // reuse this proposal's transaction ID with different exclusions after that boundary.
    var frozenApplyPlan by remember { mutableStateOf<AiProposal?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    var diagnostics by remember { mutableStateOf<List<AiProbeDiagnostic>>(emptyList()) }
    var localLogging by remember { mutableStateOf(false) }
    val gate =
        remember {
            AiDiagramRoundApproval { review ->
                pending = review
            }
        }

    fun entryId() = nextEntryId++

    fun setRequestState(
        round: Int,
        state: AiRequestState,
    ) {
        val index = entries.indexOfLast { it is AiChatEntry.Request && it.round == round }
        if (index >= 0) entries[index] = (entries[index] as AiChatEntry.Request).copy(state = state)
    }

    fun clearPlan() {
        references = emptyMap()
        plan = null
        frozenApplyPlan = null
        applyConsent = false
        message = null
    }

    fun close() {
        open = false
        gate.close()
        job?.cancel()
        token = ""
        clearPlan()
        onDismiss()
    }
    LaunchedEffect(Unit) {
        store?.loadSettings()?.let { conn = it.toConnection() }
        store?.loadToken()?.let {
            token = it
            storedToken = true
        }
        if (!conn.configured) showSettings = true
    }
    DisposableEffect(Unit) {
        onDispose {
            open = false
            gate.close()
            job?.cancel()
            token = ""
            plan = null
            frozenApplyPlan = null
        }
    }
    LaunchedEffect(isSessionCurrent()) {
        if (!sessionCurrent()) {
            gate.close()
            job?.cancel()
            clearPlan()
            message = if (appliedSuccessfully) Strings.aiTools.queued() else Strings.aiProbe.debugHintWorkspace()
        }
    }
    val scopeValid = approved.size <= 128 && scopeRelations.size <= 256 && approved.all { it in workspace.objects }
    val preview = remember(plan, workspace) { plan?.preview(workspace) }
    val canSend =
        !busy && !applying && !appliedSuccessfully && plan == null && requestParameters.isSuccess && scopeValid && sessionCurrent() && task.isNotBlank() &&
            conn.configured && (conn.authentication == "None" || token.isNotBlank())
    val listState = rememberScrollState()
    LaunchedEffect(entries.size, pending, plan, message) { listState.animateScrollTo(listState.maxValue) }

    fun start() {
        if (!canSend || !open) return
        val server =
            AiServerProfile(
                conn.endpoint,
                if (conn.allowInsecureLocalHttp) AiServerLocation.Local else AiServerLocation.Remote,
                AiRequestBodyProfile(
                    conn.dialect,
                    conn.model,
                    requestParameters.getOrThrow().maxOutputTokens,
                    requestParameters.getOrThrow().temperature,
                    conn.chatTokenLimitField,
                ),
                conn.anthropicVersion.takeIf { conn.dialect == AiRequestDialect.AnthropicMessages },
                conn.allowInsecureLocalHttp,
            )
        val request = randomUuid()
        val session = randomUuid()
        val approvedScope = approved.toSet()
        val credential = token
        val mode = conn.authentication
        val prompt = task
        val trace = if (conn.traceToolCalls) aiToolTrace() else null
        trace?.record("task", request, 0, mapOf("prompt" to prompt, "model" to conn.model, "dialect" to conn.dialect.name, "scopeObjects" to approvedScope.size.toString()))
        entries += AiChatEntry.User(entryId(), prompt)
        task = ""
        clearPlan()
        busy = true
        appliedSuccessfully = false
        message = null
        round = 0
        localLogging = false
        diagnostics = emptyList()
        job =
            scope.launch {
                var logger: AiProbeLocalLog? = null
                var logRound = 0
                var diagnosticTimeline: AiProbeDiagnosticTimeline? = null
                // Round numbers restart for each task. Never locate a previous task's
                // card by round alone (Preparing can arrive before this task has a card).
                val requestEntries = mutableMapOf<Int, Int>()
                fun recordTerminal(event: AiProbeDiagnostic) {
                    if (diagnostics.lastOrNull()?.stage == AiProbeStage.Failed ||
                        diagnostics.lastOrNull()?.stage == AiProbeStage.Cancelled) return
                    val displayed = (diagnosticTimeline ?: AiProbeDiagnosticTimeline().also {
                        diagnosticTimeline = it
                    }).record(event) ?: return
                    logger?.record(displayed)
                    if (open) {
                        diagnostics = (diagnostics + displayed).takeLast(24)
                        val id = requestEntries[logRound]
                        val index = entries.indexOfFirst { it is AiChatEntry.Request && it.id == id }
                        if (index >= 0) {
                            val entry = entries[index] as AiChatEntry.Request
                            entries[index] = entry.copy(diagnostic = displayed)
                        }
                    }
                }
                try {
                    when (
                        val result =
                            runAiDiagramToolLoop(
                                server,
                                request,
                                session,
                                prompt,
                                workspace,
                                approvedScope,
                                caps,
                                approve = { review ->
                                    val id = entryId()
                                    requestEntries[review.round] = id
                                    entries +=
                                        AiChatEntry.Request(
                                            id, review.round, review.server.endpoint, review.server.body.model, review.body,
                                            AiRequestState.Pending,
                                        )
                                    gate.await(review)
                                },
                                validateScope = { validate() },
                                isSessionCurrent = { open && sessionCurrent() },
                                credentialHeaders = {
                                    aiCredentialHeaders(
                                        when (mode) {
                                            "Bearer" -> AiProbeAuthentication.Bearer
                                            "x-api-key" -> AiProbeAuthentication.ApiKey
                                            else -> AiProbeAuthentication.None
                                        },
                                        credential,
                                    )
                                },
                                onProgress = { event ->
                                    if (open) {
                                        when (event) {
                                            is AiDiagramLoopProgress.Text -> {
                                                val last = entries.lastOrNull()
                                                if (last is AiChatEntry.Assistant && last.round == event.round) {
                                                    entries[entries.lastIndex] = last.copy(text = last.text + event.text)
                                                } else {
                                                    entries += AiChatEntry.Assistant(entryId(), event.round, event.text)
                                                }
                                            }

                                            is AiDiagramLoopProgress.Tools -> {
                                                event.calls.zip(event.results).forEach { (call, result) ->
                                                    trace?.record(
                                                        "tool", request, event.round,
                                                        mapOf(
                                                            "tool" to call.name,
                                                            "status" to result.status.name,
                                                            "failure" to result.failure?.name.orEmpty(),
                                                            "arguments" to call.arguments,
                                                            "result" to result.data.toString(),
                                                        ),
                                                    )
                                                }
                                                entries += AiChatEntry.Tools(entryId(), event.round, aiToolLines(event.calls, event.results))
                                            }
                                        }
                                    }
                                },
                                onDiagnostic = { n, event ->
                                    if (n != logRound) {
                                        logRound = n
                                        diagnosticTimeline = AiProbeDiagnosticTimeline()
                                        logger =
                                            aiProbeLocalLog(
                                                server.body.dialect,
                                                when (mode) {
                                                    "Bearer" -> AiProbeAuthentication.Bearer
                                                    "x-api-key" -> AiProbeAuthentication.ApiKey
                                                    else -> AiProbeAuthentication.None
                                                },
                                            )
                                        localLogging = logger != null
                                    }
                                    val displayed = (diagnosticTimeline ?: AiProbeDiagnosticTimeline().also {
                                        diagnosticTimeline = it
                                    }).record(event)
                                    if (displayed != null) logger?.record(displayed)
                                    if (open) {
                                        round = n
                                        if (displayed != null) diagnostics = (diagnostics + displayed).takeLast(24)
                                        val activeEntryId = requestEntries[n]
                                        val index = if (activeEntryId == null) -1 else entries.indexOfFirst {
                                            it is AiChatEntry.Request && it.id == activeEntryId
                                        }
                                        if (index >= 0) {
                                            val entry = entries[index] as AiChatEntry.Request
                                            entries[index] = entry.copy(diagnostic = displayed ?: entry.diagnostic ?: event)
                                        }
                                    }
                                },
                            )
                    ) {
                        is AiDiagramLoopResult.Ready -> {
                            ensureActive()
                            if (open && sessionCurrent()) {
                                plan = result.proposal
                                references = result.localReferences.toMap()
                                // A reply with no tool calls was already streamed into the transcript.
                                trace?.record("final", request, round, mapOf("text" to result.text, "proposalItems" to (result.proposal?.items?.size ?: 0).toString()))
                                if (entries.none { it is AiChatEntry.Assistant && it.text == result.text } && result.text.isNotBlank()) {
                                    entries += AiChatEntry.Assistant(entryId(), round, result.text)
                                }
                                message = if (result.proposal == null) Strings.aiTools.noChanges() else Strings.aiTools.ready()
                            }
                        }

                        is AiDiagramLoopResult.Failed -> {
                            if (open) {
                                clearPlan()
                                message =
                                    if (result.code == AiDiagramLoopFailure.ConsentDenied) {
                                        Strings.aiProbe.consentDeclined()
                                    } else {
                                        Strings.aiTools.failed(result.code.name)
                                    }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    recordTerminal(AiProbeDiagnostic(AiProbeStage.Cancelled))
                    if (open) clearPlan()
                    throw cancelled
                } catch (failure: Exception) {
                    recordTerminal(AiProbeDiagnostic(AiProbeStage.Failed,
                        if (failure is AiRequestValidationException) AiProbeFailure.Configuration else AiProbeFailure.Unknown))
                    if (open) {
                        clearPlan()
                        message = Strings.aiProbe.failed()
                    }
                } finally {
                    busy = false
                    pending = null
                    applyConsent = false
                    // A request that never got an answer (cancel, scope change) must not keep looking actionable.
                    entries.forEachIndexed { index, entry ->
                        if (entry is AiChatEntry.Request && entry.state == AiRequestState.Pending) {
                            entries[index] = entry.copy(state = AiRequestState.Denied)
                        }
                    }
                }
            }
    }
    Dialog(onDismissRequest = { if (!applying) close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 920.dp).fillMaxWidth(.94f).fillMaxHeight(.92f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(Strings.aiTools.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.aiTools.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    if (frozenApplyPlan != null && !applying) BasicText(Strings.aiTools.publicationUnconfirmed(),
                        style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    if (frozenApplyPlan != null && !applying) onReviewOriginalSubmission?.let { reviewOriginal ->
                        ShellButton(Strings.draftReview.originalTransactions(), onClick = { close(); reviewOriginal() })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.aiTools.settings(), compact = true, enabled = !busy && !applying && frozenApplyPlan == null,
                            onClick = { showSettings = true })
                        ShellButton(Strings.aiProbe.debugCopy(), compact = true, enabled = diagnostics.isNotEmpty(), onClick = {
                            clipboard.setText(
                                AnnotatedString("BoarderLess AI tool probe\n" + diagnostics.joinToString("\n") { it.reportLine() } +
                                    "\nRequest outcomes\n" + entries.filterIsInstance<AiChatEntry.Request>().joinToString("\n") {
                                        "entry=${it.id} round=${it.round} approval=${it.state.name} " + (it.diagnostic?.reportLine() ?: "NoTransportResult")
                                    }),
                            )
                        })
                        if (busy) {
                            ShellButton(Strings.aiProbe.cancelRequest(), compact = true, onClick = {
                                job?.cancel()
                                clearPlan()
                            })
                        }
                        ShellButton(Strings.common.close(), compact = true, enabled = !applying, onClick = { close() })
                    }
                    BasicText(
                        aiProbeDiagnosticLabel(diagnostics.lastOrNull()) + (diagnostics.lastOrNull()?.httpStatus?.let { " · HTTP $it" } ?: ""),
                        style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                    )
                    diagnostics.lastOrNull()?.takeIf { it.stage == AiProbeStage.Failed }?.let {
                        BasicText(aiProbeDiagnosticHint(it), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    }
                    diagnostics.lastOrNull()?.reply?.let {
                        BasicText(Strings.aiProbe.replySummary(it.textBytes, it.toolCalls, it.argumentBytes),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                    }
                    if (localLogging) BasicText(Strings.aiProbe.localLog(), style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
                    if (appliedSuccessfully) {
                        BasicText(Strings.aiTools.appliedReviewInfo(), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    }
                    Column(Modifier.weight(1f).verticalScroll(listState), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!conn.configured) {
                            BasicText(Strings.aiTools.notConfigured(), style = TextStyle(color = colors.contentText, fontSize = 13.sp))
                        }
                        entries.forEach { entry ->
                            key(entry.id) {
                                AiChatEntryView(
                                    entry = entry,
                                    pending = pending,
                                    canRespond = sessionCurrent(),
                                    onApprove = { review ->
                                        if (gate.respond(review, true)) setRequestState(review.round, AiRequestState.Approved)
                                    },
                                    onDeny = { review ->
                                        if (gate.respond(review, false)) setRequestState(review.round, AiRequestState.Denied)
                                    },
                                )
                            }
                        }
                        message?.let { BasicText(it, style = TextStyle(color = colors.contentText, fontSize = 12.sp)) }
                        plan?.let { proposal ->
                            BasicText(Strings.aiTools.preview(), style = TextStyle(color = colors.contentText, fontSize = 15.sp))
                            proposal.items.forEach { item ->
                                ShellButton(
                                    Strings.aiTools.include(aiToolLabel(item.summary)),
                                    accent = item.included,
                                    enabled = !applying && frozenApplyPlan == null,
                                    onClick = {
                                        if (!applying && frozenApplyPlan == null) {
                                            plan = proposal.withItemIncluded(item.itemId, !item.included)
                                            applyConsent = false
                                        }
                                    },
                                )
                            }
                            if (frozenApplyPlan != null) {
                                BasicText(Strings.aiTools.selectionFixed(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            }
                            when (val p = preview) {
                                is AiProposalPreview.Ready -> {
                                    AiDiagramDraftPreview(p.workspace, references)
                                    AiProposalChangeReview(workspace, p.workspace, changesExpanded) {
                                        changesExpanded = it
                                        onChangesExpandedChange(it)
                                    }
                                }
                                is AiProposalPreview.Conflict -> BasicText(Strings.aiTools.conflict(), style = TextStyle(color = colors.danger))
                                else -> BasicText(Strings.aiTools.empty(), style = TextStyle(color = colors.contentMuted))
                            }
                            ShellButton(
                                Strings.aiTools.applyConsent(),
                                accent = applyConsent,
                                enabled = !applying && frozenApplyPlan == null && preview is AiProposalPreview.Ready,
                                onClick = { applyConsent = !applyConsent },
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(
                                    Strings.aiTools.apply(),
                                    enabled = applyConsent && frozenApplyPlan == null && preview is AiProposalPreview.Ready && !applying && sessionCurrent(),
                                    onClick = applyPlan@{
                                        if (applying || busy || frozenApplyPlan != null || !applyConsent || !open || plan !== proposal) return@applyPlan
                                        val reviewed =
                                            frozenApplyPlan ?: proposal
                                                .copy(items = proposal.items.toList())
                                                .also { frozenApplyPlan = it }
                                        applyConsent = false
                                        applying = true
                                        job =
                                            scope.launch {
                                                try {
                                                    when (apply(reviewed)) {
                                                        AiDiagramApplyOutcome.Queued -> {
                                                            appliedSuccessfully = true
                                                            message = Strings.aiTools.queued()
                                                            plan = null
                                                            frozenApplyPlan = null
                                                            applyConsent = false
                                                        }
                                                        AiDiagramApplyOutcome.Rejected -> {
                                                            frozenApplyPlan = null
                                                            message = Strings.aiTools.applyFailed()
                                                        }
                                                        AiDiagramApplyOutcome.Unconfirmed -> {
                                                            message = Strings.aiTools.publicationUnconfirmed()
                                                        }
                                                    }
                                                } catch (cancelled: CancellationException) {
                                                    message = Strings.aiTools.publicationUnconfirmed()
                                                    throw cancelled
                                                } catch (_: Exception) {
                                                    message = Strings.aiTools.publicationUnconfirmed()
                                                } finally {
                                                    applying = false
                                                }
                                            }
                                    },
                                )
                                ShellButton(Strings.aiTools.discardDraft(), enabled = !applying && frozenApplyPlan == null, onClick = { clearPlan() })
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.aiTools.scopeSelection(), compact = true, accent = !whole, enabled = !busy && plan == null, onClick = { whole = false })
                        ShellButton(Strings.aiTools.scopeWhole(), compact = true, accent = whole, enabled = !busy && plan == null, onClick = { whole = true })
                        ShellButton(
                            if (showScope) Strings.aiTools.hideScope() else Strings.aiTools.showScope(),
                            compact = true,
                            onClick = { showScope = !showScope },
                        )
                    }
                    BasicText(Strings.aiTools.scope(approved.size), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                    if (!scopeValid) BasicText(Strings.aiTools.contextInvalid(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                    if (showScope) {
                        Column(Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState())) {
                            BasicText(Strings.aiTools.dataShared(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                            // Let the user inspect every approved object before starting. No asset URLs.
                            SelectionContainer {
                                BasicText(
                                    scopeReferences.entries
                                        .filter { it.value in workspace.objects }
                                        .joinToString("\n") { (ref, id) ->
                                            ref + ": " + when (val node = workspace.objects.getValue(id)) {
                                                is TextNode -> node.text
                                                is GroupFrame -> node.title
                                                is cg.creamgod.boarderless.domain.model.ExternalMediaNode -> node.altText
                                                is MediaNode -> node.altText
                                            }
                                        },
                                    style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                )
                            }
                            if (scopeRelations.isNotEmpty()) {
                                SelectionContainer {
                                    val byId = scopeReferences.entries.associate { it.value to it.key }
                                    BasicText(
                                        scopeRelations.entries.joinToString("\n") { (ref, id) ->
                                            val edge = workspace.relations.getValue(id)
                                            val arrow =
                                                when (edge.direction) {
                                                    RelationDirection.Forward -> "→"
                                                    RelationDirection.Backward -> "←"
                                                    RelationDirection.Both -> "↔"
                                                    RelationDirection.None -> "—"
                                                }
                                            "$ref: ${byId.getValue(edge.sourceObjectId)} $arrow ${byId.getValue(edge.targetObjectId)}" +
                                                (edge.label?.let { " · $it" } ?: "")
                                        },
                                        style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                    if (plan != null) BasicText(Strings.aiTools.finishDraftFirst(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            AiProbeField(
                                if (task.isEmpty()) Strings.aiTools.composerHint() else Strings.aiTools.task(),
                                task,
                                {
                                    if (aiTextWithinUtf8Limit(it, 65536)) task = it
                                    else message = Strings.aiTools.inputRejected()
                                },
                                enabled = !busy && plan == null,
                                multiline = true,
                            )
                        }
                        ShellButton(Strings.aiTools.send(), accent = true, enabled = canSend, onClick = { start() })
                    }
                }
            }
        }
    }
    if (showSettings) {
        AiSettingsDialog(
            initial = conn,
            hasStoredToken = storedToken,
            sessionHasToken = token.isNotEmpty(),
            tokenProtected = store?.tokenProtected == true,
            problem = settingsProblem,
            onSave = { result ->
                scope.launch {
                    var ok = true
                    if (store != null) ok = store.saveSettings(result.settings.toSaved())
                    result.token?.let { typed ->
                        if (result.rememberToken && store != null) {
                            if (store.saveToken(typed)) storedToken = true else ok = false
                        }
                        token = typed
                    }
                    if (ok) {
                        conn = result.settings
                        settingsProblem = null
                        showSettings = false
                        message = Strings.aiTools.settingsSaved()
                    } else {
                        settingsProblem = Strings.aiTools.settingsSaveFailed()
                    }
                }
            },
            onForgetToken = {
                scope.launch {
                    store?.clearToken()
                    token = ""
                    storedToken = false
                    settingsProblem = Strings.aiTools.tokenForgotten()
                }
            },
            onDismiss = {
                settingsProblem = null
                showSettings = false
            },
        )
    }
}

@Composable internal fun AiDiagramDraftPreview(
    workspace: Workspace,
    references: Map<String, CanvasObjectId>,
) {
    val colors = BoarderLessTheme.colors
    val measurer = rememberTextMeasurer()
    val nodes = references.values.mapNotNull { workspace.objects[it] }
    val ids = nodes.map { it.id }.toSet()
    val names = references.entries.associate { it.value to it.key }
    if (nodes.isEmpty()) return
    val bounds =
        nodes.map { node ->
            val t = node.transform
            val angle = t.rotationDegrees.toDouble() * PI / 180
            val w = t.size.width.toDouble()
            val h = t.size.height.toDouble()
            val rx = (abs(cos(angle)) * w + abs(sin(angle)) * h) / 2
            val ry = (abs(sin(angle)) * w + abs(cos(angle)) * h) / 2
            val cx = t.position.x.toDouble() + w / 2
            val cy = t.position.y.toDouble() + h / 2
            listOf(cx - rx, cy - ry, cx + rx, cy + ry)
        }
    val left = bounds.minOf { it[0] }
    val top = bounds.minOf { it[1] }
    val width = (bounds.maxOf { it[2] } - left).coerceAtLeast(1.0)
    val height = (bounds.maxOf { it[3] } - top).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(260.dp).background(colors.canvas)) {
        val scale = minOf((size.width - 32.0) / width, (size.height - 32.0) / height).coerceAtLeast(0.0)

        fun position(node: CanvasObject) =
            Offset(
                (
                    16 + (
                        node.transform.position.x
                            .toDouble() - left
                    ) * scale
                ).toFloat(),
                (
                    16 +
                        (
                            node.transform.position.y
                                .toDouble() - top
                        ) * scale
                ).toFloat(),
            )
        clipRect {
            workspace.relations.values.filter { it.sourceObjectId in ids && it.targetObjectId in ids }.forEach { relation ->
                val a = workspace.objects.getValue(relation.sourceObjectId)
                val b = workspace.objects.getValue(relation.targetObjectId)
                drawLine(
                    colors.contentMuted,
                    position(a) + Offset((a.transform.size.width * scale / 2).toFloat(), (a.transform.size.height * scale / 2).toFloat()),
                    position(b) + Offset((b.transform.size.width * scale / 2).toFloat(), (b.transform.size.height * scale / 2).toFloat()),
                    1.5f,
                )
            }
            nodes.sortedBy { if (it is GroupFrame) 0 else 1 }.forEach { node ->
                val shape = if (node is TextNode) node.shape else NodeShape.Rectangle
                val fill = if (node is TextNode) nodeFillColor(node.colorToken, colors) else colors.contentSurface.copy(alpha = .35f)
                val nodeSize = Size((node.transform.size.width * scale).toFloat(), (node.transform.size.height * scale).toFloat())
                rotate(node.transform.rotationDegrees, pivot = position(node) + Offset(nodeSize.width / 2, nodeSize.height / 2)) {
                    drawNodePreviewShape(shape, fill, colors.contentBorder, position(node), nodeSize, 4f, 1f)
                    val text =
                        when (node) {
                            is TextNode -> node.text
                            is GroupFrame -> node.title
                            is cg.creamgod.boarderless.domain.model.ExternalMediaNode -> node.altText
                            is MediaNode -> node.altText
                        }
                    drawText(
                        textMeasurer = measurer,
                        text = text.take(60),
                        topLeft = position(node) + Offset(3f, 3f),
                        style = TextStyle(color = colors.contentText, fontSize = 10.sp),
                    )
                }
            }
        }
    }
    // Text inventory preserves full labels/directions even when the fit preview is small.
    nodes.forEach { node ->
        BasicText(
            "${names[node.id]}: " +
                when (node) {
                    is TextNode -> node.text
                    is GroupFrame -> node.title
                    is cg.creamgod.boarderless.domain.model.ExternalMediaNode -> node.altText
                    is MediaNode -> node.altText
                },
            style = TextStyle(color = colors.contentText, fontSize = 12.sp),
        )
        BasicText(
            "(${node.transform.position.x}, ${node.transform.position.y}) · ${node.transform.size.width} × ${node.transform.size.height} · ${node.transform.rotationDegrees}°",
            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
        )
    }
    workspace.relations.values.filter { it.sourceObjectId in ids && it.targetObjectId in ids }.forEach { relation ->
        BasicText(
            "${names[relation.sourceObjectId]} ${relation.direction} ${names[relation.targetObjectId]} · ${relation.label.orEmpty()} · ${relation.intent.orEmpty()}",
            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
        )
    }
}
