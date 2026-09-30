package cg.creamgod.boarderless

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Keeps every files/i18n/<tag>.json in step with en.json, the source of the generated `Strings`:
 * same keys, same kind (text or plural), same placeholders, and every file listed in the manifest.
 */
class LocalizationCatalogTest {
    private val directory = File("src/commonMain/composeResources/files/i18n")
    private val placeholder = Regex("""\{(\w+)\}""")
    private val pluralForms = setOf("zero", "one", "two", "few", "many", "other")

    private sealed interface Entry {
        val placeholders: Set<String>
    }

    private data class Text(override val placeholders: Set<String>) : Entry
    private data class Plural(override val placeholders: Set<String>) : Entry

    private fun load(file: File): Map<String, Entry> {
        val result = linkedMapOf<String, Entry>()
        fun names(text: String) = placeholder.findAll(text).map { it.groupValues[1] }.toSet()
        fun visit(prefix: String, node: JsonObject) {
            node.forEach { (name, value) ->
                val key = if (prefix.isEmpty()) name else "$prefix.$name"
                when {
                    value is JsonPrimitive -> result[key] = Text(names(value.content))
                    value is JsonObject && "other" in value && value.keys.all { it in pluralForms } ->
                        result[key] = Plural(value.values.flatMap { names(it.jsonPrimitive.content) }.toSet() - "count")
                    value is JsonObject -> visit(key, value)
                    else -> error("Unsupported value at $key in ${file.name}")
                }
            }
        }
        visit("", Json.parseToJsonElement(file.readText()).jsonObject)
        return result
    }

    @Test
    fun everyLanguageMatchesTheEnglishCatalog() {
        val english = load(File(directory, "en.json"))
        assertTrue(english.size > 100, "en.json looks empty (${File(".").absolutePath})")
        val catalogs = directory.listFiles { file -> file.extension == "json" && file.name != "languages.json" }!!
        catalogs.filter { it.name != "en.json" }.forEach { file ->
            val translated = load(file)
            val missing = english.keys - translated.keys
            val extra = translated.keys - english.keys
            assertTrue(missing.isEmpty(), "${file.name} is missing: ${missing.joinToString()}")
            assertTrue(extra.isEmpty(), "${file.name} has keys en.json does not: ${extra.joinToString()}")
            english.forEach { (key, source) ->
                val target = translated.getValue(key)
                assertEquals(source::class, target::class, "${file.name}: $key must be the same kind as in en.json")
                assertEquals(source.placeholders, target.placeholders, "${file.name}: placeholders differ for $key")
            }
        }
    }

    @Test
    fun theManifestListsExactlyTheCatalogFiles() {
        val manifest = Json.parseToJsonElement(File(directory, "languages.json").readText()).jsonObject
        val tags = manifest.getValue("languages").jsonArray.map { it.jsonObject.getValue("tag").jsonPrimitive.content }
        val files = directory.listFiles { file -> file.extension == "json" && file.name != "languages.json" }!!
            .map { it.nameWithoutExtension }
        assertEquals(files.sorted(), tags.sorted())
        assertEquals("en", manifest.getValue("default").jsonPrimitive.content)
    }

    @Test
    fun sourcesNoLongerUseLiteralEnglishKeys() {
        val offenders = File("src/commonMain/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.parentFile.name != "i18n" }
            .filter { Regex("""(?<![\w.])tr(Count)?\(\s*"""").containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertTrue(offenders.isEmpty(), "Use Strings.* instead of tr(\"…\") in: $offenders")
    }
}
