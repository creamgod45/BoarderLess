package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.*

class CommittedCatchUpReaderTest {
    private fun record(seq: Long) = CommittedWorkspaceOperationDto(seq, "op-$seq", "transaction",
        "actor", "client", seq, 0, 1, "update_object", JsonObject(emptyMap()), 1, "today")
    @Test fun splitTransactionIsBufferedAndNextCursorIsLastReceivedRecordNotHead() = runTest {
        val cursors = mutableListOf<Long>()
        val collected = collectCommittedCatchUp(0, FullCatchUpOperationsDto(listOf(record(1)), 2, true)) { cursor ->
            cursors.add(cursor)
            FullCatchUpOperationsDto(listOf(record(2)), 2, false)
        }
        assertEquals(listOf(1L), cursors)
        assertEquals(listOf(1L, 2L), collected?.operations?.map { it.serverSeq })
        assertEquals(2L, collected?.lastServerSeq)
        assertFalse(collected!!.hasMore)
    }
    @Test fun missingRecordsRegressiveHeadEmptyMoreAndIncompleteTailRequestResync() = runTest {
        for (first in listOf(
            FullCatchUpOperationsDto(listOf(record(2)), 2, false),
            FullCatchUpOperationsDto(emptyList(), 2, true),
            FullCatchUpOperationsDto(listOf(record(1)), 2, false),
        )) {
            var requested = false
            assertNull(collectCommittedCatchUp(0, first) { requested = true; null })
            assertFalse(requested)
        }
        assertNull(collectCommittedCatchUp(0, FullCatchUpOperationsDto(listOf(record(1)), 3, true)) {
            FullCatchUpOperationsDto(listOf(record(2)), 2, false)
        })
    }
    @Test fun continuouslyGrowingOrSmallPagesHaveFiniteRequestBudget() = runTest {
        var requests = 0
        assertNull(collectCommittedCatchUp(0, FullCatchUpOperationsDto(listOf(record(1)), 100, true)) { cursor ->
            requests++
            FullCatchUpOperationsDto(listOf(record(cursor + 1)), 100, true)
        })
        assertEquals(7, requests)
    }
    @Test fun nextReadFailuresPropagateAndCancelledLateResponseCannotReturnWindow() = runTest {
        val first = FullCatchUpOperationsDto(listOf(record(1)), 2, true)
        val failure = IllegalStateException("unavailable")
        assertSame(failure, assertFailsWith<IllegalStateException> {
            collectCommittedCatchUp(0, first) { throw failure }
        })
        var published = false
        launch {
            collectCommittedCatchUp(0, first) {
                currentCoroutineContext().cancel()
                FullCatchUpOperationsDto(listOf(record(2)), 2, false)
            }
            published = true
        }.join()
        assertFalse(published)
    }
}
