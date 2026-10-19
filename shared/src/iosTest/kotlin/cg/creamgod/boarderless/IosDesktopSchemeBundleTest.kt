@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.MediaNode
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import platform.Foundation.*
import platform.posix.memcpy
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.test.*

/** Actual Desktop export, not a hand-built container. SHA-256: bfd0087c7c3f916807e989dc2c7a925f8d02cdc9f38c8795d92e9ef07fea3016 */
class IosDesktopSchemeBundleTest {
    private val desktopBundle = Base64.decode("QkxRUzAwMDEAAANIeyJmb3JtYXQiOiJib2FyZGVybGVzcy5zY2hlbWUtYnVuZGxlIiwidmVyc2lvbiI6MSwic2NoZW1lIjoie1wiZm9ybWF0XCI6XCJib2FyZGVybGVzcy5xdWljay1zY2hlbWVcIixcInNjaGVtYVZlcnNpb25cIjoxLFwibmFtZVwiOlwi5Y6f5qqU8J+ZglwiLFwic2VsZWN0aW9uXCI6e1wiZm9ybWF0XCI6XCJib2FyZGVybGVzcy9zZWxlY3Rpb25cIixcInZlcnNpb25cIjo2LFwibm9kZXNcIjpbXSxcImdyb3Vwc1wiOltdLFwibWVkaWFcIjpbe1wib3JpZ2luYWxJZFwiOlwicGhvdG9cIixcInhcIjowLjAsXCJ5XCI6MC4wLFwid2lkdGhcIjoxMDAuMCxcImhlaWdodFwiOjgwLjAsXCJyb3RhdGlvbkRlZ3JlZXNcIjowLjAsXCJhc3NldElkXCI6XCI3YzQwZWQ3NC00ZDUxLTQ1ZjMtODY3Yi04NjZlYWYxNTg5ZWJcIixcIm1lZGlhS2luZFwiOlwiaW1hZ2VcIixcImFsdFRleHRcIjpcIlwiLFwidGh1bWJuYWlsQXNzZXRJZFwiOm51bGwsXCJwYXJlbnRPcmlnaW5hbElkXCI6bnVsbCxcImxvY2tlZFwiOmZhbHNlLFwiek9mZnNldFwiOjB9XSxcInJlbGF0aW9uc1wiOltdfSxcInNvdXJjZVdvcmtzcGFjZUlkXCI6XCJmNTA4NjZmMC01YjljLTQ4ZjYtYTJiZC00ZjQ4ODM3NmEzMGVcIn0iLCJhc3NldHMiOlt7InNvdXJjZUlkIjoiN2M0MGVkNzQtNGQ1MS00NWYzLTg2N2ItODY2ZWFmMTU4OWViIiwibWVkaWFUeXBlIjoiaW1hZ2UvcG5nIiwiYnl0ZVNpemUiOjgxLCJjaGVja3N1bSI6InNoYTI1Njo2N2RhMzAwYzE0MWU1Yjc5OWI5YmJkMmM2MDdmMjcxMGY2Mjg3YTEwMWU5YjI1ZWJiOWYxZWMzMzY1NjE4MTBiIiwid2lkdGgiOjMyLCJoZWlnaHQiOjI0LCJkdXJhdGlvbk1zIjpudWxsfV19iVBORw0KGgoAAAANSUhEUgAAACAAAAAYCAIAAAAUMWhjAAAAGElEQVR4Xu3BAQ0AAADCoPdP7ewBFAAANwkYAAETG/muAAAAAElFTkSuQmCCVExc2KYQV+byBuhnSU7f5ySKUBgzQWYN55mmDsrKZKE=")
    private fun bytes(path: String): ByteArray {
        val data = checkNotNull(NSFileManager.defaultManager.contentsAtPath(path))
        return ByteArray(data.length.toInt()).also { result ->
            if (result.isNotEmpty()) result.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        }
    }

    @Test fun desktopOriginalImportsIntoNativeLibraryAndCanvasWithSameReceiptAfterInterruption() = runBlocking {
        val parent = checkNotNull(NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByResolvingSymlinksInPath?.path).trimEnd('/')
        val root = "$parent/boarderless-desktop-bundle-${NSUUID().UUIDString}"
        check(NSFileManager.defaultManager.createDirectoryAtPath(root, true, null, null))
        try {
            assertEquals("bfd0087c7c3f916807e989dc2c7a925f8d02cdc9f38c8795d92e9ef07fea3016", SHA256().digest(desktopBundle).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') })
            val selected = "$root/desktop.blscheme"
            val data = desktopBundle.usePinned { NSData.create(bytes = it.addressOf(0), length = desktopBundle.size.toULong()) }
            check(NSFileManager.defaultManager.createFileAtPath(selected, data, null))
            val review = QuickSchemeBundleCodec.review(IosSchemeBundleSource.snapshot(NSURL.fileURLWithPath(selected)) { true }) { true }
            try {
                check(NSFileManager.defaultManager.removeItemAtPath(selected, null))
                assertEquals("原檔🙂", review.scheme.name)
                val sourceAsset = review.assets.single()
                assertEquals(32, sourceAsset.width); assertEquals(24, sourceAsset.height)
                val expectedOriginal = desktopBundle.copyOfRange(review.offsets.single().toInt(), review.offsets.single().toInt() + sourceAsset.byteSize.toInt())
                val initial = IosFileWorkspaceRepository("$root/documents")
                val owner = initial.openOrCreateWorkspace()
                val digest = review.digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
                fun receipts() = PersistentSchemeBundleImportReceipts(IosAtomicLocalRecordStore("$root/receipts"), owner, digest, review.assets)
                val firstLedger = receipts()
                val interrupted = assertFails {
                    QuickSchemeBundleCodec.materialize(review, owner, initial.localAssetGateway, { true }, { initial.refresh(owner) },
                        { _, id -> firstLedger.beforeComplete(sourceAsset, id) },
                        { firstLedger.recovered(it, initial) },
                        { _, _ -> error("interrupted before scheme save") })
                }
                assertEquals("interrupted before scheme save", interrupted.message)
                val readyId = initial.listAssets(owner).single().id
                assertNotEquals(sourceAsset.sourceId, readyId)
                val reopened = IosFileWorkspaceRepository("$root/documents")
                val ledger = receipts()
                val prepared = QuickSchemeBundleCodec.materialize(review, owner, reopened.localAssetGateway, { true }, { reopened.refresh(owner) },
                    { _, _ -> error("must reuse durable imported identity") },
                    { ledger.recovered(it, reopened) },
                    { _, media -> ledger.ready(sourceAsset, media) })
                assertEquals(1, reopened.listAssets(owner).size)
                val library = IosFileQuickSchemeStore("$root/library")
                val saved = saveMaterializedBundle(library, prepared)
                assertEquals(saved, saveMaterializedBundle(IosFileQuickSchemeStore("$root/library"), prepared))
                val restoredScheme = IosFileQuickSchemeStore("$root/library").list().single()
                assertEquals(saved, restoredScheme)
                assertEquals(owner.workspace.id.value, restoredScheme.sourceWorkspaceId)
                val payload = assertNotNull(decodeQuickSchemePayload(restoredScheme))
                assertEquals(readyId, payload.media.single().assetId)
                val operation = prepareClipboardInsertion(payload, owner.workspace).operation
                reopened.retainDraft(owner, owner.workspace, operation)
                val restored = IosFileWorkspaceRepository("$root/documents").openOrCreateWorkspace()
                assertEquals(readyId, (restored.workspace.objects.values.single() as MediaNode).assetId)
                val sink = IosFileAssetDownloadSink.create(sourceAsset.mediaType)
                try {
                    val local = AssetDownloadCoordinator(reopened.localAssetGateway).download(restored, readyId, sink)
                    assertContentEquals(expectedOriginal, bytes(local.token))
                    val decoded = IosAssetPreviewLoader(reopened.localAssetGateway).load(restored, readyId)
                    assertEquals(32, decoded.width); assertEquals(24, decoded.height)
                } finally { sink.release() }
            } finally { review.release() }
        } finally { NSFileManager.defaultManager.removeItemAtPath(root, null) }
    }
}
