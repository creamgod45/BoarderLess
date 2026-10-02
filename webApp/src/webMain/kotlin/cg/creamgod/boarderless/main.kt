package cg.creamgod.boarderless

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.compose.runtime.remember
import cg.creamgod.boarderless.data.browserMediaImportRuntime

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded {
            val mediaImportRuntime = remember { browserMediaImportRuntime() }
            App(mediaImportRuntime = mediaImportRuntime)
        }
    }
}
