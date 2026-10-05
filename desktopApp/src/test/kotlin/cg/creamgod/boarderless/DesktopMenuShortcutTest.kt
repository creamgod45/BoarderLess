package cg.creamgod.boarderless

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class DesktopMenuShortcutTest {
    @Test
    fun unavailableCanvasCommandsDoNotRegisterNativeEditingShortcuts() {
        for (key in listOf(Key.V, Key.C, Key.X, Key.A, Key.Z, Key.D)) {
            val shortcut = KeyShortcut(key, meta = true)
            assertNull(workspaceMenuShortcut(false, shortcut))
            assertSame(shortcut, workspaceMenuShortcut(true, shortcut))
        }
        assertNull(workspaceMenuShortcut(true, null))
    }
}
