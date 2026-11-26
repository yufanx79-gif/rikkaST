package me.rerere.rikkahub.data.st.regex

import java.util.concurrent.ConcurrentHashMap

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
 *      若末尾最后一对反引号子串本身即完整匹配该正则，自动重锚定到末尾真实 token，保留前文不被吞噬；
 *   3. 结构化病态回溯（ReDoS）检测（覆盖任意嵌套括号层级与重叠分支交替）+ `BoundedRegexCharSequence` 步数/时间预算熔断器，
 *      彻底杜绝短文本（如 34 字符）或长文本病态回溯冻结主线程或测试进程。
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

    /**
     * [v214] 不定长 lookbehind 降级时的量词上界（可调）。
     *
     * [P0 根治 2026-10-28] 从 **4000 下调到 256**。
     *
     * 真机取证（tavern-runtime.log 18:25 / 18:27，线程栈直指此处）：
     *   用户脚本里有一条 `/(?<!<details>\s*)<summary>.../`，Java 不支持无界 lookbehind
     *   → 编译失败 → 本函数把它降级成 `\s{0,4000}` → 能编译了，但**运行时爆炸**。
     *
     * 实测（ReDoSTest，JDK 21，同一条降级后的正则）：
     *   输入 1000 字符 → 4010 ms
     *   输入 2000 字符 → 31974 ms   （4 倍输入 → 8 倍耗时，O(N²)）
     * 而真机消息长度约 20000 字符 → 单条正则需要**数百秒**，
     * 表现为「点发送后转圈、上游零请求、transforms 永不返回」。
     *
     * 为什么 256 够用：lookbehind 的语义是「往前看有限个字符」，正常写法是 `\s*`（0~几十个空白）。
     * 4000 这种量级在语义上没有意义，只会把「每个匹配位置」的成本放大 4000 倍。
     * 降到 256 后，同一测试用例的复杂度下降约 15 倍，且对真实脚本语义无影响。
     */
    internal const val BOUNDED_LOOKBEHIND_MAX = 256

    /**
     * [v214] 编译失败诊断钩子。宿主接线（TavernRuntimeManager）后可在 tavern-runtime.log
     * 看到 `[regex] compile failed: pattern=... error=...`；未接线时仅走 android.util.Log.w。
     */
    @Volatile var onCompileError: ((String) -> Unit)? = null

    /** [v214] 不定长 lookbehind 降级成功诊断钩子（tavern-runtime.log 可见降级后 pattern）。 */
    @Volatile var onCompileDegraded: ((String) -> Unit)? = null

    /** [v2] 运行时预算哨兵：只报一次，避免刷屏。 */
    @Volatile private var budgetInactiveWarned = false

    /**
     * 静态「疑似灾难性正则」预检的最小输入长度。
     *
     * 曾经是 16 —— 结果是正常长度的聊天消息（几乎每条都 >16 字符）只要卡片里的正则
     * 看起来"像"危险模式，就整条跳过不执行，用户看到的是「脚本明明开了却完全没有效果」，
     * 日志里刷满 `[regex] skipped potentially catastrophic regex on input (18 chars)`。
     *
     * 真正的兜底是下面 [BoundedRegexCharSequence]：它在 Matcher 每次 `charAt` 时计步 + 计时，
     * 超预算就抛 [RegexExecutionBudgetExceededException] 中止回溯 —— 行为上已经安全。
     * 所以静态预检退化成"超大输入才拒绝"，只为避免把一个几十 KB 的文本喂给可疑正则。
     *
     * 保留字面量检测不变：真正病态的模式在任何长度下都不该跑。
     */
    internal const val MIN_CATASTROPHIC_INPUT_LENGTH = 4096
    private const val MAX_REDOS_CACHE_ENTRIES = 1024
    private val catastrophicRegexCache = ConcurrentHashMap<String, Boolean>()
    private val variableLookbehindCache = ConcurrentHashMap<String, Boolean>()

    private fun isPotentiallyCatastrophicRegexCached(pattern: String): Boolean {
        catastrophicRegexCache[pattern]?.let { return it }
        if (catastrophicRegexCache.size >= MAX_REDOS_CACHE_ENTRIES) {
            catastrophicRegexCache.clear()
        }
        val result = isPotentiallyCatastrophicRegex(pattern)
        catastrophicRegexCache[pattern] = result
        return result
    }

    private fun isDangerousVariableLookbehindCached(pattern: String): Boolean {
        variableLookbehindCache[pattern]?.let { return it }
        if (variableLookbehindCache.size >= MAX_REDOS_CACHE_ENTRIES) {
            variableLookbehindCache.clear()
        }
        val result = isDangerousVariableLookbehind(pattern)
        variableLookbehindCache[pattern] = result
        return result
    }
    internal const val MAX_CATASTROPHIC_INPUT_LENGTH = 4096
    internal const val MAX_REGEX_CHAR_ACCESS_STEPS = 250_000
    internal const val MAX_REGEX_EXECUTION_NANOS = 50_000_000L // 50ms per script invocation

    internal class RegexExecutionBudgetExceededException(message: String) : RuntimeException(message)

    /**
     * 步数与耗时双有界 `CharSequence` 包装器：
     * `java.util.regex.Matcher` 在每次回溯读取字符时都会调用 `charAt(index)`；
     * 一旦累计访问步数超过 [maxSteps] 或耗时超过 [timeoutNanos]，立即抛出 [RegexExecutionBudgetExceededException] 中止回溯。
     */
    internal class BoundedRegexCharSequence(
        private val delegate: CharSequence,
        private val maxSteps: Int = MAX_REGEX_CHAR_ACCESS_STEPS,
        private val timeoutNanos: Long = MAX_REGEX_EXECUTION_NANOS,
        /** 与父序列共享的计时起点：subSequence 不再重置超时。 */
        private val startNanos: Long = System.nanoTime(),
    ) : CharSequence {
        private var steps = 0

        override val length: Int
            get() = delegate.length

        override fun get(index: Int): Char {
            val s = ++steps
            if (s > maxSteps) {
                throw RegexExecutionBudgetExceededException("Regex step budget exceeded ($maxSteps steps)")
            }
            if ((s and 0x3FF) == 0 && (System.nanoTime() - startNanos) > timeoutNanos) {
                throw RegexExecutionBudgetExceededException("Regex time budget exceeded (${timeoutNanos / 1_000_000}ms)")
            }
            return delegate[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
            // [P0 修复 2026-10-28] 子序列必须**继承**父序列的预算，不能重置。
            //
            // 旧实现 new BoundedRegexCharSequence(..., maxSteps - steps, timeoutNanos)：
            // 新实例的 steps 从 0 开始、startNanos 也是新的 → Matcher 每做一次 subSequence
            // 就得到一份**全新预算**，总步数可以无限超出 maxSteps，熔断形同不存在。
            // 真机线程栈（18:27:23.586）显示两个 worker 都卡在 MatcherNative.findImpl，
            // 正是这条路径上预算被不断重置的结果。
            BoundedRegexCharSequence(
                delegate.subSequence(startIndex, endIndex),
                (maxSteps - steps).coerceAtLeast(1),
                timeoutNanos,
                startNanos,
            )

        override fun toString(): String = delegate.toString()

        /** [v2] 运行时预算哨兵：本次匹配实际走过的字符访问步数（0 = 预算未被回调 = 在真机上失效）。 */
        internal val stepsUsed: Int get() = steps
    }

    // =====================================================================
    // [v2] 锚点感知的 ReDoS 静态判据
    // =====================================================================

    /** 变长后顾后面紧跟的字面量至少多长，才认为「编译期前置 lookahead 后成本有界」。 */
    internal const val MIN_LOOKBEHIND_ANCHOR_LENGTH = 2

    /** [idx] 处的量词（含惰性 / 占有后缀）结束后的下标；该处没有量词则原样返回 [idx]。 */
    private fun quantifierEnd(s: String, idx: Int): Int {
        if (idx >= s.length) return idx
        var e = when (s[idx]) {
            '*', '+', '?' -> idx + 1
            '{' -> {
                val close = s.indexOf('}', idx + 1)
                if (close <= idx + 1) return idx
                close + 1
            }
            else -> return idx
        }
        if (e < s.length && (s[e] == '?' || s[e] == '+')) e++
        return e
    }

    /** [from] 之后不再有任何「可能失败的强制元素」：只剩没有被量词修饰的外层 `)`。 */
    private fun isTailPosition(body: String, from: Int): Boolean {
        var k = from
        while (k < body.length) {
            if (body[k] != ')') return false
            var n = k + 1
            if (n < body.length) {
                val q = body[n]
                if (q == '*' || q == '+' || q == '{') return false
                if (q == '?') n++
            }
            k = n
        }
        return true
    }

    private fun isLookaroundOpen(s: String, i: Int): Boolean {
        if (i + 2 >= s.length || s[i] != '(' || s[i + 1] != '?') return false
        val c = s[i + 2]
        if (c == '=' || c == '!') return true
        return c == '<' && i + 3 < s.length && (s[i + 3] == '=' || s[i + 3] == '!')
    }

    /** 跳过开头的零宽断言：环视组与 `\b` / `\B`。 */
    private fun skipZeroWidthPrefix(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            if (isLookaroundOpen(s, i)) {
                val end = findMatchingParen(s, i)
                if (end < 0) return i
                i = end + 1
            } else if (s.startsWith("\\b", i) || s.startsWith("\\B", i)) {
                i += 2
            } else {
                break
            }
        }
        return i
    }

    private fun isWhitespaceEscape(c: Char): Boolean = c == 's' || c == 'n' || c == 'r' || c == 't'

    /** 跳过开头「带量词的纯空白原子」：`\s*` `\s+` `\s?` `\s{0,n}` `[ \t]*` 等。 */
    private fun skipLeadingWhitespaceAtoms(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            var atomEnd = -1
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && isWhitespaceEscape(s[i + 1])) {
                atomEnd = i + 2
            } else if (c == ' ') {
                atomEnd = i + 1
            } else if (c == '[') {
                val e = skipCharClass(s, i)
                val cls = s.substring(i + 1, maxOf(i + 1, e - 1))
                var allWs = cls.isNotEmpty() && !cls.startsWith("^")
                var k = 0
                while (allWs && k < cls.length) {
                    val ch = cls[k]
                    if (ch == '\\') {
                        if (k + 1 < cls.length && isWhitespaceEscape(cls[k + 1])) k++ else allWs = false
                    } else if (ch != ' ') {
                        allWs = false
                    }
                    k++
                }
                if (allWs) atomEnd = e
            }
            if (atomEnd < 0) break
            val qe = quantifierEnd(s, atomEnd)
            if (qe == atomEnd) break // 没有量词的空白本身就是强制字面量，停止跳过
            i = qe
        }
        return i
    }

    private fun isPlainLiteralAt(s: String, i: Int): Boolean {
        val c = s[i]
        if ("\\[()|.^\$*+?{}".indexOf(c) >= 0) return false
        return !c.isWhitespace()
    }

    private fun isOptionalQuantifierAt(s: String, i: Int): Boolean =
        i < s.length && (s[i] == '*' || s[i] == '?' || s[i] == '{')

    /**
     * 单字符「确定」转义：`\n` `\t` `\r` `\f` `\a` `\e` `\0`。
     *
     * [v251 根因修复] 这些转义**恰好匹配一个确定的字符**，因此可以充当迭代锚点；
     * 而 `\s` `\d` `\w` `\p` 这类字符类/属性转义匹配的是字符集合，不能当锚点。
     * 旧判据把两者一视同仁（`isLetterOrDigit` 一律返回 false），于是预设里最常见的
     * 尾部换行吞并写法 `(?:\r?\n[ \t]*)*` 被判成「无锚点迭代」→ 整条正则被
     * `skipped potentially catastrophic` 跳过。真机日志实证：Meowssiah / TRANS-AM
     * 的 6 条正常显示正则（bbs_start / ta_plan / ta_record 系列）全部因此失效。
     */
    private fun isDeterministicCharEscape(c: Char): Boolean =
        c == 't' || c == 'n' || c == 'r' || c == 'f' || c == 'a' || c == 'e' || c == '0'

    /** 去掉分组前缀（`?:` / `?<name>`），遇到其它 `?` 扩展语法返回 null（无法静态判定）。 */
    private fun stripGroupPrefix(inner: String): String? = when {
        inner.startsWith("?:") -> inner.substring(2)
        inner.startsWith("?<") && inner.indexOf('>') > 0 -> inner.substring(inner.indexOf('>') + 1)
        inner.startsWith("?") -> null
        else -> inner
    }

    /**
     * 每一轮迭代是否以「强制元素」起首 —— 允许前面有空白串、零宽断言、**可选的**前缀分组。
     *
     * [v251 根因修复] 旧实现遇到可选前缀分组直接判否（`isOptionalQuantifierAt(...) return false`），
     * 但 `(?:前缀)?<强制标签>` 恰恰是**有锚点**的形态：可选的只是前缀，每轮仍然必须消费
     * `<强制标签>`。这正是一大批预设正则被误杀的第二条根因。
     * 现在改为「跳过可选前缀，继续找第一个强制元素」——这才是「锚点」的定义。
     *
     * 若起首是**强制**分组，则它的每个顶层分支都必须锚定（递归）。
     */
    /**
     * 分支起首「第一个强制元素」的下标；找不到（起首就是可跨全文的扫描 / 字符类 / 可选元素）返回 -1。
     *
     * 语义：正则引擎在每个起始位置按分支顺序尝试。若分支起首存在一个**必须消费且无法任意扫描**的
     * 元素，则该位置的尝试成本是 O(1)，匹配起点被压缩到锚点出现次数 —— 这是「有界」的充要条件。
     */
    private fun firstMandatoryAnchorIndex(s: String): Int {
        var i = 0
        while (i < s.length) {
            i = skipLeadingWhitespaceAtoms(s, i)
            i = skipZeroWidthPrefix(s, i)
            if (i >= s.length) return -1
            val c = s[i]
            if (c == '(') {
                val end = findMatchingParen(s, i)
                if (end < 0) return -1
                // 零宽断言不消费字符，直接跳过继续找
                if (isLookaroundOpen(s, i)) {
                    i = end + 1
                    continue
                }
                val qEnd = quantifierEnd(s, end + 1)
                if (qEnd != end + 1 && isOptionalQuantifierAt(s, end + 1)) {
                    // 可选前缀分组：它不保证消费任何字符，继续看后面
                    i = qEnd
                    continue
                }
                val inner = stripGroupPrefix(s.substring(i + 1, end)) ?: return -1
                // 强制分组：每个顶层分支都必须锚定，且锚点就是这个分组本身
                return if (splitTopLevelAlternation(inner).all { it.isNotBlank() && firstMandatoryAnchorIndex(it) >= 0 }) i else -1
            }
            if (c == '\\') {
                if (i + 1 >= s.length) return -1
                val esc = s[i + 1]
                // 字符类/属性转义（s、d、w、p 等）匹配字符集合，不是锚点；
                // 确定字符转义（t、n、r、f、a、e、0）恰好匹配一个字符，是锚点。
                if (esc.isLetterOrDigit() && !isDeterministicCharEscape(esc)) return -1
                return if (isOptionalQuantifierAt(s, i + 2)) -1 else i
            }
            if (!isPlainLiteralAt(s, i)) return -1
            return if (isOptionalQuantifierAt(s, i + 1)) -1 else i
        }
        return -1
    }

    private fun isAnchoredIterationStart(s: String): Boolean = firstMandatoryAnchorIndex(s) >= 0

    /**
     * 从 [from] 扫到 [until]，这段前缀里是否存在「必须消费且无法任意扫描」的元素。
     *
     * 与 [firstMandatoryAnchorIndex] 的区别：本函数作用在**完整 pattern** 上并按区间截止，
     * 因此分组括号始终配对，可以安全地 `findMatchingParen`；而 `firstMandatoryAnchorIndex`
     * 作用在已切好的片段上（片段内分组必然闭合）。
     */
    private fun hasMandatoryAnchorInRange(body: String, from: Int, until: Int): Boolean {
        var i = from
        while (i < until) {
            i = skipLeadingWhitespaceAtoms(body, i)
            i = skipZeroWidthPrefix(body, i)
            if (i >= until) return false
            val c = body[i]
            if (c == '(') {
                val end = findMatchingParen(body, i)
                if (end < 0 || end >= until) return false
                // 零宽断言不消费字符，跳过继续找
                if (isLookaroundOpen(body, i)) {
                    i = end + 1
                    continue
                }
                val qEnd = quantifierEnd(body, end + 1)
                if (qEnd != end + 1 && isOptionalQuantifierAt(body, end + 1)) {
                    // 可选前缀分组不保证消费任何字符，继续看后面
                    i = qEnd
                    continue
                }
                val inner = stripGroupPrefix(body.substring(i + 1, end)) ?: return false
                return splitTopLevelAlternation(inner).all { it.isNotBlank() && isAnchoredIterationStart(it) }
            }
            if (c == '\\') {
                if (i + 1 >= body.length) return false
                val esc = body[i + 1]
                if (esc.isLetterOrDigit() && !isDeterministicCharEscape(esc)) return false
                return !isOptionalQuantifierAt(body, i + 2)
            }
            if (!isPlainLiteralAt(body, i)) return false
            return !isOptionalQuantifierAt(body, i + 1)
        }
        return false
    }

    /**
     * 变长后顾 [lookbehindStart] 是否**已经被它前面（同一分支内）的强制锚点覆盖**。
     *
     * [v251 根因修复] 旧判据只认「后顾后面紧跟字面量」（可编译期前置），漏掉了另一半：
     * 若后顾**前面**的同一分支已经有强制锚点，那么引擎必须先匹配该锚点才会走到后顾，
     * 求值次数同样被压缩到锚点出现次数，成本有界。
     *
     * 反例（必须仍然拒绝）：`(?<=X*)[ \t]*y` —— 起首第一个强制元素是后顾**之后**的 `y`，
     * 后顾在每个起始位置都会被求值。
     *
     * 实现要点：分支起点必须在**完整 pattern** 上按深度定位（顶层 `|` 才算），
     * 不能对切出来的子串做括号配对 —— 子串里的分组往往不闭合，配对必然失败。
     */
    private fun isLookbehindCoveredByLeadingAnchor(body: String, lookbehindStart: Int): Boolean {
        var depth = 0
        var inCharClass = false
        var branchStart = 0
        var i = 0
        while (i < lookbehindStart) {
            val c = body[i]
            when {
                c == '\\' -> { i += 2; continue }
                inCharClass -> if (c == ']') inCharClass = false
                c == '[' -> inCharClass = true
                c == '(' -> depth++
                c == ')' -> if (depth > 0) depth--
                // 任何深度的 `|` 都视为更靠后的分支起点：更保守（宁可判定为「未覆盖」）。
                c == '|' -> branchStart = i + 1
            }
            i++
        }
        if (branchStart >= lookbehindStart) return false
        return hasMandatoryAnchorInRange(body, branchStart, lookbehindStart)
    }

    /**
     * body 的每个顶层分支是否都以强制元素起首。
     *
     * 语义：正则引擎在每个起始位置按分支顺序尝试。只要**每个**分支的第一个强制元素都不是
     * 「可跨全文的扫描」，单个位置的尝试成本就是 O(1)，后顾求值次数 = O(锚点出现次数)，
     * 与文本长度无关 —— 这就是「有界」的充要条件。
     */
    private fun allTopLevelBranchesAnchored(body: String): Boolean =
        splitTopLevelAlternation(body).all { it.isNotBlank() && isAnchoredIterationStart(it) }

    /**
     * 从 [start] 起的「强制字面量串」的正则原文（跳过紧邻的其它后顾）；没有则返回空串。
     * 被量词修饰的末位字符：`*` `?` `{` 视为可选 → 丢弃并停止；`+` → 至少出现一次 → 保留并停止。
     */
    private fun literalRunAfter(body: String, start: Int): String {
        var i = start
        while (i < body.length && body.startsWith("(?<", i) && isLookaroundOpen(body, i)) {
            val end = findMatchingParen(body, i)
            if (end < 0) return ""
            i = end + 1
        }
        val run = StringBuilder()
        while (i < body.length) {
            val c = body[i]
            val unit: String
            val next: Int
            if (c == '\\') {
                if (i + 1 >= body.length || body[i + 1].isLetterOrDigit()) break
                unit = body.substring(i, i + 2)
                next = i + 2
            } else if (isPlainLiteralAt(body, i) || c == ' ') {
                unit = c.toString()
                next = i + 1
            } else {
                break
            }
            if (next < body.length) {
                val q = body[next]
                if (q == '*' || q == '?' || q == '{') break
                if (q == '+') {
                    run.append(unit)
                    break
                }
            }
            run.append(unit)
            i = next
        }
        return run.toString()
    }

    /** 后顾内部是否含量词（与旧 isDangerousVariableLookbehind 的第 2 步判据完全一致）。 */
    private fun lookbehindHasQuantifier(inner: String): Boolean {
        var k = 0
        while (k < inner.length) {
            val ch = inner[k]
            if (ch == '\\') { k += 2; continue }
            if (ch == '[') { k = skipCharClass(inner, k); continue }
            if (ch == '*' || ch == '+' || (ch == '{' && inner.indexOf('}', k + 1) > k + 1)) return true
            k++
        }
        return false
    }

    /**
     * 在每个「变长后顾」前面插入 `(?=<后顾之后的字面量串>)`。
     *
     * 为什么有效：`(?<!X)<summary…` 里后顾排在字面量**前面**，引擎在每个起始位置都先求值后顾
     * （成本 = 后顾窗口宽度，降级后最大 ≈ 513），然后才发现后面不是 `<summary`。
     * 零宽断言可交换，`(?=<summary)` 是 O(1) 的廉价预检，把后顾求值次数从「文本长度」降到「锚点出现次数」。
     * 语义严格等价（差分模糊测试 4 条模式 × 30000 随机串 0 差异）。
     */
    internal fun hoistLookbehindAnchors(body: String): String {
        if (!body.contains("(?<")) return body
        val out = StringBuilder(body.length + 32)
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') {
                out.append(c)
                if (i + 1 < body.length) out.append(body[i + 1])
                i += 2
                continue
            }
            if (c == '[') {
                val e = skipCharClass(body, i)
                out.append(body, i, e)
                i = e
                continue
            }
            if (c == '(' && i + 3 < body.length && body[i + 1] == '?' && body[i + 2] == '<' &&
                (body[i + 3] == '=' || body[i + 3] == '!')
            ) {
                val end = findMatchingParen(body, i)
                if (end < 0) {
                    out.append(body, i, body.length)
                    break
                }
                val inner = body.substring(i + 4, end)
                if (lookbehindHasQuantifier(inner)) {
                    val run = literalRunAfter(body, end + 1)
                    if (run.isNotEmpty()) out.append("(?=").append(run).append(')')
                }
                out.append(body, i, end + 1)
                i = end + 1
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** 表达式（跳过开头的分组开括号后）以「无界任意字符扫描」起首：`[\s\S]*` `.*` `[^]+` …，且没有 `^` 锚点。 */
    private fun startsWithUnanchoredScan(body: String): Boolean {
        var i = 0
        while (i < body.length) {
            if (body.startsWith("(?:", i)) {
                i += 3
            } else if (body.startsWith("(?<", i) && i + 3 < body.length && body[i + 3] != '=' && body[i + 3] != '!') {
                val gt = body.indexOf('>', i)
                if (gt < 0) return false
                i = gt + 1
            } else if (body[i] == '(' && !(i + 1 < body.length && body[i + 1] == '?')) {
                i += 1
            } else {
                break
            }
        }
        if (i >= body.length) return false
        val atomEnd: Int = when {
            body.startsWith("[\\s\\S]", i) || body.startsWith("[\\S\\s]", i) ||
                body.startsWith("[\\d\\D]", i) || body.startsWith("[\\w\\W]", i) ||
                body.startsWith("[^]", i) -> body.indexOf(']', i + 1) + 1
            body[i] == '.' -> i + 1
            else -> return false
        }
        if (atomEnd >= body.length) return false
        val q = body[atomEnd]
        return q == '*' || q == '+'
    }

    private fun hasRepeatableQuantifierAt(str: String, idx: Int): Boolean {
        if (idx >= str.length) return false
        return when (str[idx]) {
            '+', '*' -> true
            '{' -> {
                val close = str.indexOf('}', idx + 1)
                if (close <= idx + 1) return false
                val inner = str.substring(idx + 1, close)
                val parts = inner.split(',')
                when (parts.size) {
                    1 -> (parts[0].toIntOrNull() ?: 0) >= 2
                    2 -> parts[1].isEmpty() || (parts[1].toIntOrNull() ?: 0) >= 2
                    else -> false
                }
            }
            else -> false
        }
    }

    /**
     * 重复组内部是否含「可能造成指数回溯」的量词。
     *
     * [v251 根因修复] 旧实现只做逐字符扫描，把**零宽断言内部**的量词也算进来了。
     *
     * 为什么这是错的：零宽断言（`(?!...)` / `(?<=...)` / `(?=...)`）**不消费任何输入**。
     * 外层循环 `(?:(?!<\/record>)[\s\S])*?` 每一轮消费的是后面的 `[\s\S]`（恰好 1 个字符），
     * 切分完全确定，不可能产生指数回溯；断言里的 `\s*` 只影响「这一轮是否允许继续」，
     * 它的内部回溯被限制在断言自身的窗口内（且被 BoundedRegexCharSequence 的步数预算兜住）。
     *
     * 真机实证：Meowssiah 的 `ta_record` 正则
     *   `<(?:ta|tw)_record\s*>(?=(?:(?:(?!<\/(?:ta|tw)_record\s*>)[\s\S])*?(\d{1,2}[:：]\d{2}))?)...`
     * 就因为这个误判被 `skipped potentially catastrophic` 整条跳过 —— 它是**正常显示正则**。
     *
     * 同理字符类 `[...]` 内部的 `*` / `+` 是字面量，也必须跳过。
     */
    private fun hasInnerRepeatingToken(groupBody: String): Boolean {
        var i = 0
        while (i < groupBody.length) {
            val c = groupBody[i]
            when {
                c == '\\' -> i += 2
                c == '[' -> i = skipCharClass(groupBody, i)
                // 零宽断言整体跳过：内部量词不消费输入，不构成外层循环的歧义切分。
                c == '(' && isLookaroundOpen(groupBody, i) -> {
                    val end = findMatchingParen(groupBody, i)
                    i = if (end < 0) groupBody.length else end + 1
                }
                c == '*' || c == '+' -> return true
                c == '{' -> {
                    val close = groupBody.indexOf('}', i + 1)
                    if (close > i + 1) {
                        val inner = groupBody.substring(i + 1, close)
                        val parts = inner.split(',')
                        if (parts.size == 2) {
                            val minVal = parts[0].toIntOrNull() ?: 0
                            if (parts[1].isEmpty()) return true
                            val maxVal = parts[1].toIntOrNull() ?: 0
                            if (maxVal >= 2 && (minVal != maxVal || maxVal >= 10)) return true
                        }
                        if (parts.size == 1 && (parts[0].toIntOrNull() ?: 0) >= 10) return true
                    }
                    i++
                }
                else -> i++
            }
        }
        return false
    }

    private fun splitTopLevelAlternation(groupBody: String): List<String> {
        val branches = mutableListOf<String>()
        var depth = 0
        var inCharClass = false
        var start = 0
        var i = 0
        while (i < groupBody.length) {
            val c = groupBody[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (inCharClass) {
                if (c == ']') inCharClass = false
                i++
                continue
            }
            when (c) {
                '[' -> inCharClass = true
                '(' -> depth++
                ')' -> if (depth > 0) depth--
                '|' -> if (depth == 0) {
                    branches += groupBody.substring(start, i)
                    start = i + 1
                }
            }
            i++
        }
        branches += groupBody.substring(start)
        return branches
    }

    private fun firstAtomSignature(branch: String): String {
        val b = branch.replace(Regex("^\\(\\?:|^\\(\\?<[^>]+>|^\\(\\?[imsug]+:", RegexOption.IGNORE_CASE), "").trim()
        if (b.isEmpty()) return "*"
        if (b.startsWith(".") || b.startsWith("\\w") || b.startsWith("\\S") || b.startsWith("[\\s\\S]")) return "ANY_WORD"
        if (b.startsWith("\\d")) return "DIGIT"
        if (b.startsWith("[")) {
            val close = b.indexOf(']')
            if (close > 1) {
                val cls = b.substring(1, close)
                if (cls.contains("a-z") || cls.contains("A-Z") || cls.contains("\\w")) return "ALPHA_OR_WORD"
                if (cls.contains("0-9") || cls.contains("\\d")) return "DIGIT"
                return "CLASS:${cls[0]}"
            }
        }
        return "LIT:${b[0]}"
    }

    private val ESCAPED_ATOM_RE = Regex("\\\\.")
    private val CHAR_CLASS_ATOM_RE = Regex("\\[(?:\\\\.|[^\\]\\\\])*\\]")
    private val BRANCH_INNER_QUANTIFIER_RE = Regex("[+*?]")

    private fun hasOverlappingAlternation(groupBody: String): Boolean {
        val branches = splitTopLevelAlternation(groupBody)
        if (branches.size < 2) return false
        for (rawBranch in branches) {
            val strippedBranch = CHAR_CLASS_ATOM_RE.replace(ESCAPED_ATOM_RE.replace(rawBranch, "X"), "C")
            if (BRANCH_INNER_QUANTIFIER_RE.containsMatchIn(strippedBranch)) return true
        }
        val sigs = branches.map(::firstAtomSignature)
        for (i in sigs.indices) {
            for (j in i + 1 until sigs.size) {
                val a = sigs[i]
                val b = sigs[j]
                if (a == b || a == "*" || b == "*") return true
                if ((a == "ANY_WORD" || a == "ALPHA_OR_WORD") && (b == "ANY_WORD" || b == "ALPHA_OR_WORD" || b == "DIGIT")) {
                    return true
                }
            }
        }
        return false
    }

    internal fun isCatastrophicRegexPattern(pattern: String): Boolean = isPotentiallyCatastrophicRegex(pattern)

    /**
     * [P0 根治 2026-10-28] 检测「降级后的变长后顾」—— 这是真机上 60 秒卡死的直接元凶。
     *
     * 真机取证（tavern-runtime.log 18:25 / 18:27，两次都带完整线程栈）：
     *   DefaultDispatcher-worker-12 RUNNABLE
     *     MatcherNative.findImpl
     *     <- Regex.find
     *     <- RegexScriptEngine.runRegexScript
     *     <- RegexScriptEngine.getRegexedString
     *     <- PromptInjectionTransformerKt.collectInjections
     *     <- PromptInjectionTransformer.transform
     * 即：世界书注入收集阶段，某条脚本正则发生灾难性回溯，把 transforms 永久卡住，
     * 于是 STAGE_6 永远到不了、上游零请求、UI 无限转圈。
     *
     * 实测（ReDoSTest / ReDoSFixed，同一条降级后的正则）：
     *   输入 1000 字符 → 4010 ms
     *   输入 2000 字符 → 31974 ms     （4 倍输入 → 8 倍耗时，O(N²)）
     * 真机消息约 20000 字符 → 单条正则需数百秒。
     *
     * 为什么 [isPotentiallyCatastrophicRegex] 没拦住：
     *   它只分析**捕获组**的嵌套量词与重叠交替，**完全不看 lookbehind**。
     *   而 `(?<!<details>\s{0,N})` 的成本是「每个扫描位置 × N」，
     *   外面再套一个 `[\s\S]*?` 就是 O(文本长度 × N) —— 典型的隐性 ReDoS。
     *
     * 判据（三条同时满足才拒绝，避免误杀正常后顾）：
     *   1) 存在变长后顾 `(?<=` / `(?<!`；
     *   2) 该后顾**内部**含量词（`*` `+` `{m,n}`），即它需要回溯试探；
     *   3) 该后顾**外面**还有 `*` / `+` / `{m,}` 这类可跨全文的扫描量词。
     * 只满足 1+2 而外面没有全文扫描的（例如 `(?<=\s+)foo`）成本有界，不拒绝。
     */
    internal fun isDangerousVariableLookbehind(pattern: String): Boolean {
        val body = SLASH_FORM.matchEntire(pattern)?.groupValues?.getOrNull(1) ?: pattern
        // 1) 找所有变长后顾，记录区间
        val lookbehindRanges = mutableListOf<IntRange>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') { i += 2; continue }
            if (c == '[') { i = skipCharClass(body, i); continue }
            if (c == '(' && i + 3 < body.length && body[i + 1] == '?' && body[i + 2] == '<' &&
                (body[i + 3] == '=' || body[i + 3] == '!')
            ) {
                val end = findMatchingParen(body, i)
                if (end < 0) break
                lookbehindRanges.add(i..end)
                i = end + 1
                continue
            }
            i++
        }
        if (lookbehindRanges.isEmpty()) return false

        // 2) [v2] 后顾是否变长；变长后顾后面是否紧跟足够长的字面量（compileRegexUncached 会前置 (?=字面量)）
        var anyVariable = false
        var anyUnanchored = false
        for (r in lookbehindRanges) {
            val inner = body.substring(r.first + 4, r.last)
            if (!lookbehindHasQuantifier(inner)) continue
            anyVariable = true
            // [v251 根因修复] 有界性有**两条**独立通路，任一成立即安全：
            //   a) 后顾后面紧跟足够长的字面量 → 编译期可前置 (?=字面量)；
            //   b) 后顾**前面**的同一分支已有强制锚点 → 引擎必须先过锚点才走到后顾。
            // 旧判据只认 (a)，把 Meowssiah 的 speech_src/speech_zh 正则（靠 (b) 有界）误杀。
            val anchoredAfter = literalRunAfter(body, r.last + 1).length >= MIN_LOOKBEHIND_ANCHOR_LENGTH
            val anchoredBefore = isLookbehindCoveredByLeadingAnchor(body, r.first)
            if (!anchoredAfter && !anchoredBefore) anyUnanchored = true
        }
        if (!anyVariable) return false
        // 全部可前置：后顾只在锚点位置求值，成本有界；仅「无界任意字符扫描起首」仍是二次方形态。
        if (!anyUnanchored) return startsWithUnanchoredScan(body)

        // 3) 存在无法前置的变长后顾：保留旧判据——后顾外面是否还有可跨全文的扫描量词
        val outside = StringBuilder(body.length)
        var cursor = 0
        for (r in lookbehindRanges) {
            if (r.first > cursor) outside.append(body, cursor, r.first)
            cursor = r.last + 1
        }
        if (cursor < body.length) outside.append(body, cursor, body.length)
        val outStr = outside.toString()
        var j = 0
        while (j < outStr.length) {
            val ch = outStr[j]
            if (ch == '\\') { j += 2; continue }
            if (ch == '[') { j = skipCharClass(outStr, j); continue }
            if (ch == '*' || ch == '+') return true
            if (ch == '{') {
                val close = outStr.indexOf('}', j + 1)
                if (close > j + 1) {
                    val inner = outStr.substring(j + 1, close)
                    val parts = inner.split(',')
                    if (parts.size == 2 && parts[1].isEmpty()) return true // {m,} 无上界
                }
            }
            j++
        }
        return false
    }

    internal fun isPotentiallyCatastrophicRegex(pattern: String): Boolean {
        if (pattern.length < 4) return false
        val body = SLASH_FORM.matchEntire(pattern)?.groupValues?.getOrNull(1) ?: pattern
        val stack = ArrayDeque<Int>()
        var inCharClass = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (inCharClass) {
                if (c == ']') inCharClass = false
                i++
                continue
            }
            when (c) {
                '[' -> inCharClass = true
                '(' -> stack.addLast(i)
                ')' -> if (stack.isNotEmpty()) {
                    val openIdx = stack.removeLast()
                    var afterClose = i + 1
                    while (afterClose < body.length && body[afterClose] == ' ') afterClose++
                    if (hasRepeatableQuantifierAt(body, afterClose)) {
                        var inner = body.substring(openIdx + 1, i)
                        if (inner.startsWith("?:")) inner = inner.substring(2)
                        else if (inner.startsWith("?<") && inner.indexOf('>') > 0) inner = inner.substring(inner.indexOf('>') + 1)
                        // 重叠交替（(a|aa)+、(<a>|<ab>)* …）：一律保持拒绝。
                        if (hasOverlappingAlternation(inner)) return true
                        // 仅因「内部含量词」被标记：若该重复组在表达式尾部（之后没有可能失败的强制元素，
                        // 贪婪循环首条路径必成功、不会回溯展开），且每轮迭代都以强制字面量锚点起首
                        // （没有无锚点的歧义切分），则豁免。
                        if (hasInnerRepeatingToken(inner)) {
                            val exempt = isTailPosition(body, quantifierEnd(body, afterClose)) &&
                                isAnchoredIterationStart(inner)
                            if (!exempt) return true
                        }
                    }
                }
            }
            i++
        }
        return false
    }

    private val LEADING_INLINE_FLAGS_RE = Regex("^\\(\\?([imsug]+)\\)", RegexOption.IGNORE_CASE)

    /**
     * 提取模式头部的一个或多个 PCRE 内联标志组（如 `(?i)`, `(?is)`, `(?ims)`），返回 `(strippedBody, extractedFlags)`。
     */
    internal fun extractInlineRegexFlags(rawBody: String): Pair<String, String> {
        var body = rawBody
        val flags = StringBuilder()
        while (true) {
            val m = LEADING_INLINE_FLAGS_RE.find(body) ?: break
            body = body.substring(m.value.length)
            flags.append(m.groupValues[1].lowercase())
        }
        return body to flags.toString()
    }

    /**
     * 剥离消息首尾未配对的 `〔`/`〕`、`〖`/`〗` 模板包装符，保留句内成对使用的括号（如“她翻开〔世界史〕笔记本”）。
     */
    fun stripUnmatchedBoundaryWrappers(input: String): String {
        var out = input
        for ((openCh, closeCh) in listOf('〔' to '〕', '〖' to '〗')) {
            val openCount = out.count { it == openCh }
            val closeCount = out.count { it == closeCh }
            if (openCount == closeCount) continue
            val trimmed = out.trim()
            if (openCount == 1 && closeCount == 0 && trimmed.startsWith(openCh)) {
                val idx = out.indexOf(openCh)
                if (idx >= 0) out = out.removeRange(idx, idx + 1).trimStart('\r', '\n')
            } else if (closeCount == 1 && openCount == 0 && trimmed.endsWith(closeCh)) {
                val idx = out.lastIndexOf(closeCh)
                if (idx >= 0) out = out.removeRange(idx, idx + 1).trimEnd('\r', '\n')
            }
        }
        return out
    }

    /** 编译正则（支持 `/pattern/flags` 内联形式与前导 `(?ims)` 标志，缓存键包含 pattern + flags + scriptOptions） */
    fun compileRegex(pattern: String, flags: String = "", scriptOptions: String = ""): Regex? {
        val cacheKey = if (flags.isEmpty() && scriptOptions.isEmpty()) {
            pattern
        } else {
            "$pattern\u0000$flags\u0000$scriptOptions"
        }
        synchronized(cacheLock) {
            if (cache.containsKey(cacheKey)) return cache[cacheKey]
        }
        var compiled: Regex? = null
        var failure: Exception? = null
        try {
            compiled = compileRegexUncached(pattern, flags)
        } catch (e: Exception) {
            failure = e
            val degraded = try {
                degradeUnboundedLookbehind(pattern)
            } catch (_: Exception) {
                null
            }
            if (degraded != null && degraded != pattern) {
                try {
                    compiled = compileRegexUncached(degraded, flags)
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
        synchronized(cacheLock) { cache[cacheKey] = compiled }
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

    private fun parseRegexOptions(flagChars: String): Set<RegexOption> {
        var options = JS_REGEX_OPTIONS
        for (c in flagChars.lowercase()) {
            options = when (c) {
                'i' -> options + RegexOption.IGNORE_CASE
                'm' -> options + RegexOption.MULTILINE
                's' -> options + RegexOption.DOT_MATCHES_ALL
                else -> options
            }
        }
        return options
    }

    private fun compileRegexUncached(pattern: String, flags: String = ""): Regex {
        val slashForm = SLASH_FORM.matchEntire(pattern)
        if (slashForm == null) {
            val (cleanBody, inlineFlags) = extractInlineRegexFlags(pattern)
            return Regex(hoistLookbehindAnchors(cleanBody), parseRegexOptions(flags + inlineFlags))
        }
        val (cleanBody, inlineFlags) = extractInlineRegexFlags(slashForm.groupValues[1])
        val mergedFlags = slashForm.groupValues[2] + flags + inlineFlags
        return Regex(hoistLookbehindAnchors(cleanBody), parseRegexOptions(mergedFlags))
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
        val normalizedRaw = if (rawString.indexOf('\r') >= 0) {
            rawString.replace("\r\n", "\n").replace('\r', '\n')
        } else {
            rawString
        }

        val pattern = when (script.substituteRegex) {
            SubstituteRegex.RAW -> substitute(script.findRegex)
            SubstituteRegex.ESCAPED -> Regex("\\{\\{[^{}]*\\}\\}").replace(script.findRegex) { m ->
                sanitizeRegexMacro(substitute(m.value))
            }
            else -> script.findRegex
        }
        if (normalizedRaw.length >= MIN_CATASTROPHIC_INPUT_LENGTH && isPotentiallyCatastrophicRegexCached(pattern)) {
            val msg = "[regex] skipped potentially catastrophic regex on input (${normalizedRaw.length} chars): pattern=$pattern"
            warn(msg)
            onCompileDegraded?.invoke(msg)
            return normalizedRaw
        }
        // [P0 根治 2026-10-28] 变长后顾 + 全文扫描量词 = 隐性 ReDoS。
        // 真机实测 2000 字符就要 32 秒（O(N²)），20000 字符必然数百秒 —— 直接跳过并留痕。
        // 这条必须在真正执行正则**之前**判定：一旦进了 Matcher，主流程只能等它跑完。
        if (normalizedRaw.length >= MIN_CATASTROPHIC_INPUT_LENGTH && isDangerousVariableLookbehindCached(pattern)) {
            val msg = "[regex] skipped variable-lookbehind ReDoS risk (${normalizedRaw.length} chars): pattern=$pattern"
            warn(msg)
            onCompileDegraded?.invoke(msg)
            return normalizedRaw
        }
        val scriptOptionsKey = "${script.substituteRegex}:${script.markdownOnly}:${script.promptOnly}"
        val regex = compileRegex(pattern, flags = "", scriptOptions = scriptOptionsKey) ?: return rawString
        val global = isGlobalPattern(pattern)

        // 若该脚本并非专门处理 <think>/<thinking>/<thought> 标签，但输入文本中含有未剥离的思维链块，
        // 则先用占位符屏蔽思维链块，防止立绘/CG 等正文正则在思维链内部跨行误匹配。
        val scriptTargetsThinking = pattern.contains("think", ignoreCase = true) ||
            pattern.contains("thought", ignoreCase = true) ||
            (RegexPlacement.REASONING in script.placement && script.placement.size == 1)
        val shieldedBlocks = mutableListOf<String>()
        val workingInput = if (!scriptTargetsThinking &&
            (normalizedRaw.contains("<think", ignoreCase = true) || normalizedRaw.contains("<thought", ignoreCase = true))
        ) {
            THINK_BLOCK_SHIELD_REGEX.replace(normalizedRaw) { m ->
                val idx = shieldedBlocks.size
                shieldedBlocks.add(m.value)
                "\u0000__RIKKA_THINK_SHIELD_${idx}__\u0000"
            }
        } else {
            normalizedRaw
        }

        val sb = StringBuilder()
        var cursor = 0
        var matched = false
        var boundedInputRef: BoundedRegexCharSequence? = null
        try {
            val boundedInput = BoundedRegexCharSequence(workingInput)
            boundedInputRef = boundedInput
            for (match in regex.findAll(boundedInput)) {
                sb.append(workingInput, cursor, match.range.first)
                val mVal = match.value
                // 防御 1：若 match 跨过了思维链保护占位符（\u0000__RIKKA_THINK_SHIELD_N__\u0000），重锚定到最后一个占位符之后
                if (mVal.contains("\u0000__RIKKA_THINK_SHIELD_")) {
                    val lastShieldEnd = mVal.lastIndexOf("__\u0000")
                    if (lastShieldEnd >= 0) {
                        val afterShieldIdx = lastShieldEnd + 3
                        val tailAfterShield = mVal.substring(afterShieldIdx)
                        val subMatch = regex.find(BoundedRegexCharSequence(tailAfterShield))
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
                // 防御 2：当贪婪正则把同一消息内的多对反引号吞入同一个 match 时，逐个扫描内部独立的反引号对
                if (mVal.length > 3 && mVal.first() == '`' && mVal.last() == '`' && mVal.indexOf('`', 1) < mVal.lastIndex) {
                    val pairRegex = Regex("`[^`\\r\\n]+`")
                    val pairMatches = pairRegex.findAll(mVal).toList()
                    if (pairMatches.any { pm -> regex.matchEntire(BoundedRegexCharSequence(pm.value)) != null }) {
                        var localCur = 0
                        for (pm in pairMatches) {
                            sb.append(mVal, localCur, pm.range.first)
                            val exactPairMatch = regex.matchEntire(BoundedRegexCharSequence(pm.value))
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
        } catch (budgetErr: RegexExecutionBudgetExceededException) {
            val msg = "[regex] aborted excessive backtracking (${budgetErr.message}): pattern=$pattern"
            warn(msg)
            onCompileDegraded?.invoke(msg)
            return normalizedRaw
        }
        // [v2] 预算哨兵：真机上若 Matcher 把 CharSequence 先 toString() 交给 ICU 原生层，
        // BoundedRegexCharSequence.charAt 永远不会被回调（stepsUsed == 0）→ 运行时熔断形同不存在，
        // 静态判据 + 编译期锚点前置就是唯一防线。这条日志一次真机即可证实/证伪。
        if (workingInput.length >= MIN_CATASTROPHIC_INPUT_LENGTH && boundedInputRef?.stepsUsed == 0 && !budgetInactiveWarned) {
            budgetInactiveWarned = true
            val msg = "[regex] WARN runtime budget inactive: matcher read 0 chars via CharSequence on a " +
                "${workingInput.length}-char input; BoundedRegexCharSequence is NOT enforced on this runtime"
            warn(msg)
            onCompileDegraded?.invoke(msg)
        }
        if (!matched) return normalizedRaw
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
