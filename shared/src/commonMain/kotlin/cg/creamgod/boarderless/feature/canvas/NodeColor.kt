package cg.creamgod.boarderless.feature.canvas

import androidx.compose.ui.graphics.Color
import cg.creamgod.boarderless.designsystem.BoarderLessColors
import cg.creamgod.boarderless.designsystem.color.RgbColor
import cg.creamgod.boarderless.designsystem.color.contrastRatio
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.i18n.LocalizedText
import cg.creamgod.boarderless.i18n.Strings

/** Theme-aware node colors. Any other token of the form `#rrggbb` is a user-picked sRGB color. */
internal val NodeColorPresetTokens = listOf("paper", "lilac", "amber", "mint")

internal fun customNodeColor(token: String): RgbColor? = if (token.startsWith("#")) RgbColor.parseHex(token) else null

internal fun customNodeColorToken(color: RgbColor): String = color.toHex().lowercase()

internal fun presetNodeColor(
    token: String,
    colors: BoarderLessColors,
): Color =
    when (token) {
        "lilac" -> colors.nodeLilac
        "amber" -> colors.nodeAmber
        "mint" -> colors.nodeMint
        else -> colors.contentSurface
    }

internal fun nodeFillColor(
    token: String,
    colors: BoarderLessColors,
): Color = customNodeColor(token)?.let { Color(it.r, it.g, it.b) } ?: presetNodeColor(token, colors)

internal data class NodeTextColors(
    val text: Color,
    val muted: Color,
)

/** Presets are tuned per theme; custom fills pick whichever ink contrasts more. */
internal fun nodeTextColors(
    token: String,
    colors: BoarderLessColors,
    shape: NodeShape = NodeShape.RoundedRectangle,
): NodeTextColors {
    if (shape == NodeShape.PlainText) {
        val ink = if (token == "paper") colors.contentText else nodeFillColor(token, colors)
        return NodeTextColors(ink, ink.copy(alpha = .66f))
    }
    val custom = customNodeColor(token) ?: return NodeTextColors(colors.contentText, colors.contentMuted)
    val ink = if (contrastRatio(custom, DarkInk) >= contrastRatio(custom, LightInk)) DarkInk else LightInk
    val text = Color(ink.r, ink.g, ink.b)
    return NodeTextColors(text, text.copy(alpha = 0.66f))
}

internal fun nodeColorLabel(token: String): String = customNodeColor(token)?.toHex() ?: colorTokenLabel(token)

/** Names for the theme color tokens nodes and groups use. */
internal fun colorTokenLabel(token: String): String =
    when (token) {
        "paper" -> Strings.objects.paper()
        "lilac" -> Strings.objects.lilac()
        "amber" -> Strings.objects.amber()
        "mint" -> Strings.objects.mint()
        "group" -> Strings.objects.group()
        else -> token.replaceFirstChar(Char::uppercase)
    }

internal fun nodeShapeText(shape: NodeShape): LocalizedText =
    when (shape) {
        NodeShape.RoundedRectangle -> Strings.objects.rounded
        NodeShape.Rectangle -> Strings.objects.process
        NodeShape.Ellipse -> Strings.objects.ellipse
        NodeShape.Diamond -> Strings.objects.decision
        NodeShape.Pill -> Strings.objects.startEnd
        NodeShape.Parallelogram -> Strings.objects.inputOutput
        NodeShape.Hexagon -> Strings.objects.system
        NodeShape.Document -> Strings.objects.document
        NodeShape.Database -> Strings.objects.database
        NodeShape.PlainText -> Strings.objects.plainText
        NodeShape.Triangle -> Strings.objects.triangle
        NodeShape.Pentagon -> Strings.objects.pentagon
        NodeShape.Octagon -> Strings.objects.octagon
        NodeShape.Trapezoid -> Strings.objects.trapezoid
        NodeShape.Plus -> Strings.objects.plus
        NodeShape.ArrowRight -> Strings.objects.arrowRight
        NodeShape.ArrowLeft -> Strings.objects.arrowLeft
        NodeShape.ArrowUp -> Strings.objects.arrowUp
        NodeShape.ArrowDown -> Strings.objects.arrowDown
        NodeShape.TriangleDown -> Strings.objects.triangleDown
        NodeShape.RightTriangle -> Strings.objects.rightTriangle
        NodeShape.Chevron -> Strings.objects.chevron
        NodeShape.DoubleArrow -> Strings.objects.doubleArrow
        NodeShape.Star -> Strings.objects.star
        NodeShape.ManualInput -> Strings.objects.manualInput
    }

internal fun nodeShapeLabel(shape: NodeShape): String = nodeShapeText(shape)()

private val DarkInk = RgbColor.fromArgb(0xFF242421)
private val LightInk = RgbColor.fromArgb(0xFFF7F6F2)
