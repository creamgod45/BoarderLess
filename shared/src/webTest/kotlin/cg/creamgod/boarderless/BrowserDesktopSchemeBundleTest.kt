package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.WorkspaceHistory
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Frozen actual Desktop export, also used by the iOS real-file import regression. */
class BrowserDesktopSchemeBundleTest {
    private val encodedDesktopBundle = "QkxRUzAwMDEAAANIeyJmb3JtYXQiOiJib2FyZGVybGVzcy5zY2hlbWUtYnVuZGxlIiwidmVyc2lvbiI6MSwic2NoZW1lIjoie1wiZm9ybWF0XCI6XCJib2FyZGVybGVzcy5xdWljay1zY2hlbWVcIixcInNjaGVtYVZlcnNpb25cIjoxLFwibmFtZVwiOlwi5Y6f5qqU8J+ZglwiLFwic2VsZWN0aW9uXCI6e1wiZm9ybWF0XCI6XCJib2FyZGVybGVzcy9zZWxlY3Rpb25cIixcInZlcnNpb25cIjo2LFwibm9kZXNcIjpbXSxcImdyb3Vwc1wiOltdLFwibWVkaWFcIjpbe1wib3JpZ2luYWxJZFwiOlwicGhvdG9cIixcInhcIjowLjAsXCJ5XCI6MC4wLFwid2lkdGhcIjoxMDAuMCxcImhlaWdodFwiOjgwLjAsXCJyb3RhdGlvbkRlZ3JlZXNcIjowLjAsXCJhc3NldElkXCI6XCI3YzQwZWQ3NC00ZDUxLTQ1ZjMtODY3Yi04NjZlYWYxNTg5ZWJcIixcIm1lZGlhS2luZFwiOlwiaW1hZ2VcIixcImFsdFRleHRcIjpcIlwiLFwidGh1bWJuYWlsQXNzZXRJZFwiOm51bGwsXCJwYXJlbnRPcmlnaW5hbElkXCI6bnVsbCxcImxvY2tlZFwiOmZhbHNlLFwiek9mZnNldFwiOjB9XSxcInJlbGF0aW9uc1wiOltdfSxcInNvdXJjZVdvcmtzcGFjZUlkXCI6XCJmNTA4NjZmMC01YjljLTQ4ZjYtYTJiZC00ZjQ4ODM3NmEzMGVcIn0iLCJhc3NldHMiOlt7InNvdXJjZUlkIjoiN2M0MGVkNzQtNGQ1MS00NWYzLTg2N2ItODY2ZWFmMTU4OWViIiwibWVkaWFUeXBlIjoiaW1hZ2UvcG5nIiwiYnl0ZVNpemUiOjgxLCJjaGVja3N1bSI6InNoYTI1Njo2N2RhMzAwYzE0MWU1Yjc5OWI5YmJkMmM2MDdmMjcxMGY2Mjg3YTEwMWU5YjI1ZWJiOWYxZWMzMzY1NjE4MTBiIiwid2lkdGgiOjMyLCJoZWlnaHQiOjI0LCJkdXJhdGlvbk1zIjpudWxsfV19iVBORw0KGgoAAAANSUhEUgAAACAAAAAYCAIAAAAUMWhjAAAAGElEQVR4Xu3BAQ0AAADCoPdP7ewBFAAANwkYAAETG/muAAAAAElFTkSuQmCCVExc2KYQV+byBuhnSU7f5ySKUBgzQWYN55mmDsrKZKE="
    private val bytes get() = Base64.decode(encodedDesktopBundle)
    @Test fun desktopOriginalBundleImportsThroughPrivateBrowserSnapshotAndReopensLibraryDocumentAndPixels() = runTest {
        browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("allow"); browserBundleFixtureInstall("normal")
        var review: ReviewedSchemeBundle? = null
        try {
            assertEquals("bfd0087c7c3f916807e989dc2c7a925f8d02cdc9f38c8795d92e9ef07fea3016", hash(bytes))
            browserBundleFixtureSelectBytes(encodedDesktopBundle)
            val runtime = browserSchemeBundleRuntime()
            val source = assertNotNull(assertNotNull(runtime.chooseSource).invoke { true })
            val selected = QuickSchemeBundleCodec.review(source) { true }; review = selected
            assertEquals("原檔🙂", selected.scheme.name)
            val original = selected.assets.single()
            val expected = bytes.copyOfRange(selected.offsets.single().toInt(), selected.offsets.single().toInt() + original.byteSize.toInt())
            val repo = browserLocalWorkspaceRepository(); val owner = repo.openOrCreateWorkspace()
            val ledger = assertNotNull(runtime.receipts).invoke(owner, hash(selected.digest, digestAlready = true), selected.assets)
            val interrupted = assertFails {
                QuickSchemeBundleCodec.materialize(selected, owner, assertNotNull(repo.localAssetGateway), { true }, { repo.refresh(owner) },
                    { _, id -> ledger.beforeComplete(original, id) }, { ledger.recovered(it, repo) },
                    { _, _ -> error("interrupted before scheme publication") })
            }
            assertEquals("interrupted before scheme publication", interrupted.message)
            val readyId = repo.listAssets(owner).single().id
            assertNotEquals(original.sourceId, readyId)
            repo.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository(); val current = reopened.openOrCreateWorkspace()
            val receipts = assertNotNull(browserSchemeBundleRuntime().receipts).invoke(current, hash(selected.digest, digestAlready = true), selected.assets)
            val prepared = QuickSchemeBundleCodec.materialize(selected, current, assertNotNull(reopened.localAssetGateway), { true }, { reopened.refresh(current) },
                { _, _ -> error("must not allocate or upload again") }, { receipts.recovered(it, reopened) },
                { _, imported -> receipts.ready(original, imported) })
            assertEquals(1, reopened.listAssets(current).size)
            val library = browserQuickSchemeRepository()
            val saved = saveMaterializedBundle(library, prepared)
            assertEquals(saved, saveMaterializedBundle(browserQuickSchemeRepository(), prepared))
            browserRecordFixtureReopen()
            val scheme = browserQuickSchemeRepository().list().single()
            assertEquals(saved, scheme)
            assertEquals(current.workspace.id.value, scheme.sourceWorkspaceId)
            val payload = assertNotNull(decodeQuickSchemePayload(scheme))
            assertEquals(readyId, payload.media.single().assetId)
            val operation = prepareClipboardInsertion(payload, current.workspace).operation
            val created = WorkspaceHistory(current.workspace).execute(operation)
            reopened.retainDraftAwaited(current, current.workspace, operation)
            val restoredRepo = browserLocalWorkspaceRepository(); var restored = restoredRepo.openOrCreateWorkspace()
            assertEquals(created.history.workspace, restored.workspace)
            assertEquals(readyId, (restored.workspace.objects.values.single() as MediaNode).assetId)
            val gateway = assertIs<AssetDownloadGateway>(restoredRepo.localAssetGateway)
            val ticket = gateway.authorize(restored, readyId)
            var downloaded: ByteArray? = null
            gateway.download(ticket) { downloaded = it }
            assertContentEquals(expected, downloaded)
            val mediaRuntime = browserMediaImportRuntime(localGateway = gateway)
            val image = assertNotNull(mediaRuntime.loadPreview).invoke(restored, readyId)
            assertEquals(32, image.width); assertEquals(24, image.height)
            val undone = created.history.undo()
            restoredRepo.retainDraftAwaited(restored, restored.workspace, assertNotNull(undone.appliedOperation))
            restored = restoredRepo.refresh(restored)
            assertTrue(restored.workspace.objects.isEmpty())
            val redone = undone.history.redo()
            restoredRepo.retainDraftAwaited(restored, restored.workspace, assertNotNull(redone.appliedOperation))
            val finalRepo = browserLocalWorkspaceRepository(); val final = finalRepo.openOrCreateWorkspace()
            assertEquals(redone.history.workspace, final.workspace)
            assertEquals(1, finalRepo.listAssets(final).size)
            // Re-export uses the original bytes, not the rendered preview.
            val export = assertNotNull(assertNotNull(runtime.chooseDestination).invoke("desktop-roundtrip.blscheme") { true })
            QuickSchemeBundleCodec.export(scheme, final, assertIs<AssetDownloadGateway>(finalRepo.localAssetGateway), export, { true }, { scheme })
            browserBundleFixtureSelectDownload()
            val secondSource = assertNotNull(assertNotNull(runtime.chooseSource).invoke { true })
            val roundtrip = QuickSchemeBundleCodec.review(secondSource) { true }
            try {
                assertEquals(original.checksum, roundtrip.assets.single().checksum)
                assertEquals(original.byteSize, roundtrip.assets.single().byteSize)
                assertContentEquals(expected, roundtrip.source.readChunk(roundtrip.offsets.single(), expected.size))
            } finally { roundtrip.release(); export.acknowledgeDownload() }
            finalRepo.close(); restoredRepo.close(); reopened.close()
        } finally {
            review?.release()
            withContext(NonCancellable) {
                suspendCancellableCoroutine<Unit> { waiter -> browserBundleFixtureRestore { waiter.resume(Unit) } }
            }
            browserOriginalFixtureRestore(); browserRecordFixtureRestore()
        }
    }
    @Test fun unknownReceiptAcknowledgementKeepsPendingOriginalAndReconcilesExactIdentity() = runTest {
        browserRecordFixtureInstall("allow"); browserOriginalFixtureInstall("allow"); browserBundleFixtureInstall("normal")
        var review: ReviewedSchemeBundle? = null
        try {
            browserBundleFixtureSelectBytes(encodedDesktopBundle)
            val runtime = browserSchemeBundleRuntime()
            val selected = QuickSchemeBundleCodec.review(assertNotNull(assertNotNull(runtime.chooseSource).invoke { true })) { true }
            review = selected
            val repo = browserLocalWorkspaceRepository(); val owner = repo.openOrCreateWorkspace()
            val original = selected.assets.single(); val digest = hash(selected.digest, digestAlready = true)
            val disk = BrowserDurableLocalRecordStore()
            var loseAck = true
            val ledger = PersistentSchemeBundleImportReceipts(object : AwaitedDurableLocalRecordStore {
                override suspend fun read(key: String) = disk.read(key)
                override suspend fun compareAndSet(key: String, generation: Long?, payload: ByteArray) {
                    disk.compareAndSet(key, generation, payload)
                    if (loseAck) { loseAck = false; error("Receipt committed, acknowledgement lost") }
                }
                override suspend fun confirmDurable(key: String, generation: Long) = disk.confirmDurable(key, generation)
            }, owner, digest, selected.assets)
            assertFails {
                QuickSchemeBundleCodec.materialize(selected, owner, assertNotNull(repo.localAssetGateway), { true }, { repo.refresh(owner) },
                    { _, id -> ledger.beforeComplete(original, id) }, { ledger.recovered(it, repo) },
                    { _, imported -> ledger.ready(original, imported) })
            }
            val pending = repo.listAssets(owner).single()
            assertEquals(AssetStatus.Pending, pending.status)
            assertEquals(BrowserOriginalState.Pending, BrowserOriginalStore().state(pending.id, pending.byteSize, pending.checksum))
            repo.close(); browserRecordFixtureReopen(); browserOriginalFixtureReopen()
            val reopened = browserLocalWorkspaceRepository(); val current = reopened.openOrCreateWorkspace()
            val restored = browserSchemeBundleReceipts(current, digest, selected.assets)
            val prepared = QuickSchemeBundleCodec.materialize(selected, current, assertNotNull(reopened.localAssetGateway), { true }, { reopened.refresh(current) },
                { _, _ -> error("must reuse durable receipt") }, { restored.recovered(it, reopened) },
                { _, imported -> restored.ready(original, imported) })
            assertEquals(pending.id, assertNotNull(decodeQuickSchemePayload(prepared)).media.single().assetId)
            assertEquals(listOf(pending.copy(status = AssetStatus.Ready)), reopened.listAssets(current))
            reopened.close()
        } finally {
            review?.release()
            withContext(NonCancellable) { suspendCancellableCoroutine<Unit> { waiter -> browserBundleFixtureRestore { waiter.resume(Unit) } } }
            browserOriginalFixtureRestore(); browserRecordFixtureRestore()
        }
    }
    private fun hash(value: ByteArray, digestAlready: Boolean = false): String = (if (digestAlready) value else SHA256().digest(value)).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0')
    }
}
internal expect fun browserBundleFixtureSelectBytes(base64: String)
