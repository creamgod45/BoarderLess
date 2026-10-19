package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.WorkspaceId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class BrowserLocalAssetCatalogTest {
    private val asset = WorkspaceAsset("00112233-4455-6677-8899-aabbccddeeff", WorkspaceId("local-test"),
        "owner", "image/png", 10, "sha256:" + "a".repeat(64), 32, 24, null,
        AssetStatus.Pending, "2026-10-10T00:00:00Z")

    @Test fun pendingAndVerifiedReadyReopenAndSameIdentityRetryDoesNotDuplicate() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val catalog = browserLocalAssetCatalog()
            catalog.add(asset); catalog.add(asset)
            assertEquals(asset, catalog.get(asset.id))
            val ready = catalog.ready(asset)
            assertEquals(AssetStatus.Ready, ready.status)
            browserRecordFixtureReopen()
            assertEquals(ready, browserLocalAssetCatalog().get(asset.id))
            assertEquals(ready, browserLocalAssetCatalog().ready(asset))
            assertFails { browserLocalAssetCatalog().abandon(asset) }
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun unknownConfirmationReopensPendingAndRetryConfirmsExactAsset() = runTest {
        browserRecordFixtureInstall("abortConfirmation")
        try {
            assertFails { browserLocalAssetCatalog().add(asset) }
            browserRecordFixtureReopen()
            val catalog = browserLocalAssetCatalog()
            assertEquals(asset, catalog.get(asset.id))
            assertFails { catalog.add(asset.copy(byteSize = 11)) }
            catalog.add(asset)
            catalog.abandon(asset)
            assertFailsWith<NoSuchElementException> { catalog.get(asset.id) }
        } finally { browserRecordFixtureRestore() }
    }
    @Test fun cancellationOfPutAndStorageDenialNeverPublishAsset() = runTest {
        browserRecordFixtureInstall("hold")
        try {
            val put = CompletableDeferred<Unit>()
            browserRecordFixtureWaitForPut { put.complete(Unit) }
            val work = async { browserLocalAssetCatalog().add(asset) }
            put.await(); assertFalse(work.isCompleted)
            assertEquals("0", browserRecordFixtureValue("acknowledged"))
            work.cancelAndJoin(); browserRecordFixtureReopen()
            assertFailsWith<NoSuchElementException> { browserLocalAssetCatalog().get(asset.id) }
        } finally { browserRecordFixtureRestore() }
        browserRecordFixtureInstall("denied")
        try { assertFails { browserLocalAssetCatalog().add(asset) } }
        finally { browserRecordFixtureRestore() }
    }
    @Test fun competingCatalogsNeverOverwriteAcknowledgedAssetAndInvalidCatalogCannotReset() = runTest {
        browserRecordFixtureInstall("allow")
        try {
            val other = asset.copy(id = "11223344-5566-7788-99aa-bbccddeeff00")
            supervisorScope {
                val a = async { runCatching { browserLocalAssetCatalog().add(asset) } }
                val b = async { runCatching { browserLocalAssetCatalog().add(other) } }
                val outcomes = listOf(asset to a.await(), other to b.await())
                for ((saved, result) in outcomes) if (result.isSuccess)
                    assertEquals(saved, browserLocalAssetCatalog().get(saved.id))
            }
            val files = BrowserDurableLocalRecordStore(BrowserRecordScope.AssetCatalog)
            val key = PersistentLocalAssetCatalog.Key
            val old = assertNotNull(files.read(key))
            files.compareAndSet(key, old.generation, "{invalid}".encodeToByteArray())
            assertFails { browserLocalAssetCatalog().get(asset.id) }
            assertFails { browserLocalAssetCatalog().add(other) }
            assertContentEquals("{invalid}".encodeToByteArray(), files.read(key)?.payload)
        } finally { browserRecordFixtureRestore() }
    }
}
