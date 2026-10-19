package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.remote.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.*

class DesktopClientSequenceLedgerTest {
    private val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")

    private fun fixture(action: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-client-ledger-test-").toRealPath()
        try {
            action(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }
    }

    @Test fun explicitFloorSharesAcrossWorkspacesAndNeverRegressesOrOverflows() =
        fixture { root ->
            val store = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
            assertFails { store.reserve(scope, 1) }
            store.initialize(scope, 40)
            assertEquals(41L..43L, store.reserve(scope, 1, 3))
            val reopened = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
            assertEquals(44L..45L, reopened.reserve(scope.copy(workspaceId = "other"), 2))
            reopened.advance(scope, 1)
            assertEquals(ClientSequenceRecord(3, 45), reopened.read(scope))
            assertFailsWith<AtomicDraftConflictException> { store.reserve(scope, 1, 1) }
            assertNull(reopened.read(scope.copy(clientId = "other")))
            reopened.advance(scope, 9_007_199_254_740_991L)
            val last = reopened.read(scope)
            assertFails { reopened.reserve(scope, 1) }
            assertEquals(last, reopened.read(scope))
        }

    @Test fun unknownPayloadAndTombstoneArePreservedNotReset() =
        fixture { root ->
            val files = DesktopAtomicDraftStore(root)
            val ledger = DesktopClientSequenceLedger(files)
            val bytes = "unknown-valid-outer-envelope".toByteArray()
            files.compareAndSet(ledger.key(scope), null, bytes)
            assertFails { ledger.initialize(scope, 0) }
            assertFails { ledger.reserve(scope, 1) }
            assertContentEquals(bytes, files.read(ledger.key(scope))!!.payload)
            val other = scope.copy(clientId = "tombstone")
            files.compareAndSet(ledger.key(other), null, null)
            assertFails { ledger.initialize(other, 0) }
            assertFails { ledger.reserve(other, 1) }
        }

    @Test fun publicationUnknownConsumesRangeAndRequiresFreshReview() =
        fixture { root ->
            val normal = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
            normal.initialize(scope, 50)
            val fault =
                DesktopClientSequenceLedger(
                    DesktopAtomicDraftStore(root) {
                        if (it == AtomicDraftStage.Published) error("fixture unknown sequence publication")
                    },
                )
            assertFailsWith<AtomicDraftCommitUnknownException> { fault.reserve(scope, 1, 3) }
            assertEquals(ClientSequenceRecord(2, 53), normal.read(scope))
            assertFailsWith<AtomicDraftConflictException> { normal.reserve(scope, 1, 3) }
            assertEquals(54L..55L, normal.reserve(scope, 2, 2))
        }

    private fun child(
        root: Path,
        mode: String = "reserve",
    ): Process =
        ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            checkNotNull(System.getProperty("boarderless.test.classpath")),
            ClientSequenceProcessFixture::class.java.name,
            root.toString(),
            mode,
        ).redirectErrorStream(true).start()

    private fun line(reader: java.io.BufferedReader): String =
        CompletableFuture.supplyAsync { reader.readLine() }.get(8, TimeUnit.SECONDS) ?: error("Fixture child exited")

    @org.junit.Test(timeout = 30000L)
    fun realProcessesCannotBothReserveSameGeneration() =
        fixture { root ->
            val ledger = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
            ledger.initialize(scope, 50)
            val children = listOf(child(root), child(root))
            try {
                val readers = children.map { it.inputStream.bufferedReader() }
                readers.forEach { assertEquals("READY", line(it)) }
                children.forEach {
                    it.outputStream.write(1)
                    it.outputStream.flush()
                }
                val results = readers.map(::line)
                assertEquals(1, results.count { it == "WIN:51:53" })
                assertEquals(1, results.count { it == "BUSY" || it == "CONFLICT" })
                children.forEach {
                    assertTrue(it.waitFor(8, TimeUnit.SECONDS))
                    assertEquals(0, it.exitValue())
                }
                assertEquals(ClientSequenceRecord(2, 53), ledger.read(scope))
                assertEquals(54L..55L, ledger.reserve(scope, 2, 2))
            } finally {
                children.forEach {
                    it.destroyForcibly()
                    it.waitFor(8, TimeUnit.SECONDS)
                }
            }
        }

    @org.junit.Test(timeout = 30000L)
    fun killedReservationReopensOldOrNewCompleteHighWater() =
        fixture { root ->
            val ledger = DesktopClientSequenceLedger(DesktopAtomicDraftStore(root))
            ledger.initialize(scope, 50)
            for (mode in listOf("kill-data", "kill-published")) {
                val process = child(root, mode)
                try {
                    val reader = process.inputStream.bufferedReader()
                    assertEquals("READY", line(reader))
                    process.outputStream.write(1)
                    process.outputStream.flush()
                    assertEquals("CHECKPOINT", line(reader))
                    assertFailsWith<AtomicDraftBusyException> { ledger.read(scope) }
                    process.destroyForcibly()
                    assertTrue(process.waitFor(8, TimeUnit.SECONDS))
                    assertEquals(
                        if (mode == "kill-data") ClientSequenceRecord(1, 50) else ClientSequenceRecord(2, 53),
                        DesktopClientSequenceLedger(DesktopAtomicDraftStore(root)).read(scope),
                    )
                } finally {
                    process.destroyForcibly()
                    process.waitFor(8, TimeUnit.SECONDS)
                }
            }
            assertEquals(54L..55L, ledger.reserve(scope, 2, 2))
        }
}

object ClientSequenceProcessFixture {
    @JvmStatic fun main(args: Array<String>) {
        val ledger =
            DesktopClientSequenceLedger(
                DesktopAtomicDraftStore(Path.of(args[0])) { stage ->
                    if ((args[1] == "kill-data" && stage == AtomicDraftStage.DataForced) ||
                        (args[1] == "kill-published" && stage == AtomicDraftStage.Published)
                    ) {
                        println("CHECKPOINT")
                        System.out.flush()
                        System.`in`.read()
                    }
                },
            )
        val scope = PendingSubmissionScope("https://qa.invalid/api/v1", "actor", "client", "workspace")
        println("READY")
        System.out.flush()
        System.`in`.read()
        val result =
            try {
                val range = ledger.reserve(scope, 1, 3)
                "WIN:${range.first}:${range.last}"
            } catch (_: AtomicDraftBusyException) {
                "BUSY"
            } catch (_: AtomicDraftConflictException) {
                "CONFLICT"
            }
        println(result)
        System.out.flush()
    }
}
