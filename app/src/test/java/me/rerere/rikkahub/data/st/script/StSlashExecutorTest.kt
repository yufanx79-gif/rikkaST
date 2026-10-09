package me.rerere.rikkahub.data.st.script

import me.rerere.ai.core.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * StSlashExecutor 单测（纯 JVM，无 Android 依赖）。
 *
 * 覆盖：管道循环 / 管道注入规则（未命名参数为空时接收管道值）/ `||` 禁用注入 /
 * `{{pipe}}` 宏替换 / 变量族 / 消息插入（send/sendas/sys）/ 未知命令兜底 / 无宿主降级。
 */
class StSlashExecutorTest {

    /** 录音宿主：记录调用，变量用内存 Map 模拟。 */
    private class FakeHost : StSlashHost {
        val vars = mutableMapOf<String, String>()
        val varCalls = mutableListOf<Triple<String, String, String>>()
        val inserted = mutableListOf<Triple<MessageRole, String, String?>>()
        var lastAt: Int? = null
        var triggerCount = 0
        var lastContinuePrompt: String? = null
        var lastImpersonatePrompt: String? = null
        var lastSysgenPrompt: String? = null
        var lastEchoText: String? = null
        var lastEchoTitle: String? = null
        var lastJsCode: String? = null
        var lastTavernSub: String? = null
        var lastPersona: Pair<String, String>? = null

        override val chatKey: String? = "chat-1"

        override fun varOp(cmd: String, name: String, value: String): String? {
            varCalls += Triple(cmd, name, value)
            return when (cmd) {
                "setvar" -> { vars[name] = value; "" }
                "getvar" -> vars[name] ?: ""
                "addvar" -> { vars[name] = "-add-"; "" }
                "incvar" -> { vars[name] = "-inc-"; "" }
                "decvar" -> { vars[name] = "-dec-"; "" }
                "flushvar" -> { vars.remove(name); "" }
                "listvar" -> vars.keys.joinToString(",")
                else -> null
            }
        }

        override fun insertMessage(role: MessageRole, text: String, name: String?, at: Int?): String? {
            inserted += Triple(role, text, name)
            lastAt = at
            return ""
        }

        override fun trigger(): String? {
            triggerCount++
            return ""
        }

        override fun continueGen(prompt: String): String? {
            lastContinuePrompt = prompt
            return ""
        }

        override fun impersonate(prompt: String): String? {
            lastImpersonatePrompt = prompt
            return ""
        }

        override fun sysgen(prompt: String, name: String?, at: Int?, trim: Boolean): String? {
            lastSysgenPrompt = prompt
            return ""
        }

        override fun gen(prompt: String, asRole: String, lock: Boolean, length: Int, name: String?, trim: Boolean): String? = ""

        override fun persona(name: String, mode: String): String? {
            lastPersona = name to mode
            return ""
        }

        override fun renameChar(name: String): String? = ""

        override fun js(code: String): String? {
            lastJsCode = code
            return ""
        }

        override fun tavern(sub: String): String? {
            lastTavernSub = sub
            return ""
        }

        override fun echo(text: String, title: String?): String? {
            lastEchoText = text
            lastEchoTitle = title
            return text
        }

        var lastHide: Triple<String, Boolean, String?>? = null
        var lastSwipe: Pair<String, Boolean>? = null

        override fun hideMessages(value: String, unhide: Boolean, nameFilter: String?): String? {
            lastHide = Triple(value, unhide, nameFilter)
            return ""
        }

        override fun swipe(direction: String, await: Boolean): String? {
            lastSwipe = direction to await
            return ""
        }

        // ---- ST Checkpoints ----

        var lastCheckpointCreate: Pair<Int?, String?>? = null
        var lastCheckpointGo: Int? = null
        var checkpointExitCount = 0
        var lastCheckpointGet: Int? = null
        var lastCheckpointListLinks: Boolean? = null
        var lastBranchCreate: Int? = null

        override fun checkpointCreate(mesId: Int?, name: String?): String? {
            lastCheckpointCreate = mesId to name
            return ""
        }

        override fun checkpointGo(mesId: Int?): String? {
            lastCheckpointGo = mesId
            return ""
        }

        override fun checkpointExit(): String? {
            checkpointExitCount++
            return ""
        }

        override fun checkpointParent(): String? = "Parent Chat"

        override fun checkpointGet(mesId: Int?): String? {
            lastCheckpointGet = mesId
            return ""
        }

        override fun checkpointList(links: Boolean): String? {
            lastCheckpointListLinks = links
            return "[]"
        }

        override fun branchCreate(mesId: Int?): String? {
            lastBranchCreate = mesId
            return ""
        }
    }

    // ---------- 管道核心 ----------

    @Test
    fun echoOutputsToPipe() {
        val host = FakeHost()
        assertEquals("hello", StSlashExecutor.execute("/echo hello", host))
        assertEquals("hello", host.lastEchoText)
    }

    @Test
    fun pipeInjectionIntoBlankArgs() {
        val host = FakeHost()
        assertEquals("hello", StSlashExecutor.execute("/echo hello | /echo", host))
        assertEquals("hello", host.lastEchoText)
    }

    @Test
    fun doublePipeDisablesInjection() {
        val host = FakeHost()
        // 第二段显式不接收管道；其未命名参数为空 → echo 收到空串
        assertEquals("", StSlashExecutor.execute("/echo hello || /echo", host))
        assertEquals("", host.lastEchoText)
    }

    @Test
    fun explicitArgsBeatPipe() {
        val host = FakeHost()
        assertEquals("world", StSlashExecutor.execute("/echo hello | /echo world", host))
        assertEquals("world", host.lastEchoText)
    }

    @Test
    fun passKeepsPipe() {
        val host = FakeHost()
        assertEquals("hello", StSlashExecutor.execute("/echo hello | /pass", host))
    }

    @Test
    fun pipeMacroReplacement() {
        val host = FakeHost()
        StSlashExecutor.execute("/echo world | /send {{pipe}}", host)
        assertEquals(1, host.inserted.size)
        assertEquals(MessageRole.USER, host.inserted[0].first)
        assertEquals("world", host.inserted[0].second)
    }

    @Test
    fun unknownCommandDoesNotBreakPipeline() {
        val host = FakeHost()
        val out = StSlashExecutor.execute("/bogus some arg | /echo tail", host)
        assertEquals("tail", out)
    }

    @Test
    fun unknownCommandReturnsErrorText() {
        val out = StSlashExecutor.execute("/bogus some arg", FakeHost())
        assertEquals("未知命令：/bogus", out)
    }

    @Test
    fun nullHostDegradesGracefully() {
        assertEquals("命令不可用：/trigger", StSlashExecutor.execute("/trigger", null))
        assertEquals("命令不可用：/send", StSlashExecutor.execute("/send x", null))
        // echo 无宿主持有原文本输出
        assertEquals("hi", StSlashExecutor.execute("/echo hi", null))
    }

    @Test
    fun plainTextSegmentsDropped() {
        val host = FakeHost()
        assertEquals("tail", StSlashExecutor.execute("loose text | /echo tail", host))
    }

    // ---------- 变量族 ----------

    @Test
    fun setvarPositionalKeyValue() {
        val host = FakeHost()
        StSlashExecutor.execute("/setvar mood happy", host)
        assertEquals(Triple("setvar", "mood", "happy"), host.varCalls.single())
        assertEquals("happy", host.vars["mood"])
    }

    @Test
    fun setvarAndGetvarAcrossPipe() {
        val host = FakeHost()
        val out = StSlashExecutor.execute("/setvar k v | /getvar k", host)
        assertEquals("v", out)
        assertEquals(2, host.varCalls.size)
        assertEquals("getvar", host.varCalls[1].first)
        assertEquals("k", host.varCalls[1].second)
    }

    @Test
    fun incvarPassesThrough() {
        val host = FakeHost()
        StSlashExecutor.execute("/incvar counter", host)
        assertEquals(Triple("incvar", "counter", ""), host.varCalls.single())
    }

    // ---------- 消息插入 ----------

    @Test
    fun sendInsertsUserMessageWithNamedArgs() {
        val host = FakeHost()
        StSlashExecutor.execute("/send at=0 name=Bob hi there", host)
        assertEquals(1, host.inserted.size)
        assertEquals(MessageRole.USER, host.inserted[0].first)
        assertEquals("hi there", host.inserted[0].second)
        assertEquals("Bob", host.inserted[0].third)
        assertEquals(0, host.lastAt)
    }

    @Test
    fun sendasInsertsAssistantMessage() {
        val host = FakeHost()
        StSlashExecutor.execute("/sendas hello", host)
        assertEquals(MessageRole.ASSISTANT, host.inserted.single().first)
        assertEquals("hello", host.inserted.single().second)
    }

    @Test
    fun sysInsertsSystemMessage() {
        val host = FakeHost()
        StSlashExecutor.execute("/sys note", host)
        assertEquals(MessageRole.SYSTEM, host.inserted.single().first)
    }

    @Test
    fun sendBlankTextReportsError() {
        val host = FakeHost()
        val out = StSlashExecutor.execute("/send", host)
        assertEquals("命令缺少文本：/send", out)
        assertTrue(host.inserted.isEmpty())
    }

    @Test
    fun sendQuotedTextUnwrapped() {
        val host = FakeHost()
        StSlashExecutor.execute("/send \"hello world\"", host)
        assertEquals("hello world", host.inserted.single().second)
    }

    // ---------- 生成类 ----------

    @Test
    fun triggerCallsHost() {
        val host = FakeHost()
        StSlashExecutor.execute("/trigger", host)
        assertEquals(1, host.triggerCount)
    }

    @Test
    fun continueReceivesPipe() {
        val host = FakeHost()
        StSlashExecutor.execute("/echo prev | /continue", host)
        assertEquals("prev", host.lastContinuePrompt)
    }

    @Test
    fun impersonateReceivesExplicitText() {
        val host = FakeHost()
        StSlashExecutor.execute("/impersonate \"as the villain\"", host)
        assertEquals("as the villain", host.lastImpersonatePrompt)
    }

    @Test
    fun sysgenReceivesPrompt() {
        val host = FakeHost()
        StSlashExecutor.execute("/sysgen it starts to rain", host)
        assertEquals("it starts to rain", host.lastSysgenPrompt)
    }

    // ---------- 其他 ----------

    @Test
    fun personaModeParsed() {
        val host = FakeHost()
        StSlashExecutor.execute("/persona-set Alice mode=temp", host)
        assertEquals("Alice" to "temp", host.lastPersona)
    }

    @Test
    fun personaDefaultModeAll() {
        val host = FakeHost()
        StSlashExecutor.execute("/persona Alice", host)
        assertEquals("Alice" to "all", host.lastPersona)
    }

    @Test
    fun jsPassesCode() {
        val host = FakeHost()
        StSlashExecutor.execute("/js return 1 + 1;", host)
        assertEquals("return 1 + 1;", host.lastJsCode)
    }

    @Test
    fun tavernSubLowercased() {
        val host = FakeHost()
        StSlashExecutor.execute("/tavern RELOAD", host)
        assertEquals("reload", host.lastTavernSub)
    }

    @Test
    fun echoTitleNamedArg() {
        val host = FakeHost()
        StSlashExecutor.execute("/echo hello title=Greeting", host)
        assertEquals("hello", host.lastEchoText)
        assertEquals("Greeting", host.lastEchoTitle)
    }

    // ---------- /hide /unhide /swipe ----------

    @Test
    fun hideDefaultsToEmptyValue() {
        val host = FakeHost()
        assertEquals("", StSlashExecutor.execute("/hide", host))
        assertEquals(Triple("", false, null), host.lastHide)
    }

    @Test
    fun hideParsesRange() {
        val host = FakeHost()
        StSlashExecutor.execute("/hide 0-2", host)
        assertEquals(Triple("0-2", false, null), host.lastHide)
    }

    @Test
    fun hideParsesNameFilter() {
        val host = FakeHost()
        StSlashExecutor.execute("/hide 1-3 name=Alice", host)
        assertEquals(Triple("1-3", false, "Alice"), host.lastHide)
    }

    @Test
    fun unhideSetsFlag() {
        val host = FakeHost()
        StSlashExecutor.execute("/unhide 2", host)
        assertEquals(Triple("2", true, null), host.lastHide)
    }

    @Test
    fun hideReceivesPipeWhenBlank() {
        val host = FakeHost()
        StSlashExecutor.execute("/echo 5 | /hide", host)
        assertEquals(Triple("5", false, null), host.lastHide)
    }

    @Test
    fun hideUnavailableWithoutHost() {
        assertEquals("命令不可用：/hide", StSlashExecutor.execute("/hide", null))
    }

    @Test
    fun swipeDefaultsToRight() {
        val host = FakeHost()
        StSlashExecutor.execute("/swipe", host)
        assertEquals("right" to false, host.lastSwipe)
    }

    @Test
    fun swipeParsesDirectionAndAwait() {
        val host = FakeHost()
        StSlashExecutor.execute("/swipe direction=left await=true", host)
        assertEquals("left" to true, host.lastSwipe)
    }

    @Test
    fun swipeDirectionCaseInsensitive() {
        val host = FakeHost()
        StSlashExecutor.execute("/swipe direction=LEFT", host)
        assertEquals("left" to false, host.lastSwipe)
    }

    @Test
    fun swipeUnavailableWithoutHost() {
        assertEquals("命令不可用：/swipe", StSlashExecutor.execute("/swipe", null))
    }

    // ---------- ST Checkpoints ----------

    @Test
    fun checkpointCreateParsesMesIdAndName() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-create mesId=2 My CP", host)
        assertEquals(2 to "My CP", host.lastCheckpointCreate)
    }

    @Test
    fun checkpointCreateWithoutArgs() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-create", host)
        assertEquals(null to "", host.lastCheckpointCreate)
    }

    @Test
    fun checkpointGoParsesMesId() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-go mesId=4", host)
        assertEquals(4, host.lastCheckpointGo)
    }

    @Test
    fun checkpointExitInvokesHost() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-exit", host)
        assertEquals(1, host.checkpointExitCount)
    }

    @Test
    fun checkpointParentReturnsTitle() {
        val host = FakeHost()
        assertEquals("Parent Chat", StSlashExecutor.execute("/checkpoint-parent", host))
    }

    @Test
    fun checkpointGetParsesMesId() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-get mesId=1", host)
        assertEquals(1, host.lastCheckpointGet)
    }

    @Test
    fun checkpointListDefaultsToIndexes() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-list", host)
        assertEquals(false, host.lastCheckpointListLinks)
    }

    @Test
    fun checkpointListLinksFlag() {
        val host = FakeHost()
        StSlashExecutor.execute("/checkpoint-list links=true", host)
        assertEquals(true, host.lastCheckpointListLinks)
    }

    @Test
    fun branchCreateParsesMesId() {
        val host = FakeHost()
        StSlashExecutor.execute("/branch-create mesId=0", host)
        assertEquals(0, host.lastBranchCreate)
    }

    @Test
    fun checkpointUnavailableWithoutHost() {
        assertEquals("命令不可用：/checkpoint-exit", StSlashExecutor.execute("/checkpoint-exit", null))
        assertEquals("命令不可用：/checkpoint-list", StSlashExecutor.execute("/checkpoint-list", null))
    }
}