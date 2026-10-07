package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.domain.model.WorkspaceId
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MaxRecentWorkspaces = 5

@Serializable
data class RecentWorkspace(
    val id: String,
    val title: String,
)

/** Most recently opened first, without duplicates, at most [MaxRecentWorkspaces]. */
fun List<RecentWorkspace>.withOpened(
    id: String,
    title: String,
): List<RecentWorkspace> = (listOf(RecentWorkspace(id, title)) + filterNot { it.id == id }).take(MaxRecentWorkspaces)

/** Keeps only workspaces the user can still open, with their current titles. */
fun List<RecentWorkspace>.reconciledWith(available: List<WorkspaceSummary>): List<RecentWorkspace> {
    val titles = available.associate { it.id.value to it.title }
    return mapNotNull { recent -> titles[recent.id]?.let { recent.copy(title = it) } }
}

/** The recently opened workspaces, kept in the app's settings so they survive restarts. */
class RecentWorkspaceStore(
    private val settings: Settings = Settings(),
) {
    var items: List<RecentWorkspace>
        get() =
            settings
                .getStringOrNull(RecentWorkspacesKey)
                ?.let { runCatching { StoreJson.decodeFromString<List<RecentWorkspace>>(it) }.getOrNull() }
                .orEmpty()
                .take(MaxRecentWorkspaces)
        private set(value) = settings.putString(RecentWorkspacesKey, StoreJson.encodeToString(value))

    fun recordOpened(
        id: WorkspaceId,
        title: String,
    ) {
        items = items.withOpened(id.value, title)
    }

    fun rename(
        id: WorkspaceId,
        title: String,
    ) {
        items = items.map { if (it.id == id.value) it.copy(title = title) else it }
    }

    fun remove(id: WorkspaceId) {
        items = items.filterNot { it.id == id.value }
    }

    fun reconcile(available: List<WorkspaceSummary>) {
        items = items.reconciledWith(available)
    }

    private companion object {
        const val RecentWorkspacesKey = "launcher.recentWorkspaces"
        val StoreJson = Json { ignoreUnknownKeys = true }
    }
}

/**
 * Shows the recent workspaces outside the app: the macOS Dock menu, the Windows jump list, Android
 * launcher shortcuts and iOS Home Screen quick actions.
 */
fun interface RecentWorkspacesPublisher {
    fun publish(workspaces: List<RecentWorkspace>)

    companion object {
        val None = RecentWorkspacesPublisher {}
    }
}

/**
 * Requests from outside the app (a Dock menu item, jump list entry, launcher shortcut or quick
 * action) to open a workspace. The workspace screen opens the pending request and consumes it.
 */
object WorkspaceLaunchRequests {
    private val pendingRequest = MutableStateFlow<WorkspaceId?>(null)
    val pending: StateFlow<WorkspaceId?> = pendingRequest.asStateFlow()

    /** Ignores ids that cannot be workspace ids, since they come from outside the app. */
    fun open(workspaceId: String) {
        if (isWorkspaceId(workspaceId)) pendingRequest.value = WorkspaceId(workspaceId)
    }

    fun consume(workspaceId: WorkspaceId) {
        pendingRequest.compareAndSet(workspaceId, null)
    }

    fun isWorkspaceId(value: String): Boolean = WorkspaceIdPattern.matches(value)

    private val WorkspaceIdPattern = Regex("[A-Za-z0-9_-]{1,128}")
}
