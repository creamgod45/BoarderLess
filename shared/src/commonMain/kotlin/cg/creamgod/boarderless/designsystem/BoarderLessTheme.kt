package cg.creamgod.boarderless.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BoarderLessColors(
    val canvas: Color,
    val canvasWarm: Color,
    val canvasCool: Color,
    val grid: Color,
    val contentSurface: Color,
    val nodeLilac: Color,
    val nodeAmber: Color,
    val nodeMint: Color,
    val contentBorder: Color,
    val contentText: Color,
    val contentMuted: Color,
    val shellSurface: Color,
    val shellSurfaceOpaque: Color,
    val shellBorder: Color,
    val shellText: Color,
    val accent: Color,
    val selection: Color,
    val danger: Color,
)

private val LightColors =
    BoarderLessColors(
        canvas = Color(0xFFF4F3EF),
        canvasWarm = Color(0xFFF8F0E6),
        canvasCool = Color(0xFFEAF1F5),
        grid = Color(0x1F30302E),
        contentSurface = Color(0xFFFFFEFA),
        nodeLilac = Color(0xFFF0EDFF),
        nodeAmber = Color(0xFFFFF1CF),
        nodeMint = Color(0xFFE1F5E9),
        contentBorder = Color(0xFFD8D5CB),
        contentText = Color(0xFF242421),
        contentMuted = Color(0xFF6F6D65),
        shellSurface = Color(0xD9FFFFFF),
        shellSurfaceOpaque = Color(0xFFF8F8F5),
        shellBorder = Color(0x66FFFFFF),
        shellText = Color(0xFF242421),
        accent = Color(0xFF6255D9),
        selection = Color(0xFF6255D9),
        danger = Color(0xFFB53A3A),
    )

private val DarkColors =
    BoarderLessColors(
        canvas = Color(0xFF181817),
        canvasWarm = Color(0xFF211B18),
        canvasCool = Color(0xFF171F24),
        grid = Color(0x2EFFFFFF),
        contentSurface = Color(0xFF262624),
        nodeLilac = Color(0xFF342F4D),
        nodeAmber = Color(0xFF453A25),
        nodeMint = Color(0xFF263F33),
        contentBorder = Color(0xFF464641),
        contentText = Color(0xFFF2F1EB),
        contentMuted = Color(0xFFAAA79E),
        shellSurface = Color(0xD92C2C2A),
        shellSurfaceOpaque = Color(0xFF2C2C2A),
        shellBorder = Color(0x38FFFFFF),
        shellText = Color(0xFFF4F3EE),
        accent = Color(0xFF9F94FF),
        selection = Color(0xFF9F94FF),
        danger = Color(0xFFFF8A8A),
    )

private val LocalBoarderLessColors = staticCompositionLocalOf { LightColors }
private val LocalReduceTransparency = staticCompositionLocalOf { false }

object BoarderLessTheme {
    val colors: BoarderLessColors
        @Composable get() = LocalBoarderLessColors.current

    val reduceTransparency: Boolean
        @Composable get() = LocalReduceTransparency.current
}

@Composable
fun BoarderLessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalBoarderLessColors provides if (darkTheme) DarkColors else LightColors,
        LocalReduceTransparency provides reduceTransparency,
        content = content,
    )
}
