package me.rerere.rikkahub.data.st.import

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.TavernCharacterData
import me.rerere.rikkahub.data.st.regex.parseCardRegexScripts
import me.rerere.rikkahub.data.st.runtime.buildTavernScriptsJson
import me.rerere.rikkahub.data.st.runtime.listCardScripts
import me.rerere.rikkahub.data.st.runtime.setCardScriptEnabled
import me.rerere.rikkahub.ui.pages.assistant.detail.parseEmbeddedBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真实复杂角色卡导入回归（用户素材，2026-09-28）：
 * - `magical_girl_card.json`：chara_card_v3「合成样例卡」
 *   = 47 条世界书（46 常驻，before_char 3 / after_char 44）+ 11 条正则 + 3 个酒馆助手脚本
 *   （含 MVU 变量框架 `mvu_bundle_full.js`）+ depth_prompt——社区复杂卡的典型形态；
 * - `kelivo_wanohi_card.json` / `kelivo_kasumi_card.json`：kelivo 导出格式（非 ST 标准），冒烟容错。
 *
 * 覆盖链路：extensions.regex_scripts → 内嵌正则；character_book → 内嵌世界书；
 * extensions.tavern_helper.scripts → 卡脚本装载（MVU 运行前提）与启停开关。
 */
class RealCardV3ImportTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing $name" }
            .bufferedReader().use { it.readText() }
        return json.parseToJsonElement(text).jsonObject
    }

    private fun data(card: JsonObject): JsonObject = card["data"]?.jsonObject ?: card

    // ========== 魔法少女：内嵌正则 ==========

    @Test
    fun `magical girl regex scripts parse with full fields`() {
        val ext = data(load("magical_girl_card.json"))["extensions"]?.jsonObject
        val scripts = parseCardRegexScripts(ext)
        assertEquals(11, scripts.size)
        val opening = scripts.first()
        assertTrue(opening.findRegex.isNotBlank())
        assertTrue(scripts.all { it.scriptName.isNotBlank() })
        assertTrue(scripts.all { it.findRegex.isNotBlank() })
    }

    // ========== 魔法少女：内嵌世界书（47 条） ==========

    @Test
    fun `magical girl embedded book has 47 entries with position and constant distribution`() {
        val book = parseEmbeddedBook(data(load("magical_girl_card.json"))["character_book"]?.jsonObject)
        assertNotNull(book)
        assertEquals(47, book!!.entries.size)
        assertEquals(46, book.entries.count { it.constant })
        // 位置语义对齐 ST world-info.js（position: entry.extensions?.position ?? 字符串 fallback）：
        // 本卡 44 条字符串 after_char 的扩展数字为 4（官方枚举 4=atDepth，深度注入形态），
        // 3 条 before_char 的扩展数字为 0（before）——扩展数字优先，解析结果应与 ST 一致。
        assertEquals(3, book.entries.count { it.position == 0 })
        assertEquals(44, book.entries.count { it.position == 4 })
        // 卡内合法存在少量占位空条目（id 37/44），解析层应原样保留；其余条目内容/关键词非空
        assertTrue(book.entries.count { it.content.isNotBlank() || it.keys.isNotEmpty() } >= 45)
        // [v241] ST 1.18 的 character_book 启停字段是 enabled（官方写 enabled、读 enabled → disable = !enabled）：
        // 本卡 3 条 enabled=false 必须映射为禁用，不能全部当作启用
        assertEquals(3, book.entries.count { it.disable })
        assertEquals(44, book.entries.count { !it.disable })
    }

    @Test
    fun `entry position string fallback maps like st`() {
        // 无 extensions.position 时按字符串 fallback：before_char→0、after_char→1（ST world-info.js L5517）
        val mini = """
            {"entries": [
                {"id": 1, "keys": ["a"], "content": "x", "position": "before_char"},
                {"id": 2, "keys": ["b"], "content": "y", "position": "after_char"},
                {"id": 3, "keys": ["c"], "content": "z", "position": "at_depth", "extensions": {"position": 4}},
                {"id": 4, "keys": ["d"], "content": "w", "position": "EMTop"},
                {"id": 5, "keys": ["e"], "content": "v", "position": "at_depth"}
            ]}
        """.trimIndent()
        val book = parseEmbeddedBook(json.parseToJsonElement(mini).jsonObject)
        assertNotNull(book)
        val byId = book!!.entries.associateBy { it.id }
        assertEquals(0, byId[1]?.position)
        assertEquals(1, byId[2]?.position)
        assertEquals(4, byId[3]?.position)
        assertEquals(5, byId[4]?.position)
        assertEquals(4, byId[5]?.position)
    }

    // ========== 魔法少女：酒馆助手脚本（MVU）装载与启停 ==========

    @Test
    fun `magical girl tavern helper scripts load and toggle`() {
        val extRaw = data(load("magical_girl_card.json"))["extensions"].toString()
        val assistant = Assistant(tavernData = TavernCharacterData(extensionsRaw = extRaw))
        val briefs = listCardScripts(assistant)
        assertEquals(3, briefs.size)
        assertTrue(briefs.any { it.name == "SYNTH-TEXT-0048" })
        assertTrue(briefs.any { it.name == "SYNTH-TEXT-0059" })
        assertTrue(briefs.any { it.name == "SYNTH-TEXT-0062" })

        // 三源合并装载结果应包含 MVU bundle（运行时按 JSR 约定装入 TH-script iframe）
        val merged = buildTavernScriptsJson(assistant, Settings())
        assertTrue(merged.contains("tavern_helper"))
        assertTrue(merged.contains("SYNTH-TEXT-0048"))

        // 启停开关：禁用后卡脚本应标记 enabled=false
        val target = briefs.first { it.name == "SYNTH-TEXT-0048" }
        val updated = setCardScriptEnabled(extRaw, target.id, false)
        assertNotNull(updated)
        val after = listCardScripts(Assistant(tavernData = TavernCharacterData(extensionsRaw = updated!!)))
        assertFalse(after.first { it.name == "SYNTH-TEXT-0048" }.enabled)
        assertTrue(after.first { it.name == "SYNTH-TEXT-0059" }.enabled)
    }

    // ========== kelivo 导出格式冒烟 ==========

    @Test
    fun `kelivo exported cards parse without crash`() {
        listOf("kelivo_wanohi_card.json", "kelivo_kasumi_card.json").forEach { name ->
            val d = data(load(name))
            assertTrue("$name 顶层 name 丢失", d["name"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true)
            // 这两张 kelivo 卡无 regex/book/scripts，解析层应返回空而非抛异常
            assertTrue(parseCardRegexScripts(d["extensions"]?.jsonObject).isEmpty())
            if (d["character_book"] != null) {
                parseEmbeddedBook(d["character_book"]?.jsonObject)
            }
        }
    }

    // ========== 容错：坏输入不崩 ==========

    @Test
    fun `malformed inputs are tolerated`() {
        assertTrue(parseCardRegexScripts(null).isEmpty())
        assertTrue(parseCardRegexScripts(JsonObject(emptyMap())).isEmpty())
        assertEquals(null, parseEmbeddedBook(null))
    }
}