package cg.creamgod.boarderless

import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import cg.creamgod.boarderless.data.persistence.browserQuickSchemeRepository
import cg.creamgod.boarderless.data.persistence.browserWorkspaceStorageRuntime
import cg.creamgod.boarderless.data.browserSchemeBundleRuntime
import cg.creamgod.boarderless.data.browserDraftBackupRuntime
import cg.creamgod.boarderless.data.browserCanvasExportRuntime
import cg.creamgod.boarderless.data.browserDraftImportRuntime
import cg.creamgod.boarderless.data.rememberBrowserMediaImportRuntime

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        WithFontResourcesLoaded {
            val mediaImportRuntime = rememberBrowserMediaImportRuntime()
            val workspaceStorageRuntime = remember(mediaImportRuntime.playbackActivity) {
                browserWorkspaceStorageRuntime(mediaImportRuntime.playbackActivity)
            }
            val draftBackupRuntime = remember { browserDraftBackupRuntime() }
            val draftImportRuntime = remember { browserDraftImportRuntime() }
            val canvasExportRuntime = remember { browserCanvasExportRuntime() }
            val schemeBundleRuntime = remember { browserSchemeBundleRuntime() }
            val schemeRepository = remember { browserQuickSchemeRepository() }
            App(
                workspaceStorageRuntime = workspaceStorageRuntime,
                legacyQuickSchemeReader = remember { cg.creamgod.boarderless.data.persistence.legacyQuickSchemeCatalogReader() },
                awaitedQuickSchemeRepository = schemeRepository,
                schemeBundleRuntime = schemeBundleRuntime,
                canvasExportRuntime = canvasExportRuntime,
                mediaImportRuntime = mediaImportRuntime,
                draftBackupRuntime = draftBackupRuntime,
                draftImportRuntime = draftImportRuntime,
            )
        }
    }
}
