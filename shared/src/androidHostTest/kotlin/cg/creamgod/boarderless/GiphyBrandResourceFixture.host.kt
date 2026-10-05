package cg.creamgod.boarderless

import java.util.zip.ZipFile

/** Inspect exactly the APP artifact supplied by Gradle, not a checkout/resource fallback. */
internal actual suspend fun readGiphyBrandResourceForTest(): ByteArray {
    val apk = checkNotNull(System.getProperty("boarderless.test.android.apk")) { "Built APP APK is required" }
    return ZipFile(apk).use { zip ->
        val path = "assets/composeResources/boarderless.shared.generated.resources/drawable/giphy_powered_by.png"
        val entry = checkNotNull(zip.getEntry(path)) { "Built APP APK is missing the GIPHY mark" }
        check(!entry.isDirectory && entry.size in 1..4096) { "Packaged mark exceeds fixture bounds" }
        zip.getInputStream(entry).use { input ->
            val bytes = input.readNBytes(4097)
            check(bytes.size.toLong() == entry.size) { "Packaged mark is truncated or oversized" }
            bytes
        }
    }
}
