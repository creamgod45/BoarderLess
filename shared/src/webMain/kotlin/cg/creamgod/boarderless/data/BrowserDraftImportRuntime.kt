package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.data.remote.randomUuid
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun browserDraftImportRuntime() = DraftImportRuntime { canRead ->
    suspendCancellableCoroutine { pending ->
        val id = randomUuid()
        pending.invokeOnCancellation { browserCancelDraftSelection(id) }
        if (pending.isActive) browserPickDraftJson(id, canRead) { content, error ->
            if (pending.isActive) {
                if (error != null) pending.resumeWithException(IllegalArgumentException("Draft file could not be read"))
                else pending.resume(content)
            }
        }
    }
}

internal expect fun browserPickDraftJson(id: String, canRead: () -> Boolean, done: (String?, String?) -> Unit)
internal expect fun browserCancelDraftSelection(id: String)
