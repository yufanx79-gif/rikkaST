package me.rerere.rikkahub.data.st.script

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [今晚 P2] 「12 条斜杠命令只挂名不干活」回归守卫。
 *
 * ## 背景：一次被审计工具骗过去的事故
 *
 * 旧的 `dev-scripts/slash_coverage.mjs` 报告「ST 99 条命令 / 我方 419 / 缺 0」，
 * 全绿。但它实际的判定是：
 *
 * ```js
 * const ours = new Set([...oursSrc.matchAll(/"([a-z][a-z0-9-]*)"/g)].map(m => m[1]));
 * ```
 *
 * 在**整个 StSlashExecutor.kt** 里 grep 所有带引号的小写单词 —— 注释、日志文案、
 * 命名参数名、变量名全被算进去。419 只代表「文件里出现过 419 个不同的带引号词」，
 * 跟实现覆盖毫无关系。
 *
 * 重写脚本（递归扫全 public/scripts 目录 + 只认 `->` 左侧字面量 + 扣除派发名单 + 扣除纯 no-op）
 * 之后的真实结果：
 *
 * ```
 * ST 内置命令总数: 291
 * 有真实行为覆盖: 279 / 291 = 95.9%
 * 接受但无真实行为: 12
 * ```
 *
 * 那 12 条只出现在 `StSlashExecutor` 的派发名单里，
 * `ChatSlashHost.executeExtendedCommand` 没有分支 → 返回 null →
 * 落到 `runFallbackExtendedCommand` 的 `else -> unnamed` → **把输入原样回显**。
 * 不报错、不崩溃、什么也没做。
 *
 * ## 本测试锁什么
 * 1. 12 条命令必须在 `ChatSlashHost.kt` 里有**显式分支**（不再只活在派发名单）
 * 2. A 组 4 条必须真的写 `StSlashRuntimeOverrides`（不是返回空串）
 * 3. B/C 组 8 条必须走 `slashUnsupported`（给出可见错误，不静默成功）
 */
class StSlashSilentNoOpGuardTest {

    private fun find(rel: String): File? = listOf(
        File(rel),
        File("app/$rel"),
    ).firstOrNull { it.isFile }

    private val hostSrc: String by lazy {
        val f = find("src/main/java/me/rerere/rikkahub/ui/pages/chat/ChatSlashHost.kt")
            ?: error("找不到 ChatSlashHost.kt")
        f.readText()
    }

    private val overridesSrc: String by lazy {
        val f = find("src/main/java/me/rerere/rikkahub/data/st/script/StSlashRuntimeOverrides.kt")
            ?: error("找不到 StSlashRuntimeOverrides.kt")
        f.readText()
    }

    private val generationSrc: String by lazy {
        val f = find("src/main/java/me/rerere/rikkahub/data/ai/GenerationLoop.kt")
            ?: error("找不到 GenerationLoop.kt")
        f.readText()
    }

    /** A 组：真接线，必须写 runtime overrides。 */
    private val groupA = listOf("start-reply-with", "stop-strings", "prompt-post-processing", "ask")

    /** B/C 组：宿主暂不具备能力，必须给出明确「不支持」文案。 */
    private val groupBC = listOf(
        "bgcol", "css-var", "list-gallery", "show-gallery", "pick-icon",
        "chat-jump", "chat-manager", "yt-script",
    )

    @Test
    fun `12 条命令都必须在 ChatSlashHost 有显式分支`() {
        val missing = (groupA + groupBC).filter { cmd ->
            // 行锚定：只认 `"cmd" ->` 或 `"cmd", ... ->` 这种真正出现在 when 左侧的写法。
            !Regex("""^\s*(?:"[a-z0-9-]+"\s*,\s*)*"${Regex.escape(cmd)}""", RegexOption.MULTILINE)
                .containsMatchIn(hostSrc)
        }
        assertTrue(
            "以下命令在 ChatSlashHost.executeExtendedCommand 里没有分支，" +
                "会落回 runFallbackExtendedCommand 的 `else -> unnamed`（静默回显输入）: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `A 组 4 条必须写 StSlashRuntimeOverrides 或调用 gen`() {
        assertTrue(
            "/start-reply-with 必须写 StSlashRuntimeOverrides.assistantPrefill",
            hostSrc.contains("StSlashRuntimeOverrides.assistantPrefill"),
        )
        assertTrue(
            "/stop-strings 必须写 StSlashRuntimeOverrides.stopStrings",
            hostSrc.contains("StSlashRuntimeOverrides.stopStrings"),
        )
        assertTrue(
            "/prompt-post-processing 必须写 StSlashRuntimeOverrides.promptPostProcessing",
            hostSrc.contains("StSlashRuntimeOverrides.promptPostProcessing"),
        )
        // /ask 走 quiet prompt（gen），不写 overrides
        val askBlock = hostSrc.substringAfter("\"ask\" ->", "")
        assertTrue(
            "/ask 必须调用 gen(...) 走 quiet prompt，而不是返回空串",
            askBlock.take(600).contains("gen("),
        )
    }

    @Test
    fun `B C 组 8 条必须明确报不支持而不是静默回显`() {
        val notHandled = groupBC.filter { cmd ->
            // 必须与 slashUnsupported 出现在同一个 when 分支组里。
            // 简化判定：命令出现在 hostSrc，且 hostSrc 里有 slashUnsupported 调用。
            !Regex("""^\s*(?:"[a-z0-9-]+"\s*,\s*)*"${Regex.escape(cmd)}""", RegexOption.MULTILINE)
                .containsMatchIn(hostSrc)
        }
        assertTrue("B/C 组命令没有分支: $notHandled", notHandled.isEmpty())

        assertTrue(
            "必须存在 slashUnsupported(cmd) 调用（给可见错误文案，不静默成功）。",
            hostSrc.contains("slashUnsupported(cmd)"),
        )
        assertTrue(
            "slashUnsupported 必须真的返回「命令不可用」文案。",
            Regex("""fun slashUnsupported\(cmd: String\): String""").containsMatchIn(hostSrc) &&
                hostSrc.contains("命令不可用"),
        )
        // 明确禁止：这 8 条返回空串（那等于假装成功）。
        // 用逐行扫描而不是复杂正则 —— 上一版正则的转义把自己绕进去了。
        val hostLines = hostSrc.lines()
        for (cmd in groupBC) {
            // 注意：多条命令可能写在同一行（如 `"bgcol", "css-var" -> slashUnsupported(cmd)`），
            // 所以不能用 startsWith，要判断该行是 when 分支且**含**这个命令名。
            val idx = hostLines.indexOfFirst { line ->
                val t = line.trim()
                t.contains("\"$cmd\"") && t.contains("->") && !t.startsWith("//") && !t.startsWith("*")
            }
            assertTrue("$cmd 找不到分支行", idx >= 0)
            val branchLine = hostLines[idx].trim()
            val returnsEmpty = branchLine.endsWith("-> \"\"") || branchLine.endsWith("->\"\"")
            assertFalse(
                "$cmd 直接返回空串（静默假成功），必须走 slashUnsupported。实际: $branchLine",
                returnsEmpty,
            )
        }
    }

    @Test
    fun `运行时覆写必须真的接到生成链路`() {
        // 只定义不消费 = 又一次「静默假成功」。
        assertTrue(
            "GenerationLoop 必须读 stopStrings 覆写",
            generationSrc.contains("StSlashRuntimeOverrides.stopStrings"),
        )
        assertTrue(
            "GenerationLoop 必须读 assistantPrefill 覆写",
            generationSrc.contains("StSlashRuntimeOverrides.assistantPrefill"),
        )
        // 覆写为 null 时必须退回原行为（不能把预设值吃掉）
        assertTrue(
            "stopStrings 覆写为 null 时必须回落预设值（?: preset.customStoppingStrings）",
            Regex("""StSlashRuntimeOverrides\.stopStrings\s*\r?\n?\s*\?:\s*preset\.customStoppingStrings""")
                .containsMatchIn(generationSrc),
        )
    }

    @Test
    fun `overrides 对象必须是 null 默认值而不是空串`() {
        // null = 「未设置，用预设值」；空串 = 「显式清空」。
        // 两者语义不同，混了会让 /start-reply-with 一启动就吃掉预设 prefill。
        assertTrue(
            "assistantPrefill 默认必须是 null",
            Regex("""var assistantPrefill: String\? = null""").containsMatchIn(overridesSrc),
        )
        assertTrue(
            "stopStrings 默认必须是 null",
            Regex("""var stopStrings: List<String>\? = null""").containsMatchIn(overridesSrc),
        )
        assertTrue(
            "promptPostProcessing 默认必须是 null",
            Regex("""var promptPostProcessing: Boolean\? = null""").containsMatchIn(overridesSrc),
        )
    }
}
