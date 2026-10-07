package cg.creamgod.boarderless

import cg.creamgod.boarderless.designsystem.color.RgbColor
import cg.creamgod.boarderless.designsystem.color.contrastRatio
import cg.creamgod.boarderless.feature.canvas.customNodeColor
import cg.creamgod.boarderless.feature.canvas.customNodeColorToken
import cg.creamgod.boarderless.feature.canvas.nodeColorLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeColorTest {
    @Test fun plainTextUsesThemeDefaultOrChosenInkNotCardContrastInk() {
        val white = androidx.compose.ui.graphics.Color.White
        val colors = cg.creamgod.boarderless.designsystem.BoarderLessColors(
            canvas = white, canvasWarm = white, canvasCool = white, grid = white, contentSurface = white,
            nodeLilac = white, nodeAmber = white, nodeMint = white, contentBorder = white, contentText = white,
            contentMuted = white, shellSurface = white, shellSurfaceOpaque = white, shellBorder = white,
            shellText = white, accent = white, selection = white, danger = white)
        val plain = cg.creamgod.boarderless.domain.model.NodeShape.PlainText
        assertEquals(white, cg.creamgod.boarderless.feature.canvas.nodeTextColors("paper", colors, plain).text)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF6255D9),
            cg.creamgod.boarderless.feature.canvas.nodeTextColors("#6255d9", colors, plain).text)
    }
    @Test
    fun customTokensAreLowercaseHex() {
        val color = RgbColor.parseHex("#6255D9")!!
        assertEquals("#6255d9", customNodeColorToken(color))
        assertEquals("#6255D9", customNodeColor("#6255d9")?.toHex())
        assertNull(customNodeColor("lilac"))
        assertNull(customNodeColor("#nothex"))
    }

    @Test
    fun customColorsAreLabelledByTheirHexCode() {
        assertEquals("#6255D9", nodeColorLabel("#6255d9"))
        assertEquals("Lilac", nodeColorLabel("lilac"))
    }

    @Test
    fun contrastRatioFollowsWcag() {
        val black = RgbColor(0f, 0f, 0f)
        val white = RgbColor(1f, 1f, 1f)
        assertTrue(contrastRatio(black, white) in 20.9f..21.1f)
        assertEquals(1f, contrastRatio(white, white))
    }
}
