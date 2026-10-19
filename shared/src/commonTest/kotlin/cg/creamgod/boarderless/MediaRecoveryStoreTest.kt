package cg.creamgod.boarderless

import cg.creamgod.boarderless.data.persistence.MediaRecoveryStore
import kotlin.test.*

class MediaRecoveryStoreTest {
    @Test fun ignoredWritesCannotDiscardLegacyOrClaimRecordSuccess() {
        val backing = InMemorySettings()
        val legacy = "mediaRecovery.v1:1:u:1:w"
        backing.putString(legacy, "[\"a\"]")
        val ignored =
            object : com.russhwolf.settings.Settings by backing {
                override fun putString(
                    key: String,
                    value: String,
                ) = Unit
            }
        assertFailsWith<IllegalStateException> { MediaRecoveryStore(ignored).record("u", "w", "b") }
        assertEquals("[\"a\"]", backing.getStringOrNull(legacy))
        assertEquals(listOf("a"), MediaRecoveryStore(backing).list("u", "w"))
    }

    @Test fun ignoredRemovalCannotClaimDismissalOrDiscardLegacy() {
        val backing = InMemorySettings()
        val store = MediaRecoveryStore(backing)
        store.record("u", "w", "a")
        val ignored =
            object : com.russhwolf.settings.Settings by backing {
                override fun remove(key: String) = Unit
            }
        assertFailsWith<IllegalStateException> { MediaRecoveryStore(ignored).dismiss("u", "w", "a") }
        assertEquals(listOf("a"), store.list("u", "w"))
    }

    @Test fun uuidScopeKeysRemainBoundedAndDistinct() {
        val settings = InMemorySettings()
        val store = MediaRecoveryStore(settings)
        val user = "11111111-1111-4111-8111-111111111111"
        val workspace = "22222222-2222-4222-8222-222222222222"
        store.record(user, workspace, "a")
        store.record(user + "x", workspace, "b")
        store.record(user, workspace + "x", "c")
        assertTrue(settings.keys.all { it.length == 70 })
        assertEquals(3, settings.keys.size)
        assertEquals(listOf("a"), store.list(user, workspace))
    }

    @Test fun legacyRemindersMigrateOnlyAfterSuccessfulWrite() {
        val settings = InMemorySettings()
        val legacy = "mediaRecovery.v1:1:u:1:w"
        settings.putString(legacy, "[\"a\"]")
        val store = MediaRecoveryStore(settings)
        assertEquals(listOf("a"), store.list("u", "w"))
        assertTrue(settings.hasKey(legacy))
        store.record("u", "w", "a")
        assertFalse(settings.hasKey(legacy))
        assertEquals(listOf("a"), MediaRecoveryStore(settings).list("u", "w"))
        store.dismiss("u", "w", "a")
        assertTrue(settings.keys.isEmpty())
    }

    @Test fun survivesNewStoreInstanceAndDeduplicates() {
        val settings = InMemorySettings()
        MediaRecoveryStore(settings).apply {
            record("u", "w", "a")
            record("u", "w", "a")
            record("u", "w", "b")
        }
        assertEquals(listOf("a", "b"), MediaRecoveryStore(settings).list("u", "w"))
        assertTrue(settings.keys.none { "url" in it || "checksum" in it })
    }

    @Test fun isolatesUsersWorkspacesAndAmbiguousKeySegments() {
        val store = MediaRecoveryStore(InMemorySettings())
        store.record("u", "w", "a")
        store.record("u:w", "x", "b")
        store.record("u", "w:x", "c")
        assertTrue(store.list("other", "w").isEmpty())
        assertTrue(store.list("u", "other").isEmpty())
        assertEquals(listOf("b"), store.list("u:w", "x"))
        assertEquals(listOf("c"), store.list("u", "w:x"))
    }

    @Test fun dismissalOnlyRemovesScopedReminder() {
        val settings = InMemorySettings()
        val store = MediaRecoveryStore(settings)
        store.record("u", "w", "a")
        store.record("other", "w", "a")
        store.dismiss("u", "w", "unknown")
        assertEquals(listOf("a"), store.list("u", "w"))
        store.dismiss("u", "w", "a")
        assertTrue(MediaRecoveryStore(settings).list("u", "w").isEmpty())
        assertEquals(listOf("a"), store.list("other", "w"))
    }

    @Test fun fullJournalNeverSilentlyEvictsUnresolvedImports() {
        val store = MediaRecoveryStore(InMemorySettings())
        repeat(MediaRecoveryStore.MaximumEntries) { store.record("u", "w", "a$it") }
        store.record("u", "w", "a0")
        assertFailsWith<IllegalStateException> { store.record("u", "w", "overflow") }
        assertEquals(MediaRecoveryStore.MaximumEntries, store.list("u", "w").size)
        assertEquals("a0", store.list("u", "w").first())
    }

    @Test fun corruptJournalIsNotOverwrittenByNewImport() {
        val settings = InMemorySettings()
        val store = MediaRecoveryStore(settings)
        store.record("u", "w", "a")
        val key = settings.keys.single()
        settings.putString(key, "corrupt")
        assertFails { store.record("u", "w", "b") }
        assertEquals("corrupt", settings.getString(key, ""))
    }
}
