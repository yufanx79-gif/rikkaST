package me.rerere.rikkahub.data.ai.transformers

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PlaceholderTransformer 性能守卫。
 *
 * ## 为什么需要这个测试
 *
 * 真机日志（2026-11-18）：
 * ```
 * [transformer] slow #4 PlaceholderTransformer 748ms msgs=8
 * ```
 *
 * 根因是 `replacePlaceholders()` 内部每次都调用
 * `parseActiveStPresetConfig(ctx.settings.tavernPresets, ...)`，
 * 它会 `parseToJsonElement` **整个预设 JSON**（Meowssiah 2.0.11 = 543 KB）。
 * 而 `replacePlaceholders` 是对「每条消息的每个 Text part」各调一次，
 * 8 条消息 = 8 次 543KB JSON 解析 ≈ 748ms。
 *
 * 修复方式：把与消息内容无关的前置计算提到 `transform()` 里做一次，
 * 通过 [MacroPassContext] 传进去。
 *
 * 这个测试用**源码结构断言**锁住该优化：
 * - `parseActiveStPresetConfig` 只能出现在 `buildMacroPassContext()` 里
 * - `replacePlaceholders()` 的参数必须是 `MacroPassContext`，不能是 `TransformerContext`
 *
 * 单测里没法构造 Android `Context`（TransformerContext 需要），
 * 所以用结构守卫而不是运行时计时。这类守卫在本仓库有先例
 * （见 KotlinNestedCommentGuardTest）。
 */
class PlaceholderTransformerPerfGuardTest {

    private fun sourceFile(): File {
        val candidates = listOf(
            File("src/main/java/me/rerere/rikkahub/data/ai/transformers/PlaceholderTransformer.kt"),
            File("app/src/main/java/me/rerere/rikkahub/data/ai/transformers/PlaceholderTransformer.kt"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("找不到 PlaceholderTransformer.kt，cwd=${File(".").absolutePath}")
    }

    /** 截取某个函数体（从 `fun <name>(` 开始到下一个同缩进的 `}`）。 */
    private fun functionBody(src: String, signature: String): String {
        val start = src.indexOf(signature)
        if (start < 0) error("找不到函数：$signature")
        // 找函数体结束：从 start 起匹配花括号
        val braceStart = src.indexOf('{', start)
        if (braceStart < 0) error("$signature 没有函数体")
        // 必须跳过字符串字面量：宏替换代码里有 "{{$key}}" 这类内容，
        // 直接数花括号会把它们当成代码块。
        var depth = 0
        var i = braceStart
        var inString = false
        var inRaw = false
        while (i < src.length) {
            if (!inString && !inRaw && src.startsWith("\"\"\"", i)) {
                inRaw = true; i += 3; continue
            }
            if (inRaw) {
                if (src.startsWith("\"\"\"", i)) { inRaw = false; i += 3; continue }
                i++; continue
            }
            val c = src[i]
            if (inString) {
                if (c == '\\') { i += 2; continue }
                if (c == '"') { inString = false }
                i++; continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(braceStart, i + 1)
                }
            }
            i++
        }
        error("$signature 花括号不闭合")
    }

    @Test
    fun `preset parsing happens once per transform not per message`() {
        val src = sourceFile().readText()

        // 1) parseActiveStPresetConfig 只能出现在 buildMacroPassContext 里
        val occurrences = Regex("""parseActiveStPresetConfig""").findAll(src).count()
        assertTrue(
            "parseActiveStPresetConfig 应只出现 1 次（在 buildMacroPassContext 内），实际 $occurrences 次。" +
                "如果又加回消息循环里，会重新引入 748ms 卡顿。",
            occurrences == 1,
        )

        val buildBody = functionBody(src, "private fun buildMacroPassContext(")
        assertTrue(
            "parseActiveStPresetConfig 必须位于 buildMacroPassContext() 内",
            buildBody.contains("parseActiveStPresetConfig"),
        )

        // 2) replacePlaceholders 不能接收 TransformerContext（否则说明它又在自己算预设）
        val replaceBody = functionBody(src, "private fun replacePlaceholders(")
        assertTrue(
            "replacePlaceholders() 不应再接收 TransformerContext —— " +
                "那意味着它又在每条消息上重算预设配置。",
            !replaceBody.contains("ctx: TransformerContext"),
        )
        // 参数在函数体外，要在源码里查 replacePlaceholders 的签名段
        val replaceSigStart = src.indexOf("private fun replacePlaceholders(")
        val replaceSigEnd = src.indexOf('{', replaceSigStart)
        val replaceSig = src.substring(replaceSigStart, replaceSigEnd)
        assertTrue(
            "replacePlaceholders() 应通过 MacroPassContext 复用前置结果，实际签名：$replaceSig",
            replaceSig.contains("pass: MacroPassContext"),
        )
        assertTrue(
            "replacePlaceholders() 内部不应再出现 parseActiveStPresetConfig",
            !replaceBody.contains("parseActiveStPresetConfig"),
        )
    }

    @Test
    fun `transform builds macro context exactly once`() {
        val src = sourceFile().readText()
        val transformBody = functionBody(src, "override suspend fun transform(")

        val builds = Regex("""buildMacroPassContext\(""").findAll(transformBody).count()
        assertTrue(
            "transform() 里应恰好调用 1 次 buildMacroPassContext()，实际 $builds 次",
            builds == 1,
        )

        // 调用必须在 messages.map 之前（否则还是在循环里）
        val buildIdx = transformBody.indexOf("buildMacroPassContext(")
        val mapIdx = transformBody.indexOf("messages.map")
        assertTrue(
            "buildMacroPassContext() 必须在 messages.map 之前调用",
            buildIdx in 0 until mapIdx,
        )
    }
}
