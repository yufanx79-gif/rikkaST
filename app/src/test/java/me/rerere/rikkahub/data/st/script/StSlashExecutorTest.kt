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
    private open class FakeHost : StSlashHost {
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

        var lastGenPrompt: String? = null
        var lastGenRawPrompt: Pair<String, String>? = null
        var authorNoteState: String = ""
        var authorNoteDepthState: Int = 4
        var authorNoteFreqState: Int = 1
        var authorNotePosState: String = "in_chat"
        var authorNoteRoleState: String = "system"
        var lastInputText: String = ""
        val jsSlashRegistry = mutableMapOf<String, (Map<String, String>, String) -> String>()

        override fun gen(prompt: String, asRole: String, lock: Boolean, length: Int, name: String?, trim: Boolean): String? {
            lastGenPrompt = prompt
            return if (prompt == "请输出OK") "OK" else "GEN:$prompt"
        }

        override fun genRaw(prompt: String, systemPrompt: String, length: Int, trim: Boolean): String? {
            lastGenRawPrompt = systemPrompt to prompt
            return "RAW:$systemPrompt|$prompt"
        }

        override fun authorNote(text: String?): String? {
            if (text != null) authorNoteState = text
            return authorNoteState
        }

        override fun authorNoteDepth(depth: Int?): String? {
            if (depth != null) authorNoteDepthState = depth
            return authorNoteDepthState.toString()
        }

        override fun authorNoteFrequency(freq: Int?): String? {
            if (freq != null) authorNoteFreqState = freq
            return authorNoteFreqState.toString()
        }

        override fun authorNotePosition(pos: String?): String? {
            if (pos != null) authorNotePosState = pos
            return authorNotePosState
        }

        override fun authorNoteRole(role: String?): String? {
            if (role != null) authorNoteRoleState = role
            return authorNoteRoleState
        }

        override fun setInput(text: String): String? {
            lastInputText = text
            return text
        }

        override fun getMessages(spec: String, includeNames: Boolean): String? {
            return if (includeNames) "Alice: msg-$spec" else "msg-$spec"
        }

        override fun charFind(name: String): String? = if (name == "Alice") "Alice" else ""

        override fun quickReply(label: String): String? = "QR:$label"

        override fun translateText(text: String, targetLang: String): String? = "TRANS($targetLang):$text"

        override fun summarizeChat(prompt: String): String? = "SUMMARY:$prompt"

        override fun captionImage(prompt: String): String? = "CAPTION:$prompt"

        override fun executeJsSlashCommand(
            name: String,
            namedArgs: Map<String, String>,
            unnamedArgs: String,
            pipe: String,
        ): String? = jsSlashRegistry[name.lowercase()]?.invoke(namedArgs, unnamedArgs)

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

    // ---------- 流控与作用域变量（/let /var /if /else /while /times /break {| ... |}） ----------

    @Test
    fun letAndVarScopedMacroExpansion() {
        val host = FakeHost()
        val out = StSlashExecutor.execute("/let x 10 | /var x 25 | /echo {{var::x}}", host)
        assertEquals("25", out)
    }

    @Test
    fun pipeBarClosureSyntaxAndIfElse() {
        val host = FakeHost()
        val out1 = StSlashExecutor.execute("/let score 80 | /if left={{var::score}} rule=gt right=50 {| /echo PASS |} else={| /echo FAIL |}", host)
        assertEquals("PASS", out1)
        val out2 = StSlashExecutor.execute("/let score 30 | /if left={{var::score}} rule=gt right=50 {| /echo PASS |} | /else {| /echo RETRY |}", host)
        assertEquals("RETRY", out2)
    }

    @Test
    fun timesAndWhileLoopWithBreak() {
        val host = FakeHost()
        val timesOut = StSlashExecutor.execute("/times 3 {| /echo step-{{timesIndex}} |}", host)
        assertEquals("step-2", timesOut)

        val whileOut = StSlashExecutor.execute(
            "/let i 0 | /while left={{var::i}} rule=lt right=5 {| /var i 3 | /if left={{var::i}} rule=eq right=3 {| /break done-3 |} |}",
            host
        )
        assertEquals("done-3", whileOut)
    }

    @Test
    fun commentCommandKeepsPipe() {
        val host = FakeHost()
        val out = StSlashExecutor.execute("/echo keep-me | // ignored comment | /pass", host)
        assertEquals("keep-me", out)
    }

    @Test
    fun timesChainPipeAccumulationAndMixedNestedClosures() {
        val host = FakeHost()
        val chainOut = StSlashExecutor.execute("/times 3 {| /echo a-{{pipe}} |}", host)
        assertEquals("a-", chainOut)

        val nestedOut = StSlashExecutor.execute("/run {: /if (1 == 1) {| /echo ok |} :}", host)
        assertEquals("ok", nestedOut)
    }

    @Test
    fun quietGenAndGenRawReturnOutputToPipeWithoutTouchingInput() {
        val host = FakeHost()
        host.lastInputText = "original-draft"
        val out = StSlashExecutor.execute("/gen 请输出OK | /setvar res", host)
        assertEquals("", out)
        assertEquals("OK", host.vars["res"])
        assertEquals("original-draft", host.lastInputText)

        val rawOut = StSlashExecutor.execute("""/genraw system="你是计算器" 1+1等于几""", host)
        assertEquals("RAW:你是计算器|1+1等于几", rawOut)
        assertEquals("你是计算器" to "1+1等于几", host.lastGenRawPrompt)
    }

    @Test
    fun mathAndStringCommandsAndAuthorNoteAndJsFallback() {
        val host = FakeHost()
        // 1. 算术管道：/add 10 20 | /mul {{pipe}} 2 | /sub {{pipe}} 5 -> "55"
        val mathOut = StSlashExecutor.execute("/add 10 20 | /mul {{pipe}} 2 | /sub {{pipe}} 5", host)
        assertEquals("55", mathOut)
        assertEquals("4", StSlashExecutor.execute("/div 20 5", host))
        assertEquals("2", StSlashExecutor.execute("/mod 17 5", host))
        assertEquals("8", StSlashExecutor.execute("/pow 2 3", host))
        assertEquals("3", StSlashExecutor.execute("/min 9 3 7", host))
        assertEquals("9", StSlashExecutor.execute("/max 9 3 7", host))
        assertEquals("4", StSlashExecutor.execute("/round 3.6", host))
        assertEquals("3", StSlashExecutor.execute("/floor 3.9", host))
        assertEquals("4", StSlashExecutor.execute("/ceil 3.1", host))
        assertEquals("42", StSlashExecutor.execute("/abs -42", host))

        // 2. 字符串与数组命令
        assertEquals("5", StSlashExecutor.execute("/len hello", host))
        assertEquals("HELLO", StSlashExecutor.execute("/upper hello", host))
        assertEquals("hello", StSlashExecutor.execute("/lower HELLO", host))
        assertEquals("ell", StSlashExecutor.execute("/substr start=1 end=4 hello", host))
        assertEquals("a-b-c", StSlashExecutor.execute("""/split sep="," "a,b,c" | /join sep="-"""", host))
        assertEquals("hi world", StSlashExecutor.execute("""/replace find="foo" replace="world" "hi foo"""", host))

        // 3. 作者备注命令
        assertEquals("新的作者备注", StSlashExecutor.execute("/note 新的作者备注", host))
        assertEquals("新的作者备注", host.authorNoteState)
        assertEquals("3", StSlashExecutor.execute("/note-depth 3", host))
        assertEquals(3, host.authorNoteDepthState)
        assertEquals("2", StSlashExecutor.execute("/note-frequency 2", host))
        assertEquals(2, host.authorNoteFreqState)

        // 4. 输入框、消息、快速回复与原生能力命令
        assertEquals("草稿内容", StSlashExecutor.execute("/setinput 草稿内容", host))
        assertEquals("草稿内容", host.lastInputText)
        assertEquals("Alice: msg-0-1", StSlashExecutor.execute("/messages 0-1", host))
        assertEquals("msg-0-1", StSlashExecutor.execute("/messages names=off 0-1", host))
        assertEquals("Alice", StSlashExecutor.execute("/char-find Alice", host))
        assertEquals("QR:开局面板", StSlashExecutor.execute("/qr 开局面板", host))
        assertEquals("TRANS(en):你好", StSlashExecutor.execute("/translate target=en 你好", host))
        assertEquals("SUMMARY:总结剧情", StSlashExecutor.execute("/summarize 总结剧情", host))
        assertEquals("CAPTION:描述画面", StSlashExecutor.execute("/caption 描述画面", host))

        // 5. JS SlashCommandParser 自定义命令回退桥
        host.jsSlashRegistry["ext_ping"] = { _, valArg -> "pong:$valArg" }
        assertEquals("pong:hello", StSlashExecutor.execute("/ext_ping hello", host))
    }

    @Test
    fun d1AndD2AndD11SlashRoutingBracketArgsAndMacroExpansion() {
        // D1: TavernRuntimeManager JS 命令表 O(1) 查询与防死锁守卫
        me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.updateRegisteredJsSlashCommands(listOf("custom_js_cmd"))
        assertTrue(me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.hasJsSlashCommand("Custom_Js_Cmd"))
        assertEquals(false, me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.hasJsSlashCommand("unregistered_cmd"))
        assertEquals(false, me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.isInJavaBridgeCall())

        // D2: shouldRouteToStSlashExecutor 路由判定
        val legacyHelpCmd = me.rerere.rikkahub.ui.components.ai.SlashCommand(
            name = "help",
            description = "help",
            content = "",
            filePath = "builtin",
            builtinKind = me.rerere.rikkahub.ui.components.ai.BuiltinSlashKind.HELP,
        )
        val legacySetvarCmd = me.rerere.rikkahub.ui.components.ai.SlashCommand(
            name = "setvar",
            description = "setvar",
            content = "",
            filePath = "builtin",
            builtinKind = me.rerere.rikkahub.ui.components.ai.BuiltinSlashKind.VAR,
        )
        assertEquals(false, me.rerere.rikkahub.ui.components.ai.shouldRouteToStSlashExecutor("/help", legacyHelpCmd))
        assertTrue(me.rerere.rikkahub.ui.components.ai.shouldRouteToStSlashExecutor("/echo hello", null))
        assertTrue(me.rerere.rikkahub.ui.components.ai.shouldRouteToStSlashExecutor("/setvar a 1 | /echo {{getvar::a}}", legacySetvarCmd))
        assertTrue(me.rerere.rikkahub.ui.components.ai.shouldRouteToStSlashExecutor("/times 2 {| /echo hi |}", null))

        // D11.1: StSlashParser.extractNamedArgs 支持带空格的方括号 JSON 数组与引号包裹分支
        val (named, unnamed) = StSlashParser.extractNamedArgs("""labels=["Option A", "Option B"] Choose one""")
        assertEquals("""["Option A", "Option B"]""", named["labels"])
        assertEquals("Choose one", unnamed)

        val host = FakeHost()
        assertEquals("Option A", StSlashExecutor.execute("""/buttons labels=["Option A", "Option B"] Choose one""", host))
        assertEquals("quoted branch", StSlashExecutor.execute("""/if left=1 rule=eq right=1 "/echo quoted branch" """, host))
    }

    @Test
    fun all288OfficialSlashCommandsAndExtendedMathDataBankWorkCorrectly() {
        // 1. 数学扩展命令：/sin /cos /log /sqrt（含负数/零边界防 NaN）
        assertEquals("0", StSlashExecutor.execute("/sin 0"))
        assertEquals("1", StSlashExecutor.execute("/cos 0"))
        assertEquals("4", StSlashExecutor.execute("/sqrt 16"))
        assertEquals("0", StSlashExecutor.execute("/sqrt -9"))
        assertEquals("0", StSlashExecutor.execute("/log 0"))

        // 2. 列表排序、数组包裹与模糊匹配：/sort /array-wrap /array-unwrap /fuzzy
        assertEquals("[1,2,10]", StSlashExecutor.execute("""/sort [10, 1, 2]"""))
        assertEquals("[10,2,1]", StSlashExecutor.execute("""/sort order=desc [1, 10, 2]"""))
        assertEquals("""["hello"]""", StSlashExecutor.execute("/array-wrap hello"))
        assertEquals("hello", StSlashExecutor.execute("""/array-unwrap ["hello"]"""))
        assertEquals("apple", StSlashExecutor.execute("""/fuzzy list=["apple","banana","orange"] appl"""))

        // 3. 闭包序列化与反序列化：/closure-serialize & /closure-deserialize
        val serialized = StSlashExecutor.execute("/closure-serialize {: /echo hi :}")
        assertTrue(serialized.contains("__st_closure__"))
        val deserialized = StSlashExecutor.execute("/closure-deserialize name=myfn '$serialized' | /run myfn")
        assertEquals("hi", deserialized)

        // 4. 分词计数与截断：/tokens /count /trimtokens
        val tokCount = StSlashExecutor.execute("/tokens hello world").toIntOrNull() ?: 0
        assertTrue("Token count should be > 0", tokCount > 0)
        val trimmed = StSlashExecutor.execute("/trimtokens limit=1 direction=end 'hello world again'")
        assertTrue(trimmed.isNotEmpty() && trimmed.length < "hello world again".length)

        // 5. Data Bank 与向量检索命令族：/db-add /db-get /db-search /vector-threshold
        StSlashExecutor.execute("/db-purge")
        StSlashExecutor.execute("/db-add name=lore1 'The crimson dragon sleeps in the northern volcano'")
        StSlashExecutor.execute("/db-add name=lore2 'The royal library contains ancient magic scrolls'")
        assertEquals("The crimson dragon sleeps in the northern volcano", StSlashExecutor.execute("/db-get lore1"))
        StSlashExecutor.execute("/vector-threshold 0.1")
        val searchHit = StSlashExecutor.execute("/db-search 'crimson dragon volcano'")
        assertTrue(searchHit.contains("crimson dragon"))

        // 6. Reasoning 思维链解析命令：/reasoning-parse
        val thought = StSlashExecutor.execute("/reasoning-parse '<think>step 1 and step 2</think>Final answer'")
        assertEquals("step 1 and step 2", thought)

        // 7. 块内写变量宏向作用域实时回写：{{setvar::k::v}}
        val macroWriteOut = StSlashExecutor.execute("/echo {{setvar:: inlineKey ::42}} | /echo {{getvar::inlineKey}}")
        assertEquals("42", macroWriteOut)
    }

    @Test
    fun kot08AndKot09ToolsInvokeLoaderWrapAndModalHostHooksWork() {
        // 1. /tools-register + /tools-invoke + /tools-unregister (验证 KOT-09 runClosure 修复)
        StSlashExecutor.execute(
            """/tools-register name=calc_sum description="Add two numbers" parameters='{"type":"object","properties":{"a":{"type":"number"},"b":{"type":"number"}}}' {: a=0 b=0 /add {{var::a}} {{var::b}} :}"""
        )
        val invoked = StSlashExecutor.execute("""/tools-invoke name=calc_sum parameters='{"a":15,"b":27}'""")
        assertEquals("42", invoked)
        val unregistered = StSlashExecutor.execute("/tools-unregister calc_sum")
        assertEquals("true", unregistered)

        // 2. /loader-wrap 闭包执行 (验证 KOT-09 runClosure 修复)
        val wrapped = StSlashExecutor.execute("/loader-wrap {: /echo inside-loader :}")
        assertEquals("inside-loader", wrapped)

        // 3. /popup /input /buttons 宿主弹窗回调与无宿主降级
        val modalHost = object : FakeHost() {
            override val chatKey: String? = "modal-chat"
            override fun showPopup(text: String, title: String?, expectResult: Boolean, okButton: String?, cancelButton: String?): String? {
                return if (expectResult) "1" else "ACK:$text"
            }
            override fun showInput(prompt: String, title: String?, defaultValue: String): String? {
                return "USER_TYPED:$defaultValue"
            }
            override fun showButtons(prompt: String, labels: List<String>, multiple: Boolean): String? {
                return if (multiple) """["${labels.lastOrNull().orEmpty()}"]""" else labels.lastOrNull().orEmpty()
            }
        }
        assertEquals("1", StSlashExecutor.execute("/popup result=true Confirm?", modalHost))
        assertEquals("USER_TYPED:initVal", StSlashExecutor.execute("/input default=initVal Enter name", modalHost))
        assertEquals("ChoiceB", StSlashExecutor.execute("""/buttons labels=["ChoiceA","ChoiceB"] Pick one""", modalHost))

        // 4. JS Function Tool 注册表增删查
        me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.registerJsFunctionTool(
            me.rerere.rikkahub.data.st.runtime.JsFunctionToolSpec(
                name = "weather_lookup",
                description = "Lookup weather by city",
                parametersJson = """{"type":"object","properties":{"city":{"type":"string"}}}""",
                stealth = false,
            )
        )
        assertTrue(me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.listJsFunctionTools().any { it.name == "weather_lookup" })
        assertTrue(me.rerere.rikkahub.data.st.runtime.TavernRuntimeManager.unregisterJsFunctionTool("weather_lookup"))
    }
}
