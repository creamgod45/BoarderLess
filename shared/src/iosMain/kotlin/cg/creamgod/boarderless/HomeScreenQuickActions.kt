package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.MaxRecentWorkspaces
import cg.creamgod.boarderless.data.RecentWorkspace
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationShortcutIcon
import platform.UIKit.UIApplicationShortcutItem
import platform.UIKit.shortcutItems

private const val OpenWorkspaceType = "cg.creamgod.boarderless.openWorkspace"
private const val WorkspaceIdKey = "workspaceId"

/** Recent workspaces as Home Screen quick actions (long-press the app icon). */
internal object HomeScreenQuickActions : RecentWorkspacesPublisher {
    override fun publish(workspaces: List<RecentWorkspace>) {
        UIApplication.sharedApplication.shortcutItems = workspaces.take(MaxRecentWorkspaces).map { workspace ->
            UIApplicationShortcutItem(
                type = OpenWorkspaceType,
                localizedTitle = workspace.title,
                localizedSubtitle = null,
                icon = UIApplicationShortcutIcon.iconWithSystemImageName("square.grid.2x2"),
                userInfo = mapOf(WorkspaceIdKey to workspace.id),
            )
        }
    }
}

/** Called by the app's scene delegate with the quick action the user chose; false if it is not ours. */
fun handleQuickAction(item: UIApplicationShortcutItem): Boolean {
    if (item.type != OpenWorkspaceType) return false
    val workspaceId = item.userInfo?.get(WorkspaceIdKey) as? String ?: return false
    WorkspaceLaunchRequests.open(workspaceId)
    return true
}
