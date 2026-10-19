package cg.creamgod.boarderless.data.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal const val MaxCatchUpReplayRecords = 8_000

/** Read-only bounded assembly; no session/checkpoint publication before the complete window.
 * Cursor follows received records, never the advertised head. Null requests a state resync.
 * HTTP access errors propagate from readNext; they are not compatibility/fallback signals.
 */
internal suspend fun collectCommittedCatchUp(
    afterSeq: Long,
    first: FullCatchUpOperationsDto,
    readNext: suspend (Long) -> FullCatchUpOperationsDto?,
): FullCatchUpOperationsDto? {
    val maxSafe = 9_007_199_254_740_991L
    if (afterSeq !in 0..maxSafe) return null
    val collected = mutableListOf<CommittedWorkspaceOperationDto>()
    var cursor = afterSeq
    var knownHead = afterSeq
    var page = first
    repeat(8) { pageIndex ->
        currentCoroutineContext().ensureActive()
        if (page.lastServerSeq !in knownHead..maxSafe || page.operations.size > 1000 ||
            collected.size + page.operations.size > MaxCatchUpReplayRecords
        ) {
            return null
        }
        knownHead = page.lastServerSeq
        for (record in page.operations) {
            if (cursor == maxSafe || record.serverSeq != cursor + 1 || record.serverSeq > knownHead) return null
            cursor = record.serverSeq
            collected.add(record)
        }
        if (!page.hasMore) {
            if (cursor != knownHead) return null
            return FullCatchUpOperationsDto(collected.toList(), cursor, false)
        }
        if (page.operations.isEmpty() || cursor >= knownHead || pageIndex == 7) return null
        page = readNext(cursor) ?: return null
        currentCoroutineContext().ensureActive()
    }
    return null
}
