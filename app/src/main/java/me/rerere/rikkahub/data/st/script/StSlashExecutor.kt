package me.rerere.rikkahub.data.st.script

import me.rerere.ai.core.MessageRole

/**
 * STscript 宿主回调：由 UI 层（聊天页 / JS 运行时）实现。
 *
 * 每个方法返回该命令的输出文本（进入管道）；返回 null 表示"命令在当前上下文不可用"。
 * 说明：对齐官方语义——生成类命令（trigger/sysgen/…）为异步任务的"触发"，
 * 本执行器立即继续（不阻塞脚本管道）；真实结果由 UI 侧异步落地。
 */
interface StSlashHost {
    /** 当前对话键（变量作用域）；为空表示无对话上下文。 */
    val chatKey: String?

    /** 变量族命令（setvar/getvar/addvar/incvar/decvar/flushvar/listvar）。 */
    fun varOp(cmd: String, name: String, value: String): String?

    /** 消息插入（send/sendas/sys）。role 已映射为 用户/助手/系统。 */
    fun insertMessage(role: MessageRole, text: String, name: String?, at: Int?): String?

    fun trigger(): String?
    fun continueGen(prompt: String): String?
    fun impersonate(prompt: String): String?
    fun sysgen(prompt: String, name: String?, at: Int?, trim: Boolean): String?
    fun gen(prompt: String, asRole: String, lock: Boolean, length: Int, name: String?, trim: Boolean): String?
    fun persona(name: String, mode: String): String?
    fun renameChar(name: String): String?
    fun js(code: String): String?
    fun tavern(sub: String): String?
    fun echo(text: String, title: String?): String?

    /**
     * /hide | /unhide：切换消息的隐藏标记（对齐 ST `hideChatMessageRange`）。
     *
     * [value] 为 0 基消息索引或 `start-end` 范围（闭区间）；空表示最后一条消息。
     * [nameFilter] 仅作用于指定发言人名的消息（对齐 ST `name` 参数）。
     * @return 输出文本（ST 返回 ''）
     */
    fun hideMessages(value: String, unhide: Boolean, nameFilter: String?): String?

    /**
     * /swipe：切换最后一条消息的 swipe 变体（对齐 ST `swipe()`）。
     *
     * 右向越界时：非用户且未隐藏的消息重新生成（新变体）；否则回绕到第一条（ST LOOP）。
     * 左向越界时回绕到最后一条变体。
     *
     * [direction] `right`（下一条/生成新变体）或 `left`（上一条）；
     * [await] true 时等待生成完成（对齐 ST await 参数）。
     */
    fun swipe(direction: String, await: Boolean): String?

    /**
     * /expression-fallback：表情立绘兜底标签（对齐 ST `extensions/expressions/index.js:792-813`）。
     *
     * [label] 为空 → 返回当前兜底标签；合法标签 → 设为兜底并返回该标签；
     * 无法匹配 → 返回空串（官方 toastr.warning 后返回 ''，不改设置）。
     */
    fun expressionFallback(label: String): String? = null

    // ---- ST Checkpoints（书签，对齐 bookmarks.js）----

    /**
     * /checkpoint-create：在 [mesId]（null = 最后一条）处创建检查点。
     * [name] 为空时按 `<标题> - Checkpoint #N` 自动命名并去重（对齐 ST forceName）。
     */
    fun checkpointCreate(mesId: Int?, name: String?): String? = null

    /** /checkpoint-go：打开 [mesId]（null = 最后一条）消息上挂的检查点会话。 */
    fun checkpointGo(mesId: Int?): String? = null

    /** /checkpoint-exit：从当前检查点返回父会话。 */
    fun checkpointExit(): String? = null

    /** /checkpoint-parent：返回当前检查点的父会话名字。 */
    fun checkpointParent(): String? = null

    /** /checkpoint-get：返回 [mesId]（null = 最后一条）消息上的检查点链接名。 */
    fun checkpointGet(mesId: Int?): String? = null

    /** /checkpoint-list：[links] true 返回链接名 JSON 数组；false 返回消息索引数组。 */
    fun checkpointList(links: Boolean): String? = null

    /** /branch-create：在 [mesId]（null = 最后一条）处创建分支并打开（对齐 ST `createBranch`）。 */
    fun branchCreate(mesId: Int?): String? = null

    /** 文本宏展开钩子（可选；缺省原样返回，由调用方决定是否接宏引擎）。 */
    fun expandMacros(text: String): String = text
}

/**
 * STscript 简化版执行器（对齐 SillyTavern `slash-commands` 的运行时核心子集）。
 *
 * 执行模型（与官方一致）：
 * - `|` 串联命令；`||` 禁用"上一段输出作为本段默认参数"的自动注入；
 * - 每个命令的输出进入管道（pipe），供后续 `{{pipe}}` 宏引用；
 * - 首个命令不接收注入；未写未命名参数的命令（非首段）自动以管道值作为其文本参数；
 * - 游离文本段丢弃（官方 root closure 语义）；`{{pipe}}` 在参数中替换；
 * - 未知命令输出错误文本（不中断后续管道）。
 */
object StSlashExecutor {
    private const val UNKNOWN_CMD = "未知命令：/%s"
    private const val UNAVAILABLE_CMD = "命令不可用：/%s"

    /** [W4] 闭包递归深度上限（防无限递归栈溢出；官方无显式上限，靠 abortController 停止） */
    internal const val MAX_CLOSURE_DEPTH = 32

    /** [W4] `/delay` 单次最大等待（毫秒）：防止脚本把执行线程挂死 */
    internal const val MAX_DELAY_MS = 30_000L

    /**
     * [v240 W4] 诊断日志钩子（可选）：UI 侧接 `TavernRuntimeManager.appendLog`，
     * 便于真机验证时在 `tavern-runtime.log` 观察「闭包是否解析成功、/run 是否命中」。
     * JVM 单测保持 null（零开销、核心层不引入 Android 依赖）。
     */
    @Volatile
    var debugLogger: ((String) -> Unit)? = null

    /** 执行一行 STscript，返回最终管道值。 */
    fun execute(script: String, host: StSlashHost?): String =
        execute(script, host, StSlashScope(), initialPipe = "")

    /**
     * 执行入口（内部）：[scope] 承载单次执行的闭包作用域 / 参数栈 / 中止标记；
     * 闭包体通过 [initialPipe] 继承调用方的管道值（官方 closure scope.pipe 的 parent 继承语义）。
     */
    internal fun execute(
        script: String,
        host: StSlashHost?,
        scope: StSlashScope,
        initialPipe: String = "",
    ): String {
        val segments = StSlashParser.parseParts(script)
        var pipe = initialPipe
        var cmdIndex = 0
        for (seg in segments) {
            if (scope.abort) break // /abort：停止整个批次（官方 abortController 语义）
            if (!seg.isCommand || seg.command.isBlank()) continue // 对齐官方：游离文本丢弃
            val isFirst = cmdIndex == 0
            // 官方：参数中的 {{pipe}} 宏在命令执行时替换为当前管道值
            val rawArgs = seg.args.replace("{{pipe}}", pipe)
            val output = try {
                runCommand(seg, rawArgs, host, pipe, isFirst, scope)
            } catch (e: Exception) {
                "命令执行失败：/${seg.command}（${e.message ?: e.javaClass.simpleName}）"
            }
            debugLogger?.invoke("[slash] /${seg.command} -> ${output.take(180)}")
            pipe = output
            cmdIndex++
        }
        return pipe
    }

    private fun runCommand(
        seg: StSlashParser.Segment,
        rawArgs: String,
        host: StSlashHost?,
        pipe: String,
        isFirst: Boolean,
        scope: StSlashScope,
    ): String = when (seg.command) {
        // ———— 管道工具 ————
        "pass", "return" -> resolveText(rawArgs, host, pipe, isFirst, seg.noPipeInject).first

        "echo" -> {
            val (txt, named) = resolveText(rawArgs, host, pipe, isFirst, seg.noPipeInject)
            host?.echo(txt, named["title"]) ?: txt
        }

        // ———— 变量族 ————
        "setvar", "getvar", "addvar", "incvar", "decvar", "flushvar", "listvar" -> {
            // [W4] 闭包作为值：/setvar fn={: ... :}（官方 scope 变量可存闭包；现有变量只存字符串，
            // 故本地单开一张闭包表 StClosureStore，按会话作用域隔离）
            val closureAssignment = if (seg.command == "setvar") parseClosureAssignment(rawArgs) else null
            if (closureAssignment != null) {
                scope.localClosures[closureAssignment.first] = closureAssignment.second
                StClosureStore.put(host?.chatKey, closureAssignment.first, closureAssignment.second)
                ""
            } else {
                val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
                val key: String
                val value: String
                if (named.containsKey("key")) {
                    key = named["key"]!!.trim()
                    value = rest
                } else {
                    val trimmed = rest.trim()
                    val sp = trimmed.indexOfFirst { it.isWhitespace() }
                    key = StSlashParser.unwrapQuotes(if (sp == -1) trimmed else trimmed.substring(0, sp))
                    value = if (sp == -1) "" else trimmed.substring(sp).trim()
                }
                host?.varOp(seg.command, key, value) ?: unavailable(seg.command)
            }
        }

        // ———— 消息插入 ————
        "send" -> insertCmd(seg, rawArgs, host, pipe, isFirst) { h, text, name, at ->
            h.insertMessage(MessageRole.USER, text, name, at)
        }

        "sendas" -> insertCmd(seg, rawArgs, host, pipe, isFirst) { h, text, name, at ->
            h.insertMessage(MessageRole.ASSISTANT, text, name, at)
        }

        "sys" -> insertCmd(seg, rawArgs, host, pipe, isFirst) { h, text, name, at ->
            h.insertMessage(MessageRole.SYSTEM, text, name, at)
        }

        "sysgen" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val text = resolveRest(rest, pipe, isFirst, seg.noPipeInject).let(StSlashParser::unwrapQuotes)
            if (text.isBlank()) {
                "命令缺少文本：/sysgen"
            } else {
                host?.sysgen(
                    prompt = text,
                    name = named["name"],
                    at = named["at"]?.toIntOrNull(),
                    trim = isTrueBoolean(named["trim"] ?: ""),
                ) ?: unavailable(seg.command)
            }
        }

        // ———— 生成类 ————
        "trigger" -> host?.trigger() ?: unavailable(seg.command)

        "continue" -> {
            val text = StSlashParser.unwrapQuotes(resolveRest(rawArgs, pipe, isFirst, seg.noPipeInject))
            host?.continueGen(text) ?: unavailable(seg.command)
        }

        "impersonate" -> {
            val text = StSlashParser.unwrapQuotes(resolveRest(rawArgs, pipe, isFirst, seg.noPipeInject))
            host?.impersonate(text) ?: unavailable(seg.command)
        }

        "gen" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val prompt = resolveRest(rest, pipe, isFirst, seg.noPipeInject).let(StSlashParser::unwrapQuotes)
            if (prompt.isBlank()) {
                "命令缺少文本：/gen"
            } else {
                host?.gen(
                    prompt = prompt,
                    asRole = named["as"] ?: "system",
                    lock = isTrueBoolean(named["lock"] ?: ""),
                    length = named["length"]?.toIntOrNull() ?: 0,
                    name = named["name"],
                    trim = isTrueBoolean(named["trim"] ?: ""),
                ) ?: unavailable(seg.command)
            }
        }

        // ———— 人设 / 角色 ————
        "persona", "persona-set" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            host?.persona(rest.trim(), (named["mode"] ?: "all").lowercase()) ?: unavailable(seg.command)
        }

        "rename-char" -> {
            val name = StSlashParser.unwrapQuotes(resolveRest(rawArgs, pipe, isFirst, seg.noPipeInject))
            if (name.isBlank()) {
                "命令缺少名称：/rename-char"
            } else {
                host?.renameChar(name) ?: unavailable(seg.command)
            }
        }

        // ———— 运行时 ————
        "js" -> host?.js(rawArgs.trim()) ?: unavailable(seg.command)

        "tavern" -> host?.tavern(rawArgs.trim().lowercase()) ?: unavailable(seg.command)

        // ———— [W4] 控制流：闭包 / /run ————
        "run", "call", "exec" -> runClosureCommand(rawArgs, host, pipe, isFirst, seg, scope)

        // ———— [W4] /abort（官方 slash-commands.js:2363-2380 abortCallback）————
        "abort" -> {
            val (_, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val reason = StSlashParser.unwrapQuotes(resolveRest(rest, pipe, isFirst, seg.noPipeInject)).trim()
            scope.abort = true
            scope.abortReason = reason
            ""
        }

        // ———— [W4] /delay（官方别名 wait/sleep，参数为毫秒）————
        "delay", "wait", "sleep" -> {
            val (_, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val amount = resolveRest(rest, pipe, isFirst, seg.noPipeInject).trim().toLongOrNull() ?: 0L
            delayBestEffort(amount)
            ""
        }

        // ———— [W4] /switch（官方 1.18 无此命令；本地闭包分派扩展，见 DIVERGENCE.md）————
        "switch" -> runSwitchCommand(rawArgs, host, pipe, isFirst, seg, scope)

        // ———— 消息隐藏 / swipe ————
        "hide", "unhide" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val value = StSlashParser.unwrapQuotes(resolveRest(rest, pipe, isFirst, seg.noPipeInject)).trim()
            host?.hideMessages(
                value = value,
                unhide = seg.command == "unhide",
                nameFilter = named["name"]?.trim()?.takeIf { it.isNotBlank() },
            ) ?: unavailable(seg.command)
        }

        "swipe" -> {
            val (named, _) = StSlashParser.extractNamedArgs(rawArgs)
            // 对齐 ST：direction 仅 left 视为左向，其余（含缺省）为右向
            val direction = if (named["direction"].equals("left", ignoreCase = true)) "left" else "right"
            host?.swipe(direction, isTrueBoolean(named["await"] ?: "")) ?: unavailable(seg.command)
        }

        // ———— 表情立绘（W3，对齐 ST /expression-fallback）————
        "expression-fallback" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val label = StSlashParser.unwrapQuotes(resolveRest(rest, pipe, isFirst, seg.noPipeInject)).trim()
            host?.expressionFallback(label) ?: unavailable(seg.command)
        }

        // ———— ST Checkpoints（书签，对齐 bookmarks.js）————
        // 注意：extractNamedArgs 的键名一律小写化，取值需用 mesid（而非 mesId）
        "checkpoint-create" -> {
            val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
            val name = StSlashParser.unwrapQuotes(resolveRest(rest, pipe, isFirst, seg.noPipeInject)).trim()
            host?.checkpointCreate(named["mesid"]?.toIntOrNull(), name) ?: unavailable(seg.command)
        }

        "checkpoint-go" -> {
            val (named, _) = StSlashParser.extractNamedArgs(rawArgs)
            host?.checkpointGo(named["mesid"]?.toIntOrNull()) ?: unavailable(seg.command)
        }

        "checkpoint-exit" -> host?.checkpointExit() ?: unavailable(seg.command)

        "checkpoint-parent" -> host?.checkpointParent() ?: unavailable(seg.command)

        "checkpoint-get" -> {
            val (named, _) = StSlashParser.extractNamedArgs(rawArgs)
            host?.checkpointGet(named["mesid"]?.toIntOrNull()) ?: unavailable(seg.command)
        }

        "checkpoint-list" -> {
            val (named, _) = StSlashParser.extractNamedArgs(rawArgs)
            host?.checkpointList(isTrueBoolean(named["links"] ?: "")) ?: unavailable(seg.command)
        }

        "branch-create" -> {
            val (named, _) = StSlashParser.extractNamedArgs(rawArgs)
            host?.branchCreate(named["mesid"]?.toIntOrNull()) ?: unavailable(seg.command)
        }

        else -> UNKNOWN_CMD.format(seg.command)
    }

    /** 解析命令参数：剥离命名参数并把文本整体去外层引号；未命名参数为空时按注入规则接收管道值。 */
    private fun resolveText(
        rawArgs: String,
        host: StSlashHost?,
        pipe: String,
        isFirst: Boolean,
        noPipeInject: Boolean,
    ): Pair<String, Map<String, String>> {
        val expanded = host?.expandMacros(rawArgs) ?: rawArgs
        val (named, rest) = StSlashParser.extractNamedArgs(expanded)
        val text = resolveRest(rest, pipe, isFirst, noPipeInject).let(StSlashParser::unwrapQuotes)
        return text to named
    }

    /** 管道注入规则：未命名参数为空 且 非首命令 且 未禁用注入 且 管道非空 → 以管道值作为文本参数。 */
    private fun resolveRest(
        rest: String,
        pipe: String,
        isFirst: Boolean,
        noPipeInject: Boolean,
    ): String {
        return if (rest.isBlank() && !isFirst && !noPipeInject && pipe.isNotEmpty()) pipe else rest
    }

    private inline fun insertCmd(
        seg: StSlashParser.Segment,
        rawArgs: String,
        host: StSlashHost?,
        pipe: String,
        isFirst: Boolean,
        invoke: (StSlashHost, String, String?, Int?) -> String?,
    ): String {
        val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
        val text = resolveRest(rest, pipe, isFirst, seg.noPipeInject).let(StSlashParser::unwrapQuotes)
        if (text.isBlank()) return "命令缺少文本：/${seg.command}"
        val h = host ?: return unavailable(seg.command)
        return invoke(h, text, named["name"], named["at"]?.toIntOrNull()) ?: unavailable(seg.command)
    }

    private fun unavailable(cmd: String): String = UNAVAILABLE_CMD.format(cmd)

    /** 官方 isTrueBoolean：true/1/on（大小写不敏感）。 */
    private fun isTrueBoolean(value: String): Boolean =
        value.equals("true", ignoreCase = true) || value == "1" || value.equals("on", ignoreCase = true)

    // ==================== [W4] 闭包支持 ====================

    /** 解析 `/setvar name={: ... :}` / `/setvar name {: ... :}`（官方变量可存闭包）。 */
    private fun parseClosureAssignment(rawArgs: String): Pair<String, StClosure>? {
        val text = rawArgs.trim()
        val start = text.indexOf(StClosureSyntax.OPEN)
        if (start <= 0) return null
        val end = StClosureSyntax.findClosureEnd(text, start)
        if (end < 0) return null
        val closure = StClosureSyntax.parse(text.substring(start, end)) ?: return null
        val head = text.substring(0, start).trim().removeSuffix("=").trim()
        val name = StSlashParser.unwrapQuotes(head)
        if (name.isBlank() || name.contains(' ')) return null
        return name to closure
    }

    /** `/run`（别名 `/call` `/exec`）：执行闭包字面量，或变量里的闭包。 */
    private fun runClosureCommand(
        rawArgs: String,
        host: StSlashHost?,
        pipe: String,
        isFirst: Boolean,
        seg: StSlashParser.Segment,
        scope: StSlashScope,
    ): String {
        // 1. 直接给闭包字面量：`/run {: ... :} key=value`
        StClosureSyntax.takeFirstClosure(rawArgs)?.let { (closure, rest) ->
            val (named, _) = StSlashParser.extractNamedArgs(rest)
            return runClosure(closure, named, host, pipe, scope, label = "<literal>")
        }
        // 2. 变量名（本次脚本局部闭包 → 会话闭包表 → 字符串变量里的字面量）
        val (named, rest) = StSlashParser.extractNamedArgs(rawArgs)
        val target = resolveRest(rest, pipe, isFirst, seg.noPipeInject).trim()
        if (target.isBlank()) return "\"/run\" 缺少闭包名或闭包字面量"
        StClosureSyntax.parse(target)?.let { direct ->
            return runClosure(direct, named, host, pipe, scope, label = "<literal>")
        }
        val closure = lookupClosure(target, host, scope)
            ?: return "\"$target\" is not callable.".also {
                debugLogger?.invoke("[slash] /run $target -> not callable")
            }
        debugLogger?.invoke("[slash] /run $target (args=${named.keys})")
        return runClosure(closure, named, host, pipe, scope, label = target)
    }

    /** 闭包查找：本次脚本局部 → 会话闭包表 → 字符串变量里的闭包字面量（兼容路径）。 */
    private fun lookupClosure(name: String, host: StSlashHost?, scope: StSlashScope): StClosure? {
        scope.localClosures[name]?.let { return it }
        StClosureStore.get(host?.chatKey, name)?.let { return it }
        val raw = host?.varOp("getvar", name, "")?.takeIf { it.isNotBlank() } ?: return null
        return StClosureSyntax.parse(raw)
    }

    /**
     * 执行闭包（官方 SlashCommandClosure.execute）：
     * - 每次执行用独立的参数映射（声明默认值 + /run 提供值覆盖），互不泄漏；
     * - 未提供的 `{{arg::key}}` 展开为空串（官方 `arg::*` 通配默认）；
     * - 递归深度超限返回错误文本，绝不栈溢出。
     */
    private fun runClosure(
        closure: StClosure,
        provided: Map<String, String>,
        host: StSlashHost?,
        parentPipe: String,
        scope: StSlashScope,
        label: String,
    ): String {
        if (scope.depth >= MAX_CLOSURE_DEPTH) {
            return "闭包递归深度超过上限（$MAX_CLOSURE_DEPTH）：$label"
        }
        val args = LinkedHashMap<String, String>()
        closure.argumentList.forEach { args[it.name] = it.value }
        provided.forEach { (key, value) -> args[key] = value }
        scope.argStack.addLast(args)
        scope.depth++
        try {
            val body = StClosureSyntax.substituteArgs(closure.body, args)
            return execute(body, host, scope, initialPipe = parentPipe)
        } finally {
            scope.depth--
            scope.argStack.removeLast()
        }
    }

    /**
     * `/switch`：闭包分派（rikkaST 扩展，官方 1.18 无此命令）。
     * 语法：`/switch <value> <case>={<closure>} default={<closure>}`；值相等执行对应分支，
     * 否则执行 default；分支值不是闭包字面量时按普通文本返回。
     */
    private fun runSwitchCommand(
        rawArgs: String,
        host: StSlashHost?,
        pipe: String,
        isFirst: Boolean,
        seg: StSlashParser.Segment,
        scope: StSlashScope,
    ): String {
        val text = resolveRest(rawArgs, pipe, isFirst, seg.noPipeInject)
        val tokens = StClosureSyntax.tokenizeArgs(text)
        if (tokens.isEmpty()) return "用法：/switch <value> <case>={<closure>} default={<closure>}"
        val value = StSlashParser.unwrapQuotes(tokens.first())
        var fallback: String? = null
        for (token in tokens.drop(1)) {
            val eq = token.indexOf('=')
            if (eq <= 0) continue
            val key = token.substring(0, eq).trim()
            val branch = token.substring(eq + 1).trim()
            if (key.equals("default", ignoreCase = true)) fallback = branch
            if (key == value) return executeSwitchBranch(branch, host, pipe, scope)
        }
        return fallback?.let { executeSwitchBranch(it, host, pipe, scope) } ?: ""
    }

    private fun executeSwitchBranch(branch: String, host: StSlashHost?, pipe: String, scope: StSlashScope): String {
        val closure = StClosureSyntax.parse(branch)
        return if (closure != null) {
            runClosure(closure, emptyMap(), host, pipe, scope, label = "<switch>")
        } else {
            StSlashParser.unwrapQuotes(branch)
        }
    }

    /**
     * `/delay` 的真实等待：
     * - 非 UI 线程：`Thread.sleep`（官方 delayCallback 的等待语义）；
     * - UI 线程：跳过等待（不能阻塞主线程造成 ANR），管道继续；差异登记在 DIVERGENCE.md。
     */
    private fun delayBestEffort(millis: Long) {
        val ms = millis.coerceIn(0L, MAX_DELAY_MS)
        if (ms <= 0) return
        if (Thread.currentThread().name == "main") return
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}

/**
 * [W4] 单次 STscript 执行的运行时作用域：
 * - [localClosures]：本次脚本内 `/setvar name={:...:}` 定义的闭包（官方 scope.variables 的闭包值子集）；
 * - [argStack]：闭包命名参数栈（`{{arg::key}}` 数据源，官方 scope.macroList 的等价物）；
 * - [depth]：闭包调用深度（防无限递归）；
 * - [abort]/[abortReason]：`/abort` 触发后停止整个批次（官方 abortController 语义）。
 */
internal class StSlashScope {
    var abort: Boolean = false
    var abortReason: String = ""
    var depth: Int = 0
    val localClosures: MutableMap<String, StClosure> = mutableMapOf()
    val argStack: ArrayDeque<Map<String, String>> = ArrayDeque()

    /** 当前最内层闭包的命名参数（无闭包时为空表） */
    fun currentArgs(): Map<String, String> = argStack.lastOrNull().orEmpty()
}
