package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.PromptInjection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [v250] 预设脚本重复装载回归。
 *
 * 真机日志实证（Meowssiah）：
 *   [scripts] loaded 7: 4A专用版, 双马尾插件 v4.6.1, 音乐盒·独立版, SPreset, 菜单, SPreset, 菜单
 * 同一个预设脚本被挂两个 iframe —— 根因是 tavernPresetScripts 桶与 tavernPresets[group]
 * 的 extensions.tavern_helper.scripts 各存了一份，两处都读。
 *
 * 两个同 id iframe 会：抢同一批事件监听、重复注册按钮、slash 命令执行两次、变量写两遍。
 */
class TavernScriptDedupeTest {

    private fun script(id: String, name: String, content: String = "code") = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("type", JsonPrimitive("script"))
        put("enabled", JsonPrimitive(true))
        put("content", JsonPrimitive(content))
    }

    private fun presetJson(scripts: kotlinx.serialization.json.JsonArray) = buildJsonObject {
        put("extensions", buildJsonObject {
            put("tavern_helper", buildJsonObject { put("scripts", scripts) })
        })
    }.toString()

    private fun settingsFor(
        group: String = "G",
        bucket: kotlinx.serialization.json.JsonArray? = null,
        embedded: kotlinx.serialization.json.JsonArray? = null,
    ): Pair<Settings, Assistant> {
        val inj = PromptInjection.ModeInjection(name = "x", presetGroup = group, enabled = true)
        var s = Settings(modeInjections = listOf(inj))
        if (bucket != null) {
            s = s.copy(tavernPresetScripts = buildJsonObject { put(group, bucket) }.toString())
        }
        if (embedded != null) {
            s = s.copy(tavernPresets = buildJsonObject { put(group, JsonPrimitive(presetJson(embedded))) }.toString())
        }
        return s to Assistant(name = "t", modeInjectionIds = setOf(inj.id))
    }

    private fun loadedScripts(settings: Settings, assistant: Assistant): kotlinx.serialization.json.JsonArray {
        val json = buildTavernScriptsJson(assistant, settings)
        return Json.parseToJsonElement(json).jsonObject["tavern_helper"]!!.jsonObject["scripts"]!!.jsonArray
    }

    @Test
    fun sameScriptInBucketAndPresetIsLoadedOnce() {
        val s1 = script("id-1", "SPreset", "a")
        val s2 = script("id-2", "菜单", "b")
        val (settings, assistant) = settingsFor(
            bucket = buildJsonArray { add(s1); add(s2) },
            embedded = buildJsonArray { add(s1); add(s2) },
        )
        val scripts = loadedScripts(settings, assistant)
        assertEquals("同一脚本只能装载一次：$scripts", 2, scripts.size)
        assertEquals(listOf("id-1", "id-2"), scripts.map { it.jsonObject["id"]!!.jsonPrimitive.content })
    }

    @Test
    fun scriptsWithoutIdAreDedupedByContentSignature() {
        val a = buildJsonObject {
            put("name", JsonPrimitive("S"))
            put("type", JsonPrimitive("script"))
            put("content", JsonPrimitive("same-body"))
        }
        val (settings, assistant) = settingsFor(
            bucket = buildJsonArray { add(a) },
            embedded = buildJsonArray { add(a) },
        )
        assertEquals("无 id 的同内容脚本也应去重", 1, loadedScripts(settings, assistant).size)
    }

    @Test
    fun differentScriptsAreBothKept() {
        val a = script("id-a", "A", "body-a")
        val b = script("id-b", "B", "body-b")
        val (settings, assistant) = settingsFor(embedded = buildJsonArray { add(a); add(b) })
        assertEquals("不同脚本一个都不能少", 2, loadedScripts(settings, assistant).size)
    }
}