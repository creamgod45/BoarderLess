package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.data.persistence.QuickSchemeRepository
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.*

/** Save may have completed before an IO error. Re-read exact content before allocating a library ID. */
internal fun saveMaterializedBundle(library: QuickSchemeRepository, prepared: QuickScheme): QuickScheme =
    library.list().filter { it.name == prepared.name.trim() && it.payload == prepared.payload &&
        it.schemaVersion == prepared.schemaVersion && it.sourceWorkspaceId == prepared.sourceWorkspaceId }.minByOrNull { it.id }
        ?: library.save(prepared.payload, prepared.name, prepared.schemaVersion, prepared.sourceWorkspaceId)

internal fun schemeBundleScopeMatches(owner: WorkspaceSession, baseline: Workspace, live: PenCanvasLive): Boolean {
    val session = live.session ?: return false
    return live.ready && session.userId == owner.userId && session.clientId == owner.clientId && session.workspace.id == baseline.id &&
        session.workspaceVersion == owner.workspaceVersion && session.lastServerSeq == owner.lastServerSeq && live.workspace == baseline
}

@Composable internal fun SchemeBundleDialog(
    runtime: SchemeBundleRuntime,
    repository: CanvasWorkspaceRepository,
    library: QuickSchemeRepository,
    sourceOwner: WorkspaceSession,
    sourceBaseline: Workspace,
    requestedExportScheme: QuickScheme?,
    readLive: () -> PenCanvasLive,
    onSaved: (QuickScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val owner = remember { sourceOwner }
    val baseline = remember { sourceBaseline }
    val exportScheme = remember { requestedExportScheme }
    val scope = rememberCoroutineScope()
    val gateway = remember(repository) { repository.localAssetGateway ?: BackendAssetTransferGateway() }
    val latestRead by rememberUpdatedState(readLive)
    val latestSaved by rememberUpdatedState(onSaved)
    val latestDismiss by rememberUpdatedState(onDismiss)
    var reviewed by remember { mutableStateOf<ReviewedSchemeBundle?>(null) }
    var prepared by remember { mutableStateOf<QuickScheme?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    val selected = exportScheme ?: reviewed?.scheme
    val payload = remember(selected) { selected?.let(::decodeQuickSchemePayload) }
    val liveState = readLive()
    val current = schemeBundleScopeMatches(owner, baseline, liveState) && (exportScheme != null || liveState.session?.canEditContent == true)
    fun valid(): Boolean {
        val live = latestRead()
        return open && schemeBundleScopeMatches(owner, baseline, live) && (exportScheme != null || live.session?.canEditContent == true)
    }
    suspend fun fresh(): WorkspaceSession {
        check(valid())
        val session = repository.refresh(owner)
        currentCoroutineContext().ensureActive()
        check(valid())
        require(session.userId == owner.userId && session.clientId == owner.clientId && session.workspace.id == baseline.id &&
            session.workspaceVersion == owner.workspaceVersion && session.lastServerSeq == owner.lastServerSeq && session.workspace == baseline)
        return session
    }
    fun close() {
        open = false
        job?.cancel()
        latestDismiss()
    }
    DisposableEffect(Unit) {
        onDispose {
            open = false
            job?.cancel()
            val release = reviewed
            scope.launch(NonCancellable) { withTimeoutOrNull(5000) { release?.release() } }
            if (repository.localAssetGateway == null) gateway.close()
        }
    }
    Dialog(onDismissRequest = ::close) {
        GlassSurface(Modifier.widthIn(max = 780.dp).fillMaxWidth().heightIn(max = 800.dp)) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(if (exportScheme == null) Strings.bundle.importTitle() else Strings.bundle.exportTitle(),
                    style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                BasicText(if (repository.isLocalOnly) Strings.bundle.hint() else Strings.bundle.remoteHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                if (exportScheme == null) ShellButton(Strings.bundle.chooseFile(), enabled = !busy && prepared == null && current, onClick = {
                    busy = true
                    confirmed = false
                    problem = null
                    job = scope.launch {
                        var acquired: SchemeBundleSource? = null
                        var adopted = false
                        try {
                            val source = checkNotNull(runtime.chooseSource).invoke(::valid)
                            acquired = source
                            if (source != null) {
                                val result = QuickSchemeBundleCodec.review(source, ::valid)
                                if (!valid()) return@launch
                                withContext(NonCancellable) { reviewed?.release() }
                                reviewed = result
                                adopted = true
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { if (open) problem = Strings.schemes.transferFailed() }
                        finally {
                            if (!adopted) withContext(NonCancellable) { acquired?.release() }
                            if (open) busy = false
                        }
                    }
                })
                selected?.let {
                    BasicText(it.name, style = TextStyle(color = colors.contentText, fontSize = 14.sp))
                    payload?.let { definition -> QuickSchemePreview(definition, Modifier.fillMaxWidth().height(240.dp)) }
                    val count = reviewed?.assets?.size ?: payload?.media?.flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }?.distinct()?.size ?: 0
                    BasicText(Strings.bundle.assetCount(count), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    BasicText(Strings.bundle.destination(baseline.title), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    reviewed?.assets?.forEachIndexed { index, asset ->
                        BasicText(Strings.bundle.assetDetails(index + 1, asset.mediaType, asset.byteSize,
                            asset.width?.toString() ?: "?", asset.height?.toString() ?: "?"),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    }
                }
                val sourceAvailable = exportScheme == null || payload?.media?.isEmpty() == true || exportScheme.sourceWorkspaceId == baseline.id.value
                if (!sourceAvailable) BasicText(Strings.bundle.openSource(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                if (!current) BasicText(Strings.sequence.stale(), style = TextStyle(color = colors.danger, fontSize = 12.sp))
                if (busy) BasicText(Strings.bundle.working(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                problem?.let { BasicText(it, style = TextStyle(color = colors.danger, fontSize = 12.sp)) }
                ShellButton(if (confirmed) Strings.schemes.confirmed() else Strings.bundle.confirm(),
                    enabled = selected != null && current && sourceAvailable && !busy,
                    accent = confirmed, onClick = { confirmed = !confirmed })
                ShellButton(if (exportScheme == null) Strings.bundle.importSave() else Strings.bundle.exportSave(),
                    enabled = confirmed && selected != null && current && sourceAvailable && !busy &&
                        (exportScheme != null || (owner.canEditContent && runtime.receipts != null)), onClick = {
                        busy = true
                        problem = null
                        job = scope.launch {
                            try {
                                val authority = fresh()
                                if (exportScheme != null) {
                                    val destination = checkNotNull(runtime.chooseDestination).invoke("boarderless-scheme.blscheme", ::valid)
                                    if (destination != null) {
                                        var started = false
                                        try {
                                            fresh()
                                            started = true
                                            QuickSchemeBundleCodec.export(exportScheme, authority, gateway as AssetDownloadGateway,
                                                destination, ::valid, { library.list().singleOrNull { it.id == exportScheme.id } })
                                            if (valid()) { problem = Strings.bundle.exported(); confirmed = false }
                                        } finally {
                                            if (!started) withContext(NonCancellable) { destination.abort() }
                                        }
                                    }
                                } else {
                                    require(authority.canEditContent)
                                    val bundle = checkNotNull(reviewed)
                                    val digest = bundle.digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                                    val receipts = checkNotNull(runtime.receipts).invoke(authority, digest, bundle.assets)
                                    if (prepared == null) prepared = QuickSchemeBundleCodec.materialize(bundle, authority, gateway, ::valid, ::fresh,
                                        { sourceId, destinationId -> receipts.beforeComplete(bundle.assets.single { it.sourceId == sourceId }, destinationId) },
                                        { receipts.recovered(it, repository) },
                                        { sourceId, media -> receipts.ready(bundle.assets.single { it.sourceId == sourceId }, media) })
                                    fresh()
                                    check(valid())
                                    val saved = saveMaterializedBundle(library, checkNotNull(prepared))
                                    check(valid())
                                    latestSaved(saved)
                                    close()
                                }
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { if (open) problem = Strings.bundle.failed() }
                            finally { if (open) { confirmed = false; busy = false } }
                        }
                    })
                ShellButton(Strings.common.close(), onClick = ::close)
            }
        }
    }
}
