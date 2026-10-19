@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless.data

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.posix.*
import kotlin.coroutines.coroutineContext

/** Permission held only around the coordinated write; no bookmark or directory preference. */
internal class IosDraftBackupDestination(
    private val folder: NSURL,
    private val filenamePrefix: String = "boarderless-draft",
) : DraftBackupDestination {
    private var used = false
    private var closed = false

    override suspend fun write(
        json: String,
        canWrite: () -> Boolean,
    ) = withContext(Dispatchers.Default) {
        check(!used && !closed)
        used = true
        val context = coroutineContext
        context.ensureActive()
        check(canWrite())
        check(folder.startAccessingSecurityScopedResource()) { "Folder access denied" }
        try {
            require(filenamePrefix.matches(Regex("[A-Za-z0-9_-]+")))
            val name = "$filenamePrefix-${NSUUID().UUIDString}.json"
            val target = checkNotNull(folder.URLByAppendingPathComponent(name))
            coordinateIosDraftBackupWrite(target, json) {
                context.ensureActive()
                canWrite()
            }
        } finally {
            folder.stopAccessingSecurityScopedResource()
        }
    }

    override fun close() {
        closed = true
    }
}

internal class IosDraftCoordinationFailure(
    val nativeError: NSError?,
    accessorRan: Boolean,
) : IllegalStateException("File coordination failed (${nativeError?.domain}:${nativeError?.code}, accessor=$accessorRan)")

internal fun coordinateIosDraftBackupWrite(
    target: NSURL,
    json: String,
    canWrite: () -> Boolean,
) {
    check(target.isFileURL() && canWrite())
    var callbackFailure: Throwable? = null
    var written = false
    memScoped {
        val coordinationError = alloc<ObjCObjectVar<NSError?>>()
        coordinationError.value = null
        // Announce save-as/creation intent even for a not-yet-existing destination.
        // The accessor still uses O_EXCL: this option never grants permission to overwrite.
        NSFileCoordinator(filePresenter = null).coordinateWritingItemAtURL(
            target,
            options = NSFileCoordinatorWritingForReplacing,
            error = coordinationError.ptr,
        ) { coordinated ->
            try {
                check(canWrite())
                writeIosDraftBackupNewFile(checkNotNull(checkNotNull(coordinated).path), json, canWrite)
                written = true
            } catch (error: Throwable) {
                callbackFailure = error
            }
        }
        callbackFailure?.let { throw it }
        if (coordinationError.value != null || !written) {
            throw IosDraftCoordinationFailure(coordinationError.value, written)
        }
    }
}

/** Called inside file coordination for external URLs; tested independently on sandbox files.
 * Exclusive creation and owner-only permissions. Errors never delete a pre-existing path.
 */
internal fun writeIosDraftBackupNewFile(
    path: String,
    json: String,
    canWrite: () -> Boolean,
) {
    check(canWrite()) { "Draft backup scope changed" }
    val bytes = json.encodeToByteArray()
    require(bytes.isNotEmpty())
    val descriptor = open(path, O_WRONLY or O_CREAT or O_EXCL, 384) // 0600
    check(descriptor >= 0) { "Unable to create new backup file" }
    var completed = false
    var failure: Throwable? = null
    try {
        check(canWrite())
        bytes.usePinned { pinned ->
            var offset = 0
            while (offset < bytes.size) {
                check(canWrite())
                val count = platform.posix.write(descriptor, pinned.addressOf(offset), (bytes.size - offset).toULong())
                check(count > 0) { "Backup write failed" }
                offset += count.toInt()
            }
        }
        check(canWrite())
        completed = true
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        val closed = platform.posix.close(descriptor)
        if (!completed || closed != 0) unlink(path)
        if (failure == null) check(closed == 0) { "Backup close failed" }
    }
}
