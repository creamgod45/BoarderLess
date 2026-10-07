package cg.creamgod.boarderless.feature.canvas

import androidx.compose.ui.graphics.Color
import cg.creamgod.boarderless.designsystem.BoarderLessColors
import cg.creamgod.boarderless.i18n.Strings

/** Theme-aware canvas backgrounds. Any other `#rrggbb` token is a user-picked sRGB color. */
internal val CanvasBackgroundPresetTokens = listOf("default", "warm", "cool")

internal fun presetCanvasBackground(
    token: String,
    colors: BoarderLessColors,
): Color =
    when (token) {
        "warm" -> colors.canvasWarm
        "cool" -> colors.canvasCool
        else -> colors.canvas
    }

internal fun canvasBackgroundColor(
    token: String,
    colors: BoarderLessColors,
): Color = customNodeColor(token)?.let { Color(it.r, it.g, it.b) } ?: presetCanvasBackground(token, colors)

/** Theme grid lines are tuned for theme backgrounds; a custom one gets ink that stays visible on it. */
internal fun canvasGridColor(
    token: String,
    colors: BoarderLessColors,
): Color {
    val custom = customNodeColor(token) ?: return colors.grid
    return if (custom.relativeLuminance() > 0.18f) Color(0x24000000) else Color(0x33FFFFFF)
}

internal fun canvasBackgroundLabel(token: String): String =
    customNodeColor(token)?.toHex() ?: when (token) {
        "warm" -> Strings.canvasBackground.warm()
        "cool" -> Strings.canvasBackground.cool()
        else -> Strings.canvasBackground.default()
    }
