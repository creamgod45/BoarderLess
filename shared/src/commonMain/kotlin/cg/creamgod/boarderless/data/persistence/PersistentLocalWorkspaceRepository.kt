package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/** Platform-independent local documents and save receipts over a forced, generation-fenced record.
 * The platform supplies operation serialization, storage and time; no server data is copied. */
internal interface LocalDocumentLock {
    fun <T> withLock(block: () -> T): T
}

internal class PersistentLocalWorkspaceRepository(
    loadFiles: () -> DurableLocalRecordStore,
    private val lock: LocalDocumentLock,
    private val now: () -> String,
) : CanvasWorkspaceRepository, LocalWorkspaceSyncStore {
    private val files by lazy(loadFiles)
    private val json = Json { encodeDefaults = true; allowStructuredMapKeys = true }
    override val isLocalOnly = true
    override val localSyncStore: LocalWorkspaceSyncStore get() = this
    override val supportsExternalMedia = true
    override val supportsCanvasStyle = true
    override val supportsRelationGeometry = true
    override val supportsVectorPaths = true
    override val supportsTextPlacement = true
    override val supportsSequenceDiagrams = true

    @Serializable private data class Saved(
        val id: String,
        val digest: String,
        val version: Long,
        val at: String,
        val kind: String,
    )

    @Serializable private data class Board(
        val workspace: Workspace,
        val deleted: Boolean = false,
        val saved: List<Saved> = emptyList(),
        val syncCheckpoint: LocalWorkspaceSyncCheckpoint? = null,
        val syncProposal: LocalWorkspaceSyncProposal? = null,
        val syncApplied: AppliedLocalSyncProposal? = null,
        val syncOutgoing: OutgoingLocalSyncTransaction? = null,
        val syncStopped: List<StoppedLocalSyncTransaction> = emptyList(),
        val syncRebaseProposal: LocalSyncRebaseProposal? = null,
        val syncRebases: List<AppliedLocalSyncRebase> = emptyList(),
        val syncSuperseded: List<SupersededLocalSyncProposal> = emptyList(),
        val syncMaterializations: List<LocalAssetMaterialization> = emptyList(),
        val assetUploadPreparations: List<LocalAssetUploadPreparation> = emptyList(),
    )

    @Serializable private data class Documents(
        val version: Int = 1,
        val userId: String,
        val clientId: String,
        val activeId: String,
        val boards: List<Board>,
    )

    private data class Read(
        val generation: Long,
        val documents: Documents,
    )

    private fun read(): Read? {
        val record = files.read(Key) ?: return null
        val bytes = requireNotNull(record.payload) { "Local documents were removed" }
        val content = bytes.decodeToString(throwOnInvalidSequence = true)
        val raw = parseStrictJsonObject(content)
        requireStrictSequenceCanvasFields(raw)
        val d = json.decodeFromString<Documents>(content)
        validate(d)
        return Read(record.generation, d)
    }

    private fun validate(d: Documents) {
        require(d.version == 1 && d.userId.isNotBlank() && d.clientId.isNotBlank())
        require(
            d.boards.size in 1..256 && d.boards
                .map { it.workspace.id }
                .distinct()
                .size == d.boards.size,
        )
        require(d.boards.any { it.workspace.id.value == d.activeId && !it.deleted })
        val destinations = d.boards.flatMap { it.syncMaterializations }.map { it.localAssetId }
        require(destinations.distinct().size == destinations.size)
        d.boards.forEach { b ->
            require(b.assetUploadPreparations.size <= 4096)
            require(b.assetUploadPreparations.map { it.request.requestId }.distinct().size == b.assetUploadPreparations.size)
            b.assetUploadPreparations.forEach {
                require(it.request.binding == b.syncCheckpoint?.binding && it.request.userId == d.userId &&
                    it.request.clientId == d.clientId && it.request.workspaceId == b.workspace.id)
            }
            require(b.syncMaterializations.size <= 4096 && b.syncMaterializations.map { it.source.id }.distinct().size == b.syncMaterializations.size)
            b.syncMaterializations.forEach {
                require(it.binding == b.syncCheckpoint?.binding && it.userId == d.userId && it.clientId == d.clientId && it.workspaceId == b.workspace.id)
            }
            require(b.syncSuperseded.size <= 32 && b.syncSuperseded.map { it.proposal.id }.distinct().size == b.syncSuperseded.size)
            b.syncSuperseded.forEach { old ->
                require(old.checkpoint.binding == b.syncCheckpoint?.binding && old.proposal.target.id == b.workspace.id)
                require(old.applied.materializations.all { it in b.syncMaterializations })
            }
            require(b.syncRebases.size <= 32 && b.syncRebases.map { it.operation.operationId }.distinct().size == b.syncRebases.size)
            b.syncRebases.forEach { applied ->
                require(applied.materializations.all { it in b.syncMaterializations })
                require(applied.reviewed.context.original.binding == b.syncCheckpoint?.binding && applied.workspace.id == b.workspace.id)
                val captured = WorkspaceSession(d.userId, d.clientId, WorkspaceMemberRole.Owner, applied.reviewed.localBefore.version, 0, applied.reviewed.localBefore)
                require(LocalSyncRebasePlan(captured, applied.reviewed.context).proposal(applied.reviewed.id, applied.reviewed.checkpointDigest,
                    applied.reviewed.intentChoices, applied.reviewed.latestChoices) == applied.reviewed)
            }
            b.syncRebaseProposal?.let { rebase ->
                require(rebase.context.original == b.syncProposal && rebase.context.applied == b.syncApplied && rebase.context.outgoing == b.syncOutgoing)
                require(rebase.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
                val owner = WorkspaceSession(d.userId, d.clientId, WorkspaceMemberRole.Owner, rebase.localBefore.version, 0, rebase.localBefore)
                require(LocalSyncRebasePlan(owner, rebase.context).proposal(rebase.id, rebase.checkpointDigest,
                    rebase.intentChoices, rebase.latestChoices) == rebase)
            }
            require(b.syncStopped.size <= 32 && b.syncStopped.map { it.outgoing.prepared!!.transactionId }.distinct().size == b.syncStopped.size)
            b.syncStopped.forEach { stopped ->
                require(stopped.applied.materializations.all { it in b.syncMaterializations })
                require(stopped.proposal.target.id == b.workspace.id && stopped.proposal.binding == b.syncCheckpoint?.binding)
                require(syncWorkspaceDigest(stopped.applied.operation.before) == stopped.proposal.localDigest)
            }
            b.syncProposal?.let { proposal ->
                require(proposal.target.id == b.workspace.id && proposal.target.hasValidSequenceBindings())
                require(proposal.binding == b.syncCheckpoint?.binding)
                val checkpoint = requireNotNull(b.syncCheckpoint)
                val settled = b.syncApplied?.settledCheckpoint
                if (settled == null) require(proposal.checkpointDigest == syncCheckpointDigest(checkpoint))
                else {
                    require(settled == checkpoint && !settled.requiresInitialReview)
                    val commit = b.syncApplied?.committed
                    if (commit == null) {
                        require(settled.serverVersion == proposal.remoteVersion && settled.serverSeq == proposal.remoteSeq)
                        require(sameSyncContent(settled.baseline, proposal.remote) && sameSyncContent(proposal.target, proposal.remote))
                    } else {
                        commit.outgoing.requireProposal(proposal)
                        require(settled.serverVersion == commit.workspaceVersion && settled.serverSeq == commit.serverSeq)
                        require(sameSyncContent(settled.baseline, proposal.target))
                    }
                }
                if (settled == null && proposal.localDigest == syncWorkspaceDigest(b.workspace)) {
                    val plan = LocalWorkspaceSyncMergePlan(syncSnapshot(d, b), proposal.remoteSession())
                    require(plan.resolve(proposal.choices) == proposal.target)
                }
            }
            b.syncApplied?.let { applied ->
                val proposal = requireNotNull(b.syncProposal)
                applied.requireCanonicalTarget(proposal)
                require(applied.materializations.all { it in b.syncMaterializations })
                require(syncWorkspaceDigest(applied.operation.before) == proposal.localDigest)
                require(applied.appliedDigest == syncWorkspaceDigest(applied.operation.appliedWorkspace()))
                require(applied.committed == null || applied.settledCheckpoint != null)
            }
            b.syncOutgoing?.let { outgoing ->
                require(b.syncApplied != null && b.syncApplied.settledCheckpoint == null)
                outgoing.requireProposal(requireNotNull(b.syncProposal))
            }
            b.syncCheckpoint?.let { checkpoint ->
                require(checkpoint.baseline.id == b.workspace.id)
                require(checkpoint.baseline.hasValidSequenceBindings())
            }
            require(b.workspace.hasValidSequenceBindings()) { "Invalid sequence diagram bindings" }
            require(b.workspace.version in 0..9_007_199_254_740_991L && b.saved.size <= 200)
            require(
                b.saved
                    .map { it.id }
                    .distinct()
                    .size == b.saved.size,
            )
            require(b.saved.all { it.id.isNotBlank() && it.digest.matches(Regex("[0-9a-f]{64}")) && it.version in 1..b.workspace.version })
        }
    }

    private fun write(
        previous: Read?,
        documents: Documents,
    ): Documents {
        validate(documents)
        val content = json.encodeToString(documents)
        requireBoundedDraftJsonDepth(content)
        files.compareAndSet(Key, previous?.generation, content.encodeToByteArray())
        return documents
    }

    private fun session(
        d: Documents,
        b: Board,
    ) = WorkspaceSession(d.userId, d.clientId, WorkspaceMemberRole.Owner, b.workspace.version, 0, b.workspace)

    private fun owned(
        d: Documents,
        s: WorkspaceSession,
    ) {
        require(s.userId == d.userId && s.clientId == d.clientId)
    }

    private fun board(
        d: Documents,
        id: WorkspaceId,
    ) = d.boards.single { it.workspace.id == id && !it.deleted }

    private fun hash(operation: WorkspaceOperation) =
        SHA256()
            .digest(json.encodeToString(WorkspaceOperation.serializer(), operation).encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun summary(b: Board) = WorkspaceSummary(b.workspace.id, b.workspace.title, "owner", b.workspace.version, 0)

    override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?): WorkspaceSession =
        lock.withLock {
            val current = read()
            val d =
                if (current == null) {
                    val workspace = Workspace(WorkspaceId(randomUuid()), "BoarderLess")
                    write(
                        null,
                        Documents(
                            userId = "local",
                            clientId = randomUuid(),
                            activeId = workspace.id.value,
                            boards = listOf(Board(workspace)),
                        ),
                    )
                } else {
                    current.documents
                }
            val selected =
                preferredWorkspaceId?.let { id -> d.boards.singleOrNull { it.workspace.id == id && !it.deleted } }
                    ?: board(d, WorkspaceId(d.activeId))
            if (d.activeId != selected.workspace.id.value) write(requireNotNull(current), d.copy(activeId = selected.workspace.id.value))
            session(d, selected)
        }

    override fun retainDraft(
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) = lock.withLock {
        val current = requireNotNull(read())
        val d = current.documents
        owned(d, session)
        val b = board(d, before.id)
        require(session.workspace.id == before.id)
        val after = (operation.applyTo(before) as? OperationResult.Applied)?.workspace ?: error("Local change cannot be applied")
        val existing = b.saved.singleOrNull { it.id == operation.operationId }
        if (existing != null) {
            require(existing.digest == hash(operation) && existing.version == after.version && b.workspace == after) {
                "Saved local operation or document changed"
            }
            files.confirmDurable(Key, current.generation)
            return@withLock Unit
        }
        require(b.workspace == before) { "Local document changed" }
        val saved =
            Saved(operation.operationId, hash(operation), after.version, now(), operation::class.simpleName ?: "edit")
        write(
            current,
            d.copy(boards = d.boards.map { if (it == b) b.copy(workspace = after, saved = (b.saved + saved).takeLast(200)) else it }),
        )
        Unit
    }

    override suspend fun submit(
        session: WorkspaceSession,
        operation: WorkspaceOperation,
    ): SubmitOutcome =
        lock.withLock {
            val d = requireNotNull(read()).documents
            owned(d, session)
            val b = board(d, session.workspace.id)
            val saved = b.saved.single { it.id == operation.operationId }
            require(saved.digest == hash(operation)) { "Saved operation content changed" }
            // retainDraft already forced the document before optimistic UI publication. Complete
            // any prior unknown directory barrier, without allocating another file generation.
            val current = requireNotNull(read())
            files.confirmDurable(Key, current.generation)
            SubmitOutcome.Accepted(saved.version, 0)
        }

    override suspend fun refresh(session: WorkspaceSession): WorkspaceSession =
        lock.withLock {
            val d = requireNotNull(read()).documents
            owned(d, session)
            session(d, board(d, session.workspace.id))
        }

    override suspend fun readSyncSnapshot(session: WorkspaceSession): LocalWorkspaceSyncSnapshot = lock.withLock {
        val d = requireNotNull(read()).documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        syncSnapshot(d, b)
    }

    private fun syncSnapshot(d: Documents, b: Board) = LocalWorkspaceSyncSnapshot(
        session(d, b), b.syncCheckpoint, b.syncProposal, b.syncApplied, b.syncOutgoing, b.syncStopped, b.syncRebaseProposal, b.syncRebases, b.syncSuperseded, b.syncMaterializations, b.assetUploadPreparations)

    override suspend fun stageUploadedAssetMaterialization(verified: VerifiedUploadedAssetMaterialization): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read()); val d = previous.documents
        val expected = verified.reviewed; owned(d, expected.local)
        val b = board(d, expected.local.workspace.id); val current = syncSnapshot(d, b)
        val mapping = verified.mapping; val preparation = verified.preparation
        require(preparation in b.assetUploadPreparations &&
            preparation.remoteIdentity?.hasSamePreparedOriginal(mapping.source) == true)
        require(mapping.localSource == preparation.request.source)
        require(mapping.localAssetId == preparation.request.source.id && mapping.createdAt == preparation.request.source.createdAt)
        require(mapping.binding == preparation.request.binding && mapping.reviewId == preparation.request.reviewId)
        b.syncMaterializations.singleOrNull { it.source.id == mapping.source.id }?.let {
            require(it == mapping && current.copy(materializations = expected.materializations) == expected)
            require(current.materializations == expected.materializations || current.materializations == expected.materializations + mapping)
            files.confirmDurable(Key, previous.generation)
            return@withLock current
        }
        require(current == expected && current.outgoing == null && current.rebaseProposal == null)
        preparation.request.requireReview(current)
        require(b.syncMaterializations.size < 4096 && d.boards.none { other ->
            other.syncMaterializations.any { it.localAssetId == mapping.localAssetId || it.source.id == mapping.source.id }
        })
        validatePinnedLocalAsset(expected.local, mapping.pendingAsset())
        MaterializedAssetProjection(expected.local, mapping.binding, b.syncMaterializations + mapping)
        val changed = b.copy(syncMaterializations = b.syncMaterializations + mapping)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun stageAssetUploadPreparation(session: WorkspaceSession,
        expected: LocalAssetUploadPreparation?, next: LocalAssetUploadPreparation): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read()); val d = previous.documents
        owned(d, session); val b = board(d, session.workspace.id)
        val request = next.request
        require(request.userId == d.userId && request.clientId == d.clientId && request.workspaceId == b.workspace.id)
        require(request.binding == b.syncCheckpoint?.binding)
        val stored = b.assetUploadPreparations.singleOrNull { it.request.requestId == request.requestId }
        if (stored == next) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(stored == expected) { "Upload preparation changed" }
        if (stored == null) {
            require(next.remoteIdentity == null && next.ticketRequestIds.isEmpty() && next.leases.isEmpty())
            require(b.assetUploadPreparations.size < 4096)
            require(b.assetUploadPreparations.none { it.request.source.id == request.source.id && it.request.binding == request.binding }) {
                "Recover the existing preparation; do not mint another request"
            }
        } else next.requireSuccessorOf(stored)
        // Receipt recovery may survive later local edits. New reservations/leases require
        // the reviewed document; accepting an existing receipt grants no upload permission.
        if (stored == null || next.ticketRequestIds != stored.ticketRequestIds) {
            require(session.workspace == b.workspace && session.workspaceVersion == b.workspace.version)
            request.requireReview(syncSnapshot(d, b))
        }
        val changed = b.copy(assetUploadPreparations = if (stored == null) b.assetUploadPreparations + next
            else b.assetUploadPreparations.map { if (it == stored) next else it })
        val saved = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(saved, changed)
    }

    override suspend fun stageSyncMaterialization(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        mapping: LocalAssetMaterialization): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read()); val d = previous.documents
        owned(d, session); val b = board(d, session.workspace.id)
        require(mapping.userId == d.userId && mapping.clientId == d.clientId && mapping.workspaceId == b.workspace.id)
        require(mapping.binding == b.syncCheckpoint?.binding && mapping.binding == expectedCheckpoint.binding)
        validatePinnedLocalAsset(session, mapping.pendingAsset())
        b.syncMaterializations.singleOrNull { it.source.id == mapping.source.id }?.let {
            require(it == mapping) { "Materialization identity changed" }
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncCheckpoint == expectedCheckpoint && session.workspace == b.workspace && session.workspaceVersion == b.workspace.version)
        val current = syncSnapshot(d, b)
        require(if (b.syncRebaseProposal != null) current.rebaseIsCurrent else current.proposalIsCurrent || current.appliedIsCurrent) {
            "Materialization review is stale"
        }
        val reviewed = b.syncRebaseProposal?.target ?: b.syncProposal?.target ?: error("Materialization requires a saved review")
        require(mapping.reviewId == (b.syncRebaseProposal?.id ?: b.syncProposal?.id))
        reviewed.requireMaterializationReference(mapping.source)
        require(b.syncMaterializations.size < 4096 && d.boards.none { other ->
            other.syncMaterializations.any { it.localAssetId == mapping.localAssetId } ||
                other.workspace.objects.values.filterIsInstance<MediaNode>().any { it.assetId == mapping.localAssetId || it.thumbnailAssetId == mapping.localAssetId }
        }) { "Materialization destination is already used" }
        val changed = b.copy(syncMaterializations = b.syncMaterializations + mapping)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun applySyncRebaseProposal(session: WorkspaceSession, reviewedId: String,
        operation: LocalWorkspaceContentOperation, verification: PreparedSubmissionVerification): LocalSyncApplyResult =
        applyLocalSyncRebase(session, reviewedId, operation, verification, null)

    override suspend fun applyMaterializedSyncRebaseProposal(verified: VerifiedMaterializedTarget,
        operation: LocalWorkspaceContentOperation, receipt: PreparedSubmissionVerification): LocalSyncApplyResult =
        applyLocalSyncRebase(verified.reviewed.local, requireNotNull(verified.reviewed.rebaseProposal).id, operation, receipt, verified)

    private fun applyLocalSyncRebase(session: WorkspaceSession, reviewedId: String,
        operation: LocalWorkspaceContentOperation, verification: PreparedSubmissionVerification,
        materialized: VerifiedMaterializedTarget?): LocalSyncApplyResult = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        b.syncRebases.singleOrNull { it.reviewed.id == reviewedId }?.let {
            require(it.operation == operation && b.workspace == it.workspace) { "Applied rebase changed after confirmation" }
            val mappings: List<LocalAssetMaterialization> = materialized?.reviewed?.materializations ?: emptyList()
            require(it.materializations == mappings)
            materialized?.let { proof -> require(proof.reviewed.rebaseProposal == it.reviewed && proof.localTarget == operation.after) }
            files.confirmDurable(Key, previous.generation)
            return@withLock LocalSyncApplyResult(syncSnapshot(d, b), operation)
        }
        val reviewed = requireNotNull(b.syncRebaseProposal)
        require(reviewed.id == reviewedId && b.workspace == reviewed.localBefore)
        require(localSyncRebaseContext(syncSnapshot(d, b), verification) == reviewed.context)
        require(reviewed.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
        require(b.syncRebases.size < 32 && b.saved.none { it.id == operation.operationId })
        materialized?.let { require(it.reviewed == syncSnapshot(d, b) && it.localTarget == operation.after) }
        val applied = AppliedLocalSyncRebase(reviewed, operation, materialized?.reviewed?.materializations ?: emptyList())
        val localAssets = b.workspace.objects.values.filterIsInstance<MediaNode>().flatMap { listOfNotNull(it.assetId, it.thumbnailAssetId) }.toMutableSet()
        localAssets.addAll(applied.materializations.map { it.localAssetId })
        require(applied.workspace.objects.values.filterIsInstance<MediaNode>().all { it.assetId in localAssets && (it.thumbnailAssetId == null || it.thumbnailAssetId in localAssets) }) { "Remote originals require materialization" }
        val updatedSession = session(d, b).copy(workspace = applied.workspace, workspaceVersion = applied.workspace.version)
        val continuation = applied.continuation(updatedSession)
        val changed = b.copy(workspace = applied.workspace, syncCheckpoint = applied.checkpoint, syncProposal = continuation,
            syncApplied = AppliedLocalSyncProposal(continuation.id, applied.normalizedOperation, syncWorkspaceDigest(applied.workspace), materializations = applied.materializations),
            syncOutgoing = null, syncRebaseProposal = null, syncRebases = b.syncRebases + applied,
            saved = (b.saved + Saved(operation.operationId, hash(operation), applied.workspace.version, now(), "local-sync-rebase")).takeLast(200))
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        LocalSyncApplyResult(syncSnapshot(next, changed), operation)
    }

    override suspend fun stageSyncRebaseProposal(session: WorkspaceSession, expectedId: String?, proposal: LocalSyncRebaseProposal,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        if (b.syncRebaseProposal == proposal) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncRebaseProposal?.id != proposal.id && b.syncRebaseProposal?.id == expectedId)
        require(b.workspace == proposal.localBefore && proposal.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
        require(localSyncRebaseContext(syncSnapshot(d, b), verification) == proposal.context) { "Commit/head changed; review again" }
        val planned = LocalSyncRebasePlan(session(d, b), proposal.context).proposal(proposal.id, proposal.checkpointDigest,
            proposal.intentChoices, proposal.latestChoices)
        require(planned == proposal)
        val changed = b.copy(syncRebaseProposal = proposal)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun archiveFencedSyncSubmission(session: WorkspaceSession,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot = lock.withLock {
        require(verification.status == PendingReceiptStatus.Fenced)
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val checkpoint = requireNotNull(b.syncCheckpoint)
        require(b.syncRebaseProposal == null) { "Committed rebase intent cannot be fenced away" }
        checkpoint.binding.requireRemote(verification.current)
        require(verification.current.clientId == verification.prepared.clientId)
        b.syncStopped.singleOrNull { it.outgoing.prepared == verification.prepared }?.let {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncStopped.size < 32) { "Stopped transaction archive requires export before adding more" }
        val outgoing = requireNotNull(b.syncOutgoing)
        require(outgoing.prepared == verification.prepared)
        val stopped = StoppedLocalSyncTransaction(requireNotNull(b.syncProposal), requireNotNull(b.syncApplied), outgoing,
            verification.current.workspaceVersion, verification.current.lastServerSeq)
        val changed = b.copy(syncProposal = null, syncApplied = null, syncOutgoing = null, syncStopped = b.syncStopped + stopped)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun settleCommittedSyncSubmission(session: WorkspaceSession,
        verification: PreparedSubmissionVerification): LocalWorkspaceSyncSnapshot = lock.withLock {
        require(verification.status == PendingReceiptStatus.Committed)
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val proposal = requireNotNull(b.syncProposal)
        val applied = requireNotNull(b.syncApplied)
        val current = verification.current
        require(b.syncRebaseProposal == null) { "Reviewed rebase requires explicit application" }
        proposal.binding.requireRemote(current)
        require(current.clientId == proposal.remoteClientId)
        val version = requireNotNull(verification.committedWorkspaceVersion)
        val seq = requireNotNull(verification.committedServerSeq)
        require(current.workspaceVersion == version && current.lastServerSeq == seq && sameSyncContent(current.workspace, proposal.target)) {
            "Remote changed after original commit; review required"
        }
        val outgoing = b.syncOutgoing ?: requireNotNull(applied.committed).outgoing
        require(outgoing.prepared == verification.prepared)
        outgoing.requireProposal(proposal)
        val commit = CommittedLocalSyncTransaction(outgoing, version, seq)
        val checkpoint = LocalWorkspaceSyncCheckpoint(proposal.binding, current.workspace.copy(id = b.workspace.id), version, seq)
        if (applied.committed == commit) {
            require(applied.settledCheckpoint == checkpoint && b.syncCheckpoint == checkpoint)
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(applied.settledCheckpoint == null && proposal.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
        val changed = b.copy(syncCheckpoint = checkpoint, syncOutgoing = null,
            syncApplied = applied.copy(settledCheckpoint = checkpoint, committed = commit))
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun bindSyncOutgoingSubmission(session: WorkspaceSession, expected: OutgoingLocalSyncTransaction,
        prepared: PreparedWorkspaceSubmission): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val proposal = requireNotNull(b.syncProposal)
        val bound = expected.copy(prepared = prepared)
        bound.requireProposal(proposal)
        if (b.syncOutgoing == bound) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(expected.prepared == null && b.syncOutgoing == expected) { "Outgoing request identity cannot be replaced" }
        val changed = b.copy(syncOutgoing = bound)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun stageSyncOutgoing(session: WorkspaceSession, outgoing: OutgoingLocalSyncTransaction): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val proposal = requireNotNull(b.syncProposal)
        val applied = requireNotNull(b.syncApplied)
        applied.requireCanonicalTarget(proposal)
        require(!syncSnapshot(d, b).requiresAssetIdentityReview) { "Uploaded identity requires a new canonical review" }
        require(applied.settledCheckpoint == null)
        outgoing.requireProposal(proposal)
        b.syncOutgoing?.let {
            require(it == outgoing) { "Original outgoing transaction requires reconciliation" }
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(outgoing.prepared == null) { "Save the canvas transaction before binding wire identity" }
        require(b.workspace == applied.operation.appliedWorkspace()) { "Local edits require a new review" }
        val changed = b.copy(syncOutgoing = outgoing)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun settleReadOnlySyncProposal(session: WorkspaceSession, proposalId: String,
        verifiedRemote: WorkspaceSession): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val proposal = requireNotNull(b.syncProposal)
        val applied = requireNotNull(b.syncApplied)
        require(b.syncOutgoing == null) { "Outgoing transaction requires receipt reconciliation" }
        require(proposal.id == proposalId && applied.proposalId == proposalId)
        proposal.binding.requireRemote(verifiedRemote)
        require(verifiedRemote.workspaceVersion == proposal.remoteVersion && verifiedRemote.lastServerSeq == proposal.remoteSeq)
        require(sameSyncContent(proposal.remote, verifiedRemote.workspace)) { "Remote changed; review again" }
        require(sameSyncContent(proposal.target, proposal.remote)) { "Upload requires submission receipts" }
        val checkpoint = LocalWorkspaceSyncCheckpoint(proposal.binding,
            verifiedRemote.workspace.copy(id = b.workspace.id), verifiedRemote.workspaceVersion, verifiedRemote.lastServerSeq)
        applied.settledCheckpoint?.let {
            require(it == checkpoint && b.syncCheckpoint == checkpoint)
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(proposal.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
        val changed = b.copy(syncCheckpoint = checkpoint, syncApplied = applied.copy(settledCheckpoint = checkpoint))
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun applyMaterializedSyncProposal(verified: VerifiedMaterializedTarget,
        operation: LocalWorkspaceContentOperation): LocalSyncApplyResult = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        val expected = verified.reviewed
        owned(d, expected.local)
        val b = board(d, expected.local.workspace.id)
        val proposal = requireNotNull(expected.proposal)
        require(expected.materializations.isNotEmpty() && expected.proposalIsCurrent && expected.applied == null && expected.outgoing == null && expected.rebaseProposal == null)
        require(operation.before == expected.local.workspace && operation.after == verified.localTarget && !operation.restoreVersions)
        val captured = AppliedLocalSyncProposal(proposal.id, operation, syncWorkspaceDigest(operation.appliedWorkspace()),
            materializations = expected.materializations)
        captured.requireCanonicalTarget(proposal)
        b.syncApplied?.let {
            require(it == captured && b.workspace == operation.appliedWorkspace()) { "Another local publication must be preserved" }
            require(b.syncProposal == expected.proposal && b.syncCheckpoint == expected.checkpoint &&
                b.syncMaterializations == expected.materializations && b.syncOutgoing == null && b.syncRebaseProposal == null)
            files.confirmDurable(Key, previous.generation)
            return@withLock LocalSyncApplyResult(syncSnapshot(d, b), operation)
        }
        require(syncSnapshot(d, b) == expected) { "Verified materialization review changed" }
        require(b.saved.none { it.id == operation.operationId })
        val workspace = operation.appliedWorkspace()
        val saved = Saved(operation.operationId, hash(operation), workspace.version, now(), "local-sync-materialized-merge")
        val changed = b.copy(workspace = workspace, syncApplied = captured, saved = (b.saved + saved).takeLast(200))
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        LocalSyncApplyResult(syncSnapshot(next, changed), operation)
    }

    override suspend fun applySyncProposal(session: WorkspaceSession, proposalId: String,
        operation: LocalWorkspaceContentOperation): LocalSyncApplyResult = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val proposal = requireNotNull(b.syncProposal)
        require(proposal.id == proposalId)
        b.syncApplied?.let { applied ->
            require(applied.operation == operation && applied.proposalId == proposalId)
            require(b.workspace == operation.appliedWorkspace()) { "Applied proposal changed after confirmation" }
            files.confirmDurable(Key, previous.generation)
            return@withLock LocalSyncApplyResult(syncSnapshot(d, b), operation)
        }
        require(!operation.restoreVersions && operation.before == b.workspace && operation.after == proposal.target)
        val localAssets = b.workspace.objects.values.filterIsInstance<MediaNode>().map { it.assetId }.toSet()
        require(proposal.target.objects.values.filterIsInstance<MediaNode>().all { it.assetId in localAssets }) {
            "Server originals require verified local materialization"
        }
        require(proposal.localDigest == syncWorkspaceDigest(b.workspace) && proposal.checkpointDigest == syncCheckpointDigest(requireNotNull(b.syncCheckpoint)))
        require(b.saved.none { it.id == operation.operationId })
        val workspace = operation.appliedWorkspace()
        val applied = AppliedLocalSyncProposal(proposalId, operation, syncWorkspaceDigest(workspace))
        val saved = Saved(operation.operationId, hash(operation), workspace.version, now(), "local-sync-merge")
        val changed = b.copy(workspace = workspace, syncApplied = applied, saved = (b.saved + saved).takeLast(200))
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        LocalSyncApplyResult(syncSnapshot(next, changed), operation)
    }

    override suspend fun stageSyncProposal(session: WorkspaceSession, expectedCheckpoint: LocalWorkspaceSyncCheckpoint,
        expectedProposalId: String?, proposal: LocalWorkspaceSyncProposal): LocalWorkspaceSyncSnapshot = lock.withLock {
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        require(b.syncOutgoing == null) { "Outgoing transaction requires receipt reconciliation" }
        if (b.syncProposal == proposal) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncProposal?.id != proposal.id) { "A saved proposal ID cannot change content" }
        require(b.syncRebaseProposal == null) { "Committed rebase requires its separate review" }
        require(b.syncCheckpoint == expectedCheckpoint && b.syncProposal?.id == expectedProposalId)
        require(proposal.binding == expectedCheckpoint.binding && proposal.localDigest == syncWorkspaceDigest(b.workspace))
        require(proposal.checkpointDigest == syncCheckpointDigest(expectedCheckpoint))
        val plan = LocalWorkspaceSyncMergePlan(syncSnapshot(d, b), proposal.remoteSession())
        require(plan.resolve(proposal.choices) == proposal.target) { "Reviewed target differs from choices" }
        val archived = b.syncApplied?.let {
            require(b.syncSuperseded.size < 32 && b.syncSuperseded.none { old -> old.proposal.id == b.syncProposal?.id }) {
                "Previous local reviews require export before adding more"
            }
            SupersededLocalSyncProposal(requireNotNull(b.syncProposal), it, expectedCheckpoint)
        }
        val changed = b.copy(syncProposal = proposal, syncApplied = null,
            syncSuperseded = if (archived == null) b.syncSuperseded else b.syncSuperseded + archived)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun bindSyncDestination(
        session: WorkspaceSession,
        binding: LocalWorkspaceSyncBinding,
        verifiedRemote: WorkspaceSession,
    ): LocalWorkspaceSyncSnapshot = lock.withLock {
        binding.requireRemote(verifiedRemote)
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        require(session.workspace == b.workspace && session.workspaceVersion == b.workspace.version)
        val checkpoint = LocalWorkspaceSyncCheckpoint(binding,
            verifiedRemote.workspace.copy(id = b.workspace.id), verifiedRemote.workspaceVersion, verifiedRemote.lastServerSeq,
            requiresInitialReview = !sameSyncContent(b.workspace, verifiedRemote.workspace))
        if (b.syncCheckpoint == checkpoint) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncCheckpoint == null) { "A document already has a sync destination" }
        val changed = b.copy(syncCheckpoint = checkpoint)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun acknowledgeSyncCheckpoint(
        session: WorkspaceSession,
        expected: LocalWorkspaceSyncCheckpoint,
        verifiedRemote: WorkspaceSession,
    ): LocalWorkspaceSyncSnapshot = lock.withLock {
        require(!expected.requiresInitialReview) { "Initial pairing differences require an explicit reviewed merge" }
        expected.binding.requireRemote(verifiedRemote)
        require(verifiedRemote.workspaceVersion >= expected.serverVersion && verifiedRemote.lastServerSeq >= expected.serverSeq)
        val previous = requireNotNull(read())
        val d = previous.documents
        owned(d, session)
        val b = board(d, session.workspace.id)
        val checkpoint = LocalWorkspaceSyncCheckpoint(expected.binding,
            verifiedRemote.workspace.copy(id = b.workspace.id), verifiedRemote.workspaceVersion, verifiedRemote.lastServerSeq)
        require(b.syncProposal == null) { "A reviewed proposal requires settlement before advancing sync" }
        if (b.syncCheckpoint == checkpoint) {
            files.confirmDurable(Key, previous.generation)
            return@withLock syncSnapshot(d, b)
        }
        require(b.syncCheckpoint == expected) { "Sync checkpoint changed; compare again" }
        val changed = b.copy(syncCheckpoint = checkpoint)
        val next = write(previous, d.copy(boards = d.boards.map { if (it == b) changed else it }))
        syncSnapshot(next, changed)
    }

    override suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary> =
        lock.withLock {
            val d = requireNotNull(read()).documents
            owned(d, session)
            d.boards.filterNot { it.deleted }.map(::summary)
        }

    override suspend fun createWorkspace(
        session: WorkspaceSession,
        title: String,
    ): WorkspaceSession =
        lock.withLock {
            require(title.isNotBlank() && title.encodeToByteArray().size <= 4096)
            val current = requireNotNull(read())
            val d = current.documents
            owned(d, session)
            require(d.boards.size < 256)
            val b = Board(Workspace(WorkspaceId(randomUuid()), title.trim()))
            val next = write(current, d.copy(activeId = b.workspace.id.value, boards = d.boards + b))
            session(next, b)
        }

    override suspend fun openWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ): WorkspaceSession =
        lock.withLock {
            val current = requireNotNull(read())
            val d = current.documents
            owned(d, session)
            val b = board(d, workspaceId)
            write(current, d.copy(activeId = workspaceId.value))
            session(d, b)
        }

    override suspend fun renameWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
        title: String,
    ): WorkspaceSummary =
        lock.withLock {
            require(title.isNotBlank() && title.encodeToByteArray().size <= 4096)
            val current = requireNotNull(read())
            val d = current.documents
            owned(d, session)
            val b = board(d, workspaceId)
            val renamed = b.copy(workspace = b.workspace.copy(title = title.trim()))
            write(current, d.copy(boards = d.boards.map { if (it == b) renamed else it }))
            summary(renamed)
        }

    override suspend fun deleteWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ) = lock.withLock {
        val current = requireNotNull(read())
        val d = current.documents
        owned(d, session)
        val b = board(d, workspaceId)
        var boards = d.boards.map { if (it == b) it.copy(deleted = true) else it }
        val active =
            boards.firstOrNull { !it.deleted } ?: Board(
                Workspace(WorkspaceId(randomUuid()), "BoarderLess"),
            ).also { boards = boards + it }
        write(current, d.copy(boards = boards, activeId = if (d.activeId == workspaceId.value) active.workspace.id.value else d.activeId))
        Unit
    }

    override suspend fun listRecentActivity(
        session: WorkspaceSession,
        limit: Int,
    ): List<WorkspaceActivity> =
        lock.withLock {
            require(limit in 1..200)
            val d = requireNotNull(read()).documents
            owned(d, session)
            board(d, session.workspace.id).saved.takeLast(limit).reversed().map {
                WorkspaceActivity(0, d.userId, d.clientId, it.version, it.kind, it.at)
            }
        }

    override suspend fun listWorkspaceMembers(session: WorkspaceSession): List<WorkspaceMember> {
        refresh(session)
        return listOf(WorkspaceMember(session.userId, "Local", WorkspaceMemberRole.Owner, ""))
    }

    override suspend fun setWorkspaceMemberRole(
        session: WorkspaceSession,
        userId: String,
        role: WorkspaceMemberRole,
    ) {
        error("Local documents are not shared")
    }

    override suspend fun removeWorkspaceMember(
        session: WorkspaceSession,
        userId: String,
    ) {
        error("Local documents are not shared")
    }

    override suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset> {
        refresh(session)
        return emptyList()
    }

    override suspend fun getAsset(
        session: WorkspaceSession,
        assetId: String,
    ): WorkspaceAsset {
        refresh(session)
        error("Local assets require a platform gateway")
    }

    override fun close() {}

    companion object {
        val Key =
            SHA256().digest("BoarderLess.local-documents.v1".encodeToByteArray()).joinToString("") {
                (
                    it.toInt() and
                        255
                ).toString(16).padStart(2, '0')
            }
    }
}
