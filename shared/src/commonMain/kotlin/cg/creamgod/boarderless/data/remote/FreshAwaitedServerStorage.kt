package cg.creamgod.boarderless.data.remote

import cg.creamgod.boarderless.data.PendingReceiptStatus
import cg.creamgod.boarderless.data.WorkspaceSession
import cg.creamgod.boarderless.data.parseStrictJsonObject
import cg.creamgod.boarderless.data.persistence.AwaitedDurableLocalRecordStore
import cg.creamgod.boarderless.domain.history.WorkspaceOperation
import cg.creamgod.boarderless.domain.model.Workspace
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.kotlincrypto.hash.sha2.SHA256

/**
 * Fresh, migration-free durable storage for drafts, sequence ledgers and recovery safety on
 * platforms whose records are written through an awaited store (Android/iOS files, browser IDB).
 *
 * A workspace scope is enrolled the first time it is used: the control record is saved as
 * Preparing, the empty draft/safety artifacts and (for the first workspace of an identity) the
 * sequence ledger are created, and only then is the entry promoted to Ready. Enrollment never
 * adopts pre-existing unregistered records, never resets or lowers a ledger and never reads
 * legacy Settings. Missing artifacts of a Ready entry are a hard failure, never "empty".
 *
 * The caller wraps [files] with the writer operation lease, so logical writer drain also waits
 * for physical storage work, and supplies [requireWriterActive] for ownership checks.
 */
internal class FreshAwaitedServerStorage(
    private val files: AwaitedDurableLocalRecordStore,
    private val apiBase: String,
    private val requireWriterActive: () -> Unit,
) {
    @Serializable private enum class Phase { Preparing, Ready }

    @Serializable private data class Entry(val scope: PendingSubmissionScope, val phase: Phase)

    @Serializable private data class Control(val version: Int = 1, val entries: List<Entry> = emptyList())

    private data class StoredControl(val generation: Long, val control: Control)

    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private val verified = mutableSetOf<PendingSubmissionScope>()
    private var controlGeneration: Long? = null

    init {
        require(apiBase.endsWith("/api/v1") && apiBase.length > "/api/v1".length)
    }

    private val controlKey =
        SHA256().digest("boarderless.fresh-awaited-server-control.v1\n$apiBase".encodeToByteArray())
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    /** Synchronous identity/ownership check used by every awaited store; never enrolls. */
    private fun requireScope(scope: PendingSubmissionScope) {
        requireWriterActive()
        require(scope.apiBase == apiBase) { "Scope belongs to another server" }
        requireRealtimeUuid(scope.userId)
        requireRealtimeUuid(scope.clientId)
        requireRealtimeUuid(scope.workspaceId)
        WorkspaceDraftScopeBundleCodec.scopeHash(scope)
    }

    private val drafts = AwaitedAtomicWorkspaceDraftPersistence(files, requireScope = ::requireScope)
    private val sequences = AwaitedAtomicClientSequenceAllocator(files, ::requireScope)
    private val safety = AwaitedAtomicRecoverySafetyPersistence(files, ::requireScope)

    private fun emptySafety(scope: PendingSubmissionScope) = RecoverySafetyBundleCodec.prepareLegacy(scope, emptyList(), emptyList())

    private suspend fun readControl(): StoredControl? {
        currentCoroutineContext().ensureActive()
        val stored = files.read(controlKey) ?: return null
        currentCoroutineContext().ensureActive()
        require(stored.generation in 1 until RealtimeMaxSequence && stored.payload.size in 1..1024 * 1024)
        val raw = parseStrictJsonObject(stored.payload.decodeToString(throwOnInvalidSequence = true))
        require(raw.keys == setOf("version", "entries"))
        val control = json.decodeFromJsonElement(Control.serializer(), raw)
        require(control.version == 1 && control.entries.size <= 256)
        require(control.entries.map { it.scope }.distinct().size == control.entries.size)
        require(control.entries.count { it.phase == Phase.Preparing } <= 1)
        control.entries.forEach {
            require(it.scope.apiBase == apiBase)
            WorkspaceDraftScopeBundleCodec.scopeHash(it.scope)
        }
        return StoredControl(stored.generation, control)
    }

    private suspend fun saveControl(before: StoredControl?, next: Control): StoredControl {
        val payload = json.encodeToString(Control.serializer(), next).encodeToByteArray(throwOnInvalidSequence = true)
        require(payload.size <= 1024 * 1024)
        currentCoroutineContext().ensureActive()
        requireWriterActive()
        files.compareAndSet(controlKey, before?.generation, payload)
        val generation = (before?.generation ?: 0L) + 1
        files.confirmDurable(controlKey, generation)
        currentCoroutineContext().ensureActive()
        requireWriterActive()
        val saved = requireNotNull(readControl()) { "Server storage control disappeared after saving" }
        require(saved.generation == generation && saved.control == next) { "Server storage control changed while saving" }
        controlGeneration = generation
        return saved
    }

    /** Durable read-back of every artifact of a Ready scope. Missing means corruption, not empty. */
    private suspend fun verifyReady(scope: PendingSubmissionScope) {
        val draft = drafts.readDurable(scope)
        val safe = safety.readDurable(scope)
        val ledger = sequences.readDurable(scope)
        require(safe.bundle.legacyImportDigest == emptySafety(scope).legacyImportDigest) { "Recovery safety identity changed" }
        val wires = listOfNotNull(draft.bundle?.pending, draft.bundle?.stoppedPending) +
            draft.bundle?.retainedStopped.orEmpty().map { it.submitted }
        require(wires.all { wire -> wire.request.operations.all { it.clientSeq <= ledger.highWater } }) {
            "A saved request is newer than the sequence ledger; refusing to reuse sequences"
        }
    }

    /** Idempotent, resumable first-use enrollment of [scope]. */
    suspend fun ensureEnrolled(scope: PendingSubmissionScope) {
        requireScope(scope)
        if (scope in verified) return
        mutex.withLock {
            requireScope(scope)
            if (scope in verified) return
            var control = readControl()
            val entries = control?.control?.entries.orEmpty()
            val entry = entries.singleOrNull { it.scope == scope }
            if (entry?.phase == Phase.Ready) {
                verifyReady(scope)
                verified += scope
                return
            }
            require(entries.none { it.phase == Phase.Preparing && it.scope != scope }) {
                "Resume the original workspace enrollment first"
            }
            require(entry != null || entries.size < 256) { "Too many enrolled workspaces" }
            // The ledger is shared by every workspace of one origin/user/client.
            val sharedByOthers = entries.any {
                it.scope != scope && it.scope.userId == scope.userId && it.scope.clientId == scope.clientId
            }
            if (entry == null) {
                require(drafts.read(scope) == null && safety.read(scope) == null) {
                    "Unregistered records need recovery; they cannot be enrolled as empty"
                }
                val ledger = sequences.read(scope)
                require(if (sharedByOthers) ledger != null else ledger == null) {
                    "Sequence ledger does not match the enrolled identities; refusing to initialize"
                }
                control = saveControl(control, Control(entries = entries + Entry(scope, Phase.Preparing)))
            }
            // Preparing binds the identity BEFORE any artifact exists, so a lost acknowledgement
            // can only ever resume this exact entry, never adopt a foreign or advanced record.
            drafts.adoptReviewedBundle(scope, null)
            safety.adoptLegacy(scope, emptyList(), emptyList())
            val ledger = sequences.read(scope)
            if (ledger == null) {
                require(!sharedByOthers) { "Shared sequence ledger is missing; refusing to reset it" }
                sequences.initialize(scope, 0)
            } else if (!sharedByOthers) {
                require(ledger.highWater == 0L) { "A preparing workspace cannot own an advanced ledger" }
            }
            verifyReady(scope)
            val current = requireNotNull(control)
            saveControl(current, current.control.copy(entries = current.control.entries.map {
                if (it.scope == scope) it.copy(phase = Phase.Ready) else it
            }))
            verified += scope
        }
    }

    private suspend fun <T> enrolled(scope: PendingSubmissionScope, action: suspend () -> T): T {
        ensureEnrolled(scope)
        val value = action()
        currentCoroutineContext().ensureActive()
        requireScope(scope)
        return value
    }

    private val guardedDrafts =
        object : AwaitedWorkspaceDraftPersistence {
            override suspend fun read(scope: PendingSubmissionScope) = enrolled(scope) { drafts.read(scope) }

            override suspend fun append(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace, operation: WorkspaceOperation) =
                enrolled(scope) { drafts.append(scope, session, before, operation) }

            override suspend fun appendWithIntent(scope: PendingSubmissionScope, session: WorkspaceSession, before: Workspace,
                operation: WorkspaceOperation, intent: cg.creamgod.boarderless.domain.history.WorkspaceHistoryIntent,
                beforeHistory: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                enrolled(scope) { drafts.appendWithIntent(scope, session, before, operation, intent, beforeHistory) }

            override suspend fun stage(entry: PendingWorkspaceSubmission) = enrolled(entry.scope) { drafts.stage(entry) }

            override suspend fun acknowledge(entry: PendingWorkspaceSubmission, version: Long, seq: Long) =
                enrolled(entry.scope) { drafts.acknowledge(entry, version, seq) }

            override suspend fun acknowledgeWithHistory(entry: PendingWorkspaceSubmission, version: Long, seq: Long,
                history: cg.creamgod.boarderless.domain.history.WorkspaceHistory?) =
                enrolled(entry.scope) { drafts.acknowledgeWithHistory(entry, version, seq, history) }

            override suspend fun restore(scope: PendingSubmissionScope, id: String, current: WorkspaceSession) =
                enrolled(scope) { drafts.restore(scope, id, current) }

            override suspend fun remove(scope: PendingSubmissionScope, id: String) = enrolled(scope) { drafts.remove(scope, id) }

            override suspend fun stop(scope: PendingSubmissionScope, transactionId: String) =
                enrolled(scope) { drafts.stop(scope, transactionId) }

            override suspend fun recordStoppedEvidence(scope: PendingSubmissionScope, expectedGeneration: Long,
                submitted: PendingWorkspaceSubmission, evidence: StoppedSubmissionEvidence) =
                enrolled(scope) { drafts.recordStoppedEvidence(scope, expectedGeneration, submitted, evidence) }
        }

    private val guardedSequences =
        object : AwaitedClientSequenceAllocator {
            override suspend fun reserve(scope: PendingSubmissionScope, count: Int) = enrolled(scope) { sequences.reserve(scope, count) }

            override suspend fun advance(scope: PendingSubmissionScope, sequence: Long) = enrolled(scope) { sequences.advance(scope, sequence) }
        }

    private val guardedSafety =
        object : AwaitedRecoverySafetyPersistence {
            override suspend fun fenceState(pending: PendingWorkspaceSubmission) = enrolled(pending.scope) { safety.fenceState(pending) }

            override suspend fun beginFence(pending: PendingWorkspaceSubmission) = enrolled(pending.scope) { safety.beginFence(pending) }

            override suspend fun observeFence(pending: PendingWorkspaceSubmission, status: PendingReceiptStatus) =
                enrolled(pending.scope) { safety.observeFence(pending, status) }

            override suspend fun deletion(scope: PendingSubmissionScope, entity: String) = enrolled(scope) { safety.deletion(scope, entity) }

            override suspend fun recordDeletions(scope: PendingSubmissionScope, records: List<CommittedDeletionEvidence>) =
                enrolled(scope) { safety.recordDeletions(scope, records) }
        }

    /** Handed to the repository exactly like an activated runtime, but without a legacy identity. */
    val runtime: AwaitedSubmissionRuntime =
        AwaitedSubmissionRuntime(guardedDrafts, guardedSequences, guardedSafety, ::requireScope, activationTarget = null)
}
