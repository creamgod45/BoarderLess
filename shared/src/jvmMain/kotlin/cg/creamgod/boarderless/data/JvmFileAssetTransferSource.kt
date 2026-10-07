package cg.creamgod.boarderless.data

import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

class JvmFileAssetTransferSource private constructor(
    private val path: Path,
    private val lastModifiedMillis: Long,
    override val displayName: String,
    override val mediaType: String,
    override val byteSize: Long,
    override val checksum: String,
    override val width: Int?,
    override val height: Int?,
    override val durationMs: Long?,
) : AssetTransferSource {
    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(offset >= 0) { "Asset chunk offset must not be negative" }
        require(maximumBytes in 1..DefaultAssetUploadChunkBytes) {
            "Asset chunk size must be between 1 and $DefaultAssetUploadChunkBytes"
        }
        verifyUnchanged()
        if (offset >= byteSize) return@withContext byteArrayOf()
        val length = minOf(maximumBytes.toLong(), byteSize - offset).toInt()
        RandomAccessFile(path.toFile(), "r").use { file ->
            file.seek(offset)
            ByteArray(length).also { bytes -> file.readFully(bytes) }
        }
    }

    private fun verifyUnchanged() {
        if (!Files.isRegularFile(path) || Files.size(path) != byteSize ||
            Files.getLastModifiedTime(path).toMillis() != lastModifiedMillis
        ) {
            throw IllegalStateException("Selected asset changed after it was prepared")
        }
    }

    companion object {
        suspend fun fromPath(rawPath: String): JvmFileAssetTransferSource = withContext(Dispatchers.IO) {
            val path = Paths.get(rawPath).toAbsolutePath().normalize()
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw IllegalArgumentException("Selected asset is not a readable regular file")
            }
            val size = Files.size(path)
            val lastModifiedMillis = Files.getLastModifiedTime(path).toMillis()
            when {
                size <= 0 -> throw AssetImportException(
                    AssetImportIssue.InvalidByteSize,
                    "Selected asset is empty",
                )
                size > MaxWorkspaceAssetBytes -> throw AssetImportException(
                    AssetImportIssue.AssetTooLarge,
                    "Selected asset exceeds the $MaxWorkspaceAssetBytes byte limit",
                )
            }
            val mediaType = detectMediaType(path)
            val dimensions = if (mediaType.startsWith("image/")) {
                imageDimensions(path)
            } else {
                null
            }
            JvmFileAssetTransferSource(
                path = path,
                lastModifiedMillis = lastModifiedMillis,
                displayName = path.fileName?.toString().orEmpty(),
                mediaType = mediaType,
                byteSize = size,
                checksum = sha256(path),
                width = dimensions?.first,
                height = dimensions?.second,
                durationMs = null,
            ).also {
                it.verifyUnchanged()
                validateAssetTransferSource(it)
            }
        }

        private fun imageDimensions(path: Path): Pair<Int, Int>? =
            ImageIO.createImageInputStream(path.toFile())?.use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) return@use null
                val reader = readers.next()
                try {
                    // Read headers only; decoding all pixels is unnecessary for upload metadata.
                    reader.setInput(input, true, true)
                    reader.getWidth(0) to reader.getHeight(0)
                } finally {
                    reader.dispose()
                }
            }

        private fun detectMediaType(path: Path): String {
            val detected = runCatching { Files.probeContentType(path) }.getOrNull()?.lowercase()
            if (detected in setOf("image/png", "image/jpeg", "image/webp", "image/gif", "video/mp4", "video/webm")) {
                return checkNotNull(detected)
            }
            return when (path.fileName.toString().substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                else -> detected ?: "application/octet-stream"
            }
        }

        private suspend fun sha256(path: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(path).use { input ->
                val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            return "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
