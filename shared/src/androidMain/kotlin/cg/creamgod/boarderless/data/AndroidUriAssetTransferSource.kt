package cg.creamgod.boarderless.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** A private immutable snapshot, including for non-seekable cloud document providers. */
class AndroidUriAssetTransferSource private constructor(
    private val file: File,
    override val displayName: String,
    override val mediaType: String,
    override val byteSize: Long,
    override val checksum: String,
    override val width: Int?,
    override val height: Int?,
    override val durationMs: Long?,
) : AssetTransferSource {
    override suspend fun readChunk(
        offset: Long,
        maximumBytes: Int,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            require(offset >= 0)
            require(maximumBytes in 1..DefaultAssetUploadChunkBytes)
            check(file.isFile && file.length() == byteSize) { "Selected asset snapshot is no longer available" }
            if (offset >= byteSize) return@withContext byteArrayOf()
            val length = minOf(maximumBytes.toLong(), byteSize - offset).toInt()
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                ByteArray(length).also(input::readFully)
            }
        }

    override suspend fun release() =
        withContext(Dispatchers.IO) {
            check(!file.exists() || file.delete()) { "Unable to release selected asset snapshot" }
        }

    companion object {
        suspend fun fromUri(
            context: Context,
            uri: Uri,
        ): AndroidUriAssetTransferSource {
            var snapshot: File? = null
            try {
                return withContext(Dispatchers.IO) {
                    val resolver = context.contentResolver
                    var name: String? = null
                    var declaredSize: Long? = null
                    resolver
                        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn)
                                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) declaredSize = cursor.getLong(sizeColumn)
                            }
                        }
                    if (declaredSize != null && checkNotNull(declaredSize) > MaxWorkspaceAssetBytes) {
                        throw AssetImportException(AssetImportIssue.AssetTooLarge, "Selected media exceeds the size limit")
                    }
                    val mediaType = resolver.getType(uri)?.lowercase() ?: mimeFromName(name.orEmpty())
                    val kind =
                        when (mediaType) {
                            "image/png", "image/jpeg", "image/webp", "image/gif" -> "image"
                            "video/mp4", "video/webm" -> "video"
                            else -> throw AssetImportException(AssetImportIssue.UnsupportedMediaType, "Unsupported selected media")
                        }
                    val local = File.createTempFile("boarderless-import-", ".part", context.cacheDir)
                    snapshot = local
                    val digest = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    val input = resolver.openInputStream(uri) ?: error("Selected document cannot be opened")
                    input.use { source ->
                        local.outputStream().use { destination ->
                            val buffer = ByteArray(DefaultAssetUploadChunkBytes)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = source.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                size += count
                                if (size > MaxWorkspaceAssetBytes) {
                                    throw AssetImportException(AssetImportIssue.AssetTooLarge, "Selected media exceeds the size limit")
                                }
                                digest.update(buffer, 0, count)
                                destination.write(buffer, 0, count)
                            }
                        }
                    }
                    if (declaredSize != null && checkNotNull(declaredSize) >= 0 && declaredSize != size) {
                        throw AssetImportException(AssetImportIssue.TruncatedSource, "Document content changed while being selected")
                    }
                    var width: Int? = null
                    var height: Int? = null
                    var duration: Long? = null
                    if (kind == "image") {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(local.absolutePath, bounds)
                        width = bounds.outWidth.takeIf { it > 0 }
                        height = bounds.outHeight.takeIf { it > 0 }
                    } else {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(local.absolutePath)
                            width =
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()?.takeIf { it > 0 }
                            height =
                                retriever
                                    .extractMetadata(
                                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT,
                                    )?.toIntOrNull()
                                    ?.takeIf { it > 0 }
                            duration =
                                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it >= 0 }
                        } catch (_: RuntimeException) {
                            // Optional metadata may be unsupported by a device codec.
                        } finally {
                            retriever.release()
                        }
                    }
                    AndroidUriAssetTransferSource(
                        file = local,
                        displayName = name?.takeIf { it.isNotBlank() } ?: "media",
                        mediaType = mediaType,
                        byteSize = size,
                        checksum = "sha256:" + digest.digest().joinToString("") { "%02x".format(it) },
                        width = width,
                        height = height,
                        durationMs = duration,
                    ).also { validateAssetTransferSource(it) }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.IO) { snapshot?.delete() }
                throw error
            }
        }

        private fun mimeFromName(name: String): String =
            when (name.substringAfterLast('.', "").lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                else -> "application/octet-stream"
            }
    }
}
