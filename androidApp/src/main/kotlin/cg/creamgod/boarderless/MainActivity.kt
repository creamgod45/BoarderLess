package cg.creamgod.boarderless

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.remember
import cg.creamgod.boarderless.data.AndroidUriAssetTransferSource
import cg.creamgod.boarderless.data.AndroidAssetPreviewLoader
import cg.creamgod.boarderless.data.remote.BackendAssetTransferGateway
import cg.creamgod.boarderless.data.remote.loadGiphyStill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.MediaActivityTracker
import cg.creamgod.boarderless.data.androidDraftBackupRuntime
import cg.creamgod.boarderless.data.androidDraftImportRuntime
import cg.creamgod.boarderless.data.discardBlankDraftDocument
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext
import cg.creamgod.boarderless.data.loadAndroidGif
import cg.creamgod.boarderless.data.loadAndroidGiphyAnimation
import cg.creamgod.boarderless.data.loadAndroidVideo
import cg.creamgod.boarderless.data.AndroidVideoSurface
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private val mediaActivity = MediaActivityTracker()
    private var pickerInFlight = false
    private var pendingSelection: CompletableDeferred<Uri?>? = null
    private var draftPickerInFlight = false
    private var pendingDraftDestination: CompletableDeferred<Uri?>? = null
    private var draftImportPickerInFlight = false
    private var pendingDraftSource: CompletableDeferred<Uri?>? = null
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerInFlight = false
        pendingSelection?.complete(uri)
        pendingSelection = null
    }
    // Preserve the original media launcher's registration order across Activity recreation.
    private val draftPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        draftPickerInFlight = false
        val waiting = pendingDraftDestination
        pendingDraftDestination = null
        if (waiting == null || !waiting.complete(uri)) {
            if (uri != null) lifecycleScope.launch(Dispatchers.IO) {
                try { discardBlankDraftDocument(applicationContext, uri) } catch (_: Exception) { /* Best effort, never delete nonempty data. */ }
            }
        }
    }

    // Appended after the existing two launchers: recreation must retain their registration order.
    private val draftImportPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        draftImportPickerInFlight = false
        pendingDraftSource?.complete(uri)
        pendingDraftSource = null
        // Late existing document is never read or deleted.
    }

    private suspend fun selectDraftSource(): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight)
        val result = CompletableDeferred<Uri?>()
        pendingDraftSource = result
        draftImportPickerInFlight = true
        try { draftImportPicker.launch(arrayOf("application/json", "text/plain")) }
        catch (error: Exception) {
            draftImportPickerInFlight = false
            pendingDraftSource = null
            result.cancel()
            throw error
        }
        return try { result.await() } finally {
            result.cancel()
            if (pendingDraftSource === result) pendingDraftSource = null
            // Keep in-flight until native result, even after the caller is cancelled.
        }
    }

    private suspend fun selectMediaSource(): AndroidUriAssetTransferSource? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight) { "Close the previous document picker before selecting again" }
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
        val uri = try {
            result.await()
        } finally {
            if (pendingSelection === result) pendingSelection = null
        }
        return uri?.let { AndroidUriAssetTransferSource.fromUri(applicationContext, it) }
    }

    private suspend fun selectDraftDestination(suggestedName: String): Uri? {
        check(!pickerInFlight && !draftPickerInFlight && !draftImportPickerInFlight) { "Close the previous document picker before selecting again" }
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
        return try { result.await() } finally {
            // Prompt cancellation can race with delivery of a created URI before await resumes.
            if (!coroutineContext.isActive && result.isCompleted && !result.isCancelled) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val orphan = result.await()
                    if (orphan != null) try { discardBlankDraftDocument(applicationContext, orphan) } catch (_: Exception) {}
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
        // Not on recreation (for example rotation): the shortcut was already handled.
        if (savedInstanceState == null) intent.openRequestedWorkspace()

        setContent {
            val mediaImportRuntime = remember {
                val previews = AndroidAssetPreviewLoader(applicationContext.cacheDir)
                val gifDownloads = Semaphore(2)
                MediaImportRuntime(
                    playbackActivity = mediaActivity.activity,
                    selectSource = ::selectMediaSource,
                    clearPreviewCache = previews::clear,
                    loadGiphyAnimation = ::loadAndroidGiphyAnimation,
                    loadGiphyStill = { url -> withContext(Dispatchers.IO) {
                        loadGiphyStill(url, AndroidAssetPreviewLoader::decodeBytes)
                    } },
                    loadPreview = { session, assetId ->
                        val gateway = BackendAssetTransferGateway()
                        try { previews.load(gateway, session, assetId) } finally { gateway.close() }
                    },
                    loadGif = { session, assetId -> gifDownloads.withPermit {
                        val gateway = BackendAssetTransferGateway()
                        try { loadAndroidGif(gateway, session, assetId, applicationContext.cacheDir) }
                        finally { gateway.close() }
                    } },
                    loadVideo = { session, assetId -> gifDownloads.withPermit {
                        val gateway = BackendAssetTransferGateway()
                        try { loadAndroidVideo(gateway, session, assetId, applicationContext.cacheDir, applicationContext) }
                        finally { gateway.close() }
                    } },
                    videoSurface = { playback, modifier -> AndroidVideoSurface(playback, modifier) },
                )
            }
            val launcherShortcuts = remember { LauncherShortcuts(applicationContext) }
            val draftBackupRuntime = remember { androidDraftBackupRuntime(applicationContext, ::selectDraftDestination) }
            val draftImportRuntime = remember { androidDraftImportRuntime(applicationContext, ::selectDraftSource) }
            App(mediaImportRuntime = mediaImportRuntime, recentWorkspacesPublisher = launcherShortcuts,
                draftBackupRuntime = draftBackupRuntime, draftImportRuntime = draftImportRuntime)
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
