package me.rerere.rikkahub.data.st.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [v246] 声明式脚本按钮（`script.button` 字段）提取测试。
 *
 * 背景：JSR 的按钮有两种来源 ——
 *  1. **声明式**：脚本 JSON 里 `button: {enabled, buttons:[{name, visible}]}`
 *  2. **运行时**：脚本代码调 `replaceScriptButtons([...])`
 *
 * 我们此前只支持 (2)。实测三个社区预设**全部**走 (1)：
 *   - TRANS-AM 的 7 个脚本里，0 个调 replaceScriptButtons，
 *     但「控制台」脚本的 button.enabled=true 且声明了 `⚙️ TRANS-AM 控制台`。
 *   - 这就是「导入后按钮/面板不出现」的根因（用户反馈的 KEMINI 面板问题）。
 *
 * 本测试锁定提取规则，防止回退。
 */
class DeclaredScriptButtonsTest {

    private fun loadPreset(name: String): String {
        val candidates = listOf(
            File("src/test/resources/presets/$name"),
            File("app/src/test/resources/presets/$name"),
        )
        return candidates.first { it.exists() }.readText()
    }

    /** 复刻 runtime.js 里 syncDeclaredScriptButtons 的提取规则。 */
    private fun extractDeclaredButtons(presetJson: String): List<Pair<String, String>> {
        val root = Json.parseToJsonElement(presetJson).jsonObject
        val scripts = ((root["extensions"] as? JsonObject)
            ?.get("tavern_helper") as? JsonObject)
            ?.get("scripts") as? JsonArray ?: return emptyList()

        val out = mutableListOf<Pair<String, String>>()
        val seen = mutableSetOf<String>()
        for (el in scripts) {
            val sc = el as? JsonObject ?: continue
            // 只处理 type=script 且 enabled != false
            val type = (sc["type"] as? JsonPrimitive)?.contentOrNull
            if (type != null && type != "script") continue
            val scEnabled = (sc["enabled"] as? JsonPrimitive)?.booleanOrNull
            if (scEnabled == false) continue

            val btnCfg = sc["button"] as? JsonObject ?: continue
            if ((btnCfg["enabled"] as? JsonPrimitive)?.booleanOrNull == false) continue
            val buttons = btnCfg["buttons"] as? JsonArray ?: continue

            val scriptId = (sc["id"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            for (b in buttons) {
                val bo = b as? JsonObject ?: continue
                if ((bo["visible"] as? JsonPrimitive)?.booleanOrNull == false) continue
                val name = (bo["name"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (name.isEmpty()) continue
                if (!seen.add("$scriptId::$name")) continue
                out += scriptId to name
            }
        }
        return out
    }

    @Test
    fun transAmPresetDeclaresConsoleButton() {
        val buttons = extractDeclaredButtons(loadPreset("TRANS-AM-4A版-v1.1.2.3.json"))
        assertEquals("TRANS-AM-4A 应声明 1 个按钮", 1, buttons.size)
        val names = buttons.map { it.second }
        assertTrue(
            "应包含控制台按钮，实际: $names",
            names.any { it.contains("TRANS-AM 控制台") },
        )
    }

    @Test
    fun transAm4HAlsoDeclaresConsoleButton() {
        val buttons = extractDeclaredButtons(loadPreset("TRANS-AM-4H版-v1.0.0.1.json"))
        assertEquals("TRANS-AM-4H 应声明 1 个按钮", 1, buttons.size)
        assertTrue(buttons.first().second.contains("TRANS-AM 控制台"))
    }

    @Test
    fun meowssiahHasNoDeclaredButtons() {
        // Meowssiah 的「菜单」脚本 button.enabled=true 但 buttons=[]（运行时注册）
        val buttons = extractDeclaredButtons(loadPreset("Meowssiah-2.0.11.json"))
        assertEquals("Meowssiah 无声明式按钮", 0, buttons.size)
    }

    @Test
    fun buttonCarriesScriptIdForDispatch() {
        val buttons = extractDeclaredButtons(loadPreset("TRANS-AM-4A版-v1.1.2.3.json"))
        val (scriptId, _) = buttons.first()
        assertTrue("scriptId 不能为空（点击派发需要它）", scriptId.isNotBlank())
        // TRANS-AM 控制台的 id
        assertEquals("5e4b75d1-e2c0-450f-aef9-e743a8cf57a1", scriptId)
    }

    @Test
    fun disabledButtonConfigIsSkipped() {
        // 构造一个 button.enabled=false 的脚本，应被跳过
        val json = """
            {
              "extensions": {
                "tavern_helper": {
                  "scripts": [
                    {"type":"script","enabled":true,"id":"a","name":"A",
                     "button":{"enabled":false,"buttons":[{"name":"不该出现","visible":true}]}},
                    {"type":"script","enabled":true,"id":"b","name":"B",
                     "button":{"enabled":true,"buttons":[{"name":"应该出现","visible":true}]}}
                  ]
                }
              }
            }
        """.trimIndent()
        val buttons = extractDeclaredButtons(json)
        assertEquals(1, buttons.size)
        assertEquals("应该出现", buttons.first().second)
    }

    @Test
    fun invisibleButtonIsSkipped() {
        val json = """
            {
              "extensions": {
                "tavern_helper": {
                  "scripts": [
                    {"type":"script","enabled":true,"id":"a","name":"A",
                     "button":{"enabled":true,"buttons":[
                        {"name":"隐藏的","visible":false},
                        {"name":"显示的","visible":true}
                     ]}}
                  ]
                }
              }
            }
        """.trimIndent()
        val buttons = extractDeclaredButtons(json)
        assertEquals(1, buttons.size)
        assertEquals("显示的", buttons.first().second)
    }

    @Test
    fun disabledScriptIsSkipped() {
        val json = """
            {
              "extensions": {
                "tavern_helper": {
                  "scripts": [
                    {"type":"script","enabled":false,"id":"a","name":"A",
                     "button":{"enabled":true,"buttons":[{"name":"不该出现","visible":true}]}}
                  ]
                }
              }
            }
        """.trimIndent()
        assertEquals(0, extractDeclaredButtons(json).size)
    }
}
