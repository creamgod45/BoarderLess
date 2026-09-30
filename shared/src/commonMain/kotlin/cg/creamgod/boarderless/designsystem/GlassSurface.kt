package cg.creamgod.boarderless.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 18.dp, shape = shape)
            .clip(shape)
            .background(
                if (BoarderLessTheme.reduceTransparency) {
                    colors.shellSurfaceOpaque
                } else {
                    colors.shellSurface
                },
            )
            .border(width = 1.dp, color = colors.shellBorder, shape = shape),
        content = content,
    )
}

/** Space between neighbouring shell buttons, in rows and in stacked lists alike. */
val ButtonGap = 8.dp

@Composable
fun ShellButton(
    label: String,
    enabled: Boolean = true,
    accent: Boolean = false,
    icon: ShellIcon? = null,
    showLabel: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val background = when {
        !enabled -> Color.Transparent
        accent -> colors.accent
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> colors.contentMuted.copy(alpha = 0.5f)
        accent -> Color.White
        else -> colors.shellText
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .then(
                if (showLabel) Modifier else Modifier.semantics { contentDescription = label },
            )
            .border(
                width = if (focused && enabled) 2.dp else 0.dp,
                color = colors.selection,
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { ShellIconGlyph(icon = it, tint = foreground) }
            if (showLabel) {
                BasicText(
                    text = label,
                    style = TextStyle(
                        color = foreground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
