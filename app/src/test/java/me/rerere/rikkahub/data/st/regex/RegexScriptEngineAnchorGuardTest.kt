package me.rerere.rikkahub.data.st.regex

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * [v2] 锚点感知的 ReDoS 判据回归。
 *
 * 真机背景（tavern-runtime.log）：
 *  - skipped potentially catastrophic regex on input (16467 chars) —— 前端综合面板
 *  - skipped variable-lookbehind ReDoS risk —— 前端综合面板 / 修剪多余思考 / summary 脚本
 *
 * 两条守卫都在误杀「尾部位置 + 锚点起首」的重复组与「紧跟字面量」的变长后顾。
 * 本测试锁定：真实预设正则不再被误杀；真正的病态形态仍然被拒。
 */
class RegexScriptEngineAnchorGuardTest {

    private val messages = java.util.Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        messages.clear()
        RegexScriptEngine.onCompileDegraded = { messages.add(it) }
    }

    @After
    fun tearDown() {
        RegexScriptEngine.onCompileDegraded = null
    }

    // ---- 真实预设 / 日志里的正则原文 ----
    private val PANEL =
        """/((?:<options\b[^>]*>[\s\S]*?<\/options>|(?<!<details\b[^>]*>\s*)<summary\b[^>]*>[\s\S]*?<\/summary>|<comment\b[^>]*>[\s\S]*?<\/comment>)(?:\s*(?:<options\b[^>]*>[\s\S]*?<\/options>|(?<!<details\b[^>]*>\s*)<summary\b[^>]*>[\s\S]*?<\/summary>|<comment\b[^>]*>[\s\S]*?<\/comment>))*)/gi"""

    private val TRIM_THINK =
        """/(?<!<!--\s*)<think(?:\s[^>]*)?>(?:(?!(?:<think_nya|<report_nya))[\s\S])*?(?:<\/think>(?!\s*-->)|(?=<(?:think_nya|report_nya)~?>))\s*/gi"""

    private val SUMMARY =
        """/(?<!<details>\s*)<summary>(((?!<summary>)[\s\S])*?)<\/summary>/gi"""

    private val TAIL = "\n\n<options>\n<option>A</option>\n</options>\n\n<summary>\n- s\n</summary>\n\n<comment>\nc\n</comment>"

    private fun filler(n: Int): String =
        buildString { while (length < n) append("织乃半张脸埋在白色的棉布枕套里，说话的声音闷在织物纤维间。\n") }

    private fun script(id: String, find: String, replace: String) = RegexScript(
        id = id, scriptName = id, findRegex = find, replaceString = replace,
        trimStrings = emptyList(), placement = listOf(RegexPlacement.AI_OUTPUT),
        disabled = false, markdownOnly = true, promptOnly = false, runOnEdit = true,
        substituteRegex = 0, minDepth = null, maxDepth = null,
    )

    private fun bodyOf(p: String) = p.removePrefix("/").substringBeforeLast("/")

    // =====================================================================
    // A. 静态判据
    // =====================================================================

    @Test
    fun realPresetRegexesAreNotFlagged() {
        assertFalse(RegexScriptEngine.isPotentiallyCatastrophicRegex(PANEL))
        assertFalse(RegexScriptEngine.isDangerousVariableLookbehind(PANEL))
        assertFalse(RegexScriptEngine.isDangerousVariableLookbehind(TRIM_THINK))
        assertFalse(RegexScriptEngine.isDangerousVariableLookbehind(SUMMARY))
    }

    @Test
    fun trulyCatastrophicPatternsAreStillFlagged() {
        listOf(
            """/(a+)+${'$'}$/""",
            """/(a|aa)+b/""",
            """/(.*a){10}/""",
            """/(\w+\s?)+${'$'}$/""",
            """/(\s*[a-z]+)*/""",
            """/(x+x+)+y/""",
            """/(?:<a>|<ab>)*x/""",
            """/(?:<a>[\s\S]*?<\/a>\s*)+END/""",
            """/(?:\s*<a>)+x/""",
            """/(?:\s*<a>)*${'$'}$/""",
            """/((?:\s*<a>)*)+/""",
            """/(\s*)*x/""",
        ).forEach { assertTrue("should be flagged: ${'$'}it", RegexScriptEngine.isPotentiallyCatastrophicRegex(it)) }
    }

    @Test
    fun anchoredTailRepetitionIsExempt() {
        listOf(
            """/(?:\s*<b>[\s\S]*?<\/b>)*/""",
            """/(?:\s*<a>)+/""",
            """/(?:\s*(?:<a>|<b>))*/""",
            """/((?:\s*<a>)*)/""",
            """/<a>([\s\S]*?)<\/a>/g""",
        ).forEach { assertFalse("should be exempt: ${'$'}it", RegexScriptEngine.isPotentiallyCatastrophicRegex(it)) }
    }

    @Test
    fun unanchoredVariableLookbehindsAreStillDangerous() {
        listOf(
            """/(?<=\s+)[\s\S]*?foo/""",
            """/(?<!\s*)[a-z]+x/""",
            """/(?<!\s*)x[\s\S]*?y/""",
            """/[\s\S]*?(?<!<details>\s*)<summary>([\s\S]*?)<\/summary>[\s\S]*/g""",
            """/.*(?<!<!--\s*)<think>[\s\S]*?<\/think>/gi""",
        ).forEach { assertTrue("should be dangerous: ${'$'}it", RegexScriptEngine.isDangerousVariableLookbehind(it)) }
    }

    @Test
    fun boundedOrAnchoredLookbehindsAreSafe() {
        listOf(
            """/(?<=\s+)foo/""",
            """/(?<!<details>\s*)<summary>[\s\S]*?<\/summary>/gi""",
            """/(?<!<!--\s*)<think(?:\s[^>]*)?>[\s\S]*?<\/think>/gi""",
            """/(?<=a\s*)(?<!b\s*)<tag>[\s\S]*?z/""",
            """/^[\s\S]*?(?<!<details>\s*)<summary>[\s\S]*?<\/summary>/g""",
        ).forEach { assertFalse("should be safe: ${'$'}it", RegexScriptEngine.isDangerousVariableLookbehind(it)) }
    }

    // =====================================================================
    // B. 锚点前置
    // =====================================================================

    @Test
    fun hoistTable() {
        val cases = listOf(
            """(?<!a\s*)<summary>x""" to """(?=<summary>x)(?<!a\s*)<summary>x""",
            """(?<=ab)cd""" to """(?<=ab)cd""",
            """(?<!x\s*)ab*c""" to """(?=a)(?<!x\s*)ab*c""",
            """(?<!x\s*)ab+c""" to """(?=ab)(?<!x\s*)ab+c""",
            """[(?<!a*)]b""" to """[(?<!a*)]b""",
            """(?<!x\s*)\d+""" to """(?<!x\s*)\d+""",
            """(?<!x\s*)\/foo""" to """(?=\/foo)(?<!x\s*)\/foo""",
            """(?:(?<!x\s*)<a>|b)""" to """(?:(?=<a>)(?<!x\s*)<a>|b)""",
            """(?<!x\s*)<a{2}""" to """(?=<)(?<!x\s*)<a{2}""",
            """(?<!x{0,5})ab|cd""" to """(?=ab)(?<!x{0,5})ab|cd""",
            """(?<=a\s*)(?<!b\s*)<tag>z""" to """(?=<tag>z)(?<=a\s*)(?=<tag>z)(?<!b\s*)<tag>z""",
        )
        for ((input, expected) in cases) {
            assertEquals(input, expected, RegexScriptEngine.hoistLookbehindAnchors(input))
        }
    }

    @Test
    fun degradedPanelPatternIsHoisted() {
        val degraded = RegexScriptEngine.degradeUnboundedLookbehind(bodyOf(PANEL))
        assertNotNull(degraded)
        val hoisted = RegexScriptEngine.hoistLookbehindAnchors(degraded!!)
        assertTrue(hoisted.contains("""(?=<summary)(?<!<details\b[^>]{0,256}>\s{0,256})<summary"""))
    }

    /** 前置不得改变匹配语义：降级版与前置版在随机输入上逐匹配一致。 */
    @Test
    fun hoistedAndPlainPatternsAgree() {
        val tokens = listOf(
            "<details>", "<details class=\"a\">", "</details>", "<summary>", "</summary>", "<options>", "</options>",
            "<comment>", "</comment>", " ", "\n", "x", "<", ">", "<!-- ", "-->", "<think>", "</think>", "<SUMMARY>",
        )
        val rnd = Random(42)
        val opts = setOf(RegexOption.IGNORE_CASE, RegexOption.UNIX_LINES)
        for (pat in listOf(PANEL, TRIM_THINK, SUMMARY)) {
            val bounded = RegexScriptEngine.degradeUnboundedLookbehind(bodyOf(pat)) ?: bodyOf(pat)
            val plain = Regex(bounded, opts)
            val hoisted = Regex(RegexScriptEngine.hoistLookbehindAnchors(bounded), opts)
            repeat(3000) {
                val text = buildString { repeat(rnd.nextInt(40)) { append(tokens[rnd.nextInt(tokens.size)]) } }
                assertEquals(
                    plain.findAll(text).map { it.range to it.value }.toList(),
                    hoisted.findAll(text).map { it.range to it.value }.toList(),
                )
            }
        }
    }

    @Test
    fun hoistedPanelPatternFitsRuntimeBudgetOn16kInput() {
        val text = filler(16_000) + TAIL
        val bounded = RegexScriptEngine.degradeUnboundedLookbehind(bodyOf(PANEL))!!
        val hoisted = Regex(
            RegexScriptEngine.hoistLookbehindAnchors(bounded),
            setOf(RegexOption.IGNORE_CASE, RegexOption.UNIX_LINES),
        )
        val seq = RegexScriptEngine.BoundedRegexCharSequence(
            text, RegexScriptEngine.MAX_REGEX_CHAR_ACCESS_STEPS, 10_000_000_000L,
        )
        val m = hoisted.find(seq)
        assertNotNull(m)
        assertTrue(m!!.value.startsWith("<options>"))
    }

    // =====================================================================
    // C. 端到端（runRegexScript）
    // =====================================================================

    @Test
    fun panelRegexRunsOnceAndIsNotSkippedOn16kInput() {
        val input = filler(16_000) + TAIL
        val out = RegexScriptEngine.runRegexScript(script("panel", PANEL, "[PANEL]"), input)
        assertEquals(1, Regex("""\[PANEL]""").findAll(out).count())
        assertFalse(out.contains("<options>"))
        assertFalse(out.contains("<comment>"))
        assertTrue(messages.toString(), messages.none { it.contains("skipped") || it.contains("aborted") })
    }

    @Test
    fun trimThinkStripsResidualThinkOnLongInput() {
        val input = filler(5_000) + "<think>abc</think>" + filler(100)
        val out = RegexScriptEngine.runRegexScript(script("trim", TRIM_THINK, ""), input)
        assertFalse(out.contains("<think>"))
        assertTrue(messages.toString(), messages.none { it.contains("skipped") || it.contains("aborted") })
    }

    @Test
    fun summaryInsideDetailsIsPreserved() {
        val input = filler(5_000) +
            "<details>\n<summary>keep</summary>\n</details>\n" +
            "<summary>drop</summary>" + filler(50)
        val out = RegexScriptEngine.runRegexScript(script("sum", SUMMARY, "[S]"), input)
        assertTrue(out.contains("<summary>keep</summary>"))
        assertFalse(out.contains("<summary>drop</summary>"))
        assertTrue(out.contains("[S]"))
    }

    @Test
    fun thinkNyaIsNotTouchedByTrimThink() {
        val input = filler(5_000) + "<think_nya>\nabc</think_nya><game>x</game>"
        val out = RegexScriptEngine.runRegexScript(script("trim", TRIM_THINK, ""), input)
        assertEquals(input, out)
    }

    @Test
    fun genuinelyCatastrophicRegexIsStillSkipped() {
        val input = "a".repeat(5_000) + "b"
        val out = RegexScriptEngine.runRegexScript(script("evil", """/(a+)+${'$'}$/""", "X"), input)
        assertEquals(input, out)
        assertTrue(messages.toString(), messages.any { it.contains("skipped potentially catastrophic") })
    }

    @Test
    fun leadingScanWithLookbehindIsStillSkipped() {
        val input = filler(5_000)
        val pat = """/[\s\S]*?(?<!<details>\s*)<summary>([\s\S]*?)<\/summary>[\s\S]*/g"""
        val out = RegexScriptEngine.runRegexScript(script("quad", pat, "X"), input)
        assertEquals(input, out)
        assertTrue(messages.toString(), messages.any { it.contains("variable-lookbehind") })
    }

    // =====================================================================
    // D. 真实预设误杀回归（v251）
    //
    // 真机日志（2026-11-24 21:44，Meowssiah-2.0.11 导入后）：
    //   skipped potentially catastrophic regex ... pattern=/<(bbs_start|(?:ta|tw)_time)\b[^>]*>...
    //   skipped potentially catastrophic regex ... pattern=/(?<!['"`\u201c\u201d\u2018\u2019])<(?:ta|tw)_plan\s*>...
    //   skipped potentially catastrophic regex ... pattern=/(?<!['"`...])<(?:ta|tw)_record\s*>(?=...
    //   skipped variable-lookbehind ReDoS risk ... pattern=/(?:(?:[“"「『]...speech_src...speech_zh...
    //
    // 这些是**正常显示正则**，全部因为两条判据 bug 被整条跳过，用户看到的是「正则明明开了却没效果」。
    // =====================================================================

    /** 尾部「换行吞并」重复组：\r?\n 是**确定字符**转义，必须算锚点。 */
    private val BBS_TIME =
        """/<(bbs_start|(?:ta|tw)_time)\b[^>]*>\s*([^<>\r\n]+?)\s*<\/\1\s*>[ \t]*(?:\r?\n[ \t]*)*/gi"""

    /** 变长后顾 + 紧跟字面量锚点（<ta_plan>）。 */
    private val TA_PLAN =
        """/(?<!['"`\u201c\u201d\u2018\u2019])<(?:ta|tw)_plan\s*>((?:(?!<(?:ta|tw)_(?:zh|source)\b)[\s\S])*?)<\/(?:ta|tw)_plan\s*>[ \t]*(?:\r?\n[ \t]*)*/gi"""

    /** 后顾 + 可选前缀分组 + 强制 <ta_record> 锚点。 */
    private val TA_RECORD =
        """/(?<!['"`\u201c\u201d\u2018\u2019])<(?:ta|tw)_record\s*>(?=(?:(?:(?!<\/(?:ta|tw)_record\s*>)[\s\S])*?(\d{1,2}[:：]\d{2}))?)((?:(?!<(?:ta|tw)_(?:zh|source)\b)[\s\S])*?)<\/(?:ta|tw)_record\s*>[ \t]*(?:\r?\n[ \t]*)*/gi"""

    /** 可选前缀分组 (?:…)? 起首 + 变长后顾：两条判据都不能误杀。 */
    private val SPEECH =
        """/(?:(?:[“"「『][ \t\r\n]*(?=<(?:ta|tw)_speech_src\b[^>]*>[\s\S]*?<\/(?:ta|tw)_speech_src\s*>\s*<(?:ta|tw)_speech_zh\b[^>]*>[\s\S]*?<\/(?:ta|tw)_speech_zh\s*>[ \t\r\n]*[”"」』]))?<(?:ta|tw)_speech_src\b[^>]*>([\s\S]*?)<\/(?:ta|tw)_speech_src\s*>\s*<(?:ta|tw)_speech_zh\b[^>]*>([\s\S]*?)<\/(?:ta|tw)_speech_zh\s*>(?:(?<=[“"「『][ \t\r\n]*<(?:ta|tw)_speech_src\b[^>]*>[\s\S]*?<\/(?:ta|tw)_speech_src\s*>\s*<(?:ta|tw)_speech_zh\b[^>]*>[\s\S]*?<\/(?:ta|tw)_speech_zh\s*>)[ \t\r\n]*[”"」』])?|(?<=[\u4e00-\u9fff][：:，,。！？])[ \t]*("[A-Za-z][^"\r\n]*")[ \t\r\n]+(“[^”\r\n]*[\u4e00-\u9fff][^”\r\n]*”))/gi"""

    @Test
    fun realMeowssiahRegexesAreNotSkipped() {
        listOf(BBS_TIME, TA_PLAN, TA_RECORD, SPEECH).forEach { pat ->
            assertFalse("catastrophic 误杀: $pat", RegexScriptEngine.isPotentiallyCatastrophicRegex(pat))
            assertFalse("lookbehind 误杀: $pat", RegexScriptEngine.isDangerousVariableLookbehind(pat))
        }
    }

    @Test
    fun realMeowssiahRegexesActuallyRunOnLongInput() {
        val body = filler(20_000) +
            "<ta_plan>\n计划正文\n</ta_plan>\n" +
            "<ta_record>12:34 记录正文</ta_record>\n" +
            "<ta_time>早上 08:00</ta_time>\n"
        val cases = listOf(
            "bbs" to BBS_TIME,
            "plan" to TA_PLAN,
            "record" to TA_RECORD,
        )
        for ((id, pat) in cases) {
            messages.clear()
            val out = RegexScriptEngine.runRegexScript(script(id, pat, "[$id]"), body)
            assertTrue(
                "正则 $id 被跳过了：${messages}",
                messages.none { it.contains("skipped") || it.contains("aborted") },
            )
            assertTrue("正则 $id 没有生效", out.contains("[$id]"))
        }
    }

    @Test
    fun optionalPrefixGroupStillCountsAsAnchored() {
        // 可选前缀分组之后必须紧跟强制元素 → 仍然是锚定的
        listOf(
            """/(?:前缀)?<tag>[\s\S]*?<\/tag>)*/""",
            """/(?:(?:[“"])?<src>[\s\S]*?<\/src>)*/""",
            """/(?:\s*(?:<a>)?<b>)*/""",
        ).forEach { assertFalse("可选前缀不该被判为无锚点: $it", RegexScriptEngine.isPotentiallyCatastrophicRegex(it)) }
    }

    @Test
    fun deterministicCharEscapeCountsAsAnchorButCharClassDoesNot() {
        // \r?\n = 确定的换行 → 锚点
        assertFalse(RegexScriptEngine.isPotentiallyCatastrophicRegex("""/(?:\r?\n[ \t]*)*/"""))
        // \s* 后面没有强制元素 → 不是锚点（保持拒绝）
        assertTrue(RegexScriptEngine.isPotentiallyCatastrophicRegex("""/(?:\s*)*/"""))
    }
}

