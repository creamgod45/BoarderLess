package cg.creamgod.boarderless.data.remote

import com.russhwolf.settings.Settings

class SessionPreferences(
    private val settings: Settings = Settings(),
) {
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
        const val UserIdKey = "backend.userId"
        const val WorkspaceIdKey = "backend.workspaceId"
        const val ClientIdKey = "backend.clientId"
        const val ClientSequenceKey = "backend.clientSequence"
    }
}
