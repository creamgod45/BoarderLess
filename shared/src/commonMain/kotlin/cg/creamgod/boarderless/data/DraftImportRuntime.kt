package cg.creamgod.boarderless.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicit, bounded local JSON selection. Never persist a path/grant or upload the selected file. */
class DraftImportRuntime(
    val selectJson: (suspend (canRead: () -> Boolean) -> String?)? = null,
) {
    companion object {
        val Unavailable = DraftImportRuntime()
    }
}

internal suspend fun selectWorkspaceDraftBackup(
    runtime: DraftImportRuntime,
    canRead: () -> Boolean,
): String? {
    currentCoroutineContext().ensureActive()
    check(canRead())
    val result = checkNotNull(runtime.selectJson).invoke(canRead)
    currentCoroutineContext().ensureActive()
    check(canRead())
    if (result != null) {
        require(result.length in 1..4 * 1024 * 1024)
        require(result.encodeToByteArray().size <= 4 * 1024 * 1024)
    }
    return result
}
