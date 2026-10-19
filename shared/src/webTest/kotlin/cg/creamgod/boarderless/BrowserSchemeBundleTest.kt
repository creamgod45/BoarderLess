package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.QuickScheme
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.test.*

/** Production adapter with actual browser OPFS; intercepted clicks do not prove OS picker/save UI. */
class BrowserSchemeBundleTest {
    @Test fun fullOriginalBundleExportsToDownloadThenImportsFromPrivateSnapshot() = runTest {
        browserBundleFixtureInstall("normal")
        try {
            val bytes = ByteArray(33 * 1024 * 1024 + 79) { (it % 251).toByte() }
            val checksum = "sha256:" + SHA256().digest(bytes).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            val session = WorkspaceSession("owner", "client", WorkspaceMemberRole.Owner, 0, 0, Workspace(WorkspaceId("source"), "Source"))
            val asset = WorkspaceAsset("old_asset", session.workspace.id, "owner", "image/gif", bytes.size.toLong(), checksum, 10, 12, 200, AssetStatus.Ready, "now")
            val payload = ClipboardPayload(nodes = emptyList(), media = listOf(ClipboardMedia("m1", 0f, 0f, 100f, 100f, 0f, asset.id, "gif")))
            val scheme = QuickScheme(1, "原始動畫🙂", ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), 6, "source")
            val gateway = object : AssetDownloadGateway {
                override suspend fun authorize(session: WorkspaceSession, assetId: String) = AssetDownloadTicket(asset, "opaque:ticket")
                override suspend fun download(ticket: AssetDownloadTicket, onChunk: suspend (ByteArray) -> Unit) {
                    for (offset in bytes.indices step QuickSchemeBundleCodec.ChunkBytes) {
                        onChunk(bytes.copyOfRange(offset, minOf(offset + QuickSchemeBundleCodec.ChunkBytes, bytes.size)))
                    }
                }
            }
            val runtime = browserSchemeBundleRuntime()
            assertTrue(runtime.usesBrowserDownload); assertNotNull(runtime.receipts)
            val destination = assertNotNull(runtime.chooseDestination!!("boarderless-scheme.blscheme") { true })
            QuickSchemeBundleCodec.export(scheme, session, gateway, destination, { true }, { scheme })
            assertEquals("1", browserBundleFixtureValue("downloads"))
            assertFails { destination.commit() }
            destination.abort() // An uncertain/cancelled published download must keep its backing file.
            assertEquals("2", audit())
            browserBundleFixtureSelectDownload()
            val source = assertNotNull(runtime.chooseSource!! { true })
            // Change the selected file after its private snapshot was created.
            suspendCancellableCoroutine<Unit> { pending -> browserBundleFixtureMutateDownload { pending.resume(Unit) } }
            val review = QuickSchemeBundleCodec.review(source) { true }
            assertEquals(checksum, review.assets.single().checksum)
            assertEquals(bytes.size.toLong(), review.assets.single().byteSize)
            assertEquals(scheme.payload, review.scheme.payload)
            review.release()
            assertEquals("2", audit()) // Published download and marker still hold their lease.
            destination.acknowledgeDownload(); destination.acknowledgeDownload()
            assertEquals("0", audit())
            assertFails { source.readChunk(0, 45) }
        } finally { restore() }
    }

    @Test fun scopeRevokedDuringActualWritePreventsDownloadAndRemovesOwnedStage() = runTest {
        browserBundleFixtureInstall("hold")
        try {
            var allowed = true
            val destination = assertNotNull(browserSchemeBundleRuntime().chooseDestination!!("scheme.blscheme") { allowed })
            val written = CompletableDeferred<Unit>(); browserBundleFixtureWaitForWrite { written.complete(Unit) }
            supervisorScope {
                val write = async { runCatching { destination.writeChunk(ByteArray(100) { 23 }) } }
                written.await(); allowed = false; browserBundleFixtureReleaseWrite()
                assertTrue(write.await().isFailure)
            }
            assertFails { destination.commit() }; destination.abort()
            assertEquals("0", browserBundleFixtureValue("downloads")); assertEquals("0", audit())
        } finally { restore() }
    }

    @Test fun cancelDuringActualWriteAndPickerCancelLeaveNoPublishedFile() = runTest {
        browserBundleFixtureInstall("hold")
        try {
            val destination = assertNotNull(browserSchemeBundleRuntime().chooseDestination!!("scheme.blscheme") { true })
            val written = CompletableDeferred<Unit>(); browserBundleFixtureWaitForWrite { written.complete(Unit) }
            val work = launch { destination.writeChunk(ByteArray(100) { 23 }) }
            written.await(); work.cancelAndJoin(); browserBundleFixtureReleaseWrite()
            destination.abort()
            assertEquals("0", browserBundleFixtureValue("downloads")); assertEquals("0", audit())
        } finally { restore() }
        browserBundleFixtureInstall("cancel")
        try { assertNull(browserSchemeBundleRuntime().chooseSource!! { true }); assertEquals("0", audit()) }
        finally { restore() }
    }

    @Test fun cancelledSourceCopyCleansLatePrivateSnapshotAndLeavesSelectedFileUntouched() = runTest {
        browserBundleFixtureInstall("hold")
        try {
            val written = CompletableDeferred<Unit>(); browserBundleFixtureWaitForWrite { written.complete(Unit) }
            val work = launch { browserSchemeBundleRuntime().chooseSource!! { true } }
            written.await(); work.cancelAndJoin(); browserBundleFixtureReleaseWrite()
            suspendCancellableCoroutine<Unit> { pending -> browserBundleFixtureWaitForIdle { pending.resume(Unit) } }
            assertEquals("0", audit()); assertEquals("0", browserBundleFixtureValue("downloads"))
            // The same selected File is still readable after the cancelled private copy.
            val source = assertNotNull(browserSchemeBundleRuntime().chooseSource!! { true })
            assertContentEquals(ByteArray(100) { 17 }, source.readChunk(0, 100))
            source.release(); assertEquals("0", audit())
        } finally { restore() }
    }

    @Test fun realWorkerLeaseProtectsActiveFilesAndTerminationAllowsIdleRecovery() = runTest {
        browserBundleFixtureInstall("normal")
        try {
            val result = suspendCancellableCoroutine<String> { pending ->
                browserBundleFixtureExerciseLeases { value, error ->
                    if (pending.isActive) {
                        if (error != null) pending.resumeWith(kotlin.Result.failure(IllegalStateException("Lease fixture failed")))
                        else pending.resume(requireNotNull(value))
                    }
                }
            }
            assertEquals("protected,reclaimed,foreign-preserved", result)
        } finally { restore() }
    }

    @Test fun cleanupFailureNeverAcknowledgesDeletionAndExplicitRetryRemovesSameStage() = runTest {
        browserBundleFixtureInstall("normal")
        try {
            val destination = assertNotNull(browserSchemeBundleRuntime().chooseDestination!!("scheme.blscheme") { true })
            destination.writeChunk(ByteArray(100) { 17 }); destination.commit()
            browserBundleFixtureFailNextRemoval()
            assertFails { destination.acknowledgeDownload() }
            assertEquals("2", audit())
            destination.acknowledgeDownload()
            assertEquals("0", audit()); assertEquals("1", browserBundleFixtureValue("downloads"))
        } finally { restore() }
    }

    private suspend fun audit(): String = suspendCancellableCoroutine { pending ->
        browserBundleFixtureAudit { count, error ->
            if (pending.isActive) { check(error == null); pending.resume(requireNotNull(count)) }
        }
    }
    private suspend fun restore() = withContext(NonCancellable) {
        suspendCancellableCoroutine<Unit> { pending -> browserBundleFixtureRestore { pending.resume(Unit) } }
    }
}
internal expect fun browserBundleFixtureInstall(mode: String)
internal expect fun browserBundleFixtureSelectDownload()
internal expect fun browserBundleFixtureValue(name: String): String
internal expect fun browserBundleFixtureWaitForWrite(done: () -> Unit)
internal expect fun browserBundleFixtureReleaseWrite()
internal expect fun browserBundleFixtureAudit(done: (String?, String?) -> Unit)
internal expect fun browserBundleFixtureRestore(done: () -> Unit)

internal expect fun browserBundleFixtureMutateDownload(done: () -> Unit)

internal expect fun browserBundleFixtureWaitForIdle(done: () -> Unit)

internal expect fun browserBundleFixtureExerciseLeases(done: (String?, String?) -> Unit)

internal expect fun browserBundleFixtureFailNextRemoval()
