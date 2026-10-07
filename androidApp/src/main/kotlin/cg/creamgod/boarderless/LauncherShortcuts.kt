package cg.creamgod.boarderless

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import cg.creamgod.boarderless.data.MaxRecentWorkspaces
import cg.creamgod.boarderless.data.RecentWorkspace
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests

private const val ExtraWorkspaceId = "cg.creamgod.boarderless.extra.WORKSPACE_ID"

/** Recent workspaces as dynamic launcher shortcuts (long-press the app icon). Needs Android 7.1. */
internal class LauncherShortcuts(private val context: Context) : RecentWorkspacesPublisher {
    override fun publish(workspaces: List<RecentWorkspace>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val shortcuts = workspaces
            .take(minOf(MaxRecentWorkspaces, manager.maxShortcutCountPerActivity))
            .mapIndexed { rank, workspace ->
                val label = workspace.title.ifBlank { context.getString(R.string.app_name) }
                ShortcutInfo.Builder(context, "workspace:${workspace.id}")
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(
                        Intent(Intent.ACTION_VIEW)
                            .setClass(context, MainActivity::class.java)
                            .putExtra(ExtraWorkspaceId, workspace.id),
                    )
                    .setRank(rank)
                    .build()
            }
        // Rate limited while the app is in the background; the next publish catches up.
        runCatching { manager.dynamicShortcuts = shortcuts }
    }
}

/** Opens the workspace a launcher shortcut was started with, if any. */
internal fun Intent.openRequestedWorkspace() {
    getStringExtra(ExtraWorkspaceId)?.let(WorkspaceLaunchRequests::open)
}
