package cg.creamgod.boarderless

import cg.creamgod.boarderless.designsystem.color.ColorChannel
import cg.creamgod.boarderless.designsystem.color.ColorModel
import cg.creamgod.boarderless.designsystem.color.RgbColor
import cg.creamgod.boarderless.designsystem.color.rgbToHsb
import cg.creamgod.boarderless.designsystem.color.rgbToHsl
import cg.creamgod.boarderless.designsystem.color.rgbToOklab
import cg.creamgod.boarderless.designsystem.color.rgbToOklch
import cg.creamgod.boarderless.i18n.Strings
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorSpacesTest {
    private fun assertClose(expected: Float, actual: Float, tolerance: Float = 0.002f) =
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun parsesAndFormatsHex() {
        assertEquals("#6255D9", RgbColor.parseHex("#6255d9")?.toHex())
        assertEquals("#6255D9", RgbColor.parseHex("  6255D9 ")?.toHex())
        assertEquals("#AABBCC", RgbColor.parseHex("#abc")?.toHex())
        assertNull(RgbColor.parseHex("#12345"))
        assertNull(RgbColor.parseHex("#GG0000"))
        assertEquals("#000000", RgbColor(-0.2f, 0f, 0f).toHex())
    }

    @Test
    fun convertsToHsbAndHsl() {
        val red = RgbColor(1f, 0f, 0f)
        assertEquals(listOf(0f, 1f, 1f), rgbToHsb(red).toList())
        assertEquals(listOf(0f, 1f, 0.5f), rgbToHsl(red).toList())
        val teal = RgbColor.parseHex("#008080")!!
        assertClose(180f, rgbToHsb(teal)[0])
        assertClose(0.502f, rgbToHsb(teal)[2])
        assertClose(0.251f, rgbToHsl(teal)[2])
    }

    @Test
    fun matchesPublishedOklabAndOklchValues() {
        val (l, a, b) = rgbToOklab(RgbColor(1f, 0f, 0f))
        assertClose(0.62796f, l)
        assertClose(0.22486f, a)
        assertClose(0.12585f, b)
        val white = rgbToOklab(RgbColor(1f, 1f, 1f))
        assertClose(1f, white[0])
        assertClose(0f, white[1])
        val (_, chroma, hue) = rgbToOklch(RgbColor(1f, 0f, 0f))
        assertClose(0.25768f, chroma)
        assertClose(29.23f, hue, tolerance = 0.05f)
    }

    @Test
    fun everyModelRoundTripsSrgbColors() {
        val samples = listOf("#6255D9", "#FFF1CF", "#1A1A1A", "#E1F5E9", "#FF8800", "#00FFFF", "#FFFFFF", "#000000")
        ColorModel.entries.forEach { model ->
            samples.forEach { hex ->
                val color = RgbColor.parseHex(hex)!!
                assertEquals(hex, model.toRgb(model.fromRgb(color)).toHex(), "${model.label} round trip of $hex")
            }
        }
    }

    @Test
    fun oklchOutsideSrgbIsReportedAndClampable() {
        val vivid = ColorModel.Oklch.toRgb(listOf(70f, 0.37f, 140f))
        assertFalse(vivid.inGamut)
        assertTrue(vivid.clamped().inGamut)
        assertTrue(ColorModel.Oklch.toRgb(listOf(70f, 0.05f, 140f)).inGamut)
    }

    @Test
    fun channelsFormatAndClampTheirValues() {
        val chroma = ColorChannel("C", Strings.colorPicker.chroma, 0f, 0.37f, decimals = 3)
        assertEquals("0.120", chroma.format(0.12f))
        assertEquals(0.37f, chroma.coerce(2f))
        assertEquals("255", ColorModel.Rgb.channels[0].format(254.6f))
    }
}
