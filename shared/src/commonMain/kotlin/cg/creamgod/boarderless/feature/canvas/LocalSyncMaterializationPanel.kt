@file:OptIn(kotlin.time.ExperimentalTime::class)
package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.randomUuid
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Two distinct approvals: reserve identity, then download/verify. No automatic Node apply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LocalSyncMaterializationPanel(repository: CanvasWorkspaceRepository,
    snapshot: LocalWorkspaceSyncSnapshot, server: CanvasWorkspaceRepository?, selected: WorkspaceSession?,
    historical: Boolean, parentBusy: Boolean, canContinue: () -> Boolean,
    onBusy: (Boolean) -> Unit, onUpdated: (LocalWorkspaceSyncSnapshot) -> Unit) {
    val review = runCatching { snapshot.materializationReview() }.getOrNull() ?: return
    val ownedIds = snapshot.local.workspace.objects.values.filterIsInstance<MediaNode>().flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.toSet()
    val candidates = review.first.objects.values.filterIsInstance<MediaNode>().flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.distinct().filter { it !in ownedIds }.sorted()
    if (candidates.isEmpty()) return
    val colors = BoarderLessTheme.colors; val scope = rememberCoroutineScope()
    val currentScope by rememberUpdatedState(canContinue)
    var job by remember { mutableStateOf<Job?>(null) }
    var sourceId by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<LocalAssetMaterialization?>(null) }
    var downloadConsent by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<MaterializationStage?>(null) }
    var page by remember(candidates) { mutableStateOf(0) }
    val pages = (candidates.size + 19) / 20
    val enabled = !parentBusy && !historical && server?.syncAssetGatewayFactory != null && selected != null
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    fun run(action: suspend () -> Unit) {
        if (parentBusy || job?.isActive == true || !currentScope()) return
        onBusy(true); message = null; progress = null
        job = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) {
                progress = null
                if (currentScope()) message = Strings.localSync.materializationCancelled()
                throw cancelled
            } catch (_: Throwable) { progress = null; if (currentScope()) message = Strings.localSync.materializationFailed() }
            finally { onBusy(false); job = null }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(Strings.localSync.materializationTitle(), style = TextStyle(color = colors.contentText, fontSize = 15.sp))
        BasicText(Strings.localSync.materializationHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        if (job?.isActive != true) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            candidates.drop(page * 20).take(20).forEach { id ->
                ShellButton(Strings.localSync.inspectMaterialization(id), compact = true, enabled = enabled, onClick = {
                    sourceId = id; prepared = null; downloadConsent = false
                    run {
                        val asset = inspectMaterializationSource(repository, snapshot, requireNotNull(server), requireNotNull(selected), id, currentScope)
                        val existing = snapshot.materializations.singleOrNull { it.source.id == id }
                        require(existing == null || existing.source == MaterializationSourceDescriptor.fromAsset(asset))
                        val proposal = existing ?: newLocalAssetMaterialization(snapshot, asset, randomUuid(), Clock.System.now().toString())
                        if (currentScope()) { prepared = proposal; message = Strings.localSync.materializationInspected() }
                    }
                })
            }
        }
        if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BasicText(Strings.localSync.materializationPages((page + 1).toString(), pages.toString()),
                style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            ShellButton(Strings.localSync.previousMaterializationPage(), compact = true, enabled = !parentBusy && page > 0, onClick = { page-- })
            ShellButton(Strings.localSync.nextMaterializationPage(), compact = true, enabled = !parentBusy && page + 1 < pages, onClick = { page++ })
        }
        val chosen = prepared
        if (chosen != null && sourceId == chosen.source.id) {
            BasicText(Strings.localSync.materializationMetadata(chosen.source.mediaType, chosen.source.byteSize.toString(), chosen.localAssetId),
                style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            val saved = snapshot.materializations.singleOrNull { it.source.id == chosen.source.id }
            if (saved == null) {
                BasicText(Strings.localSync.materializationSaveWarning(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                ShellButton(Strings.localSync.confirmMaterializationSave(), enabled = enabled, onClick = { run {
                    val result = saveReviewedMaterialization(repository, snapshot, requireNotNull(server), requireNotNull(selected), chosen, currentScope)
                    if (currentScope()) { onUpdated(result); message = Strings.localSync.materializationSaved() }
                } })
            } else {
                if (!downloadConsent) ShellButton(Strings.localSync.reviewMaterializationDownload(), enabled = enabled, onClick = { downloadConsent = true })
                else {
                    BasicText(Strings.localSync.materializationDownloadWarning(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.localSync.confirmMaterializationDownload(), enabled = enabled, accent = true, onClick = { run {
                            downloadConsent = false
                            withWorkspaceAssetDownloadGateway(requireNotNull(server)) { gateway ->
                                materializeRemoteAsset(repository, snapshot.local, server, requireNotNull(selected), gateway, saved, currentScope) {
                                    if (currentScope()) progress = it
                                }
                            }
                            if (currentScope()) message = Strings.localSync.materializationReady()
                        } })
                        ShellButton(Strings.common.cancel(), enabled = !parentBusy, onClick = { downloadConsent = false })
                    }
                }
            }
        }
        progress?.let { stage ->
            val status = when (stage) {
                MaterializationStage.Authorizing -> Strings.localSync.materializationAuthorizing()
                MaterializationStage.Recovering -> Strings.localSync.materializationRecovering()
                is MaterializationStage.Downloading -> Strings.localSync.materializationProgress(stage.bytes.toString(), stage.total.toString())
                MaterializationStage.Verifying -> Strings.localSync.materializationVerifying()
                is MaterializationStage.Ready -> Strings.localSync.materializationReady()
            }
            BasicText(status, style = TextStyle(color = colors.accent, fontSize = 12.sp))
        }
        message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
        if (job?.isActive == true) ShellButton(Strings.localSync.cancelMaterialization(), onClick = { job?.cancel() })
    }
}
