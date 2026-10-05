package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.readDraftJsonChunks
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DraftJsonStreamTest {
    @Test fun utf8CharactersSplitAcrossChunksAndBomRemainIntact() = runTest {
        val bytes = "\uFEFF{\"text\":\"中文🙂\"}".encodeToByteArray()
        var offset = 0
        assertEquals("{\"text\":\"中文🙂\"}", readDraftJsonChunks({ true }) { maximum ->
            assertEquals(8192, maximum)
            if (offset == bytes.size) byteArrayOf() else byteArrayOf(bytes[offset++])
        })
    }

    @Test fun emptyInvalidUtf8AndOversizeProviderResponsesAreRejected() = runTest {
        assertFailsWith<IllegalArgumentException> { readDraftJsonChunks({ true }) { byteArrayOf() } }
        var reads = 0
        assertFails { readDraftJsonChunks({ true }) { if (reads++ == 0) byteArrayOf(0xC3.toByte(), 0x28) else byteArrayOf() } }
        assertFailsWith<IllegalArgumentException> { readDraftJsonChunks({ true }) { ByteArray(8193) } }
        reads = 0
        assertFailsWith<IllegalArgumentException> { readDraftJsonChunks({ true }) { reads++; ByteArray(8192) } }
        assertEquals(513, reads)
    }

    @Test fun scopeExpiryBeforeAndAfterReadStopsWithoutPublishingBytes() = runTest {
        var reads = 0
        assertFailsWith<IllegalStateException> { readDraftJsonChunks({ false }) { reads++; byteArrayOf() } }
        assertEquals(0, reads)
        var active = true
        assertFailsWith<IllegalStateException> {
            readDraftJsonChunks({ active }) { reads++; active = false; "private".encodeToByteArray() }
        }
        assertEquals(1, reads)
    }
}
