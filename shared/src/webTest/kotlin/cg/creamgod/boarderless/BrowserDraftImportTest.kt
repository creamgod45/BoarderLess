package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.test.*

/** Calls actual Kotlin JS/Wasm bridge with real DOM input/File, not an OS chooser signoff. */
class BrowserDraftImportTest {
    @Test fun actualBridgeReadsFileAndReturnsUnicodeWithBomRemoved() =
        runTest {
            browserDraftImportFixtureInstall("unicode")
            try {
                assertEquals("{\"text\":\"中文🙂\"}", selectWorkspaceDraftBackup(browserDraftImportRuntime()) { true })
                assertEquals(0, browserDraftImportFixtureInputs())
            } finally {
                browserDraftImportFixtureRestore()
            }
        }

    @Test fun actualBridgeReturnsNullOnCancelAndRejectsOversizeOrInvalidBytes() =
        runTest {
            browserDraftImportFixtureInstall("cancel")
            try {
                assertNull(selectWorkspaceDraftBackup(browserDraftImportRuntime()) { true })
                assertEquals(0, browserDraftImportFixtureInputs())
            } finally {
                browserDraftImportFixtureRestore()
            }
            listOf("oversized", "invalid").forEach { mode ->
                browserDraftImportFixtureInstall(mode)
                try {
                    val error =
                        assertFailsWith<IllegalArgumentException> {
                            selectWorkspaceDraftBackup(browserDraftImportRuntime()) { true }
                        }
                    assertEquals("Draft file could not be read", error.message)
                    assertEquals(0, browserDraftImportFixtureInputs())
                } finally {
                    browserDraftImportFixtureRestore()
                }
            }
        }

    @Test fun actualBridgeCannotReturnFileAfterCoroutineCancellation() =
        runTest {
            browserDraftImportFixtureInstall("held")
            try {
                val pending =
                    async(start = CoroutineStart.UNDISPATCHED) {
                        selectWorkspaceDraftBackup(browserDraftImportRuntime()) { true }
                    }
                assertEquals(1, browserDraftImportFixtureInputs())
                pending.cancel()
                assertFailsWith<CancellationException> { pending.await() }
                assertEquals(0, browserDraftImportFixtureInputs())
                suspendCancellableCoroutine<Unit> { continuation ->
                    browserDraftImportFixtureRelease { if (continuation.isActive) continuation.resume(Unit) }
                }
                assertTrue(pending.isCancelled)
                assertEquals(0, browserDraftImportFixtureInputs())
            } finally {
                browserDraftImportFixtureRestore()
            }
        }

    @Test fun booleanScopeCallbackIsRecheckedAfterActualAsyncFileRead() =
        runTest {
            browserDraftImportFixtureInstall("unicode")
            try {
                var checks = 0
                assertFailsWith<IllegalArgumentException> {
                    selectWorkspaceDraftBackup(browserDraftImportRuntime()) { ++checks < 4 }
                }
                assertEquals(4, checks)
                assertEquals(0, browserDraftImportFixtureInputs())
            } finally {
                browserDraftImportFixtureRestore()
            }
        }
}

internal expect fun browserDraftImportFixtureInstall(mode: String)

internal expect fun browserDraftImportFixtureRestore()

internal expect fun browserDraftImportFixtureInputs(): Int

internal expect fun browserDraftImportFixtureRelease(done: () -> Unit)
