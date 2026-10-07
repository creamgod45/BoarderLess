package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AndroidAssetPreviewLoader
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.test.*

/** BitmapFactory requires a device; host mocks are not evidence of pixel decoding. */
class AndroidGiphyStillDeviceTest {
    @Test fun ephemeralPngCanBeDecodedWithoutAssetStorage() = runBlocking {
        val bytes = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=")
        val bitmap = AndroidAssetPreviewLoader.decodeBytes(bytes)
        assertEquals(1, bitmap.width)
        assertEquals(1, bitmap.height)
    }
    @Test fun corruptProviderStillIsRejected() = runBlocking {
        assertFailsWith<IllegalArgumentException> { AndroidAssetPreviewLoader.decodeBytes(byteArrayOf(1, 2, 3)) }
        Unit
    }
}
