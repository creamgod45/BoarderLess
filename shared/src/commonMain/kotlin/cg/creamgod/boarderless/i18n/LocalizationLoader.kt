package cg.creamgod.boarderless.i18n

import boarderless.shared.generated.resources.Res
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val I18nDirectory = "files/i18n"

@Serializable
private data class LanguageManifest(val default: String, val languages: List<ManifestLanguage>)

@Serializable
private data class ManifestLanguage(val tag: String, val name: String, val matches: List<String> = emptyList())

/** Reads files/i18n/languages.json; adding a language is a new <tag>.json plus an entry there. */
suspend fun loadAvailableLanguages(): List<AppLanguage> = runCatching {
    val manifest = Json.decodeFromString<LanguageManifest>(Res.readBytes("$I18nDirectory/languages.json").decodeToString())
    manifest.languages.map { AppLanguage(it.tag, it.name, it.matches) }
}.getOrNull()?.takeIf { it.isNotEmpty() } ?: listOf(DefaultLanguage)

/**
 * Loads [language]'s catalog and makes it current. English is compiled into `Strings`, so it needs
 * no file; if a catalog cannot be read the UI stays usable in English.
 */
suspend fun activateLanguage(language: AppLanguage) {
    if (language.tag == DefaultLanguage.tag) {
        Localization.install(language, null)
        return
    }
    val catalog = runCatching { Res.readBytes("$I18nDirectory/${language.tag}.json").decodeToString() }.getOrNull()
    Localization.install(if (catalog == null) DefaultLanguage else language, catalog)
}
