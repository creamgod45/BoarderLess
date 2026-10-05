package cg.creamgod.boarderless.data.remote

import com.russhwolf.settings.Settings
import kotlinx.coroutines.sync.Mutex

class SessionPreferences(
    private val settings: Settings = Settings(),
) {
    internal val pendingSubmissions = PendingWorkspaceSubmissionStore(settings)
    internal val draftJournals = WorkspaceDraftJournalStore(settings)
    // Configure before constructing a repository, never switch a live scope without migration.
    internal var draftPersistence: WorkspaceDraftPersistence = SettingsWorkspaceDraftPersistence(draftJournals, pendingSubmissions)
    internal val submissionMutex = Mutex()
    internal var sequenceAllocator: ClientSequenceAllocator? = null

    /** Called under submissionMutex. Gaps after failure are intentional; never recycle a range. */
    internal fun reserveClientSequences(count: Int): LongRange {
        require(count in 1..200)
        val current = clientSequence
        require(current in 0..MaxSafeSequence && current <= MaxSafeSequence - count)
        val last = current + count
        clientSequence = last // Persist before publishing a new exact wire or starting transport.
        return (current + 1)..last
    }

    internal fun advanceClientSequence(sequence: Long) {
        require(sequence in 0..MaxSafeSequence)
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
        get() = settings.getStringOrNull(ClientIdKey) ?: randomUuid().also {
            settings.putString(ClientIdKey, it)
        }

    var clientSequence: Long
        get() = settings.getLong(ClientSequenceKey, 0L)
        set(value) = settings.putLong(ClientSequenceKey, value)

    fun clearIdentity() {
        userId = null
        workspaceId = null
    }

    private fun Settings.putOrRemove(key: String, value: String?) {
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
