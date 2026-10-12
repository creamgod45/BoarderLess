package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserWorkspaceStorageRuntimeTest {
    @Test fun productionFactoryReopensLocalDocumentsAndProvidesLocalMediaWithoutHttpFallback() = runTest {
        browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("allow")
        try {
            val activity = MutableStateFlow(MediaPlaybackActivity())
            val runtime = browserWorkspaceStorageRuntime(activity)
            val local = runtime.localRepository()
            assertTrue(local.isLocalOnly); assertTrue(local.awaitsDraftPublication)
            assertIs<AssetDownloadGateway>(local.localAssetGateway)
            val media = runtime.mediaRuntimeFor(local)
            assertSame(activity, media.playbackActivity)
            assertNotNull(media.selectSource); assertNotNull(media.loadPreview)
            assertNotNull(media.loadGif); assertNotNull(media.loadVideo); assertNotNull(media.loadExportImage)
            val session = local.openOrCreateWorkspace()
            val node = TextNode(CanvasObjectId("mode-text"), transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(120f, 80f)), text = "本機文件🙂")
            local.retainDraftAwaited(session, session.workspace, CreateObjectsOperation("mode-save", listOf(node)))
            local.close()
            assertFails { local.refresh(session) }
            val reopened = runtime.localRepository()
            assertEquals(node, reopened.openOrCreateWorkspace().workspace.objects[node.id])
            val missingGateway = object : CanvasWorkspaceRepository by reopened {
                override val localAssetGateway: AssetTransferGateway? = null
            }
            assertFails { runtime.mediaRuntimeFor(missingGateway) }
            // Server mode creates the existing backend repository, without opening a workspace or a request.
            val server = runtime.serverRepository()
            try {
                assertFalse(server.isLocalOnly)
                assertNull(server.localAssetGateway)
                assertSame(activity, runtime.mediaRuntimeFor(server).playbackActivity)
            } finally { server.close() }
            reopened.close()
        } finally { browserOriginalFixtureRestore(); browserRecordFixtureRestore() }
    }
    @Test fun persistedModePreferenceReopensAndDeniedDocumentStorageDoesNotBecomeMemoryRepository() = runTest {
        val preferences = UiPreferences()
        val original = preferences.workspaceStorageMode
        try {
            preferences.workspaceStorageMode = WorkspaceStorageMode.Local
            assertEquals(WorkspaceStorageMode.Local, UiPreferences().workspaceStorageMode)
            preferences.workspaceStorageMode = WorkspaceStorageMode.Server
            assertEquals(WorkspaceStorageMode.Server, UiPreferences().workspaceStorageMode)
            preferences.workspaceStorageMode = null
            assertNull(UiPreferences().workspaceStorageMode)
        } finally { preferences.workspaceStorageMode = original }
        browserRecordFixtureInstall("denied")
        try {
            val local = browserWorkspaceStorageRuntime().localRepository()
            assertTrue(local.isLocalOnly)
            assertFails { local.openOrCreateWorkspace() }
            local.close()
        } finally { browserRecordFixtureRestore() }
    }
}
