package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopSchemeBundleTest {
    @Test fun originalBundleTravelsToAnotherNativeRepositoryAndReceiptsSurviveRestart() = runTest {
        val root = Files.createTempDirectory("boarderless-bundle-test")
        try {
            val sourceRepo = DesktopFileWorkspaceRepository(root.resolve("source"))
            val sourceSession = sourceRepo.openOrCreateWorkspace()
            val path = root.resolve("original.png")
            assertTrue(ImageIO.write(BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), "png", path.toFile()))
            val originalBytes = Files.readAllBytes(path)
            val selected = JvmFileAssetTransferSource.fromPath(path.toString())
            val imported = AssetImportCoordinator(sourceRepo.localAssetGateway!!).import(sourceSession, selected)
            val payload = ClipboardPayload(nodes = emptyList(), media = listOf(
                ClipboardMedia("photo", 0f, 0f, 100f, 80f, 0f, imported.asset.id, "image")))
            val scheme = sourceRepo.quickSchemeRepository!!.save(ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload),
                "原檔🙂", 6, sourceSession.workspace.id.value)
            val bundlePath = root.resolve("transfer.boarderless")
            val output = DesktopSchemeBundleDestination(bundlePath) { true }
            QuickSchemeBundleCodec.export(scheme, sourceSession, sourceRepo.localAssetGateway!!, output,
                { true }, { sourceRepo.quickSchemeRepository!!.list().single() })
            // Explicit QA-only artifact export; normal test runs leave no fixture behind.
            System.getenv("BOARDERLESS_BUNDLE_GUI_FIXTURE")?.let { destination ->
                Files.copy(bundlePath, java.nio.file.Path.of(destination))
            }
            Files.delete(path)
            var review = QuickSchemeBundleCodec.review(DesktopSchemeBundleSource.snapshot(bundlePath) { true }) { true }
            val destinationRepo = DesktopFileWorkspaceRepository(root.resolve("destination"))
            val destination = destinationRepo.openOrCreateWorkspace()
            val digest = review.digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            fun receipts() = DesktopSchemeBundleImportReceipts(root.resolve("receipts"), destination, digest, review.assets)
            var ledger = receipts()
            var confirmations = 0
            val interrupted = assertFails {
                QuickSchemeBundleCodec.materialize(review, destination, destinationRepo.localAssetGateway!!,
                    { true }, { destinationRepo.refresh(destination) },
                    { sourceId, id -> ledger.beforeComplete(review.assets.single { it.sourceId == sourceId }, id) },
                    { ledger.recovered(it, destinationRepo) },
                    { _, _ -> confirmations++; error("library interrupted after Ready") })
            }
            assertEquals("library interrupted after Ready", interrupted.message, interrupted.stackTraceToString())
            assertEquals(1, confirmations)
            assertTrue(destinationRepo.quickSchemeRepository!!.list().isEmpty())
            assertTrue(destinationRepo.refresh(destination).workspace.objects.isEmpty())
            val firstId = destinationRepo.listAssets(destination).single().id
            review.release()
            review = QuickSchemeBundleCodec.review(DesktopSchemeBundleSource.snapshot(bundlePath) { true }) { true }
            ledger = receipts()
            val reopened = DesktopFileWorkspaceRepository(root.resolve("destination"))
            val remapped = QuickSchemeBundleCodec.materialize(review, destination, reopened.localAssetGateway!!,
                { true }, { reopened.refresh(destination) },
                { _, _ -> error("must reuse original receipt") }, { ledger.recovered(it, reopened) },
                { sourceId, media -> ledger.ready(review.assets.single { it.sourceId == sourceId }, media) })
            val saved = reopened.quickSchemeRepository!!.save(remapped.payload, remapped.name, remapped.schemaVersion, remapped.sourceWorkspaceId)
            val received = assertNotNull(decodeQuickSchemePayload(saved))
            assertEquals(firstId, received.media.single().assetId)
            assertEquals(1, reopened.listAssets(destination).size)
            val operation = prepareClipboardInsertion(received, destination.workspace).operation
            reopened.retainDraft(destination, destination.workspace, operation)
            val restored = DesktopFileWorkspaceRepository(root.resolve("destination")).openOrCreateWorkspace()
            assertEquals(firstId, (restored.workspace.objects.values.single() as MediaNode).assetId)
            val sink = JvmFileAssetDownloadSink.create(root.resolve("verify").toString(), firstId, "image/png")
            val local = AssetDownloadCoordinator(reopened.localAssetGateway!!).download(restored, firstId, sink)
            assertContentEquals(originalBytes, Files.readAllBytes(java.nio.file.Path.of(local.token)))
            assertEquals(32, ImageIO.read(java.nio.file.Path.of(local.token).toFile()).width)
            review.release()
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun destinationNeverOverwritesAndSnapshotOutlivesSelectedFile() = runTest {
        val root = Files.createTempDirectory("boarderless-bundle-files")
        try {
            val path = root.resolve("bundle")
            val data = ByteArray(80) { it.toByte() }
            val output = DesktopSchemeBundleDestination(path) { true }
            output.writeChunk(data)
            assertFalse(Files.exists(path))
            output.commit()
            assertContentEquals(data, Files.readAllBytes(path))
            val snapshot = DesktopSchemeBundleSource.snapshot(path) { true }
            Files.write(path, ByteArray(80))
            assertContentEquals(data, snapshot.readChunk(0, 80))
            val second = DesktopSchemeBundleDestination(path) { true }
            assertFails { second.writeChunk(data) }
            second.abort()
            assertContentEquals(ByteArray(80), Files.readAllBytes(path))
            snapshot.release()
            assertFails { snapshot.readChunk(0, 80) }
            val cancelledPath = root.resolve("cancelled")
            val cancelled = DesktopSchemeBundleDestination(cancelledPath) { true }
            cancelled.writeChunk(data)
            cancelled.abort()
            assertFalse(Files.exists(cancelledPath))
            assertTrue(Files.list(root).use { files -> files.noneMatch { it.fileName.toString().endsWith(".part") } })
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }
}
