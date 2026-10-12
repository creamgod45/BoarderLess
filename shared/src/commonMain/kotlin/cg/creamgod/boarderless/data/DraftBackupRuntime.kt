package cg.creamgod.boarderless.data

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** User-selected local destination. The adapter checks [canWrite] at the actual write boundary,
 * Native adapters create a NEW file (never silently overwrite), removing own partial files.
 * Browser downloads only request delivery: filename/location and completion belong to the browser.
 * No cloud upload, automatic import, opening or persisted directory preference.
 */
interface DraftBackupDestination {
    suspend fun write(
        json: String,
        canWrite: () -> Boolean,
    )

    fun close()

    /** Native document providers may need awaited IO cleanup, including on cancellation. */
    suspend fun dispose() {
        close()
    }
}

class DraftBackupRuntime(
    val choosesFolder: Boolean = false,
    val usesBrowserDownload: Boolean = false,
    val chooseDestination: (suspend (suggestedName: String) -> DraftBackupDestination?)? = null,
) {
    companion object {
        val Unavailable = DraftBackupRuntime()
    }
}

internal enum class DraftBackupResult { Saved, DownloadRequested, Cancelled }

/** Choose first, then fresh read ACL, then final scope/cancellation checks. Private content
 * does not enter a native picker or a destination until the explicit confirmed export.
 */
internal suspend fun exportWorkspaceDraftBackup(
    runtime: DraftBackupRuntime,
    draftId: String,
    isCurrent: () -> Boolean,
    readFresh: suspend () -> WorkspaceDraftReview,
): DraftBackupResult {
    return exportLocalJsonBackup(runtime, "boarderless-draft-backup.json", isCurrent) {
        val reviewed = readFresh()
        check(reviewed.draftId == draftId) { "Draft backup identity changed" }
        reviewed.toBackupJson()
    }
}

/** Shared native/browser destination lifecycle. Content is obtained only after selection. */
internal suspend fun exportLocalJsonBackup(
    runtime: DraftBackupRuntime,
    suggestedName: String,
    isCurrent: () -> Boolean,
    readFreshJson: suspend () -> String,
): DraftBackupResult {
    val context = currentCoroutineContext()

    fun checkCurrent() {
        context.ensureActive()
        check(isCurrent()) { "Draft backup scope changed" }
    }
    checkCurrent()
    val choose = checkNotNull(runtime.chooseDestination) { "Draft file export unavailable" }
    val destination = choose(suggestedName) ?: return DraftBackupResult.Cancelled
    var failure: Throwable? = null
    try {
        checkCurrent()
        val content = readFreshJson()
        checkCurrent()
        destination.write(content) { context.isActive && isCurrent() }
        checkCurrent()
        return if (runtime.usesBrowserDownload) DraftBackupResult.DownloadRequested else DraftBackupResult.Saved
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try {
            withContext(NonCancellable) { destination.dispose() }
        } catch (
            closeError: Throwable,
        ) {
            if (failure == null) throw closeError
        }
    }
}
