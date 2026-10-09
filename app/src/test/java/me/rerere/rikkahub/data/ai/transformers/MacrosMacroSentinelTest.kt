package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

/**
 * [v236.1 P0] `MacrosMacroPass.SENTINEL` 正则回归 + 「ICU 严格括号」仓库级 lint。
 *
 * 事故（真机 2026-10-06 主人上报）：
 *   `Regex("\{\{__rikka_fn::([^:}]+)::([^}]+)}}")` 结尾是**裸 `}}`**。
 *   - 桌面 JVM 的 `java.util.regex.Pattern` **容忍**它 → 566 条单测全绿、ESM 全绿，CI 毫无察觉；
 *   - **Android 的 `com.android.icu.regex.PatternNative` 直接抛 `PatternSyntaxException`**
 *     （`Syntax error in regexp pattern near index 34`）→ `MacrosMacroPass`（混淆名 `oc7`）`<clinit>` 失败
 *     → `ExceptionInInitializerError` → `NoClassDefFoundError`
 *     → 真机表现：「消息生成失败 oc7」/「未知错误 unknown error」/ 群聊发消息直接闪退。
 *
 * 因此本测试**不能**只依赖 `Regex(...)` 能否在 JVM 编译（那正是漏网原因），
 * 而要按 **ICU 的严格规则**静态校验：`{` / `}` 必须转义，或构成合法量词 `{n}` / `{n,}` / `{n,m}`。
 */
class MacrosMacroSentinelTest {

    // ---------------------------------------------------------------- 定点回归

    @Test
    fun sentinelPatternUsesEscapedTrailingBraces() {
        val p = MacrosMacroPass.SENTINEL.pattern
        assertFalse("SENTINEL 结尾不能是裸 `}}`（Android ICU 会抛 PatternSyntaxException）: $p", p.contains(")}}"))
        assertTrue("SENTINEL 的右花括号必须转义成 \\}\\}: $p", p.contains("\\}\\}"))
    }

    @Test
    fun sentinelMatchesFunctionMacroPlaceholder() {
        val ms = MacrosMacroPass.SENTINEL.findAll("x {{__rikka_fn::myMacro::abc123}} y").toList()
        assertEquals(1, ms.size)
        assertEquals("myMacro", ms[0].groupValues[1])
        assertEquals("abc123", ms[0].groupValues[2])
    }

    @Test
    fun sentinelIgnoresOrdinaryMacrosAndEmptyName() {
        assertTrue(MacrosMacroPass.SENTINEL.findAll("{{getvar::x}} {{char}}").toList().isEmpty())
    }

    @Test
    fun lintDetectsTheOriginalBugPattern() {
        // 负向对照：证明 lint 真能抓住当初那条出事的正则（否则 lint 可能是个空壳）
        val buggy = "\\{\\{__rikka_fn::([^:}]+)::([^}]+)}}"
        val fixed = "\\{\\{__rikka_fn::([^:}]+)::([^}]+)\\}\\}"
        assertTrue("lint 必须标出裸 `}}`", bareBraceIndexes(buggy).isNotEmpty())
        assertTrue("lint 不能误报修好后的写法", bareBraceIndexes(fixed).isEmpty())
        assertTrue("lint 不能误报合法量词", bareBraceIndexes("\\n{3,}").isEmpty())
        assertTrue("lint 不能误报字符类里的 {} ", bareBraceIndexes("[{}]").isEmpty())
        assertTrue("lint 不能误报 \\p{..} 属性转义", bareBraceIndexes("\\p{L}+").isEmpty())
    }

    // ---------------------------------------------------------------- ICU 严格括号 lint

    /** 合法量词：`{n}` / `{n,}` / `{n,m}` */
    private val quant = Pattern.compile("\\{\\d+(,\\d*)?\\}")

    /**
     * 返回正则里「ICU 会拒绝」的裸括号下标。
     * 规则：`{` / `}` 必须被 `\\` 转义，或构成合法量词。
     * 已排除：字符类 `[...]` 内部（ICU 允许字面 `{` `}`）、`\\p{..}` / `\\P{..}` 属性转义。
     */
    private fun bareBraceIndexes(re: String): List<Int> {
        val bad = mutableListOf<Int>()
        var i = 0
        var inClass = false
        while (i < re.length) {
            val c = re[i]
            if (c == '\\') {
                // 属性转义 \p{...} / \P{...}：整段跳过
                if (i + 1 < re.length && (re[i + 1] == 'p' || re[i + 1] == 'P')) {
                    val close = re.indexOf('}', i + 2)
                    if (close >= 0) { i = close + 1; continue }
                }
                i += 2
                continue
            }
            if (c == '[' && !inClass) { inClass = true; i++; continue }
            if (c == ']' && inClass) { inClass = false; i++; continue }
            if (!inClass && c == '{') {
                val m = quant.matcher(re.substring(i))
                if (m.lookingAt()) { i += m.end(); continue }
                bad += i
            } else if (!inClass && c == '}') {
                bad += i
            }
            i++
        }
        return bad
    }

    /** 把 Kotlin 单行字符串字面量还原成正则实参（处理 `\\` 与 `\"`；`${..}` 是 Kotlin 插值，先剥掉）。 */
    private fun literalToRegex(lit: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < lit.length) {
            val c = lit[i]
            if (c == '$' && i + 1 < lit.length && lit[i + 1] == '{') {
                val close = lit.indexOf('}', i + 2)
                if (close >= 0) { i = close + 1; continue }
            }
            if (c == '\\' && i + 1 < lit.length) {
                val n = lit[i + 1]
                if (n == '\\') { sb.append('\\'); i += 2; continue }
                if (n == '"') { sb.append('"'); i += 2; continue }
                sb.append('\\').append(n); i += 2; continue
            }
            sb.append(c); i++
        }
        return sb.toString()
    }

    private val singleLineRegex = Pattern.compile("Regex\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val tripleRegex = Pattern.compile("Regex\\(\\s*\"\"\"([\\s\\S]*?)\"\"\"")

    @Test
    fun noBareBracesInAnyKotlinRegexLiteral() {
        val root = findMainSourceRoot()
        assumeTrue("找不到 app/src/main 源码根，跳过 lint", root != null)

        val offenders = mutableListOf<String>()
        var scanned = 0
        root!!.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach { f ->
                val text = f.readText()
                for ((re, lit) in regexLiteralsIn(text)) {
                    scanned++
                    val idx = bareBraceIndexes(re)
                    if (idx.isNotEmpty()) {
                        offenders += "${f.relativeTo(root).path}: $lit  -> 裸括号下标 $idx"
                    }
                }
            }

        assertTrue("扫描到 0 个 Regex 字面量，lint 失效", scanned > 0)
        assertTrue(
            "以下 Regex 字面量含裸 { }（Android ICU 会抛 PatternSyntaxException，桌面 JVM 不会）：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    private fun regexLiteralsIn(text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val tripleRanges = mutableListOf<IntRange>()
        val m3 = tripleRegex.matcher(text)
        while (m3.find()) {
            tripleRanges += m3.start()..m3.end()
            out += literalToRegex(m3.group(1)) to m3.group(1)
        }
        val m1 = singleLineRegex.matcher(text)
        while (m1.find()) {
            if (tripleRanges.any { m1.start() in it }) continue
            out += literalToRegex(m1.group(1)) to m1.group(1)
        }
        return out
    }

    private fun findMainSourceRoot(): File? {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
            File("../app/src/main/java"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }
}
