package cg.creamgod.boarderless.domain.export

/** Portable PNG/PDF writers. Stored DEFLATE blocks avoid platform codecs and preserve every pixel. */
internal fun encodeCanvasPng(raster: CanvasExportRaster): ByteArray {
    val rowBytes = raster.width.toLong() * 4 + 1
    val zlib = storedZlib(rowBytes * raster.height) { offset ->
        val column = (offset % rowBytes).toInt()
        if (column == 0) 0 else {
            val pixel = raster.pixel((offset / rowBytes * raster.width + (column - 1) / 4).toInt())
            val shift = when ((column - 1) % 4) { 0 -> 16; 1 -> 8; 2 -> 0; else -> 24 }
            (pixel ushr shift) and 255
        }
    }
    val out = ExportBytes(exportCapacity(8L + 25 + zlib.size + 12 + 12))
    out.bytes(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
    val header = ExportBytes(13)
    header.int32(raster.width); header.int32(raster.height)
    header.bytes(byteArrayOf(8, 6, 0, 0, 0)) // RGBA, 8 bits, no interlace.
    out.pngChunk("IHDR", header.result()); out.pngChunk("IDAT", zlib); out.pngChunk("IEND", byteArrayOf())
    return out.result()
}

internal fun encodeCanvasPdf(raster: CanvasExportRaster): ByteArray {
    val rgb = storedZlib(raster.pixelCount.toLong() * 3) { i -> (raster.pixel(i / 3) ushr (16 - i % 3 * 8)) and 255 }
    val hasAlpha = (0 until raster.pixelCount).any { raster.pixel(it) ushr 24 != 255 }
    val alpha = if (hasAlpha) storedZlib(raster.pixelCount.toLong()) { raster.pixel(it) ushr 24 } else null
    val out = ExportBytes(exportCapacity(rgb.size.toLong() + (alpha?.size ?: 0) + 4096))
    out.ascii("%PDF-1.4\n%"); out.bytes(byteArrayOf(226.toByte(), 227.toByte(), 207.toByte(), 211.toByte())); out.ascii("\n")
    val count = if (alpha == null) 5 else 6
    val offsets = IntArray(count + 1)
    fun obj(id: Int, body: String) {
        offsets[id] = out.size; out.ascii("$id 0 obj\n$body\nendobj\n")
    }
    fun stream(id: Int, dictionary: String, bytes: ByteArray) {
        offsets[id] = out.size
        out.ascii("$id 0 obj\n<< $dictionary /Length ${bytes.size} >>\nstream\n")
        out.bytes(bytes); out.ascii("\nendstream\nendobj\n")
    }
    obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
    obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
    // One canvas pixel = one PDF point. Raster row order and the image matrix preserve orientation.
    obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${raster.width} ${raster.height}] /Resources << /XObject << /Canvas 5 0 R >> >> /Contents 4 0 R >>")
    stream(4, "", "q\n${raster.width} 0 0 ${raster.height} 0 0 cm\n/Canvas Do\nQ\n".encodeToByteArray())
    stream(5, "/Type /XObject /Subtype /Image /Width ${raster.width} /Height ${raster.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /FlateDecode" + if (alpha != null) " /SMask 6 0 R" else "", rgb)
    if (alpha != null) stream(6, "/Type /XObject /Subtype /Image /Width ${raster.width} /Height ${raster.height} /ColorSpace /DeviceGray /BitsPerComponent 8 /Filter /FlateDecode", alpha)
    val xref = out.size
    out.ascii("xref\n0 ${count + 1}\n0000000000 65535 f \n")
    for (id in 1..count) out.ascii(offsets[id].toString().padStart(10, '0') + " 00000 n \n")
    out.ascii("trailer\n<< /Size ${count + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    return out.result()
}

/** RFC 1950 wrapper around RFC 1951 stored blocks; no dictionary. */
private fun storedZlib(length: Long, value: (Int) -> Int): ByteArray {
    require(length > 0)
    val out = ExportBytes(exportCapacity(2 + length + (length + 65534) / 65535 * 5 + 4))
    out.byte(0x78); out.byte(0x01)
    var offset = 0; var a = 1; var b = 0
    while (offset < length) {
        val count = minOf(65535L, length - offset).toInt()
        out.byte(if (offset.toLong() + count == length) 1 else 0)
        out.byte(count); out.byte(count ushr 8)
        out.byte(count xor 0xffff); out.byte((count xor 0xffff) ushr 8)
        repeat(count) {
            val byte = value(offset++) and 255
            out.byte(byte); a = (a + byte) % 65521; b = (b + a) % 65521
        }
    }
    out.int32((b shl 16) or a)
    return out.result()
}

private fun exportCapacity(bytes: Long): Int {
    require(bytes in 1..Int.MAX_VALUE.toLong()) { "Encoded export exceeds ByteArray capacity" }
    return bytes.toInt()
}

private class ExportBytes(capacity: Int) {
    private val buffer = ByteArray(capacity)
    var size = 0; private set
    fun byte(value: Int) { check(size < buffer.size); buffer[size++] = value.toByte() }
    fun bytes(values: ByteArray) {
        check(values.size <= buffer.size - size)
        values.copyInto(buffer, size); size += values.size
    }
    fun ascii(value: String) {
        require(value.all { it.code < 128 }); bytes(value.encodeToByteArray())
    }
    fun int32(value: Int) { byte(value ushr 24); byte(value ushr 16); byte(value ushr 8); byte(value) }
    fun pngChunk(type: String, payload: ByteArray) {
        int32(payload.size)
        val start = size; ascii(type); bytes(payload)
        var crc = -1
        for (i in start until size) {
            crc = crc xor (buffer[i].toInt() and 255)
            repeat(8) { crc = (crc ushr 1) xor (if (crc and 1 != 0) 0xedb88320.toInt() else 0) }
        }
        int32(crc.inv())
    }
    fun result(): ByteArray = buffer.copyOf(size)
}
