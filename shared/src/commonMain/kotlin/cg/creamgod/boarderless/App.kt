package cg.creamgod.boarderless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import cg.creamgod.boarderless.data.persistence.UiPreferences
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.RecentWorkspacesPublisher
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
    qaRuntime: QaRuntime = QaRuntime.Disabled,
    mediaImportRuntime: MediaImportRuntime = MediaImportRuntime.Unavailable,
    menuBridge: WorkspaceMenuBridge? = null,
    recentWorkspacesPublisher: RecentWorkspacesPublisher = RecentWorkspacesPublisher.None,
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
    // Wait for the first catalog so the UI never flashes English before switching language.
    if (!languageReady) return
    BoarderLessTheme(reduceTransparency = reduceTransparency) {
        Box(modifier = Modifier.fillMaxSize()) {
            WorkspaceScreen(
                mediaImportRuntime = mediaImportRuntime,
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
            QaWorkbenchHost(qaRuntime)
        }
    }
}
