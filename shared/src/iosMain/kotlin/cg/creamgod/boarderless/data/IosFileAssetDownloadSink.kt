@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.CoreCrypto.*
import platform.Foundation.*
import kotlin.coroutines.coroutineContext

class IosFileAssetDownloadSink private constructor(
    private val part: String,
    private val target: String,
    private val mediaType: String,
) : AssetDownloadSink {
    private var offset = 0L
    private var finished = false

    override suspend fun writeChunk(offset: Long, bytes: ByteArray) = withContext(Dispatchers.Default) {
        check(!finished)
        require(offset == this@IosFileAssetDownloadSink.offset && bytes.isNotEmpty())
        coroutineContext.ensureActive()
        val output = NSFileHandle.fileHandleForWritingAtPath(part) ?: error("Preview part is unavailable")
        try {
            output.seekToFileOffset(offset.toULong())
            autoreleasepool {
                bytes.usePinned { output.writeData(NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong())) }
            }
            this@IosFileAssetDownloadSink.offset += bytes.size
        } finally { output.closeFile() }
    }

    override suspend fun commit(expectedByteSize: Long, expectedChecksum: String): LocalAssetReference =
        withContext(Dispatchers.Default) {
            check(!finished && expectedByteSize > 0 && offset == expectedByteSize)
            val size = (NSFileManager.defaultManager.attributesOfItemAtPath(part, null)?.get(NSFileSize) as? NSNumber)?.longLongValue
            check(size == expectedByteSize) { "Downloaded asset size mismatch" }
            val input = NSFileHandle.fileHandleForReadingAtPath(part) ?: error("Preview part is unavailable")
            val checksum = try {
                memScoped {
                    val digest = alloc<CC_SHA256_CTX>()
                    CC_SHA256_Init(digest.ptr)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = autoreleasepool {
                            val data = input.readDataOfLength(DefaultAssetUploadChunkBytes.toULong())
                            CC_SHA256_Update(digest.ptr, data.bytes, data.length.toUInt())
                            data.length
                        }
                        if (count == 0UL) break
                    }
                    val hash = allocArray<UByteVar>(32)
                    CC_SHA256_Final(hash, digest.ptr)
                    "sha256:" + (0 until 32).joinToString("") { hash[it].toString(16).padStart(2, '0') }
                }
            } finally { input.closeFile() }
            check(checksum.equals(expectedChecksum, ignoreCase = true)) { "Downloaded asset checksum mismatch" }
            coroutineContext.ensureActive()
            val manager = NSFileManager.defaultManager
            check(!manager.fileExistsAtPath(target) && manager.moveItemAtPath(part, target, null))
            finished = true
            LocalAssetReference(target, mediaType)
        }

    override suspend fun abort() = withContext(Dispatchers.Default) {
        if (!finished) remove(part)
        finished = true
    }

    /** Consumer releases verified content after decoding; never deletes any source/user file. */
    suspend fun release() = withContext(Dispatchers.Default) {
        remove(part)
        remove(target)
        finished = true
    }

    private fun remove(path: String) {
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtPath(path, null))
    }

    companion object {
        suspend fun create(mediaType: String): IosFileAssetDownloadSink {
            require(mediaType.isNotBlank())
            var created: IosFileAssetDownloadSink? = null
            try {
                return withContext(Dispatchers.Default) {
                    val base = NSTemporaryDirectory() + "boarderless-preview-${NSUUID().UUIDString}"
                    check(NSFileManager.defaultManager.createFileAtPath("$base.part", null, null))
                    // AVFoundation uses the local extension to identify its container. The file
                    // remains private and is still published only after size/SHA-256 verification.
                    val suffix = when (mediaType) { "video/mp4" -> ".mp4"; "video/webm" -> ".webm"; else -> "" }
                    IosFileAssetDownloadSink("$base.part", "$base.ready$suffix", mediaType).also { created = it }
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) { created?.release() }
                throw error
            }
        }
    }
}
