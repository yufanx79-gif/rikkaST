package me.rerere.rikkahub.data.st.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TavernBookEntry
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.model.TavernEmbeddedBook
import me.rerere.rikkahub.data.model.parseWorldInfoDecorators
import me.rerere.rikkahub.ui.pages.assistant.detail.parseEmbeddedBook
import me.rerere.rikkahub.utils.CardExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * W2 无损回归：世界书条目的导入 → 导出往返必须逐字一致（硬约束 #1）。
 *
 * 覆盖：
 * 1. `app/src/test/resources/magical_girl_card.json`（47 条世界书的真实复杂卡）：
 *    重新解析后的 embeddedBook 经 [CardExporter.buildV3CardJson] 导出，再解析回来，
 *    **内容/关键词/全部语义字段保持不变**；
 * 2. 带 `@@activate` / `@@dont_activate` 装饰器的条目：装饰器行在导出 JSON 里原样保留
 *    （扫描期的剥壳只作用于派生的扫描副本，不落库、不改导出）。
 */
class WorldInfoDecoratorLosslessTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing $name" }
            .bufferedReader().use { it.readText() }
        return json.parseToJsonElement(text).jsonObject
    }

    private fun data(card: JsonObject): JsonObject = card["data"]?.jsonObject ?: card

    /** 导出 → 再解析回 TavernEmbeddedBook。 */
    private fun roundTrip(book: TavernEmbeddedBook): TavernEmbeddedBook {
        val assistant = Assistant(
            name = "round-trip",
            tavernData = TavernCharacterData(name = "round-trip", embeddedBook = book),
        )
        val exported = CardExporter.buildV3CardJson(assistant)
        val exportedRoot = json.parseToJsonElement(exported).jsonObject
        val exportedBook = exportedRoot["data"]?.jsonObject?.get("character_book")?.jsonObject
        requireNotNull(exportedBook) { "导出 JSON 缺少 character_book" }
        return requireNotNull(parseEmbeddedBook(exportedBook)) { "导出 JSON 无法重新解析 character_book" }
    }

    @Test
    fun `magical girl 47-entry book survives import export round trip`() {
        val original = requireNotNull(parseEmbeddedBook(data(load("magical_girl_card.json"))["character_book"]?.jsonObject))
        assertEquals(47, original.entries.size)

        val reimported = roundTrip(original)
        assertEquals("条目数量必须保持一致", 47, reimported.entries.size)

        // 核心无损断言：正文逐字一致（含换行 / 空白 / 特殊字符）
        assertEquals(
            original.entries.map { it.content },
            reimported.entries.map { it.content },
        )
        // 关键词一致
        assertEquals(original.entries.map { it.keys }, reimported.entries.map { it.keys })
        assertEquals(original.entries.map { it.secondaryKeys }, reimported.entries.map { it.secondaryKeys })

        // 全部语义字段一致（extensionsRaw 由导出时按当前字段重建，单独排除）
        assertEquals(
            original.entries.map { it.copy(extensionsRaw = "") },
            reimported.entries.map { it.copy(extensionsRaw = "") },
        )

        // 原始对象没有被扫描/派生读取改写
        assertEquals(
            data(load("magical_girl_card.json")).let { d ->
                parseEmbeddedBook(d["character_book"]?.jsonObject)!!.entries.map { it.content }
            },
            original.entries.map { it.content },
        )
    }

    @Test
    fun `decorator lines survive import export byte exact`() {
        val decorated = TavernEmbeddedBook(
            name = "deco",
            entries = listOf(
                TavernBookEntry(
                    id = 1,
                    keys = listOf("alpha"),
                    content = "@@activate\nFORCED-CONTENT",
                    comment = "forced",
                ),
                TavernBookEntry(
                    id = 2,
                    keys = listOf("beta"),
                    content = "@@dont_activate\nSUPPRESSED-CONTENT\nSECOND-LINE",
                    comment = "suppressed",
                ),
            ),
        )

        val reimported = roundTrip(decorated)

        assertEquals(
            listOf("@@activate\nFORCED-CONTENT", "@@dont_activate\nSUPPRESSED-CONTENT\nSECOND-LINE"),
            reimported.entries.map { it.content },
        )
        // 装饰器行必须原样出现在导出 JSON 文本里（不是被剥壳后落库）
        val exported = CardExporter.buildV3CardJson(
            Assistant(name = "x", tavernData = TavernCharacterData(name = "x", embeddedBook = decorated)),
        )
        assertTrue(exported.contains("@@activate\\nFORCED-CONTENT"))
        assertTrue(exported.contains("@@dont_activate\\nSUPPRESSED-CONTENT\\nSECOND-LINE"))

        // 派生解析只读：存储对象本身仍保留装饰器
        assertEquals("@@activate\nFORCED-CONTENT", decorated.entries[0].content)
        assertEquals(
            listOf("@@dont_activate"),
            parseWorldInfoDecorators(decorated.entries[1].content).decorators,
        )
    }
}
