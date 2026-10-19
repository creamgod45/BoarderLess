package cg.creamgod.boarderless.designsystem.color

import cg.creamgod.boarderless.i18n.LocalizedText
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Gamma-encoded sRGB with channels in 0..1. Values may fall outside 0..1 before [clamped]. */
data class RgbColor(
    val r: Float,
    val g: Float,
    val b: Float,
) {
    val inGamut: Boolean
        get() = listOf(r, g, b).all { it in -GamutTolerance..1f + GamutTolerance }

    fun clamped(): RgbColor = RgbColor(r.coerceIn(0f, 1f), g.coerceIn(0f, 1f), b.coerceIn(0f, 1f))

    /** `#RRGGBB`, uppercase. */
    fun toHex(): String {
        val c = clamped()
        return "#" +
            listOf(c.r, c.g, c.b)
                .joinToString("") { channel ->
                    (channel * 255f).roundToInt().toString(16).padStart(2, '0')
                }.uppercase()
    }

    fun toArgb(): Long {
        val c = clamped()
        return (0xFFL shl 24) or
            ((c.r * 255f).roundToInt().toLong() shl 16) or
            ((c.g * 255f).roundToInt().toLong() shl 8) or
            (c.b * 255f).roundToInt().toLong()
    }

    /** WCAG relative luminance. */
    fun relativeLuminance(): Float {
        val c = clamped()
        return 0.2126f * srgbToLinear(c.r) + 0.7152f * srgbToLinear(c.g) + 0.0722f * srgbToLinear(c.b)
    }

    companion object {
        private const val GamutTolerance = 0.0005f

        /** Accepts `#RGB`, `#RRGGBB`, with or without `#`, surrounding spaces and any case. */
        fun parseHex(input: String): RgbColor? {
            val raw = input.trim().removePrefix("#")
            val expanded =
                when (raw.length) {
                    3 -> raw.map { "$it$it" }.joinToString("")
                    6 -> raw
                    else -> return null
                }
            if (!expanded.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
            val value = expanded.toInt(16)
            return RgbColor(
                r = ((value shr 16) and 0xFF) / 255f,
                g = ((value shr 8) and 0xFF) / 255f,
                b = (value and 0xFF) / 255f,
            )
        }

        fun fromArgb(argb: Long): RgbColor =
            RgbColor(
                r = ((argb shr 16) and 0xFF) / 255f,
                g = ((argb shr 8) and 0xFF) / 255f,
                b = (argb and 0xFF) / 255f,
            )
    }
}

fun contrastRatio(
    first: RgbColor,
    second: RgbColor,
): Float {
    val a = first.relativeLuminance()
    val b = second.relativeLuminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/** One editable channel of a [ColorModel]; [decimals] controls display and text entry precision. */
data class ColorChannel(
    val symbol: String,
    /** Full channel name for accessibility, e.g. "Chroma". */
    val name: LocalizedText,
    val min: Float,
    val max: Float,
    val decimals: Int = 0,
    val unit: String = "",
) {
    fun coerce(value: Float): Float = value.coerceIn(min, max)

    fun format(value: Float): String {
        if (decimals == 0) return value.roundToInt().toString()
        val factor = 10f.pow(decimals)
        val rounded = (value * factor).roundToInt() / factor
        val text = rounded.toString()
        val dot = text.indexOf('.')
        return if (dot < 0) text + "." + "0".repeat(decimals) else text.padEnd(dot + 1 + decimals, '0')
    }
}

enum class ColorModel(
    val label: String,
    val channels: List<ColorChannel>,
) {
    Hsb(
        "HSB",
        listOf(
            ColorChannel("H", Strings.colorPicker.hue, 0f, 360f, unit = "°"),
            ColorChannel("S", Strings.colorPicker.saturation, 0f, 100f, unit = "%"),
            ColorChannel("B", Strings.colorPicker.brightness, 0f, 100f, unit = "%"),
        ),
    ),
    Hsl(
        "HSL",
        listOf(
            ColorChannel("H", Strings.colorPicker.hue, 0f, 360f, unit = "°"),
            ColorChannel("S", Strings.colorPicker.saturation, 0f, 100f, unit = "%"),
            ColorChannel("L", Strings.colorPicker.lightness, 0f, 100f, unit = "%"),
        ),
    ),
    Rgb(
        "RGB",
        listOf(
            ColorChannel("R", Strings.colorPicker.red, 0f, 255f),
            ColorChannel("G", Strings.colorPicker.green, 0f, 255f),
            ColorChannel("B", Strings.colorPicker.blue, 0f, 255f),
        ),
    ),
    Oklab(
        "Oklab",
        listOf(
            ColorChannel("L", Strings.colorPicker.perceptualLightness, 0f, 100f, unit = "%"),
            ColorChannel("a", Strings.colorPicker.greenRedAxis, -0.4f, 0.4f, decimals = 3),
            ColorChannel("b", Strings.colorPicker.blueYellowAxis, -0.4f, 0.4f, decimals = 3),
        ),
    ),
    Oklch(
        "Oklch",
        listOf(
            ColorChannel("L", Strings.colorPicker.perceptualLightness, 0f, 100f, unit = "%"),
            ColorChannel("C", Strings.colorPicker.chroma, 0f, 0.37f, decimals = 3),
            ColorChannel("H", Strings.colorPicker.hue, 0f, 360f, unit = "°"),
        ),
    ),
    ;

    fun fromRgb(rgb: RgbColor): List<Float> =
        when (this) {
            Hsb -> rgbToHsb(rgb).let { listOf(it[0], it[1] * 100f, it[2] * 100f) }
            Hsl -> rgbToHsl(rgb).let { listOf(it[0], it[1] * 100f, it[2] * 100f) }
            Rgb -> listOf(rgb.r * 255f, rgb.g * 255f, rgb.b * 255f)
            Oklab -> rgbToOklab(rgb).let { listOf(it[0] * 100f, it[1], it[2]) }
            Oklch -> rgbToOklch(rgb).let { listOf(it[0] * 100f, it[1], it[2]) }
        }

    /** Unclamped: Oklab/Oklch values can land outside sRGB; check [RgbColor.inGamut]. */
    fun toRgb(values: List<Float>): RgbColor {
        require(values.size == 3) { "A color model needs three channel values" }
        val (x, y, z) = values
        return when (this) {
            Hsb -> hsbToRgb(x, y / 100f, z / 100f)
            Hsl -> hslToRgb(x, y / 100f, z / 100f)
            Rgb -> RgbColor(x / 255f, y / 255f, z / 255f)
            Oklab -> oklabToRgb(x / 100f, y, z)
            Oklch -> oklchToRgb(x / 100f, y, z)
        }
    }
}

// region HSB / HSV and HSL — hue in degrees, other channels 0..1

fun rgbToHsb(rgb: RgbColor): FloatArray {
    val c = rgb.clamped()
    val max = maxOf(c.r, c.g, c.b)
    val delta = max - minOf(c.r, c.g, c.b)
    val saturation = if (max == 0f) 0f else delta / max
    return floatArrayOf(hueOf(c, max, delta), saturation, max)
}

fun hsbToRgb(
    hue: Float,
    saturation: Float,
    brightness: Float,
): RgbColor {
    val s = saturation.coerceIn(0f, 1f)
    val v = brightness.coerceIn(0f, 1f)
    val chroma = v * s
    return fromHueChroma(hue, chroma, v - chroma)
}

fun rgbToHsl(rgb: RgbColor): FloatArray {
    val c = rgb.clamped()
    val max = maxOf(c.r, c.g, c.b)
    val min = minOf(c.r, c.g, c.b)
    val delta = max - min
    val lightness = (max + min) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - abs(2f * lightness - 1f))
    return floatArrayOf(hueOf(c, max, delta), saturation.coerceIn(0f, 1f), lightness)
}

fun hslToRgb(
    hue: Float,
    saturation: Float,
    lightness: Float,
): RgbColor {
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val chroma = (1f - abs(2f * l - 1f)) * s
    return fromHueChroma(hue, chroma, l - chroma / 2f)
}

private fun hueOf(
    c: RgbColor,
    max: Float,
    delta: Float,
): Float {
    if (delta == 0f) return 0f
    val hue =
        when (max) {
            c.r -> 60f * (((c.g - c.b) / delta) % 6f)
            c.g -> 60f * ((c.b - c.r) / delta + 2f)
            else -> 60f * ((c.r - c.g) / delta + 4f)
        }
    return normalizeHue(hue)
}

private fun fromHueChroma(
    hue: Float,
    chroma: Float,
    offset: Float,
): RgbColor {
    val h = normalizeHue(hue) / 60f
    val x = chroma * (1f - abs(h % 2f - 1f))
    val (r, g, b) =
        when {
            h < 1f -> Triple(chroma, x, 0f)
            h < 2f -> Triple(x, chroma, 0f)
            h < 3f -> Triple(0f, chroma, x)
            h < 4f -> Triple(0f, x, chroma)
            h < 5f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
    return RgbColor(r + offset, g + offset, b + offset)
}

fun normalizeHue(hue: Float): Float {
    val wrapped = hue % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

// endregion

// region Oklab / Oklch (Björn Ottosson, https://bottosson.github.io/posts/oklab/) — L in 0..1

fun srgbToLinear(channel: Float): Float = if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

fun linearToSrgb(channel: Float): Float =
    if (abs(channel) <= 0.0031308f) {
        channel * 12.92f
    } else {
        (if (channel < 0f) -1f else 1f) * (1.055f * abs(channel).pow(1f / 2.4f) - 0.055f)
    }

fun rgbToOklab(rgb: RgbColor): FloatArray {
    val r = srgbToLinear(rgb.r)
    val g = srgbToLinear(rgb.g)
    val b = srgbToLinear(rgb.b)
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    return floatArrayOf(
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
    )
}

fun oklabToRgb(
    lightness: Float,
    a: Float,
    b: Float,
): RgbColor {
    val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
    val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
    val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
    return RgbColor(
        linearToSrgb(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s),
        linearToSrgb(-1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s),
        linearToSrgb(-0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s),
    )
}

fun rgbToOklch(rgb: RgbColor): FloatArray {
    val (l, a, b) = rgbToOklab(rgb)
    val chroma = sqrt(a * a + b * b)
    val hue = if (chroma < 0.0002f) 0f else normalizeHue((atan2(b, a) * 180.0 / PI).toFloat())
    return floatArrayOf(l, chroma, hue)
}

fun oklchToRgb(
    lightness: Float,
    chroma: Float,
    hue: Float,
): RgbColor {
    val radians = hue * PI.toFloat() / 180f
    return oklabToRgb(lightness, chroma * cos(radians), chroma * sin(radians))
}

// endregion
