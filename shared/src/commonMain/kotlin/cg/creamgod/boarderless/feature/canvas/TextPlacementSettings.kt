package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

internal fun textAnchorLabel(anchor: TextAnchor): String = when (anchor) {
    TextAnchor.TopLeft -> Strings.textPosition.topLeft()
    TextAnchor.TopCenter -> Strings.textPosition.topCenter()
    TextAnchor.TopRight -> Strings.textPosition.topRight()
    TextAnchor.CenterLeft -> Strings.textPosition.centerLeft()
    TextAnchor.Center -> Strings.textPosition.center()
    TextAnchor.CenterRight -> Strings.textPosition.centerRight()
    TextAnchor.BottomLeft -> Strings.textPosition.bottomLeft()
    TextAnchor.BottomCenter -> Strings.textPosition.bottomCenter()
    TextAnchor.BottomRight -> Strings.textPosition.bottomRight()
    TextAnchor.Above -> Strings.textPosition.above()
    TextAnchor.Below -> Strings.textPosition.below()
    TextAnchor.Left -> Strings.textPosition.left()
    TextAnchor.Right -> Strings.textPosition.right()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TextPlacementSettings(initial: TextPlacement?, enabled: Boolean, onApply: (TextPlacement?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var anchor by remember(initial) { mutableStateOf(initial?.anchor) }
    var x by remember(initial) { mutableStateOf((initial?.offsetX ?: 0f).toString()) }
    var y by remember(initial) { mutableStateOf((initial?.offsetY ?: 0f).toString()) }
    var appearance by remember(initial) { mutableStateOf<TextAppearance?>(initial?.appearance ?: TextAppearance()) }
    val candidate = runCatching { TextPlacement(anchor, x.toFloat(), y.toFloat(), requireNotNull(appearance)) }.getOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShellButton(Strings.textPosition.title(), accent = expanded, onClick = { expanded = !expanded })
        if (expanded) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap), verticalArrangement = Arrangement.spacedBy(ButtonGap)) {
            TextAnchor.entries.forEach { value ->
                ShellButton(textAnchorLabel(value), enabled = enabled, accent = anchor == value,
                    onClick = { anchor = value })
            }
        }
        TextAppearanceSettings(initial?.appearance ?: TextAppearance(), enabled) { appearance = it }
        AiProbeField(Strings.textPosition.offsetX(), x, { if (it.length <= 16) x = it }, enabled = enabled)
        AiProbeField(Strings.textPosition.offsetY(), y, { if (it.length <= 16) y = it }, enabled = enabled)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
            ShellButton(Strings.textPosition.apply(), enabled = enabled && candidate != null,
                onClick = { candidate?.let(onApply) })
            ShellButton(Strings.textPosition.reset(), enabled = enabled,
                onClick = {
                    val style = appearance ?: initial?.appearance ?: TextAppearance()
                    onApply(if (style == TextAppearance()) null else TextPlacement(appearance = style))
                    anchor = null; x = "0"; y = "0"
                })
        }
        }
    }
}
