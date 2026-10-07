@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.CoreCrypto.*
import platform.Foundation.*
import platform.posix.memcpy
import kotlin.coroutines.coroutineContext

class IosFileAssetTransferSource private constructor(
    private val path: String,
    override val displayName: String,
    override val mediaType: String,
    override val byteSize: Long,
    override val checksum: String,
) : AssetTransferSource {
    override val width: Int? = null
    override val height: Int? = null
    override val durationMs: Long? = null

    override suspend fun readChunk(offset: Long, maximumBytes: Int): ByteArray = withContext(Dispatchers.Default) {
        require(offset >= 0)
        require(maximumBytes in 1..DefaultAssetUploadChunkBytes)
        if (offset >= byteSize) return@withContext byteArrayOf()
        val input = NSFileHandle.fileHandleForReadingAtPath(path) ?: error("Selected media snapshot is unavailable")
        try {
            input.seekToFileOffset(offset.toULong())
            val data = input.readDataOfLength(minOf(maximumBytes.toLong(), byteSize - offset).toULong())
            ByteArray(data.length.toInt()).also { bytes ->
                if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
            }
        } finally {
            input.closeFile()
        }
    }

    override suspend fun release() = withContext(Dispatchers.Default) {
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null)) { "Unable to release media snapshot" }
    }

    companion object {
        suspend fun fromUrl(url: NSURL): IosFileAssetTransferSource {
            var snapshotPath: String? = null
            try {
                return withContext(Dispatchers.Default) {
                    val securityScoped = url.startAccessingSecurityScopedResource()
                    try {
                        val name = url.lastPathComponent ?: "media"
                        val mime = when (name.substringAfterLast('.', "").lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "webp" -> "image/webp"
                            "gif" -> "image/gif"
                            "mp4", "m4v" -> "video/mp4"
                            "webm" -> "video/webm"
                            else -> throw AssetImportException(AssetImportIssue.UnsupportedMediaType, "Unsupported selected media")
                        }
                        val original = url.path ?: error("Selected media has no local file")
                        val declaredSize = (NSFileManager.defaultManager.attributesOfItemAtPath(original, null)
                            ?.get(NSFileSize) as? NSNumber)?.longLongValue
                        if (declaredSize != null && declaredSize > MaxWorkspaceAssetBytes) {
                            throw AssetImportException(AssetImportIssue.AssetTooLarge, "Selected media exceeds the size limit")
                        }
                        val input = NSFileHandle.fileHandleForReadingAtPath(original) ?: error("Selected media cannot be opened")
                        try {
                            val path = NSTemporaryDirectory() + "boarderless-import-${NSUUID().UUIDString}.part"
                            snapshotPath = path
                            check(NSFileManager.defaultManager.createFileAtPath(path, null, null))
                            val output = NSFileHandle.fileHandleForWritingAtPath(path) ?: error("Cannot create media snapshot")
                            try {
                                memScoped {
                                    val digest = alloc<CC_SHA256_CTX>()
                                    CC_SHA256_Init(digest.ptr)
                                    var size = 0L
                                    while (true) {
                                        coroutineContext.ensureActive()
                                        val count = autoreleasepool {
                                            val data = input.readDataOfLength(DefaultAssetUploadChunkBytes.toULong())
                                            size += data.length.toLong()
                                            if (size > MaxWorkspaceAssetBytes) {
                                                throw AssetImportException(AssetImportIssue.AssetTooLarge, "Selected media exceeds the size limit")
                                            }
                                            CC_SHA256_Update(digest.ptr, data.bytes, data.length.toUInt())
                                            output.writeData(data)
                                            data.length.toLong()
                                        }
                                        if (count == 0L) break
                                    }
                                    val hash = allocArray<UByteVar>(32)
                                    CC_SHA256_Final(hash, digest.ptr)
                                    if (declaredSize != null && declaredSize != size) {
                                        throw AssetImportException(AssetImportIssue.TruncatedSource, "Selected media changed while being copied")
                                    }
                                    val checksum = "sha256:" + (0 until 32).joinToString("") { hash[it].toString(16).padStart(2, '0') }
                                    IosFileAssetTransferSource(path, name, mime, size, checksum)
                                        .also { validateAssetTransferSource(it) }
                                }
                            } finally {
                                output.closeFile()
                            }
                        } finally {
                            input.closeFile()
                        }
                    } finally {
                        if (securityScoped) url.stopAccessingSecurityScopedResource()
                    }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable + Dispatchers.Default) {
                    snapshotPath?.let { NSFileManager.defaultManager.removeItemAtPath(it, null) }
                }
                throw error
            }
        }
    }
}
