package me.rerere.rikkahub.data.st.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StSlashParser 单测（纯 JVM，无 Android 依赖）。
 *
 * 覆盖对齐 SillyTavern `SlashCommandParser` 的核心语法语义：
 * - `|` 管道切分（引号 / 宏 `{}` / `()` / `[]` 感知，`\|` 转义）；
 * - `||` 双管道 = 下一命令禁用管道注入；
 * - 命令名小写化 / args 保留 / 纯文本段；
 * - 命名参数 `key=value` / `key="quoted value"` 提取；
 * - 外层引号剥离。
 */
class StSlashParserTest {

    // ---------- splitParts / splitPipelines ----------

    @Test
    fun basicPipeSplit() {
        assertEquals(
            listOf("/send hi", "/trigger"),
            StSlashParser.splitPipelines("/send hi | /trigger"),
        )
    }

    @Test
    fun noSplitInsideDoubleQuotes() {
        assertEquals(
            listOf("/send \"a | b\"", "/trigger"),
            StSlashParser.splitPipelines("/send \"a | b\" | /trigger"),
        )
    }

    @Test
    fun noSplitInsideSingleQuotes() {
        assertEquals(
            listOf("/send 'a | b'"),
            StSlashParser.splitPipelines("/send 'a | b'"),
        )
    }

    @Test
    fun noSplitInsideMacroBraces() {
        assertEquals(
            listOf("/setvar x {{pick::a|b}}"),
            StSlashParser.splitPipelines("/setvar x {{pick::a|b}}"),
        )
    }

    @Test
    fun noSplitInsideParensAndBrackets() {
        assertEquals(listOf("/echo (a|b)"), StSlashParser.splitPipelines("/echo (a|b)"))
        assertEquals(listOf("/echo [a|b]"), StSlashParser.splitPipelines("/echo [a|b]"))
    }

    @Test
    fun escapedPipeDoesNotSplit() {
        val parts = StSlashParser.splitParts("/send a \\| b")
        assertEquals(1, parts.size)
        assertEquals("/send a \\| b", parts[0].text)
    }

    @Test
    fun doublePipeFlagsNextSegment() {
        val parts = StSlashParser.splitParts("/echo hello || /echo world")
        assertEquals(2, parts.size)
        assertFalse(parts[0].noPipeInject)
        assertTrue(parts[1].noPipeInject)
    }

    @Test
    fun singlePipeDoesNotFlag() {
        val parts = StSlashParser.splitParts("/echo hello | /echo world")
        assertEquals(2, parts.size)
        assertFalse(parts[0].noPipeInject)
        assertFalse(parts[1].noPipeInject)
    }

    @Test
    fun blankSegmentsDropped() {
        assertEquals(
            listOf("/echo a", "/echo b"),
            StSlashParser.splitPipelines("/echo a |  | /echo b"),
        )
    }

    // ---------- parseParts / parse ----------

    @Test
    fun commandNameLowercasedAndArgsKept() {
        val seg = StSlashParser.parse("/SEND Hello World").single()
        assertTrue(seg.isCommand)
        assertEquals("send", seg.command)
        assertEquals("Hello World", seg.args)
    }

    @Test
    fun plainTextSegment() {
        val seg = StSlashParser.parse("just text").single()
        assertFalse(seg.isCommand)
        assertEquals("", seg.command)
        assertEquals("just text", seg.args)
    }

    @Test
    fun commandWithoutArgs() {
        val seg = StSlashParser.parse("/trigger").single()
        assertEquals("trigger", seg.command)
        assertEquals("", seg.args)
    }

    @Test
    fun noPipeInjectCarriedIntoSegment() {
        val segs = StSlashParser.parse("/echo a || /echo b")
        assertEquals(2, segs.size)
        assertTrue(segs[1].noPipeInject)
    }

    // ---------- extractNamedArgs ----------

    @Test
    fun namedArgsSimple() {
        val (named, rest) = StSlashParser.extractNamedArgs("at=0 name=Alice hello world")
        assertEquals("0", named["at"])
        assertEquals("Alice", named["name"])
        assertEquals("hello world", rest)
    }

    @Test
    fun namedArgsQuotedValue() {
        val (named, rest) = StSlashParser.extractNamedArgs("name=\"Foo Bar\" text here")
        assertEquals("Foo Bar", named["name"])
        assertEquals("text here", rest)
    }

    @Test
    fun namedArgsKeysLowercased() {
        val (named, _) = StSlashParser.extractNamedArgs("NAME=x")
        assertEquals("x", named["name"])
    }

    @Test
    fun nonNamedTokenGoesToRest() {
        val (named, rest) = StSlashParser.extractNamedArgs("hello name=x world")
        assertEquals("x", named["name"])
        assertEquals("hello world", rest)
    }

    @Test
    fun namedArgsEscapedQuoteInsideQuotedValue() {
        val (named, _) = StSlashParser.extractNamedArgs("v=\"a\\\"b\"")
        assertEquals("a\"b", named["v"])
    }

    // ---------- unwrapQuotes ----------

    @Test
    fun unwrapQuotesBasic() {
        assertEquals("hello world", StSlashParser.unwrapQuotes("\"hello world\""))
        assertEquals("hello world", StSlashParser.unwrapQuotes("'hello world'"))
        assertEquals("hello", StSlashParser.unwrapQuotes("hello"))
        assertEquals("\"a\" b", StSlashParser.unwrapQuotes("\"a\" b"))
    }
}
