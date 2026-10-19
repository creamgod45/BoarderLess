package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.test.*

/** DOM dispatch is intercepted: tests do not assert actual browser download completion. */
class BrowserDraftBackupTest {
    @Test fun realBlobUtf8SingleDispatchAndDelayedResourceRelease() =
        runTest {
            browserBackupFixtureInstall()
            try {
                val json = "{\"text\":\"私密🙂</script>\"}"
                val destination = BrowserDraftBackupDestination("boarderless-draft-fixture.json")
                destination.write(json) { true }
                assertEquals("1", browserBackupFixtureValue("clicks"))
                assertEquals("boarderless-draft-fixture.json", browserBackupFixtureValue("filename"))
                assertEquals("application/json;charset=utf-8", browserBackupFixtureValue("type"))
                assertEquals("0", browserBackupFixtureValue("anchors"))
                val text =
                    suspendCancellableCoroutine<String> { pending ->
                        browserBackupFixtureText { if (pending.isActive) pending.resume(it) }
                    }
                assertEquals(json, text)
                assertEquals("1", browserBackupFixtureValue("urls"))
                destination.close() // Closing the adapter must not prematurely cancel browser consumption.
                assertFails { destination.write(json) { true } }
                browserBackupFixtureExpire()
                assertEquals("0", browserBackupFixtureValue("urls"))
                assertEquals("1", browserBackupFixtureValue("revoked"))
                assertEquals("0", browserBackupFixtureValue("timers"))
            } finally {
                browserBackupFixtureRestore()
            }
        }

    @Test fun expiredScopeBeforeAndDuringDispatchDoesNotDownloadOrLeak() =
        runTest {
            browserBackupFixtureInstall()
            try {
                assertFails { BrowserDraftBackupDestination("fixture.json").write("private") { false } }
                assertEquals("0", browserBackupFixtureValue("created"))
                var checks = 0
                assertFails { BrowserDraftBackupDestination("fixture.json").write("private") { ++checks < 3 } }
                assertEquals(3, checks)
                assertEquals("1", browserBackupFixtureValue("created"))
                assertEquals("1", browserBackupFixtureValue("revoked"))
                assertEquals("0", browserBackupFixtureValue("clicks"))
                assertEquals("0", browserBackupFixtureValue("urls"))
                assertEquals("0", browserBackupFixtureValue("timers"))
                assertEquals("0", browserBackupFixtureValue("anchors"))
            } finally {
                browserBackupFixtureRestore()
            }
        }

    @Test fun dispatchFailureAndClosedDestinationDoNotLeakOrRetry() =
        runTest {
            browserBackupFixtureInstall()
            try {
                val closed = BrowserDraftBackupDestination("fixture.json")
                closed.close()
                assertFails { closed.write("private") { true } }
                assertEquals("0", browserBackupFixtureValue("created"))
                browserBackupFixtureFailClick()
                val blocked = BrowserDraftBackupDestination("fixture.json")
                assertFails { blocked.write("private") { true } }
                assertFails { blocked.write("private") { true } }
                assertEquals("1", browserBackupFixtureValue("created"))
                assertEquals("1", browserBackupFixtureValue("revoked"))
                assertEquals("0", browserBackupFixtureValue("clicks"))
                assertEquals("0", browserBackupFixtureValue("urls"))
                assertEquals("0", browserBackupFixtureValue("timers"))
                assertEquals("0", browserBackupFixtureValue("anchors"))
            } finally {
                browserBackupFixtureRestore()
            }
        }
}

internal expect fun browserBackupFixtureInstall()

internal expect fun browserBackupFixtureRestore()

internal expect fun browserBackupFixtureExpire()

internal expect fun browserBackupFixtureFailClick()

internal expect fun browserBackupFixtureValue(field: String): String

internal expect fun browserBackupFixtureText(done: (String) -> Unit)
