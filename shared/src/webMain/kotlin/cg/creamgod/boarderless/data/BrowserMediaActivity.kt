package cg.creamgod.boarderless.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import cg.creamgod.boarderless.data.remote.randomUuid

@Composable
fun rememberBrowserMediaImportRuntime(): MediaImportRuntime {
    val tracker = remember { MediaActivityTracker() }
    val ownerId = remember { randomUuid() }
    val runtime = remember(tracker) { browserMediaImportRuntime(tracker.activity) }
    DisposableEffect(tracker, ownerId) {
        browserBindMediaActivity(ownerId, tracker::setAvailable)
        onDispose {
            browserUnbindMediaActivity(ownerId)
            tracker.close()
        }
    }
    return runtime
}

internal expect fun browserBindMediaActivity(ownerId: String, onChange: (Boolean) -> Unit)
internal expect fun browserUnbindMediaActivity(ownerId: String)
