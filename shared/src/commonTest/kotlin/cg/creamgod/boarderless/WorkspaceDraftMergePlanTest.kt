package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.*
import cg.creamgod.boarderless.domain.model.*
import kotlin.test.*

class WorkspaceDraftMergePlanTest {
    private val node = TextNode(CanvasObjectId("node"), transform = CanvasTransform(Vec2.Zero, CanvasSize(120f, 80f)), text = "Baseline")
    private val base = Workspace(WorkspaceId("workspace"), "board", objects = mapOf(node.id to node))

    private fun plan(
        draft: Workspace,
        remote: Workspace = base,
    ) = WorkspaceDraftMergePlan(WorkspaceDraftReview("draft", 0, 0, 1, 1, base, draft, remote, emptyList(), true, true))

    private fun decisions(
        plan: WorkspaceDraftMergePlan,
        choice: DraftMergeChoice,
    ) = plan.fields.associate { it.id to choice }

    @Test fun requiresEveryDecisionAndNeverAutomaticallySelectsNonconflictingChanges() {
        val draft = base.copy(objects = mapOf(node.id to node.copy(text = "Draft", version = 2)))
        val plan = plan(draft)
        assertEquals(listOf(listOf("text")), plan.fields.map { it.id.path })
        assertFalse(plan.fields.single().conflicts)
        assertFails { plan.resolve(emptyMap()) }
        assertEquals(base, plan.resolve(decisions(plan, DraftMergeChoice.KeepRemote)))
        val applied = plan.resolve(decisions(plan, DraftMergeChoice.UseDraft))
        assertEquals("Draft", (applied.objects.getValue(node.id) as TextNode).text)
        assertEquals(1, applied.objects.getValue(node.id).version)
        assertEquals("Baseline", node.text)
    }

    @Test fun independentlyMergesTextAndTransformLeavesWhileRetainingUnrelatedRemoteProperties() {
        val draftNode = node.copy(text = "Draft", transform = node.transform.copy(position = Vec2(10f, 20f)))
        val remoteNode =
            node.copy(
                version = 5,
                text = "Remote",
                colorToken = "purple",
                transform = node.transform.copy(position = Vec2(30f, 0f), rotationDegrees = 90f),
            )
        val plan = plan(base.copy(objects = mapOf(node.id to draftNode)), base.copy(objects = mapOf(node.id to remoteNode)))
        assertEquals(3, plan.fields.size)
        assertEquals(2, plan.fields.count { it.conflicts })
        val choices =
            plan.fields.associate {
                it.id to
                    if (it.id.path == listOf("transform", "position", "x")) {
                        DraftMergeChoice.KeepRemote
                    } else {
                        DraftMergeChoice.UseDraft
                    }
            }
        val merged = plan.resolve(choices).objects.getValue(node.id) as TextNode
        assertEquals(remoteNode.copy(text = "Draft", transform = remoteNode.transform.copy(position = Vec2(30f, 20f))), merged)
    }

    @Test fun structuralChoicesAreAtomicAndCannotDeleteReferencedNodeImplicitly() {
        val other = node.copy(id = CanvasObjectId("other"))
        val edge = Relation(RelationId("edge"), sourceObjectId = node.id, targetObjectId = other.id)
        val remote = base.copy(objects = base.objects + (other.id to other), relations = mapOf(edge.id to edge))
        val plan = plan(base.copy(objects = emptyMap()), remote)
        assertTrue(
            plan.fields
                .single()
                .id.path
                .isEmpty(),
        )
        assertEquals(remote, plan.resolve(decisions(plan, DraftMergeChoice.KeepRemote)))
        assertFails { plan.resolve(decisions(plan, DraftMergeChoice.UseDraft)) }
    }

    @Test fun explicitGroupAndChildCreationChecksDependenciesAndNormalizesNewVersions() {
        val group = GroupFrame(CanvasObjectId("group"), version = 10, transform = node.transform)
        val child = node.copy(id = CanvasObjectId("child"), version = 20, parentId = group.id)
        val plan = plan(base.copy(objects = base.objects + mapOf(group.id to group, child.id to child)))
        val all = decisions(plan, DraftMergeChoice.UseDraft)
        val merged = plan.resolve(all)
        assertEquals(1, merged.objects.getValue(group.id).version)
        assertEquals(1, merged.objects.getValue(child.id).version)
        val groupRow = plan.fields.single { it.id.entityId == "group" }.id
        assertFails { plan.resolve(all + (groupRow to DraftMergeChoice.KeepRemote)) }
    }

    @Test fun lockedObjectsRequireExplicitUnlockBeforeEditsAndRejectUnknownRows() {
        val locked = node.copy(locked = true)
        val remote = base.copy(objects = mapOf(node.id to locked))
        val edit = plan(base.copy(objects = mapOf(node.id to node.copy(text = "Draft"))), remote)
        assertFails { edit.resolve(decisions(edit, DraftMergeChoice.UseDraft)) }
        assertFails {
            edit.resolve(
                decisions(edit, DraftMergeChoice.KeepRemote) +
                    (DraftMergeFieldId(false, "foreign", listOf("text")) to DraftMergeChoice.UseDraft),
            )
        }
        val unlock =
            WorkspaceDraftMergePlan(
                WorkspaceDraftReview(
                    "unlock",
                    0,
                    0,
                    0,
                    0,
                    remote,
                    base,
                    remote,
                    emptyList(),
                    false,
                    false,
                ),
            )
        assertEquals(node, unlock.resolve(decisions(unlock, DraftMergeChoice.UseDraft)).objects[node.id])
        val unlockAndEdit =
            WorkspaceDraftMergePlan(
                unlock.review.copy(
                    proposed =
                        base.copy(objects = mapOf(node.id to node.copy(text = "Explicit edit"))),
                ),
            )
        assertEquals(
            node.copy(text = "Explicit edit"),
            unlockAndEdit.resolve(decisions(unlockAndEdit, DraftMergeChoice.UseDraft)).objects[node.id],
        )
    }
}
