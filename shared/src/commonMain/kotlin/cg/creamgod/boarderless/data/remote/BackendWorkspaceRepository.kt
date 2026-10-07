package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.AssetRepository
import cg.creamgod.boarderless.data.AssetStatus
import cg.creamgod.boarderless.data.PendingWorkspaceChange
import cg.creamgod.boarderless.data.PendingWorkspaceDraft
import cg.creamgod.boarderless.data.RestoredWorkspaceDraft
import cg.creamgod.boarderless.data.SubmitOutcome
import cg.creamgod.boarderless.data.WorkspaceActivity
import cg.creamgod.boarderless.data.WorkspaceAsset
import cg.creamgod.boarderless.data.WorkspaceDraftReview
import cg.creamgod.boarderless.data.WorkspaceMember
import cg.creamgod.boarderless.data.WorkspaceMemberRole
import cg.creamgod.boarderless.data.WorkspaceRepository
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.WorkspaceSummary
import cg.creamgod.boarderless.data.canEditContent
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.history.CreateRelationsOperation
import cg.creamgod.boarderless.domain.history.DeleteObjectsOperation
import cg.creamgod.boarderless.domain.history.DeleteRelationsOperation
import cg.creamgod.boarderless.domain.history.EditTextOperation
import cg.creamgod.boarderless.domain.history.ReparentObjectsOperation
import cg.creamgod.boarderless.domain.history.TransactionOperation
import cg.creamgod.boarderless.domain.history.TransformObjectsOperation
import cg.creamgod.boarderless.domain.history.UpdateGroupFrameAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateMediaNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateMediaReferenceOperation
import cg.creamgod.boarderless.domain.history.UpdateRelationAttributesOperation
import cg.creamgod.boarderless.domain.history.UpdateTextNodeAttributesOperation
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.CanvasObject
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.MediaKind
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.domain.model.Relation
import cg.creamgod.boarderless.domain.model.RelationDirection
import cg.creamgod.boarderless.domain.model.RelationId
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

class BackendWorkspaceRepository(
    baseUrl: String = defaultBackendBaseUrl(),
    private val preferences: SessionPreferences = SessionPreferences(),
    private val client: HttpClient = createHttpClient(),
) : cg.creamgod.boarderless.data.CanvasWorkspaceRepository {
    private val apiBase = "${baseUrl.trimEnd('/')}/api/v1"
    private val submissionMutex = preferences.submissionMutex
    private val draftPersistence = preferences.draftPersistence
    private val sequenceAllocator = preferences.sequenceAllocator

    private data class ProjectionCache(
        val session: WorkspaceSession,
        val state: WorkspaceStateDto,
    )

    // One authoritative raw snapshot only. Optimistic/domain-only sessions cannot reuse it.
    private var projectionCache: ProjectionCache? = null
    private val replayJson = Json { ignoreUnknownKeys = true }

    override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?): WorkspaceSession {
        val savedUserId = preferences.userId
        if (savedUserId != null && preferredWorkspaceId != null && preferredWorkspaceId.value != preferences.workspaceId) {
            try {
                return loadAndRemember(savedUserId, preferredWorkspaceId.value)
            } catch (error: BackendHttpException) {
                // Gone or no longer shared: fall back to the last opened workspace.
                if (error.status != HttpStatusCode.NotFound && error.status != HttpStatusCode.Forbidden) throw error
            }
        }
        val savedWorkspaceId = preferences.workspaceId
        if (savedUserId != null && savedWorkspaceId != null) {
            try {
                return loadSession(savedUserId, savedWorkspaceId)
            } catch (error: BackendHttpException) {
                when (error.status) {
                    HttpStatusCode.NotFound -> preferences.workspaceId = null
                    HttpStatusCode.Unauthorized -> preferences.clearIdentity()
                    else -> throw error
                }
            }
        }

        if (savedUserId != null && preferences.userId != null) {
            try {
                val available = listWorkspaceDtos(savedUserId)
                if (available.isNotEmpty()) {
                    return loadAndRemember(savedUserId, available.first().id)
                }
                return createWorkspaceForUser(savedUserId, Strings.content.myThinkingSpace())
            } catch (error: BackendHttpException) {
                if (error.status != HttpStatusCode.Unauthorized && error.status != HttpStatusCode.NotFound) throw error
                preferences.clearIdentity()
            }
        }

        return createSession()
    }

    override suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary> =
        listWorkspaceDtos(session.userId).map(WorkspaceDto::toSummary)

    override suspend fun createWorkspace(
        session: WorkspaceSession,
        title: String,
    ): WorkspaceSession {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Workspace title must not be blank" }
        return createWorkspaceForUser(session.userId, normalizedTitle)
    }

    override suspend fun openWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ): WorkspaceSession = loadAndRemember(session.userId, workspaceId.value)

    override suspend fun renameWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
        title: String,
    ): WorkspaceSummary {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Workspace title must not be blank" }
        return client
            .patch("$apiBase/workspaces/${workspaceId.value}") {
                contentType(ContentType.Application.Json)
                header(DevUserHeader, session.userId)
                setBody(RenameWorkspaceRequest(normalizedTitle))
            }.requireSuccess()
            .body<WorkspaceDto>()
            .toSummary()
    }

    override suspend fun deleteWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ) {
        client
            .delete("$apiBase/workspaces/${workspaceId.value}") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
        if (preferences.workspaceId == workspaceId.value) preferences.workspaceId = null
    }

    override suspend fun listRecentActivity(
        session: WorkspaceSession,
        limit: Int,
    ): List<WorkspaceActivity> {
        require(limit in 1..1000) { "Activity limit must be between 1 and 1000" }
        val metadata =
            client
                .get("$apiBase/workspaces/${session.workspace.id.value}") {
                    header(DevUserHeader, session.userId)
                }.requireSuccess()
                .body<WorkspaceDto>()
        val afterSeq = recentActivityAfterSeq(metadata.lastServerSeq, limit)
        return client
            .get("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                header(DevUserHeader, session.userId)
                parameter("afterSeq", afterSeq)
                parameter("limit", limit)
            }.requireSuccess()
            .body<CatchUpOperationsDto>()
            .operations
            .map { operation ->
                WorkspaceActivity(
                    serverSeq = operation.serverSeq,
                    actorId = operation.actorId,
                    clientId = operation.clientId,
                    workspaceVersion = operation.workspaceVersion,
                    operationType = operation.operationType,
                    committedAt = operation.committedAt,
                )
            }
    }

    override suspend fun listWorkspaceMembers(session: WorkspaceSession): List<WorkspaceMember> =
        client
            .get("$apiBase/workspaces/${session.workspace.id.value}/members") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
            .body<WorkspaceMemberListDto>()
            .members
            .map(WorkspaceMemberDto::toDomain)

    override suspend fun listAssets(session: WorkspaceSession): List<WorkspaceAsset> =
        client
            .get("$apiBase/workspaces/${session.workspace.id.value}/assets") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
            .body<AssetListDto>()
            .assets
            .map(AssetDto::toDomain)

    override suspend fun getAsset(
        session: WorkspaceSession,
        assetId: String,
    ): WorkspaceAsset {
        require(assetId.isNotBlank()) { "Asset id must not be blank" }
        return client
            .get("$apiBase/workspaces/${session.workspace.id.value}/assets/${assetId.trim()}") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
            .body<AssetDto>()
            .toDomain()
    }

    override suspend fun setWorkspaceMemberRole(
        session: WorkspaceSession,
        userId: String,
        role: WorkspaceMemberRole,
    ) {
        require(userId.isNotBlank()) { "Member user ID must not be blank" }
        require(role != WorkspaceMemberRole.Owner) { "The owner role cannot be assigned" }
        client
            .put("$apiBase/workspaces/${session.workspace.id.value}/members/${userId.trim()}") {
                contentType(ContentType.Application.Json)
                header(DevUserHeader, session.userId)
                setBody(SetWorkspaceMemberRoleRequest(role.token))
            }.requireSuccess()
    }

    override suspend fun removeWorkspaceMember(
        session: WorkspaceSession,
        userId: String,
    ) {
        require(userId.isNotBlank()) { "Member user ID must not be blank" }
        client
            .delete("$apiBase/workspaces/${session.workspace.id.value}/members/${userId.trim()}") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
    }

    override suspend fun refresh(session: WorkspaceSession): WorkspaceSession {
        val cached = projectionCache?.takeIf { it.session == session }
        val metadata =
            client
                .get("$apiBase/workspaces/${session.workspace.id.value}") {
                    header(DevUserHeader, session.userId)
                }.requireSuccess()
                .body<WorkspaceDto>()
        if (metadata.id != session.workspace.id.value) throw BackendContractException("Workspace metadata scope mismatch")
        val role = metadata.role.toWorkspaceMemberRole()
        val response =
            client
                .get("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                    header(DevUserHeader, session.userId)
                    parameter("afterSeq", session.lastServerSeq)
                    parameter("limit", 1000)
                }.requireSuccess()
        val firstPage =
            try {
                replayJson.decodeFromString<FullCatchUpOperationsDto>(response.bodyAsText())
            } catch (_: SerializationException) {
                null
            }
        currentCoroutineContext().ensureActive()
        val catchUp =
            if (cached != null && firstPage != null) {
                collectCommittedCatchUp(
                    session.lastServerSeq,
                    firstPage,
                ) { afterSeq ->
                    currentCoroutineContext().ensureActive()
                    val next =
                        client
                            .get("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                                header(DevUserHeader, session.userId)
                                parameter("afterSeq", afterSeq)
                                parameter("limit", 1000)
                            }.requireSuccess()
                    try {
                        replayJson.decodeFromString<FullCatchUpOperationsDto>(next.bodyAsText())
                    } catch (_: SerializationException) {
                        null
                    }
                }
            } else {
                firstPage
            }
        if (cached != null && catchUp != null && catchUp.lastServerSeq >= metadata.lastServerSeq) {
            val replayed =
                try {
                    reduceCommittedCatchUpPage(cached.state, catchUp)
                } catch (_: BackendContractException) {
                    null
                }
            if (replayed != null && replayed.workspaceVersion >= metadata.currentVersion) {
                val refreshed =
                    session.copy(
                        role = role,
                        workspaceVersion = replayed.workspaceVersion,
                        lastServerSeq = replayed.throughServerSeq,
                        workspace = replayed.toDomainWorkspace(metadata.title),
                    )
                currentCoroutineContext().ensureActive()
                projectionCache = ProjectionCache(refreshed, replayed)
                return refreshed
            }
        }

        return loadWorkspaceState(session.userId, session.workspace.id.value, metadata.title, role)
    }

    override suspend fun submit(
        session: WorkspaceSession,
        operation: WorkspaceOperation,
    ): SubmitOutcome =
        submissionMutex.withLock {
            check(session.workspace.sequenceDiagrams.isEmpty()) { "Backend sequence diagram capability is unavailable" }
            check(session.clientId == preferences.clientId) { "Submission belongs to another client" }
            val scope = submissionScope(session)
            check(draftPersistence.pending(scope) == null) { "An unconfirmed change requires recovery first" }
            draftPersistence.journal(scope)?.let { draft ->
                check(!draft.quarantined && draft.operations.firstOrNull() == operation) { "Saved draft head does not match submission" }
            }
            val expanded = operation.toExpandedDtos(0)
            preferences.deletionEvidence.requireRestoration(scope, expanded, operation)
            val range = sequenceAllocator?.reserve(scope, expanded.size) ?: preferences.reserveClientSequences(expanded.size)
            if (sequenceAllocator != null) preferences.advanceClientSequence(range.last) // Compatibility mirror only.
            val operations = expanded.mapIndexed { index, dto -> dto.copy(clientSeq = range.first + index) }
            val request =
                SubmitOperationsRequest(
                    clientId = session.clientId,
                    transactionId = randomUuid(),
                    baseVersion = session.workspaceVersion,
                    operations = operations,
                )
            val pending =
                PendingWorkspaceSubmission(
                    scope,
                    operation.operationId,
                    request,
                    operation.deletionVersions(),
                    operation.deletionSnapshotDigests(),
                )
            draftPersistence.stage(pending)
            submitWireRequest(session, request).also { outcome ->
                if (outcome is SubmitOutcome.Accepted) {
                    sequenceAllocator?.advance(scope, range.last)
                    preferences.advanceClientSequence(range.last)
                    draftPersistence.acknowledge(pending, outcome.workspaceVersion, outcome.lastServerSeq)
                }
            }
        }

    private fun submissionScope(session: WorkspaceSession) =
        PendingSubmissionScope(apiBase, session.userId, session.clientId, session.workspace.id.value)

    override fun retainDraft(
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) {
        check(session.clientId == preferences.clientId && session.canEditContent)
        check(
            before.sequenceDiagrams.isEmpty() &&
                operation.flatten().none { it is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation },
        ) { "Backend sequence diagram capability is unavailable" }
        check(operation.flatten().none { it is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation }) {
            "Backend canvas style capability is unavailable"
        }
        operation.requireLegacyRelationContract()
        draftPersistence.append(submissionScope(session), session, before, operation)
    }

    override suspend fun pendingDraft(session: WorkspaceSession): PendingWorkspaceDraft? =
        draftPersistence.journal(submissionScope(session))?.takeIf { it.operations.isNotEmpty() }?.let {
            PendingWorkspaceDraft(it.id, it.operations.size, it.quarantined || it.headTransactionId != null)
        }

    override suspend fun restorePendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): RestoredWorkspaceDraft =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            check(draftPersistence.pending(scope) == null) { "Resolve the submitted transaction first" }
            val current = refresh(session)
            currentCoroutineContext().ensureActive()
            check(current.canEditContent)
            val journal = draftPersistence.restore(scope, draftId, current)
            RestoredWorkspaceDraft(current, journal.operations)
        }

    override suspend fun dismissPendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): WorkspaceSession =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            check(draftPersistence.pending(scope) == null) { "Resolve the submitted transaction first" }
            val current = refresh(session)
            currentCoroutineContext().ensureActive()
            check(current.userId == session.userId && current.clientId == session.clientId && current.workspace.id == session.workspace.id)
            check(draftPersistence.remove(scope, draftId))
            current
        }

    override suspend fun reviewPendingDraft(
        session: WorkspaceSession,
        draftId: String,
    ): WorkspaceDraftReview =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            check(draftPersistence.journal(scope)?.id == draftId)
            val current = refresh(session) // Fresh read authorization; viewer may inspect, revoked access fails.
            currentCoroutineContext().ensureActive()
            check(current.userId == session.userId && current.clientId == session.clientId && current.workspace.id == session.workspace.id)
            val journal = checkNotNull(draftPersistence.journal(scope))
            check(journal.id == draftId)
            WorkspaceDraftReview(
                journal.id,
                journal.baseVersion,
                journal.baseServerSeq,
                current.workspaceVersion,
                current.lastServerSeq,
                journal.baseWorkspace,
                journal.replay(),
                current.workspace,
                journal.operations,
                journal.quarantined,
                draftPersistence.pending(scope) != null || journal.headTransactionId != null,
            )
        }

    override suspend fun pendingChange(session: WorkspaceSession): PendingWorkspaceChange? =
        draftPersistence.pending(submissionScope(session))?.let {
            PendingWorkspaceChange(it.request.transactionId, it.request.operations.size, preferences.fenceAttempts.state(it) != null)
        }

    override suspend fun stoppedChanges(session: WorkspaceSession): List<cg.creamgod.boarderless.data.StoppedWorkspaceChange> =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            draftPersistence.stopped(scope).map {
                val evidence = draftPersistence.stoppedEvidence(scope, it.request.transactionId)
                evidence?.validateAgainst(it)
                cg.creamgod.boarderless.data
                    .StoppedWorkspaceChange(it.request.transactionId, it.request.operations.size, evidence != null)
            }
        }

    override suspend fun inspectStoppedReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.PendingSubmissionReceipt =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            val archived = checkNotNull(draftPersistence.stopped(scope).singleOrNull { it.request.transactionId == transactionId })
            val response =
                client
                    .post("$apiBase/workspaces/${session.workspace.id.value}/operations/receipts") {
                        contentType(ContentType.Application.Json)
                        header(DevUserHeader, session.userId)
                        setBody(ReceiptQueryDto(listOf(transactionId)))
                    }.requireSuccess()
                    .body<SubmissionReceiptResponseDto>()
            currentCoroutineContext().ensureActive()
            check(draftPersistence.stopped(scope).singleOrNull { it.request.transactionId == transactionId } == archived)
            // Read only: terminal summary is not an ACK and cannot remove quarantine or authorize Apply.
            response.validatePendingReceipt(session.workspace.id.value, session.userId, archived.request)
        }

    override suspend fun inspectPendingReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.PendingSubmissionReceipt =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId) { "Receipt belongs to another client" }
            val scope = submissionScope(session)
            val pending = checkNotNull(draftPersistence.pending(scope)) { "Pending request is unavailable" }
            check(pending.request.transactionId == transactionId) { "Pending request changed" }
            val response =
                client
                    .post("$apiBase/workspaces/${session.workspace.id.value}/operations/receipts") {
                        contentType(ContentType.Application.Json)
                        header(DevUserHeader, session.userId)
                        setBody(ReceiptQueryDto(listOf(transactionId)))
                    }.requireSuccess()
                    .body<SubmissionReceiptResponseDto>()
            currentCoroutineContext().ensureActive()
            check(draftPersistence.pending(scope) == pending) { "Pending request changed during lookup" }
            response.validatePendingReceipt(session.workspace.id.value, session.userId, pending.request).also {
                preferences.fenceAttempts.observe(pending, it.status)
            }
        }

    override val supportsAtomicStoppedEvidence: Boolean get() = draftPersistence.supportsAtomicStoppedEvidence

    override suspend fun verifyStoppedSubmission(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.StoppedSubmissionVerification =
        submissionMutex.withLock {
            readStoppedVerification(session, transactionId).verification
        }

    override suspend fun saveStoppedSubmissionEvidence(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.StoppedSubmissionVerification =
        submissionMutex.withLock {
            check(supportsAtomicStoppedEvidence) { "Atomic stopped evidence storage is unavailable" }
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            val generation =
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    checkNotNull(draftPersistence.recoveryGeneration(scope))
                }
            val checked = readStoppedVerification(session, transactionId) // Re-query, never trust a UI proof.
            currentCoroutineContext().ensureActive()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                currentCoroutineContext().ensureActive()
                draftPersistence.recordStoppedEvidence(scope, generation, checked.submitted, checked.evidence)
            }
            checked.verification
        }

    private data class CheckedStoppedSubmission(
        val submitted: PendingWorkspaceSubmission,
        val evidence: StoppedSubmissionEvidence,
        val verification: cg.creamgod.boarderless.data.StoppedSubmissionVerification,
    )

    /** Called under submissionMutex; does not itself write any storage. */
    private suspend fun readStoppedVerification(
        session: WorkspaceSession,
        transactionId: String,
    ): CheckedStoppedSubmission {
        check(session.clientId == preferences.clientId)
        val scope = submissionScope(session)
        val archived = checkNotNull(draftPersistence.stopped(scope).singleOrNull { it.request.transactionId == transactionId })
        val response =
            client
                .post("$apiBase/workspaces/${session.workspace.id.value}/operations/receipts") {
                    contentType(ContentType.Application.Json)
                    header(DevUserHeader, session.userId)
                    setBody(ReceiptQueryDto(listOf(transactionId)))
                }.requireSuccess()
                .body<SubmissionReceiptResponseDto>()
        val result = response.validatePendingReceipt(session.workspace.id.value, session.userId, archived.request)
        check(result.status != cg.creamgod.boarderless.data.PendingReceiptStatus.Unknown) { "Original outcome remains unknown" }
        val committed = response.receipts.singleOrNull()
        val acknowledgement = committed?.let { readOriginalCommittedLog(session, archived.request, it) }
        val current = loadSession(session.userId, session.workspace.id.value)
        check(current.userId == session.userId && current.clientId == session.clientId && current.workspace.id == session.workspace.id)
        check(current.workspaceVersion in maxOf(session.workspaceVersion, result.headWorkspaceVersion)..9_007_199_254_740_991L)
        check(current.lastServerSeq in maxOf(session.lastServerSeq, result.headServerSeq)..9_007_199_254_740_991L)
        currentCoroutineContext().ensureActive()
        check(draftPersistence.stopped(scope).singleOrNull { it.request.transactionId == transactionId } == archived)
        // No local ACK, sequence, provenance write, quarantine change or archive deletion.
        val evidence = StoppedSubmissionEvidence(response, acknowledgement, current.workspaceVersion, current.lastServerSeq)
        evidence.validateAgainst(archived)
        return CheckedStoppedSubmission(
            archived,
            evidence,
            cg.creamgod.boarderless.data.StoppedSubmissionVerification(
                transactionId,
                result.status,
                current,
                committed?.workspaceVersion,
                committed?.toServerSeq,
            ),
        )
    }

    private suspend fun readOriginalCommittedLog(
        session: WorkspaceSession,
        request: SubmitOperationsRequest,
        receipt: TransactionReceiptDto,
    ): AcceptedOperationsDto {
        val page =
            client
                .get("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                    header(DevUserHeader, session.userId)
                    parameter("afterSeq", receipt.fromServerSeq - 1)
                    parameter("limit", 200)
                }.requireSuccess()
                .body<FullCatchUpOperationsDto>()
        return validateOriginalCommittedLog(receipt, request, session.userId, page)
    }

    override suspend fun fencePendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.PendingSubmissionReceipt =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId && session.canEditContent) { "Fence requires current edit permission" }
            val scope = submissionScope(session)
            val pending = checkNotNull(draftPersistence.pending(scope))
            check(pending.request.transactionId == transactionId) { "Pending transaction changed" }
            preferences.fenceAttempts.begin(pending)
            val response =
                client
                    .post("$apiBase/workspaces/${session.workspace.id.value}/operations/fences") {
                        contentType(ContentType.Application.Json)
                        header(DevUserHeader, session.userId)
                        setBody(ReceiptQueryDto(listOf(transactionId)))
                    }.requireSuccess()
                    .body<SubmissionReceiptResponseDto>()
            currentCoroutineContext().ensureActive()
            check(draftPersistence.pending(scope) == pending)
            val resolved = response.validatePendingReceipt(session.workspace.id.value, session.userId, pending.request)
            if (resolved.status == cg.creamgod.boarderless.data.PendingReceiptStatus.Unknown) {
                throw BackendContractException("Fence did not establish a terminal original transaction result")
            }
            preferences.fenceAttempts.observe(pending, resolved.status)
            // A concurrent commit wins instead of fence. Neither result clears pending here.
            resolved
        }

    override suspend fun confirmPendingReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            val pending = checkNotNull(draftPersistence.pending(scope))
            check(pending.request.transactionId == transactionId)
            val receiptResponse =
                client
                    .post("$apiBase/workspaces/${session.workspace.id.value}/operations/receipts") {
                        contentType(ContentType.Application.Json)
                        header(DevUserHeader, session.userId)
                        setBody(ReceiptQueryDto(listOf(transactionId)))
                    }.requireSuccess()
                    .body<SubmissionReceiptResponseDto>()
            check(
                receiptResponse.validatePendingReceipt(session.workspace.id.value, session.userId, pending.request).status ==
                    cg.creamgod.boarderless.data.PendingReceiptStatus.Committed,
            ) { "Original transaction is not confirmed committed" }
            val receipt = receiptResponse.receipts.single()
            val ack = readOriginalCommittedLog(session, pending.request, receipt)
            // Fresh ACL/state before local settlement. The receipt's version is not today's head.
            val current = loadSession(session.userId, session.workspace.id.value)
            check(current.workspaceVersion >= receipt.workspaceVersion && current.lastServerSeq >= receipt.toServerSeq)
            check(current.workspaceVersion <= 9_007_199_254_740_991L && current.lastServerSeq <= 9_007_199_254_740_991L)
            currentCoroutineContext().ensureActive()
            check(draftPersistence.pending(scope) == pending)
            preferences.fenceAttempts.observe(pending, cg.creamgod.boarderless.data.PendingReceiptStatus.Committed)
            preferences.deletionEvidence.record(pending, ack)
            sequenceAllocator?.advance(
                scope,
                pending.request.operations
                    .last()
                    .clientSeq,
            )
            preferences.advanceClientSequence(
                pending.request.operations
                    .last()
                    .clientSeq,
            )
            draftPersistence.acknowledge(pending, receipt.workspaceVersion, receipt.toServerSeq)
            current
        }

    override suspend fun retryPendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession {
        check(pendingChange(session)?.transactionId == transactionId) { "Pending change no longer matches" }
        val current = refresh(session)
        retryPendingSubmission(current)
        return refresh(current)
    }

    override suspend fun dismissPendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession =
        submissionMutex.withLock {
            check(pendingChange(session)?.transactionId == transactionId) { "Pending change no longer matches" }
            val current = refresh(session)
            draftPersistence.stop(submissionScope(session), transactionId)
            current
        }

    /** Explicit recovery hook; not automatically called at startup until recovery UI is integrated.
     * Reuses the persisted request without calling the UUID-allocating domain mapper again.
     * A conflict/access error retains the record for deliberate resolution.
     */
    internal suspend fun retryPendingSubmission(session: WorkspaceSession): SubmitOutcome? =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId) { "Pending submission belongs to another client" }
            check(session.role == WorkspaceMemberRole.Owner || session.role == WorkspaceMemberRole.Editor) {
                "Pending submission requires current edit permission"
            }
            val scope = PendingSubmissionScope(apiBase, session.userId, session.clientId, session.workspace.id.value)
            val pending = draftPersistence.pending(scope) ?: return@withLock null
            check(preferences.fenceAttempts.state(pending) == null) { "Fence recovery requires receipt verification, not resend" }
            draftPersistence.stage(pending)
            submitWireRequest(session, pending.request).also { outcome ->
                if (outcome is SubmitOutcome.Accepted) {
                    sequenceAllocator?.advance(
                        scope,
                        pending.request.operations
                            .last()
                            .clientSeq,
                    )
                    preferences.advanceClientSequence(
                        pending.request.operations
                            .last()
                            .clientSeq,
                    )
                    draftPersistence.acknowledge(pending, outcome.workspaceVersion, outcome.lastServerSeq)
                }
            }
        }

    private suspend fun submitWireRequest(
        session: WorkspaceSession,
        request: SubmitOperationsRequest,
    ): SubmitOutcome {
        val response =
            client.post("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                contentType(ContentType.Application.Json)
                header(DevUserHeader, session.userId)
                setBody(request)
            }

        return when (response.status) {
            HttpStatusCode.OK -> {
                val accepted = response.body<AcceptedOperationsDto>()
                accepted.validateAcknowledgement(request, session.userId)
                val pending = checkNotNull(draftPersistence.pending(submissionScope(session)))
                check(pending.request == request)
                preferences.deletionEvidence.record(pending, accepted)
                SubmitOutcome.Accepted(
                    workspaceVersion = accepted.workspaceVersion,
                    lastServerSeq = accepted.toServerSeq,
                )
            }

            HttpStatusCode.Conflict -> {
                val conflict = response.body<ConflictOperationsDto>()
                val current = loadSession(session.userId, session.workspace.id.value)
                SubmitOutcome.Conflict(
                    current.copy(
                        workspaceVersion = conflict.workspaceVersion,
                        lastServerSeq = conflict.lastServerSeq,
                    ),
                )
            }

            else -> {
                throw response.toException()
            }
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun createSession(): WorkspaceSession {
        val user =
            client
                .post("$apiBase/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserRequest(Strings.content.defaultUserName()))
                }.requireSuccess()
                .body<UserDto>()
        preferences.userId = user.id
        return createWorkspaceForUser(user.id, Strings.content.myThinkingSpace())
    }

    private suspend fun createWorkspaceForUser(
        userId: String,
        title: String,
    ): WorkspaceSession {
        val workspace =
            client
                .post("$apiBase/workspaces") {
                    contentType(ContentType.Application.Json)
                    header(DevUserHeader, userId)
                    setBody(CreateWorkspaceRequest(title))
                }.requireSuccess()
                .body<WorkspaceDto>()
        return loadAndRemember(userId, workspace.id)
    }

    private suspend fun listWorkspaceDtos(userId: String): List<WorkspaceDto> =
        client
            .get("$apiBase/workspaces") {
                header(DevUserHeader, userId)
            }.requireSuccess()
            .body<WorkspaceListDto>()
            .workspaces

    private suspend fun loadAndRemember(
        userId: String,
        workspaceId: String,
    ): WorkspaceSession {
        val loaded = loadSession(userId, workspaceId)
        preferences.userId = userId
        preferences.workspaceId = workspaceId
        return loaded
    }

    private suspend fun loadSession(
        userId: String,
        workspaceId: String,
    ): WorkspaceSession {
        val metadata =
            client
                .get("$apiBase/workspaces/$workspaceId") {
                    header(DevUserHeader, userId)
                }.requireSuccess()
                .body<WorkspaceDto>()
        if (metadata.id != workspaceId) throw BackendContractException("Workspace metadata scope mismatch")
        return loadWorkspaceState(userId, workspaceId, metadata.title, metadata.role.toWorkspaceMemberRole())
    }

    private suspend fun loadWorkspaceState(
        userId: String,
        workspaceId: String,
        title: String,
        role: WorkspaceMemberRole,
    ): WorkspaceSession {
        val state =
            client
                .get("$apiBase/workspaces/$workspaceId/state") {
                    header(DevUserHeader, userId)
                }.requireSuccess()
                .body<JsonObject>()
                .let(::decodeLegacyWorkspaceState)
        if (state.workspaceId != workspaceId) throw BackendContractException("Workspace projection scope mismatch")
        val loaded =
            WorkspaceSession(
                userId = userId,
                clientId = preferences.clientId,
                role = role,
                workspaceVersion = state.workspaceVersion,
                lastServerSeq = state.throughServerSeq,
                workspace = state.toDomainWorkspace(title),
            )
        currentCoroutineContext().ensureActive()
        projectionCache = ProjectionCache(loaded, state)
        return loaded
    }

    private suspend fun HttpResponse.requireSuccess(): HttpResponse {
        if (status.value in 200..299) return this
        throw toException()
    }

    private suspend fun HttpResponse.toException(): BackendHttpException =
        BackendHttpException(
            status = status,
            responseBody = bodyAsText(),
        )

    private companion object {
        const val DevUserHeader = "x-user-id"

        fun createHttpClient(): HttpClient =
            cg.creamgod.boarderless.data.platformHttpClient {
                expectSuccess = false
                install(io.ktor.client.plugins.HttpTimeout) {
                    requestTimeoutMillis = 30_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            encodeDefaults = true
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        },
                    )
                }
            }
    }
}

class BackendHttpException(
    val status: HttpStatusCode,
    val responseBody: String,
) : Exception("Backend request failed with HTTP ${status.value}: $responseBody")

internal val BackendHttpException.isWorkspaceAccessLoss: Boolean
    get() =
        status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ||
            status == HttpStatusCode.NotFound

/** Failed reconciliation cannot leave old write permissions active after an access denial. */
internal fun requiresWorkspaceReconnectAfterFailedSubmission(
    submitError: Throwable,
    refreshError: Throwable,
): Boolean =
    submitError !is BackendHttpException || submitError.isWorkspaceAccessLoss ||
        (refreshError as? BackendHttpException)?.isWorkspaceAccessLoss == true

internal expect fun defaultBackendBaseUrl(): String

internal fun configuredBackendBaseUrl(
    configured: String?,
    fallback: String,
): String {
    fun String.validBaseUrlOrNull(): String? {
        val normalized = trim().trimEnd('/')
        if (normalized.isEmpty() || normalized.contains("${'$'}(")) return null
        val schemeSeparator = normalized.indexOf("://")
        if (schemeSeparator <= 0) return null
        val scheme = normalized.substring(0, schemeSeparator).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = normalized.substring(schemeSeparator + 3).substringBefore('/')
        return normalized.takeIf { authority.isNotBlank() }
    }

    return configured?.validBaseUrlOrNull()
        ?: fallback.validBaseUrlOrNull()
        ?: error("Backend fallback URL must be an absolute HTTP(S) URL")
}

internal fun recentActivityAfterSeq(
    lastServerSeq: Long,
    limit: Int,
): Long {
    require(lastServerSeq >= 0) { "Last server sequence must not be negative" }
    require(limit in 1..1000) { "Activity limit must be between 1 and 1000" }
    return (lastServerSeq - limit).coerceAtLeast(0)
}

internal fun WorkspaceMemberDto.toDomain(): WorkspaceMember {
    if (userId.isBlank() || displayName.isBlank() || joinedAt.isBlank()) {
        throw BackendContractException("Workspace member contains a blank required field")
    }
    val parsedRole =
        WorkspaceMemberRole.fromToken(role)
            ?: throw BackendContractException("Workspace member has unsupported role '$role'")
    return WorkspaceMember(
        userId = userId,
        displayName = displayName,
        role = parsedRole,
        joinedAt = joinedAt,
    )
}

internal fun AssetDto.toDomain(): WorkspaceAsset {
    if (id.isBlank() || workspaceId.isBlank() || ownerId.isBlank() || storageKey.isBlank() ||
        mediaType.isBlank() || checksum.isBlank() || createdAt.isBlank()
    ) {
        throw BackendContractException("Asset metadata contains a blank required field")
    }
    val parsedStatus =
        AssetStatus.fromToken(status)
            ?: throw BackendContractException("Asset has unsupported status '$status'")
    return try {
        WorkspaceAsset(
            id = id,
            workspaceId = WorkspaceId(workspaceId),
            ownerId = ownerId,
            mediaType = mediaType,
            byteSize = byteSize,
            checksum = checksum,
            width = width,
            height = height,
            durationMs = durationMs,
            status = parsedStatus,
            createdAt = createdAt,
            thumbnailAssetId = thumbnailAssetId,
            rejectionReason =
                cg.creamgod.boarderless.data.AssetRejectionReason
                    .fromToken(rejectionReason),
        )
    } catch (error: IllegalArgumentException) {
        throw BackendContractException("Asset $id contains invalid metadata", error)
    }
}

internal fun String.toWorkspaceMemberRole(): WorkspaceMemberRole =
    WorkspaceMemberRole.fromToken(this)
        ?: throw BackendContractException("Workspace has unsupported role '$this'")

private fun WorkspaceOperation.requireLegacyRelationContract() {
    flatten().forEach { operation ->
        check(operation !is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation) {
            "Backend sequence diagram capability is unavailable"
        }
        val vectorSupported =
            when (operation) {
                is CreateObjectsOperation -> {
                    operation.objects.none { (it as? TextNode)?.vectorPath != null }
                }

                is DeleteObjectsOperation -> {
                    operation.objects.none { (it as? TextNode)?.vectorPath != null }
                }

                is UpdateTextNodeAttributesOperation -> {
                    operation.changes.all {
                        it.before.vectorPath == null && it.after.vectorPath == null
                    }
                }

                else -> {
                    true
                }
            }
        check(vectorSupported) { "Backend vector path capability is unavailable" }
        val supported =
            when (operation) {
                is CreateRelationsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is DeleteObjectsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is DeleteRelationsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is UpdateRelationAttributesOperation -> operation.changes.all { it.before.geometry == null && it.after.geometry == null }
                else -> true
            }
        check(supported) { "Backend relation geometry capability is unavailable" }
    }
}

private fun WorkspaceOperation.flatten(): List<WorkspaceOperation> =
    when (this) {
        is TransactionOperation -> operations.flatMap { it.flatten() }
        else -> listOf(this)
    }

private fun WorkspaceOperation.toDto(clientSequence: Long): OperationDto =
    when (this) {
        is CreateObjectsOperation -> {
            error("Create operations must be expanded before conversion")
        }

        is CreateRelationsOperation -> {
            error("Create relation operations must be expanded before conversion")
        }

        is DeleteObjectsOperation -> {
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "delete_objects",
                expectedObjectVersions = objects.associate { it.id.value to it.version },
                payload =
                    buildJsonObject {
                        put("objectIds", kotlinx.serialization.json.JsonArray(objects.map { JsonPrimitive(it.id.value) }))
                    },
            )
        }

        is DeleteRelationsOperation -> {
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "delete_relations",
                expectedObjectVersions = relations.associate { it.id.value to it.version },
                payload =
                    buildJsonObject {
                        put("relationIds", kotlinx.serialization.json.JsonArray(relations.map { JsonPrimitive(it.id.value) }))
                    },
            )
        }

        is UpdateRelationAttributesOperation -> {
            require(changes.size == 1) { "Backend update_relation mapping currently accepts one relation change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_relation",
                expectedObjectVersions = mapOf(change.relationId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("relationId", JsonPrimitive(change.relationId.value))
                        put("direction", JsonPrimitive(change.after.direction.toBackendValue()))
                        put("intent", change.after.intent?.let(::JsonPrimitive) ?: JsonNull)
                        put("label", change.after.label?.let(::JsonPrimitive) ?: JsonNull)
                        put(
                            "style",
                            buildJsonObject { put("colorToken", JsonPrimitive(change.after.colorToken)) },
                        )
                    },
            )
        }

        is TransformObjectsOperation -> {
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "move_objects",
                expectedObjectVersions = changes.associate { it.objectId.value to it.expectedVersion },
                payload =
                    buildJsonObject {
                        put(
                            "moves",
                            kotlinx.serialization.json.JsonArray(
                                changes.map { change ->
                                    buildJsonObject {
                                        put("objectId", JsonPrimitive(change.objectId.value))
                                        put("transform", change.after.toJson())
                                    }
                                },
                            ),
                        )
                    },
            )
        }

        is ReparentObjectsOperation -> {
            require(changes.size == 1) { "Backend update_object mapping currently accepts one parent change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        put("parentId", change.after?.let { JsonPrimitive(it.value) } ?: JsonNull)
                    },
            )
        }

        is EditTextOperation -> {
            require(changes.size == 1) { "Backend update_object mapping currently accepts one text change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        put("properties", buildJsonObject { put("text", JsonPrimitive(change.after)) })
                    },
            )
        }

        is UpdateTextNodeAttributesOperation -> {
            require(changes.size == 1) { "Backend update_object mapping currently accepts one attribute change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        if (change.before.zIndex != change.after.zIndex) {
                            put("zIndex", JsonPrimitive(change.after.zIndex))
                        }
                        if (change.before.locked != change.after.locked) {
                            put("locked", JsonPrimitive(change.after.locked))
                        }
                        val properties =
                            buildJsonObject {
                                if (change.before.colorToken != change.after.colorToken) {
                                    put("colorToken", JsonPrimitive(change.after.colorToken))
                                }
                                if (change.before.shape != change.after.shape) {
                                    put("shapeToken", JsonPrimitive(change.after.shape.token))
                                }
                            }
                        if (properties.isNotEmpty()) put("properties", properties)
                    },
            )
        }

        is UpdateGroupFrameAttributesOperation -> {
            require(changes.size == 1) { "Backend update_object mapping currently accepts one group attribute change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        if (change.before.zIndex != change.after.zIndex) put("zIndex", JsonPrimitive(change.after.zIndex))
                        if (change.before.locked != change.after.locked) put("locked", JsonPrimitive(change.after.locked))
                        val properties =
                            buildJsonObject {
                                if (change.before.colorToken != change.after.colorToken) {
                                    put("colorToken", JsonPrimitive(change.after.colorToken))
                                }
                                if (change.before.title != change.after.title) {
                                    put("title", JsonPrimitive(change.after.title))
                                }
                            }
                        if (properties.isNotEmpty()) put("properties", properties)
                    },
            )
        }

        is UpdateMediaReferenceOperation -> {
            require(changes.size == 1)
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        put(
                            "properties",
                            buildJsonObject {
                                put("assetId", JsonPrimitive(change.after.assetId))
                                put("mediaKind", JsonPrimitive(change.after.mediaKind.token))
                                // Explicit null clears the old thumbnail under the server's shallow merge.
                                put("thumbnailAssetId", change.after.thumbnailAssetId?.let(::JsonPrimitive) ?: JsonNull)
                            },
                        )
                    },
            )
        }

        is UpdateMediaNodeAttributesOperation -> {
            require(changes.size == 1) { "Backend update_object mapping currently accepts one media change" }
            val change = changes.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = clientSequence,
                kind = "update_object",
                expectedObjectVersions = mapOf(change.objectId.value to change.expectedVersion),
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(change.objectId.value))
                        if (change.before.zIndex != change.after.zIndex) put("zIndex", JsonPrimitive(change.after.zIndex))
                        if (change.before.locked != change.after.locked) put("locked", JsonPrimitive(change.after.locked))
                        if (change.before.altText != change.after.altText) {
                            put("properties", buildJsonObject { put("altText", JsonPrimitive(change.after.altText)) })
                        }
                    },
            )
        }

        is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation -> {
            error("Backend sequence diagram capability is unavailable")
        }

        is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation -> {
            error("Backend canvas style capability is unavailable")
        }

        is TransactionOperation -> {
            error("Transactions must be flattened before conversion")
        }
    }

private fun CreateObjectsOperation.expand(): List<WorkspaceOperation> =
    objects.map { canvasObject ->
        CreateObjectsOperation(operationId, listOf(canvasObject))
    }

private fun CreateRelationsOperation.expand(): List<WorkspaceOperation> =
    relations.map { relation ->
        CreateRelationsOperation(operationId, listOf(relation))
    }

private fun UpdateTextNodeAttributesOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        UpdateTextNodeAttributesOperation(operationId, listOf(change))
    }

private fun UpdateRelationAttributesOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        UpdateRelationAttributesOperation(operationId, listOf(change))
    }

private fun UpdateGroupFrameAttributesOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        UpdateGroupFrameAttributesOperation(operationId, listOf(change))
    }

private fun UpdateMediaNodeAttributesOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        UpdateMediaNodeAttributesOperation(operationId, listOf(change))
    }

private fun ReparentObjectsOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        ReparentObjectsOperation(operationId, listOf(change))
    }

private fun EditTextOperation.expand(): List<WorkspaceOperation> =
    changes.map { change ->
        EditTextOperation(operationId, listOf(change))
    }

private fun WorkspaceOperation.flattenAndExpand(): List<WorkspaceOperation> =
    flatten().flatMap {
        when (it) {
            is CreateObjectsOperation -> if (it.restoreVersions.isEmpty()) it.expand() else listOf(it)
            is CreateRelationsOperation -> if (it.restoreVersions.isEmpty()) it.expand() else listOf(it)
            is UpdateTextNodeAttributesOperation -> it.expand()
            is UpdateRelationAttributesOperation -> it.expand()
            is UpdateGroupFrameAttributesOperation -> it.expand()
            is UpdateMediaNodeAttributesOperation -> it.expand()
            is UpdateMediaReferenceOperation -> it.changes.map { change -> it.copy(changes = listOf(change)) }
            is ReparentObjectsOperation -> it.expand()
            is EditTextOperation -> it.expand()
            else -> listOf(it)
        }
    }

internal fun WorkspaceOperation.toExpandedDtos(startingSequence: Long): List<OperationDto> =
    also { it.requireLegacyRelationContract() }.flattenAndExpand().mapIndexed { index, operation ->
        val restoration =
            when (operation) {
                is CreateObjectsOperation -> {
                    operation.restoreVersions.takeIf { it.isNotEmpty() }?.let {
                        "restore_objects" to ("objectIds" to it)
                    }
                }

                is CreateRelationsOperation -> {
                    operation.restoreVersions.takeIf { it.isNotEmpty() }?.let {
                        "restore_relations" to ("relationIds" to it)
                    }
                }

                else -> {
                    null
                }
            }
        if (restoration != null) {
            val (kind, idsAndVersions) = restoration
            val (key, versions) = idsAndVersions
            require(versions.size in 1..500)
            OperationDto(
                randomUuid(),
                startingSequence + index + 1,
                kind,
                expectedObjectVersions = versions,
                payload =
                    buildJsonObject {
                        put(key, kotlinx.serialization.json.JsonArray(versions.keys.map(::JsonPrimitive)))
                    },
            )
        } else if (operation is CreateObjectsOperation) {
            val canvasObject = operation.objects.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = startingSequence + index + 1,
                kind = "create_object",
                payload =
                    buildJsonObject {
                        put("objectId", JsonPrimitive(canvasObject.id.value))
                        put(
                            "objectType",
                            JsonPrimitive(
                                when (canvasObject) {
                                    is TextNode -> "text"
                                    is GroupFrame -> "group"
                                    is MediaNode -> "media"
                                },
                            ),
                        )
                        canvasObject.parentId?.let { put("parentId", JsonPrimitive(it.value)) }
                        put("zIndex", JsonPrimitive(canvasObject.zIndex))
                        put("locked", JsonPrimitive(canvasObject.locked))
                        put("transform", canvasObject.transform.toJson())
                        put(
                            "properties",
                            buildJsonObject {
                                when (canvasObject) {
                                    is TextNode -> {
                                        put("text", JsonPrimitive(canvasObject.text))
                                        put("colorToken", JsonPrimitive(canvasObject.colorToken))
                                        put("shapeToken", JsonPrimitive(canvasObject.shape.token))
                                    }

                                    is GroupFrame -> {
                                        put("title", JsonPrimitive(canvasObject.title))
                                        put("colorToken", JsonPrimitive(canvasObject.colorToken))
                                    }

                                    is MediaNode -> {
                                        put("assetId", JsonPrimitive(canvasObject.assetId))
                                        put("mediaKind", JsonPrimitive(canvasObject.mediaKind.token))
                                        put("altText", JsonPrimitive(canvasObject.altText))
                                        canvasObject.thumbnailAssetId?.let {
                                            put("thumbnailAssetId", JsonPrimitive(it))
                                        }
                                    }
                                }
                            },
                        )
                    },
            )
        } else if (operation is CreateRelationsOperation) {
            val relation = operation.relations.single()
            OperationDto(
                operationId = randomUuid(),
                clientSeq = startingSequence + index + 1,
                kind = "create_relation",
                payload =
                    buildJsonObject {
                        put("relationId", JsonPrimitive(relation.id.value))
                        put("sourceObjectId", JsonPrimitive(relation.sourceObjectId.value))
                        put("targetObjectId", JsonPrimitive(relation.targetObjectId.value))
                        put("direction", JsonPrimitive(relation.direction.toBackendValue()))
                        relation.intent?.let { put("intent", JsonPrimitive(it)) }
                        relation.label?.let { put("label", JsonPrimitive(it)) }
                        put(
                            "style",
                            buildJsonObject { put("colorToken", JsonPrimitive(relation.colorToken)) },
                        )
                    },
            )
        } else {
            operation.toDto(startingSequence + index + 1)
        }
    }

private fun CanvasTransform.toJson(): JsonObject =
    buildJsonObject {
        put("x", JsonPrimitive(position.x))
        put("y", JsonPrimitive(position.y))
        put("width", JsonPrimitive(size.width))
        put("height", JsonPrimitive(size.height))
        put("rotationDegrees", JsonPrimitive(rotationDegrees))
    }

internal fun WorkspaceStateDto.toDomainWorkspace(title: String): Workspace {
    if (workspaceVersion < 0 || throughServerSeq < 0) {
        throw BackendContractException("Workspace projection contains a negative version or server sequence")
    }
    if (objects.map(CanvasObjectDto::objectId).distinct().size != objects.size) {
        throw BackendContractException("Workspace projection contains duplicate object IDs")
    }
    if (relations.map(RelationDto::relationId).distinct().size != relations.size) {
        throw BackendContractException("Workspace projection contains duplicate relation IDs")
    }
    val domainObjects =
        objects.map { dto ->
            if (dto.objectVersion < 1) {
                throw BackendContractException("Object ${dto.objectId} has invalid version ${dto.objectVersion}")
            }
            val transform =
                try {
                    CanvasTransform(
                        position =
                            Vec2(
                                x = dto.transform.float("x", 0f),
                                y = dto.transform.float("y", 0f),
                            ),
                        size =
                            CanvasSize(
                                width = dto.transform.float("width", 260f),
                                height = dto.transform.float("height", 132f),
                            ),
                        rotationDegrees = dto.transform.float("rotationDegrees", 0f),
                    )
                } catch (error: BackendContractException) {
                    throw error
                } catch (error: IllegalArgumentException) {
                    throw BackendContractException("Object ${dto.objectId} has an invalid transform", error)
                }
            if ("vectorPath" in dto.properties) throw BackendContractException("Vector path requires negotiated backend support")
            try {
                when (dto.objectType) {
                    "text" -> {
                        TextNode(
                            id = CanvasObjectId(dto.objectId),
                            version = dto.objectVersion,
                            parentId = dto.parentId?.let(::CanvasObjectId),
                            zIndex = dto.zIndex,
                            locked = dto.locked,
                            transform = transform,
                            text = dto.properties.string("text") ?: Strings.content.untitledThought(),
                            colorToken = dto.properties.string("colorToken") ?: "paper",
                            shape =
                                dto.properties.string("shapeToken")?.let { token ->
                                    NodeShape.fromToken(token) ?: throw BackendContractException(
                                        "Object ${dto.objectId} has unsupported node shape '$token'",
                                    )
                                } ?: NodeShape.RoundedRectangle,
                        )
                    }

                    "group" -> {
                        GroupFrame(
                            id = CanvasObjectId(dto.objectId),
                            version = dto.objectVersion,
                            parentId = dto.parentId?.let(::CanvasObjectId),
                            zIndex = dto.zIndex,
                            locked = dto.locked,
                            transform = transform,
                            title = dto.properties.string("title") ?: Strings.objects.group(),
                            colorToken = dto.properties.string("colorToken") ?: "group",
                        )
                    }

                    "media" -> {
                        MediaNode(
                            id = CanvasObjectId(dto.objectId),
                            version = dto.objectVersion,
                            parentId = dto.parentId?.let(::CanvasObjectId),
                            zIndex = dto.zIndex,
                            locked = dto.locked,
                            transform = transform,
                            assetId = dto.properties.requiredString("assetId", dto.objectId),
                            mediaKind =
                                dto.properties.requiredString("mediaKind", dto.objectId).let { token ->
                                    MediaKind.fromToken(token) ?: throw BackendContractException(
                                        "Object ${dto.objectId} has unsupported media kind '$token'",
                                    )
                                },
                            altText = dto.properties.string("altText").orEmpty(),
                            thumbnailAssetId = dto.properties.string("thumbnailAssetId"),
                        )
                    }

                    else -> {
                        throw BackendContractException(
                            "Unsupported canvas object type '${dto.objectType}' for object ${dto.objectId}",
                        )
                    }
                }
            } catch (error: BackendContractException) {
                throw error
            } catch (error: IllegalArgumentException) {
                // Domain constructors reject blank references and invalid object invariants. Keep
                // malformed projections on the same fail-closed boundary as malformed JSON fields,
                // without echoing arbitrary properties/credentials into the presentation message.
                throw BackendContractException("Object ${dto.objectId} has invalid properties", error)
            }
        }
    val objectMap = domainObjects.associateBy { it.id }
    for (canvasObject in domainObjects) {
        val parentId = canvasObject.parentId ?: continue
        val parent =
            objectMap[parentId]
                ?: throw BackendContractException(
                    "Object ${canvasObject.id.value} references parent ${parentId.value} missing from the projection",
                )
        if (parent !is GroupFrame) {
            throw BackendContractException(
                "Object ${canvasObject.id.value} references non-group parent ${parentId.value}",
            )
        }
        val visited = mutableSetOf(canvasObject.id)
        var ancestor: CanvasObject? = parent
        while (ancestor != null) {
            if (!visited.add(ancestor.id)) {
                throw BackendContractException("Workspace projection contains a parent cycle at ${canvasObject.id.value}")
            }
            ancestor = ancestor.parentId?.let(objectMap::get)
        }
    }
    val domainRelations =
        relations.map { dto ->
            if ("geometry" in dto.style) throw BackendContractException("Relation geometry requires negotiated backend support")
            if (dto.relationVersion < 1) {
                throw BackendContractException("Relation ${dto.relationId} has invalid version ${dto.relationVersion}")
            }
            val sourceId = CanvasObjectId(dto.sourceObjectId)
            val targetId = CanvasObjectId(dto.targetObjectId)
            if (sourceId == targetId) {
                throw BackendContractException("Relation ${dto.relationId} connects an object to itself")
            }
            if (sourceId !in objectMap || targetId !in objectMap) {
                throw BackendContractException(
                    "Relation ${dto.relationId} references an object missing from the projection",
                )
            }
            Relation(
                id = RelationId(dto.relationId),
                version = dto.relationVersion,
                sourceObjectId = sourceId,
                targetObjectId = targetId,
                direction = dto.direction.toDomainDirection(),
                intent = dto.intent,
                label = dto.label,
                colorToken = dto.style.string("colorToken") ?: "relation",
            )
        }
    return Workspace(
        id = WorkspaceId(workspaceId),
        title = title,
        version = workspaceVersion,
        objects = objectMap,
        relations = domainRelations.associateBy { it.id },
    )
}

private fun RelationDirection.toBackendValue(): String =
    when (this) {
        RelationDirection.None -> "none"
        RelationDirection.Forward -> "forward"
        RelationDirection.Backward -> "backward"
        RelationDirection.Both -> "both"
    }

internal fun String.toDomainDirection(): RelationDirection =
    when (this) {
        "none" -> RelationDirection.None
        "forward" -> RelationDirection.Forward
        "backward" -> RelationDirection.Backward
        "both" -> RelationDirection.Both
        else -> throw BackendContractException("Unsupported relation direction '$this'")
    }

internal class BackendContractException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

private fun WorkspaceDto.toSummary(): WorkspaceSummary =
    WorkspaceSummary(
        id = WorkspaceId(id),
        title = title,
        role = role,
        workspaceVersion = currentVersion,
        lastServerSeq = lastServerSeq,
    )

private fun JsonObject.float(
    key: String,
    default: Float,
): Float {
    val element = this[key] ?: return default
    val value = (element as? JsonPrimitive)?.floatOrNull
    return value?.takeIf(Float::isFinite)
        ?: throw BackendContractException("Projection field '$key' must be a finite number")
}

private fun JsonObject.string(key: String): String? {
    val element = this[key] ?: return null
    val primitive = element as? JsonPrimitive
    return primitive?.takeIf(JsonPrimitive::isString)?.contentOrNull
        ?: throw BackendContractException("Projection field '$key' must be a string")
}

private fun JsonObject.requiredString(
    key: String,
    objectId: String,
): String =
    string(key)?.takeIf(String::isNotBlank)
        ?: throw BackendContractException("Object $objectId requires non-blank property '$key'")

internal fun decodeLegacyWorkspaceState(raw: JsonObject): WorkspaceStateDto {
    if ("sequenceDiagrams" in raw || "sequenceDiagramsVersion" in raw) {
        throw BackendContractException("Sequence diagrams require negotiated backend support")
    }
    return Json { ignoreUnknownKeys = true }.decodeFromJsonElement(WorkspaceStateDto.serializer(), raw)
}
