package cg.creamgod.boarderless.data.persistence

import android.content.Context
import android.content.SharedPreferences
import androidx.startup.Initializer
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

private var initializedMediaRecoverySettings: Settings? = null

internal actual fun defaultMediaRecoverySettings(): Settings =
    checkNotNull(initializedMediaRecoverySettings) { "Media recovery settings startup initialization is required" }

/** Same file as Settings(), but checked synchronous writes for recovery IDs only. */
class MediaRecoverySettingsInitializer : Initializer<Settings> {
    override fun create(context: Context): Settings {
        val app = context.applicationContext
        return committedMediaRecoverySettings(app.getSharedPreferences("${app.packageName}_preferences", Context.MODE_PRIVATE))
            .also { initializedMediaRecoverySettings = it }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}

internal fun committedMediaRecoverySettings(preferences: SharedPreferences): Settings {
    val delegate = SharedPreferencesSettings(preferences)
    return object : Settings by delegate {
        override fun putString(
            key: String,
            value: String,
        ) {
            check(preferences.edit().putString(key, value).commit()) { "Media recovery commit could not be confirmed" }
        }

        override fun remove(key: String) {
            check(preferences.edit().remove(key).commit()) { "Media recovery removal could not be confirmed" }
        }
    }
}
