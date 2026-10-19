package cg.creamgod.boarderless

import cg.creamgod.boarderless.domain.history.CreateObjectsOperation
import cg.creamgod.boarderless.domain.model.CanvasObjectId
import cg.creamgod.boarderless.domain.model.CanvasSize
import cg.creamgod.boarderless.domain.model.CanvasTransform
import cg.creamgod.boarderless.domain.model.TextNode
import cg.creamgod.boarderless.domain.model.Vec2
import cg.creamgod.boarderless.feature.canvas.HistoryDirection
import cg.creamgod.boarderless.feature.canvas.describeHistoryOperation
import cg.creamgod.boarderless.i18n.AppLanguage
import cg.creamgod.boarderless.i18n.DefaultLanguage
import cg.creamgod.boarderless.i18n.LanguagePreference
import cg.creamgod.boarderless.i18n.Localization
import cg.creamgod.boarderless.i18n.Strings
import cg.creamgod.boarderless.i18n.resolveLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalizationTest {
    private val traditionalChinese = AppLanguage("zh-TW", "繁體中文", matches = listOf("zh"))
    private val japanese = AppLanguage("ja", "日本語")
    private val available = listOf(DefaultLanguage, traditionalChinese, japanese)

    private fun <T> withCatalog(
        language: AppLanguage,
        json: String,
        block: () -> T,
    ): T {
        Localization.install(language, json)
        return try {
            block()
        } finally {
            Localization.install(DefaultLanguage, null)
        }
    }

    @Test
    fun systemLocaleMatchesExactTagsThenLanguageSubtags() {
        assertEquals(traditionalChinese, resolveLanguage(LanguagePreference.System, "zh-TW", available))
        assertEquals(traditionalChinese, resolveLanguage(LanguagePreference.System, "zh-Hant-HK", available))
        assertEquals(traditionalChinese, resolveLanguage(LanguagePreference.System, "zh_TW", available))
        assertEquals(japanese, resolveLanguage(LanguagePreference.System, "ja-JP", available))
        assertEquals(DefaultLanguage, resolveLanguage(LanguagePreference.System, "fr-FR", available))
    }

    @Test
    fun anExplicitPreferenceWinsWhenThatLanguageExists() {
        assertEquals(DefaultLanguage, resolveLanguage(LanguagePreference.of(DefaultLanguage), "zh-TW", available))
        assertEquals(japanese, resolveLanguage(LanguagePreference.fromToken("ja"), "en-US", available))
        // A preference for a language that was removed falls back to the system locale.
        assertEquals(traditionalChinese, resolveLanguage(LanguagePreference.fromToken("ko"), "zh-TW", available))
        assertEquals(LanguagePreference.System, LanguagePreference.fromToken(null))
    }

    @Test
    fun englishIsCompiledInWithNamedPlaceholders() {
        assertEquals("Opening Plan…", Strings.status.opening("Plan"))
        assertEquals("Saving 1 change…", Strings.status.savingChanges(1))
        assertEquals("Saving 3 changes…", Strings.status.savingChanges(3))
        assertEquals("Close", Strings.common.close())
    }

    @Test
    fun loadedCatalogsTranslateAndFallBackToEnglishPerKey() {
        val json =
            """
            {
              "common": { "close": "關閉" },
              "status": {
                "opening": "正在開啟 {title}…",
                "savingChanges": { "other": "正在儲存 {count} 項變更…" }
              },
              "history": { "createdObjects": { "other": "已建立 {count} 個物件" } }
            }
            """.trimIndent()
        withCatalog(traditionalChinese, json) {
            assertEquals("關閉", Strings.common.close())
            assertEquals("正在開啟 Plan…", Strings.status.opening("Plan"))
            assertEquals("正在儲存 1 項變更…", Strings.status.savingChanges(1))
            assertEquals("Unknown error", Strings.common.unknownError())
            val nodes =
                listOf("a", "b").map {
                    TextNode(id = CanvasObjectId(it), transform = CanvasTransform(Vec2.Zero, CanvasSize(10f, 10f)), text = it)
                }
            assertEquals(
                "已建立 2 個物件",
                describeHistoryOperation(CreateObjectsOperation("create", nodes), HistoryDirection.Redo),
            )
        }
        assertEquals("Close", Strings.common.close())
    }

    @Test
    fun argumentsAreSubstitutedOnlyOnce() {
        assertEquals("Deleted {opened} • Opened B", Strings.status.deletedOpened("{opened}", "B"))
    }
}
