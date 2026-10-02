package cg.creamgod.boarderless

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    lateinit var controller: UIViewController
    val mediaPicker = IosMediaPicker { controller }
    controller = ComposeUIViewController {
        App(mediaImportRuntime = mediaPicker.runtime, recentWorkspacesPublisher = HomeScreenQuickActions)
    }
    return controller
}
