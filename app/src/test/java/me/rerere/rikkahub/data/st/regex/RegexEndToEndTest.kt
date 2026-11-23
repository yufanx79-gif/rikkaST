package me.rerere.rikkahub.data.st.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [v249] 正则端到端验证：真实角色卡 JSON → parseCardRegexScripts → getRegexedString。
 *
 * 真机取证背景：AI 输出正则（placement=2）和世界书正则（placement=5）都验证生效，
 * 唯独用户输入正则（placement=1）没有出现预期标记。本测试用**与真机同一份卡片 JSON**
 * 走完整链路，把"解析丢失"和"应用条件不满足"两种可能分开证明。
 */
class RegexEndToEndTest {

    /** 与 TC_alltest_card.json 完全一致的三条正则（camelCase 字段名）。 */
    private val cardJson = """
    {
      "regex_scripts": [
        {
          "id": "tc-regex-ai-1",
          "scriptName": "TC_AI_清空测试",
          "findRegex": "/【TC_AI_RAW】(.*?)【\\/TC_AI_RAW】/gs",
          "replaceString": "【TC_AI_REPLACED】$1【\\/TC_AI_REPLACED】",
          "trimStrings": [],
          "placement": [2],
          "disabled": false,
          "markdownOnly": false,
          "promptOnly": false
        },
        {
          "id": "tc-regex-user-1",
          "scriptName": "TC_USER_输入标记",
          "findRegex": "/用户说：(.*)/g",
          "replaceString": "【TC_USER_HIT】$1【\\/TC_USER_HIT】",
          "trimStrings": [],
          "placement": [1],
          "disabled": false,
          "markdownOnly": false,
          "promptOnly": false
        },
        {
          "id": "tc-regex-wi-1",
          "scriptName": "TC_WI_世界书标记",
          "findRegex": "/【TC_WI_RAW】/g",
          "replaceString": "【TC_WI_REPLACED】",
          "trimStrings": [],
          "placement": [5],
          "disabled": false,
          "markdownOnly": false,
          "promptOnly": false
        }
      ]
    }
    """.trimIndent()

    private fun scripts(): List<RegexScript> =
        parseCardRegexScripts(Json.parseToJsonElement(cardJson).jsonObject)

    // ============ 解析层 ============

    @Test
    fun cardRegexScriptsAreAllParsed() {
        val list = scripts()
        assertEquals("三条内嵌正则都必须解析出来", 3, list.size)
        assertEquals(
            "placement 必须原样保留",
            listOf(1, 2, 5),
            list.map { it.placement.single() }.sorted(),
        )
        assertTrue("findRegex 不能为空", list.all { it.findRegex.isNotBlank() })
        assertTrue("replaceString 不能为空", list.all { it.replaceString.isNotEmpty() })
    }

    // ============ 应用层：逐 placement 验证 ============

    @Test
    fun userInputRegexAppliesAtPlacement1() {
        val out = RegexScriptEngine.getRegexedString(
            rawString = "用户说：今天天气不错",
            scripts = scripts(),
            placement = RegexPlacement.USER_INPUT,
            isPrompt = true,
        )
        // ST 语义：replaceString 里的 \/ 是正则字面量的转义斜杠，引擎按字面还原为 \/
        assertTrue("placement=1 必须命中前缀标记", out.startsWith("【TC_USER_HIT】"))
        assertTrue("正文必须保留", out.contains("今天天气不错"))
        assertTrue("必须命中后缀标记", out.contains("TC_USER_HIT】"))
    }

    @Test
    fun aiOutputRegexAppliesAtPlacement2() {
        val out = RegexScriptEngine.getRegexedString(
            rawString = "【TC_AI_RAW】这段会被改写【/TC_AI_RAW】",
            scripts = scripts(),
            placement = RegexPlacement.AI_OUTPUT,
            isPrompt = true,
        )
        assertTrue("placement=2 的 AI 输出正则必须命中", out.contains("TC_AI_REPLACED"))
        assertTrue("原文标记不应残留", !out.contains("TC_AI_RAW"));
    }

    @Test
    fun worldInfoRegexAppliesAtPlacement5() {
        val out = RegexScriptEngine.getRegexedString(
            rawString = "内容里带【TC_WI_RAW】标记",
            scripts = scripts(),
            placement = RegexPlacement.WORLD_INFO,
            isPrompt = true,
        )
        assertEquals(
            "placement=5 的世界书正则必须命中",
            "内容里带【TC_WI_REPLACED】标记",
            out,
        )
    }

    /** 反向：placement 不匹配时绝不能误改。 */
    @Test
    fun placementIsolation() {
        // 用户输入正则不应作用在 AI 输出通道
        val aiChannel = RegexScriptEngine.getRegexedString(
            rawString = "用户说：你好",
            scripts = scripts(),
            placement = RegexPlacement.AI_OUTPUT,
            isPrompt = true,
        )
        assertEquals("placement=1 的正则不得作用于 AI 输出通道", "用户说：你好", aiChannel)

        // 世界书正则不应作用在用户输入通道
        val userChannel = RegexScriptEngine.getRegexedString(
            rawString = "带【TC_WI_RAW】",
            scripts = scripts(),
            placement = RegexPlacement.USER_INPUT,
            isPrompt = true,
        )
        assertEquals("placement=5 的正则不得作用于用户输入通道", "带【TC_WI_RAW】", userChannel)
    }

    /** 反向：disabled=true 的脚本不应生效。 */
    @Test
    fun disabledScriptIsSkipped() {
        val disabled = scripts().map {
            if (it.placement.contains(RegexPlacement.USER_INPUT)) it.copy(disabled = true) else it
        }
        val out = RegexScriptEngine.getRegexedString(
            rawString = "用户说：你好",
            scripts = disabled,
            placement = RegexPlacement.USER_INPUT,
            isPrompt = true,
        )
        assertEquals("禁用的正则不得生效", "用户说：你好", out)
    }
}
