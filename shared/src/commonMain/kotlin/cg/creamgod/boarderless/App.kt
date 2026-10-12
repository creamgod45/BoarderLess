package cg.creamgod.boarderless

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import cg.creamgod.boarderless.data.DraftBackupRuntime
import cg.creamgod.boarderless.data.DraftImportRuntime
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
import cg.creamgod.boarderless.data.persistence.UiPreferences
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.feature.canvas.WorkspaceMenuBridge
import cg.creamgod.boarderless.feature.canvas.WorkspaceScreen
import cg.creamgod.boarderless.feature.qa.QaRuntime
import cg.creamgod.boarderless.feature.qa.QaWorkbenchHost
import cg.creamgod.boarderless.i18n.Localization
import cg.creamgod.boarderless.i18n.activateLanguage
import cg.creamgod.boarderless.i18n.loadAvailableLanguages
import cg.creamgod.boarderless.i18n.resolveLanguage

@Composable
@Preview
fun App(
    workspaceRepository: cg.creamgod.boarderless.data.CanvasWorkspaceRepository? = null,
    workspaceStorageRuntime: cg.creamgod.boarderless.data.WorkspaceStorageRuntime? = null,
    syncServerRepository: (() -> cg.creamgod.boarderless.data.CanvasWorkspaceRepository)? = null,
    qaRuntime: QaRuntime = QaRuntime.Disabled,
    mediaImportRuntime: MediaImportRuntime = MediaImportRuntime.Unavailable,
    menuBridge: WorkspaceMenuBridge? = null,
    recentWorkspacesPublisher: RecentWorkspacesPublisher = RecentWorkspacesPublisher.None,
    draftBackupRuntime: DraftBackupRuntime = DraftBackupRuntime.Unavailable,
    draftImportRuntime: DraftImportRuntime = DraftImportRuntime.Unavailable,
    schemeBundleRuntime: cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime = cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime.Unavailable,
    canvasExportRuntime: cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime = cg.creamgod.boarderless.feature.canvas.CanvasExportRuntime.Unavailable,
    awaitedQuickSchemeRepository: cg.creamgod.boarderless.data.persistence.AwaitedQuickSchemeRepository? = null,
    quickSchemeRepository: cg.creamgod.boarderless.data.persistence.QuickSchemeRepository? = null,
) {
    val uiPreferences = remember { UiPreferences() }
    var reduceTransparency by remember { mutableStateOf(uiPreferences.reduceTransparency) }
    var reduceMotion by remember { mutableStateOf(uiPreferences.reduceMotion) }
    var languagePreference by remember { mutableStateOf(uiPreferences.language) }
    val systemLanguageTag = Locale.current.toLanguageTag()
    var languageReady by remember { mutableStateOf(false) }
    LaunchedEffect(languagePreference, systemLanguageTag) {
        if (!languageReady) Localization.languages = loadAvailableLanguages()
        activateLanguage(resolveLanguage(languagePreference, systemLanguageTag, Localization.languages))
        languageReady = true
    }
    val initialStorageChoice = remember(workspaceStorageRuntime) {
        runCatching {
            if (workspaceStorageRuntime == null) null else {
                val requested = cg.creamgod.boarderless.data.WorkspaceLaunchRequests.pending.value?.storageMode
                if (requested != null) uiPreferences.workspaceStorageMode = requested
                // An old server-mode preference must never make ordinary startup depend on
                // network availability. Explicit external workspace requests retain their scope.
                requested ?: workspaceStorageRuntime.initialMode
            }
        }
    }
    val initialSession = remember(workspaceStorageRuntime, workspaceRepository) {
        runCatching {
            val runtime = workspaceStorageRuntime
            val mode = initialStorageChoice.getOrNull()
            if (runtime != null && mode != null) {
                val created = runtime.createRepository(mode)
                try { created to runtime.mediaRuntimeFor(created) }
                catch (failure: Exception) {
                    if (created !== workspaceRepository) runCatching { created.close() }
                    throw failure
                }
            } else workspaceRepository to mediaImportRuntime
        }
    }
    var storageMode by remember(workspaceStorageRuntime, workspaceRepository) {
        mutableStateOf(if (initialSession.isFailure) null else initialStorageChoice.getOrNull())
    }
    var storagePreferenceFailed by remember(workspaceStorageRuntime, workspaceRepository) {
        mutableStateOf(initialStorageChoice.isFailure || initialSession.isFailure)
    }
    var activeSession by remember(workspaceStorageRuntime, workspaceRepository) {
        mutableStateOf(initialSession.getOrNull())
    }
    val activeRepository = activeSession?.first
    val activeMediaRuntime = if (workspaceStorageRuntime == null) mediaImportRuntime else activeSession?.second ?: mediaImportRuntime
    fun chooseStorage(chosen: cg.creamgod.boarderless.data.WorkspaceStorageMode): Boolean {
        val runtime = workspaceStorageRuntime ?: return false
        if (chosen == storageMode && activeSession != null && !storagePreferenceFailed) return true
        var candidate: cg.creamgod.boarderless.data.CanvasWorkspaceRepository? = null
        return try {
            // Acquire platform storage/lease before discarding the current screen or
            // persisting the choice. Failure retains the active repository and canvas.
            val created = runtime.createRepository(chosen)
            candidate = created
            val preparedMedia = runtime.mediaRuntimeFor(created)
            uiPreferences.workspaceStorageMode = chosen
            activeSession = created to preparedMedia
            storageMode = chosen
            storagePreferenceFailed = false
            true
        } catch (_: Exception) {
            candidate?.let { if (it !== activeRepository) runCatching { it.close() } }
            storagePreferenceFailed = true
            false
        }
    }
    // Wait for the first catalog so the UI never flashes English before switching language.
    if (!languageReady) return
    BoarderLessTheme(reduceTransparency = reduceTransparency) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (workspaceStorageRuntime != null && storageMode == null) {
                cg.creamgod.boarderless.feature.canvas.WorkspaceStorageChooser(storagePreferenceFailed) { chosen ->
                    chooseStorage(chosen)
                }
            } else key(activeRepository) {
                WorkspaceScreen(
                    workspaceRepository = activeRepository,
                    syncServerRepository = workspaceStorageRuntime?.serverRepository ?: syncServerRepository,
                    workspaceStorageMode = if (workspaceStorageRuntime == null) null else storageMode,
                    onRequestStorageMode = if (workspaceStorageRuntime == null) null else ({ chosen ->
                        chooseStorage(chosen)
                    }),
                    onChooseStorageMode = if (workspaceStorageRuntime == null) null else ({ storageMode = null; storagePreferenceFailed = false }),
                    mediaImportRuntime = activeMediaRuntime,
                    draftBackupRuntime = draftBackupRuntime,
                    draftImportRuntime = draftImportRuntime,
                    schemeBundleRuntime = schemeBundleRuntime,
                    canvasExportRuntime = canvasExportRuntime,
                    quickSchemeRepository = quickSchemeRepository,
                    awaitedQuickSchemeRepository = awaitedQuickSchemeRepository,
                    menuBridge = menuBridge,
                    recentWorkspacesPublisher = recentWorkspacesPublisher,
                    reduceTransparency = reduceTransparency,
                    onReduceTransparencyChange = { enabled ->
                        reduceTransparency = enabled
                        uiPreferences.reduceTransparency = enabled
                    },
                    reduceMotion = reduceMotion,
                    onReduceMotionChange = { enabled ->
                        reduceMotion = enabled
                        uiPreferences.reduceMotion = enabled
                    },
                    languagePreference = languagePreference,
                    onLanguagePreferenceChange = { preference ->
                        languagePreference = preference
                        uiPreferences.language = preference
                    },
                )
            }
            QaWorkbenchHost(qaRuntime)
        }
    }
}
