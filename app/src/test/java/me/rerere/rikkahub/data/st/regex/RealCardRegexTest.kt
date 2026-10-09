package me.rerere.rikkahub.data.st.regex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用「合成正则脚本集」验证解析与执行。
 *
 * 结构与真实卡导出的 `regex_scripts` 对齐（25 条 / 8 条），但**内容全部是占位文本**，
 * 不含任何第三方角色卡的创作内容。
 *
 * 覆盖点：
 * - 25 条 / 8 条全部解析成功
 * - 单条坏数据不影响其余（逐条解析容错）
 * - 复杂脚本执行不崩溃、耗时可接受
 * - CRLF 开场白必须被命中（Java `UNIX_LINES` 回归：默认 `.` 不匹配 `\r`）
 */
class RealCardRegexTest {

    private fun loadArray(resource: String): String =
        javaClass.classLoader!!.getResourceAsStream(resource)!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun toExt(json: String): JsonObject {
        val arr = Json.parseToJsonElement(json).jsonArray
        return buildJsonObject { put("regex_scripts", arr) }
    }

    /**
     * 合成「状态栏 DSL → HTML 卡片」脚本，等价替代真实卡里的基础替换脚本。
     * 参数（placement / markdownOnly / promptOnly / runOnEdit / depth）与真实卡一致，
     * 只有文案与 HTML 是自造的。
     */
    private fun syntheticBaseScript() = parseCardRegexScripts(
        toExt(
            """
            [{"id":"s1","scriptName":"SYNTH-BASE",
              "findRegex":"</base>\\s*日期[：:]\\s*(.*?)\\n\\s*星期[：:]\\s*(.*?)\\n([\\s\\S]*?)</base>",
              "replaceString":"<div class=\"card\"><style>.card{color:#fff}</style>SYNTH</div>",
              "trimStrings":[],"placement":[1,2],"disabled":false,
              "markdownOnly":true,"promptOnly":false,"runOnEdit":true,"substituteRegex":0,
              "minDepth":null,"maxDepth":null}]
            """.trimIndent()
        )
    ).first()

    private val syntheticStatusText = """
        </base>
        日期：1月1日
        星期：一
        时间：00:00
        电量：100%
        信号：5格
        ID：0001
        状态：在线

        示例正文。
        </base>
    """.trimIndent()

    @Test
    fun sampleSetParsesAll25Scripts() {
        val scripts = parseCardRegexScripts(toExt(loadArray("sample_regex_25.json")))
        assertEquals(25, scripts.size)
        assertTrue(scripts.all { it.findRegex.isNotBlank() })
    }

    @Test
    fun sampleSetBParsesAll8Scripts() {
        val scripts = parseCardRegexScripts(toExt(loadArray("sample_regex_8.json")))
        assertEquals(8, scripts.size)
    }

    @Test
    fun oneBadScriptDoesNotBreakOthers() {
        // [v241] 宽容解析升级后的语义：单个坏字段（minDepth 非数字）被忽略并回退默认（null=不限制），
        // 脚本本身保留 —— 旧实现用严格序列化会把整条脚本静默丢掉，真实卡里一个坏字段就丢一条正则。
        // 只有「不是对象 / 没有 findRegex」才整条丢弃（见 CardRegexParseTest）。
        val json = """[
            {"id":"a","scriptName":"good1","findRegex":"A","replaceString":"B","placement":[2]},
            {"id":"b","scriptName":"bad","findRegex":"C","minDepth":"NOT-A-NUMBER"},
            {"id":"c","scriptName":"good2","findRegex":"D","replaceString":"E","placement":[2]}
        ]"""
        val scripts = parseCardRegexScripts(toExt(json))
        assertEquals(3, scripts.size)
        assertEquals("good1", scripts[0].scriptName)
        assertEquals("bad", scripts[1].scriptName)
        assertNull(scripts[1].minDepth)
        assertEquals("good2", scripts[2].scriptName)
    }

    @Test
    fun syntheticBaseScriptPerformsReplacement() {
        val base = syntheticBaseScript()
        val out = RegexScriptEngine.getRegexedString(
            rawString = syntheticStatusText,
            scripts = listOf(base),
            placement = RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        assertNotEquals(syntheticStatusText, out) // 发生了替换
        assertTrue("应替换为 HTML 卡片，实际前缀：" + out.take(120), out.contains("<style>"))
        println("synthetic base output preview: " + out.take(160))
    }

    @Test
    fun sampleSetScriptsExecuteWithoutCrashAndWithinBudget() {
        val scripts = parseCardRegexScripts(toExt(loadArray("sample_regex_8.json")))
        // 样例文本：模拟一条含状态栏/标记的长消息
        val text = buildString {
            append("示例消息一\n")
            append("<status>\n字段A：1\n字段B：2\n</status>\n")
            append("说明文本".repeat(50))
        }
        val t0 = System.nanoTime()
        val out = RegexScriptEngine.getRegexedString(
            rawString = text,
            scripts = scripts,
            placement = RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        val ms = (System.nanoTime() - t0) / 1_000_000
        println("sample regex cost: " + ms + "ms, out len=" + out.length)
        assertTrue("耗时 " + ms + "ms 超出预算", ms < 10_000)
    }

    /**
     * 回归（bug：开场白不渲染）：fixture 是 CRLF，必须与 LF 一样被命中。
     *
     * 合成脚本的 pattern 形如 `</base>\s*日期[：:]\s*(.*?)\n\s*星期[：:]…`；
     * JS 的 `.` 匹配 `\r` 而 Java 默认不匹配 → 默认语义下 `(.*?)` 越不过 `\r`，整条静默失配，
     * 开场白 HTML 注入不生效（显示裸 DSL）。修复：编译正则时带 RegexOption.UNIX_LINES。
     */
    @Test
    fun syntheticBaseScriptMatchesCrlfFirstMes() {
        val base = syntheticBaseScript()
        val input = loadArray("sample_first_mes_crlf.txt")
        assertTrue("fixture 应保留 CRLF 行尾", input.contains("\r\n"))
        val out = RegexScriptEngine.getRegexedString(
            rawString = input,
            scripts = listOf(base),
            placement = RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        assertNotEquals("CRLF 开场白必须被命中", input, out)
        assertTrue("CRLF 开场白应替换为 HTML 卡片，实际前缀：" + out.take(120), out.contains("<style>"))
    }

    /** 回归：CRLF 与 LF 输入经全部 25 条脚本处理后，除捕获组带入的 `\r` 外结果一致。 */
    @Test
    fun sampleSetAllScriptsTreatCrlfLikeLf() {
        val scripts = parseCardRegexScripts(toExt(loadArray("sample_regex_25.json")))
        val crlf = loadArray("sample_first_mes_crlf.txt")
        val lf = crlf.replace("\r\n", "\n")
        val outCrlf = RegexScriptEngine.getRegexedString(
            rawString = crlf, scripts = scripts,
            placement = RegexPlacement.AI_OUTPUT, isMarkdown = true,
        )
        val outLf = RegexScriptEngine.getRegexedString(
            rawString = lf, scripts = scripts,
            placement = RegexPlacement.AI_OUTPUT, isMarkdown = true,
        )
        assertEquals(outLf.replace("\r", ""), outCrlf.replace("\r", ""))
    }
}
