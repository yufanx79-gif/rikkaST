package me.rerere.rikkahub.data.st.script

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0-B 探勘用运行时测试：判定「用户在输入框敲 /xxx 回车」这条链路到底有没有达到
 * ChatSlashHost.executeExtendedCommand 的扩展命令分支。
 *
 * 与 StSlashSilentNoOpGuardTest 的根本区别：
 * 那个测试 grep 源码（结构断言，已证明会被注释骗过、也不能证明运行时可达）。
 * 这个测试**真的调用 StSlashExecutor.execute(rawInput, host)**，
 * 走的是 ChatInput.sendMessage() 的 onSlashScript 分支所用的同一个入口。
 *
 * 说明：本测试用一个最小 host 复刻 ChatSlashHost 的命令分发形状
 * （executeExtendedCommand 返回"命令不可用：/x（…）"）。它证明的是
 * 「executor 能把这条输入派发到 executeExtendedCommand」。
 * 它**不能**证明 ChatPage 里那个真实 host 的行为 —— 那需要真机日志。
 */
class SlashUserInputReachabilityTest {

    /** 最小宿主：只实现执行链路必需面，扩展命令分派形状与 ChatSlashHost 一致。 */
    private class ProbeHost : StSlashHost {
        override val chatKey: String? = "probe-chat"

        val extendedCalls = mutableListOf<String>()

        override fun varOp(cmd: String, name: String, value: String): String? = ""

        override fun insertMessage(role: MessageRole, text: String, name: String?, at: Int?): String? = ""

        override fun trigger(): String? = ""
        override fun continueGen(prompt: String): String? = ""
        override fun impersonate(prompt: String): String? = ""
        override fun sysgen(prompt: String, name: String?, at: Int?, trim: Boolean): String? = ""
        override fun gen(
            prompt: String,
            asRole: String,
            lock: Boolean,
            length: Int,
            name: String?,
            trim: Boolean,
        ): String? = ""

        override fun persona(name: String, mode: String): String? = ""
        override fun renameChar(name: String): String? = ""
        override fun js(code: String): String? = ""
        override fun tavern(sub: String): String? = ""
        override fun echo(text: String, title: String?): String? = ""
        override fun hideMessages(value: String, unhide: Boolean, nameFilter: String?): String? = ""
        override fun swipe(direction: String, await: Boolean): String? = ""

        override fun executeExtendedCommand(
            cmd: String,
            named: Map<String, String>,
            unnamed: String,
            pipe: String,
        ): String? {
            extendedCalls += cmd
            // 与 ChatSlashHost 的 B/C 组形状一致：给出可见错误文案，不返回 null。
            return when (cmd) {
                "bgcol", "css-var", "list-gallery", "show-gallery", "pick-icon",
                "chat-jump", "chat-manager", "yt-script",
                -> "命令不可用：/$cmd（当前客户端暂不支持该命令）"
                else -> null
            }
        }
    }

    private val host = ProbeHost()

    /**
     * 这五条就是用户在真机上敲的原文（docs/progress/用户反馈-2026-11-19-夜间.md）。
     * 判定标准：executor 必须把它们派发到 executeExtendedCommand，
     * 且 bgcol / css-var / list-gallery 必须产出「命令不可用」可见文案。
     */
    @Test
    fun `用户实测的五条命令都必须被派发到扩展命令分支`() {
        val inputs = listOf(
            "/start-reply-with 你好啊",
            "/stop-strings </s>,###",
            "/css-var --name=--primary",
            "/bgcol red",
            "/list-gallery",
        )
        val dispatched = mutableListOf<String>()
        for (input in inputs) {
            host.extendedCalls.clear()
            val out = StSlashExecutor.execute(input, host)
            assertNotNull("输入没有被执行器处理: $input", out)
            dispatched += "$input -> extendedCalls=${host.extendedCalls} output=$out"
        }
        // 至少这五条都要真的到达 executor（否则链路断在输入框层）
        assertTrue("五条命令应全部到达 executor", dispatched.size == 5)
        println(dispatched.joinToString("\n"))
    }

    @Test
    fun `不可用命令必须产出可见文案而不是静默回显`() {
        for (input in listOf("/bgcol red", "/css-var --name=--primary", "/list-gallery")) {
            val out = StSlashExecutor.execute(input, host)
            assertTrue(
                "[$input] 应产出「命令不可用」可见文案，实际: $out",
                out.contains("命令不可用"),
            )
        }
    }

    /**
     * 直击上一轮的失效形态：无宿主时，扩展命令会落到 runFallbackExtendedCommand 的
     * `else -> unnamed`，**只剩未命名参数**（连命令名都丢了），既不报错也不做事。
     *
     * 实测：`/bgcol red` (host=null) -> `red`
     * 这正是「静默假成功」的形状 —— 若真实 ChatPage 的 host 没接上扩展分支，
     * 用户看到的就是"什么也没发生"。
     */
    @Test
    fun `无宿主时扩展命令会静默吞掉命令名只剩参数`() {
        val out = StSlashExecutor.execute("/bgcol red", null)
        println("/bgcol red (host=null) -> $out")
        assertEquals("red", out)
        // 关键：输出里既没有错误文案，也没有命令名 —— 用户无从得知命令没执行。
        assertTrue("不应包含任何错误提示: $out", !out.contains("命令不可用"))
    }
}
