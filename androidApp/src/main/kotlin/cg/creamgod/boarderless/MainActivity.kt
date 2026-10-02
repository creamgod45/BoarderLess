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
import cg.creamgod.boarderless.data.MediaImportRuntime
import cg.creamgod.boarderless.data.loadAndroidGif
import cg.creamgod.boarderless.data.loadAndroidVideo
import cg.creamgod.boarderless.data.AndroidVideoSurface
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CompletableDeferred

class MainActivity : ComponentActivity() {
    private var pickerInFlight = false
    private var pendingSelection: CompletableDeferred<Uri?>? = null
    private val mediaPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerInFlight = false
        pendingSelection?.complete(uri)
        pendingSelection = null
    }

    private suspend fun selectMediaSource(): AndroidUriAssetTransferSource? {
        check(!pickerInFlight) { "Close the previous document picker before selecting again" }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on recreation (for example rotation): the shortcut was already handled.
        if (savedInstanceState == null) intent.openRequestedWorkspace()

        setContent {
            val mediaImportRuntime = remember {
                val previews = AndroidAssetPreviewLoader(applicationContext.cacheDir)
                val gifDownloads = Semaphore(2)
                MediaImportRuntime(
                    selectSource = ::selectMediaSource,
                    clearPreviewCache = previews::clear,
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
            App(mediaImportRuntime = mediaImportRuntime, recentWorkspacesPublisher = launcherShortcuts)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.openRequestedWorkspace()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
