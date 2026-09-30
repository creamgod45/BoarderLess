package cg.creamgod.boarderless.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * Translations live in composeResources/files/i18n/<tag>.json as namespaced JSON. en.json is the
 * source of truth: the build generates the `Strings` object from it (see :shared:generateI18nStrings),
 * so code refers to keys like `Strings.status.opening(title)` and English is compiled in as the
 * fallback. Other languages are loaded at runtime; a missing key falls back to English.
 */

/** A language listed in files/i18n/languages.json. [matches] are extra system locale prefixes. */
data class AppLanguage(val tag: String, val nativeName: String, val matches: List<String> = emptyList())

val DefaultLanguage = AppLanguage("en", "English")

/** The user's choice: a language tag, or [System] to follow the platform locale. */
data class LanguagePreference(val token: String) {
    companion object {
        val System = LanguagePreference("system")

        fun of(language: AppLanguage) = LanguagePreference(language.tag)

        fun fromToken(token: String?): LanguagePreference =
            token?.takeIf { it.isNotBlank() }?.let(::LanguagePreference) ?: System
    }
}

/**
 * Picks the language to show: an explicit, available preference first; otherwise the system locale,
 * matched by exact tag, then by its language subtag against each tag and its `matches`.
 */
fun resolveLanguage(
    preference: LanguagePreference,
    systemLanguageTag: String,
    available: List<AppLanguage>,
): AppLanguage {
    available.firstOrNull { it.tag.equals(preference.token, ignoreCase = true) }?.let { return it }
    val system = systemLanguageTag.replace('_', '-').lowercase()
    available.firstOrNull { it.tag.lowercase() == system }?.let { return it }
    val subtag = system.substringBefore('-')
    return available.firstOrNull { language ->
        language.tag.lowercase().substringBefore('-') == subtag || language.matches.any { it.lowercase() == subtag }
    } ?: available.firstOrNull { it.tag == DefaultLanguage.tag } ?: DefaultLanguage
}

internal sealed interface Translation {
    data class Text(val value: String) : Translation

    /** CLDR-style forms; only "one" and "other" are selected today. */
    data class Plural(val forms: Map<String, String>) : Translation
}

/**
 * The active language and its loaded translations. Snapshot state, so composables that read a
 * string recompose when the language changes. Starts as built-in English, which keeps logic tests
 * deterministic; `App()` activates the user's language.
 */
object Localization {
    var language: AppLanguage by mutableStateOf(DefaultLanguage)
        private set
    var languages: List<AppLanguage> by mutableStateOf(listOf(DefaultLanguage))
        internal set
    private var translations: Map<String, Translation> by mutableStateOf(emptyMap())

    /** Switches to [language] using [catalogJson] (its <tag>.json); null means built-in English only. */
    fun install(language: AppLanguage, catalogJson: String?) {
        translations = catalogJson?.let(::parseCatalog).orEmpty()
        this.language = language
    }

    internal fun lookup(key: String): Translation? = translations[key]
}

/** A key without placeholders. Call it to get the text in the current language. */
class LocalizedText(val key: String, val english: String) {
    operator fun invoke(): String = tr(key, english)

    override fun toString(): String = invoke()
}

fun tr(key: String, english: String, vararg args: Pair<String, Any?>): String {
    val template = (Localization.lookup(key) as? Translation.Text)?.value ?: english
    return formatTemplate(template, args)
}

fun trPlural(
    key: String,
    englishOne: String,
    englishOther: String,
    count: Int,
    vararg args: Pair<String, Any?>,
): String {
    val forms = (Localization.lookup(key) as? Translation.Plural)?.forms
    val template = if (forms == null) {
        if (count == 1) englishOne else englishOther
    } else {
        (if (count == 1) forms["one"] else null) ?: forms["other"] ?: englishOther
    }
    return formatTemplate(template, arrayOf("count" to count, *args))
}

/** Replaces `{name}` placeholders in one pass, so argument text is never substituted again. */
internal fun formatTemplate(template: String, args: Array<out Pair<String, Any?>>): String {
    if (args.isEmpty()) return template
    val values = args.toMap()
    return PlaceholderPattern.replace(template) { match ->
        val name = match.groupValues[1]
        if (name in values) values[name].toString() else match.value
    }
}

/** Flattens a namespaced catalog into dotted keys; objects holding only plural forms are plurals. */
internal fun parseCatalog(json: String): Map<String, Translation> {
    val result = mutableMapOf<String, Translation>()
    fun visit(prefix: String, element: JsonObject) {
        element.forEach { (name, value) ->
            val key = if (prefix.isEmpty()) name else "$prefix.$name"
            when {
                value is JsonPrimitive && value.isString -> result[key] = Translation.Text(value.content)
                value is JsonObject && value.isPluralForms() -> result[key] = Translation.Plural(
                    value.mapValues { (_, form) -> (form as JsonPrimitive).content },
                )
                value is JsonObject -> visit(key, value)
            }
        }
    }
    visit("", Json.parseToJsonElement(json) as JsonObject)
    return result
}

private fun JsonObject.isPluralForms(): Boolean =
    "other" in this && keys.all { it in PluralForms } &&
        values.all { it is JsonPrimitive && (it as JsonPrimitive).contentOrNull != null && it.isString }

private val PluralForms = setOf("zero", "one", "two", "few", "many", "other")
private val PlaceholderPattern = Regex("""\{(\w+)\}""")
