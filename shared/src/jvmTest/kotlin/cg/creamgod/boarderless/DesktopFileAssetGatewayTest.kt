package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.DesktopFileWorkspaceRepository
import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.mediaNodeFromReadyAsset
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopFileAssetGatewayTest {
    private fun temporary(block: (Path) -> Unit) {
        val root = Files.createTempDirectory("boarderless-local-media")
        try {
            block(root)
        } finally {
            Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private suspend fun source(root: Path): JvmFileAssetTransferSource {
        val path = root.resolve("中文🙂.png")
        val image = BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB)
        image.setRGB(10, 10, 0x3366ff)
        check(ImageIO.write(image, "png", path.toFile()))
        return JvmFileAssetTransferSource.fromPath(path.toString())
    }

    @Test fun realImportAndCanvasSaveReopenWithoutPickerOriginalAndDownloadThroughVerifiedSink() =
        temporary { root ->
            runBlocking {
                val repo = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val session = repo.openOrCreateWorkspace()
                val selected = source(root)
                val imported = AssetImportCoordinator(assertNotNull(repo.localAssetGateway)).import(session, selected)
                assertEquals(AssetStatus.Ready, imported.asset.status)
                val node =
                    mediaNodeFromReadyAsset(imported.asset, session.workspace.id, CanvasObjectId("image"), Vec2(200f, 200f), 1f, 1, "中文🙂")
                repo.retainDraft(session, session.workspace, CreateObjectsOperation("image-create", listOf(node)))
                Files.delete(root.resolve("中文🙂.png"))
                val reopened = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val restored = reopened.openOrCreateWorkspace()
                assertEquals(node, restored.workspace.objects.getValue(node.id))
                assertEquals(listOf(imported.asset), reopened.listAssets(restored))
                val gateway = assertNotNull(reopened.localAssetGateway)
                val sink =
                    JvmFileAssetDownloadSink.create(
                        root.resolve("preview-cache").toString(),
                        imported.asset.id,
                        imported.asset.mediaType,
                    )
                val local = AssetDownloadCoordinator(gateway).download(restored, imported.asset.id, sink)
                assertEquals(32, ImageIO.read(Path.of(local.token).toFile()).width)
                assertEquals(selected.checksum, JvmFileAssetTransferSource.fromPath(local.token).checksum)
                assertEquals(
                    imported.asset,
                    gateway.confirm(
                        restored,
                        AssetUploadTicket(imported.asset.copy(status = AssetStatus.Pending), "boarderless-local:${imported.asset.id}"),
                    ),
                )
            }
        }

    @Test fun interruptedConfirmationIsRecoveredByExplicitMetadataRefreshWithSameId() =
        temporary { root ->
            runBlocking {
                val repo = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val session = repo.openOrCreateWorkspace()
                val selected = source(root)
                val gateway = assertNotNull(repo.localAssetGateway)
                val ticket = gateway.prepare(session, selected)
                gateway.upload(ticket, selected) {}
                // Restart after original publication, before Ready metadata publication.
                val reopened = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val restored = reopened.openOrCreateWorkspace()
                assertEquals(AssetStatus.Pending, reopened.listAssets(restored).single().status)
                val recovered = reopened.getAsset(restored, ticket.asset.id)
                assertEquals(ticket.asset.copy(status = AssetStatus.Ready), recovered)
                assertEquals(listOf(recovered), reopened.listAssets(restored))
                assertEquals(recovered, reopened.getAsset(restored, ticket.asset.id))
            }
        }

    @Test fun foreignScopeAndCorruptOriginalNeverPublishPreview() =
        temporary { root ->
            runBlocking {
                val repo = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val first = repo.openOrCreateWorkspace()
                val imported = AssetImportCoordinator(assertNotNull(repo.localAssetGateway)).import(first, source(root))
                val second = repo.createWorkspace(first, "Other")
                val gateway = assertNotNull(repo.localAssetGateway)
                assertTrue(repo.listAssets(second).isEmpty())
                assertFails { gateway.authorize(second, imported.asset.id) }
                assertFails { gateway.authorize(first.copy(clientId = "foreign"), imported.asset.id) }
                assertFails { gateway.authorize(first, "../../escape") }
                val blob = root.resolve("documents/assets/${imported.asset.id}.original")
                val original = Files.readAllBytes(blob)
                original[original.lastIndex] = (original.last().toInt() xor 1).toByte()
                Files.write(blob, original)
                val sink = JvmFileAssetDownloadSink.create(root.resolve("cache").toString(), "invalid", "image/png")
                assertFails { AssetDownloadCoordinator(gateway).download(first, imported.asset.id, sink) }
                assertEquals(0L, Files.list(root.resolve("cache")).use { it.count() })
                assertEquals(AssetStatus.Ready, repo.getAsset(first, imported.asset.id).status)
            }
        }

    @Test fun changedOrTruncatedSourceIsAbandonedWithoutReadyMetadataOrOriginal() =
        temporary { root ->
            runBlocking {
                val repo = DesktopFileWorkspaceRepository(root.resolve("documents"))
                val session = repo.openOrCreateWorkspace()
                val selected = source(root)
                val corrupt =
                    object : AssetTransferSource by selected {
                        override suspend fun readChunk(
                            offset: Long,
                            maximumBytes: Int,
                        ): ByteArray =
                            selected.readChunk(offset, maximumBytes).also {
                                if (it.isNotEmpty()) {
                                    it[0] =
                                        (it[0].toInt() xor 1).toByte()
                                }
                            }
                    }
                val gateway = assertNotNull(repo.localAssetGateway)
                assertFails { AssetImportCoordinator(gateway).import(session, corrupt) }
                assertTrue(repo.listAssets(session).isEmpty())
                val truncated =
                    object : AssetTransferSource by selected {
                        override suspend fun readChunk(
                            offset: Long,
                            maximumBytes: Int,
                        ) = byteArrayOf()
                    }
                assertFails { AssetImportCoordinator(gateway).import(session, truncated) }
                assertTrue(repo.listAssets(session).isEmpty())
                assertTrue(
                    Files.list(root.resolve("documents/assets")).use { paths ->
                        paths.noneMatch {
                            it.fileName.toString().endsWith(".original") ||
                                it.fileName.toString().endsWith(".part")
                        }
                    },
                )
            }
        }
}
