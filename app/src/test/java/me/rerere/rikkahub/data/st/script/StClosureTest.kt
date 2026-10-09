package me.rerere.rikkahub.data.st.script

import me.rerere.ai.core.MessageRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [W4] STscript 控制流单测：闭包字面量 / `/run`（`/call` `/exec`）/ `/abort` / `/delay` / `/switch`。
 *
 * 金标准：SillyTavern 1.18.0
 * - `SlashCommandParser.js:743-820` 闭包解析（头部命名参数声明 + `{: ... :}`）；
 * - `slash-commands.js:2575-2601` `/run` 定义、`runCallback`（:4214-4259）；
 * - `slash-commands.js:2363-2380` `/abort`、`:2477-2495` `/delay(wait/sleep)`；
 * - `QuickReply.js:1884-1886` / `SlashCommandHandler.js:654,675`：`{{arg::key}}` 来自
 *   scope macro（`arg::*` 通配兜底为空串）。
 *
 * 说明：官方 1.18 **没有** `/switch` 命令（已用源码核实）；`/switch` 是 rikkaST 本批的
 * 闭包分派扩展（语义见 StSlashExecutor.runSwitchCommand 与 DIVERGENCE.md）。
 */
class StClosureTest {

    /** 录音宿主：只需要 echo / insertMessage / varOp 三个通道。 */
    private class FakeHost(override val chatKey: String? = "chat-closure") : StSlashHost {
        val echoes = mutableListOf<String>()
        val vars = mutableMapOf<String, String>()
        val inserted = mutableListOf<Pair<MessageRole, String>>()

        override fun varOp(cmd: String, name: String, value: String): String? = when (cmd) {
            "setvar" -> {
                vars[name] = value
                ""
            }
            "getvar" -> vars[name] ?: ""
            "flushvar" -> {
                vars.remove(name)
                ""
            }
            else -> ""
        }

        override fun insertMessage(role: MessageRole, text: String, name: String?, at: Int?): String? {
            inserted += role to text
            return ""
        }

        override fun trigger(): String? = ""
        override fun continueGen(prompt: String): String? = ""
        override fun impersonate(prompt: String): String? = ""
        override fun sysgen(prompt: String, name: String?, at: Int?, trim: Boolean): String? = ""
        override fun gen(prompt: String, asRole: String, lock: Boolean, length: Int, name: String?, trim: Boolean): String? = ""
        override fun persona(name: String, mode: String): String? = ""
        override fun renameChar(name: String): String? = ""
        override fun js(code: String): String? = ""
        override fun tavern(sub: String): String? = ""
        override fun echo(text: String, title: String?): String? {
            echoes += text
            return text
        }

        override fun hideMessages(value: String, unhide: Boolean, nameFilter: String?): String? = ""

        override fun swipe(direction: String, await: Boolean): String? = ""
    }

    @Before
    fun setUp() = StClosureStore.clearAll()

    @After
    fun tearDown() = StClosureStore.clearAll()

    // ==================== 语法层 ====================

    @Test
    fun `parse closure literal with leading named arguments`() {
        val closure = StClosureSyntax.parse("{: name=rikka age=\"18 years\" /echo {{arg::name}} :}")
        assertTrue(closure != null)
        assertEquals(
            listOf(StClosureArg("name", "rikka"), StClosureArg("age", "18 years")),
            closure!!.argumentList,
        )
        assertEquals("/echo {{arg::name}}", closure.body)
    }

    @Test
    fun `parse rejects non closure text`() {
        assertEquals(null, StClosureSyntax.parse("/echo hi"))
        assertEquals(null, StClosureSyntax.parse("{: unclosed"))
    }

    @Test
    fun `take first closure keeps surrounding args`() {
        val taken = StClosureSyntax.takeFirstClosure("key=value {: /echo hi :} other")
        assertTrue(taken != null)
        assertEquals("/echo hi", taken!!.first.body)
        assertEquals("key=value  other", taken.second)
    }

    @Test
    fun `tokenize args keeps closure as single token`() {
        val tokens = StClosureSyntax.tokenizeArgs("b a={: /echo A :} default={: /echo D :}")
        assertEquals(listOf("b", "a={: /echo A :}", "default={: /echo D :}"), tokens)
        // 引号内空白不切分
        assertEquals(listOf("\"a b\"", "c"), StClosureSyntax.tokenizeArgs("\"a b\" c"))
    }

    @Test
    fun `substitute args falls back to wildcard then empty`() {
        assertEquals("x=1 y=", StClosureSyntax.substituteArgs("x={{arg::x}} y={{arg::y}}", mapOf("x" to "1")))
        assertEquals("W", StClosureSyntax.substituteArgs("{{arg::anything}}", mapOf("*" to "W")))
        assertEquals("1", StClosureSyntax.substituteArgs("{{ARG::X}}", mapOf("x" to "1")))
        // 大小写不敏感
        assertEquals("1", StClosureSyntax.substituteArgs("{{arg::X}}", mapOf("x" to "1")))
    }

    // ==================== /run：单行闭包 ====================

    @Test
    fun `run executes single line closure literal`() {
        val host = FakeHost()
        assertEquals("hello", StSlashExecutor.execute("/run {: /echo hello :}", host))
        assertEquals(listOf("hello"), host.echoes)
    }

    @Test
    fun `run supports call and exec aliases`() {
        val host = FakeHost()
        assertEquals("a", StSlashExecutor.execute("/call {: /echo a :}", host))
        assertEquals("b", StSlashExecutor.execute("/exec {: /echo b :}", host))
    }

    @Test
    fun `closure body supports pipes`() {
        val host = FakeHost()
        assertEquals("B", StSlashExecutor.execute("/run {: /echo A | /echo B :}", host))
    }

    @Test
    fun `closure literal can appear as an argument value`() {
        val host = FakeHost()
        // 官方 /run 的 unnamed arg 类型包含 CLOSURE：直接给字面量 + 命名参数
        assertEquals("Hi rikka", StSlashExecutor.execute("/run {: /echo Hi {{arg::who}} :} who=rikka", host))
    }

    // ==================== /run：存进变量的闭包 ====================

    @Test
    fun `setvar stores closure and run executes it in same script`() {
        val host = FakeHost()
        assertEquals("HELLO", StSlashExecutor.execute("/setvar fn={: /echo HELLO :} | /run fn", host))
    }

    @Test
    fun `stored closure survives across script invocations`() {
        val host = FakeHost()
        StSlashExecutor.execute("/setvar fn={: /echo {{arg::text}} :}", host)
        // closure 表按 chatKey 作用域持久（官方 scope 亦为会话内存）
        assertEquals("second call", StSlashExecutor.execute("/run fn text=\"second call\"", host))
        // 另一个对话作用域互不可见
        val other = FakeHost(chatKey = "chat-other")
        assertTrue(StSlashExecutor.execute("/run fn", other).contains("not callable"))
    }

    @Test
    fun `declared named args provide defaults overridden by run args`() {
        val host = FakeHost()
        assertEquals("default", StSlashExecutor.execute("/run {: name=default /echo {{arg::name}} :}", host))
        assertEquals("override", StSlashExecutor.execute("/run {: name=default /echo {{arg::name}} :} name=override", host))
    }

    @Test
    fun `missing named arg expands to empty string`() {
        val host = FakeHost()
        assertEquals("[]", StSlashExecutor.execute("/run {: /echo [{{arg::missing}}] :}", host))
        assertEquals("W", StSlashExecutor.execute("/run {: /echo {{arg::*}} :} *=W", host))
    }

    @Test
    fun `run on unknown name returns not callable`() {
        val host = FakeHost()
        assertEquals("\"nope\" is not callable.", StSlashExecutor.execute("/run nope", host))
    }

    // ==================== 递归 / 作用域隔离 ====================

    @Test
    fun `recursive closure is depth limited without crash`() {
        val host = FakeHost()
        StSlashExecutor.execute("/setvar self={: /echo step | /run self :}", host)
        val result = StSlashExecutor.execute("/run self", host)
        assertTrue("递归必须被深度上限截断：$result", result.contains("闭包递归深度超过上限"))
        // 每次真正执行都会 echo step；深度上限 32 → 至少执行若干次且不栈溢出
        assertTrue(host.echoes.size in 2..StSlashExecutor.MAX_CLOSURE_DEPTH + 2)
    }

    @Test
    fun `mutual recursion is depth limited`() {
        val host = FakeHost()
        StSlashExecutor.execute("/setvar a={: /run b :} | /setvar b={: /run a :}", host)
        val result = StSlashExecutor.execute("/run a", host)
        assertTrue(result.contains("闭包递归深度超过上限"))
    }

    @Test
    fun `closure args do not leak into other closures`() {
        val host = FakeHost()
        // 第一个闭包提供 x=inner；第二个闭包是独立闭包，`{{arg::x}}` 必须为空
        // （官方：根作用域没有 arg::x，通配 arg::* 默认为空串）
        val result = StSlashExecutor.execute(
            "/run {: x=inner /echo {{arg::x}} :} | /run {: /echo [{{arg::x}}] :}",
            host,
        )
        assertEquals("[]", result)
        assertEquals(listOf("inner", "[]"), host.echoes)
    }

    @Test
    fun `repeated runs start from fresh args`() {
        val host = FakeHost()
        StSlashExecutor.execute("/setvar fn={: /echo [{{arg::v}}] :}", host)
        assertEquals("[]", StSlashExecutor.execute("/run fn", host))
        assertEquals("[1]", StSlashExecutor.execute("/run fn v=1", host))
        // 第二次不带参数时不继承上一次的 v（官方每次 execute 用 scope 副本）
        assertEquals("[]", StSlashExecutor.execute("/run fn", host))
    }

    // ==================== /abort ====================

    @Test
    fun `abort stops the batch`() {
        val host = FakeHost()
        val result = StSlashExecutor.execute("/echo before | /abort because | /echo after", host)
        assertEquals("", result)
        assertEquals(listOf("before"), host.echoes)
        assertFalse(host.echoes.contains("after"))
    }

    @Test
    fun `abort inside closure stops the whole batch`() {
        val host = FakeHost()
        val result = StSlashExecutor.execute("/echo outer | /run {: /echo inner | /abort :} | /echo tail", host)
        assertEquals("", result)
        assertEquals(listOf("outer", "inner"), host.echoes)
    }

    // ==================== /delay ====================

    @Test
    fun `delay does not break the pipeline`() {
        val host = FakeHost()
        val started = System.currentTimeMillis()
        assertEquals("done", StSlashExecutor.execute("/delay 20 | /echo done", host))
        // JVM 测试线程不是 main → 真实等待（≥10ms 留出调度余量）
        assertTrue(System.currentTimeMillis() - started >= 10)
    }

    @Test
    fun `delay caps absurd amounts`() {
        assertEquals(30_000L, StSlashExecutor.MAX_DELAY_MS)
        val host = FakeHost()
        // 负数/非法值不等待也不报错
        assertEquals("ok", StSlashExecutor.execute("/delay -5 | /echo ok", host))
        assertEquals("ok2", StSlashExecutor.execute("/delay abc | /echo ok2", host))
    }

    // ==================== /switch（本地扩展） ====================

    @Test
    fun `switch dispatches to matching closure branch`() {
        val host = FakeHost()
        assertEquals(
            "B",
            StSlashExecutor.execute("/switch b a={: /echo A :} b={: /echo B :} default={: /echo D :}", host),
        )
    }

    @Test
    fun `switch falls back to default or empty`() {
        val host = FakeHost()
        assertEquals(
            "D",
            StSlashExecutor.execute("/switch z a={: /echo A :} default={: /echo D :}", host),
        )
        assertEquals(
            "",
            StSlashExecutor.execute("/switch z a={: /echo A :}", host),
        )
    }

    @Test
    fun `switch branch can be plain text`() {
        val host = FakeHost()
        assertEquals("plain", StSlashExecutor.execute("/switch b a={: /echo A :} b=plain", host))
    }

    @Test
    fun `switch value can come from pipe`() {
        val host = FakeHost()
        // {{pipe}} 在命令执行时被替换成上一条命令的输出（官方管道语义）
        assertEquals("A", StSlashExecutor.execute("/pass a | /switch {{pipe}} a={: /echo A :}", host))
    }
}
