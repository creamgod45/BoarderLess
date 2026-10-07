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

/** Tool wizard, per-round consent and batch proposal review. Credentials remain memory-only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiDiagramToolDialog(
    workspace: Workspace,
    selectedIds: Set<CanvasObjectId>,
    validateScope: suspend () -> Boolean,
    isSessionCurrent: () -> Boolean,
    onApply: suspend (AiProposal) -> AiDiagramApplyOutcome,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val validate by rememberUpdatedState(validateScope)
    val sessionCurrent by rememberUpdatedState(isSessionCurrent)
    val apply by rememberUpdatedState(onApply)
    val selection = remember { selectedIds.toSet() }
    // This is the established APP wire subset, not an assertion of Router tool support.
    val caps =
        remember {
            AiDiagramCapabilities(
                setOf(
                    NodeShape.RoundedRectangle,
                    NodeShape.Rectangle,
                    NodeShape.Ellipse,
                    NodeShape.Diamond,
                    NodeShape.Pill,
                    NodeShape.Parallelogram,
                    NodeShape.Hexagon,
                ),
                NodeColorPresetTokens.toSet(),
                setOf("group"),
                selfLoops = false,
            )
        }
    var whole by remember { mutableStateOf(false) }
    val approved = if (whole) workspace.objects.keys.toSet() else selection
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var version by remember { mutableStateOf("") }
    var dialect by remember { mutableStateOf(AiRequestDialect.OpenAiChat) }
    var auth by remember { mutableStateOf("Bearer") }
    var localHttp by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var pending by remember { mutableStateOf<AiDiagramRequestReview?>(null) }
    var consented by remember { mutableStateOf<AiDiagramRequestReview?>(null) }
    var references by remember { mutableStateOf<Map<String, CanvasObjectId>>(emptyMap()) }
    var plan by remember { mutableStateOf<AiProposal?>(null) }
    var applyConsent by remember { mutableStateOf(false) }
    // A failed retainDraft can have published the journal before reporting failure. Never
    // reuse this proposal's transaction ID with different exclusions after that boundary.
    var frozenApplyPlan by remember { mutableStateOf<AiProposal?>(null) }
    var answer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var textRound by remember { mutableStateOf(0) }
    var round by remember { mutableStateOf(0) }
    var diagnostics by remember { mutableStateOf<List<AiProbeDiagnostic>>(emptyList()) }
    var localLogging by remember { mutableStateOf(false) }
    val gate =
        remember {
            AiDiagramRoundApproval {
                pending = it
                consented = null
            }
        }

    fun clearPlan() {
        references = emptyMap()
        plan = null
        frozenApplyPlan = null
        applyConsent = false
        answer = ""
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
    DisposableEffect(Unit) {
        onDispose {
            open = false
            gate.close()
            job?.cancel()
            token = ""
            plan = null
            frozenApplyPlan = null
            answer = ""
        }
    }
    LaunchedEffect(isSessionCurrent()) {
        if (!sessionCurrent()) {
            gate.close()
            job?.cancel()
            clearPlan()
            message =
                Strings.aiProbe.debugHintWorkspace()
        }
    }
    val scopeValid = approved.size <= 128 && approved.all { it in workspace.objects }
    val preview = remember(plan, workspace) { plan?.preview(workspace) }
    Dialog(onDismissRequest = { if (!applying) close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 920.dp).fillMaxWidth(.94f).fillMaxHeight(.92f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(Strings.aiTools.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.aiTools.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    BasicText(
                        aiProbeStageLabel(diagnostics.lastOrNull()?.stage) +
                            (diagnostics.lastOrNull()?.httpStatus?.let { " · HTTP $it" } ?: ""),
                        style = TextStyle(color = colors.contentText),
                    )
                    diagnostics
                        .lastOrNull()
                        ?.takeIf {
                            it.stage == AiProbeStage.Failed
                        }?.let { BasicText(aiProbeDiagnosticHint(it), style = TextStyle(color = colors.danger, fontSize = 12.sp)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.aiProbe.debugCopy(), enabled = diagnostics.isNotEmpty(), onClick = {
                            clipboard.setText(
                                AnnotatedString("BoarderLess AI tool probe\n" + diagnostics.joinToString("\n") { it.reportLine() }),
                            )
                        })
                        if (busy) {
                            ShellButton(Strings.aiProbe.cancelRequest(), onClick = {
                                job?.cancel()
                                consented = null
                                clearPlan()
                            })
                        }
                    }
                    if (localLogging) {
                        BasicText(
                            Strings.aiProbe.localLog(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentText, fontSize = 12.sp)) }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!busy && plan == null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(Strings.aiTools.scopeSelection(), accent = !whole, onClick = {
                                    whole = false
                                    clearPlan()
                                })
                                ShellButton(Strings.aiTools.scopeWhole(), accent = whole, onClick = {
                                    whole = true
                                    clearPlan()
                                })
                                ShellButton("OpenAI-compatible", accent = dialect == AiRequestDialect.OpenAiChat, onClick = {
                                    dialect =
                                        AiRequestDialect.OpenAiChat
                                })
                                ShellButton("Claude-compatible", accent = dialect == AiRequestDialect.AnthropicMessages, onClick = {
                                    dialect =
                                        AiRequestDialect.AnthropicMessages
                                })
                            }
                            BasicText(
                                Strings.aiTools.scope(approved.size),
                                style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                            )
                            // Let the user inspect every approved object before starting. No asset URLs.
                            SelectionContainer {
                                BasicText(
                                    approved
                                        .filter {
                                            it in workspace.objects
                                        }.sortedBy { it.value }
                                        .joinToString("\n") { id ->
                                            when (val node = workspace.objects.getValue(id)) {
                                                is TextNode -> node.text
                                                is GroupFrame -> node.title
                                                is MediaNode -> node.altText
                                            }
                                        },
                                    style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                )
                            }
                            if (!scopeValid) BasicText(Strings.aiTools.contextInvalid(), style = TextStyle(color = colors.danger))
                            AiProbeField(Strings.aiProbe.endpoint(), endpoint, { if (it.length <= 4096) endpoint = it })
                            AiProbeField(Strings.aiProbe.model(), model, { if (it.length <= 256) model = it })
                            if (dialect ==
                                AiRequestDialect.AnthropicMessages
                            ) {
                                AiProbeField(Strings.aiProbe.version(), version, {
                                    if (it.length <=
                                        32
                                    ) {
                                        version = it
                                    }
                                })
                            }
                            AiProbeField(Strings.aiProbe.token(), token, { if (it.length <= 8192) token = it }, secret = true)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Bearer", "x-api-key", "None").forEach { mode ->
                                    ShellButton(
                                        mode,
                                        accent =
                                            auth == mode,
                                        onClick = { auth = mode },
                                    )
                                }
                            }
                            ShellButton(Strings.aiProbe.localHttp(), accent = localHttp, onClick = { localHttp = !localHttp })
                            AiProbeField(
                                Strings.aiTools.task(),
                                task,
                                { if (it.encodeToByteArray().size <= 65536) task = it },
                                multiline = true,
                            )
                            ShellButton(
                                Strings.aiTools.start(),
                                enabled =
                                    scopeValid && sessionCurrent() && task.isNotBlank() && endpoint.isNotBlank() && model.isNotBlank() &&
                                        (auth == "None" || token.isNotBlank()),
                                onClick = startTask@{
                                    if (busy || applying || !open) return@startTask
                                    val server =
                                        AiServerProfile(
                                            endpoint,
                                            if (localHttp) AiServerLocation.Local else AiServerLocation.Remote,
                                            AiRequestBodyProfile(dialect, model, 4096),
                                            version.takeIf {
                                                dialect ==
                                                    AiRequestDialect.AnthropicMessages
                                            },
                                            localHttp,
                                        )
                                    val request = randomUuid()
                                    val session = randomUuid()
                                    val approvedScope = approved.toSet()
                                    val credential = token
                                    val mode = auth
                                    clearPlan()
                                    busy = true
                                    diagnostics = emptyList()
                                    job =
                                        scope.launch {
                                            var logger: AiProbeLocalLog? = null
                                            var logRound = 0
                                            try {
                                                when (
                                                    val result =
                                                        runAiDiagramToolLoop(
                                                            server,
                                                            request,
                                                            session,
                                                            task,
                                                            workspace,
                                                            approvedScope,
                                                            caps,
                                                            approve = { gate.await(it) },
                                                            validateScope = { validate() },
                                                            isSessionCurrent = {
                                                                open &&
                                                                    sessionCurrent()
                                                            },
                                                            credentialHeaders = {
                                                                when (mode) {
                                                                    "Bearer" -> headersOf(HttpHeaders.Authorization, "Bearer $credential")
                                                                    "x-api-key" -> headersOf("x-api-key", credential)
                                                                    else -> Headers.Empty
                                                                }
                                                            },
                                                            onProgress = { event ->
                                                                if (open) {
                                                                    when (event) {
                                                                        is AiDiagramLoopProgress.Text -> {
                                                                            if (textRound !=
                                                                                event.round
                                                                            ) {
                                                                                answer = ""
                                                                            }
                                                                            textRound = event.round
                                                                            answer += event.text
                                                                        }

                                                                        is AiDiagramLoopProgress.Tools -> {
                                                                            message =
                                                                                Strings.aiTools.tools(event.round, event.results.size)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            onDiagnostic = { n, event ->
                                                                if (n !=
                                                                    logRound
                                                                ) {
                                                                    logRound = n
                                                                    logger =
                                                                        aiProbeLocalLog(
                                                                            server.body.dialect,
                                                                            when (mode) {
                                                                                "Bearer" -> AiProbeAuthentication.Bearer
                                                                                "x-api-key" -> AiProbeAuthentication.ApiKey
                                                                                else -> AiProbeAuthentication.None
                                                                            },
                                                                        )
                                                                    localLogging =
                                                                        logger != null
                                                                }
                                                                logger?.record(event)
                                                                if (open) {
                                                                    round = n
                                                                    diagnostics = (diagnostics + event).takeLast(24)
                                                                }
                                                            },
                                                        )
                                                ) {
                                                    is AiDiagramLoopResult.Ready -> {
                                                        ensureActive()
                                                        if (open &&
                                                            sessionCurrent()
                                                        ) {
                                                            plan = result.proposal
                                                            references = result.localReferences.toMap()
                                                            answer =
                                                                result.text
                                                            message =
                                                                if (result.proposal ==
                                                                    null
                                                                ) {
                                                                    Strings.aiTools.noChanges()
                                                                } else {
                                                                    Strings.aiTools.ready()
                                                                }
                                                        }
                                                    }

                                                    is AiDiagramLoopResult.Failed -> {
                                                        if (open) {
                                                            clearPlan()
                                                            message =
                                                                Strings.aiTools.failed(result.code.name)
                                                        }
                                                    }
                                                }
                                            } catch (
                                                cancelled: CancellationException,
                                            ) {
                                                if (open) clearPlan()
                                                throw cancelled
                                            } catch (
                                                _: Exception,
                                            ) {
                                                if (open) {
                                                    clearPlan()
                                                    message = Strings.aiProbe.failed()
                                                }
                                            } finally {
                                                busy = false
                                                pending = null
                                                consented = null
                                                applyConsent = false
                                            }
                                        }
                                },
                            )
                        }
                        pending?.let { review ->
                            BasicText(Strings.aiTools.round(review.round), style = TextStyle(color = colors.contentText, fontSize = 15.sp))
                            BasicText(Strings.aiTools.waiting(), style = TextStyle(color = colors.contentMuted))
                            SelectionContainer {
                                BasicText(
                                    "${review.server.endpoint}\n${review.server.body.model}\n${review.body}",
                                    Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                                    style = TextStyle(color = colors.contentText, fontSize = 11.sp),
                                )
                            }
                            ShellButton(Strings.aiProbe.consent(), accent = consented === review, onClick = {
                                consented =
                                    if (consented === review) null else review
                            })
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(Strings.aiTools.approve(), enabled = consented === review && sessionCurrent(), onClick = {
                                    gate.respond(review, true)
                                    consented =
                                        null
                                })
                                ShellButton(Strings.aiTools.deny(), onClick = {
                                    gate.respond(review, false)
                                    consented = null
                                })
                            }
                        }
                        if (answer.isNotEmpty()) {
                            SelectionContainer {
                                BasicText(
                                    answer,
                                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                                    style = TextStyle(color = colors.contentText, fontSize = 12.sp),
                                )
                            }
                        }
                        plan?.let { proposal ->
                            BasicText(Strings.aiTools.preview(), style = TextStyle(color = colors.contentText, fontSize = 15.sp))
                            proposal.items.forEach { item ->
                                ShellButton(
                                    Strings.aiTools.include(
                                        when (item.summary) {
                                            "create_nodes" -> Strings.aiTools.createNodes()
                                            "create_groups" -> Strings.aiTools.createGroups()
                                            "create_relations" -> Strings.aiTools.createRelations()
                                            "update_nodes" -> Strings.aiTools.updateNodes()
                                            "layout_nodes" -> Strings.aiTools.layoutNodes()
                                            else -> item.summary
                                        },
                                    ),
                                    accent = item.included,
                                    enabled =
                                        !applying && frozenApplyPlan == null,
                                    onClick = {
                                        if (!applying &&
                                            frozenApplyPlan == null
                                        ) {
                                            plan = proposal.withItemIncluded(item.itemId, !item.included)
                                            applyConsent =
                                                false
                                        }
                                    },
                                )
                            }
                            if (frozenApplyPlan !=
                                null
                            ) {
                                BasicText(
                                    Strings.aiTools.selectionFixed(),
                                    style = TextStyle(color = colors.contentMuted, fontSize = 12.sp),
                                )
                            }
                            when (val p = preview) {
                                is AiProposalPreview.Ready -> {
                                    AiDiagramDraftPreview(p.workspace, references)
                                }

                                is AiProposalPreview.Conflict -> {
                                    BasicText(
                                        Strings.aiTools.conflict(),
                                        style = TextStyle(color = colors.danger),
                                    )
                                }

                                else -> {
                                    BasicText(Strings.aiTools.empty(), style = TextStyle(color = colors.contentMuted))
                                }
                            }
                            ShellButton(
                                Strings.aiTools.applyConsent(),
                                accent = applyConsent,
                                enabled =
                                    !applying && preview is AiProposalPreview.Ready,
                                onClick = { applyConsent = !applyConsent },
                            )
                            ShellButton(
                                Strings.aiTools.apply(),
                                enabled =
                                    applyConsent && preview is AiProposalPreview.Ready && !applying && sessionCurrent(),
                                onClick = applyPlan@{
                                    if (applying || busy || !applyConsent || !open || plan !== proposal) return@applyPlan
                                    val reviewed =
                                        frozenApplyPlan ?: proposal
                                            .copy(
                                                items = proposal.items.toList(),
                                            ).also { frozenApplyPlan = it }
                                    applyConsent = false
                                    applying = true
                                    job =
                                        scope.launch {
                                            try {
                                                if (apply(reviewed) ==
                                                    AiDiagramApplyOutcome.Queued
                                                ) {
                                                    message = Strings.aiTools.queued()
                                                    plan = null
                                                    answer = ""
                                                    close()
                                                } else {
                                                    message =
                                                        Strings.aiTools.applyFailed()
                                                }
                                            } catch (
                                                cancelled: CancellationException,
                                            ) {
                                                throw cancelled
                                            } catch (
                                                _: Exception,
                                            ) {
                                                message = Strings.aiTools.applyFailed()
                                            } finally {
                                                applying = false
                                            }
                                        }
                                },
                            )
                        }
                        ShellButton(Strings.common.cancel(), enabled = !applying, onClick = { close() })
                    }
                }
            }
        }
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
