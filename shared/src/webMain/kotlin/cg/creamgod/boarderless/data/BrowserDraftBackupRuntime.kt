package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Explicit, fresh-ACL-checked local Blob download, never a claim of completed disk storage. */
fun browserDraftBackupRuntime() =
    DraftBackupRuntime(usesBrowserDownload = true) { suggested ->
        require(suggested.matches(Regex("[A-Za-z0-9_-]+\\.json")))
        BrowserDraftBackupDestination("${suggested.removeSuffix(".json")}-${randomUuid()}.json")
    }

internal class BrowserDraftBackupDestination(
    private val filename: String,
) : DraftBackupDestination {
    private var used = false
    private var closed = false

    override suspend fun write(
        json: String,
        canWrite: () -> Boolean,
    ) {
        check(!used && !closed)
        used = true
        val context = currentCoroutineContext()
        context.ensureActive()
        check(canWrite())
        browserDispatchDraftDownload(filename, json) {
            context.ensureActive()
            canWrite()
        }
    }

    override fun close() {
        closed = true
    }
}

internal expect fun browserDispatchDraftDownload(
    filename: String,
    json: String,
    canWrite: () -> Boolean,
)
