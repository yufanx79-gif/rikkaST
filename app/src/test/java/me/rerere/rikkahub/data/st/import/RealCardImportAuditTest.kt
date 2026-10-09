package me.rerere.rikkahub.data.st.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.st.runtime.buildTavernScriptsJson
import me.rerere.rikkahub.data.st.runtime.listCardScripts
import me.rerere.rikkahub.ui.pages.assistant.detail.parseCardJson
import me.rerere.rikkahub.ui.pages.assistant.detail.parseEmbeddedBook
import me.rerere.rikkahub.utils.CardExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v241] 真实复杂卡「导入 → 导出 → 再导入」全链路审计。
 *
 * 素材是主人桌面上的真实成品卡（`Desktop/角色卡/` 原图内嵌 `ccv3` 的 JSON 原样抽出）：
 * - `user_card_campus.json`：SYNTH-TEXT-0332 —— 499 条内嵌世界书（63 常驻 / 77 启用 / 422 禁用）、
 *   6 条正则（placement=[2]）、9 条 MVU tavern_helper 脚本、`odysseia_trace` 扩展、`extensions.world` 绑定；
 * - `real_card_xingyu.json`：合成样例卡 —— 42 条世界书、11 条正则（placement 有 [2] 与 [1,2] 两种）、
 *   2 条脚本、`odysseia_trace`。
 *
 * 覆盖的兼容点（v241 修复的回归护栏）：
 * 1. `character_book.entries[].enabled`（ST 1.18 规范字段，官方写 enabled 读 enabled → disable = !enabled）
 *    必须正确映射成启用/禁用，不能把 422 条禁用条目全放出来；
 * 2. 导出侧 `entries` 必须是**数组**且每项带 `id`、写 `enabled`（ST 的 convertCharacterBook 用
 *    `.forEach` 读数组、只认 enabled），`extensions.role` 必须是数字；
 * 3. `regex_scripts` 的 placement 数组必须逐条保留；
 * 4. tavern_helper 脚本与 `extensionsRaw`（含 odysseia_trace）无损透传。
 */
class RealCardImportAuditTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing $name" }
            .bufferedReader().use { it.readText() }
        return json.parseToJsonElement(text).jsonObject
    }

    @Test
    fun `user campus card imports fully with enabled state and scripts`() {
        val card = load("user_card_campus.json")
        val (assistant, books) = parseCardJson(card)
        val tav = requireNotNull(assistant.tavernData)

        assertEquals("SYNTH-TEXT-0332", assistant.name)
        assertEquals("chara_card_v3", tav.spec)
        assertEquals("SYNTH-TEXT-0332", tav.extensions["world"])

        // 6 条内嵌正则，placement 全部保留
        assertEquals(6, tav.embeddedRegexScripts.size)
        assertTrue(tav.embeddedRegexScripts.all { it.placement == listOf(2) })

        // 9 条 MVU 脚本全部装载
        val scripts = listCardScripts(assistant)
        assertEquals(9, scripts.size)
        assertEquals(
            setOf(
                "SYNTH-TEXT-0334", "SYNTH-TEXT-0338", "SYNTH-TEXT-0342", "SYNTH-TEXT-0346", "SYNTH-TEXT-0350",
                "SYNTH-TEXT-0354", "SYNTH-TEXT-0358", "SYNTH-TEXT-0362", "SYNTH-TEXT-0366",
            ),
            scripts.map { it.name }.toSet(),
        )
        assertTrue(scripts.all { it.enabled })

        // 499 条世界书：63 常驻；enabled=false 的 422 条必须是禁用（v241 修复点）
        val book = books.single()
        assertTrue(book.isCharacterBook)
        assertEquals("SYNTH-TEXT-0332", book.name)
        assertEquals(499, book.entries.size)
        assertEquals(63, book.entries.count { it.constantActive })
        assertEquals(77, book.entries.count { it.enabled })
        assertEquals(422, book.entries.count { !it.enabled })

        // 导出 → 再导入：ST 规范形状 + 内容/启用态零丢失
        val exported = CardExporter.buildV3CardJson(assistant)
        val exportedRoot = json.parseToJsonElement(exported).jsonObject
        val exportedBook = requireNotNull(exportedRoot["data"]?.jsonObject?.get("character_book")?.jsonObject)
        val exportedEntries = exportedBook["entries"] as JsonArray
        assertEquals(499, exportedEntries.size)
        assertTrue("每项必须带 id（ST 数组形态）", exportedEntries.all { it.jsonObject["id"] != null })
        assertEquals(
            "启用态必须写进 ST 规范的 enabled 字段",
            77,
            exportedEntries.count { it.jsonObject["enabled"]?.jsonPrimitive?.contentOrNull == "true" },
        )
        assertEquals(
            422,
            exportedEntries.count { it.jsonObject["enabled"]?.jsonPrimitive?.contentOrNull == "false" },
        )
        // ST 的 extensions.role 是数字
        val roleRaw = exportedEntries.first().jsonObject["extensions"]?.jsonObject?.get("role")
            ?.jsonPrimitive?.contentOrNull
        assertNotNull(roleRaw)
        assertNotNull("extensions.role 必须是数字（ST extension_prompt_roles）", roleRaw!!.toIntOrNull())

        val reimported = requireNotNull(parseEmbeddedBook(exportedBook))
        // Lorebook.entries 是 RegexInjection（keywords/enabled），重新解析回来的是 TavernBookEntry（keys/disable）
        assertEquals(book.entries.map { it.content }, reimported.entries.map { it.content })
        assertEquals(book.entries.map { it.enabled }, reimported.entries.map { !it.disable })
        assertEquals(book.entries.map { it.keywords }, reimported.entries.map { it.keys })

        // 脚本 + 原始扩展（含 odysseia_trace）随导出无损
        assertTrue(exported.contains("SYNTH-TEXT-0338"))
        assertTrue(exported.contains("odysseia_trace"))

        // 运行时脚本装载 JSON：9 条脚本 + variables 通道
        val runtimeJson = buildTavernScriptsJson(assistant, Settings())
        assertTrue(runtimeJson.contains("SYNTH-TEXT-0338"))
        assertTrue(runtimeJson.contains("\"variables\""))
    }

    @Test
    fun `xingyu card keeps regex placement variants and trace extension`() {
        val (assistant, books) = parseCardJson(load("real_card_xingyu.json"))
        val tav = requireNotNull(assistant.tavernData)

        assertEquals("SYNTH-TEXT-0158", assistant.name)
        assertEquals(11, tav.embeddedRegexScripts.size)
        assertEquals(3, tav.embeddedRegexScripts.count { it.placement == listOf(1, 2) })
        assertEquals(8, tav.embeddedRegexScripts.count { it.placement == listOf(2) })

        val book = books.single()
        assertEquals(42, book.entries.size)
        assertEquals(25, book.entries.count { it.constantActive })
        assertEquals(41, book.entries.count { it.enabled })
        assertEquals(1, book.entries.count { !it.enabled })

        assertEquals(2, listCardScripts(assistant).size)
        assertTrue(tav.extensionsRaw.contains("odysseia_trace"))
    }
}
