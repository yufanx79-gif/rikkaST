package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `file` 工具 glob 匹配的回归测试。
 *
 * ## 修的是什么
 *
 * 旧实现：
 * ```kotlin
 * val pattern = Regex.escape(this).replace("\\*", ".*").replace("\\?", ".")
 * ```
 *
 * `Regex.escape("rikka*")` 返回 `\Qrikka*\E` —— 整个字符串被包进 `\Q...\E`，
 * 内部全是字面量。此时串里只有一个 `*`，并没有「反斜杠+星号」这个两字符序列，
 * 所以 `.replace("\\*", ".*")` **一次都匹配不到**，替换静默失效。
 *
 * 后果（真机 2026-11-15 实测）：`rikka*`、`*.txt`、`a?b`、`file[1].txt`
 * 全部匹配失败，`file` 工具的 name 搜索与 content 搜索的 file_pattern 双双失效。
 *
 * 修法：逐字符转换，只有 `*` 和 `?` 变通配，其余字符逐个转义。
 */
class FileToolsGlobTest {

    private fun matches(glob: String, name: String) = glob.toGlobRegex().matches(name)

    // ── 通配符必须真的生效 ──────────────────────────────────────

    @Test
    fun `星号匹配任意后缀`() {
        assertTrue("rikka* 应匹配 rikka_tool_test.txt", matches("rikka*", "rikka_tool_test.txt"))
        assertTrue("rikka* 应匹配 rikka", matches("rikka*", "rikka"))
    }

    @Test
    fun `星号匹配任意前缀`() {
        assertTrue("*.txt 应匹配 rikka_tool_test.txt", matches("*.txt", "rikka_tool_test.txt"))
        assertTrue("*.txt 应匹配 a.txt", matches("*.txt", "a.txt"))
    }

    @Test
    fun `问号匹配单个字符`() {
        assertTrue("a?c 应匹配 abc", matches("a?c", "abc"))
        assertFalse("a?c 不应匹配 ac", matches("a?c", "ac"))
        assertFalse("a?c 不应匹配 abbc", matches("a?c", "abbc"))
    }

    @Test
    fun `多个通配符组合`() {
        assertTrue("*.tar.* 应匹配 pkg.tar.gz", matches("*.tar.*", "pkg.tar.gz"))
        assertTrue("*a*b* 应匹配 xaxbx", matches("*a*b*", "xaxbx"))
    }

    // ── 正则元字符必须按字面量处理（不能当正则）──────────────

    @Test
    fun `方括号按字面量匹配`() {
        assertTrue("file[1].txt 应匹配自身", matches("file[1].txt", "file[1].txt"))
        // 若被当成正则字符类，会匹配 file1.txt
        assertFalse("file[1].txt 不应匹配 file1.txt", matches("file[1].txt", "file1.txt"))
    }

    @Test
    fun `点号按字面量匹配`() {
        assertTrue("a.txt 应匹配 a.txt", matches("a.txt", "a.txt"))
        assertFalse("a.txt 不应匹配 aXtxt", matches("a.txt", "aXtxt"))
    }

    @Test
    fun `圆括号与加号按字面量匹配`() {
        assertTrue("(a)+.md 应匹配自身", matches("(a)+.md", "(a)+.md"))
        assertFalse("(a)+.md 不应匹配 aa.md", matches("(a)+.md", "aa.md"))
    }

    @Test
    fun `反斜杠不会破坏正则`() {
        // Windows 风格路径片段不应抛异常
        val re = "a\\b".toGlobRegex()
        assertTrue(re.matches("a\\b"))
    }

    // ── 边界 ────────────────────────────────────────────────────

    @Test
    fun `大小写不敏感`() {
        assertTrue("RIKKA* 应匹配 rikka.txt", matches("RIKKA*", "rikka.txt"))
        assertTrue("*.TXT 应匹配 a.txt", matches("*.TXT", "a.txt"))
    }

    @Test
    fun `单个星号匹配任意文件名`() {
        assertTrue(matches("*", "anything"))
        assertTrue(matches("*", "a.b.c"))
    }

    @Test
    fun `空模式只匹配空串`() {
        assertTrue(matches("", ""))
        assertFalse(matches("", "a"))
    }

    @Test
    fun `无通配符时是精确匹配`() {
        assertTrue(matches("exact.txt", "exact.txt"))
        assertFalse(matches("exact.txt", "exact.txt.bak"))
        assertFalse(matches("exact.txt", "other.txt"))
    }
}
