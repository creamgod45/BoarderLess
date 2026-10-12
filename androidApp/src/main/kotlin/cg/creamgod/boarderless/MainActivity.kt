package cg.creamgod.boarderless

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import cg.creamgod.boarderless.data.AndroidAssetPreviewLoader
import cg.creamgod.boarderless.data.AndroidExportImageLoader
import cg.creamgod.boarderless.data.AndroidUriAssetTransferSource
import cg.creamgod.boarderless.data.AndroidVideoSurface
import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.androidSchemeBundleRuntime
import cg.creamgod.boarderless.data.androidCanvasExportRuntime
import cg.creamgod.boarderless.domain.export.CanvasExportFormat
import cg.creamgod.boarderless.data.androidDraftBackupRuntime
import cg.creamgod.boarderless.data.androidDraftImportRuntime
import cg.creamgod.boarderless.data.discardBlankDraftDocument
import cg.creamgod.boarderless.data.loadAndroidGif
import cg.creamgod.boarderless.data.loadAndroidGiphyAnimation
import cg.creamgod.boarderless.data.loadAndroidVideo
import cg.creamgod.boarderless.data.remote.loadGiphyStill
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class MainActivity : ComponentActivity() {
    private val mediaActivity = MediaActivityTracker()
    private var pickerInFlight = false
    private var pendingSelection: CompletableDeferred<Uri?>? = null
    private var draftPickerInFlight = false
    private var pendingDraftDestination: CompletableDeferred<Uri?>? = null
    private var draftImportPickerInFlight = false
    private var pendingDraftSource: CompletableDeferred<Uri?>? = null
    private var bundleImportPickerInFlight = false
    private var pendingBundleSource: CompletableDeferred<Uri?>? = null
    private var bundlePickerInFlight = false
    private var pendingBundleDestination: CompletableDeferred<Uri?>? = null
    private var exportPickerInFlight = false
    private var pendingExportDestination: CompletableDeferred<Uri?>? = null
    private val mediaPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pickerInFlight = false
            pendingSelection?.complete(uri)
            pendingSelection = null
        }

    // Preserve the original media launcher's registration order across Activity recreation.
    private val draftPicker =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            draftPickerInFlight = false
            val waiting = pendingDraftDestination
            pendingDraftDestination = null
            if (waiting == null || !waiting.complete(uri)) {
                if (uri != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            discardBlankDraftDocument(applicationContext, uri)
                        } catch (
                            _: Exception,
                        ) {
                            // Best effort, never delete nonempty data.
                        }
                    }
                }
            }
        }

    // Appended after the existing two launchers: recreation must retain their registration order.
    private val draftImportPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            draftImportPickerInFlight = false
            pendingDraftSource?.complete(uri)
            pendingDraftSource = null
            // Late existing document is never read or deleted.
        }

    // Append registrations so existing picker results keep their recreation keys.
    private val pngExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("image/png"), ::exportDestinationSelected)
    private val pdfExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), ::exportDestinationSelected)

    // Append after PNG/PDF: preserve all previous Activity result registration keys.
    private val bundleExportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        bundlePickerInFlight = false
        val waiting = pendingBundleDestination
        pendingBundleDestination = null
        if (waiting == null || !waiting.complete(uri)) {
            if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
                runCatching { discardBlankDraftDocument(applicationContext, uri) }
            }
        }
    }

    // Keep the export launcher's existing result key; import is appended after it.
    private val bundleImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        bundleImportPickerInFlight = false
        pendingBundleSource?.complete(uri)
        pendingBundleSource = null
    }

    private suspend fun selectBundleSource(): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight)
        val result=CompletableDeferred<Uri?>()
        pendingBundleSource=result; bundleImportPickerInFlight=true
        try { bundleImportPicker.launch(arrayOf("application/octet-stream", "application/x-boarderless-scheme", "*/*")) }
        catch (failure: Exception) {
            bundleImportPickerInFlight=false; pendingBundleSource=null; result.cancel(); throw failure
        }
        return try { result.await() } finally {
            result.cancel()
            if (pendingBundleSource === result) pendingBundleSource=null
            // Late existing documents are never read or removed.
        }
    }

    private suspend fun selectBundleDestination(name: String): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight)
        val result = CompletableDeferred<Uri?>()
        pendingBundleDestination = result
        bundlePickerInFlight = true
        try { bundleExportPicker.launch(name) }
        catch (failure: Exception) {
            bundlePickerInFlight = false; pendingBundleDestination = null; result.cancel(); throw failure
        }
        return try { result.await() } finally {
            if (!coroutineContext.isActive && result.isCompleted && !result.isCancelled) {
                withContext(NonCancellable + Dispatchers.IO) {
                    result.await()?.let { runCatching { discardBlankDraftDocument(applicationContext, it) } }
                }
            }
            result.cancel()
            if (pendingBundleDestination === result) pendingBundleDestination = null
        }
    }

    private fun exportDestinationSelected(uri: Uri?) {
        exportPickerInFlight = false
        val waiting = pendingExportDestination
        pendingExportDestination = null
        if (waiting == null || !waiting.complete(uri)) {
            if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
                runCatching { discardBlankDraftDocument(applicationContext, uri) }
            }
        }
    }

    private suspend fun selectExportDestination(name: String, format: CanvasExportFormat): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight)
        val result = CompletableDeferred<Uri?>()
        pendingExportDestination = result
        exportPickerInFlight = true
        try {
            (if (format == CanvasExportFormat.Png) pngExportPicker else pdfExportPicker).launch(name)
        } catch (failure: Exception) {
            exportPickerInFlight = false
            pendingExportDestination = null
            result.cancel()
            throw failure
        }
        return try { result.await() } finally {
            if (!coroutineContext.isActive && result.isCompleted && !result.isCancelled) {
                withContext(NonCancellable + Dispatchers.IO) {
                    result.await()?.let { runCatching { discardBlankDraftDocument(applicationContext, it) } }
                }
            }
            result.cancel()
            if (pendingExportDestination === result) pendingExportDestination = null
            // Keep the native picker in flight until delivery, including rotation/cancellation.
        }
    }

    private suspend fun selectDraftSource(): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight)
        val result = CompletableDeferred<Uri?>()
        pendingDraftSource = result
        draftImportPickerInFlight = true
        try {
            draftImportPicker.launch(arrayOf("application/json", "text/plain"))
        } catch (error: Exception) {
            draftImportPickerInFlight = false
            pendingDraftSource = null
            result.cancel()
            throw error
        }
        return try {
            result.await()
        } finally {
            result.cancel()
            if (pendingDraftSource === result) pendingDraftSource = null
            // Keep in-flight until native result, even after the caller is cancelled.
        }
    }

    private suspend fun selectMediaSource(): AndroidUriAssetTransferSource? {
        check(
            !pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight,
        ) { "Close the previous document picker before selecting again" }
        val result = CompletableDeferred<Uri?>()
        pendingSelection = result
        pickerInFlight = true
        try {
            mediaPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm"))
        } catch (error: Exception) {
            pickerInFlight = false
            pendingSelection = null
            throw error
        }
        val uri =
            try {
                result.await()
            } finally {
                if (pendingSelection === result) pendingSelection = null
            }
        return uri?.let { AndroidUriAssetTransferSource.fromUri(applicationContext, it) }
    }

    private suspend fun selectDraftDestination(suggestedName: String): Uri? {
        check(
            !pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight && !exportPickerInFlight && !bundlePickerInFlight && !bundleImportPickerInFlight,
        ) { "Close the previous document picker before selecting again" }
        val result = CompletableDeferred<Uri?>()
        pendingDraftDestination = result
        draftPickerInFlight = true
        try {
            draftPicker.launch(suggestedName)
        } catch (error: Exception) {
            draftPickerInFlight = false
            pendingDraftDestination = null
            result.cancel()
            throw error
        }
        return try {
            result.await()
        } finally {
            // Prompt cancellation can race with delivery of a created URI before await resumes.
            if (!coroutineContext.isActive && result.isCompleted && !result.isCancelled) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val orphan = result.await()
                    if (orphan != null) {
                        try {
                            discardBlankDraftDocument(applicationContext, orphan)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            result.cancel()
            if (pendingDraftDestination === result) pendingDraftDestination = null
            // Native picker stays in flight until its result; do not launch a second one.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pickerInFlight = savedInstanceState?.getBoolean("boarderless.mediaPickerInFlight") ?: false
        draftPickerInFlight = savedInstanceState?.getBoolean("boarderless.draftPickerInFlight") ?: false
        draftImportPickerInFlight = savedInstanceState?.getBoolean("boarderless.draftImportPickerInFlight") ?: false
        exportPickerInFlight = savedInstanceState?.getBoolean("boarderless.exportPickerInFlight") ?: false
        bundlePickerInFlight = savedInstanceState?.getBoolean("boarderless.bundlePickerInFlight") ?: false
        bundleImportPickerInFlight = savedInstanceState?.getBoolean("boarderless.bundleImportPickerInFlight") ?: false
        // Not on recreation (for example rotation): the shortcut was already handled.
        if (savedInstanceState == null) intent.openRequestedWorkspace()

        setContent {
            val workspaceStorageRuntime =
                remember {
                    cg.creamgod.boarderless.data.WorkspaceStorageRuntime(
                        serverRepository = { cg.creamgod.boarderless.data.remote.androidRealtimeBackendRepository(applicationContext) },
                        serverRepositoryWithSubmission = { cg.creamgod.boarderless.data.remote.androidRealtimeBackendRepository(applicationContext, it) },
                        localRepository = { cg.creamgod.boarderless.data.persistence.androidFileWorkspaceRepository(applicationContext) },
                        mediaRuntimeFor = { repository ->
                            val previews = AndroidAssetPreviewLoader(applicationContext.cacheDir)
                            val exportImages = AndroidExportImageLoader(applicationContext.cacheDir)
                            val gifDownloads = Semaphore(2)
                            MediaImportRuntime(
                                playbackActivity = mediaActivity.activity,
                                loadExportImage = { session, assetId, width, height ->
                                    cg.creamgod.boarderless.data.withWorkspaceAssetDownloadGateway(repository) { gateway ->
                                        exportImages.load(gateway, session, assetId, width, height)
                                    }
                                },
                                selectSource = ::selectMediaSource,
                                clearPreviewCache = previews::clear,
                                loadGiphyAnimation = ::loadAndroidGiphyAnimation,
                                loadGiphyStill = { url ->
                                    withContext(Dispatchers.IO) {
                                        loadGiphyStill(url, AndroidAssetPreviewLoader::decodeBytes)
                                    }
                                },
                                loadPreview = { session, assetId ->
                                    cg.creamgod.boarderless.data.withWorkspaceAssetDownloadGateway(repository) { gateway ->
                                        previews.load(gateway, session, assetId)
                                    }
                                },
                                loadGif = { session, assetId ->
                                    gifDownloads.withPermit {
                                        cg.creamgod.boarderless.data.withWorkspaceAssetDownloadGateway(repository) { gateway ->
                                            loadAndroidGif(gateway, session, assetId, applicationContext.cacheDir)
                                        }
                                    }
                                },
                                loadVideo = { session, assetId ->
                                    gifDownloads.withPermit {
                                        cg.creamgod.boarderless.data.withWorkspaceAssetDownloadGateway(repository) { gateway ->
                                            loadAndroidVideo(gateway, session, assetId, applicationContext.cacheDir, applicationContext)
                                        }
                                    }
                                },
                                videoSurface = { playback, modifier -> AndroidVideoSurface(playback, modifier) },
                            )
                        },
                    )
                }
            val launcherShortcuts = remember { LauncherShortcuts(applicationContext) }
            val draftBackupRuntime = remember { androidDraftBackupRuntime(applicationContext, ::selectDraftDestination) }
            val draftImportRuntime = remember { androidDraftImportRuntime(applicationContext, ::selectDraftSource) }
            val canvasExportRuntime = remember { androidCanvasExportRuntime(applicationContext, ::selectExportDestination) }
            val schemeBundleRuntime = remember { androidSchemeBundleRuntime(applicationContext, ::selectBundleDestination, ::selectBundleSource) }
            val quickSchemeRepository = remember {
                cg.creamgod.boarderless.data.persistence.androidFileQuickSchemeStore(applicationContext)
            }
            App(
                quickSchemeRepository = quickSchemeRepository,
                legacyQuickSchemeReader = remember { cg.creamgod.boarderless.data.persistence.legacyQuickSchemeCatalogReader() },
                schemeBundleRuntime = schemeBundleRuntime,
                canvasExportRuntime = canvasExportRuntime,
                workspaceStorageRuntime = workspaceStorageRuntime,
                recentWorkspacesPublisher = launcherShortcuts,
                draftBackupRuntime = draftBackupRuntime,
                draftImportRuntime = draftImportRuntime,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.openRequestedWorkspace()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("boarderless.mediaPickerInFlight", pickerInFlight)
        outState.putBoolean("boarderless.draftPickerInFlight", draftPickerInFlight)
        outState.putBoolean("boarderless.draftImportPickerInFlight", draftImportPickerInFlight)
        outState.putBoolean("boarderless.exportPickerInFlight", exportPickerInFlight)
        outState.putBoolean("boarderless.bundlePickerInFlight", bundlePickerInFlight)
        outState.putBoolean("boarderless.bundleImportPickerInFlight", bundleImportPickerInFlight)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        mediaActivity.setAvailable(true)
    }

    override fun onPause() {
        mediaActivity.setAvailable(false)
        super.onPause()
    }

    override fun onDestroy() {
        pendingBundleSource?.cancel()
        pendingBundleSource = null
        pendingBundleDestination?.cancel()
        pendingBundleDestination = null
        pendingExportDestination?.cancel()
        pendingExportDestination = null
        pendingDraftSource?.cancel()
        pendingDraftSource = null
        pendingDraftDestination?.cancel()
        pendingDraftDestination = null
        mediaActivity.close()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
