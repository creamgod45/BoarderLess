package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.data.remote.BackendHttpException
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.http.*
import kotlinx.coroutines.*

/** Verification-only settings wizard. No profile/token persistence or mutation callbacks. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiUnderstandingDialog(
    workspace: Workspace,
    selectedIds: Set<CanvasObjectId>,
    viewport: Viewport,
    screenSize: CanvasSize,
    validateScope: suspend () -> Boolean,
    onDismiss: () -> Unit,
    onTools: (() -> Unit)? = null,
) {
    val colors = BoarderLessTheme.colors
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val currentValidate by rememberUpdatedState(validateScope)
    var whole by remember { mutableStateOf(false) }
    val packet =
        remember(workspace, selectedIds, viewport, screenSize, whole) {
            runCatching { aiCanvasPacket(workspace, selectedIds, whole, viewport, screenSize) }.getOrNull()
        }
    var step by remember { mutableStateOf(0) }
    var endpoint by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var dialect by remember { mutableStateOf(AiRequestDialect.OpenAiChat) }
    var version by remember { mutableStateOf("") }
    var localHttp by remember { mutableStateOf(false) }
    var auth by remember { mutableStateOf("Bearer") }
    var consent by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var busy by remember { mutableStateOf(false) }
    var complete by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(true) }
    var localLogging by remember { mutableStateOf(false) }
    var diagnostics by remember { mutableStateOf<List<AiProbeDiagnostic>>(emptyList()) }

    fun resetApproval() {
        consent = false
        answer = ""
        message = null
        complete = false
        diagnostics = emptyList()
    }
    val close = {
        open = false
        job?.cancel()
        token = ""
        answer = ""
        consent = false
        onDismiss()
    }
    DisposableEffect(Unit) {
        onDispose {
            open = false
            job?.cancel()
            token = ""
            answer = ""
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 840.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.aiProbe.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    onTools?.let {
                        ShellButton(Strings.aiTools.title(), enabled = !busy, onClick = {
                            close()
                            it()
                        })
                    }
                    BasicText(Strings.aiProbe.warning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    val latest = diagnostics.lastOrNull()
                    BasicText(
                        aiProbeStageLabel(latest?.stage) + (latest?.failure?.let { " · ${it.name}" } ?: "") +
                            (latest?.httpStatus?.let { " · HTTP $it" } ?: ""),
                        style =
                            TextStyle(
                                color =
                                    if (latest?.stage ==
                                        AiProbeStage.Failed
                                    ) {
                                        colors.danger
                                    } else {
                                        colors.contentText
                                    },
                                fontSize = 14.sp,
                            ),
                    )
                    if (latest?.stage == AiProbeStage.Failed) {
                        BasicText(aiProbeDiagnosticHint(latest), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.aiProbe.debugCopy(), enabled = diagnostics.isNotEmpty(), onClick = {
                            clipboard.setText(
                                AnnotatedString("BoarderLess AI probe\n" + diagnostics.joinToString("\n") { it.reportLine() }),
                            )
                            message = Strings.aiProbe.copied()
                        })
                        if (busy) {
                            ShellButton(Strings.aiProbe.cancelRequest(), onClick = {
                                job?.cancel()
                                consent = false
                            })
                        }
                    }
                    if (diagnostics.isNotEmpty()) {
                        SelectionContainer {
                            BasicText(
                                diagnostics.joinToString("\n") { it.reportLine() },
                                Modifier.heightIn(max = 90.dp).verticalScroll(rememberScrollState()),
                                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                            )
                        }
                    }
                    if (localLogging) {
                        BasicText(
                            Strings.aiProbe.localLog(),
                            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                        )
                    }
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (step == 0) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(Strings.aiProbe.selection(), accent = !whole, onClick = {
                                    whole = false
                                    resetApproval()
                                })
                                ShellButton(Strings.aiProbe.whole(), accent = whole, onClick = {
                                    whole = true
                                    resetApproval()
                                })
                            }
                            if (packet == null) {
                                BasicText(Strings.aiProbe.contextInvalid(), style = TextStyle(color = colors.contentMuted))
                            } else {
                                SelectionContainer {
                                    BasicText(
                                        packet.json,
                                        Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                                        style = TextStyle(color = colors.contentText, fontSize = 11.sp),
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ShellButton(Strings.aiProbe.copy(), icon = ShellIcon.Copy, onClick = {
                                        clipboard.setText(AnnotatedString(packet.json))
                                        message = Strings.aiProbe.copied()
                                    })
                                    ShellButton(Strings.aiProbe.copyPrompt(), onClick = {
                                        clipboard.setText(AnnotatedString(packet.prompt))
                                        message = Strings.aiProbe.copied()
                                    })
                                    ShellButton(Strings.aiProbe.configuration(), onClick = {
                                        step = 1
                                        message = null
                                    })
                                }
                            }
                        }
                        if (step == 1) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ShellButton(
                                    "OpenAI-compatible",
                                    accent = dialect == AiRequestDialect.OpenAiChat,
                                    onClick = {
                                        dialect = AiRequestDialect.OpenAiChat
                                        resetApproval()
                                    },
                                )
                                ShellButton(
                                    "Claude-compatible",
                                    accent = dialect == AiRequestDialect.AnthropicMessages,
                                    onClick = {
                                        dialect = AiRequestDialect.AnthropicMessages
                                        resetApproval()
                                    },
                                )
                            }
                            AiProbeField(Strings.aiProbe.endpoint(), endpoint, {
                                if (it.length <= 4096) {
                                    endpoint = it
                                    resetApproval()
                                }
                            })
                            AiProbeField(Strings.aiProbe.model(), model, {
                                if (it.length <= 256) {
                                    model = it
                                    resetApproval()
                                }
                            })
                            if (dialect == AiRequestDialect.AnthropicMessages) {
                                AiProbeField(Strings.aiProbe.version(), version, {
                                    if (it.length <= 32) {
                                        version = it
                                        resetApproval()
                                    }
                                })
                            }
                            AiProbeField(Strings.aiProbe.token(), token, {
                                if (it.length <=
                                    8192
                                ) {
                                    token = it
                                    resetApproval()
                                }
                            }, secret = true)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Bearer", "x-api-key", "None").forEach { mode ->
                                    ShellButton(mode, accent = auth == mode, onClick = {
                                        auth = mode
                                        resetApproval()
                                    })
                                }
                            }
                            ShellButton(Strings.aiProbe.localHttp(), accent = localHttp, onClick = {
                                localHttp = !localHttp
                                resetApproval()
                            })
                            ShellButton(
                                Strings.aiProbe.next(),
                                enabled =
                                    endpoint.isNotBlank() && model.isNotBlank() &&
                                        (auth == "None" || token.isNotBlank()) &&
                                        (dialect != AiRequestDialect.AnthropicMessages || version.isNotBlank()),
                                onClick = {
                                    step = 2
                                    resetApproval()
                                },
                            )
                        }
                        if (step == 2 && packet != null) {
                            SelectionContainer {
                                BasicText(
                                    "$endpoint\n$model\n${packet.prompt}",
                                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                                    style = TextStyle(color = colors.contentText, fontSize = 11.sp),
                                )
                            }
                            ShellButton(Strings.aiProbe.consent(), accent = consent, enabled = !busy, onClick = { consent = !consent })
                            ShellButton(Strings.aiProbe.send(), enabled = consent && !busy, onClick = {
                                val approvedPacket = packet
                                val server =
                                    AiServerProfile(
                                        endpoint,
                                        if (localHttp) AiServerLocation.Local else AiServerLocation.Remote,
                                        AiRequestBodyProfile(dialect, model, 4096),
                                        anthropicVersion = version.takeIf { dialect == AiRequestDialect.AnthropicMessages },
                                        allowInsecureLocalHttp = localHttp,
                                    )
                                val approvedAuth = auth
                                val approvedToken = token
                                val log =
                                    aiProbeLocalLog(
                                        dialect,
                                        when (auth) {
                                            "Bearer" -> AiProbeAuthentication.Bearer
                                            "x-api-key" -> AiProbeAuthentication.ApiKey
                                            else -> AiProbeAuthentication.None
                                        },
                                    )
                                localLogging = log != null
                                busy = true
                                complete = false
                                answer = ""
                                message = null
                                diagnostics = listOf(AiProbeDiagnostic(AiProbeStage.Preparing))
                                job =
                                    scope.launch {
                                        try {
                                            val credentials =
                                                when (approvedAuth) {
                                                    "Bearer" -> headersOf(HttpHeaders.Authorization, "Bearer $approvedToken")
                                                    "x-api-key" -> headersOf("x-api-key", approvedToken)
                                                    else -> Headers.Empty
                                                }
                                            val result =
                                                runAiUnderstandingProbe(
                                                    server,
                                                    credentials,
                                                    approvedPacket.prompt,
                                                    approved = {
                                                        if (!open || !consent) {
                                                            false
                                                        } else {
                                                            try {
                                                                if (!withTimeout(15_000) { currentValidate() }) {
                                                                    throw AiProbePreflightException(AiProbeFailure.WorkspaceChanged)
                                                                }
                                                                true
                                                            } catch (_: TimeoutCancellationException) {
                                                                throw AiProbePreflightException(AiProbeFailure.WorkspaceBackend)
                                                            } catch (
                                                                cancelled: CancellationException,
                                                            ) {
                                                                throw cancelled
                                                            } catch (
                                                                failure: AiProbePreflightException,
                                                            ) {
                                                                throw failure
                                                            } catch (failure: Exception) {
                                                                throw AiProbePreflightException(
                                                                    AiProbeFailure.WorkspaceBackend,
                                                                    (failure as? BackendHttpException)?.status?.value,
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onText = { if (open) answer = it },
                                                    onDiagnostic = { event ->
                                                        log?.record(event)
                                                        if (open && diagnostics.lastOrNull() != event) {
                                                            diagnostics = (diagnostics + event).takeLast(24)
                                                        }
                                                    },
                                                )
                                            ensureActive()
                                            if (open) {
                                                answer = result
                                                complete = true
                                                message = Strings.aiProbe.complete()
                                            }
                                        } catch (cancelled: CancellationException) {
                                            if (open) {
                                                complete = false
                                                message = null
                                                if (diagnostics.lastOrNull()?.stage != AiProbeStage.Cancelled) {
                                                    diagnostics = (diagnostics + AiProbeDiagnostic(AiProbeStage.Cancelled)).takeLast(24)
                                                }
                                            }
                                            throw cancelled
                                        } catch (_: Exception) {
                                            if (open) {
                                                message = Strings.aiProbe.failed()
                                                if (diagnostics.lastOrNull()?.stage != AiProbeStage.Failed) {
                                                    diagnostics =
                                                        (
                                                            diagnostics +
                                                                AiProbeDiagnostic(
                                                                    AiProbeStage.Failed,
                                                                    AiProbeFailure.Configuration,
                                                                )
                                                        ).takeLast(24)
                                                }
                                            }
                                        }
                                        // Each attempt consumes consent, including preflight rejection.
                                        finally {
                                            consent = false
                                            busy = false
                                        }
                                    }
                            })
                        }
                        AiProbeField(Strings.aiProbe.response(), answer, {
                            if (!busy && it.encodeToByteArray().size <= 128 * 1024) {
                                answer = it
                                complete = true
                                message = null
                            }
                        }, enabled = !busy, multiline = true)
                        ShellButton(Strings.aiProbe.check(), enabled = packet != null && complete && !busy && answer.isNotBlank(), onClick = {
                            message =
                                try {
                                    val issues = checkAiCanvasAnswer(checkNotNull(packet), answer)
                                    if (issues.isEmpty()) Strings.aiProbe.passed() else Strings.aiProbe.mismatch(issues.joinToString(", "))
                                } catch (_: Exception) {
                                    Strings.aiProbe.invalid()
                                }
                        })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (step > 0) {
                                ShellButton(Strings.aiProbe.back(), enabled = !busy, onClick = {
                                    step--
                                    consent = false
                                })
                            }
                            ShellButton(Strings.common.cancel(), onClick = close)
                        }
                    }
                }
            }
        }
    }
}

internal fun aiProbeStageLabel(stage: AiProbeStage?): String =
    when (stage) {
        null -> Strings.aiProbe.debugIdle()
        AiProbeStage.Preparing -> Strings.aiProbe.debugPreparing()
        AiProbeStage.CheckingWorkspace -> Strings.aiProbe.debugWorkspace()
        AiProbeStage.Connecting -> Strings.aiProbe.debugConnecting()
        AiProbeStage.HttpResponse -> Strings.aiProbe.debugHttp()
        AiProbeStage.Receiving -> Strings.aiProbe.debugReceiving()
        AiProbeStage.Completed -> Strings.aiProbe.complete()
        AiProbeStage.Cancelled -> Strings.aiProbe.debugCancelled()
        AiProbeStage.Failed -> Strings.aiProbe.debugFailed()
    }

internal fun aiProbeDiagnosticHint(event: AiProbeDiagnostic): String =
    when (event.failure) {
        AiProbeFailure.Configuration, AiProbeFailure.Preparation -> {
            Strings.aiProbe.debugHintConfig()
        }

        AiProbeFailure.WorkspaceChanged, AiProbeFailure.WorkspaceScopeChanged,
        AiProbeFailure.WorkspaceVersionChanged, AiProbeFailure.WorkspaceSnapshotChanged,
        -> {
            Strings.aiProbe.debugHintWorkspace()
        }

        AiProbeFailure.WorkspaceBackend -> {
            Strings.aiProbe.debugHintBackend()
        }

        AiProbeFailure.HttpRejected -> {
            when (event.httpStatus) {
                401, 403 -> Strings.aiProbe.debugHintAuth()
                404 -> Strings.aiProbe.debugHintEndpoint()
                400, 422 -> Strings.aiProbe.debugHintRequest()
                429 -> Strings.aiProbe.debugHintLimit()
                in 500..599 -> Strings.aiProbe.debugHintServer()
                else -> Strings.aiProbe.failed()
            }
        }

        AiProbeFailure.Network, AiProbeFailure.Timeout -> {
            Strings.aiProbe.debugHintNetwork()
        }

        AiProbeFailure.ResponseFormat, AiProbeFailure.StreamFormat, AiProbeFailure.UnsupportedMode -> {
            Strings.aiProbe.debugHintFormat()
        }

        AiProbeFailure.Incomplete, AiProbeFailure.OutputLimit -> {
            Strings.aiProbe.debugHintIncomplete()
        }

        else -> {
            Strings.aiProbe.failed()
        }
    }

@Composable internal fun AiProbeField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
    enabled: Boolean = true,
    multiline: Boolean = false,
) {
    val colors = BoarderLessTheme.colors
    BasicText(label, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
    BasicTextField(
        value,
        onChange,
        enabled = enabled,
        singleLine = !multiline,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(color = colors.contentText, fontSize = 13.sp),
        cursorBrush = SolidColor(colors.accent),
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (multiline) Modifier.heightIn(min = 100.dp, max = 240.dp) else Modifier)
                .background(colors.contentSurface)
                .padding(10.dp),
    )
}
