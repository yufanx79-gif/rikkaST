package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.ai.transformers.collectInjections
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Lorebook
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

/**
 * [v249] 缺口 1 / 缺口 2 的行为验证测试。
 *
 * 每条测试都对应"修复前一定失败、修复后才通过"的可观察行为差异，
 * 而不是"编译通过/不报错"——编译通过不能证明语义真的生效。
 */
class VectorizedPropagationTest {

    // ============ 缺口 2：vectorized 读路径（Kotlin → JS 下发） ============

    /**
     * 修复前：entryToStJson 把 vectorized 写死成 false，
     * 卡/插件读到的 strategy.type 恒为 selective。此测试证明读路径真的通了。
     */
    @Test
    fun vectorizedTrueSurvivesDownstreamJson() {
        val book = Lorebook(
            name = "语义测试书",
            entries = listOf(
                PromptInjection.RegexInjection(
                    id = Uuid.random(),
                    name = "向量化条目",
                    content = "条目内容",
                    keywords = listOf("关键词"),
                    vectorized = true,
                ),
                PromptInjection.RegexInjection(
                    id = Uuid.random(),
                    name = "普通条目",
                    content = "普通内容",
                    keywords = listOf("普通"),
                    vectorized = false,
                ),
            ),
        )

        val json = Json.parseToJsonElement(lorebookToStJson(book)).jsonObject
        val entries = json["entries"]!!.jsonObject

        // 关键断言：向量化条目必须真的是 true，普通条目必须是 false
        assertTrue(
            "vectorized=true 的条目下发到 JS 后必须还是 true",
            entries["0"]!!.jsonObject["vectorized"]!!.jsonPrimitive.boolean,
        )
        assertFalse(
            "vectorized=false 的条目不应被误标为 true",
            entries["1"]!!.jsonObject["vectorized"]!!.jsonPrimitive.boolean,
        )
    }

    // ============ 缺口 2：vectorized 写路径（JSR updateWorldbookWith） ============

    /**
     * 修复前：jsrEntryToRegexInjection 不映射 vectorized，写入被静默丢弃。
     * 此测试证明卡能把条目写成 vectorized，且回读一致（往返无损）。
     */
    @Test
    fun vectorizedRoundTripThroughJsrUpsert() {
        val book = Lorebook(name = "往返书", entries = emptyList())
        val payload = """
            {"upsert":[{
                "uid": 0,
                "name": "向量条目",
                "content": "向量内容",
                "enabled": true,
                "strategy": {"type": "vectorized", "keys": ["触发词"]},
                "position": {"type": "after_character_definition", "role": "system", "depth": 4, "order": 100},
                "probability": 100
            }]}
        """.trimIndent()

        val updated = applyLorebookUpsertPatch(book, payload)
        checkNotNull(updated) { "upsert 应产生实际变更" }

        // ① 写进去的就是 vectorized
        assertTrue("写入后条目必须带 vectorized=true", updated.entries[0].vectorized)

        // ② 回读出来的 JSON 也必须还是 vectorized（往返无损）
        val json = Json.parseToJsonElement(lorebookToStJson(updated)).jsonObject
        val entry = json["entries"]!!.jsonObject["0"]!!.jsonObject
        assertTrue("回读 JSON 的 vectorized 必须仍为 true", entry["vectorized"]!!.jsonPrimitive.boolean)
    }

    /**
     * 边界：把已有 vectorized 条目改回 selective，旧值不得复活（防"永远关不掉"）。
     */
    @Test
    fun vectorizedCanBeTurnedOffByJsrUpsert() {
        val book = Lorebook(
            name = "关闭书",
            entries = listOf(
                PromptInjection.RegexInjection(
                    id = Uuid.random(),
                    name = "原向量条目",
                    content = "内容",
                    keywords = listOf("k"),
                    vectorized = true,
                ),
            ),
        )
        val payload = """
            {"upsert":[{
                "uid": 0,
                "name": "原向量条目",
                "content": "内容",
                "strategy": {"type": "selective", "keys": ["k"]},
                "position": {"type": "after_character_definition"}
            }]}
        """.trimIndent()

        val updated = applyLorebookUpsertPatch(book, payload)
        checkNotNull(updated)
        assertFalse(
            "显式改回 selective 后 vectorized 必须为 false，不能被旧值覆盖",
            updated.entries[0].vectorized,
        )
    }

    // ============ 缺口 3：vectorMaxEntries 数量上限 ============

    private fun vectorBook(count: Int): Lorebook {
        val entries = (1..count).map { i ->
            PromptInjection.RegexInjection(
                id = Uuid.random(),
                name = "vec$i",
                content = "VEC_CONTENT_$i",
                keywords = listOf("zz_never_hit_$i"), // 永不命中，强制走向量分支
                vectorized = true,
                constantActive = false,
            )
        }
        return Lorebook(name = "向量库", entries = entries)
    }

    /**
     * 修复前：只要 sim>=threshold 就全部激活，没有数量上限。
     * 此测试证明上限真的截断了，且留下的是相似度最高的那几条。
     */
    @Test
    fun vectorActivationRespectsMaxEntriesAndKeepsHighestSimilarity() {
        val book = vectorBook(10)
        val assistant = Assistant(lorebookIds = setOf(book.id))
        val messages = listOf(UIMessage.user("query"))

        // 注入语义打分器：VEC_CONTENT_i 的相似度 = i/100，故第 10 条最高、第 1 条最低
        val scorer: (String, String) -> Double = { _, doc ->
            // doc = keywords+secondaryKeys+name+content。
            // 基线 0.5 保证全部高于默认阈值 0.35（否则 1/100..10/100 会全被阈值滤掉，
            // 测出来的是"阈值过滤"而不是"数量上限"，那就测不到要验的东西了）。
            val n = doc.substringAfter("VEC_CONTENT_", "").takeWhile { it.isDigit() }.toIntOrNull() ?: 0
            0.5 + n.toDouble() / 100.0
        }

        val result = collectInjections(
            messages = messages,
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
            semanticScorer = scorer,
            vectorMaxEntries = 3,
        )

        val vecContents = result
            .filterIsInstance<PromptInjection.RegexInjection>()
            .map { it.content }
            .filter { it.startsWith("VEC_CONTENT_") }
            .sorted()

        // 必须恰好 3 条，且是相似度最高的 8/9/10
        assertEquals(
            "vectorMaxEntries=3 时必须只激活 3 条向量条目",
            3, vecContents.size,
        )
        assertEquals(
            "必须保留相似度最高的 3 条（8/9/10），而不是顺序前 3 条",
            listOf("VEC_CONTENT_10", "VEC_CONTENT_8", "VEC_CONTENT_9"),
            vecContents,
        )
    }

    /**
     * 兼容：vectorMaxEntries<=0 表示不限制，保持旧行为（全部激活）。
     */
    @Test
    fun vectorActivationUnlimitedWhenMaxEntriesNonPositive() {
        val book = vectorBook(6)
        val assistant = Assistant(lorebookIds = setOf(book.id))
        val messages = listOf(UIMessage.user("query"))
        val scorer: (String, String) -> Double = { _, _ -> 0.99 }

        val result = collectInjections(
            messages = messages,
            assistant = assistant,
            modeInjections = emptyList(),
            lorebooks = listOf(book),
            semanticScorer = scorer,
            vectorMaxEntries = 0,
        )

        val vecCount = result
            .filterIsInstance<PromptInjection.RegexInjection>()
            .count { it.content.startsWith("VEC_CONTENT_") }
        assertEquals("vectorMaxEntries=0 必须不限制，6 条全部激活", 6, vecCount)
    }

    // ============ 缺口 2 补充：ST 事件载荷（TavernWorldInfoEvents） ============

    /**
     * 真机取证：worldinfo_entries_loaded 里的条目缺 vectorized 字段，
     * 导致事件订阅方（插件）看到的策略类型与 entryToStJson 不一致。
     */
    @Test
    fun worldInfoEventPayloadCarriesVectorized() {
        val entry = PromptInjection.RegexInjection(
            id = Uuid.random(),
            name = "事件向量条目",
            content = "内容",
            keywords = listOf("k"),
            vectorized = true,
        )
        val payload = TavernWorldInfoEvents.entriesLoadedPayload(
            globalLore = emptyList(),
            characterLore = listOf(entry),
        )
        val arr = Json.parseToJsonElement(payload).jsonObject["characterLore"]!!.jsonArray
        val first = arr[0].jsonObject
        assertTrue(
            "worldinfo_entries_loaded 载荷必须带 vectorized=true",
            first["vectorized"]!!.jsonPrimitive.boolean,
        )
    }
}
