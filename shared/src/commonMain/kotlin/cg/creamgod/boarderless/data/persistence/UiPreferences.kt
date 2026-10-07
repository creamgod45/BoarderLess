package cg.creamgod.boarderless.data.persistence

import cg.creamgod.boarderless.designsystem.color.ColorModel
import cg.creamgod.boarderless.i18n.LanguagePreference
import com.russhwolf.settings.Settings

class UiPreferences(
    private val settings: Settings = Settings(),
) {
    var reduceTransparency: Boolean
        get() = settings.getBoolean(ReduceTransparencyKey, false)
        set(value) = settings.putBoolean(ReduceTransparencyKey, value)

    var reduceMotion: Boolean
        get() = settings.getBoolean(ReduceMotionKey, false)
        set(value) = settings.putBoolean(ReduceMotionKey, value)

    var language: LanguagePreference
        get() = LanguagePreference.fromToken(settings.getStringOrNull(LanguageKey))
        set(value) = settings.putString(LanguageKey, value.token)

    var colorPickerModel: ColorModel
        get() =
            settings
                .getStringOrNull(ColorPickerModelKey)
                ?.let { name -> ColorModel.entries.firstOrNull { it.name == name } }
                ?: ColorModel.Hsb
        set(value) = settings.putString(ColorPickerModelKey, value.name)

    var collapsedInspectorSections: Set<String>
        get() =
            settings
                .getStringOrNull(CollapsedInspectorSectionsKey)
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        set(value) =
            settings.putString(
                CollapsedInspectorSectionsKey,
                value
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .sorted()
                    .joinToString(","),
            )

    var collapsedPanelSections: Set<String>
        get() =
            settings
                .getStringOrNull(CollapsedPanelSectionsKey)
                .orEmpty()
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        set(value) =
            settings.putString(
                CollapsedPanelSectionsKey,
                value
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .sorted()
                    .joinToString(","),
            )

    private companion object {
        const val ReduceTransparencyKey = "ui.accessibility.reduceTransparency"
        const val ReduceMotionKey = "ui.accessibility.reduceMotion"
        const val LanguageKey = "ui.language"
        const val ColorPickerModelKey = "ui.colorPicker.model"
        const val CollapsedInspectorSectionsKey = "ui.inspector.collapsedSections"
        const val CollapsedPanelSectionsKey = "ui.panels.collapsedSections"
    }
}
