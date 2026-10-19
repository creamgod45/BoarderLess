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
import cg.creamgod.boarderless.data.ai.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings

/** Connection values that are safe to keep on disk. The token is handled separately. */
internal data class AiConnectionSettings(
    val endpoint: String = "",
    val model: String = "",
    val dialect: AiRequestDialect = AiRequestDialect.OpenAiChat,
    val authentication: String = "Bearer",
    val anthropicVersion: String = "",
    val maxTokens: String = "",
    val temperature: String = "",
    val chatTokenLimitField: AiChatTokenLimitField = AiChatTokenLimitField.MaxCompletionTokens,
    val allowInsecureLocalHttp: Boolean = false,
    val traceToolCalls: Boolean = false,
) {
    fun toSaved() =
        AiSavedSettings(
            endpoint, model, dialect.name, authentication, anthropicVersion, maxTokens, temperature,
            chatTokenLimitField.name, allowInsecureLocalHttp, traceToolCalls,
        )

    val configured get() = endpoint.isNotBlank() && model.isNotBlank()
}

internal fun AiSavedSettings.toConnection() =
    AiConnectionSettings(
        endpoint = endpoint,
        model = model,
        dialect = AiRequestDialect.entries.firstOrNull { it.name == dialect } ?: AiRequestDialect.OpenAiChat,
        authentication = authentication.takeIf { it in listOf("Bearer", "x-api-key", "None") } ?: "Bearer",
        anthropicVersion = anthropicVersion,
        maxTokens = maxTokens,
        temperature = temperature,
        chatTokenLimitField = AiChatTokenLimitField.entries.firstOrNull { it.name == chatTokenLimitField }
            ?: AiChatTokenLimitField.MaxCompletionTokens,
        allowInsecureLocalHttp = allowInsecureLocalHttp,
        traceToolCalls = traceToolCalls,
    )

/** Result of saving: the token is non-null only when the user typed or kept one for this session. */
internal data class AiSettingsResult(
    val settings: AiConnectionSettings,
    val token: String?,
    val forgetToken: Boolean,
    val rememberToken: Boolean,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingsDialog(
    initial: AiConnectionSettings,
    hasStoredToken: Boolean,
    sessionHasToken: Boolean,
    tokenProtected: Boolean,
    problem: String?,
    onSave: (AiSettingsResult) -> Unit,
    onForgetToken: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    var settings by remember { mutableStateOf(initial) }
    var token by remember { mutableStateOf("") }
    var rememberToken by remember { mutableStateOf(tokenProtected) }
    val parameters = remember(settings.maxTokens, settings.temperature) {
        runCatching { parseAiRequestParameters(settings.maxTokens, settings.temperature) }
    }
    DisposableEffect(Unit) { onDispose { token = "" } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth(.94f).fillMaxHeight(.9f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(Strings.aiTools.settingsTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton("OpenAI-compatible", accent = settings.dialect == AiRequestDialect.OpenAiChat, onClick = {
                                settings = settings.copy(dialect = AiRequestDialect.OpenAiChat)
                            })
                            ShellButton("Claude-compatible", accent = settings.dialect == AiRequestDialect.AnthropicMessages, onClick = {
                                settings = settings.copy(dialect = AiRequestDialect.AnthropicMessages)
                            })
                        }
                        AiProbeField(Strings.aiProbe.endpoint(), settings.endpoint, { if (it.length <= 4096) settings = settings.copy(endpoint = it) })
                        AiProbeField(Strings.aiProbe.model(), settings.model, { if (it.length <= 256) settings = settings.copy(model = it) })
                        if (aiChatToolsUseNoReasoning(settings.dialect, settings.model)) {
                            BasicText(Strings.aiTools.chatReasoningNone(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        }
                        AiProbeField(Strings.aiProbe.maxTokens(), settings.maxTokens, { settings = settings.copy(maxTokens = it) })
                        if (settings.dialect == AiRequestDialect.OpenAiChat) {
                            AiTokenLimitFieldPicker(settings.chatTokenLimitField) { settings = settings.copy(chatTokenLimitField = it) }
                        }
                        AiProbeField(Strings.aiProbe.temperature(), settings.temperature, { settings = settings.copy(temperature = it) })
                        if (parameters.isFailure) BasicText(Strings.aiProbe.invalidParameters(), style = TextStyle(color = colors.danger))
                        if (settings.dialect == AiRequestDialect.AnthropicMessages) {
                            AiProbeField(Strings.aiProbe.version(), settings.anthropicVersion, {
                                if (it.length <= 32) settings = settings.copy(anthropicVersion = it)
                            })
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Bearer", "x-api-key", "None").forEach { mode ->
                                ShellButton(mode, accent = settings.authentication == mode, onClick = {
                                    settings = settings.copy(authentication = mode)
                                })
                            }
                        }
                        ShellButton(Strings.aiProbe.localHttp(), accent = settings.allowInsecureLocalHttp, onClick = {
                            settings = settings.copy(allowInsecureLocalHttp = !settings.allowInsecureLocalHttp)
                        })
                        ShellButton(Strings.aiTools.traceToolCalls(), accent = settings.traceToolCalls, onClick = {
                            settings = settings.copy(traceToolCalls = !settings.traceToolCalls)
                        })
                        BasicText(Strings.aiTools.traceHint(), style = TextStyle(color = colors.contentMuted, fontSize = 11.sp))
                        if (settings.authentication != "None") {
                            AiProbeField(Strings.aiProbe.token(), token, { if (it.length <= 8192) token = it }, secret = true)
                            BasicText(
                                if (tokenProtected) Strings.aiTools.tokenProtected() else Strings.aiTools.tokenUnprotected(),
                                style = TextStyle(color = colors.contentMuted, fontSize = 11.sp),
                            )
                            if (tokenProtected) {
                                ShellButton(Strings.aiTools.tokenRemember(), accent = rememberToken, onClick = { rememberToken = !rememberToken })
                            }
                            if (hasStoredToken) {
                                BasicText(Strings.aiTools.tokenStored(), style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                            }
                            if (hasStoredToken || sessionHasToken) {
                                ShellButton(Strings.aiTools.tokenForget(), onClick = onForgetToken)
                            }
                        }
                        problem?.let { BasicText(it, style = TextStyle(color = colors.danger, fontSize = 12.sp)) }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(
                            Strings.aiTools.settingsSave(),
                            accent = true,
                            enabled = parameters.isSuccess,
                            onClick = {
                                onSave(
                                    AiSettingsResult(
                                        settings.copy(endpoint = settings.endpoint.trim(), model = settings.model.trim()),
                                        token.takeIf { it.isNotBlank() },
                                        forgetToken = false,
                                        rememberToken = rememberToken && tokenProtected,
                                    ),
                                )
                                token = ""
                            },
                        )
                        ShellButton(Strings.aiTools.settingsClose(), onClick = onDismiss)
                    }
                }
            }
        }
    }
}
