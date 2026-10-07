package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.remote.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.sequence.hasValidSequenceBindings
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

/** Desktop local documents. One atomic file contains the active document, other documents and
 * bounded local save receipts. Saving never contacts a server or copies test-server data.
 */
class DesktopFileWorkspaceRepository private constructor(
    loadFiles: () -> DesktopAtomicDraftStore,
    assetRoot: Path? = null,
) : CanvasWorkspaceRepository {
    constructor(rootPath: Path) : this({ DesktopAtomicDraftStore(rootPath) }, rootPath.resolve("assets"))
    internal constructor(files: DesktopAtomicDraftStore) : this({ files })

    private val files by lazy(loadFiles)
    private val json =
        Json {
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
    override val isLocalOnly = true
    override val supportsCanvasStyle = true
    override val supportsRelationGeometry = true
    override val supportsVectorPaths = true
    override val supportsSequenceDiagrams = true
    override val quickSchemeRepository by lazy {
        assetRoot?.let { root ->
            DesktopFileQuickSchemeStore(root.parent.resolve("schemes")) {
                files
                Unit
            }
        }
    }
    override val localAssetGateway by lazy {
        assetRoot?.let {
            DesktopFileAssetGateway(it) { session ->
                refresh(session)
                Unit
            }
        }
    }

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
        d.boards.forEach { b ->
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
        MessageDigest
            .getInstance("SHA-256")
            .digest(json.encodeToString(WorkspaceOperation.serializer(), operation).encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun summary(b: Board) = WorkspaceSummary(b.workspace.id, b.workspace.title, "owner", b.workspace.version, 0)

    override suspend fun openOrCreateWorkspace(preferredWorkspaceId: WorkspaceId?): WorkspaceSession =
        synchronized(this) {
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
    ) = synchronized(this) {
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
            return@synchronized Unit
        }
        require(b.workspace == before) { "Local document changed" }
        val saved =
            Saved(operation.operationId, hash(operation), after.version, Instant.now().toString(), operation::class.simpleName ?: "edit")
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
        synchronized(this) {
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
        synchronized(this) {
            val d = requireNotNull(read()).documents
            owned(d, session)
            session(d, board(d, session.workspace.id))
        }

    override suspend fun listWorkspaces(session: WorkspaceSession): List<WorkspaceSummary> =
        synchronized(this) {
            val d = requireNotNull(read()).documents
            owned(d, session)
            d.boards.filterNot { it.deleted }.map(::summary)
        }

    override suspend fun createWorkspace(
        session: WorkspaceSession,
        title: String,
    ): WorkspaceSession =
        synchronized(this) {
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
        synchronized(this) {
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
        synchronized(this) {
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
    ) = synchronized(this) {
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
        synchronized(this) {
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
        return localAssetGateway?.list(session).orEmpty()
    }

    override suspend fun getAsset(
        session: WorkspaceSession,
        assetId: String,
    ): WorkspaceAsset = requireNotNull(localAssetGateway).recover(session, assetId)

    override fun close() {}

    private companion object {
        val Key =
            MessageDigest.getInstance("SHA-256").digest("BoarderLess.local-documents.v1".toByteArray()).joinToString("") {
                (
                    it.toInt() and
                        255
                ).toString(16).padStart(2, '0')
            }
    }
}
