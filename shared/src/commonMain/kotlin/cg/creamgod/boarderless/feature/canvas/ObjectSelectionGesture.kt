package cg.creamgod.boarderless.feature.canvas

import cg.creamgod.boarderless.domain.model.Vec2

/** Slop is in pointer pixels, not world coordinates, so zoom does not amplify finger jitter. */
internal class ObjectSelectionGesture(
    private val selectionOnly: Boolean,
    private val touchSlop: Float,
) {
    var displacement: Vec2 = Vec2.Zero
        private set
    var crossedSlop: Boolean = false
        private set
    val canTap: Boolean get() = !crossedSlop
    val canDrag: Boolean get() = crossedSlop && !selectionOnly

    fun move(delta: Vec2): Boolean {
        displacement += delta
        if (displacement.x * displacement.x + displacement.y * displacement.y > touchSlop * touchSlop) crossedSlop = true
        return canDrag
    }
}
