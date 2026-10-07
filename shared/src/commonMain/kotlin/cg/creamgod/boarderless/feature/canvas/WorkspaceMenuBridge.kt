package cg.creamgod.boarderless.feature.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Lets a host menu bar (the macOS screen menu) show workspace commands and run them through the
 * same code as the command palette. Ids are command palette entry ids plus [WorkspaceMenuCommands].
 * Keyboard shortcuts stay with the canvas key handler; the host only runs a command when its menu
 * item is clicked.
 */
class WorkspaceMenuBridge {
    /** Command id → enabled, for every command the workspace currently offers. */
    var commands: Map<String, Boolean> by mutableStateOf(emptyMap())
        internal set

    internal var handler: ((String) -> Unit)? = null

    fun has(id: String): Boolean = id in commands

    fun isEnabled(id: String): Boolean = commands[id] == true

    fun run(id: String) {
        if (isEnabled(id)) handler?.invoke(id)
    }
}

/** Menu commands that have a keyboard shortcut but no command palette entry. */
object WorkspaceMenuCommands {
    const val CommandPalette = "command-palette"
    const val Cut = "cut"
    const val Copy = "copy"
    const val Paste = "paste"
    const val Duplicate = "duplicate"
    const val SelectAll = "select-all"
}
