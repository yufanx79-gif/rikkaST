package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

/**
 * [v246] `getPreset()` 数据源回归测试。
 *
 * 背景：TRANS-AM / SPreset 系脚本第一行就是 `getPreset('in_use').prompts`，
 * 此前 runtime 完全没有该函数 → `TypeError: getPreset is not a function`
 * → 控制台面板打不开、输出开关 / 历史窗口 / 决策抽签全部静默失效。
 *
 * 本测试锁定 [buildTavernPresetJson] 的输出契约：
 *  1. 无生效预设 → 空串（runtime 转 null，脚本 `getPreset()?.prompts` 安全短路）
 *  2. 有生效预设 → 可解析对象，且 `prompts` 数组长度与原预设一致
 *  3. `prompts[]` 每项都能取到 `id` 或 `identifier`（脚本写的是 `p.id ?? p.identifier`）
 */
class TavernPresetBridgeTest {

    private fun loadPreset(name: String): String {
        val candidates = listOf(
            File("src/test/resources/presets/$name"),
            File("app/src/test/resources/presets/$name"),
        )
        return candidates.first { it.exists() }.readText()
    }

    /** 构造「某预设组已启用」的 settings + assistant。 */
    private fun activePreset(label: String, raw: String): Pair<Settings, Assistant> {
        val injId = Uuid.random()
        val injection = PromptInjection.ModeInjection(
            id = injId,
            name = "entry",
            enabled = true,
            priority = 0,
            content = "x",
            presetGroup = label,
        )
        val settings = Settings(
            tavernPresets = JsonObject(mapOf(label to JsonPrimitive(raw))).toString(),
            modeInjections = listOf(injection),
        )
        val assistant = Assistant(
            id = Uuid.random(),
            name = "test",
            modeInjectionIds = setOf(injId),
        )
        return settings to assistant
    }

    @Test
    fun noActivePresetReturnsEmptyString() {
        assertEquals("无 settings", "", buildTavernPresetJson(null, null))
        assertEquals("空 settings", "", buildTavernPresetJson(null, Settings()))
        // 有预设但没启用任何组
        val settings = Settings(
            tavernPresets = JsonObject(mapOf("x" to JsonPrimitive("{}"))).toString(),
        )
        assertEquals("未启用组", "", buildTavernPresetJson(null, settings))
    }

    @Test
    fun activePresetReturnsFullPromptsArray() {
        val raw = loadPreset("TRANS-AM-4A版-v1.1.2.3.json")
        val (settings, assistant) = activePreset("TRANS-AM-4A", raw)

        val out = buildTavernPresetJson(assistant, settings)
        assertTrue("应返回非空 JSON", out.isNotBlank())

        val obj = Json.parseToJsonElement(out).jsonObject
        val prompts = obj["prompts"] as? JsonArray
        assertNotNull("必须含 prompts 数组", prompts)
        assertEquals("prompts 数量应与原预设一致", 101, prompts!!.size)
        assertNotNull("应补上 name 字段供 getPreset(name) 匹配", obj["name"])
    }

    @Test
    fun everyPromptExposesIdOrIdentifier() {
        val raw = loadPreset("Meowssiah-2.0.11.json")
        val (settings, assistant) = activePreset("Meowssiah", raw)

        val out = buildTavernPresetJson(assistant, settings)
        val prompts = Json.parseToJsonElement(out).jsonObject["prompts"] as JsonArray

        var missing = 0
        for (p in prompts) {
            val o = p.jsonObject
            val id = (o["id"] as? JsonPrimitive)?.contentOrNull
            val ident = (o["identifier"] as? JsonPrimitive)?.contentOrNull
            if (id == null && ident == null) missing++
        }
        assertEquals("每条 prompt 都应能取到 id 或 identifier", 0, missing)
    }

    @Test
    fun threeRealPresetsAllProduceParsableJson() {
        val cases = listOf(
            "Meowssiah-2.0.11.json" to 53,
            "TRANS-AM-4A版-v1.1.2.3.json" to 101,
            "TRANS-AM-4H版-v1.0.0.1.json" to 102,
        )
        for ((file, expectedCount) in cases) {
            val raw = loadPreset(file)
            val (settings, assistant) = activePreset(file, raw)
            val out = buildTavernPresetJson(assistant, settings)
            val obj = Json.parseToJsonElement(out).jsonObject
            val prompts = obj["prompts"] as JsonArray
            assertEquals("$file prompts 数量", expectedCount, prompts.size)
        }
    }
}
