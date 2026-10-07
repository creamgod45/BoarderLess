package cg.creamgod.boarderless.designsystem.color

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cg.creamgod.boarderless.designsystem.BoarderLessTheme
import cg.creamgod.boarderless.designsystem.ButtonGap
import cg.creamgod.boarderless.designsystem.GlassSurface
import cg.creamgod.boarderless.designsystem.ShellButton
import cg.creamgod.boarderless.designsystem.ShellIcon
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.roundToInt

/** A named color the picker can offer; [token] is what the caller stores when it is chosen. */
@Immutable
data class ColorSwatchOption(
    val token: String,
    val label: String,
    val color: Color,
)

/**
 * A floating, draggable color picker in the spirit of Adobe's: a saturation/brightness field with a
 * hue strip, new/current preview swatches, HEX entry first, and channel editing in HSB (HSV), HSL,
 * RGB, Oklab or Oklch. Picking a [presets] swatch reports its token; any other color reports
 * `#rrggbb`. [onPreview] fires for every change so the caller can render the result live.
 */
@Composable
fun ColorPickerWindow(
    initialToken: String,
    initialColor: RgbColor,
    presets: List<ColorSwatchOption>,
    recentColors: List<RgbColor>,
    model: ColorModel,
    onModelChange: (ColorModel) -> Unit,
    onPreview: (token: String) -> Unit,
    onApply: (token: String) -> Unit,
    onCancel: () -> Unit,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = Strings.colorPicker.title(),
    fieldSize: Dp = 188.dp,
) {
    val colors = BoarderLessTheme.colors
    val initialPreset = initialToken.takeIf { token -> presets.any { it.token == token } }
    var hsb by remember { mutableStateOf(HsbState.from(initialColor)) }
    var presetToken by remember { mutableStateOf(initialPreset) }
    // Channel values the user typed or dragged in the active model. Kept verbatim so an Oklab/Oklch
    // value outside sRGB, or a hue at zero chroma, does not snap while it is being edited.
    var draft by remember { mutableStateOf<List<Float>?>(null) }
    var windowOffset by remember { mutableStateOf(Offset.Zero) }

    val rgb = hsb.toRgb()
    val token = presetToken ?: rgb.toHex().lowercase()
    val channelValues = draft ?: hsb.valuesIn(model)
    val draftOutOfGamut = draft?.let { !model.toRgb(it).inGamut } == true
    val currentOnPreview by rememberUpdatedState(onPreview)
    LaunchedEffect(token) { currentOnPreview(token) }

    fun pick(
        color: RgbColor,
        preset: String? = null,
        keepDraft: List<Float>? = null,
    ) {
        hsb = hsb.adopt(color)
        presetToken = preset
        draft = keepDraft
    }

    fun setChannel(
        index: Int,
        value: Float,
    ) {
        val next = channelValues.toMutableList().also { it[index] = model.channels[index].coerce(value) }
        if (model == ColorModel.Hsb) {
            hsb = HsbState(next[0], next[1] / 100f, next[2] / 100f)
            presetToken = null
            draft = null
        } else {
            pick(model.toRgb(next).clamped(), keepDraft = next)
        }
    }

    GlassSurface(
        modifier =
            modifier
                .offset { IntOffset(windowOffset.x.roundToInt(), windowOffset.y.roundToInt()) }
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onCancel()
                        true
                    } else {
                        false
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .width(fieldSize + 124.dp)
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                windowOffset += dragAmount
                            }
                        },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    style = TextStyle(color = colors.contentText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                )
                ShellButton(
                    label = Strings.common.close(),
                    icon = ShellIcon.Close,
                    showLabel = false,
                    onClick = onCancel,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SaturationBrightnessField(
                    hue = hsb.hue,
                    saturation = hsb.saturation,
                    brightness = hsb.brightness,
                    size = fieldSize,
                    onChange = { saturation, brightness ->
                        hsb = hsb.copy(saturation = saturation, brightness = brightness)
                        presetToken = null
                        draft = null
                    },
                )
                HueStrip(
                    hue = hsb.hue,
                    height = fieldSize,
                    onChange = { hue ->
                        hsb = hsb.copy(hue = hue)
                        presetToken = null
                        draft = null
                    },
                )
                Column(
                    modifier = Modifier.width(62.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SwatchCaption(Strings.colorPicker.new())
                    // Adobe-style preview: the new color stacked on the current one, framed so dark
                    // colors stay visible against the glass surface.
                    Column(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.contentBorder, RoundedCornerShape(10.dp)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 62.dp, height = 48.dp)
                                    .background(rgb.toComposeColor())
                                    .semantics { contentDescription = Strings.colorPicker.newColor(rgb.toHex()) },
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 62.dp, height = 48.dp)
                                    .background(initialColor.toComposeColor())
                                    .clickable(role = Role.Button) { pick(initialColor, initialPreset) }
                                    .semantics {
                                        contentDescription = Strings.colorPicker.revertToCurrentColor(initialColor.toHex())
                                    },
                        )
                    }
                    SwatchCaption(Strings.colorPicker.current())
                }
            }

            HexField(
                hex = rgb.toHex(),
                onHexChange = { pick(it) },
                onEditingChange = onEditingChange,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ColorModel.entries.forEach { entry ->
                    PickerChip(
                        label = entry.label,
                        selected = entry == model,
                        onClick = {
                            draft = null
                            onModelChange(entry)
                        },
                    )
                }
            }

            model.channels.forEachIndexed { index, channel ->
                ChannelRow(
                    model = model,
                    channel = channel,
                    values = channelValues,
                    index = index,
                    onChange = { setChannel(index, it) },
                    onEditingChange = onEditingChange,
                )
            }
            if (draftOutOfGamut) {
                BasicText(
                    text = Strings.colorPicker.outsideSrgbGamutDisplayedColor(),
                    style = TextStyle(color = colors.danger, fontSize = 10.sp),
                )
            }

            SwatchSection(Strings.colorPicker.themeColors()) {
                presets.forEach { preset ->
                    PickerSwatch(
                        color = preset.color,
                        selected = presetToken == preset.token,
                        description = preset.label,
                        onClick = {
                            pick(RgbColor(preset.color.red, preset.color.green, preset.color.blue), preset.token)
                        },
                    )
                }
            }
            if (recentColors.isNotEmpty()) {
                SwatchSection(Strings.colorPicker.colorsInThisSpace()) {
                    recentColors.forEach { recent ->
                        PickerSwatch(
                            color = recent.toComposeColor(),
                            selected = presetToken == null && recent.toHex() == rgb.toHex(),
                            description = recent.toHex(),
                            onClick = { pick(recent) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.End),
            ) {
                ShellButton(
                    label = Strings.colorPicker.cancel(),
                    icon = ShellIcon.Close,
                    onClick = onCancel,
                )
                ShellButton(label = Strings.colorPicker.apply(), accent = true, onClick = { onApply(token) })
            }
        }
    }
}

/** Compact swatch + label button for inspectors; [color] null draws a hue ring for "custom". */
@Composable
fun ColorSwatchButton(
    label: String,
    color: Color?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(if (selected && enabled) colors.selection.copy(alpha = 0.16f) else Color.Transparent)
                .border(if (selected && enabled) 1.5.dp else 0.dp, colors.selection, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { if (selected) stateDescription = Strings.colorPicker.selected() }
                .heightIn(min = 44.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val alpha = if (enabled) 1f else 0.45f
        if (color != null) {
            Box(
                modifier =
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color.copy(alpha = alpha))
                        .border(1.dp, colors.contentBorder, RoundedCornerShape(5.dp)),
            )
        } else {
            Canvas(modifier = Modifier.size(18.dp)) {
                drawCircle(brush = Brush.sweepGradient(HueStops.map { it.copy(alpha = alpha) }))
                drawCircle(color = colors.shellSurfaceOpaque, radius = size.minDimension * 0.22f)
            }
        }
        BasicText(
            text = label,
            style =
                TextStyle(
                    color = if (enabled) colors.shellText else colors.contentMuted.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}

/** HSB is the picker's source of truth so hue and saturation survive passing through gray or black. */
private data class HsbState(
    val hue: Float,
    val saturation: Float,
    val brightness: Float,
) {
    fun toRgb(): RgbColor = hsbToRgb(hue, saturation, brightness)

    fun adopt(color: RgbColor): HsbState {
        val (h, s, v) = rgbToHsb(color)
        return HsbState(
            hue = if (s < 0.0001f || v < 0.0001f) hue else h,
            saturation = if (v < 0.0001f) saturation else s,
            brightness = v,
        )
    }

    fun valuesIn(model: ColorModel): List<Float> =
        when (model) {
            ColorModel.Hsb -> {
                listOf(hue, saturation * 100f, brightness * 100f)
            }

            ColorModel.Hsl -> {
                model.fromRgb(toRgb()).let { values ->
                    if (values[1] < 0.01f) listOf(hue) + values.drop(1) else values
                }
            }

            else -> {
                model.fromRgb(toRgb())
            }
        }

    companion object {
        fun from(color: RgbColor): HsbState = rgbToHsb(color).let { HsbState(it[0], it[1], it[2]) }
    }
}

private val HueStops = (0..6).map { Color(hsbToRgb(it * 60f, 1f, 1f).toArgb()) }

private fun RgbColor.toComposeColor(): Color = clamped().let { Color(it.r, it.g, it.b) }

@Composable
private fun SaturationBrightnessField(
    hue: Float,
    saturation: Float,
    brightness: Float,
    size: Dp,
    onChange: (saturation: Float, brightness: Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    val hueColor = Color(hsbToRgb(hue, 1f, 1f).toArgb())
    Canvas(
        modifier =
            Modifier
                .size(size)
                .clip(RoundedCornerShape(10.dp))
                .semantics {
                    contentDescription = Strings.colorPicker.saturationAndBrightness()
                    stateDescription = "S ${(saturation * 100).roundToInt()}% • B ${(brightness * 100).roundToInt()}%"
                }.pointerInput(Unit) {
                    fun report(position: Offset) =
                        currentOnChange(
                            (position.x / this.size.width).coerceIn(0f, 1f),
                            1f - (position.y / this.size.height).coerceIn(0f, 1f),
                        )
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        report(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            report(change.position)
                        }
                    }
                },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * this.size.width, (1f - brightness) * this.size.height)
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = 8.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius = 8.dp.toPx(), center = center, style = Stroke(1.75.dp.toPx()))
    }
}

@Composable
private fun HueStrip(
    hue: Float,
    height: Dp,
    onChange: (Float) -> Unit,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier =
            Modifier
                .size(width = 22.dp, height = height)
                .clip(RoundedCornerShape(8.dp))
                .semantics {
                    contentDescription = Strings.colorPicker.hue()
                    stateDescription = "${hue.roundToInt()}°"
                }.pointerInput(Unit) {
                    fun report(position: Offset) = currentOnChange((position.y / size.height).coerceIn(0f, 1f) * 359.99f)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        report(down.position)
                        drag(down.id) { change ->
                            change.consume()
                            report(change.position)
                        }
                    }
                },
    ) {
        drawRect(Brush.verticalGradient(HueStops))
        val y = hue / 360f * size.height
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(1.dp.toPx(), y - 3.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), 6.dp.toPx()),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(2.dp.toPx()),
        )
    }
}

@Composable
private fun HexField(
    hex: String,
    onHexChange: (RgbColor) -> Unit,
    onEditingChange: (Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    var field by remember { mutableStateOf(TextFieldValue(hex.removePrefix("#"))) }
    var focused by remember { mutableStateOf(false) }
    // The pointer that focuses the field also places the caret; skip that one collapse of the selection.
    var keepSelectAll by remember { mutableStateOf(false) }
    LaunchedEffect(hex, focused) { if (!focused) field = TextFieldValue(hex.removePrefix("#")) }

    fun commit() {
        val committed = (RgbColor.parseHex(field.text)?.also(onHexChange)?.toHex() ?: hex).removePrefix("#")
        field = TextFieldValue(committed, TextRange(committed.length))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = "HEX",
            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                    .border(1.dp, if (focused) colors.selection else colors.contentBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("#", style = TextStyle(color = colors.contentMuted, fontSize = 14.sp, fontFamily = FontFamily.Monospace))
            BasicTextField(
                value = field,
                onValueChange = { next ->
                    if (keepSelectAll && next.text == field.text) {
                        keepSelectAll = false
                        return@BasicTextField
                    }
                    keepSelectAll = false
                    val cleaned =
                        next.text
                            .trim()
                            .removePrefix("#")
                            .take(6)
                    field = next.copy(text = cleaned, selection = TextRange(minOf(next.selection.end, cleaned.length)))
                    // Six valid digits apply immediately, so pasting a code is a single step.
                    if (cleaned.length == 6) RgbColor.parseHex(cleaned)?.let(onHexChange)
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { contentDescription = Strings.colorPicker.hexColorCode() }
                        .onFocusChanged { state ->
                            if (focused && !state.isFocused) commit()
                            if (!focused && state.isFocused) {
                                field = field.copy(selection = TextRange(0, field.text.length))
                                keepSelectAll = true
                            }
                            focused = state.isFocused
                            onEditingChange(state.isFocused)
                        }.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                commit()
                                true
                            } else {
                                false
                            }
                        },
                textStyle =
                    TextStyle(
                        color = colors.contentText,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                cursorBrush = SolidColor(colors.selection),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ChannelRow(
    model: ColorModel,
    channel: ColorChannel,
    values: List<Float>,
    index: Int,
    onChange: (Float) -> Unit,
    onEditingChange: (Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val value = values[index]
    val currentOnChange by rememberUpdatedState(onChange)
    val trackStops =
        remember(model, values, index) {
            (0..16).map { step ->
                val sample = values.toMutableList()
                sample[index] = channel.min + (channel.max - channel.min) * step / 16f
                model.toRgb(sample).toComposeColor()
            }
        }
    val fraction = ((value - channel.min) / (channel.max - channel.min)).coerceIn(0f, 1f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BasicText(
            text = channel.symbol,
            modifier = Modifier.width(14.dp),
            style = TextStyle(color = colors.contentMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
        )
        Canvas(
            modifier =
                Modifier
                    .weight(1f)
                    .height(22.dp)
                    .semantics {
                        contentDescription = channel.name()
                        stateDescription = channel.format(value) + channel.unit
                    }.pointerInput(channel) {
                        fun report(position: Offset) =
                            currentOnChange(
                                channel.min + (channel.max - channel.min) * (position.x / size.width).coerceIn(0f, 1f),
                            )
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            report(down.position)
                            drag(down.id) { change ->
                                change.consume()
                                report(change.position)
                            }
                        }
                    },
        ) {
            val trackHeight = 10.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            drawRoundRect(
                brush = Brush.horizontalGradient(trackStops),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            val center = Offset(fraction * size.width, size.height / 2f)
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 8.dp.toPx(), center = center)
            drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
            drawCircle(trackStops[(fraction * 16).roundToInt()], radius = 4.5.dp.toPx(), center = center)
        }
        ChannelField(channel = channel, value = value, onCommit = onChange, onEditingChange = onEditingChange)
    }
}

@Composable
private fun ChannelField(
    channel: ColorChannel,
    value: Float,
    onCommit: (Float) -> Unit,
    onEditingChange: (Boolean) -> Unit,
) {
    val colors = BoarderLessTheme.colors
    var text by remember(channel) { mutableStateOf(channel.format(value)) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused, channel) { if (!focused) text = channel.format(value) }

    fun commit() {
        val parsed =
            text
                .trim()
                .removeSuffix(channel.unit)
                .replace(',', '.')
                .trim()
                .toFloatOrNull()
        if (parsed != null) onCommit(channel.coerce(parsed))
        text = channel.format(parsed?.let(channel::coerce) ?: value)
    }
    Row(
        modifier =
            Modifier
                .width(64.dp)
                .background(colors.canvas.copy(alpha = 0.78f), RoundedCornerShape(8.dp))
                .border(1.dp, if (focused) colors.selection else colors.contentBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { if (it.length <= 7) text = it },
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = channel.name() }
                    .onFocusChanged { state ->
                        if (focused && !state.isFocused) commit()
                        focused = state.isFocused
                        onEditingChange(state.isFocused)
                    }.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                            commit()
                            true
                        } else {
                            false
                        }
                    },
            textStyle = TextStyle(color = colors.contentText, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
            cursorBrush = SolidColor(colors.selection),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            singleLine = true,
        )
        if (channel.unit.isNotEmpty()) {
            BasicText(channel.unit, style = TextStyle(color = colors.contentMuted, fontSize = 10.sp))
        }
    }
}

@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    val shape = RoundedCornerShape(8.dp)
    BasicText(
        text = label,
        modifier =
            Modifier
                .clip(shape)
                .background(if (selected) colors.accent else colors.canvas.copy(alpha = 0.6f))
                .border(1.dp, if (selected) colors.accent else colors.contentBorder, shape)
                .clickable(role = Role.Tab, onClick = onClick)
                .semantics { if (selected) stateDescription = Strings.colorPicker.selected() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        style =
            TextStyle(
                color = if (selected) Color.White else colors.shellText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
    )
}

@Composable
private fun SwatchSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        BasicText(title, style = TextStyle(color = colors.contentMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ButtonGap),
            verticalArrangement = Arrangement.spacedBy(ButtonGap),
        ) { content() }
    }
}

@Composable
private fun PickerSwatch(
    color: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = BoarderLessTheme.colors
    Box(
        modifier =
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.selection else colors.contentBorder, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
    )
}

@Composable
private fun SwatchCaption(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(vertical = 3.dp),
        style = TextStyle(color = BoarderLessTheme.colors.contentMuted, fontSize = 10.sp),
    )
}
