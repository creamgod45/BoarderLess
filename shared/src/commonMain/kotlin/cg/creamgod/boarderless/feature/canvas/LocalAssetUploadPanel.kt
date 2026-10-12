package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Upload approval is separate from canvas publication. Stored identities survive dismiss;
 * tickets remain ephemeral. No automatic polling, retry, completion or new reservation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LocalAssetUploadPanel(repository: CanvasWorkspaceRepository,
    snapshot: LocalWorkspaceSyncSnapshot, server: CanvasWorkspaceRepository?, historical: Boolean,
    parentBusy: Boolean, canContinue: () -> Boolean, onBusy: (Boolean) -> Unit,
    onUpdated: (LocalWorkspaceSyncSnapshot) -> Unit) {
    val store = repository.localSyncStore ?: return
    if (snapshot.checkpoint == null) return
    val ids = snapshot.local.workspace.objects.values.filterIsInstance<MediaNode>()
        .flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.distinct()
        .filter { id -> snapshot.materializations.none { it.localAssetId == id } }
    if (ids.isEmpty() && snapshot.assetUploadPreparations.isEmpty()) return
    val colors = BoarderLessTheme.colors
    val scope = rememberCoroutineScope()
    val currentScope by rememberUpdatedState(canContinue)
    var job by remember { mutableStateOf<Job?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var reservation by remember { mutableStateOf<LocalAssetUploadPreparation?>(null) }
    var ticketIdentity by remember { mutableStateOf<String?>(null) }
    var replacementTicketIdentity by remember { mutableStateOf<String?>(null) }
    var ticket by remember { mutableStateOf<PreparedAssetUploadTicket?>(null) }
    var consent by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf<String?>(null) }
    var observedReceipt by remember { mutableStateOf<AssetPreparationReceipt?>(null) }
    var publication by remember { mutableStateOf<VerifiedUploadedAssetMaterialization?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(0) }
    val candidates = (ids + snapshot.assetUploadPreparations.map { it.request.source.id }).distinct().sorted()
    val pages = (candidates.size + 19) / 20
    val enabled = !parentBusy && !historical && server?.syncAssetPreparationGatewayFactory != null
    val saved = snapshot.assetUploadPreparations.singleOrNull { it.request.source.id == chosen }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    fun run(step: String, action: suspend (BackendAssetPreparationGateway, LocalAssetUploadCoordinator) -> Unit) {
        if (!enabled || job?.isActive == true || !currentScope()) return
        consent = null; onBusy(true); status = Strings.localSync.uploadWorking(step)
        if (step == "lookup") { phase = null; observedReceipt = null }
        job = scope.launch {
            var gateway: BackendAssetPreparationGateway? = null
            try {
                gateway = requireNotNull(server?.syncAssetPreparationGatewayFactory).invoke()
                require(gateway.serverScope == snapshot.checkpoint.binding.serverScope)
                action(gateway, LocalAssetUploadCoordinator(store, gateway))
                if (currentScope()) onUpdated(store.readSyncSnapshot(snapshot.local))
            } catch (cancelled: CancellationException) {
                if (currentScope()) status = Strings.localSync.uploadCancelled()
                throw cancelled
            } catch (failure: Throwable) {
                if (currentScope()) {
                    // A failed response is not a valid metadata review or Ready proof.
                    observedReceipt = null
                    if (failure is AssetPreparationMetadataChangedException ||
                        failure is AssetPreparationHttpException && failure.httpStatus in setOf(401, 403, 404)) {
                        phase = null; ticket = null
                    }
                    status = when {
                        failure is AssetPreparationMetadataChangedException -> Strings.localSync.uploadMetadataChanged()
                        failure !is AssetPreparationHttpException -> Strings.localSync.uploadFailed()
                        failure.httpStatus == 401 -> Strings.localSync.uploadAuthenticationRequired()
                        failure.httpStatus == 403 -> Strings.localSync.uploadPermissionDenied()
                        failure.code == AssetPreparationFailureCode.LeaseExpired -> Strings.localSync.uploadLeaseExpired()
                        failure.code == AssetPreparationFailureCode.LeaseSuperseded -> Strings.localSync.uploadLeaseSuperseded()
                        failure.code == AssetPreparationFailureCode.UploadMissing -> Strings.localSync.uploadOriginalMissing()
                        failure.httpStatus == 404 -> Strings.localSync.uploadRequestNotFound()
                        else -> Strings.localSync.uploadHttpFailure(failure.httpStatus.toString())
                    }
                    // Read back after unknown local ACK, without repeating network writes.
                    runCatching { store.readSyncSnapshot(snapshot.local) }.getOrNull()?.let(onUpdated)
                }
            } finally { gateway?.close(); onBusy(false); job = null }
        }
    }
    fun select(id: String) {
        chosen = id; reservation = null; ticketIdentity = null; replacementTicketIdentity = null
        ticket = null; consent = null; phase = null; observedReceipt = null; publication = null; status = null
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicText(Strings.localSync.assetUploadTitle(), style = TextStyle(color = colors.contentText, fontSize = 15.sp))
        BasicText(Strings.localSync.assetUploadHint(), style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
        if (snapshot.requiresAssetIdentityReview) BasicText(Strings.localSync.uploadMappingSaved(),
            style = TextStyle(color = colors.accent, fontSize = 12.sp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            candidates.drop(page * 20).take(20).forEach { id ->
                ShellButton(id.take(16), compact = true, enabled = !parentBusy, accent = id == chosen, onClick = { select(id) })
            }
        }
        if (pages > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ShellButton(Strings.localSync.previousMaterializationPage(), compact = true, enabled = !parentBusy && page > 0, onClick = { page-- })
            ShellButton(Strings.localSync.nextMaterializationPage(), compact = true, enabled = !parentBusy && page + 1 < pages, onClick = { page++ })
        }
        if (chosen != null) {
            BasicText(chosen!!, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
            if (saved == null) {
                ShellButton(Strings.localSync.inspectMaterialization(chosen!!), enabled = enabled, onClick = { run("inspect") { _, _ ->
                    val fresh = store.readSyncSnapshot(snapshot.local); require(fresh == snapshot)
                    val asset = repository.getAsset(snapshot.local, requireNotNull(chosen))
                    reservation = LocalAssetUploadPreparation(prepareLocalAssetUploadRequest(snapshot, asset, randomUuid()))
                    status = Strings.localSync.materializationInspected()
                } })
                if (reservation != null) ShellButton(Strings.localSync.uploadReserve(), enabled = enabled,
                    onClick = { consent = "reserve" })
            } else {
                BasicText(Strings.localSync.uploadIdentity(saved.request.requestId, saved.remoteIdentity?.id ?: "—"),
                    style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                BasicText(Strings.localSync.uploadDeclaredMeasurements(saved.request.source.measurementLabel()),
                    style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                val readyReview = observedReceipt?.takeIf {
                    it.requestId == saved.request.requestId && it.requestDigest == saved.request.requestDigest &&
                        it.identity == saved.remoteIdentity && it.phase == "ready"
                }?.readyIdentity
                readyReview?.let { measured ->
                    BasicText(Strings.localSync.uploadVerifiedMeasurements(measured.measurementLabel()),
                        style = TextStyle(color = colors.contentText, fontSize = 12.sp))
                    BasicText(Strings.localSync.uploadMeasurementReviewHint(),
                        style = TextStyle(color = colors.contentMuted, fontSize = 12.sp))
                }
                ShellButton(Strings.localSync.uploadLookup(), enabled = enabled, onClick = { run("lookup") { _, coordinator ->
                    val receipt = coordinator.lookup(snapshot, saved.request.requestId)
                    phase = receipt.phase; observedReceipt = receipt; status = receipt.uploadStatusLabel()
                } })
                if (saved.remoteIdentity == null) ShellButton(Strings.localSync.uploadPrepare(), enabled = enabled,
                    onClick = { consent = "prepare" })
                if (saved.remoteIdentity != null && phase == "prepared") {
                    ShellButton(Strings.localSync.uploadTicket(), enabled = enabled, onClick = { consent = "ticket" })
                    if (saved.ticketRequestIds.isNotEmpty()) ShellButton(Strings.localSync.uploadRenewTicket(), enabled = enabled,
                        onClick = {
                            if (replacementTicketIdentity == null || replacementTicketIdentity == saved.ticketRequestIds.lastOrNull())
                                replacementTicketIdentity = randomUuid()
                            consent = "renew"
                        })
                    if (ticket != null) ShellButton(Strings.localSync.uploadBytes(), enabled = enabled, onClick = { consent = "upload" })
                    if (saved.leases.isNotEmpty()) ShellButton(Strings.localSync.uploadComplete(), enabled = enabled,
                        onClick = { consent = "complete" })
                }
                if (phase == "ready" && readyReview != null && snapshot.materializations.none { it.localAssetId == saved.request.source.id })
                    ShellButton(Strings.localSync.uploadPublishMapping(), enabled = enabled && snapshot.outgoing == null && snapshot.rebaseProposal == null,
                        onClick = { consent = "publish" })
            }
        }
        consent?.let { step ->
            BasicText(Strings.localSync.uploadConsent(step), style = TextStyle(color = colors.accent, fontSize = 12.sp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ShellButton(Strings.localSync.uploadConfirm(), accent = true, enabled = enabled, onClick = { run(step) { preparationGateway, coordinator ->
                    when (step) {
                        "reserve" -> {
                            val candidate = requireNotNull(reservation)
                            store.stageAssetUploadPreparation(snapshot.local, null, candidate)
                            status = Strings.localSync.materializationSaved()
                        }
                        "prepare" -> {
                            val receipt = coordinator.prepare(snapshot, requireNotNull(saved).request.requestId)
                            phase = receipt.phase; observedReceipt = receipt; status = receipt.uploadStatusLabel()
                        }
                        "ticket", "renew" -> {
                            val current = requireNotNull(saved)
                            ticket = null
                            // An unknown save/signing ACK keeps the same ID for explicit retry.
                            val id = if (step == "renew") requireNotNull(replacementTicketIdentity)
                                else current.ticketRequestIds.lastOrNull() ?: ticketIdentity
                                    ?: randomUuid().also { ticketIdentity = it }
                            val staged = coordinator.reserveTicket(snapshot, current.request.requestId, id)
                            ticket = coordinator.issueTicket(staged, current.request.requestId)
                            status = Strings.localSync.uploadTicketSaved()
                        }
                        "upload" -> {
                            val current = requireNotNull(saved)
                            val remote = requireNotNull(server?.syncAssetGatewayFactory).invoke()
                            try {
                                withLocalOriginalUploadSource(repository, snapshot, current.request) { source ->
                                    coordinator.upload(snapshot, current.request.requestId, requireNotNull(ticket), remote,
                                        source, currentScope) { bytes -> if (currentScope()) status = Strings.localSync.uploadProgress(bytes.toString(), source.byteSize.toString()) }
                                }
                            } finally { remote.close() }
                            status = Strings.localSync.uploadBytesSent()
                        }
                        "complete" -> {
                            val receipt = coordinator.complete(snapshot, requireNotNull(saved).request.requestId)
                            phase = receipt.phase; observedReceipt = receipt; ticket = null; status = receipt.uploadStatusLabel()
                        }
                        "publish" -> {
                            val current = requireNotNull(saved)
                            val reviewed = requireNotNull(observedReceipt)
                            require(reviewed.requestId == current.request.requestId && reviewed.requestDigest == current.request.requestDigest &&
                                reviewed.identity == current.remoteIdentity && reviewed.phase == "ready")
                            val reviewedReady = requireNotNull(reviewed.readyIdentity)
                            val adapter = requireNotNull(server)
                            val savedProof = publication
                            val durable = store.readSyncSnapshot(snapshot.local)
                            val recovery = savedProof?.takeIf { it.mapping in durable.materializations }
                            val freshProof = recovery ?: verifyUploadedAssetMaterialization(repository, snapshot, adapter,
                                requireNotNull(snapshot.proposal).remoteSession(), preparationGateway, current.request.requestId, reviewedReady, currentScope)
                            require(freshProof.mapping.source == reviewedReady)
                            publication = freshProof
                            store.stageUploadedAssetMaterialization(freshProof)
                            ticket = null; status = Strings.localSync.uploadMappingSaved()
                        }
                    }
                } })
                ShellButton(Strings.common.cancel(), enabled = !parentBusy, onClick = { consent = null })
            }
        }
        status?.let { BasicText(it, style = TextStyle(color = colors.contentMuted, fontSize = 12.sp)) }
        if (job?.isActive == true) ShellButton(Strings.common.cancel(), onClick = { job?.cancel() })
    }
}

private fun AssetPreparationReceipt.uploadStatusLabel(): String =
    Strings.localSync.uploadPhase(phase) +
        (rejectionReason?.let { " · " + assetRejectionReasonLabel(it) } ?: "")

private fun MaterializationSourceDescriptor.measurementLabel(): String =
    "${width ?: "—"} × ${height ?: "—"} px · ${durationMs?.let { "$it ms" } ?: "—"}"
