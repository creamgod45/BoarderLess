package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlinx.serialization.json.*
import kotlin.test.*

class WorkspaceDraftReviewTest {
    private val transform = CanvasTransform(Vec2(15f, -20f), CanvasSize(200f, 120f), 45f)
    private val text = TextNode(CanvasObjectId("text"), transform = transform, text = "Private <script>text</script>", shape = NodeShape.Diamond)
    private val media = MediaNode(CanvasObjectId("media"), transform = transform, assetId = "asset", mediaKind = MediaKind.Video, thumbnailAssetId = "poster")
    private val relation = Relation(RelationId("text"), sourceObjectId = text.id, targetObjectId = media.id, intent = "Supports", label = "Edge")
    private val baseline = Workspace(WorkspaceId("workspace"), "Private board", 8,
        mapOf(text.id to text, media.id to media), mapOf(relation.id to relation))
    private fun review(proposed: Workspace, remote: Workspace = baseline) = WorkspaceDraftReview("draft", 8, 19, 8, 19,
        baseline, proposed, remote, listOf(CreateObjectsOperation("original-local-id", listOf(text))), true, true)

    @Test fun detectsAddedChangedRemovedObjectsAndRelationsWithoutConfusingMatchingContentWithAck() {
        val changed = text.copy(version = 2, text = "Draft", transform = transform.copy(rotationDegrees = 90f))
        val added = text.copy(id = CanvasObjectId("added"), text = "Added")
        val proposed = baseline.copy(objects = mapOf(changed.id to changed, added.id to added), relations = emptyMap())
        val remote = baseline.copy(objects = mapOf(text.id to text.copy(version = 2, text = "Remote"), added.id to added))
        val reviewed = review(proposed, remote)
        val diffs = reviewed.differences()
        assertEquals(listOf("object:added", "object:media", "object:text", "relation:text"), diffs.map { it.id })
        assertEquals(DraftRemoteComparison.MatchesDraft, diffs[0].remoteComparison)
        assertEquals(DraftRemoteComparison.MatchesDraft, diffs[1].remoteComparison)
        assertEquals(DraftRemoteComparison.Changed, diffs[2].remoteComparison)
        assertEquals(DraftRemoteComparison.Unchanged, diffs[3].remoteComparison)
        assertTrue(reviewed.hasUnconfirmedSubmission && reviewed.quarantined)
        assertEquals(baseline, reviewed.baseline)
        assertEquals(text, baseline.objects[text.id])
    }

    @Test fun unrelatedRemoteChangesStillBlockBaselineMatchAndMetadataTitleMayDiffer() {
        val proposed = baseline.copy(objects = baseline.objects + (text.id to text.copy(version = 2, text = "Draft")))
        val remote = baseline.copy(objects = baseline.objects + (media.id to media.copy(version = 2, altText = "Remote")))
        val reviewed = review(proposed, remote)
        assertEquals(listOf("object:text"), reviewed.differences().map { it.id })
        assertFalse(reviewed.baselineMatchesCurrent)
        assertTrue(review(baseline, baseline.copy(version = 50, title = "Renamed")).baselineMatchesCurrent)
        assertFalse(review(baseline).copy(currentVersion = 9).baselineMatchesCurrent)
        assertFalse(review(baseline).copy(currentServerSeq = 20).baselineMatchesCurrent)
    }

    @Test fun backupIsVersionedPrivateContentNotWireRequestAndRetainsMediaReferences() {
        val reviewed = review(baseline)
        val content = reviewed.toBackupJson()
        val document = Json.parseToJsonElement(content).jsonObject
        assertEquals("boarderless.workspace-draft-review", document.getValue("format").jsonPrimitive.content)
        assertEquals(1, document.getValue("schemaVersion").jsonPrimitive.int)
        assertFalse(document.getValue("importSupported").jsonPrimitive.boolean)
        assertTrue(document.getValue("hasUnconfirmedSubmission").jsonPrimitive.boolean)
        assertTrue(document.keys.none { it in setOf("scope", "apiBase", "userId", "clientId", "request", "transactionId", "ticket", "apiKey") })
        val json = Json { allowStructuredMapKeys = true }
        assertEquals(baseline, json.decodeFromJsonElement(Workspace.serializer(), document.getValue("baseline")))
        assertEquals(baseline, json.decodeFromJsonElement(Workspace.serializer(), document.getValue("proposed")))
        assertEquals(reviewed.operations, json.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(WorkspaceOperation.serializer()), document.getValue("operations")))
        assertTrue("asset" in content && "poster" in content)
        assertEquals(text.text, (json.decodeFromJsonElement(Workspace.serializer(), document.getValue("baseline")).objects[text.id] as TextNode).text)
    }

    @Test fun publicationRejectsScopeChangesAndLeavingThenReturningWithNewEpoch() {
        val opened = WorkspaceSession("user", "client", WorkspaceMemberRole.Editor, 8, 19, baseline)
        assertTrue(canPublishDraftReview(opened, opened, "epoch", "epoch", "draft", "draft"))
        val changed = listOf(opened.copy(userId = "other"), opened.copy(clientId = "other"),
            opened.copy(workspace = baseline.copy(id = WorkspaceId("other"))), null)
        changed.forEach { assertFalse(canPublishDraftReview(opened, it, "epoch", "epoch", "draft", "draft")) }
        assertFalse(canPublishDraftReview(opened, opened, "old", "returned", "draft", "draft"))
        assertFalse(canPublishDraftReview(opened, opened, "epoch", "epoch", "draft", "new-draft"))
        assertFalse(canPublishDraftReview(opened, opened, "", "", "draft", "draft"))
    }
}
