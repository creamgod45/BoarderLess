package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.GroupFrame
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.hierarchyAwareSelectionAfterObjectTap
import cg.creamgod.boarderless.feature.canvas.selectionAfterObjectTap
import cg.creamgod.boarderless.feature.canvas.selectionPropertySummary
import cg.creamgod.boarderless.feature.canvas.topLevelSelectionIds
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionPropertyTest {
    @Test
    fun additiveObjectTapBuildsAndTogglesTouchSelection() {
        val first = CanvasObjectId("first")
        val second = CanvasObjectId("second")

        assertEquals(setOf(first), selectionAfterObjectTap(emptySet(), first, additive = true))
        assertEquals(setOf(first, second), selectionAfterObjectTap(setOf(first), second, additive = true))
        assertEquals(setOf(second), selectionAfterObjectTap(setOf(first, second), first, additive = true))
    }

    @Test
    fun nonAdditiveObjectTapReplacesSelection() {
        val first = CanvasObjectId("first")
        val second = CanvasObjectId("second")
        val replacement = CanvasObjectId("replacement")

        assertEquals(
            setOf(replacement),
            selectionAfterObjectTap(setOf(first, second), replacement, additive = false),
        )
    }

    @Test
    fun selectAllUsesHierarchyRootsInsteadOfSelectingGroupsAndTheirChildrenTogether() {
        val groupId = CanvasObjectId("group")
        val rootNodeId = CanvasObjectId("root-node")
        val childId = CanvasObjectId("child")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f))

        assertEquals(
            setOf(groupId, rootNodeId),
            topLevelSelectionIds(
                listOf(
                    GroupFrame(groupId, transform = transform),
                    TextNode(rootNodeId, transform = transform, text = "Root"),
                    TextNode(childId, parentId = groupId, transform = transform, text = "Child"),
                ),
            ),
        )
    }

    @Test
    fun additiveSelectionReplacesAnAncestorWithItsChildAndKeepsSiblings() {
        val groupId = CanvasObjectId("group")
        val childId = CanvasObjectId("child")
        val siblingId = CanvasObjectId("sibling")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f))
        val objects =
            listOf(
                GroupFrame(groupId, transform = transform),
                TextNode(childId, parentId = groupId, transform = transform, text = "Child"),
                TextNode(siblingId, transform = transform, text = "Sibling"),
            ).associateBy { it.id }

        assertEquals(
            setOf(siblingId, childId),
            hierarchyAwareSelectionAfterObjectTap(
                selectedIds = setOf(groupId, siblingId),
                objectId = childId,
                additive = true,
                objectsById = objects,
            ),
        )
    }

    @Test
    fun additiveSelectionReplacesSelectedDescendantsWithTheirGroup() {
        val groupId = CanvasObjectId("group")
        val childId = CanvasObjectId("child")
        val nestedGroupId = CanvasObjectId("nested")
        val nestedChildId = CanvasObjectId("nested-child")
        val transform = CanvasTransform(Vec2.Zero, CanvasSize(240f, 120f))
        val objects =
            listOf(
                GroupFrame(groupId, transform = transform),
                TextNode(childId, parentId = groupId, transform = transform, text = "Child"),
                GroupFrame(nestedGroupId, parentId = groupId, transform = transform),
                TextNode(nestedChildId, parentId = nestedGroupId, transform = transform, text = "Nested"),
            ).associateBy { it.id }

        assertEquals(
            setOf(groupId),
            hierarchyAwareSelectionAfterObjectTap(
                selectedIds = setOf(childId, nestedChildId),
                objectId = groupId,
                additive = true,
                objectsById = objects,
            ),
        )
    }

    @Test
    fun removingAnAdditivelySelectedObjectPreservesTheOtherSelection() {
        val firstId = CanvasObjectId("first")
        val secondId = CanvasObjectId("second")

        assertEquals(
            setOf(secondId),
            hierarchyAwareSelectionAfterObjectTap(
                selectedIds = setOf(firstId, secondId),
                objectId = firstId,
                additive = true,
                objectsById = emptyMap(),
            ),
        )
    }

    @Test
    fun sharedSelectionValueIsReported() {
        assertEquals(
            "Lilac",
            selectionPropertySummary(listOf("lilac", "lilac")) { it.replaceFirstChar(Char::uppercase) },
        )
    }

    @Test
    fun differingSelectionValuesAreExplicitlyMixed() {
        assertEquals(
            "Mixed",
            selectionPropertySummary(listOf("lilac", "mint"), valueLabel = { it }),
        )
    }

    @Test
    fun emptySelectionUsesTheRequestedFallback() {
        assertEquals(
            "No color",
            selectionPropertySummary(emptyList<String>(), emptyLabel = "No color", valueLabel = { it }),
        )
    }
}
