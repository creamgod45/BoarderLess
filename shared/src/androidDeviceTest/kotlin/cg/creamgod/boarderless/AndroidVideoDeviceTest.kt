package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.AndroidVideoPlayback
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Native preparation failure gate; successful playback/codec/texture tests still require QA media. */
class AndroidVideoDeviceTest {
    @Test fun corruptVideoCannotPrepareAndCleansOwnedDirectory() = runBlocking {
        val root = File(System.getProperty("java.io.tmpdir") ?: error("No private temp directory"))
        val directory = File.createTempFile("video-native-test-", ".tmp", root).apply { check(delete()); check(mkdir()) }
        val file = File(directory, "invalid.mp4")
        file.writeBytes(ByteArray(32))
        try {
            assertFails { AndroidVideoPlayback.open(file, directory) }
            assertEquals(false, directory.exists())
        } finally {
            if (file.exists()) check(file.delete())
            if (directory.exists()) check(directory.delete())
        }
    }
}
