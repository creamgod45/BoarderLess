package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.history.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.test.*

class WorkspaceDraftMergeOperationsTest {
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(100f, 80f)), text = "Before")
    private val base = Workspace(WorkspaceId("workspace"), "Board", objects = mapOf(node.id to node))

    private fun compile(
        before: Workspace,
        after: Workspace,
    ): TransactionOperation? {
        val plan =
            WorkspaceDraftMergePlan(
                WorkspaceDraftReview(
                    "draft",
                    0,
                    0,
                    0,
                    0,
                    before,
                    after,
                    before,
                    listOf(CreateObjectsOperation("old-id", listOf(node))),
                    false,
                    false,
                ),
            )
        var serial = 0
        return buildDraftMergeOperation(plan, plan.fields.associate { it.id to DraftMergeChoice.UseDraft }, before) { "new-${++serial}" }
    }

    private fun apply(
        before: Workspace,
        after: Workspace,
    ): Workspace {
        val operation = checkNotNull(compile(before, after))
        assertTrue(operation.operations.all { it.operationId.startsWith("new-") })
        val applied = (operation.applyTo(before) as OperationResult.Applied).workspace
        assertEquals(after.mergeContent(), applied.mergeContent())
        assertEquals(before.version + 1, applied.version)
        val executed = WorkspaceHistory(before).execute(operation)
        assertTrue(executed.succeeded)
        val undone = executed.history.undo()
        assertTrue(undone.succeeded)
        assertEquals(before.mergeContent(), undone.history.workspace.mergeContent())
        val redone = undone.history.redo()
        assertTrue(redone.succeeded)
        assertEquals(after.mergeContent(), redone.history.workspace.mergeContent())
        return applied
    }

    @Test fun editsTransformTextShapeAndLockUsingCurrentVersionsAndNewIds() {
        val after =
            base.copy(
                objects =
                    mapOf(
                        node.id to
                            node.copy(
                                text = "After",
                                shape = NodeShape.Diamond,
                                transform = node.transform.copy(rotationDegrees = 45f),
                                locked = true,
                            ),
                    ),
            )
        val applied = apply(base, after)
        assertEquals(5, applied.objects.getValue(node.id).version)
        val commands = checkNotNull(compile(base, after)).operations
        val lock = commands.last() as UpdateTextNodeAttributesOperation
        assertEquals(
            lock.changes
                .single()
                .before
                .copy(locked = true),
            lock.changes.single().after,
        )
        assertTrue(draftMergeContractGaps(base, checkNotNull(compile(base, after))).isEmpty())
        val plan = WorkspaceDraftMergePlan(WorkspaceDraftReview("draft", 0, 0, 0, 0, base, after, base, emptyList(), false, false))
        val choices = plan.fields.associate { it.id to DraftMergeChoice.UseDraft }
        assertFails { buildDraftMergeOperation(plan, choices, base.copy(title = "Changed")) }
        assertFails { buildDraftMergeOperation(plan, choices, base) { "same-id" } }
    }

    @Test fun explicitUnlockPrecedesEditsAndKeepRemoteNeedsNoOperation() {
        val before = base.copy(objects = mapOf(node.id to node.copy(locked = true)))
        val after = base.copy(objects = mapOf(node.id to node.copy(text = "After")))
        val operation = checkNotNull(compile(before, after))
        assertTrue(operation.operations.first() is UpdateTextNodeAttributesOperation)
        apply(before, after)
        assertNull(compile(base, base))
    }

    @Test fun replacesMediaReferenceWithoutLosingDesiredRelations() {
        val media = MediaNode(CanvasObjectId("media"), transform = node.transform, assetId = "old", mediaKind = MediaKind.Image)
        val edge = Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = media.id)
        val before = base.copy(objects = base.objects + (media.id to media), relations = mapOf(edge.id to edge))
        val after =
            before.copy(
                objects = before.objects + (media.id to media.copy(assetId = "new", altText = "After")),
                relations = mapOf(edge.id to edge.copy(label = "Updated")),
            )
        val applied = apply(before, after)
        assertEquals(3, applied.objects.getValue(media.id).version)
        assertEquals(2, applied.relations.getValue(edge.id).version)
        assertTrue(draftMergeContractGaps(before, checkNotNull(compile(before, after))).isEmpty())
    }

    @Test fun createsGroupsReparentsSurvivingChildrenThenDeletesOldGroupAndEdges() {
        val old = GroupFrame(CanvasObjectId("old"), transform = node.transform)
        val new = old.copy(id = CanvasObjectId("new"), title = "New")
        val before = base.copy(objects = mapOf(old.id to old, node.id to node.copy(parentId = old.id)))
        val after = base.copy(objects = mapOf(new.id to new, node.id to node.copy(parentId = new.id)))
        apply(before, after)
        val edge = Relation(RelationId("edge"), sourceObjectId = old.id, targetObjectId = node.id)
        apply(before.copy(relations = mapOf(edge.id to edge)), after)
    }

    @Test fun mediaReferenceUpdatesUnlockBeforeEditAndLockAfterward() {
        val media =
            MediaNode(
                CanvasObjectId("media"),
                transform = node.transform,
                assetId = "old",
                mediaKind = MediaKind.Video,
                thumbnailAssetId = "poster",
                locked = true,
                version = 7,
            )
        val before = base.copy(objects = mapOf(media.id to media))
        val after = before.copy(objects = mapOf(media.id to media.copy(locked = false, assetId = "new", thumbnailAssetId = null)))
        val commands = checkNotNull(compile(before, after)).operations
        assertIs<UpdateMediaNodeAttributesOperation>(commands.first())
        assertIs<UpdateMediaReferenceOperation>(commands.last())
        apply(before, after)
        val unlocked = before.copy(objects = mapOf(media.id to media.copy(locked = false)))
        val relocked = unlocked.copy(objects = mapOf(media.id to media.copy(assetId = "new", thumbnailAssetId = null)))
        val lockedCommands = checkNotNull(compile(unlocked, relocked)).operations
        assertIs<UpdateMediaReferenceOperation>(lockedCommands.first())
        assertIs<UpdateMediaNodeAttributesOperation>(lockedCommands.last())
        apply(unlocked, relocked)
    }

    @Test fun handlesRelationEndpointChangesAndGroupTitleAttributes() {
        val group = GroupFrame(CanvasObjectId("group"), transform = node.transform)
        val third = node.copy(id = CanvasObjectId("third"))
        val edge = Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = group.id)
        val before = base.copy(objects = base.objects + mapOf(group.id to group, third.id to third), relations = mapOf(edge.id to edge))
        val after =
            before.copy(
                objects = before.objects + (group.id to group.copy(title = "After", colorToken = "purple")),
                relations = mapOf(edge.id to edge.copy(targetObjectId = third.id, intent = "Supports")),
            )
        apply(before, after)
    }

    @Test fun rejectsAtomicWireExpansionOverLimitAndNeverReusesImportedIds() {
        val many =
            base.copy(
                objects =
                    base.objects +
                        (1..201).associate { number ->
                            val created = node.copy(id = CanvasObjectId("new-$number"))
                            created.id to created
                        },
            )
        assertFails { compile(base, many) }
        val target = base.copy(objects = mapOf(node.id to node.copy(text = "After")))
        val plan =
            WorkspaceDraftMergePlan(
                WorkspaceDraftReview(
                    "draft",
                    0,
                    0,
                    0,
                    0,
                    base,
                    target,
                    base,
                    listOf(EditTextOperation("old-id", listOf(TextChange(node.id, 1, node.text, "After")))),
                    false,
                    false,
                ),
            )
        assertFails { buildDraftMergeOperation(plan, plan.fields.associate { it.id to DraftMergeChoice.UseDraft }, base) { "old-id" } }
    }

    @Test fun localReplacementDoesNotClaimBackendCompatibility() {
        val media = MediaNode(CanvasObjectId("media"), transform = node.transform, assetId = "old", mediaKind = MediaKind.Image)
        val edge = Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = media.id)
        val before = base.copy(objects = base.objects + (media.id to media), relations = mapOf(edge.id to edge))
        val after = before.copy(objects = before.objects + (media.id to node.copy(id = media.id)))
        assertEquals(
            setOf(DraftMergeContractGap.ObjectIdReuse, DraftMergeContractGap.RelationIdReuse, DraftMergeContractGap.RestoreForUndoRedo),
            draftMergeContractGaps(before, checkNotNull(compile(before, after))),
        )
        val inserted = base.copy(objects = base.objects + (media.id to media))
        assertEquals(
            setOf(DraftMergeContractGap.RestoreForUndoRedo),
            draftMergeContractGaps(base, checkNotNull(compile(base, inserted))),
        )
        assertEquals(
            setOf(DraftMergeContractGap.RestoreForUndoRedo),
            draftMergeContractGaps(inserted, checkNotNull(compile(inserted, base))),
        )
    }
}
