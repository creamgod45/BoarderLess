package cg.creamgod.boarderless.data.persistence

import com.russhwolf.settings.Settings

/** Device-local built-in catalogue IDs only. No object IDs, text, media or sync credentials. */
class ComponentLibraryPreferences(private val settings: Settings = Settings()) {
    fun favorites(): Set<String> = read(FavoritesKey, 128).toSet()
    fun recent(): List<String> = read(RecentKey, 20)
    fun selectedCategory(): String = settings.getStringOrNull(CategoryKey)?.takeIf { it.length <= 16 && it in CategoryTokens } ?: "all"
    fun setSelectedCategory(token: String) { require(token in CategoryTokens); settings.putString(CategoryKey, token) }
    fun categoriesExpanded(): Boolean = settings.getBoolean(CategoriesExpandedKey, true)
    fun setCategoriesExpanded(expanded: Boolean) = settings.putBoolean(CategoriesExpandedKey, expanded)

    fun toggleFavorite(id: String): Set<String> {
        require(validId(id))
        val next = favorites().toMutableSet()
        if (!next.remove(id)) {
            check(next.size < 128) { "Favorite limit reached" }
            next.add(id)
        }
        settings.putString(FavoritesKey, next.sorted().joinToString(","))
        return next.toSet()
    }

    fun recordInsertion(id: String): List<String> {
        require(validId(id))
        val next = (listOf(id) + recent().filterNot { it == id }).take(20)
        settings.putString(RecentKey, next.joinToString(","))
        return next
    }

    private fun read(key: String, limit: Int): List<String> {
        val value = settings.getStringOrNull(key) ?: return emptyList()
        if (value.length > 8192) return emptyList()
        return value.split(',').filter(::validId).distinct().take(limit)
    }

    private fun validId(id: String) = id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))
    private companion object {
        const val FavoritesKey = "ui.componentLibrary.favorites.v1"
        const val RecentKey = "ui.componentLibrary.recent.v1"
        const val CategoryKey = "ui.componentLibrary.category.v1"
        const val CategoriesExpandedKey = "ui.componentLibrary.categoriesExpanded.v1"
        val CategoryTokens = setOf("all", "geometry", "flow", "arrows", "text", "containers", "diagrams")
    }
}
