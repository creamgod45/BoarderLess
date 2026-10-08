package cg.creamgod.boarderless

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path

/** Read JPEG metadata only: never allocate the encoded original or decoded source pixels. */
internal fun desktopJpegOrientation(file: Path): Int = Files.newInputStream(file).buffered().use { input ->
    if (input.read() != 0xff || input.read() != 0xd8) return@use 1
    while (true) {
        check(input.read() == 0xff) { "Invalid JPEG marker" }
        var marker = input.read()
        while (marker == 0xff) {
            marker = input.read()
        }
        if (marker == 0xda || marker == 0xd9) return@use 1
        check(marker >= 0 && marker != 0) { "Invalid JPEG marker" }
        if (marker == 0x01 || marker in 0xd0..0xd7) continue
        val high = input.read(); val low = input.read()
        check(high >= 0 && low >= 0)
        val size = (high shl 8) + low - 2
        check(size >= 0) { "Invalid JPEG segment length" }
        val bytes = input.readNBytes(size); check(bytes.size == size)
        if (marker != 0xe1 || bytes.size < 14 ||
            !bytes.copyOfRange(0, 6).contentEquals(byteArrayOf(69, 120, 105, 102, 0, 0))) continue
        val little = bytes[6] == 73.toByte() && bytes[7] == 73.toByte()
        if (!little && !(bytes[6] == 77.toByte() && bytes[7] == 77.toByte())) continue
        fun number(offset: Int, count: Int): Long? {
            if (offset < 6 || offset.toLong() + count > bytes.size) return null
            var result = 0L
            for (i in 0 until count) result = result or ((bytes[offset + i].toLong() and 255) shl (8 * if (little) i else count - 1 - i))
            return result
        }
        if (number(8, 2) != 42L) continue
        val first = number(10, 4) ?: continue
        if (first > bytes.size - 8L) continue
        val start = 6 + first.toInt()
        val count = number(start, 2)?.toInt() ?: continue
        if (start.toLong() + 2 + count * 12L > bytes.size) continue
        for (i in 0 until count) {
            val entry = start + 2 + i * 12
            if (number(entry, 2) == 0x112L && number(entry + 2, 2) == 3L && number(entry + 4, 4) == 1L) {
                val value = number(entry + 8, 2)?.toInt() ?: continue
                if (value in 1..8) return@use value
            }
        }
    }
    @Suppress("UNREACHABLE_CODE")
    1
}

/** Map an upright tile back to the encoded source grid before region subsampling. */
internal fun encodedImageRegion(rect: Rectangle, width: Int, height: Int, orientation: Int): Rectangle = when (orientation) {
    2 -> Rectangle(width - rect.x - rect.width, rect.y, rect.width, rect.height)
    3 -> Rectangle(width - rect.x - rect.width, height - rect.y - rect.height, rect.width, rect.height)
    4 -> Rectangle(rect.x, height - rect.y - rect.height, rect.width, rect.height)
    5 -> Rectangle(rect.y, rect.x, rect.height, rect.width)
    6 -> Rectangle(rect.y, height - rect.x - rect.width, rect.height, rect.width)
    7 -> Rectangle(width - rect.y - rect.height, height - rect.x - rect.width, rect.height, rect.width)
    8 -> Rectangle(width - rect.y - rect.height, rect.x, rect.height, rect.width)
    else -> Rectangle(rect)
}

/** Only a bounded decoded tile is transformed; source alpha is copied without compositing. */
internal fun uprightImageTile(source: BufferedImage, orientation: Int): BufferedImage {
    if (orientation == 1) return source
    val w = source.width; val h = source.height
    val result = BufferedImage(if (orientation >= 5) h else w, if (orientation >= 5) w else h, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until h) for (x in 0 until w) {
        val point = when (orientation) {
            2 -> w - 1 - x to y
            3 -> w - 1 - x to h - 1 - y
            4 -> x to h - 1 - y
            5 -> y to x
            6 -> h - 1 - y to x
            7 -> h - 1 - y to w - 1 - x
            8 -> y to w - 1 - x
            else -> error("Invalid image orientation")
        }
        result.setRGB(point.first, point.second, source.getRGB(x, y))
    }
    return result
}
