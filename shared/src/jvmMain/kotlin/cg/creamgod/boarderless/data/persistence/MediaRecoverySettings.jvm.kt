package cg.creamgod.boarderless.data.persistence

import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import java.util.prefs.Preferences

// Settings() on JVM uses userRoot. Preserve the same keys/location, including legacy reminders.
internal actual fun defaultMediaRecoverySettings(): Settings =
    flushedMediaRecoverySettings(Preferences.userRoot())

internal fun flushedMediaRecoverySettings(
    preferences: Preferences,
    checkpoint: () -> Unit = { preferences.flush() },
): Settings {
    val delegate = PreferencesSettings(preferences)
    return object : Settings by delegate {
        override fun putString(key: String, value: String) {
            delegate.putString(key, value)
            // An unknown/failed flush is not permission to send complete or erase legacy data.
            checkpoint()
        }
        override fun remove(key: String) {
            delegate.remove(key)
            checkpoint()
        }
    }
}
