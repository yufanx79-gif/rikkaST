package me.rerere.rikkahub.data.st.regex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ST 1.18 正则脚本引擎移植测试。
 *
 * 金标准来源：SillyTavern 1.18.0 `public/scripts/extensions/regex/engine.js`
 * （runRegexScript / getRegexedString / filterString / sanitizeRegexMacro 逐行对齐）。
 */
class RegexScriptEngineTest {

    private fun script(
        find: String,
        replace: String,
        placement: List<Int> = listOf(RegexPlacement.AI_OUTPUT),
        trimStrings: List<String> = emptyList(),
        disabled: Boolean = false,
        markdownOnly: Boolean = false,
        promptOnly: Boolean = false,
        runOnEdit: Boolean = false,
        substituteRegex: Int = SubstituteRegex.NONE,
        minDepth: Int? = null,
        maxDepth: Int? = null,
    ) = RegexScript(
        scriptName = "test",
        findRegex = find,
        replaceString = replace,
        placement = placement,
        trimStrings = trimStrings,
        disabled = disabled,
        markdownOnly = markdownOnly,
        promptOnly = promptOnly,
        runOnEdit = runOnEdit,
        substituteRegex = substituteRegex,
        minDepth = minDepth,
        maxDepth = maxDepth,
    )

    private fun run(
        find: String,
        replace: String,
        input: String,
        trimStrings: List<String> = emptyList(),
        disabled: Boolean = false,
    ): String = RegexScriptEngine.runRegexScript(
        script(find, replace, trimStrings = trimStrings, disabled = disabled),
        input,
    )

    // ==================== 基础替换 ====================

    @Test
    fun `basic replace is global for non slash pattern`() {
        assertEquals("bbb", run("a", "b", "aaa"))
    }

    @Test
    fun `slash form without g replaces first match only`() {
        assertEquals("baa", run("/a/", "b", "aaa"))
    }

    @Test
    fun `slash form with g replaces all`() {
        assertEquals("bbb", run("/a/g", "b", "aaa"))
    }

    @Test
    fun `slash form respects ignore case flag`() {
        assertEquals("x x", run("/HELLO/gi", "x", "hello HELLO"))
        assertEquals("x HELLO", run("/HELLO/i", "x", "hello HELLO"))
    }

    @Test
    fun `multiline and dotall flags`() {
        assertEquals("a\nB\nc", run("/^b$/m", "B", "a\nb\nc"))
        assertEquals("X", run("/a.b/s", "X", "a\nb"))
        assertEquals("a\nb", run("/a.b/", "X", "a\nb"))
    }

    @Test
    fun `disabled script returns original`() {
        val s = script("a", "b", disabled = true)
        assertEquals("aaa", RegexScriptEngine.runRegexScript(s, "aaa"))
    }

    @Test
    fun `empty find regex returns original`() {
        assertEquals("aaa", run("", "b", "aaa"))
    }

    @Test
    fun `empty input returns original`() {
        assertEquals("", run("a", "b", ""))
    }

    // ==================== 分组引用 / {{match}} ====================

    @Test
    fun `numbered group refs`() {
        assertEquals("<a><bb>", run("\\[(.*?)]", "<$1>", "[a][bb]"))
    }

    @Test
    fun `missing group becomes empty string`() {
        assertEquals("a-", run("(a)(b)?", "$1-$2", "a"))
    }

    @Test
    fun `named group refs`() {
        assertEquals("[hi]", run("(?<w>\\w+)", "[$<w>]", "hi"))
    }

    @Test
    fun `match token is case insensitive`() {
        assertEquals("a<1>b<2>", run("\\d+", "<{{match}}>", "a1b2"))
        assertEquals("a<1>b<2>", run("\\d+", "<{{MATCH}}>", "a1b2"))
    }

    @Test
    fun `trim strings removed from group refs`() {
        // 官方语义：trimStrings 作用于被引用的匹配内容（filterString）
        assertEquals("a", run("\\[(.*?)]", "$1", "[xax]", trimStrings = listOf("x")))
        // {{match}} 引用同样过滤
        assertEquals("[a]", run("\\[.*?]", "{{match}}", "[xax]", trimStrings = listOf("x")))
    }

    @Test
    fun `trim strings are macro substituted`() {
        val s = script("\\[(.*?)]", "$1", trimStrings = listOf("{{t}}"))
        val sub: (String) -> String = { if (it == "{{t}}") "x" else it }
        assertEquals("a", RegexScriptEngine.runRegexScript(s, "[xax]", sub))
    }

    @Test
    fun `replacement text is substituted at the end`() {
        val s = script("a", "{{v}}")
        val sub: (String) -> String = { if (it == "{{v}}") "V" else it }
        assertEquals("V", RegexScriptEngine.runRegexScript(s, "a", sub))
    }

    // ==================== substituteRegex 模式 ====================

    @Test
    fun `substitute regex raw applies macros before compiling`() {
        val s = script("{{kw}}", "R", substituteRegex = SubstituteRegex.RAW)
        val sub: (String) -> String = { if (it == "{{kw}}") "a+" else it }
        assertEquals("R", RegexScriptEngine.runRegexScript(s, "aaa", sub))
        assertEquals("bbb", RegexScriptEngine.runRegexScript(s, "bbb", sub))
    }

    @Test
    fun `substitute regex escaped sanitizes macro value`() {
        val s = script("{{ch}}", "R", substituteRegex = SubstituteRegex.ESCAPED)
        val sub: (String) -> String = { if (it == "{{ch}}") "a." else it }
        // "a." 被转义为 "a\."，仅命中字面量 "a."
        assertEquals("a!", RegexScriptEngine.runRegexScript(s, "a!", sub))
        assertEquals("R", RegexScriptEngine.runRegexScript(s, "a.", sub))
    }

    @Test
    fun `sanitize regex macro escapes metacharacters`() {
        assertEquals("a\\.b", RegexScriptEngine.sanitizeRegexMacro("a.b"))
        assertEquals("\\[x\\]", RegexScriptEngine.sanitizeRegexMacro("[x]"))
        assertEquals("a\\nb", RegexScriptEngine.sanitizeRegexMacro("a\nb"))
        assertEquals("a-b", RegexScriptEngine.sanitizeRegexMacro("a-b"))
    }

    // ==================== getRegexedString 过滤矩阵 ====================

    @Test
    fun `plain script applies on both channels`() {
        val s = script("a", "b")
        assertEquals("b", RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT))
        assertEquals(
            "b",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isMarkdown = true),
        )
        assertEquals(
            "b",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isPrompt = true),
        )
    }

    @Test
    fun `markdown only script skips prompt channel`() {
        val s = script("a", "b", markdownOnly = true)
        assertEquals(
            "b",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isMarkdown = true),
        )
        assertEquals(
            "a",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isPrompt = true),
        )
    }

    @Test
    fun `prompt only script skips markdown channel`() {
        val s = script("a", "b", promptOnly = true)
        assertEquals(
            "b",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isPrompt = true),
        )
        assertEquals(
            "a",
            RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT, isMarkdown = true),
        )
    }

    @Test
    fun `placement filter`() {
        val s = script("a", "b", placement = listOf(RegexPlacement.USER_INPUT))
        assertEquals("a", RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.AI_OUTPUT))
        assertEquals("b", RegexScriptEngine.getRegexedString("a", listOf(s), RegexPlacement.USER_INPUT))
    }

    @Test
    fun `depth min and max filters`() {
        val min = script("a", "b", minDepth = 1)
        assertEquals("a", RegexScriptEngine.getRegexedString("a", listOf(min), RegexPlacement.AI_OUTPUT, depth = 0))
        assertEquals("b", RegexScriptEngine.getRegexedString("a", listOf(min), RegexPlacement.AI_OUTPUT, depth = 1))

        val max = script("a", "b", maxDepth = 2)
        assertEquals("b", RegexScriptEngine.getRegexedString("a", listOf(max), RegexPlacement.AI_OUTPUT, depth = 2))
        assertEquals("a", RegexScriptEngine.getRegexedString("a", listOf(max), RegexPlacement.AI_OUTPUT, depth = 3))
    }

    @Test
    fun `edit channel respects run on edit`() {
        val noEdit = script("a", "b")
        assertEquals(
            "a",
            RegexScriptEngine.getRegexedString("a", listOf(noEdit), RegexPlacement.AI_OUTPUT, isEdit = true),
        )
        val edit = script("a", "b", runOnEdit = true)
        assertEquals(
            "b",
            RegexScriptEngine.getRegexedString("a", listOf(edit), RegexPlacement.AI_OUTPUT, isEdit = true),
        )
    }

    @Test
    fun `scripts apply in list order`() {
        val first = script("a", "b")
        val second = script("b", "c")
        assertEquals(
            "c",
            RegexScriptEngine.getRegexedString("a", listOf(first, second), RegexPlacement.AI_OUTPUT),
        )
    }

    // ==================== v214 不定长 lookbehind 降级 ====================

    /**
     * 魔法少女卡 RS[2]「[MVU] 变量更新校验-放行」原 pattern（逐字，来自
     * notes/recon-card-magicgirl-20261002.md §1.2）。含不定长 lookbehind
     * `(?<=<\/UpdateVariable(?:variable)?>[\s\S]*?)`，Java/Kotlin 直接编译失败
     * （Look-behind group does not have an obvious maximum length）。
     */
    private val magicGirlRs2Pattern =
        "/<StatusPlaceHolderImpl\\/>(?=[\\s\\S]*?<UpdateVariable(?:variable)?>)|" +
            "(?<=<\\/UpdateVariable(?:variable)?>[\\s\\S]*?)<StatusPlaceHolderImpl\\/>/g"

    private fun magicGirlRs2Script() = script(
        find = magicGirlRs2Pattern,
        replace = "<StatusPlaceHolderREADY/>",
        placement = listOf(RegexPlacement.AI_OUTPUT),
        markdownOnly = true,
        runOnEdit = true,
    )

    @Test
    fun `magic girl RS2 unbounded lookbehind matches impl before variable`() {
        val input = "<StatusPlaceHolderImpl/>\n" +
            "<UpdateVariable><JSONPatch>[]</JSONPatch></UpdateVariable>"
        val out = RegexScriptEngine.getRegexedString(
            input,
            listOf(magicGirlRs2Script()),
            RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        assertTrue("Impl 在前应放行为 READY，实际：$out", out.contains("<StatusPlaceHolderREADY/>"))
        assertFalse("Impl 不应残留，实际：$out", out.contains("<StatusPlaceHolderImpl/>"))
    }

    @Test
    fun `magic girl RS2 unbounded lookbehind matches variable before impl`() {
        val input = "<UpdateVariable><JSONPatch>[]</JSONPatch></UpdateVariable>\n" +
            "<StatusPlaceHolderImpl/>"
        val out = RegexScriptEngine.getRegexedString(
            input,
            listOf(magicGirlRs2Script()),
            RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        assertTrue("变量在前应放行为 READY，实际：$out", out.contains("<StatusPlaceHolderREADY/>"))
        assertFalse("Impl 不应残留，实际：$out", out.contains("<StatusPlaceHolderImpl/>"))
    }

    @Test
    fun `magic girl RS2 keeps bare impl when no update variable present`() {
        val input = "正文\n<StatusPlaceHolderImpl/>"
        val out = RegexScriptEngine.getRegexedString(
            input,
            listOf(magicGirlRs2Script()),
            RegexPlacement.AI_OUTPUT,
            isMarkdown = true,
        )
        assertEquals(input, out)
    }

    @Test
    fun `bounded lookbehind behavior unchanged`() {
        assertEquals("12Y", run("(?<=\\d{2})x", "Y", "12x"))
        assertEquals("1x", run("(?<=\\d{2})x", "Y", "1x"))
    }

    @Test
    fun `unbounded lookbehind degradation notifies degraded hook`() {
        val msgs = mutableListOf<String>()
        val prev = RegexScriptEngine.onCompileDegraded
        RegexScriptEngine.onCompileDegraded = { msgs += it }
        try {
            // 独立 pattern，避免命中编译缓存导致钩子不触发；
            // `(?<=a.*?)` 实测 Java 会报 Look-behind group does not have an obvious maximum length
            val compiled = RegexScriptEngine.compileRegex("(?<=a.*?)X")
            assertNotNull("降级后应编译成功", compiled)
            val regex = requireNotNull(compiled)
            assertTrue(
                "应触发降级钩子，实际：$msgs",
                msgs.any { it.startsWith("[regex] degraded unbounded lookbehind:") },
            )
            assertTrue("降级后的 lookbehind 应命中 abcX", regex.containsMatchIn("abcX"))
            assertFalse("降级后仍应要求 a 前缀", regex.containsMatchIn("bcX"))
        } finally {
            RegexScriptEngine.onCompileDegraded = prev
        }
    }

    @Test
    fun `degradation failure reports onCompileError without throwing`() {
        val errors = mutableListOf<String>()
        val prev = RegexScriptEngine.onCompileError
        RegexScriptEngine.onCompileError = { errors += it }
        try {
            // 括号未闭合：降级也无法编译，必须走 onCompileError 而不是抛异常
            val s = script("(?<=a*", "X")
            val out = RegexScriptEngine.runRegexScript(s, "aaa")
            assertEquals("aaa", out)
            assertTrue("应触发 onCompileError，实际：$errors", errors.isNotEmpty())
            assertTrue(
                "失败消息应带 [regex] compile failed，实际：${errors.first()}",
                errors.first().startsWith("[regex] compile failed: pattern="),
            )
        } finally {
            RegexScriptEngine.onCompileError = prev
        }
    }
}
