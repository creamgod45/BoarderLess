package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.data.persistence.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.media.*
import cg.creamgod.boarderless.domain.model.*
import cg.creamgod.boarderless.feature.canvas.*
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class DesktopExternalMediaSchemeTest {
    @Test fun groupedReferenceTransfersWithoutMediaAndPastesWithRemappedRelationsAfterReopen() = runTest {
        val root = Files.createTempDirectory("boarderless-reference-scheme")
        try {
            val sourceRepo = DesktopFileWorkspaceRepository(root.resolve("source"))
            val owner = sourceRepo.openOrCreateWorkspace()
            val group = GroupFrame(CanvasObjectId("group"), transform = CanvasTransform(Vec2(0f, 0f), CanvasSize(600f, 600f)))
            val gif = ExternalMediaNode(CanvasObjectId("gif"), parentId = group.id,
                transform = CanvasTransform(Vec2(20f, 30f), CanvasSize(240f, 360f), 25f),
                reference = ExternalMediaReference(ExternalMediaProvider.Giphy, "gif-id"), altText = "動畫🙂")
            val text = TextNode(CanvasObjectId("text"), parentId = group.id,
                transform = CanvasTransform(Vec2(300f, 30f), CanvasSize(120f, 80f)), text = "分支")
            val edge = Relation(RelationId("edge"), sourceObjectId = gif.id, targetObjectId = text.id, label = "連線", direction = RelationDirection.Forward)
            val create = TransactionOperation("create-group", listOf(CreateObjectsOperation("objects", listOf(group, gif, text)), CreateRelationsOperation("edges", listOf(edge))))
            sourceRepo.retainDraft(owner, owner.workspace, create)
            val opened = sourceRepo.refresh(owner)
            val payload = assertNotNull(workspaceClipboardPayload(opened.workspace, setOf(group.id)))
            assertEquals(9, payload.version); assertEquals(1, payload.externalMedia.size)
            assertEquals(ClipboardPayloadIssue.UnsupportedVersion, validateClipboardPayload(payload.copy(version = 8)))
            assertEquals(ClipboardPayloadIssue.InvalidParent, validateClipboardPayload(payload.copy(externalMedia = payload.externalMedia.map { it.copy(parentOriginalId = "missing") })))
            val saturated = opened.workspace.copy(objects = mapOf(text.id to text.copy(parentId = null, zIndex = Long.MAX_VALUE)), relations = emptyMap())
            assertFails { prepareClipboardInsertion(payload, saturated) }
            val sourceLibrary = assertNotNull(sourceRepo.quickSchemeRepository)
            val scheme = sourceLibrary.save(ClipboardJson.encodeToString(ClipboardPayload.serializer(), payload), "引用方案", 9)
            val json = QuickSchemeTransferCodec.encode(scheme)
            assertFalse(json.contains("https://")); assertFalse(json.contains("api_key"))
            assertEquals(payload, decodeQuickSchemePayload(QuickSchemeTransferCodec.decode(json)))
            val bundle = root.resolve("reference.blscheme")
            val download = assertNotNull(sourceRepo.localAssetGateway) as AssetDownloadGateway
            var assetRequests = 0
            val noOriginals = object : AssetDownloadGateway by download {
                override suspend fun authorize(session: WorkspaceSession, assetId: String): AssetDownloadTicket {
                    assetRequests++; error("provider reference must not download an original")
                }
            }
            QuickSchemeBundleCodec.export(scheme, opened, noOriginals, DesktopSchemeBundleDestination(bundle) { true }, { true }, { scheme })
            assertEquals(0, assetRequests)
            val review = QuickSchemeBundleCodec.review(DesktopSchemeBundleSource.snapshot(bundle) { true }) { true }
            try {
                assertTrue(review.assets.isEmpty())
                val destinationRepo = DesktopFileWorkspaceRepository(root.resolve("destination"))
                val destination = destinationRepo.openOrCreateWorkspace()
                val prepared = QuickSchemeBundleCodec.materialize(review, destination, assertNotNull(destinationRepo.localAssetGateway),
                    { true }, { destinationRepo.refresh(destination) }, { _, _ -> error("no original receipt") },
                    { error("no original recovery") }, { _, _ -> error("no original ready") })
                val saved = saveMaterializedBundle(assertNotNull(destinationRepo.quickSchemeRepository), prepared)
                val reopened = DesktopFileWorkspaceRepository(root.resolve("destination"))
                val restored = reopened.openOrCreateWorkspace()
                val received = assertNotNull(decodeQuickSchemePayload(assertNotNull(reopened.quickSchemeRepository).list().single()))
                assertEquals(saved, reopened.quickSchemeRepository!!.list().single())
                val insertion = prepareClipboardInsertion(received, restored.workspace)
                val history = WorkspaceHistory(restored.workspace).execute(insertion.operation)
                assertTrue(history.succeeded)
                reopened.retainDraft(restored, restored.workspace, insertion.operation)
                val final = DesktopFileWorkspaceRepository(root.resolve("destination")).openOrCreateWorkspace()
                val inserted = final.workspace.objects.values.filterIsInstance<ExternalMediaNode>().single()
                assertNotEquals(gif.id, inserted.id); assertEquals(gif.reference, inserted.reference)
                assertNotEquals(group.id, inserted.parentId)
                assertIs<GroupFrame>(final.workspace.objects.getValue(assertNotNull(inserted.parentId)))
                val relation = final.workspace.relations.values.single()
                assertEquals(inserted.id, relation.sourceObjectId); assertEquals("連線", relation.label)
                assertTrue(final.workspace.objects.getValue(relation.targetObjectId) is TextNode)
                assertTrue(reopened.listAssets(final).isEmpty())
                val undo = history.history.undo(); assertTrue(undo.succeeded); assertTrue(undo.history.workspace.objects.isEmpty())
                assertEquals(3, undo.history.redo().history.workspace.objects.size)
            } finally { review.release() }
        } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}
