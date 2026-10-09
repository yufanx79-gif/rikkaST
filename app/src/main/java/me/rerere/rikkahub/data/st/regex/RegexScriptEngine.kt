package me.rerere.rikkahub.data.st.regex

/**
 * ST 1.18 正则脚本引擎移植
 *
 * 对齐 public/scripts/extensions/regex/engine.js：
 * - [runRegexScript] / [getRegexedString] / filterString / substitute_find_regex / sanitizeRegexMacro
 * - 替换串支持 `{{match}}`（= $0）、`$1..$99`、`$<name>`；分组引用值会先剔除 trimStrings
 * - 替换串最后整体做一次宏替换（对齐 ST 末尾 substituteParams(replaceWithGroups)）
 *
 * PORT NOTE（与 ST 的差异，均已评估为净行为等价或更合理）：
 * 1. ST 的“无标记”脚本通过 raw 通道一次性改写消息源；本实现不修改消息源，改为在
 *    “提示词组装”与“渲染显示”两条通道各自叠加，净效果一致，且避免破坏原始消息。
 * 2. 非 `/pattern/flags` 形式的 findRegex 按“全局替换”处理（与 RikkaHub 既有 AssistantRegex
 *    行为一致）；`/pattern/` 未带 `g` 时仍严格“仅首个匹配”（对齐 ST）。
 */
object RegexScriptEngine {

    private const val CACHE_MAX_SIZE = 1000

    /** 编译缓存（LRU，对齐 ST RegexProvider；编译失败缓存 null，避免反复抛异常） */
    private val cache = object : LinkedHashMap<String, Regex?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex?>): Boolean =
            size > CACHE_MAX_SIZE
    }
    private val cacheLock = Any()

    /** 正则引擎诊断 logcat tag。 */
    private const val TAG = "RegexScriptEngine"

    /** [v214] 不定长 lookbehind 降级时的量词上界（可调）。 */
    internal const val BOUNDED_LOOKBEHIND_MAX = 4000

    /**
     * [v214] 编译失败诊断钩子。宿主接线（TavernRuntimeManager）后可在 tavern-runtime.log
     * 看到 `[regex] compile failed: pattern=... error=...`；未接线时仅走 android.util.Log.w。
     */
    @Volatile var onCompileError: ((String) -> Unit)? = null

    /** [v214] 不定长 lookbehind 降级成功诊断钩子（tavern-runtime.log 可见降级后 pattern）。 */
    @Volatile var onCompileDegraded: ((String) -> Unit)? = null

    /** 编译正则（支持 `/pattern/flags` 内联形式，flags: g/i/m/s/u） */
    fun compileRegex(pattern: String): Regex? {
        synchronized(cacheLock) {
            if (cache.containsKey(pattern)) return cache[pattern]
        }
        var compiled: Regex? = null
        var failure: Exception? = null
        try {
            compiled = compileRegexUncached(pattern)
        } catch (e: Exception) {
            failure = e
            val degraded = try {
                degradeUnboundedLookbehind(pattern)
            } catch (_: Exception) {
                null
            }
            if (degraded != null && degraded != pattern) {
                try {
                    compiled = compileRegexUncached(degraded)
                    val msg = "[regex] degraded unbounded lookbehind: pattern=$pattern -> $degraded"
                    warn(msg)
                    onCompileDegraded?.invoke(msg)
                } catch (e2: Exception) {
                    failure = e2
                    compiled = null
                }
            }
        }

        if (compiled == null) {
            val msg = "[regex] compile failed: pattern=$pattern error=${failure?.message}"
            warn(msg)
            onCompileError?.invoke(msg)
        }
        synchronized(cacheLock) { cache[pattern] = compiled }
        return compiled
    }

    /** logcat 诊断；单测 JVM 上 android.util.Log 未 mock 会抛异常，故 runCatching 包裹。 */
    private fun warn(message: String) {
        runCatching { android.util.Log.w(TAG, message) }
    }

    /**
     * PORT NOTE 3（对齐 JS 正则语义）：
     *
     * JS 的 `.` 不匹配 `\n` / `\u2028` / `\u2029`，但**匹配 `\r`**；Java/Kotlin 默认的 `.`
     * 连 `\r` 也不匹配。ST 卡正则大量使用 `(.*?)\n` 形式，而 ST 卡 JSON 的 `first_mes` /
     * `alternate_greetings` 等字段在 Windows 上编辑过就带 CRLF（`\r\n`）——此时默认语义下
     * `(.*?)` 无法越过 `\r`，整条正则静默失配，开场白 / 状态栏的 HTML 注入不生效
     * （外部现象：「预设对话不渲染」，只看到裸 DSL 文本；AI 后续生成的正文是 LF，所以正常）。
     *
     * `RegexOption.UNIX_LINES` 让 `.` 只把 `\n` 视作行终止符，从而与 JS 一致。
     * 实测（Java oracle，俺の彼女卡 25 条正则 × 真卡 CRLF first_mes）：
     * 默认语义 CRLF 命中 0 条、LF 命中 24 条；加 UNIX_LINES 后 CRLF 命中数与 LF 完全一致。
     */
    private val JS_REGEX_OPTIONS = setOf(RegexOption.UNIX_LINES)

    private val SLASH_FORM = Regex("^/(.*)/([a-zA-Z]*)$", RegexOption.DOT_MATCHES_ALL)

    private fun compileRegexUncached(pattern: String): Regex {
        val slashForm = SLASH_FORM.matchEntire(pattern)
            ?: return Regex(pattern, JS_REGEX_OPTIONS)
        var options = JS_REGEX_OPTIONS
        for (c in slashForm.groupValues[2].lowercase()) {
            options = when (c) {
                'i' -> options + RegexOption.IGNORE_CASE
                'm' -> options + RegexOption.MULTILINE
                's' -> options + RegexOption.DOT_MATCHES_ALL
                // u / y / x 等：Kotlin 无对应选项，忽略
                else -> options
            }
        }
        return Regex(slashForm.groupValues[1], options)
    }

    /**
     * [v214] 不定长 lookbehind 降级。
     *
     * Java/Kotlin 正则要求 lookbehind 具有「显然的最大长度」：`(?<=...[\s\S]*?)` 这类含
     * `*` / `+` / `{n,}` 的组会直接编译失败（Look-behind group does not have an obvious
     * maximum length），进而整条脚本被静默跳过。这里定位每个 `(?<=` / `(?<!` 组
     * （跳过转义与字符类，按括号嵌套配对），把组内不受限量词改写为有界形式：
     * `*?`→`{0,MAX}?`、`*`→`{0,MAX}`、`+?`→`{1,MAX}?`、`+`→`{1,MAX}`、`{n,}`→`{n,MAX}`
     * （同时保留 `?`/`+` 懒惰 / 占有后缀）。
     *
     * @return 改写后的 pattern；无可改写量词或结构畸形时返回 null。
     */
    internal fun degradeUnboundedLookbehind(pattern: String): String? {
        val out = StringBuilder(pattern.length + 16)
        var i = 0
        var changed = false
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> {
                    out.append(c)
                    if (i + 1 < pattern.length) out.append(pattern[i + 1])
                    i += 2
                }
                c == '[' -> {
                    val end = skipCharClass(pattern, i)
                    out.append(pattern, i, end)
                    i = end
                }
                c == '(' && i + 3 < pattern.length &&
                    pattern[i + 1] == '?' && pattern[i + 2] == '<' &&
                    (pattern[i + 3] == '=' || pattern[i + 3] == '!') -> {
                    val end = findMatchingParen(pattern, i)
                    if (end < 0) {
                        out.append(pattern, i, pattern.length)
                        i = pattern.length
                    } else {
                        val body = pattern.substring(i + 4, end)
                        val rewritten = rewriteUnboundedQuantifiers(body)
                        if (rewritten != body) changed = true
                        out.append(pattern, i, i + 4).append(rewritten).append(')')
                        i = end + 1
                    }
                }
                else -> {
                    out.append(c)
                    i += 1
                }
            }
        }
        return if (changed) out.toString() else null
    }

    /** 把 body 内不受限量词改写为 `{min,BOUNDED_LOOKBEHIND_MAX}` 有界形式。 */
    private fun rewriteUnboundedQuantifiers(body: String): String {
        val out = StringBuilder(body.length + 16)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' -> {
                    out.append(c)
                    if (i + 1 < body.length) out.append(body[i + 1])
                    i += 2
                }
                c == '[' -> {
                    val end = skipCharClass(body, i)
                    out.append(body, i, end)
                    i = end
                }
                c == '*' -> {
                    when (body.getOrNull(i + 1)) {
                        '?' -> {
                            out.append("{0,$BOUNDED_LOOKBEHIND_MAX}?")
                            i += 2
                        }
                        '+' -> {
                            out.append("{0,$BOUNDED_LOOKBEHIND_MAX}+")
                            i += 2
                        }
                        else -> {
                            out.append("{0,$BOUNDED_LOOKBEHIND_MAX}")
                            i += 1
                        }
                    }
                }
                c == '+' -> {
                    when (body.getOrNull(i + 1)) {
                        '?' -> {
                            out.append("{1,$BOUNDED_LOOKBEHIND_MAX}?")
                            i += 2
                        }
                        '+' -> {
                            out.append("{1,$BOUNDED_LOOKBEHIND_MAX}+")
                            i += 2
                        }
                        else -> {
                            out.append("{1,$BOUNDED_LOOKBEHIND_MAX}")
                            i += 1
                        }
                    }
                }
                c == '{' -> {
                    val close = body.indexOf('}', i + 1)
                    val inner = if (close > i) body.substring(i + 1, close) else null
                    if (inner != null && UNBOUNDED_QUANTIFIER.matches(inner)) {
                        out.append('{').append(inner.dropLast(1))
                            .append(',').append(BOUNDED_LOOKBEHIND_MAX).append('}')
                        i = close + 1
                        val suffix = body.getOrNull(i)
                        if (suffix == '?' || suffix == '+') {
                            out.append(suffix)
                            i += 1
                        }
                    } else {
                        out.append(c)
                        i += 1
                    }
                }
                else -> {
                    out.append(c)
                    i += 1
                }
            }
        }
        return out.toString()
    }

    /** `{n,}` 形式的无上界量词（[rewriteUnboundedQuantifiers] 用）。 */
    private val UNBOUNDED_QUANTIFIER = Regex("^\\d+,$")

    /** 定位 [openIndex] 处 `(` 的配对 `)`（跳过转义与字符类）；未闭合返回 -1。 */
    private fun findMatchingParen(pattern: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i += 2
                c == '[' -> i = skipCharClass(pattern, i)
                c == '(' -> {
                    depth++
                    i++
                }
                c == ')' -> {
                    depth--
                    if (depth == 0) return i
                    i++
                }
                else -> i++
            }
        }
        return -1
    }

    /** 跳过 `[` 起始的字符类（正确处理转义与首部 `]`）；返回 `]` 之后的索引。 */
    private fun skipCharClass(pattern: String, start: Int): Int {
        var i = start + 1
        if (i < pattern.length && pattern[i] == '^') i++
        if (i < pattern.length && pattern[i] == ']') i++
        while (i < pattern.length) {
            when (pattern[i]) {
                '\\' -> i += 2
                ']' -> return i + 1
                else -> i++
            }
        }
        return pattern.length
    }

    /** 是否全局替换（对齐 ST：`/.../g` 为全局；非 slash 形式按全局，见 PORT NOTE 2） */
    private fun isGlobalPattern(pattern: String): Boolean {
        val slashForm = SLASH_FORM.matchEntire(pattern)
            ?: return true
        return slashForm.groupValues[2].contains('g', ignoreCase = true)
    }

    /** 替换串引用 token：`{{match}}` / `$1` / `$<name>`（对齐 ST replaceAll 正则） */
    private val REPLACEMENT_TOKEN = Regex("\\{\\{match\\}\\}|\\$(\\d+)|\\$<([^>]+)>", RegexOption.IGNORE_CASE)

    /**
     * 对单个脚本执行替换（对齐 ST runRegexScript）。
     *
     * @param substitute 宏替换函数（默认恒等；真实调用传入宏引擎的替换）
     */
    fun runRegexScript(
        script: RegexScript,
        rawString: String,
        substitute: (String) -> String = { it },
    ): String {
        if (script.disabled) return rawString
        if (script.findRegex.isEmpty()) return rawString
        if (rawString.isEmpty()) return rawString

        val pattern = when (script.substituteRegex) {
            SubstituteRegex.RAW -> substitute(script.findRegex)
            SubstituteRegex.ESCAPED -> Regex("\\{\\{[^{}]*\\}\\}").replace(script.findRegex) { m ->
                sanitizeRegexMacro(substitute(m.value))
            }
            else -> script.findRegex
        }
        val regex = compileRegex(pattern) ?: return rawString
        val global = isGlobalPattern(pattern)

        val sb = StringBuilder()
        var cursor = 0
        var matched = false
        for (match in regex.findAll(rawString)) {
            sb.append(rawString, cursor, match.range.first)
            sb.append(buildReplacement(script, match, substitute))
            cursor = match.range.last + 1
            matched = true
            if (!global) break
        }
        if (!matched) return rawString
        sb.append(rawString, cursor, rawString.length)
        return sb.toString()
    }

    /** 组装单个匹配的替换文本（对齐 ST replace 回调 + filterString + 末尾宏替换） */
    private fun buildReplacement(
        script: RegexScript,
        match: MatchResult,
        substitute: (String) -> String,
    ): String {
        // 剔除 trimStrings（先做宏替换；空串跳过，避免 replaceAll("", "") 死循环语义）
        fun filtered(value: String?): String {
            if (value.isNullOrEmpty()) return ""
            var out: String = value
            for (trim in script.trimStrings) {
                val sub = substitute(trim)
                if (sub.isNotEmpty()) out = out.replace(sub, "")
            }
            return out
        }

        val built = REPLACEMENT_TOKEN.replace(script.replaceString) { token ->
            val num = token.groupValues[1]
            val name = token.groupValues[2]
            when {
                token.value.equals("{{match}}", ignoreCase = true) -> filtered(match.value)
                num.isNotEmpty() -> filtered(match.groupValues.getOrNull(num.toIntOrNull() ?: -1))
                name.isNotEmpty() -> filtered((match.groups as? MatchNamedGroupCollection)?.get(name)?.value)
                else -> filtered(match.value)
            }
        }
        // 替换串末尾整体做一次宏替换（对齐 ST：substituteParams(replaceWithGroups)）
        return substitute(built)
    }

    /**
     * 对一段文本应用脚本集合（对齐 ST getRegexedString）。
     *
     * 过滤顺序与 ST 一致：markdownOnly/promptOnly 通道 → isEdit/runOnEdit → depth → placement。
     * 说明（PORT NOTE 1）：无标记脚本在显示与提示词两条通道都应用，对应 ST “既不勾选则两者都作用”的净行为。
     *
     * @param depth 消息深度（离最新消息的距离）；null 表示不参与 depth 过滤
     */
    fun getRegexedString(
        rawString: String,
        scripts: List<RegexScript>,
        placement: Int,
        isMarkdown: Boolean = false,
        isPrompt: Boolean = false,
        isEdit: Boolean = false,
        depth: Int? = null,
        substitute: (String) -> String = { it },
    ): String {
        if (rawString.isEmpty()) return rawString
        var finalString = rawString
        for (script in scripts) {
            val applies = (script.markdownOnly && isMarkdown) ||
                (script.promptOnly && isPrompt) ||
                (!script.markdownOnly && !script.promptOnly)
            if (!applies) continue
            if (isEdit && !script.runOnEdit) continue
            if (depth != null) {
                val min = script.minDepth
                if (min != null && min >= -1 && depth < min) continue
                val max = script.maxDepth
                if (max != null && max >= 0 && depth > max) continue
            }
            if (placement !in script.placement) continue
            finalString = runRegexScript(script, finalString, substitute)
        }
        return finalString
    }

    /**
     * 正则转义宏替换值（对齐 ST sanitizeRegexMacro）：
     * 换行等转义序列化，其余正则元字符加反斜杠。
     */
    fun sanitizeRegexMacro(value: String): String {
        if (value.isEmpty()) return value
        val sb = StringBuilder(value.length * 2)
        for (c in value) {
            when (c) {
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u000B' -> sb.append("\\v")
                '\u000C' -> sb.append("\\f")
                '\u0000' -> sb.append("\\0")
                '.', '^', '$', '*', '+', '?', '{', '}', '[', ']', '\\', '/', '|', '(', ')' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }
}

/**
 * 便捷扩展：对文本应用脚本集合（无宏替换器的轻量通道，如显示渲染 / 世界书扫描）。
 */
fun String.getStRegexed(
    scripts: List<RegexScript>,
    placement: Int,
    isMarkdown: Boolean = false,
    isPrompt: Boolean = false,
    depth: Int? = null,
): String {
    if (scripts.isEmpty()) return this
    return RegexScriptEngine.getRegexedString(
        rawString = this,
        scripts = scripts,
        placement = placement,
        isMarkdown = isMarkdown,
        isPrompt = isPrompt,
        depth = depth,
    )
}
