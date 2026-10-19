package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.QuickSchemeStore
import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickSchemeStoreTest {
    @Test
    fun saveListRenameAndDeleteSchemes() {
        val store = QuickSchemeStore(InMemorySettings())

        val first = store.save("first payload")
        val second = store.save("second payload", "  Planning set  ")

        assertEquals("Scheme 1", first.name)
        assertEquals("Planning set", second.name)
        assertEquals(listOf(first, second), store.list())
        assertEquals("Renamed set", store.rename(first.id, " Renamed set ")?.name)
        assertTrue(store.delete(first.id))
        assertFalse(store.delete(first.id))
        assertEquals(listOf(second), store.list())
    }

    @Test
    fun deletedSlotsAreNotReused() {
        val store = QuickSchemeStore(InMemorySettings())
        val first = store.save("first")
        store.delete(first.id)

        val next = store.save("next")

        assertEquals(2, next.id)
        assertEquals("Scheme 2", next.name)
    }

    @Test
    fun storesExplicitPayloadSchemaVersion() {
        val store = QuickSchemeStore(InMemorySettings())

        val saved = store.save("v2 payload", schemaVersion = 2)

        assertEquals(2, saved.schemaVersion)
        assertEquals(2, store.latest()?.schemaVersion)
    }

    @Test
    fun readsLegacyPayloadWithoutSchemaOrName() {
        val settings =
            InMemorySettings().apply {
                putInt("quickScheme.count", 1)
                putString("quickScheme.item.1.payload", "legacy")
            }

        val scheme = QuickSchemeStore(settings).latest()

        assertEquals("Scheme 1", scheme?.name)
        assertEquals("legacy", scheme?.payload)
        assertEquals(1, scheme?.schemaVersion)
        assertNull(QuickSchemeStore(InMemorySettings()).latest())
    }

    @Test
    fun preservesSourceAcrossRestartAndRenameWithoutInventingLegacyProvenance() {
        val settings = InMemorySettings()
        val saved = QuickSchemeStore(settings).save("media", schemaVersion = 4, sourceWorkspaceId = "source")
        val reopened = QuickSchemeStore(settings)
        assertEquals("source", reopened.latest()?.sourceWorkspaceId)
        assertEquals("source", reopened.rename(saved.id, "Renamed")?.sourceWorkspaceId)
        val legacy = reopened.save("legacy")
        assertNull(legacy.sourceWorkspaceId)
        assertTrue(reopened.delete(saved.id))
        assertFalse(settings.hasKey("quickScheme.item.${saved.id}.sourceWorkspaceId"))
        assertEquals(listOf(legacy), reopened.list())
    }
}

internal class InMemorySettings : Settings {
    private val values = mutableMapOf<String, Any>()

    override val keys: Set<String> get() = values.keys
    override val size: Int get() = values.size

    override fun clear() = values.clear()

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun hasKey(key: String): Boolean = key in values

    override fun putInt(
        key: String,
        value: Int,
    ) {
        values[key] = value
    }

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = getIntOrNull(key) ?: defaultValue

    override fun getIntOrNull(key: String): Int? = values[key] as? Int

    override fun putLong(
        key: String,
        value: Long,
    ) {
        values[key] = value
    }

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = getLongOrNull(key) ?: defaultValue

    override fun getLongOrNull(key: String): Long? = values[key] as? Long

    override fun putString(
        key: String,
        value: String,
    ) {
        values[key] = value
    }

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = values[key] as? String

    override fun putFloat(
        key: String,
        value: Float,
    ) {
        values[key] = value
    }

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = getFloatOrNull(key) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = values[key] as? Float

    override fun putDouble(
        key: String,
        value: Double,
    ) {
        values[key] = value
    }

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = getDoubleOrNull(key) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = values[key] as? Double

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) {
        values[key] = value
    }

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = getBooleanOrNull(key) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = values[key] as? Boolean
}
