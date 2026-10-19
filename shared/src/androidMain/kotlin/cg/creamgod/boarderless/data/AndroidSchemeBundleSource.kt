package cg.creamgod.boarderless.data

import cg.creamgod.boarderless.feature.canvas.QuickSchemeBundleCodec
import cg.creamgod.boarderless.feature.canvas.SchemeBundleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

/** Own snapshot; a content provider handle is never retained across review or upload. */
internal class AndroidSchemeBundleSource private constructor(private val snapshot: File, override val byteSize: Long) : SchemeBundleSource {
    private var released = false
    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        check(!released && snapshot.length() == byteSize)
        require(offset in 0..byteSize && maximumBytes in 1..QuickSchemeBundleCodec.ChunkBytes)
        RandomAccessFile(snapshot,"r").use { input ->
            input.seek(offset)
            ByteArray(minOf(maximumBytes.toLong(),byteSize-offset).toInt()).also(input::readFully)
        }
    }
    override suspend fun release() = withContext(Dispatchers.IO) {
        if (!released) { check(!snapshot.exists() || snapshot.delete()); released=true }
        Unit
    }
    companion object {
        suspend fun snapshot(cache: File, canRead: () -> Boolean, openInput: () -> InputStream): AndroidSchemeBundleSource = withContext(Dispatchers.IO) {
            currentCoroutineContext().ensureActive(); check(canRead())
            val file = File.createTempFile("scheme-bundle-", ".snapshot", cache)
            try {
                var size=0L
                openInput().use { input -> FileOutputStream(file).use { output ->
                    val buffer=ByteArray(QuickSchemeBundleCodec.ChunkBytes)
                    while (true) {
                        currentCoroutineContext().ensureActive(); check(canRead())
                        val count=input.read(buffer)
                        if (count < 0) break
                        check(count > 0)
                        require(count.toLong() <= QuickSchemeBundleCodec.MaximumBundleBytes-size)
                        output.write(buffer,0,count); size+=count
                    }
                    output.flush(); output.fd.sync()
                } }
                currentCoroutineContext().ensureActive(); check(canRead())
                require(size in 45..QuickSchemeBundleCodec.MaximumBundleBytes)
                AndroidSchemeBundleSource(file,size)
            } catch (error: Throwable) {
                if (file.exists() && !file.delete()) error.addSuppressed(IllegalStateException("Unable to remove private snapshot"))
                throw error
            }
        }
    }
}
