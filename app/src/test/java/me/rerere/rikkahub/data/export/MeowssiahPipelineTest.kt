package me.rerere.rikkahub.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.rerere.rikkahub.data.model.InjectionPosition
import me.rerere.rikkahub.data.st.runtime.listPresetEmbeddedScriptBuckets
import me.rerere.rikkahub.data.st.runtime.setPresetEmbeddedScriptEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [v250] 「小猫之神」（Meowssiah）预设**全管线**覆盖测试。
 *
 * 为什么以它为准：它是社区最流行的预设之一，结构上覆盖了绝大多数社区预设会用到的特性 ——
 * 53 个 prompt（8 个 marker + 45 个内容条目，其中 28 个启用）、
 * extensions.SPreset 全套（ChatSquash / RegexBinding / ToolBindings / MessageInjections /
 * OutputPreprocessing / ForcedPostProcessing / FinalRequestProcessing / FixedPresetName）、
 * 2 个 tavern_helper 脚本、6 条正则、完整采样参数。
 *
 * 本测试锁定「导入后每一处都能被消费到」，防止再出现「导入成功但某条链路静默丢数据」。
 */
class MeowssiahPipelineTest {

    private fun load(): String {
        val candidates = listOf(
            File("src/test/resources/presets/Meowssiah-2.0.11.json"),
            File("app/src/test/resources/presets/Meowssiah-2.0.11.json"),
        )
        return candidates.first { it.exists() }.readText()
    }

    private val json by lazy { load() }

    @Test
    fun promptsAllImportWithSanePositions() {
        val injections = ModeInjectionSerializer.tryImportSillyTavernPreset(json, "Meowssiah")
        assertNotNull("Meowssiah 预设必须能解析", injections)
        val list = injections!!
        // 45 个非 marker 条目里有 2 个（version_info / SPresetSettings）**不在 prompt_order 中**，
        // 且 enabled=false —— ST 的 PromptManager 只按 prompt_order 构建 collection，
        // 这两个条目在酒馆里同样不会被发送（后者甚至是 117 KB 的 SPreset 配置镜像，不是提示词）。
        // 因此这里断言 43（= prompt_order 51 项 − 8 个 marker），与 ST 行为一致。
        assertEquals("应导入 prompt_order 中的全部非 marker 条目", 43, list.size)
        assertEquals("全部来自同一预设组", setOf("Meowssiah"), list.map { it.presetGroup }.toSet())
        val atDepth = list.filter { it.position == InjectionPosition.AT_DEPTH }
        assertTrue("相对位置条目不应被判成 AT_DEPTH", atDepth.isEmpty())
        assertTrue("所有条目内容必须非空", list.all { it.content.isNotBlank() })
        assertEquals("启用条目数应与 prompt_order 一致", 28, list.count { it.enabled })
    }

    @Test
    fun spresetSubfieldsAreAllRecognized() {
        val root = Json.parseToJsonElement(json).jsonObject
        val spreset = root["extensions"]!!.jsonObject["SPreset"]!!.jsonObject
        val expected = listOf(
            "ChatSquash", "RegexBinding", "MacroNest", "ToolBindings", "MessageInjections",
            "OutputPreprocessing", "FixedPresetName", "ForcedPostProcessing", "FinalRequestProcessing",
        )
        expected.forEach { key ->
            assertTrue("SPreset.$key 必须存在", key in spreset)
        }
        val unsupported = ModeInjectionSerializer.detectUnsupportedPresetExtensionsFromJson(json)
        expected.forEach { key ->
            assertFalse("$key 不应出现在 unsupported 列表：$unsupported", unsupported.any { it.contains(key) })
        }
    }

    @Test
    fun embeddedScriptsAreListedAndToggleable() {
        val tavernPresets = JsonObject(mapOf("Meowssiah" to JsonPrimitive(json)))

        val buckets = listPresetEmbeddedScriptBuckets(tavernPresets.toString())
        val scripts = buckets["Meowssiah"]
        assertNotNull("预设内嵌脚本必须能在管理页列出", scripts)
        assertEquals("Meowssiah 自带 2 个脚本", 2, scripts!!.size)
        assertTrue("脚本名不能为空", scripts.all { it.name.isNotBlank() })
        assertTrue("默认都是启用的", scripts.all { it.enabled })

        val target = scripts.first()
        val updated = setPresetEmbeddedScriptEnabled(tavernPresets.toString(), "Meowssiah", target.id, false)
        assertNotNull("切换预设内嵌脚本必须能写回", updated)
        val after = listPresetEmbeddedScriptBuckets(updated!!)["Meowssiah"]!!
        assertEquals("关闭后必须真的变 false", false, after.first { it.id == target.id }.enabled)
        assertEquals(2, after.size)

        val updatedRoot = Json.parseToJsonElement(
            (Json.parseToJsonElement(updated).jsonObject["Meowssiah"] as JsonPrimitive).content,
        ).jsonObject
        assertTrue("切脚本不能弄丢 SPreset", "SPreset" in updatedRoot["extensions"]!!.jsonObject)
    }

    @Test
    fun regexScriptsAllImportOnce() {
        val (state, scripts) = ModeInjectionSerializer.inspectPresetRegexScripts(json, "Meowssiah")
        assertEquals(PresetSubfieldState.PRESENT_VALID, state)
        assertEquals("正则必须内容级去重", 6, scripts.size)
        assertTrue("每条正则都要打上预设组标签", scripts.all { it.sourcePresetGroup == "Meowssiah" })
        assertTrue("每条正则都要有 findRegex", scripts.all { it.findRegex.isNotBlank() })
        assertEquals("id 不能重复", scripts.size, scripts.map { it.id }.toSet().size)
    }

    @Test
    fun samplingParamsAreParsed() {
        val cfg = parseActiveStPresetConfig(
            tavernPresetsJson = JsonObject(mapOf("Meowssiah" to JsonPrimitive(json))).toString(),
            enabledGroups = setOf("Meowssiah"),
        )
        assertEquals(1.09f.toDouble(), cfg.temperature?.toDouble() ?: 0.0, 0.001)
        assertEquals(0.98f.toDouble(), cfg.topP?.toDouble() ?: 0.0, 0.001)
        assertEquals("openai_max_tokens 必须解析", 65535, cfg.maxTokens)
        assertEquals("openai_max_context 必须解析", 2000000, cfg.maxContext)
        assertTrue("SPreset 配置必须被解析出来", cfg.spresetConfig.enabled)
        assertTrue("OutputPreprocessing 必须启用", cfg.spresetConfig.outputPreprocessing.enabled)
        assertTrue("FinalRequestProcessing 必须启用", cfg.spresetConfig.finalRequestProcessing.enabled)
        assertTrue("ToolBindings 必须解析出工具绑定", cfg.spresetConfig.toolBindings.isNotEmpty())
        assertFalse("ChatSquash 在本预设里是关闭的", cfg.spresetConfig.chatSquash.enabled)
    }
}