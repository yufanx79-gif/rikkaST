package me.rerere.rikkahub.data.st.script

import java.util.concurrent.ConcurrentHashMap

/**
 * STscript 闭包字面量 `{: ... :}`（对齐官方 `slash-commands/SlashCommandClosure.js`
 * 与 `SlashCommandParser.js:743-820` 的语法子集）。
 *
 * ## 官方语义（1.18 源码核实）
 * - 闭包字面量以 `{:` 开始、`:}` 结束，内部是一段完整 STscript（`|` 管道可用）；
 * - 闭包体**开头**的 `key=value` 序列是「闭包命名参数声明」（parser 的
 *   `while (testNamedArgument())`），可在体里用 `{{arg::key}}` 引用（官方文档：
 *   "Named arguments can be referenced in a QR with `{{arg::key}}`"）；
 * - `/run`（别名 `/call` `/exec`）执行闭包：直接给字面量，或给「存了闭包的变量名」；
 *   `/run fn key=value` 会把命名参数提供给闭包（官方 runCallback:4214-4259）；
 * - 官方 QuickReply 用 `scope.setMacro('arg::*', '')` 定义通配兜底，因此**未提供的
 *   `{{arg::key}}` 展开成空串**、`{{arg::*}}` 是通配默认值（QuickReplySet.js:123、
 *   SlashCommandHandler.js:654）。
 *
 * ## rikkaST 本批子集（与官方的差异，已登记 DIVERGENCE.md）
 * - 不实现闭包的执行期 debug/breakpoint 机制；
 * - `{{arg::key}}` 在闭包体进入执行前做一次文本替换（官方走宏引擎动态宏，
 *   效果等价：闭包体内任何位置都可见）；
 * - 变量里的闭包存在进程内存表 [StClosureStore]（现有变量只存字符串），
 *   按 `host.chatKey` 分作用域，不持久化到磁盘（官方同样只在会话内存里）。
 */
data class StClosure(
    /** 闭包体脚本（不含 `{: :}`，已剥离头部命名参数声明） */
    val body: String,
    /** 头部命名参数声明（官方 closure.argumentList） */
    val argumentList: List<StClosureArg> = emptyList(),
    /** 原始字面量（含 `{: :}`，日志/调试用） */
    val raw: String = body,
)

/** 闭包命名参数声明（`key=value`，官方 SlashCommandNamedArgumentAssignment 子集） */
data class StClosureArg(val name: String, val value: String)

object StClosureSyntax {

    const val OPEN = "{:"
    const val CLOSE = ":}"

    /** 头部命名参数声明的匹配（官方 testNamedArgument：标识符 + `=` + 单 token/引号值） */
    private val HEAD_NAMED_ARG = Regex("""^\s*([A-Za-z0-9_]+)\s*=\s*("[^"]*"|'[^']*'|[^\s]+)""")

    /** `{{arg::key}}`（官方宏引擎动态宏；大小写不敏感） */
    private val ARG_MACRO = Regex("""\{\{\s*arg::([^}]*?)\s*\}\}""", RegexOption.IGNORE_CASE)

    /** 解析整段 `{: ... :}`（前后允许空白）；不是闭包字面量返回 null */
    fun parse(text: String): StClosure? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(OPEN) || !trimmed.endsWith(CLOSE)) return null
        if (trimmed.length < OPEN.length + CLOSE.length) return null
        val inner = trimmed.substring(OPEN.length, trimmed.length - CLOSE.length)
        val (args, body) = splitLeadingNamedArgs(inner)
        return StClosure(body = body, argumentList = args, raw = trimmed)
    }

    /**
     * 从任意文本中取出**第一个**闭包字面量，返回 (闭包, 去掉该闭包后的文本)。
     * 找不到返回 null。用于 `/run {: ... :} key=value`、`/setvar fn={: ... :}`。
     */
    fun takeFirstClosure(text: String): Pair<StClosure, String>? {
        var from = 0
        while (true) {
            val start = text.indexOf(OPEN, from)
            if (start < 0) return null
            val end = findClosureEnd(text, start)
            if (end < 0) {
                from = start + OPEN.length
                continue
            }
            val closure = parse(text.substring(start, end)) ?: return null
            val rest = text.substring(0, start) + text.substring(end)
            return closure to rest
        }
    }

    /** 从 [start]（必须指向 `{:`）开始匹配 `:}`，返回结束位置之后的下标；未闭合返回 -1。 */
    fun findClosureEnd(text: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < text.length - 1) {
            when {
                text.startsWith(OPEN, i) -> {
                    depth++
                    i += OPEN.length
                }
                text.startsWith(CLOSE, i) -> {
                    depth--
                    i += CLOSE.length
                    if (depth == 0) return i
                }
                else -> i++
            }
        }
        return -1
    }

    /**
     * 参数分词（闭包字面量保持为**一个** token；引号内空白不切分）。
     * 供 `/switch value key={: ... :}` 这类「命名参数值是闭包」的命令使用。
     */
    fun tokenizeArgs(args: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < args.length) {
            val c = args[i]
            when {
                c.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        out += sb.toString()
                        sb.clear()
                    }
                    i++
                }
                c == '{' && i + 1 < args.length && args[i + 1] == ':' -> {
                    val end = findClosureEnd(args, i)
                    if (end > i) {
                        sb.append(args, i, end)
                        i = end
                    } else {
                        sb.append(c)
                        i++
                    }
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    sb.append(c)
                    i++
                    while (i < args.length && args[i] != quote) {
                        sb.append(args[i])
                        i++
                    }
                    if (i < args.length) {
                        sb.append(args[i])
                        i++
                    }
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    /**
     * `{{arg::key}}` 文本替换（官方 scope macroList 的等价物）：
     * 精确 key → 大小写不敏感 key → `*` 通配 → 空串。
     */
    fun substituteArgs(text: String, args: Map<String, String>): String {
        if (!text.contains("{{")) return text
        val lowerArgs = args.entries.associate { it.key.lowercase() to it.value }
        return ARG_MACRO.replace(text) { match ->
            val key = match.groupValues[1].trim()
            args[key]
                ?: lowerArgs[key.lowercase()]
                ?: args["*"]
                ?: lowerArgs["*"]
                ?: ""
        }
    }

    /** 官方 parseClosure 的头部命名参数循环：消费开头的 `key=value` 声明。 */
    private fun splitLeadingNamedArgs(inner: String): Pair<List<StClosureArg>, String> {
        val args = mutableListOf<StClosureArg>()
        var rest = inner
        while (true) {
            val match = HEAD_NAMED_ARG.find(rest) ?: break
            if (match.range.first != 0) break
            val rawValue = match.groupValues[2]
            val value = if (rawValue.length >= 2 && (
                    (rawValue.startsWith("\"") && rawValue.endsWith("\"")) ||
                        (rawValue.startsWith("'") && rawValue.endsWith("'"))
                    )
            ) {
                rawValue.substring(1, rawValue.length - 1)
            } else {
                rawValue
            }
            args += StClosureArg(name = match.groupValues[1], value = value)
            rest = rest.substring(match.range.last + 1)
        }
        return args to rest.trim()
    }
}

/**
 * 闭包表：把「闭包」作为值存进变量作用域（现有变量只存字符串，故单开一张内存表）。
 *
 * - 作用域键 = `host.chatKey`（每个对话一套；无会话时用 [GLOBAL_SCOPE]）；
 * - 进程内存、不持久化（官方 SlashCommandScope 同样是会话内存）；
 * - 供 `/setvar fn={: ... :}` 写入、`/run fn` 读取，支持闭包内再 `/run` 自己（递归）。
 */
object StClosureStore {

    const val GLOBAL_SCOPE = "__st_closure_global__"

    private val scopes = ConcurrentHashMap<String, ConcurrentHashMap<String, StClosure>>()

    private fun scopeOf(scopeKey: String?): ConcurrentHashMap<String, StClosure> =
        scopes.computeIfAbsent(scopeKey ?: GLOBAL_SCOPE) { ConcurrentHashMap() }

    fun get(scopeKey: String?, name: String): StClosure? = scopeOf(scopeKey)[name]

    fun put(scopeKey: String?, name: String, closure: StClosure) {
        scopeOf(scopeKey)[name] = closure
    }

    fun remove(scopeKey: String?, name: String) {
        scopeOf(scopeKey).remove(name)
    }

    fun clearScope(scopeKey: String?) {
        scopeOf(scopeKey).clear()
    }

    /** 测试/清理用 */
    fun clearAll() {
        scopes.clear()
    }
}
