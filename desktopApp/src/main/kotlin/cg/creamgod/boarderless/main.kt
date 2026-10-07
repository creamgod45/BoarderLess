package cg.creamgod.boarderless

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import cg.creamgod.boarderless.data.WorkspaceLaunchRequests
import cg.creamgod.boarderless.feature.canvas.WorkspaceMenuBridge
import cg.creamgod.boarderless.i18n.Strings
import java.awt.Desktop
import java.awt.EventQueue
import java.awt.desktop.AppReopenedListener
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JOptionPane

fun main(args: Array<String>) {
    if ("--check-media-runtime" in args) {
        require(args.size == 1) { "Media runtime check does not accept additional arguments" }
        DesktopMediaRuntimeCheck.main(emptyArray())
        return
    }
    val requestedWorkspace = args.firstOrNull { it.startsWith(OpenWorkspaceArgument) }?.removePrefix(OpenWorkspaceArgument)
    // A jump list entry starts a new process; let the running app open the workspace instead.
    if (if (requestedWorkspace != null) DesktopInstanceChannel.forward(requestedWorkspace) else DesktopInstanceChannel.focus()) return
    val lease =
        try {
            DesktopStorageLease.acquire(Path.of(System.getProperty("user.home"), ".boarderless-storage"))
        } catch (failure: Exception) {
            val message =
                if (failure is DesktopStorageBusyException) {
                    if (Locale.getDefault().language == "zh") {
                        "另一個 BoarderLess 正在使用這些檔案。請切換至原視窗，或關閉其他 BoarderLess 後再試。"
                    } else {
                        "Another BoarderLess is using these files. Switch to its window, or close the other BoarderLess and try again."
                    }
                } else if (failure is java.nio.file.NoSuchFileException) {
                    if (Locale.getDefault().language == "zh") {
                        "找不到本機資料夾，BoarderLess 無法啟動。請確認使用者資料夾仍存在，然後重新開啟 APP。"
                    } else {
                        "The local folder could not be found, so BoarderLess cannot start. Check that your user folder still exists, then reopen the APP."
                    }
                } else {
                    if (Locale.getDefault().language == "zh") {
                        "BoarderLess 無法使用本機資料夾，因此尚未啟動。請確認資料夾可讀寫，然後重新開啟 APP。"
                    } else {
                        "BoarderLess cannot use the local folder and has not started. Check that the folder can be read and written, then reopen the APP."
                    }
                }
            JOptionPane.showMessageDialog(null, message, "BoarderLess", JOptionPane.WARNING_MESSAGE)
            return
        }
    val releaseHook = Thread({ lease.close() }, "BoarderLess storage lease release")
    Runtime.getRuntime().addShutdownHook(releaseHook)
    try {
        lease.use { runDesktopApplication(requestedWorkspace, "--server" in args) }
    } finally {
        runCatching { Runtime.getRuntime().removeShutdownHook(releaseHook) }
    }
}

private fun runDesktopApplication(
    requestedWorkspace: String?,
    serverMode: Boolean,
) {
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
    DesktopInstanceChannel.listen(openWorkspace, showMainWindow)
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
    val appIcon =
        BitmapPainter(
            checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("app-icon.png")) { "Missing app-icon.png" }
                .use(::loadImageBitmap),
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
            val draftBackupRuntime = remember(window) { desktopDraftBackupRuntime(window) }
            val draftImportRuntime = remember(window) { desktopDraftImportRuntime(window) }
            val schemeBundleRuntime = remember(window) { desktopSchemeBundleRuntime(window) }
            val mediaLifecycle = remember(window) { DesktopMediaWindowLifecycle() }
            DisposableEffect(window, mediaLifecycle) {
                val binding = mediaLifecycle.bind(window, closeHidesWindow)
                onDispose { binding.close() }
            }
            val workspaceRepository =
                remember(serverMode) {
                    if (serverMode) {
                        cg.creamgod.boarderless.data.remote.desktopNativeBackendWorkspaceRepository(
                            Path.of(System.getProperty("user.home"), ".boarderless-storage", "server-v1"),
                        )
                    } else {
                        cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository(
                            Path.of(System.getProperty("user.home"), ".boarderless-storage", "local"),
                        )
                    }
                }
            val mediaImportRuntime =
                remember(window, mediaLifecycle, workspaceRepository) {
                    desktopMediaImportRuntime(window, mediaLifecycle.activity) {
                        workspaceRepository.localAssetGateway as? cg.creamgod.boarderless.data.AssetDownloadGateway
                            ?: cg.creamgod.boarderless.data.remote
                                .BackendAssetTransferGateway()
                    }
                }
            App(
                workspaceRepository = workspaceRepository,
                qaRuntime = qaRuntime,
                draftBackupRuntime = draftBackupRuntime,
                draftImportRuntime = draftImportRuntime,
                schemeBundleRuntime = schemeBundleRuntime,
                mediaImportRuntime = mediaImportRuntime,
                menuBridge = menuBridge,
                recentWorkspacesPublisher = recentWorkspacesPublisher,
            )
        }
    }
}
