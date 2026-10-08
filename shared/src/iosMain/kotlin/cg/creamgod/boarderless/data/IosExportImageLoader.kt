@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.CoreFoundation.*
import platform.CoreGraphics.*
import platform.ImageIO.*
import platform.posix.memcpy

internal suspend fun loadIosExportImage(gateway: AssetDownloadGateway, session: WorkspaceSession,
                                       assetId: String, width: Int, height: Int): ImageBitmap {
    imageExportSize(1, 1, width, height)
    val ticket = gateway.authorize(session, assetId)
    currentCoroutineContext().ensureActive()
    require(ticket.asset.id == assetId && ticket.asset.workspaceId == session.workspace.id && ticket.asset.status == AssetStatus.Ready)
    require(ticket.asset.mediaType in setOf("image/png", "image/jpeg", "image/webp") && ticket.asset.byteSize in 1..MaxWorkspaceAssetBytes)
    var sink: IosFileAssetDownloadSink? = null
    try {
        return withContext(Dispatchers.Default) {
            val destination = IosFileAssetDownloadSink.create(ticket.asset.mediaType).also { sink = it }
            val authorized = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = ticket
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) = gateway.download(ticket, onChunk)
            }
            val local = AssetDownloadCoordinator(authorized).download(session, assetId, destination)
            currentCoroutineContext().ensureActive()
            decodeIosExportImage(local.token, width, height).also { currentCoroutineContext().ensureActive() }
        }
    } finally { withContext(NonCancellable) { sink?.release() } }
}

private fun exportImageOptions(pairs: List<Pair<CFStringRef?, COpaquePointer?>>): CFDictionaryRef = memScoped {
    val keys = allocArray<COpaquePointerVar>(pairs.size)
    val values = allocArray<COpaquePointerVar>(pairs.size)
    pairs.forEachIndexed { index, pair -> keys[index] = checkNotNull(pair.first); values[index] = checkNotNull(pair.second) }
    checkNotNull(CFDictionaryCreate(null, keys, values, pairs.size.toLong(), null, null))
}

private fun imageProperty(properties: CFDictionaryRef, key: CFStringRef?): Int = memScoped {
    val number = CFDictionaryGetValue(properties, key) ?: return@memScoped 0
    val value = alloc<IntVar>()
    check(CFNumberGetValue(number.reinterpret(), kCFNumberIntType, value.ptr))
    value.value
}

/** ImageIO reads the verified file and produces an orientation-correct full-image sample.
 * Only that bounded sample is bridged losslessly into Skia; never read the original into RAM. */
internal fun decodeIosExportImage(path: String, width: Int, height: Int): ImageBitmap {
    imageExportSize(1, 1, width, height)
    val string = checkNotNull(CFStringCreateWithCString(null, path, kCFStringEncodingUTF8))
    val url = try { checkNotNull(CFURLCreateWithFileSystemPath(null, string, kCFURLPOSIXPathStyle, false)) }
        finally { CFRelease(string) }
    val sourceOptions = exportImageOptions(listOf(kCGImageSourceShouldCache to kCFBooleanFalse))
    val source = try { checkNotNull(CGImageSourceCreateWithURL(url, sourceOptions)) }
        finally { CFRelease(sourceOptions); CFRelease(url) }
    try {
        val properties = checkNotNull(CGImageSourceCopyPropertiesAtIndex(source, 0UL, null))
        val size = try {
            val sw = imageProperty(properties, kCGImagePropertyPixelWidth)
            val sh = imageProperty(properties, kCGImagePropertyPixelHeight)
            val rotated = imageProperty(properties, kCGImagePropertyOrientation) in 5..8
            imageExportSize(if (rotated) sh else sw, if (rotated) sw else sh, width, height)
        } finally { CFRelease(properties) }
        return memScoped {
            val edge = alloc<IntVar>(); edge.value = maxOf(size.width, size.height)
            val limit = checkNotNull(CFNumberCreate(null, kCFNumberIntType, edge.ptr))
            val options = try { exportImageOptions(listOf(
                kCGImageSourceCreateThumbnailFromImageAlways to kCFBooleanTrue,
                kCGImageSourceCreateThumbnailWithTransform to kCFBooleanTrue,
                kCGImageSourceThumbnailMaxPixelSize to limit,
                kCGImageSourceShouldCache to kCFBooleanFalse,
                kCGImageSourceShouldCacheImmediately to kCFBooleanTrue,
            )) } catch (failure: Throwable) { CFRelease(limit); throw failure }
            val image = try { checkNotNull(CGImageSourceCreateThumbnailAtIndex(source, 0UL, options)) }
                finally { CFRelease(options); CFRelease(limit) }
            try {
                val actualWidth = CGImageGetWidth(image).toInt(); val actualHeight = CGImageGetHeight(image).toInt()
                check(actualWidth in 1..width && actualHeight in 1..height && actualWidth.toLong() * actualHeight <= 4_194_304)
                val bitmap = IosAssetPreviewLoader.decodePreview(encodeExportSamplePng(image))
                check(bitmap.width == actualWidth && bitmap.height == actualHeight)
                bitmap
            } finally { CGImageRelease(image) }
        }
    } finally { CFRelease(source) }
}

private fun encodeExportSamplePng(image: CGImageRef): ByteArray {
    val data = checkNotNull(CFDataCreateMutable(null, 0))
    try {
        val type = checkNotNull(CFStringCreateWithCString(null, "public.png", kCFStringEncodingUTF8))
        val destination = try { checkNotNull(CGImageDestinationCreateWithData(data, type, 1UL, null)) }
            finally { CFRelease(type) }
        try {
            CGImageDestinationAddImage(destination, image, null)
            check(CGImageDestinationFinalize(destination))
        } finally { CFRelease(destination) }
        val length = CFDataGetLength(data)
        check(length in 1..AssetPreviewPolicy.MaxEncodedBytes)
        return ByteArray(length.toInt()).also { bytes ->
            bytes.usePinned { memcpy(it.addressOf(0), CFDataGetBytePtr(data), length.toULong()) }
        }
    } finally { CFRelease(data) }
}
