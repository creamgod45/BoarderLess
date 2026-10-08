package cg.creamgod.boarderless

import android.system.Os
import androidx.test.platform.app.InstrumentationRegistry
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.feature.canvas.*
import java.io.File
import java.util.UUID
import kotlin.test.*

/** Real Android rename/fsync and reopen. Each fixture owns only its private temporary leaf. */
class AndroidFileQuickSchemeDeviceTest {
    private fun root() = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir.canonicalFile,
        "scheme-library-test-${UUID.randomUUID()}")
    private fun payload(text: String = "中文🙂", count: Int = 1) = ClipboardJson.encodeToString(
        ClipboardPayload.serializer(), ClipboardPayload(version = 4,
            nodes = (1..count).map { ClipboardNode("source-$it", it * 10f, 0f, 120f, 80f, 0f, text, "paper") }))

    @Test fun saveRenameDeleteAndMonotonicIdsSurviveNativeReopen() {
        val root = root()
        try {
            val firstStore = AndroidFileQuickSchemeStore(root)
            assertTrue(firstStore.list().isEmpty())
            val first = firstStore.save(payload(), "方案🙂", 4, "source-workspace")
            val second = firstStore.save(payload("第二個"), "Second", 4)
            val reopened = AndroidFileQuickSchemeStore(root)
            assertEquals(listOf(first, second), reopened.list())
            val renamed = assertNotNull(reopened.rename(first.id, " 已改名 "))
            assertEquals("已改名", renamed.name)
            assertEquals(renamed, AndroidFileQuickSchemeStore(root).list().first())
            assertTrue(reopened.delete(first.id)); assertFalse(reopened.delete(first.id))
            val third = AndroidFileQuickSchemeStore(root).save(payload(), "第三個", 4)
            assertEquals(3, third.id)
            assertEquals(listOf(second, third), AndroidFileQuickSchemeStore(root).list())
            assertEquals(third, reopened.latest())
            assertEquals(0x1c0, Os.lstat(root.path).st_mode and 0x1ff)
            for (file in root.listFiles()!!) assertEquals(0x180, Os.lstat(file.path).st_mode and 0x1ff)
            assertFalse(root.listFiles()!!.any { it.name.endsWith(".partial") })
        } finally { root.deleteRecursively() }
    }

    @Test fun wholeLibraryAboveReceiptLimitIsDurableAndInvalidSaveLeavesItUntouched() {
        val root = root()
        try {
            val store = AndroidFileQuickSchemeStore(root)
            val content = payload("x".repeat(40000), 16)
            val first = store.save(content, "Large 1", 4)
            val second = store.save(content, "Large 2", 4)
            val record = root.listFiles()!!.single { it.name.endsWith(".receipt") }
            assertTrue(record.length() > 1024 * 1024)
            assertEquals(listOf(first, second), AndroidFileQuickSchemeStore(root).list())
            val before = record.readBytes()
            assertFails { store.save("not a scheme", "Invalid", 4) }
            assertContentEquals(before, record.readBytes())
            assertEquals(listOf(first, second), AndroidFileQuickSchemeStore(root).list())
        } finally { root.deleteRecursively() }
    }

    @Test fun corruptLibraryRefusesReadAndWriteWithoutReset() {
        val root = root()
        try {
            AndroidFileQuickSchemeStore(root).save(payload(), "Keep", 4)
            val record = root.listFiles()!!.single { it.name.endsWith(".receipt") }
            record.writeText(record.readText().replace("Keep", "Lost"))
            val corrupt = record.readBytes()
            val reopened = AndroidFileQuickSchemeStore(root)
            assertFails { reopened.list() }
            assertFails { reopened.save(payload(), "New", 4) }
            assertContentEquals(corrupt, record.readBytes())
        } finally { root.deleteRecursively() }
    }
}
