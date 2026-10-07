package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import cg.creamgod.boarderless.domain.model.Vec2
import kotlin.math.roundToInt

/**
 * Places a canvas object at its screen position with its exact zoomed size.
 *
 * A plain `width()`/`height()` is capped by the canvas constraints, so once an object grows past the
 * viewport (zoomed in, partly off-screen) its far edges were drawn at the viewport size instead of
 * where they belong. Measuring unbounded and anchoring top-start keeps the true size and origin.
 */
internal fun Modifier.canvasObjectBounds(screenPosition: Vec2, width: Dp, height: Dp): Modifier = this
    .offset { IntOffset(screenPosition.x.roundToInt(), screenPosition.y.roundToInt()) }
    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
    .size(width, height)
