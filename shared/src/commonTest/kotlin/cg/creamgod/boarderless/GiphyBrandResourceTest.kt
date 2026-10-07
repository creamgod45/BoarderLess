package cg.creamgod.boarderless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.test.*

class GiphyBrandResourceTest {
    @Test fun packagedMarkRetainsOriginalPngDimensionsAndHash() =
        runTest {
            withContext(Dispatchers.Default) {
                val bytes = readGiphyBrandResourceForTest()
                assertEquals(1627, bytes.size)
                assertContentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), bytes.copyOfRange(0, 8))

                fun uint32(offset: Int): Int = (0..3).fold(0) { result, i -> (result shl 8) or (bytes[offset + i].toInt() and 255) }
                assertEquals(200, uint32(16))
                assertEquals(42, uint32(20))
                val hash = SHA256().apply { update(bytes) }.digest().joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                assertEquals("839e790cd4c78b5aa3a416a6bfafc6ff3502e6d0447812ab7fcfed182a1ecb2e", hash)
            }
        }
}

// Android Host validates built APK bytes; Device/native/browser use their real resource reader.
internal expect suspend fun readGiphyBrandResourceForTest(): ByteArray
