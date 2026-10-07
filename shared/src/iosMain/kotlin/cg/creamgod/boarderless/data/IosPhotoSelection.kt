@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cg.creamgod.boarderless.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import platform.Foundation.*
import kotlin.coroutines.resumeWithException

/** Copy INSIDE the provider callback: Apple deletes its temporary URL when that callback returns.
 * Only the explicit selection is loaded, with no broad Photos authorization or PHAsset lookup.
 */
internal suspend fun loadIosPhotoSelection(provider: NSItemProvider): AssetTransferSource {
    val type =
        iosPhotoRepresentation(provider.registeredTypeIdentifiers.filterIsInstance<String>())
            ?: throw AssetImportException(AssetImportIssue.UnsupportedMediaType, "No supported Photos representation")
    val owned =
        withTimeout(120000) {
            suspendCancellableCoroutine<IosOwnedPhotoFile> { pending ->
                val progress =
                    provider.loadFileRepresentationForTypeIdentifier(type.first) { url, error ->
                        if (pending.isActive) {
                            try {
                                check(error == null && url != null) { "Selected photo could not be exported" }
                                val file = copyIosPhotoFile(checkNotNull(url), type.second)
                                pending.resume(file, onCancellation = { _, cancelledFile, _ -> cancelledFile.release() })
                            } catch (failure: Exception) {
                                if (pending.isActive) pending.resumeWithException(failure)
                            }
                        }
                    }
                pending.invokeOnCancellation { progress.cancel() }
            }
        }
    try {
        return IosFileAssetTransferSource.fromUrl(owned.url)
    } finally {
        owned.release()
    }
}

internal fun iosPhotoRepresentation(types: List<String>): Pair<String, String>? =
    listOf(
        "com.compuserve.gif" to "gif",
        "public.png" to "png",
        "public.jpeg" to "jpg",
        "org.webmproject.webp" to "webp",
        "public.mpeg-4" to "mp4",
        "org.webmproject.webm" to "webm",
    ).firstOrNull { it.first in types }

internal class IosOwnedPhotoFile(
    val url: NSURL,
) {
    fun release() {
        val path = checkNotNull(url.path)
        val manager = NSFileManager.defaultManager
        check(!manager.fileExistsAtPath(path) || manager.removeItemAtURL(url, null))
    }
}

internal fun copyIosPhotoFile(
    url: NSURL,
    suffix: String,
): IosOwnedPhotoFile {
    require(suffix in listOf("gif", "png", "jpg", "webp", "mp4", "webm"))
    val manager = NSFileManager.defaultManager
    val source = checkNotNull(url.path)
    val size =
        (manager.attributesOfItemAtPath(source, null)?.get(NSFileSize) as? NSNumber)?.longLongValue
            ?: error("Selected photo size is unavailable")
    if (size > MaxWorkspaceAssetBytes) throw AssetImportException(AssetImportIssue.AssetTooLarge, "Selected photo exceeds size limit")
    if (size <= 0) throw AssetImportException(AssetImportIssue.InvalidByteSize, "Selected photo is empty")
    val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + "boarderless-photo-${NSUUID().UUIDString}.$suffix")
    try {
        check(manager.copyItemAtURL(url, target, null)) { "Selected photo copy failed" }
        val copiedSize = (manager.attributesOfItemAtPath(checkNotNull(target.path), null)?.get(NSFileSize) as? NSNumber)?.longLongValue
        if (copiedSize != size) throw AssetImportException(AssetImportIssue.TruncatedSource, "Selected photo changed during copy")
        return IosOwnedPhotoFile(target)
    } catch (failure: Throwable) {
        manager.removeItemAtURL(target, null)
        throw failure
    }
}
