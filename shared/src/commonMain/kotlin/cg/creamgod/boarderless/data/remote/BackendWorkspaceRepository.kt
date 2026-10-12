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
import cg.creamgod.boarderless.domain.history.mediaReference
import cg.creamgod.boarderless.domain.history.historyTargetSide
import cg.creamgod.boarderless.domain.history.MediaReferenceHistoryOrigin
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
import cg.creamgod.boarderless.domain.model.VectorPath
import cg.creamgod.boarderless.domain.model.TextPlacement
import cg.creamgod.boarderless.domain.model.RelationGeometry
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.domain.model.Workspace
import cg.creamgod.boarderless.domain.model.WorkspaceId
import cg.creamgod.boarderless.i18n.Strings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
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
import kotlinx.serialization.json.longOrNull

class BackendWorkspaceRepository(
    baseUrl: String = defaultBackendBaseUrl(),
    private val preferences: SessionPreferences = SessionPreferences(),
    private val client: HttpClient = createHttpClient(),
    private val realtimeRuntime: RealtimeCheckpointRuntime? = null,
    private val awaitedSubmissionRuntime: AwaitedSubmissionRuntime? = null,
    private val requireServerWriterActive: () -> Unit = {},
    private val prepareServerWriter: suspend () -> Unit = {},
    private val releaseServerWriter: () -> Unit = {},
    private val serverWriterOperations: ServerWriterOperationLease? = null,
    private val mediaReferenceProofRuntime: MediaReferenceProofRuntime? = null,
    override val quickSchemeRepository: cg.creamgod.boarderless.data.persistence.QuickSchemeRepository? = null,
    override val legacyQuickSchemeReader: (suspend () -> List<cg.creamgod.boarderless.data.persistence.QuickScheme>)? = null,
) : cg.creamgod.boarderless.data.CanvasWorkspaceRepository {
    override val collaborationTransport = cg.creamgod.boarderless.data.WorkspaceCollaborationTransport.RestPolling
    override val maximumAtomicOperations: Int get() = BackendMaximumAtomicOperations
    override fun submissionOperationCount(operation: WorkspaceOperation): Int = operation.backendOperationCount()
    override val cloudQuickSchemeServer = cg.creamgod.boarderless.data.persistence.CloudSchemeServer(baseUrl)
    private val apiBase = "${baseUrl.trimEnd('/')}/api/v1"
    override val syncAssetGatewayFactory: () -> cg.creamgod.boarderless.data.AssetTransferGateway = { BackendAssetTransferGateway(baseUrl) }
    override val syncAssetPreparationGatewayFactory: () -> BackendAssetPreparationGateway = { BackendAssetPreparationGateway(baseUrl) }
    override val syncServerScope = org.kotlincrypto.hash.sha2.SHA256().digest(apiBase.encodeToByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    private val submissionMutex = preferences.submissionMutex
    private val legacyDraftPersistence = preferences.draftPersistence
    private val draftPersistence = AwaitedDraftBoundary(legacyDraftPersistence, awaitedSubmissionRuntime?.drafts, serverWriterOperations)
    private val recoverySafety = AwaitedRecoveryBoundary(preferences.fenceAttempts, preferences.deletionEvidence, awaitedSubmissionRuntime?.safety, serverWriterOperations)
    /** Construction/adoption proof only, not a claim that this repository owns the writer
     * yet. Its normal prepareServerWriter must still acquire and verify platform ownership. */
    internal fun requireActivatedSubmissionRuntime(runtime: AwaitedSubmissionRuntime, scope: PendingSubmissionScope) {
        check(awaitedSubmissionRuntime === runtime)
        check(scope.apiBase == apiBase && scope.userId == preferences.userId && scope.clientId == preferences.clientId &&
            scope.workspaceId == preferences.workspaceId)
        runtime.requireActive(scope)
    }
    private val sequenceAllocator = awaitedSubmissionRuntime?.sequences ?: preferences.sequenceAllocator?.let { AwaitedLegacyClientSequenceAllocator(it, serverWriterOperations) }
    private val mediaReferenceCommits by lazy { mediaReferenceProofRuntime?.store { scope ->
        requireServerWriterActive()
        awaitedSubmissionRuntime?.requireActive?.invoke(scope)
        check(scope.apiBase == apiBase && scope.userId == preferences.userId && scope.clientId == preferences.clientId)
    } }
    private val confirmedHistories by lazy { mediaReferenceProofRuntime?.historyStore { scope ->
        requireServerWriterActive()
        awaitedSubmissionRuntime?.requireActive?.invoke(scope)
        check(scope.apiBase == apiBase && scope.userId == preferences.userId && scope.clientId == preferences.clientId)
    } }

    override fun preparedMediaImport(session: WorkspaceSession, canContinue: () -> Boolean): cg.creamgod.boarderless.data.PreparedMediaImportController? {
        val runtime = mediaReferenceProofRuntime ?: return null
        val store = runtime.pickedAssetUploads { scope ->
            requireServerWriterActive()
            check(scope.serverScope == syncServerScope && scope.userId == preferences.userId &&
                scope.clientId == preferences.clientId && scope.workspaceId.value == preferences.workspaceId)
            check(canContinue())
        }
        val gateway = BackendAssetPreparationGateway(baseUrl = apiBase.removeSuffix("/api/v1"))
        return cg.creamgod.boarderless.data.PreparedMediaImportController(session, store, gateway,
            BackendAssetTransferGateway(apiBase.removeSuffix("/api/v1")), this, canContinue)
    }

    override suspend fun restoreConfirmedHistory(session: WorkspaceSession): cg.creamgod.boarderless.domain.history.WorkspaceHistory? =
        submissionMutex.withLock { loadConfirmedHistoryLocked(session) }

    private suspend fun loadConfirmedHistoryLocked(session: WorkspaceSession): cg.creamgod.boarderless.domain.history.WorkspaceHistory? {
        val store = confirmedHistories ?: return null
        val scope = submissionScope(session)
        check(preferences.userId == session.userId && preferences.clientId == session.clientId &&
            preferences.workspaceId == session.workspace.id.value)
        val saved = store.readDurable(scope) ?: return null
        currentCoroutineContext().ensureActive()
        check(submissionScope(session) == scope && preferences.userId == session.userId &&
            preferences.workspaceId == session.workspace.id.value && preferences.clientId == session.clientId)
        require(saved.value.workspaceVersion <= session.workspaceVersion && saved.value.lastServerSeq <= session.lastServerSeq) {
            "Server state is older than the confirmed history checkpoint"
        }
        // Keep local stack intentions but use current server objects, lock/version and ACL.
        // Undo/Redo rechecks their normal preconditions; no replay or network write here.
        return saved.value.history.copy(workspace = session.workspace)
    }
    override val awaitsDraftPublication: Boolean get() = awaitedSubmissionRuntime != null || legacyDraftPersistence.supportsAtomicStoppedEvidence
    override val awaitsHistoryCommit: Boolean get() = mediaReferenceProofRuntime != null

    private data class ProjectionCache(
        val session: WorkspaceSession,
        val state: WorkspaceStateDto,
    )

    // One authoritative raw snapshot only. Optimistic/domain-only sessions cannot reuse it.
    private var projectionCache: ProjectionCache? = null
    private var canvasReadCapabilities = BackendCanvasReadCapabilities()
    override val supportsCanvasStyle: Boolean get() = canvasReadCapabilities.canvasStyleWrite
    override val supportsRelationGeometry: Boolean get() = canvasReadCapabilities.geometry && canvasReadCapabilities.canWriteCanvas
    override val supportsVectorPaths: Boolean get() = canvasReadCapabilities.vectorPath && canvasReadCapabilities.canWriteCanvas
    override val supportsTextPlacement: Boolean get() = canvasReadCapabilities.textPlacement && canvasReadCapabilities.canWriteCanvas
    override val supportsSequenceDiagrams: Boolean get() = canvasReadCapabilities.sequenceWrite
    private val replayJson = Json { ignoreUnknownKeys = true }

    internal val canOpenRealtime: Boolean get() = realtimeRuntime != null
    internal val canReviewLegacyMigration: Boolean get() = awaitedSubmissionRuntime == null && preferences.sequenceAllocator == null

    /** Read-only preview. Neither mutex nor repeated reads certify full writer exclusion;
     * publication must recapture again under the actual migration host's barrier. */
    internal suspend fun reviewLegacyMigration(session: WorkspaceSession, catalogJson: String? = null): LegacySubmissionMigrationReview =
        submissionMutex.withLock {
            check(canReviewLegacyMigration)
            prepareWriter()
            val scope = submissionScope(session)
            val review = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val input = LegacyMigrationCatalogInput.decode(catalogJson)
                writerWork {
                    check(preferences.userId == session.userId && preferences.workspaceId == session.workspace.id.value)
                    val capture = preferences.captureCataloguedLegacySequenceForMigration(scope, input.scopes, input.archives)
                    LegacySubmissionMigrationReview(LegacySubmissionMigrationPlanner.prepare(scope, capture, emptyList()))
                }
            }
            currentCoroutineContext().ensureActive()
            submissionScope(session)
            review
        }
    internal val missingRealtimeWritePrerequisites: Set<RealtimeWritePrerequisite> get() = buildSet {
        if (realtimeRuntime == null) add(RealtimeWritePrerequisite.DurableCheckpoint)
        if (sequenceAllocator == null) add(RealtimeWritePrerequisite.SequenceLedger)
        if (!draftPersistence.supportsAtomicStoppedEvidence) add(RealtimeWritePrerequisite.AtomicRecovery)
    }
    internal val canSubmitRealtime: Boolean get() = missingRealtimeWritePrerequisites.isEmpty()
    private var activeRealtimeOwner: BackendRealtimeOwner? = null
    internal fun registerRealtimeOwner(owner: BackendRealtimeOwner, session: WorkspaceSession) {
        require(owner.belongsTo(session) && preferences.userId == session.userId && preferences.clientId == session.clientId &&
            preferences.workspaceId == session.workspace.id.value)
        if (activeRealtimeOwner !== owner) activeRealtimeOwner?.close()
        activeRealtimeOwner = owner
    }
    internal fun unregisterRealtimeOwner(owner: BackendRealtimeOwner?) {
        if (activeRealtimeOwner === owner) activeRealtimeOwner = null
    }
    internal suspend fun createRealtimeOwner(session: WorkspaceSession, subscriptionId: String,
        canContinue: () -> Boolean): BackendRealtimeOwner {
        prepareWriter()
        val runtime = checkNotNull(realtimeRuntime) { "Durable realtime storage is unavailable" }
        require(subscriptionId.isNotBlank() && subscriptionId.length <= 256 && subscriptionId.none { it.code < 32 || it.code == 127 })
        require(subscriptionId.encodeToByteArray(throwOnInvalidSequence = true).size <= 256)
        fun valid(): Boolean {
            requireServerWriterActive()
            return canContinue() && preferences.userId == session.userId &&
                preferences.workspaceId == session.workspace.id.value && preferences.clientId == session.clientId
        }
        currentCoroutineContext().ensureActive(); check(valid())
        val negotiated = readCanvasCapabilities(readRealtimeJson("$apiBase/capabilities", session.userId, 64 * 1024))
        currentCoroutineContext().ensureActive(); check(valid())
        val store = runtime.checkpoints(RealtimeCheckpointScope(syncServerScope, session.userId, session.clientId,
            session.workspace.id.value), negotiated)
        return store.createOwner(apiBase.removeSuffix("/api/v1"), subscriptionId, ::valid,
            readMetadata = {
                val raw = readRealtimeJson("$apiBase/workspaces/${session.workspace.id.value}", session.userId, 64 * 1024)
                fun text(key: String) = requireNotNull((raw[key] as? JsonPrimitive)?.takeIf { it.isString }?.content)
                require(text("id") == session.workspace.id.value)
                val revision = requireNotNull((raw["metadataRevision"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull)
                BackendRealtimeMetadata(revision, text("title"), text("role"))
            }, readSnapshot = {
                val raw = readRealtimeJson("$apiBase/workspaces/${session.workspace.id.value}/state", session.userId, 64 * 1024 * 1024)
                decodeNegotiatedWorkspaceState(raw, negotiated).also { require(it.workspaceId == session.workspace.id.value) }
            })
    }

    private suspend fun readRealtimeJson(url: String, userId: String, maximumBytes: Int): JsonObject =
        kotlinx.coroutines.withTimeout(15_000) {
            client.prepareGet(url) { header(DevUserHeader, userId); header("Cache-Control", "no-cache, no-store") }.execute { response ->
                if (response.status.value !in 200..299) throw BackendRealtimeHttpException(response.status.value)
                require(response.headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase() == "application/json")
                val channel = response.bodyAsChannel(); val bytes = ByteArray(maximumBytes + 1); var size = 0
                try {
                    while (size < bytes.size) {
                        currentCoroutineContext().ensureActive()
                        val count = channel.readAvailable(bytes, size, bytes.size - size)
                        if (count < 0) break
                        size += count
                    }
                    require(size in 1 until bytes.size)
                    cg.creamgod.boarderless.data.parseStrictJsonObject(bytes.copyOf(size).decodeToString(throwOnInvalidSequence = true))
                } finally { channel.cancel(null) }
            }
        }

    override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?): WorkspaceSession {
        prepareWriter()
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
                requireServerWriterActive()
                when (error.status) {
                    HttpStatusCode.NotFound -> writerWork { preferences.workspaceId = null }
                    HttpStatusCode.Unauthorized -> writerWork { preferences.clearIdentity() }
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
                requireServerWriterActive()
                writerWork { preferences.clearIdentity() }
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
        prepareWriter()
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotEmpty()) { "Workspace title must not be blank" }
        return createWorkspaceForUser(session.userId, normalizedTitle)
    }

    override suspend fun openWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
    ): WorkspaceSession {
        prepareWriter()
        return loadAndRemember(session.userId, workspaceId.value)
    }

    override suspend fun renameWorkspace(
        session: WorkspaceSession,
        workspaceId: WorkspaceId,
        title: String,
    ): WorkspaceSummary {
        prepareWriter()
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
        prepareWriter()
        client
            .delete("$apiBase/workspaces/${workspaceId.value}") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
        requireServerWriterActive()
        writerWork { if (preferences.workspaceId == workspaceId.value) preferences.workspaceId = null }
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
        prepareWriter()
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
        prepareWriter()
        require(userId.isNotBlank()) { "Member user ID must not be blank" }
        client
            .delete("$apiBase/workspaces/${session.workspace.id.value}/members/${userId.trim()}") {
                header(DevUserHeader, session.userId)
            }.requireSuccess()
    }

    override suspend fun refresh(session: WorkspaceSession): WorkspaceSession {
        prepareWriter()
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
                    reduceCommittedCatchUpPage(cached.state, catchUp, canvasReadCapabilities)
                } catch (_: BackendContractException) {
                    null
                }
            if (replayed != null && replayed.workspaceVersion >= metadata.currentVersion) {
                val refreshed =
                    session.copy(
                        role = role,
                        workspaceVersion = replayed.workspaceVersion,
                        lastServerSeq = replayed.throughServerSeq,
                        workspace = replayed.toDomainWorkspace(metadata.title, canvasReadCapabilities),
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
            val pending = prepareSubmissionLocked(session, operation, allowExisting = false, requireJournal = false)
            submitWireRequest(session, pending.request).also { outcome ->
                if (outcome is SubmitOutcome.Accepted) {
                    val last = pending.request.operations.last().clientSeq
                    sequenceAllocator?.advance(pending.scope, last)
                    advanceLegacySequence(last)
                    acknowledgeConfirmedPending(pending, outcome.workspaceVersion, outcome.lastServerSeq)
                }
            }
        }

    override suspend fun prepareSubmission(session: WorkspaceSession, operation: WorkspaceOperation): cg.creamgod.boarderless.data.PreparedWorkspaceSubmission =
        submissionMutex.withLock {
            preparedIdentity(prepareSubmissionLocked(session, operation, allowExisting = true, requireJournal = true))
        }

    override suspend fun preparedSubmission(session: WorkspaceSession): cg.creamgod.boarderless.data.PreparedWorkspaceSubmission? =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            draftPersistence.pending(submissionScope(session))?.let(::preparedIdentity)
        }

    override suspend fun sendPreparedSubmission(session: WorkspaceSession,
        prepared: cg.creamgod.boarderless.data.PreparedWorkspaceSubmission): SubmitOutcome = submissionMutex.withLock {
        check(session.clientId == preferences.clientId && session.canEditContent)
        val scope = submissionScope(session)
        val pending = checkNotNull(draftPersistence.pending(scope))
        check(preparedIdentity(pending) == prepared) { "Prepared request identity changed" }
        check(recoverySafety.fenceState(pending) == null) { "Fence recovery requires receipt verification" }
        draftPersistence.stage(pending)
        submitWireRequest(session, pending.request).also { outcome ->
            if (outcome is SubmitOutcome.Accepted) {
                val last = pending.request.operations.last().clientSeq
                sequenceAllocator?.advance(scope, last)
                advanceLegacySequence(last)
                acknowledgeConfirmedPending(pending, outcome.workspaceVersion, outcome.lastServerSeq)
            }
        }
    }

    private suspend fun prepareSubmissionLocked(session: WorkspaceSession, operation: WorkspaceOperation,
        allowExisting: Boolean, requireJournal: Boolean): PendingWorkspaceSubmission {
        prepareWriter()
        check(supportsSequenceDiagrams || session.workspace.sequenceDiagrams.isEmpty()) { "Backend sequence diagram capability is unavailable" }
        check(session.clientId == preferences.clientId) { "Submission belongs to another client" }
        check(session.canEditContent) { "Preparation requires edit permission" }
        check(supportsCanvasStyle || operation.flatten().none { it is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation }) {
            "Backend canvas style capability is unavailable"
        }
        val scope = submissionScope(session)
        if (awaitedSubmissionRuntime == null) writerWork { preferences.registerLegacyScope(scope) }
        val journal = draftPersistence.journal(scope)
        if (requireJournal) check(journal != null && journal.baseWorkspace == session.workspace && journal.baseVersion == session.workspaceVersion) {
            "Preparation requires the saved draft baseline"
        }
        journal?.let { check(!it.quarantined && it.operations.firstOrNull() == operation) { "Saved draft head does not match submission" } }
        draftPersistence.pending(scope)?.let { pending ->
            check(allowExisting && pending.localOperationId == operation.operationId) { "An unconfirmed change requires recovery first" }
            check(recoverySafety.fenceState(pending) == null) { "Fence recovery requires receipt verification" }
            check(pending.request.baseVersion == session.workspaceVersion && journal?.baseWorkspace == session.workspace)
            draftPersistence.stage(pending)
            return pending
        }
        val expanded = operation.toExpandedDtos(0, canvasReadCapabilities)
        require(expanded.size in 1..BackendMaximumAtomicOperations) { "Atomic transaction exceeds backend operation limit" }
        if (operation.flatten().filterIsInstance<UpdateMediaReferenceOperation>().any { part -> part.changes.any { it.before != it.after } }) {
            check(mediaReferenceProofRuntime != null) { "Media reference changes require configured durable original evidence storage" }
        }
        recoverySafety.requireRestoration(scope, expanded, operation)
        requireMediaReferenceHistory(session, operation)
        val range = sequenceAllocator?.reserve(scope, expanded.size) ?: writerWork { preferences.reserveClientSequences(expanded.size) }
        if (sequenceAllocator != null) advanceLegacySequence(range.last)
        val request = SubmitOperationsRequest(clientId = session.clientId, transactionId = randomUuid(), baseVersion = session.workspaceVersion,
            operations = expanded.mapIndexed { index, dto -> dto.copy(clientSeq = range.first + index) })
        val pending = PendingWorkspaceSubmission(scope, operation.operationId, request, operation.deletionVersions(), operation.deletionSnapshotDigests(),
            captureMediaReferenceChanges(session.workspace, operation, request),
            historyIntent = journal?.historyIntents?.get(operation.operationId))
        draftPersistence.stage(pending)
        return pending
    }

    private fun preparedIdentity(pending: PendingWorkspaceSubmission): cg.creamgod.boarderless.data.PreparedWorkspaceSubmission {
        val digest = preparedRequestDigest(pending.request)
        return cg.creamgod.boarderless.data.PreparedWorkspaceSubmission(syncServerScope, pending.scope.userId,
            pending.request.clientId, cg.creamgod.boarderless.domain.model.WorkspaceId(pending.scope.workspaceId),
            pending.localOperationId, pending.request.transactionId, pending.request.baseVersion,
            pending.request.operations.map { it.operationId }, pending.request.operations.map { it.clientSeq }, digest)
    }

    override suspend fun verifyPreparedSubmission(session: WorkspaceSession,
        prepared: cg.creamgod.boarderless.data.PreparedWorkspaceSubmission,
        operation: WorkspaceOperation, baseline: WorkspaceSession?): cg.creamgod.boarderless.data.PreparedSubmissionVerification = submissionMutex.withLock {
        check(session.clientId == preferences.clientId && prepared.clientId == session.clientId)
        check(prepared.serverScope == syncServerScope && prepared.userId == session.userId && prepared.workspaceId == session.workspace.id)
        val request = rebuildPreparedRequest(prepared, operation, canvasReadCapabilities)
        baseline?.let {
            require(it.userId == prepared.userId && it.clientId == prepared.clientId && it.workspace.id == prepared.workspaceId)
            require(it.workspaceVersion == prepared.baseVersion && it.workspace.version == prepared.baseVersion)
            require(it.lastServerSeq in 0..9_007_199_254_740_991L)
        }
        val response = client.post("$apiBase/workspaces/${session.workspace.id.value}/operations/receipts") {
            contentType(ContentType.Application.Json)
            header(DevUserHeader, session.userId)
            setBody(ReceiptQueryDto(listOf(prepared.transactionId)))
        }.requireSuccess().body<SubmissionReceiptResponseDto>()
        val result = response.validatePendingReceipt(session.workspace.id.value, session.userId, request)
        val committed = response.receipts.singleOrNull()
        if (committed != null) readOriginalCommittedLog(session, request, committed)
        val current = loadSession(session.userId, session.workspace.id.value)
        check(current.userId == session.userId && current.clientId == session.clientId && current.workspace.id == session.workspace.id)
        check(current.workspaceVersion in maxOf(session.workspaceVersion, result.headWorkspaceVersion)..9_007_199_254_740_991L)
        check(current.lastServerSeq in maxOf(session.lastServerSeq, result.headServerSeq)..9_007_199_254_740_991L)
        val committedWorkspace = if (committed != null && baseline != null) {
            // Titles currently have no versioned committed operation. Do not invent history
            // for a concurrent rename, or silently approximate unsupported snapshot fields.
            require(current.workspace.title == baseline.workspace.title) { "Historical title revision unavailable" }
            val initial = recoveryProjection(baseline, canvasReadCapabilities)
            val window = collectCommittedThrough(baseline.lastServerSeq, committed.toServerSeq) { after ->
                client.get("$apiBase/workspaces/${session.workspace.id.value}/operations") {
                    header(DevUserHeader, session.userId)
                    parameter("afterSeq", after)
                    parameter("limit", 1000)
                }.requireSuccess().body<FullCatchUpOperationsDto>()
            }
            val recovered = reduceCommittedCatchUpPage(initial, window, canvasReadCapabilities)
            validateOriginalCommittedLog(committed, request, session.userId, FullCatchUpOperationsDto(
                window.operations.filter { it.serverSeq in committed.fromServerSeq..committed.toServerSeq }, window.lastServerSeq, false))
            require(recovered.workspaceVersion == committed.workspaceVersion)
            recovered.toDomainWorkspace(baseline.workspace.title, canvasReadCapabilities)
        } else null
        currentCoroutineContext().ensureActive()
        cg.creamgod.boarderless.data.PreparedSubmissionVerification(prepared, result.status, current,
            committed?.workspaceVersion, committed?.toServerSeq, committedWorkspace)
    }

    private fun submissionScope(session: WorkspaceSession) =
        PendingSubmissionScope(apiBase, session.userId, session.clientId, session.workspace.id.value).also {
            requireServerWriterActive()
            awaitedSubmissionRuntime?.requireActive?.invoke(it)
        }

    override fun retainDraft(
        session: WorkspaceSession,
        before: Workspace,
        operation: WorkspaceOperation,
    ) = retainHistoryDraft(session, before, operation, cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit)

    override fun retainHistoryDraft(session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) {
        check(awaitedSubmissionRuntime == null) { "This platform requires awaited draft publication" }
        validateRetainedDraft(session, before, operation)
        val scope = submissionScope(session)
        writerWork {
            preferences.registerLegacyScope(scope)
            legacyDraftPersistence.appendWithIntent(scope, session, before, operation, intent, beforeHistory)
        }
    }
    override suspend fun retainDraftAwaited(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
        validateRetainedDraft(session, before, operation)
        submissionMutex.withLock {
            validateRetainedDraft(session, before, operation)
            val scope = submissionScope(session)
            if (awaitedSubmissionRuntime == null) writerWork { preferences.registerLegacyScope(scope) }
            draftPersistence.appendWithIntent(scope, session, before, operation, cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit)
        }
        currentCoroutineContext().ensureActive()
    }
    private fun validateRetainedDraft(session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) {
        require(operation.backendOperationCount() in 1..BackendMaximumAtomicOperations) {
            "Atomic transaction exceeds backend operation limit; draft was not retained"
        }
        if (awaitedSubmissionRuntime != null) check(preferences.userId == session.userId && preferences.workspaceId == session.workspace.id.value)
        check(session.clientId == preferences.clientId && session.canEditContent)
        check(
            supportsSequenceDiagrams || (before.sequenceDiagrams.isEmpty() &&
                operation.flatten().none { it is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation }),
        ) { "Backend sequence diagram capability is unavailable" }
        check(supportsCanvasStyle || operation.flatten().none { it is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation }) {
            "Backend canvas style capability is unavailable"
        }
        operation.requireLegacyRelationContract(canvasReadCapabilities)
    }

    override suspend fun retainHistoryDraftAwaited(session: WorkspaceSession, before: Workspace,
        operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
        beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) {
        validateRetainedDraft(session, before, operation)
        submissionMutex.withLock {
            validateRetainedDraft(session, before, operation)
            val scope = submissionScope(session)
            if (awaitedSubmissionRuntime == null) writerWork { preferences.registerLegacyScope(scope) }
            draftPersistence.appendWithIntent(scope, session, before, operation, intent, beforeHistory)
        }
        currentCoroutineContext().ensureActive()
    }
    private fun advanceLegacySequence(sequence: Long) {
        if (awaitedSubmissionRuntime == null) writerWork { preferences.advanceClientSequence(sequence) }
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
            RestoredWorkspaceDraft(current, journal.operations, journal.replayHistory())
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
            PendingWorkspaceChange(it.request.transactionId, it.request.operations.size, recoverySafety.fenceState(it) != null)
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
                recoverySafety.observeFence(pending, it.status)
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
            recoverySafety.beginFence(pending)
            currentCoroutineContext().ensureActive()
            check(draftPersistence.pending(scope) == pending) { "Pending request changed before fence dispatch" }
            submissionScope(session) // Recheck the activation lease after the awaited read.
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
            recoverySafety.observeFence(pending, resolved.status)
            // A concurrent commit wins instead of fence. Neither result clears pending here.
            resolved
        }

    override suspend fun confirmPendingReceipt(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession = confirmPendingReceiptWithHistory(session, transactionId).session

    override suspend fun confirmPendingReceiptWithHistory(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.ConfirmedWorkspaceReceipt =
        submissionMutex.withLock {
            check(session.clientId == preferences.clientId)
            val scope = submissionScope(session)
            val pending = checkNotNull(draftPersistence.pending(scope))
            check(pending.request.transactionId == transactionId)
            // Capture before acknowledge removes the journal head. Recover the whole
            // transaction, never only its media children. Missing legacy journals may
            // settle, but cannot manufacture an undo snapshot from server after-state.
            val original = pendingHistoryOperation(pending)
            val intent = pending.historyIntent
                ?: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit
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
            recoverySafety.observeFence(pending, cg.creamgod.boarderless.data.PendingReceiptStatus.Committed)
            requireMediaRestorationAcknowledgement(pending, ack)
            recoverySafety.recordDeletions(pending, ack)
            val origins = recordMediaReferenceCommit(pending, ack)
            recordConfirmedHistory(pending, ack, origins)
            val confirmedHistory = loadConfirmedHistoryLocked(current)
            sequenceAllocator?.advance(
                scope,
                pending.request.operations
                    .last()
                    .clientSeq,
            )
            advanceLegacySequence(
                pending.request.operations
                    .last()
                    .clientSeq,
            )
            acknowledgeConfirmedPending(pending, receipt.workspaceVersion, receipt.toServerSeq)
            cg.creamgod.boarderless.data.ConfirmedWorkspaceReceipt(current, original, origins, intent, confirmedHistory)
        }

    override suspend fun retryPendingChange(
        session: WorkspaceSession,
        transactionId: String,
    ): WorkspaceSession = retryPendingChangeWithHistory(session, transactionId).session

    override suspend fun retryPendingChangeWithHistory(
        session: WorkspaceSession,
        transactionId: String,
    ): cg.creamgod.boarderless.data.ConfirmedWorkspaceReceipt = submissionMutex.withLock {
        prepareWriter()
        check(session.clientId == preferences.clientId)
        val scope = submissionScope(session)
        val pending = checkNotNull(draftPersistence.pending(scope))
        check(pending.request.transactionId == transactionId) { "Pending change no longer matches" }
        check(recoverySafety.fenceState(pending) == null) { "Fence recovery requires receipt verification, not resend" }
        val original = pendingHistoryOperation(pending)
        val intent = pending.historyIntent
            ?: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent.Edit
        val current = refresh(session)
        check(current.canEditContent) { "Pending submission requires current edit permission" }
        check(submissionScope(current) == scope && draftPersistence.pending(scope) == pending)
        draftPersistence.stage(pending)
        // Dispatch only the saved request. A conflict keeps it pending and yields no history.
        val accepted = submitWireRequest(current, pending.request) as? SubmitOutcome.Accepted
            ?: throw BackendContractException("Original transaction retry conflicted")
        val refreshed = refresh(current)
        check(refreshed.workspaceVersion >= accepted.workspaceVersion && refreshed.lastServerSeq >= accepted.lastServerSeq)
        currentCoroutineContext().ensureActive()
        check(submissionScope(refreshed) == scope && draftPersistence.pending(scope) == pending)
        val confirmedHistory = loadConfirmedHistoryLocked(refreshed)
        val last = pending.request.operations.last().clientSeq
        sequenceAllocator?.advance(scope, last)
        advanceLegacySequence(last)
        acknowledgeConfirmedPending(pending, accepted.workspaceVersion, accepted.lastServerSeq)
        cg.creamgod.boarderless.data.ConfirmedWorkspaceReceipt(refreshed, original, accepted.mediaReferenceOrigins, intent, confirmedHistory)
    }

    /** Used only while holding submissionMutex, before settlement removes the journal head. */
    private suspend fun pendingHistoryOperation(pending: PendingWorkspaceSubmission): WorkspaceOperation? {
        val journal = draftPersistence.journal(pending.scope) ?: return null
        check(journal.baseVersion == pending.request.baseVersion)
        val head = checkNotNull(journal.operations.firstOrNull())
        check(head.operationId == pending.localOperationId)
        val expanded = head.toExpandedDtos(0, canvasReadCapabilities)
        check(expanded.size == pending.request.operations.size)
        check(expanded.zip(pending.request.operations).all { (rebuilt, sent) ->
            rebuilt.copy(operationId = sent.operationId, clientSeq = sent.clientSeq) == sent
        }) { "Saved history does not match the original wire transaction" }
        val captured = captureMediaReferenceChanges(journal.baseWorkspace, head, pending.request)
        // Legacy missing evidence must not become partial mixed-transaction authority.
        if (pending.mediaReferenceChanges.isEmpty() && captured.isNotEmpty()) return null
        check(captured == pending.mediaReferenceChanges)
        head.inverse()
        // Older entries have no declared stack intent. Never guess Undo/Redo from
        // operation IDs or infer that every confirmed operation was a new edit.
        val intent = pending.historyIntent ?: return null
        check(journal.historyIntents[head.operationId] == intent) { "Saved history intent changed after preparation" }
        return head
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
            check(recoverySafety.fenceState(pending) == null) { "Fence recovery requires receipt verification, not resend" }
            draftPersistence.stage(pending)
            submitWireRequest(session, pending.request).also { outcome ->
                if (outcome is SubmitOutcome.Accepted) {
                    sequenceAllocator?.advance(
                        scope,
                        pending.request.operations
                            .last()
                            .clientSeq,
                    )
                    advanceLegacySequence(
                        pending.request.operations
                            .last()
                            .clientSeq,
                    )
                    acknowledgeConfirmedPending(pending, outcome.workspaceVersion, outcome.lastServerSeq)
                }
            }
        }

    private suspend fun submitWireRequest(
        session: WorkspaceSession,
        request: SubmitOperationsRequest,
    ): SubmitOutcome {
        currentCoroutineContext().ensureActive()
        submissionScope(session)
        val realtime = activeRealtimeOwner?.takeIf { canSubmitRealtime && it.belongsTo(session) && it.status.value == BackendRealtimeStatus.Live }
        if (realtime != null) {
            val scope = submissionScope(session)
            val pending = checkNotNull(draftPersistence.pending(scope))
            require(pending.request == request && recoverySafety.fenceState(pending) == null)
            // Caller already staged the original request under submissionMutex. Never
            // issue an HTTP fallback when this dispatch/ACK becomes uncertain.
            return when (val result = realtime.submit(request) {
                fun current() = activeRealtimeOwner === realtime && preferences.userId == session.userId &&
                    preferences.workspaceId == session.workspace.id.value && preferences.clientId == session.clientId
                submissionScope(session)
                if (!current()) false else {
                    val saved = draftPersistence.pending(scope)
                    val fence = recoverySafety.fenceState(pending)
                    currentCoroutineContext().ensureActive()
                    submissionScope(session)
                    current() && saved == pending && fence == null
                }
            }) {
                is RealtimeSubmissionResult.Accepted -> {
                    val accepted = result.acknowledgement
                    accepted.validateAcknowledgement(request, session.userId)
                    check(draftPersistence.pending(scope) == pending)
                    requireMediaRestorationAcknowledgement(pending, accepted)
                    recoverySafety.recordDeletions(pending, accepted)
                    val origins = recordMediaReferenceCommit(pending, accepted)
                    recordConfirmedHistory(pending, accepted, origins)
                    SubmitOutcome.Accepted(accepted.workspaceVersion, accepted.toServerSeq, origins)
                }
                is RealtimeSubmissionResult.Conflict -> {
                    val current = loadSession(session.userId, session.workspace.id.value)
                    require(current.workspaceVersion >= result.workspaceVersion && current.lastServerSeq >= result.lastServerSeq)
                    SubmitOutcome.Conflict(current)
                }
                is RealtimeSubmissionResult.Rejected -> throw RealtimeSubmissionRejected(result.code)
            }
        }
        currentCoroutineContext().ensureActive()
        submissionScope(session) // No suspension between this guard and HTTP dispatch.
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
                requireMediaRestorationAcknowledgement(pending, accepted)
                recoverySafety.recordDeletions(pending, accepted)
                val origins = recordMediaReferenceCommit(pending, accepted)
                recordConfirmedHistory(pending, accepted, origins)
                SubmitOutcome.Accepted(
                    workspaceVersion = accepted.workspaceVersion,
                    lastServerSeq = accepted.toServerSeq,
                    mediaReferenceOrigins = origins,
                )
            }

            HttpStatusCode.Conflict -> {
                val conflict = response.body<ConflictOperationsDto>()
                val current = loadSession(session.userId, session.workspace.id.value)
                require(current.workspaceVersion >= conflict.workspaceVersion && current.lastServerSeq >= conflict.lastServerSeq)
                SubmitOutcome.Conflict(current)
            }

            else -> {
                throw response.toException()
            }
        }
    }

    override fun close() {
        try { activeRealtimeOwner?.close(); activeRealtimeOwner = null }
        finally { try { client.close() } finally { releaseServerWriter() } }
    }

    private suspend fun prepareWriter() {
        currentCoroutineContext().ensureActive()
        prepareServerWriter()
        currentCoroutineContext().ensureActive()
        requireServerWriterActive()
    }

    private suspend fun recordMediaReferenceCommit(pending: PendingWorkspaceSubmission, ack: AcceptedOperationsDto): List<MediaReferenceHistoryOrigin> {
        if (pending.mediaReferenceChanges.isEmpty()) return emptyList() // Legacy entries never gain invented snapshots.
        return requireNotNull(mediaReferenceCommits) { "Original media reference storage unavailable" }.record(pending, ack).historyOrigins()
    }

    /** Persist only the original committed head, never the unconfirmed journal tail.
     * Called after full ACK/provenance validation and before pending acknowledgement. */
    private suspend fun recordConfirmedHistory(pending: PendingWorkspaceSubmission, ack: AcceptedOperationsDto,
        origins: List<MediaReferenceHistoryOrigin>) {
        val store = confirmedHistories ?: return
        val original = pendingHistoryOperation(pending) ?: return
        val journal = checkNotNull(draftPersistence.journal(pending.scope))
        val baseline = journal.baseHistory ?: return // Do not replace complete history with a legacy partial reconstruction.
        val saved = store.readDurable(pending.scope)
        val requestDigest = preparedRequestDigest(pending.request)
        if (saved != null && saved.value.workspaceVersion == ack.workspaceVersion && saved.value.lastServerSeq == ack.toServerSeq) {
            require(saved.value.transactionId == pending.request.transactionId && saved.value.requestDigest == requestDigest) {
                "Original confirmed history identity differs"
            }
            return // Exact original already published, including prior committed media provenance.
        }
        val before = saved?.value?.takeIf {
            it.workspaceVersion == journal.baseVersion && it.lastServerSeq == journal.baseServerSeq &&
                it.history.workspace == journal.baseWorkspace
        }?.history ?: baseline
        val history = before.applyRecorded(original, checkNotNull(pending.historyIntent)).history
            .withCommittedMediaReferences(original, origins)
        val next = SavedWorkspaceHistory(scope = pending.scope, workspaceVersion = ack.workspaceVersion,
            lastServerSeq = ack.toServerSeq, transactionId = pending.request.transactionId,
            requestDigest = requestDigest, history = history)
        if (saved != null && (saved.value.workspaceVersion > ack.workspaceVersion || saved.value.lastServerSeq > ack.toServerSeq)) {
            // Confirming an older receipt must not rewind a later confirmed snapshot.
            return
        }
        store.save(next, saved?.generation)
    }

    private suspend fun acknowledgeConfirmedPending(pending: PendingWorkspaceSubmission, version: Long, seq: Long) {
        val saved = confirmedHistories?.readDurable(pending.scope)?.value
        val history = saved?.takeIf { it.workspaceVersion == version && it.lastServerSeq == seq }?.let {
            require(it.transactionId == pending.request.transactionId && it.requestDigest == preparedRequestDigest(pending.request)) {
                "Confirmed history does not belong to the pending transaction"
            }
            it.history
        }
        currentCoroutineContext().ensureActive()
        check(draftPersistence.pending(pending.scope) == pending)
        draftPersistence.acknowledgeWithHistory(pending, version, seq, history)
    }

    private suspend fun requireMediaReferenceHistory(session: WorkspaceSession, operation: WorkspaceOperation) {
        val parts = operation.flatten()
        if (parts.filterIsInstance<UpdateMediaReferenceOperation>().none { it.changes.any { change -> change.historyOrigin != null } }) return
        check(canvasReadCapabilities.mediaReferenceRestore && canvasReadCapabilities.canWriteCanvas) { "Backend media reference restoration is unavailable" }
        val scope = submissionScope(session)
        val store = requireNotNull(mediaReferenceCommits)
        var current = session.workspace
        val evidence = mutableMapOf<String, MediaReferenceCommitEvidence>()
        for (part in parts) {
            if (part is UpdateMediaReferenceOperation) for (change in part.changes) {
                val origin = change.historyOrigin ?: continue
                require(origin.workspaceId == scope.workspaceId && origin.actorId == scope.userId)
                val saved = evidence[origin.transactionId] ?: requireNotNull(store.read(scope, origin.transactionId)) {
                    "Original media reference evidence missing"
                }.also { evidence[origin.transactionId] = it }
                val original = saved.changes.single { it.wireOperationId == origin.wireOperationId }.change
                require(original.objectId == origin.objectId && original.before == origin.before && original.after == origin.after &&
                    original.expectedVersion == origin.objectVersionBefore)
                require(origin.before.mediaKind == origin.after.mediaKind)
                change.historyTargetSide()
                val node = current.objects[change.objectId] as? MediaNode ?: error("Historical media node missing")
                require(!node.locked && node.version == change.expectedVersion && node.mediaReference() == change.before)
            }
            current = when (val applied = part.applyTo(current)) {
                is cg.creamgod.boarderless.domain.history.OperationResult.Applied -> applied.workspace
                is cg.creamgod.boarderless.domain.history.OperationResult.Rejected -> error("Historical media baseline changed")
            }
        }
        currentCoroutineContext().ensureActive(); submissionScope(session)
    }

    private suspend fun requireMediaRestorationAcknowledgement(pending: PendingWorkspaceSubmission, ack: AcceptedOperationsDto) {
        for (wire in pending.request.operations.filter { it.kind == "restore_media_references" }) {
            validateRestorationWire(wire)
            fun text(key: String) = requireNotNull((wire.payload[key] as? JsonPrimitive)?.takeIf { it.isString }?.content)
            val original = requireNotNull(requireNotNull(mediaReferenceCommits).read(pending.scope, text("originalTransactionId"))) {
                "Original media reference evidence missing at settlement"
            }
            val captured = original.changes.single { it.wireOperationId == text("originalOperationId") }.change
            require(captured.objectId.value == text("objectId") && captured.before.mediaKind == captured.after.mediaKind)
            val expected = if (text("targetSide") == "before") captured.before else captured.after
            val committed = ack.operations.single { it.operationId == wire.operationId }
            val restored = committed.payload["restoredReference"] as? JsonObject ?: error("Restored media reference missing")
            require(restored.keys == setOf("assetId", "thumbnailAssetId") &&
                restored["assetId"] == JsonPrimitive(expected.assetId) &&
                restored["thumbnailAssetId"] == (expected.thumbnailAssetId?.let(::JsonPrimitive) ?: JsonNull))
            val version = requireNotNull((wire.payload["expectedObjectVersion"] as? JsonPrimitive)?.longOrNull)
            require(committed.payload["objectVersion"] == JsonPrimitive(version + 1))
        }
    }

    private fun <T> writerWork(action: () -> T): T {
        fun guarded(): T { requireServerWriterActive(); return action() }
        return if (serverWriterOperations != null) serverWriterOperations.run(::guarded) else guarded()
    }

    private suspend fun createSession(): WorkspaceSession {
        requireServerWriterActive()
        val user =
            client
                .post("$apiBase/users") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateUserRequest(Strings.content.defaultUserName()))
                }.requireSuccess()
                .body<UserDto>()
        requireServerWriterActive()
        writerWork { preferences.userId = user.id }
        return createWorkspaceForUser(user.id, Strings.content.myThinkingSpace())
    }

    private suspend fun createWorkspaceForUser(
        userId: String,
        title: String,
    ): WorkspaceSession {
        requireServerWriterActive()
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
        requireServerWriterActive()
        writerWork {
            preferences.userId = userId
            preferences.workspaceId = workspaceId
        }
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
        // Negotiate from this repository's configured deployment, never a provider URL.
        val capabilityResponse = client.get("$apiBase/capabilities") { header(DevUserHeader, userId) }
        val negotiated = if (capabilityResponse.status == HttpStatusCode.NotFound) BackendCanvasReadCapabilities()
            else readCanvasCapabilities(capabilityResponse.requireSuccess().body<JsonObject>())
        val state =
            client
                .get("$apiBase/workspaces/$workspaceId/state") {
                    header(DevUserHeader, userId)
                }.requireSuccess()
                .body<JsonObject>()
                .let { decodeNegotiatedWorkspaceState(it, negotiated) }
        canvasReadCapabilities = negotiated
        if (state.workspaceId != workspaceId) throw BackendContractException("Workspace projection scope mismatch")
        requireServerWriterActive() // clientId getter can initialize legacy identity.
        val loaded =
            WorkspaceSession(
                userId = userId,
                clientId = writerWork { preferences.clientId },
                role = role,
                workspaceVersion = state.workspaceVersion,
                lastServerSeq = state.throughServerSeq,
                workspace = state.toDomainWorkspace(title, negotiated),
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

private fun WorkspaceOperation.requireLegacyRelationContract(capabilities: BackendCanvasReadCapabilities = BackendCanvasReadCapabilities()) {
    flatten().forEach { operation ->
        if (operation is UpdateMediaReferenceOperation && operation.changes.any { it.historyOrigin != null }) {
            check(capabilities.mediaReferenceRestore && capabilities.canWriteCanvas) { "Backend media reference restoration is unavailable" }
        }
        check(operation !is cg.creamgod.boarderless.domain.history.UpdateSequenceDiagramsOperation || capabilities.sequenceWrite) {
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
        check(vectorSupported || capabilities.vectorPath && capabilities.canWriteCanvas) { "Backend vector path capability is unavailable" }
        val placementSupported = when (operation) {
            is CreateObjectsOperation -> operation.objects.none { (it as? TextNode)?.textPlacement != null }
            is DeleteObjectsOperation -> operation.objects.none { (it as? TextNode)?.textPlacement != null }
            is UpdateTextNodeAttributesOperation -> operation.changes.all { it.before.textPlacement == null && it.after.textPlacement == null }
            else -> true
        }
        check(placementSupported || capabilities.textPlacement && capabilities.canWriteCanvas) { "Backend text placement capability is unavailable" }
        val supported =
            when (operation) {
                is CreateRelationsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is DeleteObjectsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is DeleteRelationsOperation -> operation.relations.all { it.geometry == null && it.sourceObjectId != it.targetObjectId }
                is UpdateRelationAttributesOperation -> operation.changes.all { it.before.geometry == null && it.after.geometry == null }
                else -> true
            }
        check(supported || capabilities.geometry && capabilities.canWriteCanvas) { "Backend relation geometry capability is unavailable" }
    }
}

private fun captureMediaReferenceChanges(before: Workspace, operation: WorkspaceOperation,
    request: SubmitOperationsRequest): List<PendingMediaReferenceChange> {
    val flattened = operation.flatten()
    if (flattened.none { it is UpdateMediaReferenceOperation }) return emptyList()
    var current = before
    val captured = mutableListOf<PendingMediaReferenceChange>()
    for (part in flattened) {
        if (part is UpdateMediaReferenceOperation) part.changes.forEach { change ->
            if (change.historyOrigin != null) return@forEach // Restoration retains its original immutable evidence.
            if (change.before == change.after) return@forEach // No changed reference means no restoration authority.
            val node = current.objects[change.objectId] as? MediaNode ?: error("Original media node missing")
            require(node.version == change.expectedVersion &&
                node.mediaReference() == change.before)
            val sent = request.operations.single { it.kind == "update_object" &&
                it.payload["objectId"] == JsonPrimitive(change.objectId.value) &&
                it.expectedObjectVersions == mapOf(change.objectId.value to change.expectedVersion) &&
                (it.payload["properties"] as? JsonObject)?.get("assetId") == JsonPrimitive(change.after.assetId) }
            requireMediaReferenceWire(sent, change)
            captured += PendingMediaReferenceChange(sent.operationId, change)
        }
        current = when (val applied = part.applyTo(current)) {
            is cg.creamgod.boarderless.domain.history.OperationResult.Applied -> applied.workspace
            is cg.creamgod.boarderless.domain.history.OperationResult.Rejected -> error("Cannot capture media reference baseline")
        }
    }
    return captured
}

private fun WorkspaceOperation.flatten(): List<WorkspaceOperation> =
    when (this) {
        is TransactionOperation -> operations.flatMap { it.flatten() }
        else -> listOf(this)
    }

private fun WorkspaceOperation.toDto(clientSequence: Long): OperationDto =
    when (this) {
        is cg.creamgod.boarderless.domain.history.LocalWorkspaceContentOperation -> error("Local content transactions cannot be submitted as server operations")
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
                            buildJsonObject {
                                put("colorToken", JsonPrimitive(change.after.colorToken))
                                if (change.before.geometry != change.after.geometry) {
                                    put("geometry", Json { encodeDefaults = true }.encodeToJsonElement(RelationGeometry.serializer(),
                                        change.after.geometry ?: RelationGeometry()))
                                }
                            },
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
                                if (change.before.vectorPath != change.after.vectorPath) {
                                    put("vectorPath", change.after.vectorPath?.let { Json { encodeDefaults = true }.encodeToJsonElement(VectorPath.serializer(), it) } ?: JsonNull)
                                }
                                if (change.before.textPlacement != change.after.textPlacement) {
                                    put("textPlacement", change.after.textPlacement?.let { Json { encodeDefaults = true }.encodeToJsonElement(TextPlacement.serializer(), it) } ?: JsonNull)
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
            val origin = change.historyOrigin
            if (origin != null) {
                OperationDto(randomUuid(), clientSequence, "restore_media_references", payload = buildJsonObject {
                    put("objectId", JsonPrimitive(change.objectId.value))
                    put("expectedObjectVersion", JsonPrimitive(change.expectedVersion))
                    put("originalTransactionId", JsonPrimitive(origin.transactionId))
                    put("originalOperationId", JsonPrimitive(origin.wireOperationId))
                    put("targetSide", JsonPrimitive(change.historyTargetSide()))
                })
            } else OperationDto(
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
            OperationDto(operationId, clientSequence, "update_sequence_diagrams", payload = buildJsonObject {
                put("expectedSequenceDiagramsVersion", JsonPrimitive(expectedSequenceDiagramsVersion))
                put("before", sequenceDiagramsWire(before))
                put("after", sequenceDiagramsWire(after))
            })
        }

        is cg.creamgod.boarderless.domain.history.UpdateCanvasStyleOperation -> {
            OperationDto(operationId, clientSequence, "update_canvas_style", payload = buildJsonObject {
                put("expectedCanvasStyleVersion", JsonPrimitive(expectedCanvasStyleVersion))
                put("canvasStyle", Json { encodeDefaults = true }.encodeToJsonElement(cg.creamgod.boarderless.domain.model.CanvasStyle.serializer(), after))
            })
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

internal const val BackendMaximumAtomicOperations = 200
internal fun WorkspaceOperation.backendOperationCount(): Int = flattenAndExpand().size

internal fun WorkspaceOperation.toExpandedDtos(startingSequence: Long, capabilities: BackendCanvasReadCapabilities = BackendCanvasReadCapabilities()): List<OperationDto> =
    also { it.requireLegacyRelationContract(capabilities) }.flattenAndExpand().mapIndexed { index, operation ->
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
                                    is cg.creamgod.boarderless.domain.model.ExternalMediaNode -> error("Server does not support external media nodes")
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
                                        canvasObject.vectorPath?.let { put("vectorPath", Json { encodeDefaults = true }.encodeToJsonElement(VectorPath.serializer(), it)) }
                                        canvasObject.textPlacement?.let { put("textPlacement", Json { encodeDefaults = true }.encodeToJsonElement(TextPlacement.serializer(), it)) }
                                    }

                                    is GroupFrame -> {
                                        put("title", JsonPrimitive(canvasObject.title))
                                        put("colorToken", JsonPrimitive(canvasObject.colorToken))
                                    }

                                    is cg.creamgod.boarderless.domain.model.ExternalMediaNode -> error("Server does not support external media nodes")
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
            require(relation.sourceObjectId != relation.targetObjectId || relation.geometry != null) {
                "Self-loop requires explicit geometry"
            }
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
                            buildJsonObject {
                                put("colorToken", JsonPrimitive(relation.colorToken))
                                relation.geometry?.let {
                                    put("geometry", Json { encodeDefaults = true }.encodeToJsonElement(RelationGeometry.serializer(), it))
                                }
                            },
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

internal fun WorkspaceStateDto.toDomainWorkspace(title: String, capabilities: BackendCanvasReadCapabilities = BackendCanvasReadCapabilities()): Workspace {
    if (!capabilities.canvasStyle && (canvasStyle != cg.creamgod.boarderless.domain.model.CanvasStyle() || canvasStyleVersion != 0L))
        throw BackendContractException("Unnegotiated canvas style")
    if (!capabilities.sequenceDiagrams && (sequenceDiagrams.isNotEmpty() || sequenceDiagramsVersion != 0L))
        throw BackendContractException("Unnegotiated sequence metadata")
    if (sequenceDiagrams.map { it.containerId }.distinct().size != sequenceDiagrams.size)
        throw BackendContractException("Duplicate sequence container")
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
            if ("vectorPath" in dto.properties && !capabilities.vectorPath) throw BackendContractException("Vector path requires negotiated backend support")
            if ("textPlacement" in dto.properties && !capabilities.textPlacement) throw BackendContractException("Text placement requires negotiated backend support")
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
                            vectorPath = dto.properties["vectorPath"]?.takeUnless { it == JsonNull }?.let {
                                Json.decodeFromJsonElement(VectorPath.serializer(), it)
                            },
                            textPlacement = dto.properties["textPlacement"]?.takeUnless { it == JsonNull }?.let {
                                Json.decodeFromJsonElement(TextPlacement.serializer(), it)
                            },
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
            if ("geometry" in dto.style && !capabilities.geometry) throw BackendContractException("Relation geometry requires negotiated backend support")
            if (dto.relationVersion < 1) {
                throw BackendContractException("Relation ${dto.relationId} has invalid version ${dto.relationVersion}")
            }
            val sourceId = CanvasObjectId(dto.sourceObjectId)
            val targetId = CanvasObjectId(dto.targetObjectId)
            if (sourceId == targetId && (!capabilities.geometry || "geometry" !in dto.style)) {
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
                geometry = dto.style["geometry"]?.let { Json.decodeFromJsonElement(RelationGeometry.serializer(), it) }
            )
        }
    return Workspace(
        id = WorkspaceId(workspaceId),
        title = title,
        version = workspaceVersion,
        objects = objectMap,
        relations = domainRelations.associateBy { it.id },
        canvasStyle = canvasStyle,
        canvasStyleVersion = canvasStyleVersion,
        sequenceDiagrams = sequenceDiagrams.associateBy { it.containerId },
        sequenceDiagramsVersion = sequenceDiagramsVersion,
    ).also { if (!it.hasValidSequenceBindings()) throw BackendContractException("Invalid sequence graph bindings") }
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

internal fun decodeNegotiatedWorkspaceState(raw: JsonObject, capabilities: BackendCanvasReadCapabilities): WorkspaceStateDto {
    if (("canvasStyle" in raw || "canvasStyleVersion" in raw) && !capabilities.canvasStyle)
        throw BackendContractException("Canvas style requires negotiated backend support")
    if (("sequenceDiagrams" in raw || "sequenceDiagramsVersion" in raw) && !capabilities.sequenceDiagrams)
        throw BackendContractException("Sequence diagrams require negotiated backend support")
    if (capabilities.canvasStyle && !("canvasStyle" in raw && "canvasStyleVersion" in raw))
        throw BackendContractException("Incomplete canvas style projection")
    if (capabilities.sequenceDiagrams && !("sequenceDiagrams" in raw && "sequenceDiagramsVersion" in raw))
        throw BackendContractException("Incomplete sequence projection")
    val state = Json { ignoreUnknownKeys = true }.decodeFromJsonElement(WorkspaceStateDto.serializer(), raw)
    raw["sequenceDiagrams"]?.let { sequenceDiagramsFromWire(it) }
    require(state.sequenceDiagrams.map { it.containerId }.distinct().size == state.sequenceDiagrams.size) { "Duplicate sequence container" }
    return state
}
