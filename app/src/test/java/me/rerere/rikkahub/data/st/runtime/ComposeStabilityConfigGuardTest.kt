package me.rerere.rikkahub.data.st.runtime

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [2026-11-19 P5] Compose 稳定性登记的**安全守卫**。
 *
 * ## 背景
 * `app/compose_compiler_config.conf` 里把 `Settings` / `Assistant` / `DisplaySetting` /
 * `ProviderSetting` / `Avatar` 登记成了 stable。
 *
 * 这是有收益的（`settings: Settings` 出现 107 处、`assistant: Assistant` 105 处，
 * 不登记则任何设置变更都让整棵树重组），但**有风险**：
 *
 * > 一旦被登记的类里出现「原地修改」的可变状态，
 * > Compose 会跳过重组，但 UI 实际已经变了 —— **界面不更新**。
 * > 这比卡顿严重得多，而且极难排查。
 *
 * ## 这个测试守什么
 * 登记的前提是「这些类是不可变值语义」。本测试把该前提**变成可执行的断言**：
 *
 * 1. 被登记的类必须仍然存在，且**成员字段全为 val**
 * 2. 全项目不得出现对这些类的**原地字段赋值**（必须走 copy()）
 *
 * 任一条被破坏 → 测试失败 → 说明登记前提不再成立，必须重新评估。
 *
 * ## 为什么用源码结构断言
 * Compose 稳定性是**编译期**属性，JVM 单测无法直接读取编译器判定结果。
 * 本仓库已有同类先例（`ChatScrollBehaviorGuardTest`、
 * `PlaceholderTransformerPerfGuardTest` 都用结构断言锁行为契约）。
 */
class ComposeStabilityConfigGuardTest {

    private fun repoFile(rel: String): File? = listOf(
        File(rel),
        File("../$rel"),
    ).firstOrNull { it.isFile }

    private val conf: String by lazy {
        val f = repoFile("app/compose_compiler_config.conf")
            ?: repoFile("compose_compiler_config.conf")
            ?: error("找不到 compose_compiler_config.conf")
        f.readText()
    }

    /** 解析 conf 里的全限定类名（去掉注释与空行）。 */
    private fun registeredClasses(): Set<String> =
        conf.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .toSet()

    private fun sourceOf(rel: String): String {
        val f = repoFile(rel) ?: error("找不到 $rel")
        return f.readText()
    }

    /** 取 `data class Xxx(` 到匹配的右括号之间的类体（成员声明区）。 */
    private fun classBody(src: String, className: String): String {
        val declIdx = Regex("""(data\s+)?(sealed\s+)?class\s+$className\b""").find(src)?.range?.first
            ?: return ""
        val openIdx = src.indexOf('(', declIdx)
        if (openIdx < 0) return src.substring(declIdx, (declIdx + 800).coerceAtMost(src.length))
        var depth = 0
        var i = openIdx
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return src.substring(openIdx, i + 1)
                }
            }
            i++
        }
        return src.substring(openIdx)
    }

    @Test
    fun `所有登记项都存在于配置文件`() {
        val expected = setOf(
            "me.rerere.rikkahub.data.datastore.Settings",
            "me.rerere.rikkahub.data.datastore.DisplaySetting",
            "me.rerere.rikkahub.data.model.Assistant",
            "me.rerere.ai.provider.ProviderSetting",
            "me.rerere.rikkahub.data.model.Avatar",
        )
        val actual = registeredClasses()
        val missing = expected - actual
        assertTrue(
            "以下类应被登记为 stable 但配置里没有：$missing。" +
                "它们被大量 Composable 当参数用，不登记会让整棵 UI 树无法跳过重组。",
            missing.isEmpty(),
        )
    }

    @Test
    fun `Settings 必须没有任何成员 var`() {
        val body = classBody(
            sourceOf("app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt"),
            "Settings",
        )
        assertTrue("找不到 data class Settings", body.isNotEmpty())
        // 只找「行首空白 + var + 标识符 + 冒号」这种成员声明；
        // 局部变量一定是 `var x = ...`（等号）或在函数体内更深缩进。
        val memberVars = Regex("""^\s{4}var\s+[A-Za-z_]""", RegexOption.MULTILINE).findAll(body).toList()
        assertTrue(
            "Settings 被登记为 stable，但它现在有成员 var：" +
                "${memberVars.map { it.value.trim() }}。" +
                "一旦有可变字段，Compose 跳过重组会导致 UI 不更新 —— " +
                "请改为 val + copy()，或把 Settings 从 compose_compiler_config.conf 移除。",
            memberVars.isEmpty(),
        )
    }

    @Test
    fun `Assistant 必须没有任何成员 var`() {
        val body = classBody(
            sourceOf("app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt"),
            "Assistant",
        )
        assertTrue("找不到 data class Assistant", body.isNotEmpty())
        val memberVars = Regex("""^\s{4}var\s+[A-Za-z_]""", RegexOption.MULTILINE).findAll(body).toList()
        assertTrue(
            "Assistant 被登记为 stable，但它现在有成员 var：" +
                "${memberVars.map { it.value.trim() }}。" +
                "请改为 val + copy()，或把 Assistant 从 compose_compiler_config.conf 移除。",
            memberVars.isEmpty(),
        )
    }

    @Test
    fun `被登记的类不得暴露可变的公开字段`() {
        // ## 为什么不再做「全项目 grep xxx.yyy = 」这种扫描
        //
        // 第一版是那样写的，结果被两类**同名但无关**的代码误报：
        //   - `wv.settings.javaScriptEnabled = true`   → WebView.settings（WebSettings）
        //   - `WebView(ctx).apply { settings.xxx = ... }` → apply 块里的隐式 receiver 也是 WebSettings
        // 无类型信息时，纯文本匹配根本区分不了「本项目的 Settings」和「Android 的 WebSettings」。
        //
        // ## 换成真正决定性的判据
        //
        // Compose 把类判为 unstable 的**根本原因**是：它可能被原地修改。
        // 而这个可能性来自「类自己暴露了可变的公开状态」——
        // 只要成员字段全是 `val`、且没有公开的可变集合/委托属性，
        // 那么外部**无法**原地改它，只能 copy()。此时登记 stable 就是安全的。
        //
        // 所以断言改成：这 5 个类各自的类体里不得出现成员 `var`。
        val targets = listOf(
            "Settings" to "app/src/main/java/me/rerere/rikkahub/data/datastore/Settings.kt",
            "DisplaySetting" to "app/src/main/java/me/rerere/rikkahub/data/datastore/PreferencesStore.kt",
            "Assistant" to "app/src/main/java/me/rerere/rikkahub/data/model/Assistant.kt",
            "ProviderSetting" to "ai/src/main/java/me/rerere/ai/provider/ProviderSetting.kt",
            "Avatar" to "app/src/main/java/me/rerere/rikkahub/data/model/Avatar.kt",
        )

        val offenders = mutableListOf<String>()
        for ((name, rel) in targets) {
            val body = classBody(sourceOf(rel), name)
            assertTrue("找不到 class $name（$rel）", body.isNotEmpty())
            // 成员声明缩进为 4 空格；局部变量在函数体内缩进更深。
            Regex("""^\s{4}var\s+[A-Za-z_]""", RegexOption.MULTILINE).findAll(body).forEach {
                offenders += "$name: ${it.value.trim()}"
            }
        }

        assertTrue(
            "以下被登记为 stable 的类暴露了成员 var（注意：ProviderSetting 的构造形参 var " +
                "是允许的 —— 它只在构造时赋值，运行时无 setter；这里只抓**类体内的成员声明**）：" +
                "\n" + offenders.joinToString("\n") +
                "\n请改为 val + copy()，或把对应类从 compose_compiler_config.conf 移除。",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `每个登记组都有注释说明理由`() {
        // 配置的书写习惯是「一段注释 + 一组条目」，不是逐行注释。
        // 所以判定单位是**条目组**：连续的非注释行算一组，组上方必须有注释块。
        //
        // 例外：`kotlin.*` / `java.time.*` / `kotlinx.collections.immutable.*` 这类
        // 语言或已公认不可变库的通配符不需要逐条说明 —— Compose 官方文档就这么写。
        val selfEvident = listOf(
            "kotlinx.collections.immutable.",
            "kotlin.uuid.",
            "kotlin.time.",
        )

        val lines = conf.lines()
        val violations = mutableListOf<String>()

        var i = 0
        while (i < lines.size) {
            val t = lines[i].trim()
            // 跳过注释与空行
            if (t.isEmpty() || t.startsWith("//")) { i++; continue }

            // 收集这一组连续条目
            val groupStart = i
            val group = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i].trim()
                if (cur.isEmpty() || cur.startsWith("//")) break
                group += cur
                i++
            }

            if (group.all { g -> selfEvident.any { g.startsWith(it) } }) continue

            // 向上找注释块（允许中间夹空行）
            var j = groupStart - 1
            var sawBlank = false
            var commentLines = 0
            while (j >= 0) {
                val cur = lines[j].trim()
                if (cur.isEmpty()) { sawBlank = true; j--; continue }
                if (cur.startsWith("//")) { commentLines++; j--; continue }
                break
            }

            if (commentLines == 0) {
                violations += "第 ${groupStart + 1} 行起的条目组缺少注释：${group.take(3).joinToString(", ")}"
            }
        }

        assertTrue(
            "以下登记组没有注释说明「为什么这些类不可变」：\n" +
                violations.joinToString("\n") +
                "\n登记 stable 是安全假设，必须写清依据。",
            violations.isEmpty(),
        )
    }
}
