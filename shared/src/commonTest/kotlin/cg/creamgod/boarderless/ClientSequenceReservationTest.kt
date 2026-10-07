package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.SessionPreferences
import kotlin.test.*

class ClientSequenceReservationTest {
    @Test fun reservedRangesSurviveReopenAndAcknowledgementNeverRegresses() {
        val memory = InMemorySettings()
        val first = SessionPreferences(memory)
        assertEquals(1L..3L, first.reserveClientSequences(3))
        val reopened = SessionPreferences(memory)
        assertEquals(4L..5L, reopened.reserveClientSequences(2))
        reopened.advanceClientSequence(2)
        assertEquals(5L, reopened.clientSequence)
        reopened.advanceClientSequence(9)
        assertEquals(10L..10L, reopened.reserveClientSequences(1))
    }

    @Test fun unsafeOrExhaustedRangesDoNotMutateCounter() {
        val preferences = SessionPreferences(InMemorySettings())
        preferences.clientSequence = 9_007_199_254_740_990L
        assertFails { preferences.reserveClientSequences(2) }
        assertEquals(9_007_199_254_740_990L, preferences.clientSequence)
        assertEquals(9_007_199_254_740_991L..9_007_199_254_740_991L, preferences.reserveClientSequences(1))
        assertFails { preferences.reserveClientSequences(1) }
        assertFails { preferences.advanceClientSequence(-1) }
        assertFails { preferences.advanceClientSequence(9_007_199_254_740_992L) }
        for (count in listOf(0, -1, 201)) assertFails { preferences.reserveClientSequences(count) }
        assertEquals(9_007_199_254_740_991L, preferences.clientSequence)
    }
}
