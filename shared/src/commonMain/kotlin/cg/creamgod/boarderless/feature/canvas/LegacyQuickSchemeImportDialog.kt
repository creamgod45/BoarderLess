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
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/** Explicit legacy metadata copy. Opening performs no read, HTTP, asset materialization
 * or canvas publication. An attempted save is never replaced with another identity. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LegacyQuickSchemeImportDialog(
    reader: suspend () -> List<QuickScheme>,
    library: AwaitedQuickSchemeRepository,
    canContinue: () -> Boolean,
    onSaved: (QuickScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val live by rememberUpdatedState(canContinue)
    val savedCallback by rememberUpdatedState(onSaved)
    var open by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var items by remember { mutableStateOf<List<QuickScheme>?>(null) }
    var page by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<QuickScheme?>(null) }
    var requestId by remember { mutableStateOf(randomUuid()) }
    var confirmed by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val payload = remember(selected) { selected?.let(::decodeQuickSchemePayload) }
    val copy = remember(selected, payload) {
        val original = selected
        val definition = payload
        if (original == null || definition == null ||
            (definition.media.isNotEmpty() && original.sourceWorkspaceId == null)) null
        else runCatching { original.copy(id = 0, payload = ClipboardJson.encodeToString(definition),
            schemaVersion = definition.version) }.getOrNull()
    }
    fun valid() = open && live()
    fun close() { open = false; job?.cancel(); onDismiss() }
    fun run(action: suspend () -> Unit) {
        if (busy || !valid() || attempted) return
        busy = true
        status = Strings.cloudSchemes.working()
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) {
                if (valid()) status = if (attempted) Strings.schemes.legacyUnconfirmed() else Strings.cloudSchemes.cancelled()
                throw cancelled
            }
            catch (_: Exception) {
                if (valid()) status = if (attempted) Strings.schemes.legacyUnconfirmed() else Strings.schemes.legacyReadFailed()
            }
            finally { busy = false; job = null }
        }
    }
    DisposableEffect(Unit) { onDispose { open = false; job?.cancel() } }
    val enabled = valid() && !busy && !attempted
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding(), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 760.dp).fillMaxWidth(.94f).fillMaxHeight(.9f)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(Strings.schemes.legacyTitle(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.schemes.legacyWarning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    status?.let { BasicText(it, style = TextStyle(color = colors.contentText, fontSize = 12.sp)) }
                    if (attempted) BasicText(requestId, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.schemes.legacyLoad(), enabled = enabled, onClick = { run {
                            val loaded = reader(); check(valid())
                            items = loaded; page = 0; selected = null; confirmed = false
                            status = if (loaded.isEmpty()) Strings.schemes.legacyEmpty() else Strings.cloudSchemes.loaded()
                        } })
                        ShellButton(Strings.common.close(), onClick = ::close)
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val records = items.orEmpty()
                        val pages = ((records.size + 7) / 8).coerceAtLeast(1)
                        records.drop(page * 8).take(8).forEach { record ->
                            ShellButton("${record.id} · ${record.name}", enabled = enabled, onClick = {
                                selected = record; requestId = randomUuid(); confirmed = false; status = null
                            })
                        }
                        if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShellButton(Strings.cloudSchemes.previous(), enabled = enabled && page > 0, onClick = { page-- })
                            BasicText("${page + 1}/$pages", style = TextStyle(color = colors.contentMuted))
                            ShellButton(Strings.cloudSchemes.next(), enabled = enabled && page + 1 < pages, onClick = { page++ })
                        }
                        selected?.let { original ->
                            BasicText("${original.name}\n${original.sourceWorkspaceId ?: "—"}", style = TextStyle(color = colors.contentText))
                            payload?.let { QuickSchemePreview(it, Modifier.fillMaxWidth().height(160.dp)) }
                            BasicText(Strings.schemes.legacyOriginal(), style = TextStyle(color = colors.contentMuted))
                            CloudSchemePayloadReview(original.payload, enabled)
                            if (copy == null) BasicText(Strings.schemes.legacyUnsupported(), style = TextStyle(color = colors.contentMuted))
                            else {
                                BasicText(Strings.schemes.legacyCopy(), style = TextStyle(color = colors.contentMuted))
                                CloudSchemePayloadReview(copy.payload, enabled)
                            }
                        }
                    }
                    ShellButton(Strings.schemes.legacyConsent(), accent = confirmed,
                        enabled = enabled && copy != null && library.supportsIdentifiedSave,
                        onClick = { confirmed = !confirmed })
                    ShellButton(Strings.schemes.legacySave(),
                        enabled = enabled && confirmed && copy != null && library.supportsIdentifiedSave,
                        onClick = {
                            val original = selected ?: return@ShellButton
                            val prepared = copy ?: return@ShellButton
                            val identity = requestId
                            run {
                                // Re-read the indexed record; never silently copy a changed source.
                                val observed = reader().singleOrNull { it.id == original.id }
                                check(valid())
                                if (observed != original) {
                                    confirmed = false; status = Strings.schemes.legacyChanged()
                                    return@run
                                }
                                // From here failure/cancellation is unconfirmed, including staging.
                                attempted = true
                                library.stageIdentifiedSave(QuickSchemeSaveIntent(identity, prepared))
                                check(valid())
                                val saved = library.saveIdentified(identity, prepared.payload, prepared.name,
                                    prepared.schemaVersion, prepared.sourceWorkspaceId)
                                check(valid())
                                status = Strings.schemes.legacySaved()
                                savedCallback(saved)
                            }
                        })
                }
            }
        }
    }
}
