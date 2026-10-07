package cg.creamgod.boarderless

import androidx.compose.ui.graphics.Color
import cg.creamgod.boarderless.feature.canvas.canvasBackgroundLabel
import cg.creamgod.boarderless.feature.canvas.canvasGridColor
import cg.creamgod.boarderless.feature.canvas.canvasBackgroundColor
import cg.creamgod.boarderless.designsystem.BoarderLessColors
import kotlin.test.Test
import kotlin.test.assertEquals

class CanvasBackgroundTest {
    private val colors = BoarderLessColors(
        canvas = Color(0xFF111111), canvasWarm = Color(0xFF222222), canvasCool = Color(0xFF333333),
        grid = Color(0x1F30302E), contentSurface = Color.White, nodeLilac = Color.White, nodeAmber = Color.White,
        nodeMint = Color.White, contentBorder = Color.Gray, contentText = Color.Black, contentMuted = Color.Gray,
        shellSurface = Color.White, shellSurfaceOpaque = Color.White, shellBorder = Color.White,
        shellText = Color.Black, accent = Color.Blue, selection = Color.Blue, danger = Color.Red,
    )

    @Test
    fun presetsFollowTheThemeAndHexTokensAreLiteral() {
        assertEquals(colors.canvasWarm, canvasBackgroundColor("warm", colors))
        assertEquals(colors.canvas, canvasBackgroundColor("unknown", colors))
        assertEquals(Color(0xFF6255D9), canvasBackgroundColor("#6255d9", colors))
    }

    @Test
    fun customBackgroundsGetVisibleGridInk() {
        assertEquals(colors.grid, canvasGridColor("default", colors))
        assertEquals(Color(0x24000000), canvasGridColor("#f4f3ef", colors))
        assertEquals(Color(0x33FFFFFF), canvasGridColor("#101820", colors))
    }

    @Test
    fun labelsShowPresetNamesOrHex() {
        assertEquals("Warm", canvasBackgroundLabel("warm"))
        assertEquals("#101820", canvasBackgroundLabel("#101820"))
    }
}
