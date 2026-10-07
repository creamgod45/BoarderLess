package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DraftImportRuntimeTest {
    @Test fun explicitSelectionReturnsContentOrCancellationOnly() =
        runTest {
            var calls = 0
            val runtime =
                DraftImportRuntime { guard ->
                    assertTrue(guard())
                    calls++
                    "{\"text\":\"中文🙂\"}"
                }
            assertEquals("{\"text\":\"中文🙂\"}", selectWorkspaceDraftBackup(runtime) { true })
            assertEquals(1, calls)
            assertNull(selectWorkspaceDraftBackup(DraftImportRuntime { null }) { true })
            assertFailsWith<IllegalStateException> { selectWorkspaceDraftBackup(DraftImportRuntime.Unavailable) { true } }
        }

    @Test fun staleScopeAndOverLimitPlatformResultsCannotPublish() =
        runTest {
            var calls = 0
            assertFailsWith<IllegalStateException> {
                selectWorkspaceDraftBackup(
                    DraftImportRuntime {
                        calls++
                        "private"
                    },
                ) { false }
            }
            assertEquals(0, calls)
            var active = true
            assertFailsWith<IllegalStateException> {
                selectWorkspaceDraftBackup(
                    DraftImportRuntime {
                        active = false
                        "private"
                    },
                ) { active }
            }
            listOf("", "x".repeat(4 * 1024 * 1024 + 1), "界".repeat(1_400_000)).forEach { text ->
                assertFailsWith<IllegalArgumentException> {
                    selectWorkspaceDraftBackup(DraftImportRuntime { text }) { true }
                }
            }
        }

    @Test fun cancelledSelectionDoesNotReturnLatePrivateContent() =
        runTest {
            val pending =
                async {
                    selectWorkspaceDraftBackup(
                        DraftImportRuntime {
                            currentCoroutineContext().cancel()
                            "private"
                        },
                    ) { true }
                }
            assertFailsWith<CancellationException> { pending.await() }
        }
}
