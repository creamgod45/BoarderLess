package cg.creamgod.boarderless

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class DesktopDraftImportTest {
    @Test fun readsUnicodeAndOptionalBomWithoutModifyingSelectedFile() =
        runBlocking {
            val directory = Files.createTempDirectory("boarderless-draft-read-test")
            val path = directory.resolve("backup.json")
            val bytes = "\uFEFF{\"text\":\"私密🙂\"}".toByteArray(Charsets.UTF_8)
            try {
                Files.write(path, bytes)
                assertEquals("{\"text\":\"私密🙂\"}", readDesktopDraftJson(path) { true })
                assertContentEquals(bytes, Files.readAllBytes(path))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test fun rejectsOversizedEmptyMalformedUtf8DirectoriesAndSymlinksWithoutDeletingSource() =
        runBlocking {
            val directory = Files.createTempDirectory("boarderless-draft-read-invalid-test")
            val path = directory.resolve("backup.json")
            val link = directory.resolve("link.json")
            try {
                listOf(byteArrayOf(), byteArrayOf(0xC3.toByte(), 0x28), ByteArray(4 * 1024 * 1024 + 1)).forEach { bytes ->
                    Files.write(path, bytes)
                    val error = assertFailsWith<IllegalArgumentException> { readDesktopDraftJson(path) { true } }
                    // Coroutine stack recovery may wrap the already-sanitized exception in a copy.
                    generateSequence<Throwable>(error) { it.cause }.forEach {
                        assertEquals("Draft file could not be read", it.message)
                    }
                    assertFalse(directory.toString() in error.message.orEmpty())
                    assertContentEquals(bytes, Files.readAllBytes(path))
                }
                Files.writeString(path, "{}")
                Files.createSymbolicLink(link, path)
                assertFailsWith<IllegalArgumentException> { readDesktopDraftJson(link) { true } }
                assertFailsWith<IllegalArgumentException> { readDesktopDraftJson(directory) { true } }
                assertEquals("{}", Files.readString(path))
            } finally {
                Files.deleteIfExists(link)
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test fun scopeExpiryBeforeOpenOrDuringReadReturnsNoContentAndKeepsFile() =
        runBlocking {
            val directory = Files.createTempDirectory("boarderless-draft-read-scope-test")
            val path = directory.resolve("backup.json")
            val value = "x".repeat(20_000)
            try {
                Files.writeString(path, value)
                assertFailsWith<IllegalArgumentException> { readDesktopDraftJson(path) { false } }
                var checks = 0
                assertFailsWith<IllegalArgumentException> { readDesktopDraftJson(path) { ++checks < 3 } }
                assertTrue(checks >= 3)
                assertEquals(value, Files.readString(path))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }
}
