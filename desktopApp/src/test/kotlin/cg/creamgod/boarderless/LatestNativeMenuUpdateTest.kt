package cg.creamgod.boarderless

import kotlin.test.*

class LatestNativeMenuUpdateTest {
    @Test fun callerReturnsBeforeNativeExecutionAndUsesLatestLanguageInOneQueuedUpdate() {
        val pending = mutableListOf<() -> Unit>()
        val applied = mutableListOf<String>()
        val updates = LatestNativeMenuUpdate<String>(pending::add, applied::add)
        updates.offer("English")
        updates.offer("繁體中文")
        assertTrue(applied.isEmpty())
        assertEquals(1, pending.size)
        pending.removeAt(0).invoke()
        assertEquals(listOf("繁體中文"), applied)
        updates.offer("English")
        assertEquals(1, pending.size)
        pending.removeAt(0).invoke()
        assertEquals(listOf("繁體中文", "English"), applied)
    }

    @Test fun failedPostOrNativeUpdateDoesNotDisableFutureUpdatesOrRunOnCaller() {
        val pending = mutableListOf<() -> Unit>()
        var failPost = true
        var failApply = true
        val applied = mutableListOf<String>()
        val updates =
            LatestNativeMenuUpdate<String>(
                { task -> if (failPost) error("Post fixture") else pending.add(task) },
                { value -> if (failApply) error("Apply fixture") else applied.add(value) },
            )
        updates.offer("post failure")
        assertTrue(pending.isEmpty())
        failPost = false
        updates.offer("apply failure")
        assertFails { pending.removeAt(0).invoke() }
        failApply = false
        updates.offer("Recovered")
        assertTrue(applied.isEmpty())
        pending.removeAt(0).invoke()
        assertEquals(listOf("Recovered"), applied)
    }
}
