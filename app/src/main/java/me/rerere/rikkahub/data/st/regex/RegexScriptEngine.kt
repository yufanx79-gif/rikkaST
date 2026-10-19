package me.rerere.rikkahub.data.st.regex

/**
 * ST 1.19 正则脚本引擎移植与增强
 *
 * 对齐 public/scripts/extensions/regex/engine.js：
 * - [runRegexScript] / [getRegexedString] / filterString / substitute_find_regex / sanitizeRegexMacro
 * - 替换串支持 `{{match}}`（= $0）、`$1..$99`、`$<name>`；分组引用值会先剔除 trimStrings
 * - 替换串最后整体做一次宏替换（对齐 ST 末尾 substituteParams(replaceWithGroups)）
 * - 防御性增强：
 *   1. 对非专门匹配 `<think>`/`<thinking>`/`<thought>` 的脚本，自动保护思维链块不被立绘/CG 等正文正则穿透误匹配；
 *   2. 当反引号 `` `...` `` 闭合边界因前文不匹配代码段导致跨行吞噬整段文本（如 `` ` 格式：... - `织乃_zcw_2` ``）时，
 *      若末尾最后一对反引号子串本身即完整匹配该正则，自动重锚定到末尾真实 token，保留前文不被吞噬。
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

    private val JS_REGEX_OPTIONS = setOf(RegexOption.UNIX_LINES)

    private val SLASH_FORM = Regex("^/(.*)/([a-zA-Z]*)$", RegexOption.DOT_MATCHES_ALL)

    private val THINK_BLOCK_SHIELD_REGEX = Regex(
        "<(think|thinking|thought)\\b[^>]*>[\\s\\S]*?(?:</\\1>|$)",
        RegexOption.IGNORE_CASE,
    )

    private fun compileRegexUncached(pattern: String): Regex {
        val slashForm = SLASH_FORM.matchEntire(pattern)
            ?: return Regex(pattern, JS_REGEX_OPTIONS)
        var options = JS_REGEX_OPTIONS
        for (c in slashForm.groupValues[2].lowercase()) {
            options = when (c) {
                'i' -> options + RegexOption.IGNORE_CASE
                'm' -> options + RegexOption.MULTILINE
                's' -> options + RegexOption.DOT_MATCHES_ALL
                else -> options
            }
        }
        return Regex(slashForm.groupValues[1], options)
    }

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

    private val UNBOUNDED_QUANTIFIER = Regex("^\\d+,$")

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

    private fun isGlobalPattern(pattern: String): Boolean {
        val slashForm = SLASH_FORM.matchEntire(pattern)
            ?: return true
        return slashForm.groupValues[2].contains('g', ignoreCase = true)
    }

    private val REPLACEMENT_TOKEN = Regex("\\{\\{match\\}\\}|\\$\\$|\\$&|\\$(\\d+)|\\$<([^>]+)>", RegexOption.IGNORE_CASE)

    /**
     * 对单个脚本执行替换（对齐 ST runRegexScript）。
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

        // 若该脚本并非专门处理 <think>/<thinking>/<thought> 标签，但输入文本中含有未剥离的思维链块，
        // 则先用占位符屏蔽思维链块，防止立绘/CG 等正文正则在思维链内部跨行误匹配。
        val scriptTargetsThinking = pattern.contains("think", ignoreCase = true) ||
            pattern.contains("thought", ignoreCase = true) ||
            (RegexPlacement.REASONING in script.placement && script.placement.size == 1)
        val shieldedBlocks = mutableListOf<String>()
        val workingInput = if (!scriptTargetsThinking &&
            (rawString.contains("<think", ignoreCase = true) || rawString.contains("<thought", ignoreCase = true))
        ) {
            THINK_BLOCK_SHIELD_REGEX.replace(rawString) { m ->
                val idx = shieldedBlocks.size
                shieldedBlocks.add(m.value)
                "\u0000__RIKKA_THINK_SHIELD_${idx}__\u0000"
            }
        } else {
            rawString
        }

        val sb = StringBuilder()
        var cursor = 0
        var matched = false
        for (match in regex.findAll(workingInput)) {
            sb.append(workingInput, cursor, match.range.first)
            val mVal = match.value
            // 防御反引号跨行误吞噬：例如前文有 `无关代码` 闭合反引号，后文有 `织乃_zcw_2`，
            // 当正则写为 /`(.+?_zcw_\d+)`/gs 时，match.value 会从前一个闭合反引号一直跨行吞到 `织乃_zcw_2`。
            // 若 match.value 首尾均为反引号且内部还包含反引号，检查从最后一个内部反引号开始的子串是否独立完整匹配该正则；
            // 若匹配，则保留被误吞的前缀文本，仅对末尾真实反引号 token 执行替换。
            // 防御 1：若 match 跨过了思维链保护占位符（ __RIKKA_THINK_SHIELD_N__ ），重锚定到最后一个占位符之后
            if (mVal.contains("\u0000__RIKKA_THINK_SHIELD_")) {
                val lastShieldEnd = mVal.lastIndexOf("__\u0000")
                if (lastShieldEnd >= 0) {
                    val afterShieldIdx = lastShieldEnd + 3
                    val tailAfterShield = mVal.substring(afterShieldIdx)
                    val subMatch = regex.find(tailAfterShield)
                    if (subMatch != null) {
                        sb.append(mVal, 0, afterShieldIdx + subMatch.range.first)
                        sb.append(buildReplacement(script, subMatch, substitute))
                        sb.append(tailAfterShield, subMatch.range.last + 1, tailAfterShield.length)
                        cursor = match.range.last + 1
                        matched = true
                        if (!global) break
                        continue
                    }
                }
            }
            // 防御 2：当贪婪正则把同一消息内的多对反引号（如 `无关代码` ... `织乃_zcw_2`，或两张独立 CG `织乃_zcw_1` ... `织乃_zcw_2`）
            // 吞入同一个 match 时，逐个扫描内部独立的 `[^`]+` 反引号对：凡独立匹配该正则的反引号对均各自执行替换，其余原样保留！
            if (mVal.length > 3 && mVal.first() == '`' && mVal.last() == '`' && mVal.indexOf('`', 1) < mVal.lastIndex) {
                val pairRegex = Regex("`[^`\\r\\n]+`")
                val pairMatches = pairRegex.findAll(mVal).toList()
                if (pairMatches.any { pm -> regex.matchEntire(pm.value) != null }) {
                    var localCur = 0
                    for (pm in pairMatches) {
                        sb.append(mVal, localCur, pm.range.first)
                        val exactPairMatch = regex.matchEntire(pm.value)
                        if (exactPairMatch != null) {
                            sb.append(buildReplacement(script, exactPairMatch, substitute))
                        } else {
                            sb.append(pm.value)
                        }
                        localCur = pm.range.last + 1
                    }
                    sb.append(mVal, localCur, mVal.length)
                    cursor = match.range.last + 1
                    matched = true
                    if (!global) break
                    continue
                }
            }
            sb.append(buildReplacement(script, match, substitute))
            cursor = match.range.last + 1
            matched = true
            if (!global) break
        }
        if (!matched) return rawString
        sb.append(workingInput, cursor, workingInput.length)
        var out = sb.toString()
        if (shieldedBlocks.isNotEmpty()) {
            shieldedBlocks.forEachIndexed { idx, originalBlock ->
                out = out.replace("\u0000__RIKKA_THINK_SHIELD_${idx}__\u0000", originalBlock)
            }
        }
        return out
    }

    /** 组装单个匹配的替换文本（对齐 ST replace 回调 + filterString + 末尾宏替换） */
    private fun buildReplacement(
        script: RegexScript,
        match: MatchResult,
        substitute: (String) -> String,
    ): String {
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
                token.value == "$$" -> "$"
                token.value == "$&" || token.value.equals("{{match}}", ignoreCase = true) -> filtered(match.value)
                num.isNotEmpty() -> filtered(match.groupValues.getOrNull(num.toIntOrNull() ?: -1))
                name.isNotEmpty() -> filtered((match.groups as? MatchNamedGroupCollection)?.get(name)?.value)
                else -> filtered(match.value)
            }
        }
        return substitute(built)
    }

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
