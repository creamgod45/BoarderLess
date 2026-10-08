package cg.creamgod.boarderless.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
enum class TextAnchor {
    TopLeft, TopCenter, TopRight, CenterLeft, Center, CenterRight, BottomLeft, BottomCenter, BottomRight,
    Above, Below, Left, Right,
}

/** Coordinates are local canvas units; rotation and zoom are inherited from the owning object. */
@Serializable
data class TextPlacement(val anchor: TextAnchor? = null, val offsetX: Float = 0f, val offsetY: Float = 0f, val appearance: TextAppearance = TextAppearance()) {
    init {
        require(offsetX.isFinite() && offsetY.isFinite() && abs(offsetX) <= 100_000f && abs(offsetY) <= 100_000f)
    }
}

fun textLayerPosition(anchor: TextAnchor, box: CanvasSize, text: CanvasSize, padding: Float, gap: Float): Vec2 {
    val left = padding
    val center = (box.width - text.width) / 2f
    val right = box.width - padding - text.width
    val top = padding
    val middle = (box.height - text.height) / 2f
    val bottom = box.height - padding - text.height
    return when (anchor) {
        TextAnchor.TopLeft -> Vec2(left, top)
        TextAnchor.TopCenter -> Vec2(center, top)
        TextAnchor.TopRight -> Vec2(right, top)
        TextAnchor.CenterLeft -> Vec2(left, middle)
        TextAnchor.Center -> Vec2(center, middle)
        TextAnchor.CenterRight -> Vec2(right, middle)
        TextAnchor.BottomLeft -> Vec2(left, bottom)
        TextAnchor.BottomCenter -> Vec2(center, bottom)
        TextAnchor.BottomRight -> Vec2(right, bottom)
        TextAnchor.Above -> Vec2(center, -gap - text.height)
        TextAnchor.Below -> Vec2(center, box.height + gap)
        TextAnchor.Left -> Vec2(-gap - text.width, middle)
        TextAnchor.Right -> Vec2(box.width + gap, middle)
    }
}

@Serializable enum class TextFont { System, SansSerif, Serif, Monospace, Cursive }

@Serializable data class TextAppearance(
    val fontSize: Float = 16f,
    val weight: Int = 500,
    val color: String? = null,
    val font: TextFont = TextFont.System,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
) {
    init {
        require(fontSize.isFinite() && fontSize in 1f..512f)
        require(weight in 100..900)
        require(color == null || Regex("#[0-9a-fA-F]{6}").matches(color))
    }
}
