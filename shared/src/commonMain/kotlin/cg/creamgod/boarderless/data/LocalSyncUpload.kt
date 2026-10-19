package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.data.remote.randomUuid

internal enum class LocalSyncUploadGap { AtomicTitle, CanvasStyle, SequenceDiagrams, RelationGeometry, VectorPath, TextPlacement, ExternalMedia, ObjectIdReuse, RelationIdReuse, MediaMaterialization }
internal class LocalSyncUploadBlocked(val gaps: Set<LocalSyncUploadGap>) : IllegalStateException("Unsupported sync capabilities")
internal class LocalSyncReceiptUnavailable(val status: PendingReceiptStatus) : IllegalStateException("Original receipt is not committed")

/** Save a new review only against the exact freshly read head. Never sends/fences a request. */
internal suspend fun stageLocalSyncReview(local: CanvasWorkspaceRepository, snapshot: LocalWorkspaceSyncSnapshot,
    server: CanvasWorkspaceRepository, proposal: LocalWorkspaceSyncProposal,
    canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    check(canContinue())
    val checkpoint = requireNotNull(snapshot.checkpoint)
    require(snapshot.outgoing == null && snapshot.rebaseProposal == null)
    require(server.syncServerScope == checkpoint.binding.serverScope)
    checkpoint.binding.requireRemote(proposal.remoteSession())
    val fresh = server.refresh(proposal.remoteSession())
    check(canContinue())
    require(fresh == proposal.remoteSession() && fresh.role != WorkspaceMemberRole.Viewer)
    require(server.pendingChange(fresh) == null && server.pendingDraft(fresh) == null)
    check(canContinue())
    return requireNotNull(local.localSyncStore).stageSyncProposal(snapshot.local, checkpoint, snapshot.proposal?.id, proposal)
}

/** Irreversible server fence only after separate confirmation. A commit winning the race is
 * settled, never archived as cancelled. Stops only this request's local adapter draft; retains
 * original intent/wire identity in the local archive before permitting another review. */
internal suspend fun fenceLocalSyncUpload(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, expected: OutgoingLocalSyncTransaction,
    canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    check(canContinue())
    val store = requireNotNull(local.localSyncStore)
    val snapshot = store.readSyncSnapshot(owner)
    val archived = snapshot.stopped.singleOrNull { it.outgoing == expected }
    require(snapshot.outgoing == expected || archived != null)
    val proposal = snapshot.proposal ?: requireNotNull(archived).proposal
    require(server.syncServerScope == proposal.binding.serverScope)
    proposal.binding.requireRemote(selected)
    val prepared = requireNotNull(expected.prepared)
    var verified = server.verifyPreparedSubmission(selected, prepared, expected.operation)
    check(canContinue())
    if (verified.status == PendingReceiptStatus.Committed) {
        require(archived == null)
        return verifyAndSettleLocalSyncUpload(local, owner, server, verified.current, canContinue)
    }
    if (verified.status == PendingReceiptStatus.Unknown) {
        require(verified.current.canEditContent && server.preparedSubmission(verified.current) == prepared)
        check(canContinue())
        server.fencePendingChange(verified.current, prepared.transactionId)
        check(canContinue())
        verified = server.verifyPreparedSubmission(verified.current, prepared, expected.operation)
        check(canContinue())
        if (verified.status == PendingReceiptStatus.Committed) return verifyAndSettleLocalSyncUpload(local, owner, server, verified.current, canContinue)
    }
    if (verified.status != PendingReceiptStatus.Fenced) throw LocalSyncReceiptUnavailable(verified.status)
    var current = verified.current
    server.pendingChange(current)?.let {
        require(it.transactionId == prepared.transactionId)
        check(canContinue())
        current = server.dismissPendingChange(current, prepared.transactionId)
        check(canContinue())
    }
    server.pendingDraft(current)?.let { draft ->
        val review = server.reviewPendingDraft(current, draft.id)
        require(review.operations == listOf(expected.operation)) { "Another adapter draft must be preserved" }
        check(canContinue())
        server.dismissPendingDraft(current, draft.id)
        check(canContinue())
    }
    return store.archiveFencedSyncSubmission(snapshot.local, verified)
}

/** Explicit preparation only. Fresh read first, exact domain/wire publication second; no
 * canvas POST. Scope/cancellation gates are checked across every asynchronous boundary. */
internal suspend fun prepareLocalSyncUpload(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, canContinue: () -> Boolean,
    newId: () -> String = ::randomUuid): LocalWorkspaceSyncSnapshot {
    check(canContinue() && local.isLocalOnly)
    val store = requireNotNull(local.localSyncStore)
    var snapshot = store.readSyncSnapshot(owner)
    val proposal = requireNotNull(snapshot.proposal)
    check(!snapshot.proposalIsSettled)
    require(server.syncServerScope == proposal.binding.serverScope)
    proposal.binding.requireRemote(selected)
    snapshot.outgoing?.takeIf { it.prepared == null }?.let { outgoing ->
        server.preparedSubmission(selected)?.let { prepared ->
            check(canContinue())
            return store.bindSyncOutgoingSubmission(snapshot.local, outgoing, prepared)
        }
    }
    check(snapshot.appliedIsCurrent)
    require(!snapshot.requiresAssetIdentityReview) { "Uploaded identity requires a new canonical review" }
    val fresh = server.refresh(selected)
    check(canContinue())
    require(fresh == proposal.remoteSession() && fresh.canEditContent) { "Remote changed; review again" }
    val target = proposal.target.copy(id = fresh.workspace.id)
    if (target.title != fresh.workspace.title) throw LocalSyncUploadBlocked(setOf(LocalSyncUploadGap.AtomicTitle))
    val operation = snapshot.outgoing?.operation ?: requireNotNull(buildWorkspaceContentTransaction(fresh.workspace, target, newId))
    val contract = draftMergeContractGaps(fresh.workspace, operation)
    val gaps = buildSet {
        if (DraftMergeContractGap.CanvasStyle in contract && !server.supportsCanvasStyle) add(LocalSyncUploadGap.CanvasStyle)
        if (target.sequenceDiagrams.isNotEmpty() && !server.supportsSequenceDiagrams || DraftMergeContractGap.SequenceDiagrams in contract && !server.supportsSequenceDiagrams) add(LocalSyncUploadGap.SequenceDiagrams)
        if (DraftMergeContractGap.RelationGeometry in contract && !server.supportsRelationGeometry) add(LocalSyncUploadGap.RelationGeometry)
        if (DraftMergeContractGap.VectorPath in contract && !server.supportsVectorPaths) add(LocalSyncUploadGap.VectorPath)
        if (DraftMergeContractGap.TextPlacement in contract && !server.supportsTextPlacement) add(LocalSyncUploadGap.TextPlacement)
        if (target.objects.values.any { it is ExternalMediaNode } && !server.supportsExternalMedia) add(LocalSyncUploadGap.ExternalMedia)
        if (DraftMergeContractGap.ObjectIdReuse in contract) add(LocalSyncUploadGap.ObjectIdReuse)
        if (DraftMergeContractGap.RelationIdReuse in contract) add(LocalSyncUploadGap.RelationIdReuse)
        val serverAssets = fresh.workspace.objects.values.filterIsInstance<MediaNode>().flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.toSet()
        val uploadedAssets = snapshot.assetUploadPreparations.mapNotNull { prepared ->
            prepared.remoteIdentity?.id?.takeIf { id -> snapshot.materializations.any {
                prepared.remoteIdentity?.hasSamePreparedOriginal(it.source) == true &&
                    it.localAssetId == prepared.request.source.id && it.source.id == id
            } }
        }.toSet()
        val allowedAssets = serverAssets + uploadedAssets
        if (target.objects.values.filterIsInstance<MediaNode>().any { it.assetId !in allowedAssets || (it.thumbnailAssetId != null && it.thumbnailAssetId !in allowedAssets) }) add(LocalSyncUploadGap.MediaMaterialization)
    }
    if (gaps.isNotEmpty()) throw LocalSyncUploadBlocked(gaps)
    val mappedSources = snapshot.materializations.associateBy { it.source.id }
    target.objects.values.filterIsInstance<MediaNode>().forEach { node ->
        val asset = server.getAsset(fresh, node.assetId)
        require(asset.id == node.assetId && asset.status == AssetStatus.Ready && asset.workspaceId == fresh.workspace.id && mediaKindForAssetMediaType(asset.mediaType) == node.mediaKind)
        mappedSources[node.assetId]?.let { require(MaterializationSourceDescriptor.fromAsset(asset) == it.source) { "Mapped server original changed" } }
        node.thumbnailAssetId?.let { id ->
            val thumbnail = server.getAsset(fresh, id)
            check(canContinue())
            require(thumbnail.id == id && thumbnail.status == AssetStatus.Ready && thumbnail.workspaceId == fresh.workspace.id && mediaKindForAssetMediaType(thumbnail.mediaType) == MediaKind.Image)
            mappedSources[id]?.let { require(MaterializationSourceDescriptor.fromAsset(thumbnail) == it.source) { "Mapped server thumbnail changed" } }
        }
        check(canContinue())
    }
    if (snapshot.outgoing == null) {
        require(server.pendingChange(fresh) == null && server.pendingDraft(fresh) == null)
        check(canContinue())
        snapshot = store.stageSyncOutgoing(snapshot.local, OutgoingLocalSyncTransaction(proposal.id, proposal.remoteVersion,
            proposal.remoteSeq, proposal.remoteClientId, operation))
    }
    val outgoing = requireNotNull(snapshot.outgoing)
    if (server.pendingDraft(fresh) == null && server.pendingChange(fresh) == null) {
        check(canContinue())
        server.retainDraftAwaited(fresh, fresh.workspace, outgoing.operation)
    }
    check(canContinue())
    val prepared = server.prepareSubmission(fresh, outgoing.operation)
    check(canContinue())
    outgoing.prepared?.let { require(it == prepared) }
    return store.bindSyncOutgoingSubmission(snapshot.local, outgoing, prepared)
}

/** Called only after confirmation of this saved target. Query the original first; committed
 * requests settle without resending, fenced requests never send, unknown requests require the
 * original remote baseline still present. No fallback/new IDs after a response is lost. */
internal suspend fun sendLocalSyncUpload(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, expected: OutgoingLocalSyncTransaction,
    canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    check(canContinue())
    val store = requireNotNull(local.localSyncStore)
    val snapshot = store.readSyncSnapshot(owner)
    require(snapshot.outgoing == expected)
    val proposal = requireNotNull(snapshot.proposal)
    require(server.syncServerScope == proposal.binding.serverScope)
    proposal.binding.requireRemote(selected)
    val prepared = requireNotNull(expected.prepared)
    val verified = server.verifyPreparedSubmission(selected, prepared, expected.operation)
    check(canContinue())
    if (verified.status == PendingReceiptStatus.Committed) return verifyAndSettleLocalSyncUpload(local, owner, server, verified.current, canContinue)
    if (verified.status != PendingReceiptStatus.Unknown) throw LocalSyncReceiptUnavailable(verified.status)
    require(verified.current == proposal.remoteSession() && verified.current.canEditContent)
    require(store.readSyncSnapshot(owner).outgoing == expected)
    check(canContinue())
    require(server.sendPreparedSubmission(verified.current, prepared) is SubmitOutcome.Accepted) { "Original request was rejected" }
    check(canContinue())
    return verifyAndSettleLocalSyncUpload(local, owner, server, verified.current, canContinue)
}

internal suspend fun verifyAndSettleLocalSyncUpload(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    check(canContinue())
    val store = requireNotNull(local.localSyncStore)
    val snapshot = store.readSyncSnapshot(owner)
    val proposal = requireNotNull(snapshot.proposal)
    val outgoing = snapshot.outgoing ?: requireNotNull(snapshot.applied?.committed).outgoing
    require(server.syncServerScope == proposal.binding.serverScope)
    proposal.binding.requireRemote(selected)
    val prepared = requireNotNull(outgoing.prepared)
    val verified = server.verifyPreparedSubmission(selected, prepared, outgoing.operation, proposal.remoteSession())
    check(canContinue())
    if (verified.status != PendingReceiptStatus.Committed) throw LocalSyncReceiptUnavailable(verified.status)
    if (verified.current.workspaceVersion != verified.committedWorkspaceVersion || verified.current.lastServerSeq != verified.committedServerSeq ||
        !sameSyncContent(verified.current.workspace, proposal.target)) {
        require(verified.committedWorkspace != null) { "Historical commit snapshot unavailable" }
        throw LocalSyncRebaseRequired(snapshot, verified)
    }
    server.pendingChange(verified.current)?.let {
        require(it.transactionId == prepared.transactionId)
        val confirmed = server.confirmPendingReceipt(verified.current, prepared.transactionId)
        check(canContinue())
        require(confirmed == verified.current) { "Remote changed during receipt confirmation; review required" }
    }
    return store.settleCommittedSyncSubmission(snapshot.local, verified)
}

internal suspend fun stageLocalSyncRebase(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, reviewed: LocalSyncRebaseProposal,
    canContinue: () -> Boolean): LocalWorkspaceSyncSnapshot {
    check(canContinue())
    val original = reviewed.context.original
    require(server.syncServerScope == original.binding.serverScope)
    original.binding.requireRemote(selected)
    val verified = server.verifyPreparedSubmission(selected, requireNotNull(reviewed.context.outgoing.prepared),
        reviewed.context.outgoing.operation, original.remoteSession())
    check(canContinue())
    val store = requireNotNull(local.localSyncStore)
    val snapshot = store.readSyncSnapshot(owner)
    require(localSyncRebaseContext(snapshot, verified) == reviewed.context && snapshot.local.workspace == reviewed.localBefore)
    check(canContinue())
    return store.stageSyncRebaseProposal(snapshot.local, snapshot.rebaseProposal?.id, reviewed, verified)
}

/** Fresh original evidence and head check for one explicitly confirmed local rebase apply.
 * Confirming the old adapter ACK is local recovery only; no replacement request is sent. */
internal suspend fun verifyLocalSyncRebaseApply(local: CanvasWorkspaceRepository, owner: WorkspaceSession,
    server: CanvasWorkspaceRepository, selected: WorkspaceSession, reviewed: LocalSyncRebaseProposal,
    canContinue: () -> Boolean): Pair<LocalWorkspaceSyncSnapshot, PreparedSubmissionVerification> {
    check(canContinue())
    val original = reviewed.context.original
    require(server.syncServerScope == original.binding.serverScope)
    original.binding.requireRemote(selected)
    val prepared = requireNotNull(reviewed.context.outgoing.prepared)
    val verified = server.verifyPreparedSubmission(selected, prepared, reviewed.context.outgoing.operation, original.remoteSession())
    check(canContinue())
    val store = requireNotNull(local.localSyncStore)
    val snapshot = store.readSyncSnapshot(owner)
    val previousApply = snapshot.rebases.singleOrNull { it.reviewed.id == reviewed.id }
    val publicationSource = if (previousApply != null) {
        require(previousApply.reviewed == reviewed && snapshot.local.workspace == previousApply.workspace)
        // Exact unknown-ACK retry: history still holds the approved before-state. This is
        // private publication input only, never a claim that storage rolled back.
        snapshot.copy(local = snapshot.local.copy(workspace = reviewed.localBefore, workspaceVersion = reviewed.localBefore.version),
            proposal = reviewed.context.original, applied = reviewed.context.applied,
            outgoing = reviewed.context.outgoing, rebaseProposal = reviewed)
    } else {
        require(snapshot.rebaseProposal == reviewed && snapshot.rebaseIsCurrent)
        snapshot
    }
    require(localSyncRebaseContext(publicationSource, verified) == reviewed.context)
    server.pendingChange(verified.current)?.let {
        require(it.transactionId == prepared.transactionId)
        val confirmed = server.confirmPendingReceipt(verified.current, prepared.transactionId)
        check(canContinue())
        require(confirmed == verified.current)
    }
    require(store.readSyncSnapshot(owner).local.workspace == snapshot.local.workspace)
    check(canContinue())
    return publicationSource to verified
}
