package cg.creamgod.boarderless.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class JvmFileAssetDownloadSink private constructor(
    private val temporaryPath: Path,
    private val targetPath: Path,
    private val mediaType: String,
) : AssetDownloadSink {
    private var nextOffset = 0L
    private var finished = false

    override suspend fun writeChunk(
        offset: Long,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        check(!finished) { "Asset download sink is already closed" }
        require(bytes.isNotEmpty()) { "Asset download chunk must not be empty" }
        require(offset == nextOffset) { "Asset chunks must be written sequentially" }
        RandomAccessFile(temporaryPath.toFile(), "rw").use { file ->
            file.seek(offset)
            file.write(bytes)
        }
        nextOffset += bytes.size
    }

    override suspend fun commit(
        expectedByteSize: Long,
        expectedChecksum: String,
    ): LocalAssetReference =
        withContext(Dispatchers.IO) {
            check(!finished) { "Asset download sink is already closed" }
            if (nextOffset != expectedByteSize || Files.size(temporaryPath) != expectedByteSize) {
                throw IllegalStateException("Downloaded asset size does not match server metadata")
            }
            val actualChecksum = sha256(temporaryPath)
            if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw IllegalStateException("Downloaded asset checksum does not match server metadata")
            }
            try {
                Files.move(
                    temporaryPath,
                    targetPath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            }
            finished = true
            LocalAssetReference(targetPath.toString(), mediaType)
        }

    override suspend fun abort() =
        withContext(Dispatchers.IO) {
            if (!finished) Files.deleteIfExists(temporaryPath)
            finished = true
        }

    companion object {
        suspend fun create(
            cacheDirectory: String,
            assetId: String,
            mediaType: String,
        ): JvmFileAssetDownloadSink =
            withContext(Dispatchers.IO) {
                require(assetId.isNotBlank()) { "Asset id must not be blank" }
                require(mediaType.isNotBlank()) { "Asset media type must not be blank" }
                val directory = Paths.get(cacheDirectory).toAbsolutePath().normalize()
                Files.createDirectories(directory)
                val safeId = assetId.replace(Regex("[^A-Za-z0-9._-]"), "_").take(160)
                val extension =
                    when (mediaType.lowercase()) {
                        "image/png" -> "png"
                        "image/jpeg" -> "jpg"
                        "image/webp" -> "webp"
                        "image/gif" -> "gif"
                        "video/mp4" -> "mp4"
                        "video/webm" -> "webm"
                        else -> "bin"
                    }
                val temporary = Files.createTempFile(directory, ".$safeId-", ".part")
                JvmFileAssetDownloadSink(
                    temporaryPath = temporary,
                    targetPath = directory.resolve("$safeId.$extension"),
                    mediaType = mediaType.lowercase(),
                )
            }

        private fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            return "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
