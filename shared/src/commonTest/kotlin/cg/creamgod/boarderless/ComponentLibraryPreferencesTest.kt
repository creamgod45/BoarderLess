package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.ComponentLibraryPreferences
import cg.creamgod.boarderless.domain.model.NodeShape
import cg.creamgod.boarderless.feature.canvas.*
import kotlin.test.*

class ComponentLibraryPreferencesTest {
    @Test fun categoryAndCollapsePreferencesPersistWithoutChangingFavoritesOrRecent() {
        val settings = InMemorySettings()
        val store = ComponentLibraryPreferences(settings)
        assertEquals("all", store.selectedCategory())
        assertTrue(store.categoriesExpanded())
        store.toggleFavorite("thought")
        store.recordInsertion("thought")
        ComponentLibraryCategory.entries.forEach { category ->
            store.setSelectedCategory(category.token)
            assertEquals(category, ComponentLibraryCategory.fromToken(ComponentLibraryPreferences(settings).selectedCategory()))
        }
        store.setCategoriesExpanded(false)
        assertFalse(ComponentLibraryPreferences(settings).categoriesExpanded())
        store.setCategoriesExpanded(true)
        assertTrue(ComponentLibraryPreferences(settings).categoriesExpanded())
        assertEquals(setOf("thought"), store.favorites())
        assertEquals(listOf("thought"), store.recent())
        assertFails { store.setSelectedCategory("private text") }
        settings.putString("ui.componentLibrary.category.v1", "x".repeat(8193))
        assertEquals("all", store.selectedCategory())
        assertEquals("x".repeat(8193), settings.getStringOrNull("ui.componentLibrary.category.v1"))
        assertEquals(ComponentLibraryCategory.All, ComponentLibraryCategory.fromToken("future"))
    }

    @Test fun categoriesComposeWithSearchFavoritesAndOrderedRecentWithoutDuplicates() {
        val entries =
            listOf(
                ComponentLibraryEntry("thought", "Thought", "Idea", "paper"),
                ComponentLibraryEntry("flowchart-starter", "Flowchart", "Starter diagram", "paper"),
                ComponentLibraryEntry("tree-starter", "Tree", "Starter diagram", "paper"),
                ComponentLibraryEntry("swimlane-starter", "Swimlane", "Starter diagram", "paper"),
                ComponentLibraryEntry("arrow", "Arrow", "Direction", "paper", NodeShape.ArrowRight),
            )
        val favorites = setOf("tree-starter", "arrow")
        val recent = listOf("swimlane-starter", "tree-starter", "arrow", "tree-starter", "removed")

        fun view(
            mode: ComponentLibraryView,
            category: ComponentLibraryCategory,
            query: String = "",
        ) = componentLibraryEntriesForView(entries, query, mode, favorites, recent, category).map { it.id }
        assertEquals(
            listOf("swimlane-starter", "tree-starter"),
            view(ComponentLibraryView.Recent, ComponentLibraryCategory.Diagrams, "STARTER diagram"),
        )
        assertEquals(listOf("tree-starter"), view(ComponentLibraryView.Favorites, ComponentLibraryCategory.Diagrams))
        assertEquals(listOf("arrow"), view(ComponentLibraryView.Favorites, ComponentLibraryCategory.Arrows))
        assertEquals(listOf("swimlane-starter"), view(ComponentLibraryView.All, ComponentLibraryCategory.Containers))
        assertEquals(listOf("flowchart-starter"), view(ComponentLibraryView.All, ComponentLibraryCategory.Flow))
        assertEquals(listOf("thought"), view(ComponentLibraryView.All, ComponentLibraryCategory.Text))
        assertTrue(view(ComponentLibraryView.Favorites, ComponentLibraryCategory.Text).isEmpty())
        assertEquals(entries.map { it.id }, view(ComponentLibraryView.All, ComponentLibraryCategory.All))
    }

    @Test fun everyBuiltInShapeHasStableCategoriesAndFlowShapesRemainGeometric() {
        NodeShape.entries.forEach { shape ->
            val entry = ComponentLibraryEntry(shape.token, "Shape", "Description", "paper", shape)
            assertTrue(entry.categories.isNotEmpty())
            assertFalse(ComponentLibraryCategory.All in entry.categories)
        }
        val decision = ComponentLibraryEntry("decision-node", "Decision", "Flow", "paper", NodeShape.Diamond)
        assertEquals(setOf(ComponentLibraryCategory.Geometry, ComponentLibraryCategory.Flow), decision.categories)
        assertEquals(
            setOf(ComponentLibraryCategory.Text),
            ComponentLibraryEntry("text", "Text", "No border", "paper", NodeShape.PlainText).categories,
        )
    }

    @Test fun favoritesAndInsertionOrderPersistWithoutDuplicates() {
        val settings = InMemorySettings()
        val store = ComponentLibraryPreferences(settings)
        assertTrue(store.favorites().isEmpty())
        assertEquals(setOf("thought"), store.toggleFavorite("thought"))
        assertEquals(setOf("thought"), ComponentLibraryPreferences(settings).favorites())
        assertTrue(store.toggleFavorite("thought").isEmpty())
        repeat(25) { store.recordInsertion("component-$it") }
        assertEquals(20, store.recent().size)
        assertEquals("component-24", store.recent().first())
        store.recordInsertion("component-20")
        assertEquals("component-20", ComponentLibraryPreferences(settings).recent().first())
        assertEquals(20, store.recent().distinct().size)
    }

    @Test fun corruptedOversizedAndInvalidPreferenceIdsAreBounded() {
        val settings = InMemorySettings()
        settings.putString("ui.componentLibrary.recent.v1", "thought,thought,,bad/id,with space,process-node")
        val store = ComponentLibraryPreferences(settings)
        assertEquals(listOf("thought", "process-node"), store.recent())
        settings.putString("ui.componentLibrary.favorites.v1", "x".repeat(8193))
        assertTrue(store.favorites().isEmpty())
        assertFails { store.toggleFavorite("private,content") }
        assertFails { store.recordInsertion("../path") }
        repeat(128) { store.toggleFavorite("component-$it") }
        assertFails { store.toggleFavorite("over-limit") }
        assertEquals(128, store.favorites().size)
        store.toggleFavorite("component-0")
        assertEquals(128, store.toggleFavorite("over-limit").size)
    }

    @Test fun viewsFilterCurrentCatalogAndSearchPreservesRecentOrder() {
        val entries =
            listOf(
                ComponentLibraryEntry("thought", "Thought", "Idea", "paper"),
                ComponentLibraryEntry("process-node", "Process", "Flow action", "paper"),
                ComponentLibraryEntry("decision-node", "Decision", "Flow branch", "paper"),
            )
        val favorites = setOf("process-node", "removed-entry")
        val recent = listOf("decision-node", "removed-entry", "process-node", "decision-node")

        fun view(
            mode: ComponentLibraryView,
            query: String = "",
        ) = componentLibraryEntriesForView(entries, query, mode, favorites, recent).map { it.id }
        assertEquals(entries.map { it.id }, view(ComponentLibraryView.All))
        assertEquals(listOf("process-node"), view(ComponentLibraryView.Favorites))
        assertEquals(listOf("decision-node", "process-node"), view(ComponentLibraryView.Recent, "FLOW"))
        assertEquals(listOf("decision-node"), view(ComponentLibraryView.Recent, "flow branch"))
        assertTrue(view(ComponentLibraryView.Favorites, "missing").isEmpty())
    }
}
