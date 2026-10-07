package cg.creamgod.boarderless.data

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** java.io keeps downloads compatible with API 24 (java.nio.file needs API 26). */
class AndroidFileAssetDownloadSink private constructor(
    private val part: File,
    private val target: File,
    private val mediaType: String,
) : AssetDownloadSink {
    private var nextOffset = 0L
    private var finished = false

    override suspend fun writeChunk(offset: Long, bytes: ByteArray) = withContext(Dispatchers.IO) {
        check(!finished) { "Asset download sink is closed" }
        require(offset == nextOffset && bytes.isNotEmpty()) { "Asset chunks must be nonempty and sequential" }
        currentCoroutineContext().ensureActive()
        RandomAccessFile(part, "rw").use { file ->
            file.seek(offset)
            file.write(bytes)
        }
        nextOffset += bytes.size
    }

    override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference =
        withContext(Dispatchers.IO) {
            check(!finished) { "Asset download sink is closed" }
            check(expectedByteSize > 0 && nextOffset == expectedByteSize && part.length() == expectedByteSize) {
                "Downloaded asset size does not match server metadata"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            part.inputStream().use { input ->
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(expectedChecksum, ignoreCase = true)) { "Downloaded asset checksum does not match server metadata" }
            currentCoroutineContext().ensureActive()
            check(!target.exists() && part.renameTo(target)) { "Cannot publish verified asset file" }
            finished = true
            LocalAssetReference(target.absolutePath, mediaType)
        }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        if (!finished) check(!part.exists() || part.delete()) { "Cannot clear partial asset download" }
        finished = true
    }

    companion object {
        suspend fun create(directory: File, mediaType: String): AndroidFileAssetDownloadSink = withContext(Dispatchers.IO) {
            require(directory.isDirectory && mediaType.isNotBlank())
            val part = File.createTempFile("asset-", ".part", directory)
            AndroidFileAssetDownloadSink(part, File(directory, "${UUID.randomUUID()}.ready"), mediaType)
        }
    }
}
