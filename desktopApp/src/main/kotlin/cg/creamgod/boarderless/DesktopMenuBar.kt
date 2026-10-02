package cg.creamgod.boarderless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuScope
import cg.creamgod.boarderless.feature.canvas.WorkspaceMenuBridge
import cg.creamgod.boarderless.feature.canvas.WorkspaceMenuCommands
import cg.creamgod.boarderless.i18n.Strings
import kotlinx.coroutines.delay
import java.awt.EventQueue
import java.awt.event.KeyEvent

internal val isMacOs = System.getProperty("os.name").orEmpty().startsWith("Mac", ignoreCase = true)

/**
 * The macOS menu bar: lists the workspace commands with their shortcuts and runs them on click.
 * The canvas key handler still owns every shortcut. A shortcut that reaches a menu item is one the
 * canvas declined (for example while text is being edited), so the item ignores it. The Window
 * menu and the app menu's titles are native, see [MacNativeMenus].
 */
@Composable
internal fun FrameWindowScope.WorkspaceMenuBar(bridge: WorkspaceMenuBridge, onCloseWindow: () -> Unit) {
    MenuBar {
        Menu(Strings.menu.file()) {
            Command(bridge, "new-thought", Strings.content.newThought(), keyHint = "N")
            if (bridge.has("cancel-media-import")) {
                Command(bridge, "cancel-media-import", Strings.media.cancelImport())
            } else if (bridge.has("import-media")) {
                Command(bridge, "import-media", Strings.media.importMedia())
            }
            Separator()
            Command(bridge, WorkspaceMenuCommands.CommandPalette, Strings.palette.commandPalette(), KeyShortcut(Key.K, meta = true))
            Separator()
            Item(Strings.menu.closeWindow(), shortcut = KeyShortcut(Key.W, meta = true), onClick = onCloseWindow)
        }
        Menu(Strings.menu.edit()) {
            Command(bridge, "undo", Strings.palette.undo(), KeyShortcut(Key.Z, meta = true))
            Command(bridge, "redo", Strings.palette.redo(), KeyShortcut(Key.Z, meta = true, shift = true))
            Separator()
            Command(bridge, WorkspaceMenuCommands.Cut, Strings.inspector.cut(), KeyShortcut(Key.X, meta = true))
            Command(bridge, WorkspaceMenuCommands.Copy, Strings.inspector.copy(), KeyShortcut(Key.C, meta = true))
            Command(bridge, WorkspaceMenuCommands.Paste, Strings.inspector.paste(), KeyShortcut(Key.V, meta = true))
            Command(bridge, WorkspaceMenuCommands.Duplicate, Strings.inspector.duplicate(), KeyShortcut(Key.D, meta = true))
            Command(bridge, "delete", Strings.palette.deleteSelection(), keyHint = "⌫")
            Separator()
            Command(bridge, WorkspaceMenuCommands.SelectAll, Strings.common.selectAll(), KeyShortcut(Key.A, meta = true))
        }
        Menu(Strings.menu.arrange()) {
            Command(bridge, "edit-selected", Strings.palette.editSelectedThought(), keyHint = "↩")
            Separator()
            Command(bridge, "group", Strings.palette.groupSelection(), KeyShortcut(Key.G, meta = true))
            Command(bridge, "ungroup", Strings.palette.ungroup(), KeyShortcut(Key.G, meta = true, shift = true))
        }
        Menu(Strings.menu.view()) {
            Command(bridge, "fit-content", Strings.palette.fitContent(), keyHint = "F")
            Command(bridge, "zoom-in", Strings.palette.zoomIn())
            Command(bridge, "zoom-out", Strings.palette.zoomOut())
            Command(bridge, "reset-zoom", Strings.palette.resetZoomTo100Percent())
            Separator()
            Command(bridge, "layers", Strings.palette.openLayers())
            Command(bridge, "history", Strings.palette.openLocalHistory())
        }
    }
    val appName = Strings.app.name()
    val nativeMenuTitles = MacNativeMenus.Titles(
        window = Strings.menu.window(),
        minimize = Strings.menu.minimize(),
        zoom = Strings.menu.zoom(),
        bringAllToFront = Strings.menu.bringAllToFront(),
        about = Strings.menu.about(appName),
        services = Strings.menu.services(),
        hide = Strings.menu.hide(appName),
        hideOthers = Strings.menu.hideOthers(),
        showAll = Strings.menu.showAll(),
        quit = Strings.menu.quit(appName),
    )
    LaunchedEffect(nativeMenuTitles) {
        while (true) {
            MacNativeMenus.update(nativeMenuTitles)
            delay(500)
        }
    }
}

/**
 * [shortcut] registers a native key equivalent. Single keys without a modifier are only shown via
 * [keyHint]: as native key equivalents macOS could take them away from text fields.
 */
@Composable
private fun MenuScope.Command(
    bridge: WorkspaceMenuBridge,
    id: String,
    text: String,
    shortcut: KeyShortcut? = null,
    keyHint: String? = null,
) {
    Item(
        text = if (keyHint == null) text else "$text  $keyHint",
        enabled = bridge.isEnabled(id),
        shortcut = shortcut,
        onClick = { if (EventQueue.getCurrentEvent() !is KeyEvent) bridge.run(id) },
    )
}
