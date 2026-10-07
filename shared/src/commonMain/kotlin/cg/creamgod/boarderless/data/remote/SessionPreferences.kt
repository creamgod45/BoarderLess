package cg.creamgod.boarderless.data.remote

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex

class SessionPreferences(
    private val settings: Settings = Settings(),
) {
    internal val pendingSubmissions = PendingWorkspaceSubmissionStore(settings)
    internal val draftJournals = WorkspaceDraftJournalStore(settings)
    internal var deletionEvidence = CommittedDeletionStore(settings)
        private set
    internal var fenceAttempts = PendingFenceAttemptStore(settings)
        private set
    internal constructor(settings: Settings, safety: RecoverySafetyPersistence) : this(settings) {
        deletionEvidence = CommittedDeletionStore(settings, safety)
        fenceAttempts = PendingFenceAttemptStore(settings, safety)
    }

    // Configure before constructing a repository, never switch a live scope without migration.
    internal var draftPersistence: WorkspaceDraftPersistence = SettingsWorkspaceDraftPersistence(draftJournals, pendingSubmissions)

    // Set only by a verified native activation before any repository is constructed.
    private var nativeClientIdentity: (() -> String)? = null
    internal constructor(
        settings: Settings,
        safety: RecoverySafetyPersistence,
        drafts: WorkspaceDraftPersistence,
        sequences: ClientSequenceAllocator,
        requireNativeClientIdentity: () -> String,
    ) : this(settings, safety) {
        draftPersistence = drafts
        sequenceAllocator = sequences
        nativeClientIdentity = requireNativeClientIdentity
    }

    internal val submissionMutex = Mutex()
    internal var sequenceAllocator: ClientSequenceAllocator? = null

    /** Quiescent migration read only. Never calls the clientId/default-counter getters, which
     * could initialize missing identity or map missing evidence to zero. A double read detects
     * observed changes, NOT non-cooperating writers; the caller must keep them excluded.
     */
    internal fun captureLegacySequenceForMigration(
        scope: PendingSubmissionScope,
        knownScopes: List<PendingSubmissionScope>,
        legacyArchiveCatalog: List<StoppedSubmissionLocator>,
        bundles: List<WorkspaceDraftScopeBundle>,
    ): LegacySequenceCapture {
        val client = checkNotNull(settings.getStringOrNull(ClientIdKey)) { "Missing legacy client identity" }
        check(client == scope.clientId)
        val counter = checkNotNull(settings.getLongOrNull(ClientSequenceKey)) { "Missing legacy client sequence counter" }
        val journals = draftJournals.inventoryForMigration(knownScopes)
        val pending = pendingSubmissions.inventoryForMigration(knownScopes)
        val stopped = pendingSubmissions.stoppedInventoryForMigration(legacyArchiveCatalog)
        val floor = CapturedClientSequenceFloor.calculate(scope, counter, pending, bundles, stopped)
        val fences = fenceAttempts.inventoryForMigration()
        val deletions = deletionEvidence.inventoryForMigration()
        val knownWires =
            pending + stopped +
                bundles.flatMap {
                    listOfNotNull(it.pending, it.stoppedPending, it.acknowledged?.submitted) +
                        it.retainedStopped.map { archive -> archive.submitted }
                }
        val unboundFences =
            fences.filter { fence ->
                val matching =
                    knownWires.filter {
                        it.scope == fence.scope && it.request.transactionId == fence.transactionId
                    }
                matching.forEach { fenceAttempts.requireMatches(fence, it) }
                matching.isEmpty()
            }
        check(settings.getStringOrNull(ClientIdKey) == client && settings.getLongOrNull(ClientSequenceKey) == counter)
        check(draftJournals.inventoryForMigration(knownScopes) == journals)
        check(pendingSubmissions.inventoryForMigration(knownScopes) == pending)
        check(pendingSubmissions.stoppedInventoryForMigration(legacyArchiveCatalog) == stopped)
        check(fenceAttempts.inventoryForMigration() == fences && deletionEvidence.inventoryForMigration() == deletions)
        check(settings.getStringOrNull(ClientIdKey) == client && settings.getLongOrNull(ClientSequenceKey) == counter)
        return LegacySequenceCapture(counter, floor, pending, stopped, journals, fences, deletions, unboundFences)
    }

    /** Called under submissionMutex. Gaps after failure are intentional; never recycle a range. */
    internal fun reserveClientSequences(count: Int): LongRange {
        check(nativeClientIdentity == null) { "Native sequences require a scoped allocator" }
        require(count in 1..200)
        val current = clientSequence
        require(current in 0..MaxSafeSequence && current <= MaxSafeSequence - count)
        val last = current + count
        clientSequence = last // Persist before publishing a new exact wire or starting transport.
        return (current + 1)..last
    }

    internal fun advanceClientSequence(sequence: Long) {
        require(sequence in 0..MaxSafeSequence)
        nativeClientIdentity?.let {
            it()
            return
        } // Native allocator already advanced; never mutate the frozen legacy source.
        val current = clientSequence
        require(current in 0..MaxSafeSequence)
        clientSequence = maxOf(current, sequence)
    }

    var userId: String?
        get() = settings.getStringOrNull(UserIdKey)
        set(value) = settings.putOrRemove(UserIdKey, value)

    var workspaceId: String?
        get() = settings.getStringOrNull(WorkspaceIdKey)
        set(value) = settings.putOrRemove(WorkspaceIdKey, value)

    val clientId: String
        get() =
            nativeClientIdentity?.invoke() ?: settings.getStringOrNull(ClientIdKey) ?: randomUuid().also {
                settings.putString(ClientIdKey, it)
            }

    var clientSequence: Long
        get() {
            check(nativeClientIdentity == null) { "Native sequences require a scoped allocator" }
            return settings.getLong(ClientSequenceKey, 0L)
        }
        set(value) {
            check(nativeClientIdentity == null) { "Native sequences require a scoped allocator" }
            settings.putLong(ClientSequenceKey, value)
        }

    fun clearIdentity() {
        userId = null
        workspaceId = null
    }

    private fun Settings.putOrRemove(
        key: String,
        value: String?,
    ) {
        if (value == null) remove(key) else putString(key, value)
    }

    private companion object {
        const val MaxSafeSequence = 9_007_199_254_740_991L
        const val UserIdKey = "backend.userId"
        const val WorkspaceIdKey = "backend.workspaceId"
        const val ClientIdKey = "backend.clientId"
        const val ClientSequenceKey = "backend.clientSequence"
    }
}
