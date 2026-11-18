package me.rerere.rikkahub.data.st.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `SlashCommandParser.commands` 契约守卫。
 *
 * ## 背景（真实插件失败）
 * ST 1.19.0 `scripts/slash-commands/SlashCommandParser.js` 里 commands 是
 * **静态表**（:44 `static commands = {}`），:95 写入命令名、:99 写入每个别名。
 *
 * 第三方插件常直接读它：
 *   `getContext().SlashCommandParser.commands['echo'].callback(...)`
 * SillyTavern-Roadway 每次打印提示都这么调。
 *
 * 我们此前是 `SlashCommandParser: { commands: {} }` —— 名字在、内容是空，
 * 插件第一次提示就 `Cannot read properties of undefined (reading 'commands')`
 * 或 `.callback` of undefined，整个扩展初始化中断。
 *
 * 这个测试防止它再被简化回空对象。
 */
class StSlashCommandParserContractTest {

    private val runtimeFile = File("src/main/assets/st-runtime/runtime.js")

    private fun runtime(): String {
        assertTrue("缺少 runtime.js", runtimeFile.isFile)
        return runtimeFile.readText()
    }

    @Test
    fun `SlashCommandParser is backed by the real command table`() {
        val src = runtime()
        assertTrue(
            "stCtx.SlashCommandParser 必须指向 slashCommandParser 实例",
            src.contains("SlashCommandParser: slashCommandParser"),
        )
        assertFalse(
            "不允许再退回空 commands 桩（插件会 .callback of undefined）",
            Regex("""SlashCommandParser:\s*\{\s*commands:\s*\{\s*\}""").containsMatchIn(src),
        )
    }

    @Test
    fun `command table exposes host builtins with callable callbacks`() {
        val src = runtime()
        assertTrue("必须有 slashCommandParser 定义", src.contains("const slashCommandParser = (() => {"))
        assertTrue("必须声明宿主内置命令表", src.contains("const HOST_BUILTINS = ["))

        // 插件最常直接调的几个（Roadway 用 echo；提示类用 toast/send/sys）
        for (name in listOf("'echo'", "'toast'", "'send'", "'sys'", "'pass'")) {
            assertTrue("HOST_BUILTINS 应包含 $name", src.contains(name))
        }
        assertTrue(
            "内置命令必须生成 callback 函数（否则插件调用即崩）",
            src.contains("callback: (namedArgs, unnamedArgs) => execViaHost"),
        )
    }

    @Test
    fun `extension registered commands merge into the same table`() {
        val src = runtime()
        assertTrue("必须从 window.__rikkaStSlashRegistry 同步扩展命令",
            src.contains("__rikkaStSlashRegistry"))
        assertTrue("addCommandObject 必须写入 commands",
            src.contains("addCommandObject:"))
        assertTrue("addCommand 必须写入 commands", src.contains("addCommand:"))
        assertTrue("别名也要进表（对齐 ST :99）", src.contains("Array.isArray(cmd.aliases)"))
    }

    @Test
    fun `builtin callback routes through the host slash executor`() {
        val src = runtime()
        assertTrue(
            "callback 必须走宿主 triggerSlashAsync（不能是空实现）",
            src.contains("stCtx.triggerSlashAsync"),
        )
        assertTrue("必须拼出 /name args 形式交给宿主", src.contains("'/' + name"))
    }
}
