package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserQuickSchemeStoreTest {
    private val payload = ClipboardJson.encodeToString(ClipboardPayload.serializer(), ClipboardPayload(version = 4, nodes = listOf(ClipboardNode("source", 0f, 0f, 120f, 80f, 0f, "中文🙂", "paper"))))

    @Test fun libraryPublishesStrictlyThenReopensRenamesDeletesAndNeverReusesIds() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val library = browserQuickSchemeRepository()
            val first = library.save(payload, "中文🙂", 4)
            assertEquals("true", browserRecordFixtureValue("sawEarlySuccess"))
            assertEquals("2", browserRecordFixtureValue("committed")) // CAS + durable re-publication.
            browserRecordFixtureReopen()
            val reopened = browserQuickSchemeRepository()
            assertEquals(listOf(first), reopened.list())
            assertEquals("改名", reopened.rename(first.id, " 改名 ")?.name)
            assertTrue(reopened.delete(first.id)); assertFalse(reopened.delete(first.id))
            assertTrue(reopened.save(payload, "next", 4).id > first.id)
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun putSuccessIsNotSaveAcknowledgementAndCancellationRollsBack() = runTest {
        browserRecordFixtureInstall("hold")
        try {
            val put = CompletableDeferred<Unit>(); browserRecordFixtureWaitForPut { put.complete(Unit) }
            val library = browserQuickSchemeRepository()
            val work = async { library.save(payload, "held", 4) }
            put.await(); assertFalse(work.isCompleted)
            assertEquals("0", browserRecordFixtureValue("acknowledged"))
            work.cancelAndJoin(); browserRecordFixtureReopen()
            assertTrue(browserQuickSchemeRepository().list().isEmpty())
        } finally { browserRecordFixtureRestore() }
    }

    @Test fun competingTabsCannotOverwriteAndCorruptLibraryCannotReset() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            supervisorScope {
                val a = async { runCatching { browserQuickSchemeRepository().save(payload, "a", 4) } }
                val b = async { runCatching { browserQuickSchemeRepository().save(payload, "b", 4) } }
                // The result may be two serial allocations or one conflict; neither may overwrite.
                val successes = listOf(a.await(), b.await()).mapNotNull { it.getOrNull() }
                val saved = browserQuickSchemeRepository().list()
                assertTrue(saved.size in 1..2)
                assertTrue(saved.all { it.name in setOf("a", "b") })
                // A concurrent writer can advance generation after our strict CAS but before
                // confirmDurable, so an unacknowledged operation can still be present on disk.
                assertTrue(successes.all { success -> saved.any { it.id == success.id && it.name == success.name } })
                assertEquals(saved.size, saved.map { it.id }.distinct().size)
            }
            val corrupted = CompletableDeferred<Unit>()
            browserRecordFixtureCorrupt(PersistentQuickSchemeStore.Key) { corrupted.complete(Unit) }; corrupted.await()
            assertFails { browserQuickSchemeRepository().list() }
            assertFails { browserQuickSchemeRepository().save(payload, "must not reset", 4) }
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun storageDenialAndAbortedTransactionNeverReturnSavedScheme() = runTest {
        for (mode in listOf("denied", "abort")) {
            browserRecordFixtureInstall(mode)
            try {
                assertFails { browserQuickSchemeRepository().save(payload, "no ACK", 4) }
                assertEquals("0", browserRecordFixtureValue("acknowledged"))
                browserRecordFixtureReopen(); assertTrue(browserQuickSchemeRepository().list().isEmpty())
            } finally { browserRecordFixtureRestore() }
        }
    }
    @Test fun uncertainConfirmationReopensCommittedSchemeAndReviewRetryDoesNotAllocateAgain() = runTest {
        browserRecordFixtureInstall("abortConfirmation")
        try {
            val library = browserQuickSchemeRepository()
            assertFails { library.save(payload, "reviewed", 4) }
            browserRecordFixtureReopen()
            val committed = browserQuickSchemeRepository().list().single()
            val saved = saveMaterializedBundle(browserQuickSchemeRepository(), committed.copy(id = 77))
            assertEquals(committed, saved)
            assertEquals(2, browserQuickSchemeRepository().save(payload, "next", 4).id)
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun wholeLibrarySupportsNativeFourMiBProtocolBeyondReceiptSizeWithoutTruncation() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val large = ClipboardJson.encodeToString(ClipboardPayload.serializer(), ClipboardPayload(version = 4,
                nodes = listOf(ClipboardNode("source", 0f, 0f, 120f, 80f, 0f, "x".repeat(480 * 1024), "paper"))))
            val library = browserQuickSchemeRepository()
            repeat(3) { library.save(large, "large-$it", 4) }
            browserRecordFixtureReopen()
            val reopened = browserQuickSchemeRepository().list()
            assertEquals(3, reopened.size); assertTrue(reopened.all { it.payload == large })
        } finally { browserRecordFixtureRestore() }
    }

}
