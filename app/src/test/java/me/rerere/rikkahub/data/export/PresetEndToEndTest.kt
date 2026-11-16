package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import me.rerere.rikkahub.data.st.regex.mergeActiveRegexScripts
import me.rerere.rikkahub.data.st.runtime.buildTavernCharactersJson
import me.rerere.rikkahub.data.st.runtime.buildTavernPersonasJson
import me.rerere.rikkahub.data.st.runtime.buildTavernPresetJson
import me.rerere.rikkahub.data.st.runtime.buildTavernRegexScriptsJson
import me.rerere.rikkahub.data.st.runtime.buildTavernScriptsJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.uuid.Uuid

/**
 * [v247] 真实预设「导入 → 数据源 → JS 可读」端到端链路测试。
 *
 * 为什么需要：现有的 [RealPresetCompatibilityTest] 只验证「能解析成注入条目」，
 * 不验证「解析后能不能真的被脚本读到」。历史上反复出现的问题是：
 * 解析成功了，但 `getPreset('in_use').prompts` 读到空数组、
 * `getTavernRegexes()` 读到 0 条 —— 功能看起来装好了，实际不工作。
 *
 * 本测试走完整链路：
 *  1. 预设原文 → `tryImportSillyTavernPreset`（导入）
 *  2. 模拟「该预设组已启用」的 Settings + Assistant
 *  3. 调 runtime 数据源构建器（`buildTavernPresetJson` / `buildTavernScriptsJson` /
 *     `mergeActiveRegexScripts` / `buildTavernRegexScriptsJson`）
 *  4. **断言产出非空、形状正确、关键字段齐全**
 */
class PresetEndToEndTest {

    private fun loadPreset(name: String): String {
        val candidates = listOf(
            File("src/test/resources/presets/$name"),
            File("app/src/test/resources/presets/$name"),
        )
        return candidates.first { it.exists() }.readText()
    }

    private data class Ctx(val settings: Settings, val assistant: Assistant, val presetRaw: String)

    /** 构造「某预设组已启用」的上下文（与 ChatPage 的接线方式一致）。 */
    private fun contextFor(presetFile: String, group: String): Ctx {
        val raw = loadPreset(presetFile)
        val injId = Uuid.random()
        val settings = Settings(
            tavernPresets = JsonObject(mapOf(group to JsonPrimitive(raw))).toString(),
            modeInjections = listOf(
                PromptInjection.ModeInjection(
                    id = injId, name = "entry", enabled = true, priority = 0,
                    content = "x", presetGroup = group,
                )
            ),
        )
        val assistant = Assistant(id = Uuid.random(), name = "test", modeInjectionIds = setOf(injId))
        return Ctx(settings, assistant, raw)
    }

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    // ---------------------------------------------------------------- TRANS-AM

    @Test
    fun transAmPresetReachesRuntimeAsFullObject() {
        val ctx = contextFor("TRANS-AM-4A版-v1.1.2.3.json", "TRANS-AM-4A")
        val json = buildTavernPresetJson(ctx.assistant, ctx.settings)

        assertTrue("TRANS-AM 预设应产出非空 JSON", json.isNotBlank())
        val preset = obj(json)

        // 脚本第一行就是 getPreset('in_use').prompts —— 这个数组必须存在且非空
        val prompts = preset["prompts"]?.jsonArray
        assertNotNull("getPreset().prompts 必须存在", prompts)
        assertTrue("prompts 不应为空（TRANS-AM 有大量条目）", prompts!!.size > 10)

        // 脚本用 p.id ?? p.identifier 取条目 id —— 至少要有一个能取到
        val ids = prompts.mapNotNull {
            (it as? JsonObject)?.let { o ->
                (o["id"] as? JsonPrimitive)?.contentOrNull
                    ?: (o["identifier"] as? JsonPrimitive)?.contentOrNull
            }
        }
        assertTrue("prompts[] 每项都应有 id 或 identifier，实际拿到 ${ids.size}/${prompts.size}", ids.size == prompts.size)

        // TRANS-AM 靠这些条目开关决定功能是否生效
        val knownIds = setOf("ta-auto-continue", "ta-ooc-review", "ta-transport-tool", "ta-transport-unicode")
        val present = ids.filter { it in knownIds }
        assertTrue("应包含 TRANS-AM 的关键开关条目，实际只有 $present", present.isNotEmpty())

        // 预设内的正则（TRANS-AM 实测 19 条）必须能被 D 组读到
        val regexes = mergeActiveRegexScripts(ctx.settings, ctx.assistant)
        assertTrue("TRANS-AM 预设内嵌正则应被合并（实测 19 条），实际 ${regexes.size}", regexes.size >= 10)
        val regexJson = Json.parseToJsonElement(buildTavernRegexScriptsJson(regexes)).jsonArray
        assertEquals("序列化后条数应一致", regexes.size, regexJson.size)
        // 每条都必须有 findRegex（空正则是无效的）
        assertTrue("每条正则都应有 findRegex", regexJson.all {
            ((it as? JsonObject)?.get("findRegex") as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true
        })
    }

    @Test
    fun transAmScriptsAreLoadable() {
        val ctx = contextFor("TRANS-AM-4A版-v1.1.2.3.json", "TRANS-AM-4A")
        val json = buildTavernScriptsJson(ctx.assistant, ctx.settings)
        val helper = obj(json)["tavern_helper"]!!.jsonObject
        val scripts = helper["scripts"]!!.jsonArray

        assertTrue("TRANS-AM 应装载 7 个脚本，实际 ${scripts.size}", scripts.size >= 7)
        // 每个脚本必须有 id / name / content（runtime 用它们建 iframe）
        scripts.forEach { el ->
            val s = el.jsonObject
            assertTrue("脚本缺 id", (s["id"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true)
            assertTrue("脚本缺 content", (s["content"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true)
        }
        // 声明式按钮：runtime 靠 script.button.buttons 渲染面板入口
        val withButton = scripts.count { el ->
            val b = el.jsonObject["button"] as? JsonObject
            b != null && (b["enabled"] as? JsonPrimitive)?.contentOrNull != "false"
        }
        assertTrue("TRANS-AM 应有脚本声明按钮（控制台入口），实际 $withButton 个", withButton >= 1)
    }

    // ---------------------------------------------------------------- Meowssiah

    @Test
    fun meowssiahPresetReachesRuntime() {
        val ctx = contextFor("Meowssiah-2.0.11.json", "Meowssiah")
        val json = buildTavernPresetJson(ctx.assistant, ctx.settings)
        assertTrue("Meowssiah 预设应产出非空 JSON", json.isNotBlank())
        val preset = obj(json)
        val prompts = preset["prompts"]?.jsonArray
        assertNotNull("prompts 必须存在", prompts)
        assertTrue("Meowssiah prompts 不应为空", prompts!!.isNotEmpty())

        // 脚本数量（实测 2 个）
        val scriptsJson = obj(buildTavernScriptsJson(ctx.assistant, ctx.settings))
        val scripts = scriptsJson["tavern_helper"]!!.jsonObject["scripts"]!!.jsonArray
        assertTrue("Meowssiah 应装载 2 个脚本，实际 ${scripts.size}", scripts.size >= 2)
    }

    // ---------------------------------------------------------------- 数据源完整性

    @Test
    fun runtimeDataSourcesAreAllNonEmptyForRealContext() {
        val ctx = contextFor("TRANS-AM-4A版-v1.1.2.3.json", "TRANS-AM-4A")

        // 预设
        assertTrue("预设数据源不应为空", buildTavernPresetJson(ctx.assistant, ctx.settings).isNotBlank())
        // 脚本
        val scriptsJson2 = obj(buildTavernScriptsJson(ctx.assistant, ctx.settings))
        val scripts = scriptsJson2["tavern_helper"]!!.jsonObject["scripts"]!!.jsonArray
        assertTrue("脚本数据源不应为空", scripts.isNotEmpty())
        // 正则
        val regexes = mergeActiveRegexScripts(ctx.settings, ctx.assistant)
        assertTrue("正则数据源不应为空", regexes.isNotEmpty())

        // 角色卡（当前助手没卡时返回 []，但有卡时必须非空）
        val card = Assistant(
            id = Uuid.random(),
            name = "card",
            tavernData = me.rerere.rikkahub.data.model.TavernCharacterData(name = "Card", description = "d"),
        )
        val chars = Json.parseToJsonElement(
            buildTavernCharactersJson(ctx.settings.copy(assistants = listOf(card)), card.id.toString())
        ).jsonArray
        assertTrue("角色卡数据源不应为空", chars.isNotEmpty())

        // 人设
        val persona = me.rerere.rikkahub.data.model.Persona(id = Uuid.random(), name = "User")
        val personas = obj(buildTavernPersonasJson(ctx.settings.copy(personas = listOf(persona))))
        assertTrue("人设数据源不应为空", personas["personas"]!!.jsonObject.isNotEmpty())
    }

    @Test
    fun everyRealPresetProducesUsableRuntimeShape() {
        val presets = listOf(
            "Meowssiah-2.0.11.json" to "Meowssiah",
            "TRANS-AM-4A版-v1.1.2.3.json" to "TRANS-AM-4A",
            "TRANS-AM-4H版-v1.0.0.1.json" to "TRANS-AM-4H",
        )
        presets.forEach { (file, group) ->
            val ctx = contextFor(file, group)
            val presetJson = buildTavernPresetJson(ctx.assistant, ctx.settings)
            assertTrue("$file: 预设 JSON 不应为空", presetJson.isNotBlank())
            val preset = runCatching { obj(presetJson) }.getOrNull()
            assertNotNull("$file: 预设 JSON 应可解析", preset)
            assertNotNull("$file: prompts 必须存在", preset!!["prompts"])
            assertTrue("$file: prompts 不应为空", (preset["prompts"] as? JsonArray)!!.isNotEmpty())

            // 正则形状必须可被 ST regex-engine 消费
            val regexes = mergeActiveRegexScripts(ctx.settings, ctx.assistant)
            if (regexes.isNotEmpty()) {
                val arr = Json.parseToJsonElement(buildTavernRegexScriptsJson(regexes)).jsonArray
                arr.forEach { el ->
                    val o = el.jsonObject
                    listOf("id", "scriptName", "findRegex", "replaceString", "placement", "disabled").forEach { key ->
                        assertTrue("$file: 正则缺字段 $key", o.containsKey(key))
                    }
                }
            }
        }
    }
}
