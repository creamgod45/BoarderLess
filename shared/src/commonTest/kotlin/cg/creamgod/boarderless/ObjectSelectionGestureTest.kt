package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.ObjectSelectionGesture
import kotlin.test.*

class ObjectSelectionGestureTest {
    @Test fun fingerJitterRemainsTapInNormalAndMultiSelectModes() {
        for (selectionOnly in listOf(false, true)) {
            val motion = ObjectSelectionGesture(selectionOnly, 18f)
            listOf(Vec2(2f, 3f), Vec2(-1f, -2f), Vec2(3f, 1f)).forEach { assertFalse(motion.move(it)) }
            assertTrue(motion.canTap)
            assertFalse(motion.canDrag)
        }
    }

    @Test fun deliberateDragNeverMovesOrSelectsSingleObjectInMultiSelectMode() {
        val motion = ObjectSelectionGesture(true, 18f)
        assertFalse(motion.move(Vec2(40f, 10f)))
        assertFalse(motion.canDrag)
        assertFalse(motion.canTap)
        assertFalse(motion.move(Vec2(-40f, -10f)))
        assertFalse(motion.canTap) // Returning to start does not resurrect an aborted tap.
    }

    @Test fun normalModeWaitsForSlopAndPreservesWholeDisplacement() {
        val motion = ObjectSelectionGesture(false, 18f)
        assertFalse(motion.move(Vec2(10f, 0f)))
        assertTrue(motion.move(Vec2(10f, 0f)))
        assertEquals(Vec2(20f, 0f), motion.displacement)
        assertFalse(motion.canTap)
        assertTrue(motion.canDrag)
    }

    @Test fun diagonalDistanceAndModeRestartDoNotRetainOldDragState() {
        val old = ObjectSelectionGesture(true, 18f)
        assertFalse(old.move(Vec2(13f, 13f)))
        assertFalse(old.canTap)
        val normal = ObjectSelectionGesture(false, 18f)
        assertTrue(normal.canTap)
        assertTrue(normal.move(Vec2(20f, 0f)))
    }
}
