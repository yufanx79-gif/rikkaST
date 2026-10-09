package me.rerere.rikkahub.data.st.import

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v236 C3] ST settings.json 导入 round-trip 单测。
 *
 * 纯函数层（mapStSettings / applyStSettingsPatch / exportStSettings）无 Android 依赖，
 * 可直接在 JVM 跑：构造一份含 power_user / oai_settings / extension_settings / world_info_settings
 * 的 ST settings.json → 导入 → 导出 → 再导入 → 断言关键字段一致。
 */
class StSettingsImportTest {

    private val sample = """
        {
          "username": "user",
          "power_user": {
            "send_on_enter": 1,
            "timestamps_enabled": true,
            "message_token_count_enabled": true,
            "auto_scroll_chat_to_bottom": false,
            "font_scale": 1.25,
            "italics_text_color": "#919191",
            "quote_text_color": "#E18A24",
            "theme": "Dark",
            "blur_strength": 10
          },
          "oai_settings": {
            "enable_web_search": true,
            "show_thoughts": false,
            "temp_openai": 0.7,
            "openai_model": "gpt-4o"
          },
          "extension_settings": {
            "regex": [
              {
                "id": "r1",
                "scriptName": "clean",
                "findRegex": "\\s+",
                "replaceString": " ",
                "placement": [1, 2],
                "disabled": false
              }
            ],
            "variables": { "global": { "hp": "42", "name": "Alice" } },
            "note": { "default": "global note", "chara": [], "wiAddition": [] },
            "disabledExtensions": ["third-party/foo"],
            "tts": { "voice": "x" }
          },
          "world_info_settings": {
            "world_info_budget": 40,
            "world_info_recursive": true,
            "world_info_depth": 5,
            "world_info_character_strategy": 2
          }
        }
    """.trimIndent()

    @Test
    fun `maps power_user display fields to display setting`() {
        val result = mapStSettings(sample)
        val applied = applyStSettingsPatch(Settings(init = true), result.patch)
        assertTrue(applied.displaySetting.sendOnEnter)
        assertTrue(applied.displaySetting.showDateTimeInMessage)
        assertTrue(applied.displaySetting.showTokenUsage)
        assertFalse(applied.displaySetting.enableAutoScroll)
        assertEquals(1.25f, applied.displaySetting.fontSizeRatio, 0.0001f)
        assertEquals("#919191", applied.displaySetting.italicsColor)
        assertEquals("#E18A24", applied.displaySetting.quoteColor)
        assertTrue("power_user.send_on_enter" in result.mappedPaths)
        assertTrue("power_user.timestamps_enabled" in result.mappedPaths)
        assertTrue("power_user.theme" in result.unmappedPaths)
        assertTrue("power_user.blur_strength" in result.unmappedPaths)
    }

    @Test
    fun `maps world info author note and oai switches`() {
        val result = mapStSettings(sample)
        val applied = applyStSettingsPatch(Settings(init = true), result.patch)
        assertEquals(40, applied.worldInfoBudget)
        assertTrue(applied.worldInfoRecursive)
        assertEquals(5, applied.worldInfoDepth)
        assertEquals(2, applied.worldInfoCharacterStrategy)
        assertEquals("global note", applied.authorNote)
        assertTrue(applied.enableWebSearch)
        assertFalse(applied.displaySetting.showThinkingContent)
        assertTrue("world_info_settings.world_info_budget" in result.mappedPaths)
        assertTrue("oai_settings.enable_web_search" in result.mappedPaths)
        assertTrue("oai_settings.temp_openai" in result.unmappedPaths)
        assertTrue("oai_settings.openai_model" in result.unmappedPaths)
    }

    @Test
    fun `maps extension regex note disabled and blob`() {
        val result = mapStSettings(sample)
        val applied = applyStSettingsPatch(Settings(init = true), result.patch)
        assertEquals(1, applied.regexScripts.size)
        assertEquals("clean", applied.regexScripts[0].scriptName)
        assertEquals(listOf(1, 2), applied.regexScripts[0].placement)
        assertEquals("global note", applied.authorNote)
        assertEquals(setOf("third-party/foo"), applied.tavernThirdPartyDisabled)

        val blob = result.patch.thirdPartySettingsJson
        assertNotNull(blob)
        assertTrue(blob!!.contains("tts"))
        assertFalse(blob.contains("\"variables\""))

        val globals = result.patch.globalVariables
        assertNotNull(globals)
        assertEquals("42", (globals!!["hp"] as JsonPrimitive).content)
        assertEquals("Alice", (globals["name"] as JsonPrimitive).content)

        assertTrue("extension_settings.regex" in result.mappedPaths)
        assertTrue("extension_settings.variables" in result.mappedPaths)
        assertTrue("extension_settings.note" in result.mappedPaths)
        assertTrue("extension_settings.disabledExtensions" in result.mappedPaths)
        assertTrue("extension_settings.tts" in result.mappedPaths)
    }

    @Test
    fun `send on enter enum mapping is faithful`() {
        fun applied(v: Int) = applyStSettingsPatch(
            Settings(init = true),
            mapStSettings("""{"power_user":{"send_on_enter":$v}}""").patch,
        )
        assertTrue(applied(1).displaySetting.sendOnEnter)
        assertFalse(applied(0).displaySetting.sendOnEnter)
        assertFalse(applied(-1).displaySetting.sendOnEnter)
    }

    @Test
    fun `non hex colors are not mapped`() {
        val result = mapStSettings(
            """{"power_user":{"italics_text_color":"rgb(1, 2, 3)","quote_text_color":"#abc"}}""",
        )
        assertEquals("#abc", result.patch.display.quoteColor)
        assertNull(result.patch.display.italicsColor)
        assertTrue("power_user.italics_text_color" in result.unmappedPaths)
        assertTrue("power_user.quote_text_color" in result.mappedPaths)
    }

    @Test
    fun `reports unknown top level keys and tolerates junk`() {
        val result = mapStSettings(
            """{"username":"u","api_server":"x","power_user":{"theme":"Dark"}}""",
        )
        assertTrue("username" in result.unknownTopLevelKeys)
        assertTrue("api_server" in result.unknownTopLevelKeys)
        assertTrue("power_user.theme" in result.unmappedPaths)

        val junk = mapStSettings("not json at all")
        assertTrue(junk.mappedPaths.isEmpty())
        assertTrue(junk.unmappedPaths.isEmpty())
        assertTrue(junk.unknownTopLevelKeys.isEmpty())
    }

    @Test
    fun `round trip export import keeps mapped fields`() {
        val firstResult = mapStSettings(sample)
        val first = applyStSettingsPatch(Settings(init = true), firstResult.patch)
        val globals = firstResult.patch.globalVariables ?: JsonObject(emptyMap())
        val thirdParty = buildJsonObject { put("tts", buildJsonObject { put("voice", "x") }) }

        val exported = exportStSettings(first, globals, thirdParty)
        val secondResult = mapStSettings(exported)
        val second = applyStSettingsPatch(Settings(init = true), secondResult.patch)

        assertEquals(first.displaySetting, second.displaySetting)
        assertEquals(first.worldInfoBudget, second.worldInfoBudget)
        assertEquals(first.worldInfoBudgetCap, second.worldInfoBudgetCap)
        assertEquals(first.worldInfoMinActivations, second.worldInfoMinActivations)
        assertEquals(first.worldInfoMinActivationsDepthMax, second.worldInfoMinActivationsDepthMax)
        assertEquals(first.worldInfoRecursive, second.worldInfoRecursive)
        assertEquals(first.worldInfoMaxRecursionSteps, second.worldInfoMaxRecursionSteps)
        assertEquals(first.worldInfoDepth, second.worldInfoDepth)
        assertEquals(first.worldInfoCharacterStrategy, second.worldInfoCharacterStrategy)
        assertEquals(first.worldInfoOverflowAlert, second.worldInfoOverflowAlert)
        assertEquals(first.worldInfoUseGroupScoring, second.worldInfoUseGroupScoring)
        assertEquals(first.authorNote, second.authorNote)
        assertEquals(first.regexScripts, second.regexScripts)
        assertEquals(first.enableWebSearch, second.enableWebSearch)
        assertEquals(first.tavernThirdPartyDisabled, second.tavernThirdPartyDisabled)
        assertEquals(globals, secondResult.patch.globalVariables)
        assertTrue((secondResult.patch.thirdPartySettingsJson ?: "").contains("tts"))
    }
}
