package cg.creamgod.boarderless.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield

/** Bounded provider read. Empty chunk is EOF; no decoder substitution for invalid UTF-8. */
internal suspend fun readDraftJsonChunks(
    canRead: () -> Boolean,
    readChunk: suspend (Int) -> ByteArray,
): String {
    val chunks = mutableListOf<ByteArray>()
    var total = 0
    while (true) {
        currentCoroutineContext().ensureActive()
        check(canRead())
        val chunk = readChunk(8192)
        currentCoroutineContext().ensureActive()
        check(canRead())
        require(chunk.size <= 8192)
        if (chunk.isEmpty()) break
        require(total + chunk.size <= 4 * 1024 * 1024)
        chunks.add(chunk)
        total += chunk.size
        yield()
    }
    require(total > 0)
    val bytes = ByteArray(total)
    var offset = 0
    chunks.forEach { chunk ->
        chunk.copyInto(bytes, offset)
        offset += chunk.size
    }
    currentCoroutineContext().ensureActive()
    check(canRead())
    return bytes.decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
}
