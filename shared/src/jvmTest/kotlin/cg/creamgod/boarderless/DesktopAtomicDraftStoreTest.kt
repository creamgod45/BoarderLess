package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopAtomicDraftStoreTest {
    private val scope = "a".repeat(64)
    private fun <T> fixture(action: (Path) -> T): T {
        val root = Files.createTempDirectory("boarderless-atomic-draft-test-").toRealPath()
        try { return action(root) }
        finally { Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }

    @Test fun realFilesPreservePayloadGenerationTombstoneAndRejectStaleWriter(): Unit = fixture { root ->
        val first = DesktopAtomicDraftStore(root)
        assertNull(first.read(scope))
        val payload = "🙂 fixture".toByteArray()
        assertEquals(1L, first.compareAndSet(scope, null, payload).generation)
        payload[0] = 0
        val reopened = DesktopAtomicDraftStore(root)
        assertContentEquals("🙂 fixture".toByteArray(), reopened.read(scope)!!.payload)
        assertFailsWith<AtomicDraftConflictException> { reopened.compareAndSet(scope, null, "stale".toByteArray()) }
        val removed = reopened.compareAndSet(scope, 1, null)
        assertEquals(2L, removed.generation)
        assertNull(reopened.read(scope)!!.payload)
        assertFailsWith<AtomicDraftConflictException> { first.compareAndSet(scope, null, "ABA".toByteArray()) }
        assertEquals(3L, first.compareAndSet(scope, 2, byteArrayOf()).generation)
        assertContentEquals(byteArrayOf(), first.read(scope)!!.payload)
        assertNull(first.read("b".repeat(64)))
    }

    @Test fun corruptRecordAndUnsafePathStopWithoutReplacement(): Unit = fixture { root ->
        val store = DesktopAtomicDraftStore(root)
        assertFailsWith<IllegalArgumentException> { store.read("../outside") }
        assertFailsWith<IllegalArgumentException> { store.compareAndSet(scope, null, ByteArray(6 * 1024 * 1024 + 1)) }
        store.compareAndSet(scope, null, "fixture".toByteArray())
        val path = root.resolve("$scope.record")
        val copiedScope = "d".repeat(64)
        Files.copy(path, root.resolve("$copiedScope.record"))
        assertFailsWith<IllegalStateException> { store.read(copiedScope) }
        val corrupt = Files.readAllBytes(path).also { it[it.lastIndex] = (it.last() + 1).toByte() }
        Files.write(path, corrupt)
        assertFailsWith<IllegalStateException> { store.read(scope) }
        assertFailsWith<IllegalStateException> { store.compareAndSet(scope, 1, "replace".toByteArray()) }
        assertContentEquals(corrupt, Files.readAllBytes(path))
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, root)
        assertFailsWith<IllegalStateException> { DesktopAtomicDraftStore(alias) }
        val linkedScope = "c".repeat(64)
        Files.createSymbolicLink(root.resolve("$linkedScope.record"), path)
        assertFailsWith<IllegalStateException> { store.read(linkedScope) }
    }

    @Test fun publicationFailuresRequireFreshReadAndKeepStagingEvidence(): Unit = fixture { root ->
        val store = DesktopAtomicDraftStore(root)
        store.compareAndSet(scope, null, "old".toByteArray())
        val before = DesktopAtomicDraftStore(root) { if (it == AtomicDraftStage.DataForced) error("fixture failure") }
        assertFailsWith<IllegalStateException> { before.compareAndSet(scope, 1, "not-published".toByteArray()) }
        assertContentEquals("old".toByteArray(), store.read(scope)!!.payload)
        assertTrue(Files.list(root).use { files -> files.anyMatch { it.fileName.toString().startsWith("staging-") } })
        val after = DesktopAtomicDraftStore(root) { if (it == AtomicDraftStage.Published) error("fixture failure") }
        assertFailsWith<AtomicDraftCommitUnknownException> { after.compareAndSet(scope, 1, "published".toByteArray()) }
        assertContentEquals("published".toByteArray(), store.read(scope)!!.payload)
        assertEquals(2L, store.read(scope)!!.generation)
        assertFailsWith<AtomicDraftConflictException> { store.compareAndSet(scope, 1, "blind retry".toByteArray()) }
    }

    @Test fun reentrantReadCannotReleaseOwningProcessLock(): Unit = fixture { root ->
        val reader = DesktopAtomicDraftStore(root)
        val writer = DesktopAtomicDraftStore(root) { stage ->
            if (stage == AtomicDraftStage.DataForced) assertFailsWith<AtomicDraftBusyException> { reader.read(scope) }
        }
        writer.compareAndSet(scope, null, "fixture".toByteArray())
        assertEquals(1L, reader.read(scope)!!.generation)
    }

    private fun child(root: Path, mode: String, expected: String, payload: String): Process {
        val classpath = checkNotNull(System.getProperty("boarderless.test.classpath"))
        check(classpath.isNotBlank())
        return ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
            classpath, AtomicDraftProcessFixture::class.java.name,
            root.toString(), scope, mode, expected, payload).redirectErrorStream(true).start()
    }
    private fun line(reader: java.io.BufferedReader): String = CompletableFuture.supplyAsync { reader.readLine() }.get(8, TimeUnit.SECONDS)
        ?: error("Fixture child exited before protocol completion")

    @org.junit.Test(timeout = 30000L)
    fun twoIndependentProcessesCannotBothWinSameGeneration(): Unit = fixture { root ->
        val children = listOf(child(root, "cas", "none", "first"), child(root, "cas", "none", "second"))
        try {
            val readers = children.map { it.inputStream.bufferedReader() }
            readers.forEach { assertEquals("READY", line(it)) }
            children.forEach { it.outputStream.write(1); it.outputStream.flush() }
            val results = readers.map(::line)
            assertEquals(1, results.count { it == "WIN" })
            assertTrue(results.all { it in listOf("WIN", "BUSY", "CONFLICT") })
            children.forEach { assertTrue(it.waitFor(8, TimeUnit.SECONDS)); assertEquals(0, it.exitValue()) }
            val saved = DesktopAtomicDraftStore(root).read(scope)!!
            assertEquals(1L, saved.generation)
            assertTrue(saved.payload!!.decodeToString() in listOf("first", "second"))
        } finally { children.forEach { it.destroyForcibly(); it.waitFor(8, TimeUnit.SECONDS) } }
    }

    @org.junit.Test(timeout = 30000L)
    fun killedWriterReopensCompleteOldOrNewRecordAtPublicationBoundary(): Unit = fixture { root ->
        val store = DesktopAtomicDraftStore(root)
        store.compareAndSet(scope, null, "old".toByteArray())
        for (mode in listOf("kill-data", "kill-published")) {
            val process = child(root, mode, "1", "new")
            try {
                val reader = process.inputStream.bufferedReader()
                assertEquals("READY", line(reader))
                process.outputStream.write(1); process.outputStream.flush()
                assertEquals("CHECKPOINT", line(reader))
                assertFailsWith<AtomicDraftBusyException> { store.read(scope) }
                process.destroyForcibly()
                assertTrue(process.waitFor(8, TimeUnit.SECONDS))
                val saved = DesktopAtomicDraftStore(root).read(scope)!!
                assertEquals(if (mode == "kill-data") 1L else 2L, saved.generation)
                assertContentEquals((if (mode == "kill-data") "old" else "new").toByteArray(), saved.payload)
            } finally { process.destroyForcibly(); process.waitFor(8, TimeUnit.SECONDS) }
        }
    }
}

/** Actual separate JVMs; no real preferences, network or user data. */
object AtomicDraftProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        val store = DesktopAtomicDraftStore(Path.of(args[0])) { stage ->
            if ((args[2] == "kill-data" && stage == AtomicDraftStage.DataForced) ||
                (args[2] == "kill-published" && stage == AtomicDraftStage.Published)) {
                println("CHECKPOINT"); System.out.flush(); System.`in`.read()
            }
        }
        println("READY"); System.out.flush(); System.`in`.read()
        val result = try {
            store.compareAndSet(args[1], args[3].takeUnless { it == "none" }?.toLong(), args[4].toByteArray()); "WIN"
        } catch (_: AtomicDraftBusyException) { "BUSY" }
          catch (_: AtomicDraftConflictException) { "CONFLICT" }
        println(result); System.out.flush()
    }
}
