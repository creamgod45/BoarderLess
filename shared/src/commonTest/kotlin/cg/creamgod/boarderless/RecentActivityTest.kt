package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.recentActivityAfterSeq
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecentActivityTest {
    @Test
    fun requestsOnlyTheLatestWindowWhenHistoryIsLong() {
        assertEquals(900L, recentActivityAfterSeq(lastServerSeq = 1_000, limit = 100))
    }

    @Test
    fun startsAtZeroWhenWorkspaceHasLessThanTheRequestedWindow() {
        assertEquals(0L, recentActivityAfterSeq(lastServerSeq = 42, limit = 100))
    }

    @Test
    fun rejectsLimitsOutsideTheBackendContract() {
        assertFailsWith<IllegalArgumentException> { recentActivityAfterSeq(100, 0) }
        assertFailsWith<IllegalArgumentException> { recentActivityAfterSeq(100, 1_001) }
    }
}
