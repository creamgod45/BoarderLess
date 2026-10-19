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
            val exportPicker = remember(lifecycle) { IosCanvasExportPicker { controller } }
            DisposableEffect(exportPicker) { onDispose { exportPicker.close() } }
            DisposableEffect(importPicker) { onDispose { importPicker.close() } }
            DisposableEffect(backupPicker) { onDispose { backupPicker.close() } }
            DisposableEffect(mediaPicker, lifecycle) {
                val dispose = bindIosMediaActivity(mediaPicker.activityTracker, lifecycle) { controller }
                onDispose {
                    mediaPicker.close()
                    dispose()
                }
            }
            val schemeBundleRuntime = remember(exportPicker, importPicker) {
                cg.creamgod.boarderless.feature.canvas.SchemeBundleRuntime(
                    chooseDestination = exportPicker.schemeBundleRuntime.chooseDestination,
                    chooseSource = importPicker.chooseSchemeBundleSource,
                    receipts = { owner, digest, assets ->
                        cg.creamgod.boarderless.data.persistence.PersistentSchemeBundleImportReceipts(
                            cg.creamgod.boarderless.data.persistence.IosAtomicLocalRecordStore(
                                cg.creamgod.boarderless.data.persistence.iosPrivateRecordRoot("scheme-bundle-receipts")), owner, digest, assets)
                    },
                )
            }
            val quickSchemeRepository = remember { cg.creamgod.boarderless.data.persistence.iosFileQuickSchemeStore() }
            val workspaceStorageRuntime = remember(mediaPicker) {
                cg.creamgod.boarderless.data.WorkspaceStorageRuntime(
                    serverRepository = { cg.creamgod.boarderless.data.remote.iosRealtimeBackendRepository() },
                    serverRepositoryWithSubmission = { cg.creamgod.boarderless.data.remote.iosRealtimeBackendRepository(it) },
                    localRepository = { cg.creamgod.boarderless.data.persistence.iosFileWorkspaceRepository() },
                    mediaRuntimeFor = mediaPicker::runtimeFor,
                )
            }
            App(
                workspaceStorageRuntime = workspaceStorageRuntime,
                legacyQuickSchemeReader = remember { cg.creamgod.boarderless.data.persistence.legacyQuickSchemeCatalogReader() },
                quickSchemeRepository = quickSchemeRepository,
                canvasExportRuntime = exportPicker.runtime,
                schemeBundleRuntime = schemeBundleRuntime,
                recentWorkspacesPublisher = HomeScreenQuickActions,
                draftBackupRuntime = backupPicker.runtime,
                draftImportRuntime = importPicker.runtime,
            )
        }
    return controller
}
