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
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout

/** No network on composition/startup. Connecting, binding and refreshing the downloaded
 * baseline are explicit actions. Reviewed merges apply locally only; no server writes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LocalWorkspaceSyncDialog(
    repository: CanvasWorkspaceRepository,
    owner: WorkspaceSession,
    serverFactory: () -> CanvasWorkspaceRepository,
    backupRuntime: DraftBackupRuntime,
    canContinue: () -> Boolean,
    onApply: suspend (LocalWorkspaceSyncSnapshot, cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation, PreparedSubmissionVerification?, VerifiedMaterializedTarget?) -> LocalSyncApplyResult,
    onDismiss: () -> Unit,
) {
    val store = requireNotNull(repository.localSyncStore)
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val currentScope by rememberUpdatedState(canContinue)
    var local by remember(repository, owner.workspace.id) { mutableStateOf<LocalWorkspaceSyncSnapshot?>(null) }
    var server by remember { mutableStateOf<CanvasWorkspaceRepository?>(null) }
    var remote by remember { mutableStateOf<WorkspaceSession?>(null) }
    var spaces by remember { mutableStateOf<List<WorkspaceSummary>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var reviewOpen by remember { mutableStateOf(false) }
    var mergeOpen by remember { mutableStateOf(false) }
    var rebaseContext by remember { mutableStateOf<LocalSyncRebaseContext?>(null) }
    var historicalRemote by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var activeJob by remember { mutableStateOf<Job?>(null) }
    var evidenceConsent by remember(local) { mutableStateOf(false) }
    var uploadConsent by remember(local?.outgoing?.prepared, remote, historicalRemote) { mutableStateOf(false) }
    var fenceConsent by remember(local?.outgoing?.prepared, remote, historicalRemote) { mutableStateOf(false) }
    var pendingApply by remember(local?.proposal?.id, local?.local?.workspace, local?.materializations) {
        mutableStateOf<cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation?>(null)
    }
    var pendingRebaseApply by remember(local?.rebaseProposal?.id, local?.local?.workspace, local?.materializations) {
        mutableStateOf<cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation?>(null)
    }
    val remoteRepository = server
    DisposableEffect(remoteRepository) { onDispose { remoteRepository?.close() } }
    LaunchedEffect(repository, owner.workspace.id) {
        try {
            val snapshot = store.readSyncSnapshot(owner)
            if (currentScope()) {
                local = snapshot
                snapshot.proposal?.let { remote = it.remoteSession(); historicalRemote = true }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Throwable) { if (currentScope()) message = Strings.localSync.localFailed() }
    }

    fun perform(timed: Boolean = true, action: suspend () -> Unit) {
        if (busy || !currentScope()) return
        busy = true; message = null
        activeJob = scope.launch {
            try { if (timed) withTimeout(20_000) { action() } else action() }
            catch (rebase: LocalSyncRebaseRequired) {
                if (currentScope()) {
                    local = rebase.snapshot
                    remote = rebase.verification.current
                    rebaseContext = localSyncRebaseContext(rebase.snapshot, rebase.verification)
                    message = Strings.localSync.rebaseRequired()
                }
            }
            catch (blocked: LocalSyncUploadBlocked) { if (currentScope()) message = Strings.localSync.uploadUnsupported(blocked.gaps.joinToString { it.name }) }
            catch (receipt: LocalSyncReceiptUnavailable) { if (currentScope()) message = Strings.localSync.originalReceiptStatus(receipt.status.name) }
            catch (_: TimeoutCancellationException) { if (currentScope()) message = Strings.localSync.failed() }
            catch (cancelled: CancellationException) { if (currentScope()) message = Strings.localSync.materializationCancelled(); throw cancelled }
            catch (_: Throwable) { if (currentScope()) message = Strings.localSync.failed() }
            finally { busy = false; activeJob = null }
        }
    }

    suspend fun loadRemote(selected: WorkspaceSession) {
        val adapter = checkNotNull(server)
        val refreshed = adapter.refresh(selected)
        val snapshot = store.readSyncSnapshot(owner)
        val pending = adapter.pendingChange(refreshed)
        val draft = adapter.pendingDraft(refreshed)
        val original = snapshot.outgoing
        val identity = original?.prepared ?: if (original != null) adapter.preparedSubmission(refreshed) else null
        if (original != null && identity != null) {
            original.copy(prepared = identity).requireProposal(requireNotNull(snapshot.proposal))
            require(pending == null || pending.transactionId == identity.transactionId)
            if (draft != null && adapter.preparedSubmission(refreshed) != identity) {
                // A crash after adapter stop may leave the exact quarantined draft while
                // its wire request is archived. Allow inspection/recovery, never submission.
                val saved = adapter.reviewPendingDraft(refreshed, draft.id)
                require(saved.operations == listOf(original.operation))
            }
        } else require(pending == null && draft == null) {
            "Server submission requires reconciliation before pairing"
        }
        check(currentScope())
        snapshot.checkpoint?.let {
            require(adapter.syncServerScope == it.binding.serverScope)
            it.binding.requireRemote(refreshed)
        }
        val summaries = adapter.listWorkspaces(refreshed)
        check(currentScope())
        local = snapshot; remote = refreshed; spaces = summaries; historicalRemote = false
    }

    val snapshot = local
    val selected = remote
    val review = if (snapshot != null && selected != null) WorkspaceDraftReview(
        draftId = "sync-destination:${snapshot.local.workspace.id.value}:${selected.workspace.id.value}",
        baseVersion = snapshot.checkpoint?.serverVersion ?: selected.workspaceVersion,
        baseServerSeq = snapshot.checkpoint?.serverSeq ?: selected.lastServerSeq,
        currentVersion = selected.workspaceVersion, currentServerSeq = selected.lastServerSeq,
        baseline = snapshot.checkpoint?.baseline ?: selected.workspace.copy(id = snapshot.local.workspace.id),
        proposed = snapshot.local.workspace, current = selected.workspace.copy(id = snapshot.local.workspace.id),
        operations = emptyList(), quarantined = false, hasUnconfirmedSubmission = false,
    ) else null

    val rebasing = rebaseContext
    if (rebasing != null && snapshot != null) {
        LocalSyncRebaseDialog(snapshot, rebasing, currentScope, onStage = { reviewed ->
            val staged = stageLocalSyncRebase(repository, owner, requireNotNull(server), requireNotNull(remote), reviewed, currentScope)
            if (currentScope()) { local = staged; rebaseContext = null; message = Strings.localSync.rebaseSaved() }
        }, onDismiss = { rebaseContext = null })
        return
    }

    if (mergeOpen && snapshot?.checkpoint != null && selected != null) {
        LocalSyncMergeDialog(snapshot, selected, currentScope, onStage = { proposal ->
            check(currentScope())
            val staged = stageLocalSyncReview(repository, snapshot, requireNotNull(server), proposal, currentScope)
            if (currentScope()) { local = staged; mergeOpen = false; message = Strings.localSync.proposalSaved() }
        }, onDismiss = { mergeOpen = false })
        return
    }
    if (reviewOpen && review != null) {
        WorkspaceDraftReviewDialog(review, busy, null, null, null, false, false,
            includeRemoteChanges = true,
            onDismiss = { reviewOpen = false })
        return
    }
    Dialog(onDismissRequest = { if (!applying) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.Center) {
            GlassSurface(Modifier.widthIn(max = 880.dp).fillMaxWidth(.94f).heightIn(max = 720.dp).fillMaxHeight(.85f)) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BasicText(Strings.localSync.title(), style = TextStyle(color = colors.contentText, fontSize = 18.sp))
                    BasicText(Strings.localSync.hint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                    message?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
                    if (snapshot != null) key(snapshot.proposal?.id, snapshot.rebaseProposal?.id, snapshot.local.workspace, selected) {
                        LocalSyncMaterializationPanel(repository, snapshot, server, selected, historicalRemote, busy, currentScope,
                            onBusy = { busy = it }, onUpdated = { local = it })
                        LocalAssetUploadPanel(repository, snapshot, server, historicalRemote, busy, currentScope,
                            onBusy = { busy = it }, onUpdated = { local = it })
                    }
                    if (snapshot != null && backupRuntime.chooseDestination != null) {
                        if (!evidenceConsent) ShellButton(Strings.localSync.reviewEvidenceExport(), enabled = !busy,
                            onClick = { evidenceConsent = true })
                        else {
                            BasicText(Strings.localSync.evidenceExportWarning(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                            ShellButton(Strings.localSync.confirmEvidenceExport(), enabled = !busy, onClick = { perform(timed = false) {
                                val result = exportLocalSyncEvidence(backupRuntime, repository, snapshot, currentScope)
                                if (currentScope()) message = when (result) {
                                    DraftBackupResult.Saved -> Strings.draftReview.saved()
                                    DraftBackupResult.DownloadRequested -> Strings.draftReview.downloadRequested()
                                    DraftBackupResult.Cancelled -> Strings.draftReview.saveCancelled()
                                }
                                evidenceConsent = false
                            } })
                            ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { evidenceConsent = false })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShellButton(Strings.localSync.connect(), enabled = snapshot != null && !busy, onClick = {
                            perform {
                                val adapter = server ?: serverFactory().also { server = it }
                                snapshot?.checkpoint?.let { require(adapter.syncServerScope == it.binding.serverScope) }
                                require(adapter.syncServerScope != null)
                                val opened = adapter.openOrCreateWorkspace(snapshot?.checkpoint?.binding?.workspaceId)
                                loadRemote(opened)
                            }
                        })
                        ShellButton(Strings.common.close(), enabled = !applying, onClick = onDismiss)
                    }
                    if (selected != null && snapshot != null) {
                        if (historicalRemote) BasicText(Strings.localSync.historicalRemote(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                        if (snapshot.stopped.isNotEmpty()) BasicText(Strings.localSync.stoppedArchiveCount(snapshot.stopped.size.toString()),
                            style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        snapshot.rebaseProposal?.let { reviewed ->
                            BasicText(if (snapshot.rebaseIsCurrent) Strings.localSync.rebaseSaved() else Strings.localSync.rebaseStale(),
                                style = TextStyle(color = colors.accent, fontSize = 12.sp))
                            ShellButton(Strings.localSync.reviewRebase(), enabled = !busy,
                                onClick = { rebaseContext = reviewed.context })
                            if (snapshot.rebaseIsCurrent) {
                                val ready = pendingRebaseApply
                                if (ready == null) ShellButton(Strings.localSync.prepareRebaseApply(), enabled = !busy && server != null && !historicalRemote,
                                    onClick = { perform { pendingRebaseApply = cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation(
                                        cg.creamgod.boarderless.data.remote.randomUuid(), snapshot.local.workspace, reviewedLocalApplyTarget(snapshot)) } })
                                else {
                                    BasicText(Strings.localSync.rebaseApplyHint(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                                    if (applying && snapshot.materializations.isNotEmpty()) {
                                        BasicText(Strings.localSync.materializationVerifying(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                                        ShellButton(Strings.localSync.cancelMaterialization(), onClick = { activeJob?.cancel() })
                                    }
                                    review?.let { WorkspaceComparisonPreview(it, ready.after) }
                                    ShellButton(Strings.localSync.confirmRebaseApply(), enabled = !busy && server != null && !historicalRemote, accent = true,
                                        onClick = { perform(timed = snapshot.materializations.isEmpty()) {
                                            applying = true
                                            try {
                                                val (fresh, verified) = verifyLocalSyncRebaseApply(repository, owner, requireNotNull(server), selected, reviewed, currentScope)
                                                val materialized = if (fresh.materializations.isEmpty()) null else withWorkspaceAssetDownloadGateway(requireNotNull(server)) { gateway ->
                                                    verifyMaterializedRebaseTargetOrRecovery(repository, fresh, ready, requireNotNull(server), selected, gateway) { currentScope() }
                                                }
                                                check(currentScope())
                                                val result = onApply(fresh, ready, verified, materialized)
                                                if (currentScope()) { local = result.snapshot; message = Strings.localSync.rebaseApplied() }
                                            } finally { applying = false }
                                        } })
                                    ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { pendingRebaseApply = null })
                                }
                            }
                        }
                        BasicText(Strings.localSync.destination(selected.workspace.title), style = TextStyle(color = colors.contentText, fontSize = 14.sp))
                        BasicText(Strings.localSync.identity(selected.userId, selected.workspace.id.value), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                        if (snapshot.checkpoint == null) {
                            spaces.forEach { item -> ShellButton(item.title.take(80), accent = item.id == selected.workspace.id,
                                enabled = !busy, onClick = { perform { loadRemote(checkNotNull(server).openWorkspace(selected, item.id)) } }) }
                        }
                        if (review != null) {
                            WorkspaceComparisonPreview(review)
                            ShellButton(Strings.localSync.differences(), enabled = !busy, onClick = { reviewOpen = true })
                        }
                        if (snapshot.checkpoint == null) {
                            BasicText(Strings.localSync.bindWarning(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            ShellButton(Strings.localSync.bind(), enabled = !busy, onClick = {
                                perform {
                                    val adapter = checkNotNull(server)
                                    val fresh = adapter.refresh(selected)
                                    check(fresh == selected) { "Destination changed; compare again" }
                                    check(currentScope())
                                    val bound = store.bindSyncDestination(snapshot.local,
                                        LocalWorkspaceSyncBinding(checkNotNull(adapter.syncServerScope), fresh.userId, fresh.workspace.id), fresh)
                                    if (currentScope()) { local = bound; message = Strings.localSync.bound() }
                                }
                            })
                        } else {
                            BasicText(Strings.localSync.bound(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                            if (snapshot.checkpoint.requiresInitialReview) BasicText(Strings.localSync.initialReview(),
                                style = TextStyle(color = colors.accent, fontSize = 12.sp))
                            ShellButton(Strings.localSync.reviewMerge(), enabled = !busy && server != null && !historicalRemote && snapshot.outgoing == null && snapshot.rebaseProposal == null, onClick = { mergeOpen = true })
                            snapshot.proposal?.let {
                                if (!snapshot.requiresAssetIdentityReview && (snapshot.appliedIsCurrent || snapshot.outgoing != null) && !snapshot.proposalIsSettled && !sameSyncContent(it.target, it.remote) && snapshot.outgoing?.prepared == null) {
                                    ShellButton(Strings.localSync.prepareUpload(), enabled = !busy && server != null && !historicalRemote,
                                        onClick = { perform {
                                            val prepared = prepareLocalSyncUpload(repository, owner, requireNotNull(server), selected, currentScope)
                                            if (currentScope()) { local = prepared; message = Strings.localSync.uploadPrepared() }
                                        } })
                                }
                                snapshot.outgoing?.takeIf { it.prepared != null }?.let { outgoing ->
                                    BasicText(Strings.localSync.uploadPrepared(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                                    ShellButton(Strings.localSync.verifyUpload(), enabled = !busy && server != null && !historicalRemote,
                                        onClick = { perform {
                                            val settled = verifyAndSettleLocalSyncUpload(repository, owner, requireNotNull(server), selected, currentScope)
                                            if (currentScope()) { local = settled; message = Strings.localSync.uploadSettled() }
                                        } })
                                    if (!uploadConsent) ShellButton(Strings.localSync.reviewUpload(), enabled = !busy && server != null && !historicalRemote,
                                        onClick = { uploadConsent = true; fenceConsent = false })
                                    else {
                                        BasicText(Strings.localSync.uploadConsent(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                                        WorkspaceComparisonPreview(requireNotNull(review), it.target)
                                        ShellButton(Strings.localSync.confirmUpload(), enabled = !busy && server != null && !historicalRemote, accent = true,
                                            onClick = { perform {
                                                uploadConsent = false
                                                val settled = sendLocalSyncUpload(repository, owner, requireNotNull(server), selected, outgoing, currentScope)
                                                if (currentScope()) { local = settled; message = Strings.localSync.uploadSettled() }
                                            } })
                                        ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { uploadConsent = false })
                                    }
                                    if (!fenceConsent) ShellButton(Strings.localSync.reviewFence(), enabled = !busy && server != null && !historicalRemote,
                                        onClick = { fenceConsent = true; uploadConsent = false })
                                    else {
                                        BasicText(Strings.localSync.fenceConsent(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                                        ShellButton(Strings.localSync.confirmFence(), enabled = !busy && server != null && !historicalRemote,
                                            onClick = { perform {
                                                fenceConsent = false; uploadConsent = false
                                                val stopped = fenceLocalSyncUpload(repository, owner, requireNotNull(server), selected, outgoing, currentScope)
                                                if (currentScope()) {
                                                    local = stopped
                                                    loadRemote(selected)
                                                    message = if (stopped.proposalIsSettled) Strings.localSync.uploadSettled() else Strings.localSync.fencedArchived()
                                                }
                                            } })
                                        ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { fenceConsent = false })
                                    }
                                }
                                if (snapshot.applied != null && !snapshot.proposalIsSettled && sameSyncContent(it.target, it.remote)) {
                                    ShellButton(Strings.localSync.settleReadOnly(), enabled = !busy && server != null && !historicalRemote,
                                        onClick = {
                                            perform {
                                                val adapter = requireNotNull(server)
                                                require(adapter.syncServerScope == it.binding.serverScope)
                                                val fresh = adapter.refresh(selected)
                                                require(adapter.pendingChange(fresh) == null && adapter.pendingDraft(fresh) == null)
                                                check(currentScope())
                                                val settled = store.settleReadOnlySyncProposal(snapshot.local, it.id, fresh)
                                                if (currentScope()) { local = settled; remote = fresh; message = Strings.localSync.settled() }
                                            }
                                        })
                                }
                                BasicText(if (snapshot.proposalIsSettled) {
                                    if (snapshot.applied?.committed != null) Strings.localSync.uploadSettled() else Strings.localSync.settled()
                                } else if (snapshot.appliedIsCurrent) Strings.localSync.appliedLocal()
                                    else if (snapshot.applied != null) Strings.localSync.appliedChanged()
                                    else if (snapshot.proposalIsCurrent && it.remoteSession() == selected) Strings.localSync.proposalReopened()
                                    else Strings.localSync.proposalStale(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                                if (snapshot.proposalIsCurrent && !snapshot.requiresAssetIdentityReview && snapshot.applied == null) {
                                    val ready = pendingApply
                                    if (ready == null) {
                                        ShellButton(Strings.localSync.prepareApply(), enabled = !busy, onClick = {
                                            perform {
                                                pendingApply = cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation(
                                                    cg.creamgod.boarderless.data.remote.randomUuid(), snapshot.local.workspace, reviewedLocalApplyTarget(snapshot))
                                            }
                                        })
                                    } else {
                                        BasicText(Strings.localSync.applyHint(), style = TextStyle(color = colors.accent, fontSize = 12.sp))
                                        if (applying && snapshot.materializations.isNotEmpty()) {
                                            BasicText(Strings.localSync.materializationVerifying(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                                            ShellButton(Strings.localSync.cancelMaterialization(), onClick = { activeJob?.cancel() })
                                        }
                                        WorkspaceComparisonPreview(requireNotNull(review), ready.after)
                                        ShellButton(Strings.localSync.confirmApply(), enabled = !busy, accent = true, onClick = {
                                            perform(timed = snapshot.materializations.isEmpty()) {
                                                applying = true
                                                try {
                                                    val materialized = if (snapshot.materializations.isEmpty()) null else {
                                                        val adapter = requireNotNull(server)
                                                        withWorkspaceAssetDownloadGateway(adapter) { gateway ->
                                                            verifyMaterializedApplyOrRecovery(repository, snapshot, ready, adapter, selected, gateway) { currentScope() }
                                                        }
                                                    }
                                                    check(currentScope())
                                                    val result = onApply(snapshot, ready, null, materialized)
                                                    if (currentScope()) { local = result.snapshot; message = Strings.localSync.appliedLocal() }
                                                } finally { applying = false }
                                            }
                                        })
                                        ShellButton(Strings.common.cancel(), enabled = !busy, onClick = { pendingApply = null })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
