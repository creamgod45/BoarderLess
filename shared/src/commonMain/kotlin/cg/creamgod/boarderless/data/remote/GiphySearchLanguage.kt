package cg.creamgod.boarderless.data.remote

/** Provider language codes, not arbitrary UI locale strings. No query translation or rewriting. */
internal fun giphySearchLanguage(languageTag: String): String {
    val parts = languageTag.replace('_', '-').lowercase().split('-')
    val base = parts.firstOrNull().orEmpty()
    if (base == "zh") {
        if ("hans" in parts) return "zh-CN"
        return if ("hant" in parts || "tw" in parts || "hk" in parts || "mo" in parts) "zh-TW" else "zh-CN"
    }
    return base.takeIf { it in giphySupportedLanguages } ?: "en"
}

private val giphySupportedLanguages = setOf(
    "en", "es", "pt", "id", "fr", "ar", "tr", "th", "vi", "de", "it", "ja", "ru", "ko",
    "pl", "nl", "ro", "hu", "sv", "cs", "hi", "bn", "da", "fa", "tl", "fi", "he", "ms", "no", "uk",
)
