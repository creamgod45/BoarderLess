@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import platform.PhotosUI.PHPickerConfigurationAssetRepresentationModeCompatible
import platform.PhotosUI.PHPickerViewController
import kotlin.test.*

class IosPhotoSelectionTest {
    @Test fun callbackMatchesNativeControllerAcrossDifferentKotlinWrappersButRejectsStalePicker() {
        val original = PHPickerViewController(iosMediaPhotoConfiguration())
        val callbackWrapper = interpretObjCPointer<PHPickerViewController>(original.objcPtr())!!
        assertNotSame(original, callbackWrapper)
        assertTrue(isCurrentIosPhotoPicker(original, callbackWrapper))
        assertFalse(isCurrentIosPhotoPicker(null, callbackWrapper))
        assertFalse(isCurrentIosPhotoPicker(PHPickerViewController(iosMediaPhotoConfiguration()), callbackWrapper))
    }

    @Test fun configurationRequestsSingleCompatiblePhotoOrVideoWithoutLibraryLookup() {
        val configuration = iosMediaPhotoConfiguration()
        assertEquals(1L, configuration.selectionLimit)
        assertEquals(PHPickerConfigurationAssetRepresentationModeCompatible, configuration.preferredAssetRepresentationMode)
        assertNotNull(configuration.filter)
    }

    @Test fun animatedGifIsPreferredAndUnknownFormatsAreNotRelabeled() {
        assertEquals("com.compuserve.gif" to "gif", iosPhotoRepresentation(listOf("public.jpeg", "com.compuserve.gif")))
        assertEquals("public.mpeg-4" to "mp4", iosPhotoRepresentation(listOf("public.mpeg-4")))
        assertNull(iosPhotoRepresentation(listOf("public.heic", "com.apple.quicktime-movie")))
    }

    @Test fun providerCopySurvivesOriginalRemovalAndUploadSnapshotReleasesBothOwnedCopies() =
        runBlocking {
            val before = copies()
            val original = NSTemporaryDirectory() + "photo-test-${NSUUID().UUIDString}.png"
            val bytes = byteArrayOf(97, 98, 99)
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            check(NSFileManager.defaultManager.createFileAtPath(original, data, null))
            try {
                val owned = copyIosPhotoFile(NSURL.fileURLWithPath(original), "png")
                try {
                    check(NSFileManager.defaultManager.removeItemAtPath(original, null))
                    val source = IosFileAssetTransferSource.fromUrl(owned.url)
                    try {
                        assertEquals("image/png", source.mediaType)
                        assertEquals("sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", source.checksum)
                        assertContentEquals(bytes, source.readChunk(0, 3))
                    } finally {
                        source.release()
                    }
                } finally {
                    owned.release()
                    owned.release()
                }
                assertEquals(before, copies())
            } finally {
                NSFileManager.defaultManager.removeItemAtPath(original, null)
            }
        }

    @Test fun emptyOrMissingProviderFileIsRejectedWithoutOwnedLeaks() {
        val before = copies()
        val original = NSTemporaryDirectory() + "photo-test-${NSUUID().UUIDString}.png"
        check(NSFileManager.defaultManager.createFileAtPath(original, null, null))
        try {
            val error = assertFailsWith<AssetImportException> { copyIosPhotoFile(NSURL.fileURLWithPath(original), "png") }
            assertEquals(AssetImportIssue.InvalidByteSize, error.issue)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(original))
            assertEquals(before, copies())
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(original, null)
        }
        assertFailsWith<IllegalStateException> { copyIosPhotoFile(NSURL.fileURLWithPath(original), "png") }
        assertEquals(before, copies())
    }

    private fun copies() =
        NSFileManager.defaultManager
            .contentsOfDirectoryAtPath(NSTemporaryDirectory(), null)
            .orEmpty()
            .filterIsInstance<String>()
            .filter { it.startsWith("boarderless-photo-") }
            .toSet()
}
