package cg.creamgod.boarderless.feature.canvas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.*
import cg.creamgod.boarderless.designsystem.color.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.i18n.Strings

internal fun labelTextStyle(appearance: TextAppearance?, zoom: Float, fallback: Color, align: TextAlign = TextAlign.Start): TextStyle {
    val style = appearance ?: TextAppearance()
    val decorations = buildList {
        if (style.underline) add(TextDecoration.Underline)
        if (style.strikethrough) add(TextDecoration.LineThrough)
    }
    return TextStyle(
        color = style.color?.let(::customNodeColor)?.let { Color(it.r, it.g, it.b) } ?: fallback,
        fontSize = (style.fontSize * zoom).sp,
        fontWeight = FontWeight(style.weight),
        fontFamily = when (style.font) {
            TextFont.System -> FontFamily.Default
            TextFont.SansSerif -> FontFamily.SansSerif
            TextFont.Serif -> FontFamily.Serif
            TextFont.Monospace -> FontFamily.Monospace
            TextFont.Cursive -> FontFamily.Cursive
        },
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations),
        textAlign = align,
    )
}

internal fun textFontLabel(font: TextFont): String = when (font) {
    TextFont.System -> Strings.textPosition.systemFont()
    TextFont.SansSerif -> Strings.textPosition.sansSerif()
    TextFont.Serif -> Strings.textPosition.serif()
    TextFont.Monospace -> Strings.textPosition.monospace()
    TextFont.Cursive -> Strings.textPosition.cursive()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun TextAppearanceSettings(initial: TextAppearance, enabled: Boolean, onChange: (TextAppearance?) -> Unit) {
    var size by remember(initial) { mutableStateOf(initial.fontSize.toString()) }
    var weight by remember(initial) { mutableStateOf(initial.weight.toString()) }
    var color by remember(initial) { mutableStateOf(initial.color ?: "") }
    var pickerInitial by remember { mutableStateOf<String?>(null) }
    var colorModel by remember { mutableStateOf(ColorModel.Hsb) }
    var font by remember(initial) { mutableStateOf(initial.font) }
    var italic by remember(initial) { mutableStateOf(initial.italic) }
    var underline by remember(initial) { mutableStateOf(initial.underline) }
    var strike by remember(initial) { mutableStateOf(initial.strikethrough) }
    val candidate = runCatching { TextAppearance(size.toFloat(), weight.toInt(), color.trim().takeIf(String::isNotEmpty), font, italic, underline, strike) }.getOrNull()
    LaunchedEffect(candidate) { onChange(candidate) }
    AiProbeField(Strings.textPosition.fontSize(), size, { if (it.length <= 10) size = it }, enabled = enabled)
    AiProbeField(Strings.textPosition.fontWeight(), weight, { if (it.length <= 4) weight = it }, enabled = enabled)
    AiProbeField(Strings.textPosition.textColor(), color, { if (it.length <= 7) color = it }, enabled = enabled)
    ShellButton(Strings.colorPicker.title(), enabled = enabled, onClick = { pickerInitial = color })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
        listOf("", "#000000", "#ffffff", "#e53935", "#1976d2", "#388e3c").forEach { value ->
            ShellButton(if (value.isEmpty()) Strings.textPosition.autoColor() else value, enabled = enabled, accent = color == value, onClick = { color = value })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
        TextFont.entries.forEach { value -> ShellButton(textFontLabel(value), enabled = enabled, accent = font == value, onClick = { font = value }) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
        ShellButton(Strings.textPosition.italic(), enabled = enabled, accent = italic, onClick = { italic = !italic })
        ShellButton(Strings.textPosition.underline(), enabled = enabled, accent = underline, onClick = { underline = !underline })
        ShellButton(Strings.textPosition.strikethrough(), enabled = enabled, accent = strike, onClick = { strike = !strike })
    }
    pickerInitial?.let { before ->
        ColorPickerWindow(
            initialToken = before.ifEmpty { "#000000" },
            initialColor = customNodeColor(before) ?: RgbColor(0f, 0f, 0f),
            presets = listOf("#000000", "#ffffff", "#e53935", "#1976d2", "#388e3c").map { token ->
                val rgb = requireNotNull(customNodeColor(token))
                ColorSwatchOption(token, token, Color(rgb.r, rgb.g, rgb.b))
            },
            recentColors = emptyList(), model = colorModel, onModelChange = { colorModel = it },
            onPreview = { color = it }, onApply = { color = it; pickerInitial = null },
            onCancel = { color = before; pickerInitial = null }, onEditingChange = {},
        )
    }
    BasicText(Strings.textPosition.sample(), modifier = Modifier.heightIn(max = 80.dp).clipToBounds(), style = labelTextStyle(candidate, 1f, BoarderLessTheme.colors.contentText))
}
