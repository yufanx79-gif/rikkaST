package me.rerere.rikkahub.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 错误上报守卫：只有「标题生成失败」被静默，**其他所有错误仍然必须弹红框**。
 *
 * ## 这个测试为什么存在
 *
 * 主人看到「把 addError 改成 log」时立刻警觉：
 *
 * > 有点担心啊，你把这个 add error 给它改成 log 的话，其他的报错不会也变成 log 了吗
 *
 * 这个担心完全合理。`ChatService.addError()` 是全 App 的错误上报入口。
 * 一旦被改坏，发送失败 / 重新生成失败 / 工具审批失败都会变成静默日志，
 * 用户界面上什么都看不到 —— 这比原 bug 严重得多。
 *
 * ## 这个测试被推翻重写两次，值得记下来
 *
 * **第 1 版**：`src.contains("error_title_send_message")`。
 * 故意把一处 `addError` 改成 `appendLog` 做负向验证 —— **没抓到**。
 * 该字符串在文件里出现 10 次，只要有一处还在 `contains` 就为真。
 *
 * **第 2 版**：从每个出现位置向前回溯 1500 字符找最近的 `addError(`。
 * 再次故意改坏 —— **仍然没抓到**。原因：多行调用
 * （`addError(\n  ...,\n  title = R.string.xxx,\n)`）里，
 * 回溯会一路穿过前面的 `)` 找到**别的** `addError`，误判为正常。
 *
 * **第 3 版（当前）**：按**配对括号**切出每个 `addError(...)` / `appendLog(...)`
 * 的完整参数块，再看块里包含哪些错误标题。多行调用天然正确。
 *
 * ## 为什么还需要「基线数量」这道闸
 *
 * 块切分能抓到「把 addError 换成 appendLog 但保留标题字符串」；
 * 但如果有人把整行删掉（标题字符串也一起消失），块切分看不到任何异常。
 * 所以再对比 `git HEAD` 的基线数量：任何标题的 addError 块数**减少**即失败。
 *
 * 基线不可得时（例如打包源码、无 .git）跳过该断言，不误报。
 */
class ChatServiceErrorReportingGuardTest {

    private fun sourceFile(): File =
        listOf(
            File("src/main/java/me/rerere/rikkahub/service/ChatService.kt"),
            File("app/src/main/java/me/rerere/rikkahub/service/ChatService.kt"),
        ).firstOrNull { it.isFile }
            ?: error("找不到 ChatService.kt，cwd=${File(".").absolutePath}")

    private fun read(p: String): String = p.replace("\r\n", "\n")

    private fun currentSource(): String = read(sourceFile().readText())

    /** 截取 `fun addError(` 的函数体。 */
    private fun addErrorBody(src: String): String {
        val start = src.indexOf("    fun addError(")
        assertTrue("找不到 addError 定义", start > 0)
        val braceStart = src.indexOf('{', start)
        var depth = 0
        var i = braceStart
        while (i < src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(braceStart, i + 1)
                }
            }
            i++
        }
        error("addError 花括号不闭合")
    }

    /**
     * 切出所有 `name(...)` 调用的完整参数块（配对括号）。
     *
     * @return (起始偏移, 结束偏移, 块文本)
     */
    private fun callBlocks(src: String, name: String): List<Triple<Int, Int, String>> {
        val out = mutableListOf<Triple<Int, Int, String>>()
        val regex = Regex("""\b${Regex.escape(name)}\(""")
        for (m in regex.findAll(src)) {
            val start = m.range.first
            var i = m.range.last // 指向 '('
            var depth = 0
            while (i < src.length) {
                when (src[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) {
                            out += Triple(start, i + 1, src.substring(start, i + 1))
                            break
                        }
                    }
                }
                i++
            }
        }
        return out
    }

    private fun lineOf(src: String, offset: Int): Int = src.substring(0, offset).count { it == '\n' } + 1

    /**
     * 统计每个错误标题被哪些上报动作带走。
     *
     * @return title → list of ("addError"|"appendLog", line)
     */
    private fun reportingMap(src: String): Map<String, List<Pair<String, Int>>> {
        val addErrors = callBlocks(src, "addError")
        val appendLogs = callBlocks(src, "appendLog")
        val titles = listOf(
            "error_title_send_message",
            "error_title_regenerate_message",
            "error_title_tool_approval",
            "error_title_generate_title",
        )
        return titles.associateWith { t ->
            buildList {
                addErrors.filter { it.third.contains(t) }
                    .forEach { add("addError" to lineOf(src, it.first)) }
                appendLogs.filter { it.third.contains(t) }
                    .forEach { add("appendLog" to lineOf(src, it.first)) }
            }
        }
    }

    /** 从 git HEAD 读基线（失败返回 null）。 */
    private fun baselineSource(): String? = runCatching {
        val pb = ProcessBuilder("git", "show", "HEAD:app/src/main/java/me/rerere/rikkahub/service/ChatService.kt")
        pb.directory(sourceFile().absoluteFile.parentFile.parentFile.parentFile.parentFile.parentFile.parentFile)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor(20, TimeUnit.SECONDS)
        if (proc.exitValue() != 0 || text.isBlank()) null else read(text)
    }.getOrNull()

    // ---------------------------------------------------------------- 核心

    @Test
    fun `addError still pushes into UI-visible error stream`() {
        val body = addErrorBody(currentSource())
        assertTrue(
            "addError() 必须继续把错误放进 _errors（UI 靠它渲染红框）。\n" +
                "若这里被改成只写日志，全 App 的错误都会静默。实际函数体：\n$body",
            body.contains("_errors.update"),
        )
        assertTrue("addError() 必须继续构造 ChatError 条目", body.contains("ChatError("))
        assertTrue(
            "addError() 仍应对 CancellationException 早退（取消不是错误）",
            body.contains("if (error is CancellationException) return"),
        )
    }

    /**
     * 用户能感知的失败场景，每一处都必须仍然走 addError。
     *
     * 负向验证过：把任意一处 `addError(... error_title_send_message)` 换成
     * `appendLog(...)`，这条会失败（前提是标题字符串仍在 appendLog 参数里）。
     */
    @Test
    fun `critical errors still routed to addError`() {
        val map = reportingMap(currentSource())

        for (title in listOf(
            "error_title_send_message",
            "error_title_regenerate_message",
            "error_title_tool_approval",
        )) {
            val entries = map.getValue(title)
            assertTrue("「$title」应至少被 addError 上报 1 次，实际=$entries", entries.isNotEmpty())

            val silenced = entries.filter { it.first == "appendLog" }
            assertTrue(
                "「$title」有 ${silenced.size} 处被改成 appendLog（静默），行号=" +
                    silenced.map { it.second } +
                    "。用户可感知的错误必须继续走 addError。",
                silenced.isEmpty(),
            )
            assertTrue(
                "「$title」应至少有一处 addError。实际=$entries",
                entries.any { it.first == "addError" },
            )
        }
    }

    @Test
    fun `addError call sites not mass-replaced by logging`() {
        val src = currentSource()
        val callCount = callBlocks(src, "addError").size
        assertTrue(
            "addError 调用块数量异常（疑似被批量替换成日志）。实际 $callCount 块，期望 >= 15。",
            callCount >= 15,
        )
    }

    // ---------------------------------------------------------------- 唯一允许静默的一处

    @Test
    fun `only title generation failure is silenced`() {
        val src = currentSource()

        val markers = Regex("""\[title\]""").findAll(src).count()
        assertEquals("只应有 1 处标题生成失败的静默日志（[title] 标记）", 1, markers)

        val idx = src.indexOf("[title]")
        val block = src.substring((idx - 900).coerceAtLeast(0), (idx + 250).coerceAtMost(src.length))
        assertTrue(
            "标题生成失败的处理块里不应再出现 addError(...)。实际上下文：\n$block",
            !block.contains("addError("),
        )
        assertTrue(
            "标题生成失败应记录为 warn 级日志，便于排查",
            block.contains("appendLog(") && block.contains("\"warn\""),
        )
    }

    @Test
    fun `title error string no longer wired to addError`() {
        val map = reportingMap(currentSource())
        val entries = map.getValue("error_title_generate_title")
        assertTrue(
            "error_title_generate_title 不应再由 addError 上报。实际=$entries",
            entries.none { it.first == "addError" },
        )
    }

    // ---------------------------------------------------------------- 基线数量闸

    /**
     * 与 `git HEAD` 对比：任何标题的 addError 上报次数**不得减少**。
     *
     * 这道闸能抓到「整行删掉」这种块切分看不到的破坏。
     */
    @Test
    fun `no error title lost its addError reporting compared to git HEAD`() {
        val baseline = baselineSource()
        if (baseline == null) {
            println("[guard] 无法读取 git HEAD 基线（可能不在 git 工作区），跳过数量闸")
            return
        }

        val baseMap = reportingMap(baseline)
        val curMap = reportingMap(currentSource())

        val regressions = mutableListOf<String>()
        for ((title, baseEntries) in baseMap) {
            val baseCount = baseEntries.count { it.first == "addError" }
            val curCount = curMap[title].orEmpty().count { it.first == "addError" }
            // error_title_generate_title 是本次有意静默的，允许减少
            if (title == "error_title_generate_title") continue
            if (curCount < baseCount) {
                regressions += "$title: HEAD=$baseCount → 现在=$curCount"
            }
        }

        assertTrue(
            "以下错误标题的 addError 上报次数比 HEAD 少了（疑似被改成静默）：\n" +
                regressions.joinToString("\n") +
                "\n\n当前分布：\n" +
                curMap.entries.joinToString("\n") { (t, v) -> "  $t -> $v" },
            regressions.isEmpty(),
        )
    }
}
