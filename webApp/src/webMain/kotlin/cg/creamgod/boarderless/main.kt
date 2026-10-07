package cg.creamgod.boarderless

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import cg.creamgod.boarderless.data.browserDraftBackupRuntime
import cg.creamgod.boarderless.data.browserDraftImportRuntime
import cg.creamgod.boarderless.data.rememberBrowserMediaImportRuntime

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded {
            val mediaImportRuntime = rememberBrowserMediaImportRuntime()
            val draftBackupRuntime = remember { browserDraftBackupRuntime() }
            val draftImportRuntime = remember { browserDraftImportRuntime() }
            App(
                mediaImportRuntime = mediaImportRuntime,
                draftBackupRuntime = draftBackupRuntime,
                draftImportRuntime = draftImportRuntime,
            )
        }
    }
}
