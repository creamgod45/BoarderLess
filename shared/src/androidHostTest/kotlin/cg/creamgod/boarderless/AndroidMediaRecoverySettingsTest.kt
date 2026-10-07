package cg.creamgod.boarderless

import android.content.SharedPreferences
import cg.creamgod.boarderless.data.persistence.MediaRecoveryStore
import cg.creamgod.boarderless.data.persistence.committedMediaRecoverySettings
import java.lang.reflect.Proxy
import kotlin.test.*

/** Protocol doubles only; not evidence of Android disk persistence or process restart. */
class AndroidMediaRecoverySettingsTest {
    @Test fun recordsMigrateAndDismissThroughCheckedCommitNeverApply() {
        val preferences = PreferencesDouble()
        preferences.values["mediaRecovery.v1:1:u:1:w"] = "[\"a\"]"
        val store = MediaRecoveryStore(committedMediaRecoverySettings(preferences.api))
        store.record("u", "w", "b")
        assertEquals(listOf("a", "b"), store.list("u", "w"))
        assertFalse(preferences.values.containsKey("mediaRecovery.v1:1:u:1:w"))
        assertEquals(2, preferences.commits)
        store.dismiss("u", "w", "a")
        store.dismiss("u", "w", "b")
        assertTrue(preferences.values.isEmpty())
        assertEquals(4, preferences.commits)
        assertEquals(0, preferences.applies)
    }

    @Test fun falseCommitEvenWithMemoryUpdateCannotClaimSavedOrEraseLegacy() {
        val preferences = PreferencesDouble().apply { result = false }
        val legacy = "mediaRecovery.v1:1:u:1:w"
        preferences.values[legacy] = "[\"a\"]"
        val store = MediaRecoveryStore(committedMediaRecoverySettings(preferences.api))
        assertFailsWith<IllegalStateException> { store.record("u", "w", "b") }
        assertEquals("[\"a\"]", preferences.values[legacy])
        assertEquals(1, preferences.commits)
        assertEquals(listOf("a", "b"), store.list("u", "w")) // Unknown result is not rolled back.
        assertEquals(0, preferences.applies)
    }

    @Test fun unconfirmedDismissalReportsFailureAndDoesNotRetry() {
        val preferences = PreferencesDouble()
        val store = MediaRecoveryStore(committedMediaRecoverySettings(preferences.api))
        store.record("u", "w", "a")
        preferences.result = false
        assertFailsWith<IllegalStateException> { store.dismiss("u", "w", "a") }
        assertEquals(2, preferences.commits)
        assertEquals(0, preferences.applies)
    }

    private class PreferencesDouble {
        val values = mutableMapOf<String, String>()
        var result = true
        var commits = 0
        var applies = 0
        val api: SharedPreferences =
            proxy { name, args ->
                when (name) {
                    "getAll" -> values.toMap()
                    "contains" -> values.containsKey(args!![0])
                    "getString" -> values[args!![0]] ?: args[1]
                    "edit" -> editor()
                    else -> error("Unexpected preference call: $name")
                }
            }

        private fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, String?>()
            lateinit var editor: SharedPreferences.Editor
            editor =
                proxy { name, args ->
                    when (name) {
                        "putString" -> {
                            pending[args!![0] as String] = args[1] as String?
                            editor
                        }

                        "remove" -> {
                            pending[args!![0] as String] = null
                            editor
                        }

                        "commit" -> {
                            commits++
                            pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                            result
                        }

                        "apply" -> {
                            applies++
                            error("Recovery writes must not use apply")
                        }

                        else -> {
                            error("Unexpected editor call: $name")
                        }
                    }
                }
            return editor
        }

        private inline fun <reified T> proxy(crossinline handle: (String, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
                handle(method.name, args)
            } as T
    }
}
