package me.rerere.rikkahub.data.export

import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.data.model.InjectionPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 酒馆预设 → 模式注入（ModeInjection）导入测试。
 *
 * 解析与映射逻辑移植自 RikkaRs（issue #188），目标模型为本项目
 * [me.rerere.rikkahub.data.model.PromptInjection.ModeInjection]。
 * 金标准：SillyTavern 1.18.0 PromptManager.js / openai.js 的 injection_position 语义。
 */
class SillyTavernPresetImportTest {

    private fun parse(json: String) =
        ModeInjectionSerializer.tryImportSillyTavernPreset(json, "测试预设")

    @Test
    fun fullPresetMapsOrderPositionDepthRoleAndEnabled() {
        val json = """
            {
              "prompts": [
                {"identifier":"main","name":"主提示词","role":"system","content":"MAIN 内容","enabled":true,"injection_position":0},
                {"identifier":"jailbreak","name":"越狱","role":"system","content":"JB 内容","enabled":true,"injection_position":0},
                {"identifier":"reminder","name":"深度提醒","role":"user","content":"REMIND 内容","enabled":true,"injection_position":1,"injection_depth":2},
                {"identifier":"disabled_entry","name":"被禁用的条目","role":"assistant","content":"OFF 内容","enabled":false,"injection_position":0}
              ],
              "prompt_order": [
                {"character_id":100001,"order":[
                  {"identifier":"main","enabled":true},
                  {"identifier":"jailbreak","enabled":true},
                  {"identifier":"reminder","enabled":true},
                  {"identifier":"disabled_entry","enabled":false}
                ]}
              ]
            }
        """.trimIndent()

        val list = parse(json)
        assertNotNull(list)
        assertEquals(4, list!!.size)
        assertEquals(listOf("主提示词", "越狱", "深度提醒", "被禁用的条目"), list.map { it.name })
        assertEquals(listOf(0, 1, 2, 3), list.map { it.priority })
        assertEquals(
            listOf(
                InjectionPosition.AFTER_SYSTEM_PROMPT,
                InjectionPosition.AFTER_SYSTEM_PROMPT,
                InjectionPosition.AT_DEPTH,
                InjectionPosition.TOP_OF_CHAT,
            ),
            list.map { it.position },
        )
        assertEquals(2, list[2].injectDepth)
        assertEquals(
            listOf(MessageRole.SYSTEM, MessageRole.SYSTEM, MessageRole.USER, MessageRole.ASSISTANT),
            list.map { it.role },
        )
        assertEquals(listOf(true, true, true, false), list.map { it.enabled })
    }

    @Test
    fun promptOrderDecidesOrderAndEnabledAndPrefersDefaultGroup() {
        val json = """
            {
              "prompts": [
                {"identifier":"a","name":"A","role":"system","content":"AAA"},
                {"identifier":"b","name":"B","role":"system","content":"BBB"},
                {"identifier":"c","name":"C","role":"system","content":"CCC"}
              ],
              "prompt_order": [
                {"character_id":100000,"order":[
                  {"identifier":"a","enabled":true},
                  {"identifier":"b","enabled":true},
                  {"identifier":"c","enabled":true}
                ]},
                {"character_id":100001,"order":[
                  {"identifier":"b","enabled":true},
                  {"identifier":"a","enabled":false},
                  {"identifier":"c","enabled":true}
                ]}
              ]
            }
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(listOf("B", "A", "C"), list.map { it.name })
        assertEquals(listOf(true, false, true), list.map { it.enabled })
    }

    @Test
    fun missingPromptOrderFallsBackToPromptsOrderAndEnablesAll() {
        val json = """
            {"prompts":[
              {"identifier":"x","name":"X","role":"user","content":"XXX"},
              {"identifier":"y","name":"Y","role":"user","content":"YYY"}
            ]}
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(listOf("X", "Y"), list.map { it.name })
        assertEquals(listOf(true, true), list.map { it.enabled })
    }

    @Test
    fun skipsMarkersBlankContentAndDuplicateIdentifiers() {
        val json = """
            {
              "prompts": [
                {"identifier":"chatHistory","name":"聊天记录","role":"system","content":"","marker":true},
                {"identifier":"worldInfoBefore","name":"世界书前","role":"system","content":"WIB","marker":true},
                {"identifier":"dup","name":"第一次","role":"user","content":"FIRST"},
                {"identifier":"dup","name":"第二次","role":"user","content":"SECOND"},
                {"identifier":"blank","name":"空白","role":"user","content":" "}
              ],
              "prompt_order": [{"character_id":100001,"order":[
                {"identifier":"chatHistory","enabled":true},
                {"identifier":"worldInfoBefore","enabled":true},
                {"identifier":"dup","enabled":true},
                {"identifier":"blank","enabled":true}
              ]}]
            }
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(1, list.size)
        assertEquals("第一次", list[0].name)
    }

    @Test
    fun positionMatrixMatchesSillyTavernSemantics() {
        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.USER),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(0, MessageRole.ASSISTANT),
        )
        assertEquals(
            InjectionPosition.AT_DEPTH,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(1, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.AT_DEPTH,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(1, MessageRole.USER),
        )
        assertEquals(
            InjectionPosition.AFTER_SYSTEM_PROMPT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(null, MessageRole.SYSTEM),
        )
        assertEquals(
            InjectionPosition.TOP_OF_CHAT,
            ModeInjectionSerializer.mapSillyTavernPresetPosition(null, MessageRole.USER),
        )
    }

    @Test
    fun roleMappingHandlesCaseAndUnknown() {
        assertEquals(MessageRole.USER, ModeInjectionSerializer.mapSillyTavernPromptRole("USER"))
        assertEquals(MessageRole.ASSISTANT, ModeInjectionSerializer.mapSillyTavernPromptRole("Assistant"))
        assertEquals(MessageRole.SYSTEM, ModeInjectionSerializer.mapSillyTavernPromptRole("something"))
        assertEquals(MessageRole.SYSTEM, ModeInjectionSerializer.mapSillyTavernPromptRole(null))
    }

    @Test
    fun promptLevelEnabledFieldIsIgnored() {
        val json = """
            {"prompts":[
              {"identifier":"p","name":"P","role":"system","content":"CC","enabled":false}
            ],
            "prompt_order":[{"character_id":100001,"order":[{"identifier":"p","enabled":true}]}]}
        """.trimIndent()

        val list = parse(json)!!
        assertEquals(1, list.size)
        assertEquals(true, list[0].enabled)
    }

    @Test
    fun invalidOrEmptyPresetReturnsNull() {
        assertNull(parse("""{"prompt_order":[]}"""))
        assertNull(parse("""{"prompts":[{"identifier":"m","marker":true,"content":"x"}]}"""))
        assertNull(parse("not a json at all"))
    }
}
