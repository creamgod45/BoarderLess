package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.RecentWorkspace
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
import cg.creamgod.boarderless.i18n.Strings
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.Frame
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.Taskbar

/**
 * Recent workspaces on the macOS Dock menu (right-click the Dock icon) or the Windows taskbar jump
 * list. [onOpen] runs on the AWT event thread for a Dock menu item; a jump list entry reaches the
 * app through [DesktopInstanceChannel] instead.
 */
internal fun desktopRecentWorkspacesPublisher(onOpen: (String) -> Unit): RecentWorkspacesPublisher =
    when {
        isMacOs && Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.MENU) -> {
            RecentWorkspacesPublisher { workspaces -> EventQueue.invokeLater { Taskbar.getTaskbar().menu = dockMenu(workspaces, onOpen) } }
        }

        isWindows -> {
            System
                .getProperty("jpackage.app-path")
                ?.let(::WindowsJumpList)
                ?.let { jumpList -> RecentWorkspacesPublisher { jumpList.publish(Strings.launcher.recentWorkspaces(), it) } }
                ?: RecentWorkspacesPublisher.None
        }

        else -> {
            RecentWorkspacesPublisher.None
        }
    }

private fun dockMenu(
    workspaces: List<RecentWorkspace>,
    onOpen: (String) -> Unit,
) = PopupMenu().apply {
    workspaces.forEach { workspace ->
        add(MenuItem(workspace.title).apply { addActionListener { onOpen(workspace.id) } })
    }
}

internal fun Frame.bringToFront() {
    if (extendedState and Frame.ICONIFIED != 0) extendedState = extendedState and Frame.ICONIFIED.inv()
    isVisible = true
    toFront()
    requestFocus()
    if (isMacOs) runCatching { Desktop.getDesktop().requestForeground(true) }
}
