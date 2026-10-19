package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AndroidSchemeBundleSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class AndroidSchemeBundleSourceTest {
    @Test fun snapshotIsIndependentOfPickerSourceAndSupportsExactPositionalReads()=runBlocking {
        val cache=Files.createTempDirectory("bundle-snapshot-test-").toFile()
        val selected=Files.createTempFile("bundle-picker-test-", ".blscheme").toFile()
        try {
            val bytes=ByteArray(1024*1024+71) { (it%251).toByte() }; selected.writeBytes(bytes)
            val snapshot=AndroidSchemeBundleSource.snapshot(cache,{true}) { selected.inputStream() }
            selected.writeBytes(ByteArray(45))
            assertEquals(bytes.size.toLong(),snapshot.byteSize)
            assertContentEquals(bytes.copyOfRange(1024*1024-3,bytes.size),snapshot.readChunk(1024*1024-3L,74))
            assertContentEquals(byteArrayOf(),snapshot.readChunk(snapshot.byteSize,1))
            snapshot.release(); snapshot.release()
            assertFailsWith<IllegalStateException> { snapshot.readChunk(0,1) }
            assertEquals(0,cache.listFiles()!!.size); assertEquals(45L,selected.length())
        } finally { cache.deleteRecursively(); selected.delete() }
    }
    @Test fun changedScopeOrTruncatedFileLeavesNoPrivateSnapshot()=runBlocking {
        val cache=Files.createTempDirectory("bundle-snapshot-test-").toFile()
        try {
            var reads=0
            assertFailsWith<IllegalStateException> {
                AndroidSchemeBundleSource.snapshot(cache,{ ++reads < 3 }) { ByteArray(1024*1024+45).inputStream() }
            }
            assertEquals(0,cache.listFiles()!!.size)
            assertFailsWith<IllegalArgumentException> { AndroidSchemeBundleSource.snapshot(cache,{true}) { ByteArray(44).inputStream() } }
            assertEquals(0,cache.listFiles()!!.size)
        } finally { cache.deleteRecursively() }
    }
}
