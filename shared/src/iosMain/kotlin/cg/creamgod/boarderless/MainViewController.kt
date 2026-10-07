package cg.creamgod.boarderless

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    lateinit var controller: UIViewController
    controller =
        ComposeUIViewController {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            // A replaced lifecycle owner gets a new tracker; old disposal must not close the new one.
            val mediaPicker = remember(lifecycle) { IosMediaPicker { controller } }
            val backupPicker = remember(lifecycle) { IosDraftBackupPicker { controller } }
            val importPicker = remember(lifecycle) { IosDraftImportPicker { controller } }
            DisposableEffect(importPicker) { onDispose { importPicker.close() } }
            DisposableEffect(backupPicker) { onDispose { backupPicker.close() } }
            DisposableEffect(mediaPicker, lifecycle) {
                val dispose = bindIosMediaActivity(mediaPicker.activityTracker, lifecycle) { controller }
                onDispose {
                    mediaPicker.close()
                    dispose()
                }
            }
            App(
                mediaImportRuntime = mediaPicker.runtime,
                recentWorkspacesPublisher = HomeScreenQuickActions,
                draftBackupRuntime = backupPicker.runtime,
                draftImportRuntime = importPicker.runtime,
            )
        }
    return controller
}
