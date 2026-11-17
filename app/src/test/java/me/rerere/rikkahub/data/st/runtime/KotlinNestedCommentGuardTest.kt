package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [v248] Kotlin 块注释嵌套守卫。
 *
 * ## 为什么需要这个测试
 * Kotlin 的块注释**可以嵌套**（和 Java/C 不同）：注释里再写一个「斜杠加星号」，
 * 就会开启新的一层，直到再遇到两个「星号加斜杠」才闭合。于是后面正常的函数定义
 * 全被吞进注释里。
 *
 * 后果是文件后半部分整个失效，但编译器报的是**别处**的错误
 * （`Unresolved reference` / `Missing }` / `Unclosed comment`），
 * 定位方向完全跑偏。这个坑在本项目实际踩过两次，浪费了大量时间。
 *
 * 这个测试扫描全部 Kotlin 源码，确保注释里不出现嵌套开启符。
 * 注意要跳过字符串字面量 —— `video` 后面跟斜杠星号这种 MIME 通配符是合法的。
 */
class KotlinNestedCommentGuardTest {

    @Test
    fun `no nested block comments in kotlin sources`() {
        val srcRoot = findSourceRoot()
        val offenders = mutableListOf<String>()

        srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val hits = scanNested(file.readText())
                hits.forEach { line ->
                    offenders += "${file.relativeTo(srcRoot).invariantSeparatorsPath}:$line"
                }
            }

        assertTrue(
            "Kotlin 块注释里出现斜杠加星号会导致注释嵌套、后续代码被吞掉。\n" +
                "请把 KDoc 里的斜杠加星号改掉（例如 /api/xxx/* → /api/xxx/ 系列）。\n" +
                "违规位置:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    /** 找到 `app/src/main/java`（单测 cwd 通常是模块目录或仓库根）。 */
    private fun findSourceRoot(): File {
        val candidates = listOf(
            File("src/main/java"),
            File("app/src/main/java"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("找不到源码目录，cwd=${File(".").absolutePath}")
    }

    /**
     * 逐字符扫描，返回「处于块注释内又遇到斜杠加星号」的行号列表。
     * 正确跳过：`//` 行注释、`"..."`、`"""..."""`、`'c'`。
     */
    private fun scanNested(src: String): List<Int> {
        val hits = mutableListOf<Int>()
        var depth = 0
        var line = 1
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == '\n') { line++; i++; continue }

            if (depth == 0) {
                when {
                    src.startsWith("/*", i) -> { depth = 1; i += 2 }
                    src.startsWith("//", i) -> {
                        val j = src.indexOf('\n', i)
                        i = if (j < 0) n else j
                    }
                    src.startsWith("\"\"\"", i) -> {
                        val j = src.indexOf("\"\"\"", i + 3)
                        if (j < 0) { i = n } else {
                            line += src.substring(i, j).count { it == '\n' }
                            i = j + 3
                        }
                    }
                    c == '"' -> {
                        i++
                        while (i < n && src[i] != '"') {
                            if (src[i] == '\\') i++
                            if (i < n && src[i] == '\n') line++
                            i++
                        }
                        i++
                    }
                    c == '\'' -> {
                        i++
                        while (i < n && src[i] != '\'') {
                            if (src[i] == '\\') i++
                            i++
                        }
                        i++
                    }
                    else -> i++
                }
            } else {
                when {
                    src.startsWith("*/", i) -> { depth--; i += 2 }
                    src.startsWith("/*", i) -> { hits += line; depth++; i += 2 }
                    else -> i++
                }
            }
        }
        return hits
    }

    /** 自检：扫描器本身必须能抓到已知的坏样本，否则这个守卫是假绿。 */
    @Test
    fun `scanner detects a known bad sample`() {
        // 用变量拼接构造样本，避免本文件自身触发守卫
        val open = "/" + "*"
        val close = "*" + "/"
        // 真正的坏样本：注释里又开了一层（第 2 行），外层 close 只关掉内层
        val bad = listOf(
            " " + open + open,
            " * 上游走 /api/translate 系列，这里的 open 会再开一层",
            " " + close,
            "fun foo() {}",
        ).joinToString("\n")
        assertEquals(listOf(1), scanNested(bad))

        // 合法样本：字符串里的斜杠星号不算
        val good = listOf("fun bar() {", "    val mime = \"video" + open + "\"", "}").joinToString("\n")
        assertEquals(emptyList<Int>(), scanNested(good))
    }
}
