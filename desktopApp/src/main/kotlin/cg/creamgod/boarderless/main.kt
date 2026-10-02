package cg.creamgod.boarderless

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.awt.ComposeWindow
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import cg.creamgod.boarderless.feature.canvas.WorkspaceMenuBridge
import cg.creamgod.boarderless.i18n.Strings
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.desktop.AppReopenedListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) {
    if ("--check-media-runtime" in args) {
        require(args.size == 1) { "Media runtime check does not accept additional arguments" }
        DesktopMediaRuntimeCheck.main(emptyArray())
        return
    }
    val requestedWorkspace = args.firstOrNull { it.startsWith(OpenWorkspaceArgument) }?.removePrefix(OpenWorkspaceArgument)
    // A jump list entry starts a new process; let the running app open the workspace instead.
    if (requestedWorkspace != null && DesktopInstanceChannel.forward(requestedWorkspace)) return
    requestedWorkspace?.let(WorkspaceLaunchRequests::open)
    // Like other Mac apps, closing the window keeps the app running: the Dock icon brings the window
    // back and Quit (⌘Q) ends the app. Elsewhere closing the window ends the app.
    val closeHidesWindow = isMacOs
    var windowVisible by mutableStateOf(true)
    val mainWindow = AtomicReference<ComposeWindow?>()
    val showMainWindow = {
        EventQueue.invokeLater {
            windowVisible = true
            mainWindow.get()?.bringToFront()
        }
    }
    val openWorkspace: (String) -> Unit = { workspaceId ->
        WorkspaceLaunchRequests.open(workspaceId)
        showMainWindow()
    }
    DesktopInstanceChannel.listen(openWorkspace)
    if (isMacOs) {
        System.setProperty("apple.laf.useScreenMenuBar", "true")
        // The app menu title when run unbundled; matches app.name in the i18n catalogs.
        System.setProperty(
            "apple.awt.application.name",
            if (Locale.getDefault().language == "zh") "無邊界" else "BoarderLess",
        )
    }
    // After the apple.* properties: Taskbar initializes AWT.
    val recentWorkspacesPublisher = desktopRecentWorkspacesPublisher(openWorkspace)
    if (closeHidesWindow && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_EVENT_REOPENED)) {
        // Clicking the Dock icon.
        Desktop.getDesktop().addAppEventListener(AppReopenedListener { showMainWindow() })
    }
    val menuBridge = WorkspaceMenuBridge()
    val appIcon = BitmapPainter(
        checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")) { "Missing app-icon.png" }
            .use(::loadImageBitmap)
    )
    application {
        val closeWindow = if (closeHidesWindow) ({ windowVisible = false }) else ::exitApplication
        Window(
            onCloseRequest = closeWindow,
            visible = windowVisible,
            title = Strings.app.name(),
            icon = appIcon,
        ) {
            if (isMacOs) WorkspaceMenuBar(menuBridge, onCloseWindow = closeWindow)
            DisposableEffect(window) {
                mainWindow.set(window)
                onDispose { mainWindow.compareAndSet(window, null) }
            }
            val qaRuntime = remember(window) { desktopQaRuntime(window) }
            val mediaLifecycle = remember(window) { DesktopMediaWindowLifecycle() }
            DisposableEffect(window, mediaLifecycle) {
                val binding = mediaLifecycle.bind(window, closeHidesWindow)
                onDispose { binding.close() }
            }
            val mediaImportRuntime = remember(window, mediaLifecycle) { desktopMediaImportRuntime(window, mediaLifecycle.activity) }
            App(
                qaRuntime = qaRuntime,
                mediaImportRuntime = mediaImportRuntime,
                menuBridge = menuBridge,
                recentWorkspacesPublisher = recentWorkspacesPublisher,
            )
        }
    }
}
