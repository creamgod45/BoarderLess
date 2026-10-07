package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.objectLocalDragToWorld
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.*

class ObjectDragCoordinatesTest {
    @Test fun ninetyDegreeLocalDragFollowsScreenRightInsteadOfMovingUp() {
        assertDelta(Vec2(40f, 0f), objectLocalDragToWorld(Vec2(0f, -40f), 90f, 1f))
    }

    @Test fun screenDirectionAndDistanceRemainCorrectForEveryAngleZoomAndSelectionScale() {
        for (angle in listOf(0f, 30f, 45f, 90f, 180f, 270f, -45f, 405f)) {
            for (zoom in listOf(.25f, 1f, 2f, 4f)) {
                for (scale in listOf(1f, 1.012f)) {
                    for (screen in listOf(Vec2(60f, 0f), Vec2(0f, -40f), Vec2(-35f, 23f))) {
                        // Compose maps screen movement into the inverse-transformed local layer.
                        val radians = -(angle % 360f) * PI.toFloat() / 180f
                        val local = Vec2(screen.x * cos(radians) - screen.y * sin(radians),
                            screen.x * sin(radians) + screen.y * cos(radians)) / scale
                        assertDelta(screen / zoom, objectLocalDragToWorld(local, angle, zoom, scale))
                    }
                }
            }
        }
    }

    @Test fun multipleEventsAccumulateInWorldSpaceAndZeroDoesNotMove() {
        val first = objectLocalDragToWorld(Vec2(0f, -20f), 90f, 2f)
        val second = objectLocalDragToWorld(Vec2(12f, 0f), 90f, 2f)
        assertDelta(Vec2(10f, 6f), first + second)
        assertDelta(Vec2.Zero, objectLocalDragToWorld(Vec2.Zero, 123f, 2f))
    }

    private fun assertDelta(expected: Vec2, actual: Vec2) {
        assertEquals(expected.x, actual.x, .0001f)
        assertEquals(expected.y, actual.y, .0001f)
    }
}
