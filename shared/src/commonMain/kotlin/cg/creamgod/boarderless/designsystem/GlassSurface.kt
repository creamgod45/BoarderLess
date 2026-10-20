package cg.creamgod.boarderless.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazePerformanceMode

/**
 * Backdrop-blur source shared by every [GlassSurface]. The screen that owns the content to blur creates a
 * [HazeState], marks that content with `Modifier.hazeSource(state)` and provides the state here; without a
 * provider the glass simply stays translucent.
 */
val LocalGlassHazeState = compositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalHazeApi::class)
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(18.dp)
    val lightTheme = colors.canvas.luminance() > 0.5f
    val hazeState = LocalGlassHazeState.current
    Box(
        modifier =
            modifier
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = if (lightTheme) Color.White else Color.Black,
                    spotColor = if (lightTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                )
                .clip(shape)
                .then(
                    if (hazeState == null || BoarderLessTheme.reduceTransparency) Modifier
                    else Modifier.hazeGlass(HazeInput.Sources(hazeState), GlassStyle.clear, HazePerformanceMode.Performance),
                )
                .background(
                    brush = Brush.linearGradient(
                        colors = if (lightTheme) {
                            // White on a near-white canvas glows; tint the glass darker instead.
                            listOf(
                                Color.White.copy(alpha = 0.13f),
                                Color.White.copy(alpha = 0.12f),
                            )
                        } else {
                            listOf(
                                Color.White.copy(alpha = 0.19f),
                                Color.White.copy(alpha = 0.15f),
                            )
                        }
                    ),
                )
                .border(
                    width = if (lightTheme) 2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = shape
                )
                .padding(6.dp),
        content = content,
    )
}

/** Space between neighbouring shell buttons, in rows and in stacked lists alike. */
val ButtonGap = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShellButton(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    icon: ShellIcon? = null,
    showLabel: Boolean = true,
    compact: Boolean = false,
    hint: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(if (compact) 8.dp else 12.dp)
    // Icon-only and compact buttons are terse by design, so they always explain themselves: hover (pointer)
    // or long press (touch) shows the explicit hint, or the full label when none was given.
    val hint = hint ?: label.takeIf { (!showLabel || compact) && it.isNotBlank() }
    var focused by remember { mutableStateOf(false) }
    var hovered by remember { mutableStateOf(false) }
    var pressedHint by remember { mutableStateOf(false) }
    // Touch has no hover: a long press shows the hint briefly.
    LaunchedEffect(pressedHint) {
        if (pressedHint) {
            delay(3000)
            pressedHint = false
        }
    }
    val background =
        when {
            !enabled -> Color.Transparent
            accent -> colors.accent
            else -> Color.Transparent
        }
    val foreground =
        when {
            !enabled -> colors.contentMuted.copy(alpha = 0.5f)
            accent -> Color.White
            else -> colors.shellText
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(background)
                .then(
                    if (showLabel) Modifier else Modifier.semantics { contentDescription = label },
                ).border(
                    width = if (focused && enabled) 2.dp else 0.dp,
                    color = colors.selection,
                    shape = shape,
                ).then(
                    if (hint == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    when (awaitPointerEvent().type) {
                                        PointerEventType.Enter -> hovered = true
                                        PointerEventType.Exit, PointerEventType.Press -> hovered = false
                                        else -> Unit
                                    }
                                }
                            }
                        }
                    },
                ).combinedClickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                    onLongClick = if (hint == null) null else ({ pressedHint = true }),
                )
                // A fixed square is only right for icon-only buttons; a labelled compact chip must grow to
                // fit its text (a 28dp square used to clip every chip to its first character).
                .then(if (compact && !showLabel) Modifier.size(28.dp) else Modifier)
                .heightIn(min = if (compact) 28.dp else 44.dp)
                .then(if (compact && showLabel) Modifier.widthIn(min = 28.dp) else Modifier)
                .padding(horizontal = if (compact) 6.dp else 14.dp, vertical = if (compact) 6.dp else 9.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                ShellIconGlyph(
                    icon = it,
                    tint = foreground,
                    modifier = if (compact) Modifier.size(14.dp) else Modifier,
                )
            }
            if (showLabel) {
                BasicText(
                    text = label,
                    style =
                        TextStyle(
                            color = foreground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
            }
        }
        if (hint != null && (hovered || pressedHint)) ShellHint(hint)
    }
}

@Composable
private fun ShellHint(text: String) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(8.dp)
    Popup(
        popupPositionProvider =
            object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize,
                ): IntOffset {
                    val gap = 6
                    val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val below = anchorBounds.bottom + gap
                    val y = if (below + popupContentSize.height <= windowSize.height) below else (anchorBounds.top - gap - popupContentSize.height).coerceAtLeast(0)
                    return IntOffset(x, y)
                }
            },
    ) {
        Box(
            Modifier
                .widthIn(max = 260.dp)
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(colors.shellSurfaceOpaque)
                .border(width = 1.dp, color = colors.shellBorder, shape = shape)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            BasicText(text, style = TextStyle(color = colors.shellText, fontSize = 12.sp))
        }
    }
}
