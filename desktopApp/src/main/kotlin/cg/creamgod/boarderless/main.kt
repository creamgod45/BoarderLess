package cg.creamgod.boarderless

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BoarderLess",
    ) {
        val qaRuntime = remember(window) { desktopQaRuntime(window) }
        App(qaRuntime = qaRuntime)
    }
}
