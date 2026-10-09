package me.rerere.rikkahub.data.datastore

import kotlinx.serialization.Serializable

/**
 * [v222 R5] 消息样式（全局唯一，不做 per-assistant 覆盖 —— 主人 2026-10-04 明确要求）。
 *
 * 设计来源：Kelivo `theme/chat_bubble_style.dart` 的 `ChatBubbleStyleOverrides` +
 * `ChatMessageBackgroundStyle`（只吸收设计思路与颗粒度，不复制 Dart 代码）。
 *
 * 颗粒度对齐 Kelivo：颜色 / 不透明度 / 宽度 / 圆角各自独立可调，
 * **不**合成一个「气泡透明度」大开关。
 */
@Serializable
enum class MessageBubbleStyle {
    /** 默认：跟随主题，不可调节（副标题「跟随主题，不可调节」）。 */
    DEFAULT,

    /** 毛玻璃：半透明毛玻璃（副标题「半透明毛玻璃」）。 */
    FROSTED,

    /** 纯色：不透明纯色底（副标题「不透明纯色底」）。 */
    SOLID,
}

/**
 * 一套（浅色或深色）气泡外观参数。`null` 颜色 = 跟随主题（Kelivo 同语义）。
 *
 * @param backgroundArgb 背景色（0xRRGGBB 或 0xAARRGGBB 均可，alpha 由不透明度字段决定）
 * @param frostedOpacity 毛玻璃样式的背景不透明度（Kelivo 默认 0.66）
 * @param solidOpacity 纯色样式的背景不透明度（Kelivo 默认 1.0）
 * @param borderArgb 边框色；null = 跟随主题 outlineVariant
 * @param borderOpacity 边框不透明度（Kelivo：毛玻璃 0.14 / 纯色 0.16）
 * @param borderWidth 边框宽度 dp（Kelivo 默认 0.8 —— 即「可见细边框 hairline」）
 * @param textArgb 文字色；null = 跟随主题 onSurface
 * @param cornerRadius 圆角半径 dp（Kelivo 默认 16）
 */
@Serializable
data class BubbleStyleTheme(
    val backgroundArgb: Int? = null,
    val frostedOpacity: Float = 0.66f,
    val solidOpacity: Float = 1f,
    val borderArgb: Int? = null,
    val borderOpacity: Float = 0.14f,
    val borderWidth: Float = 0.8f,
    val textArgb: Int? = null,
    val cornerRadius: Float = 16f,
)

/**
 * [v222 R5] 「设置 → 偏好设置 → 消息样式」的完整数据。
 *
 * ⚠️ 与 R1-(2) 的「模糊强度」模块**是同一份数据**（`blurStrength`，两处入口同一个值）。
 */
/**
 * [v222 R1-(1)] 「模糊风格」子选项（A1：挂在「启用模糊效果」开关下面）。
 *
 * - [TRANSPARENT] = RikkaHub 现状（默认值）：低不透明度 + 输入栏 Haze 实时模糊、气泡无细边框。
 * - [FROSTED_GLASS] = Kelivo 风（新增）：0.66 不透明度 + hairline 细边框 + 壁纸侧一次性模糊 + 大圆角。
 */
@Serializable
enum class BlurStyle {
    TRANSPARENT,
    FROSTED_GLASS,
}

@Serializable
data class MessageStyleSetting(
    val style: MessageBubbleStyle = MessageBubbleStyle.DEFAULT,
    /** R6：助手气泡按文字宽度收缩，不再占满整行。 */
    val assistantBubbleWrapContent: Boolean = false,
    /** R6：助手回复遇到空行时拆分，每段单独一个气泡。 */
    val splitSegmentsAsBubbles: Boolean = false,
    /** R1-(2)：模糊强度档位下标（见 [BlurStrength]）。 */
    val blurStrength: Int = BlurStrength.DEFAULT_INDEX,
    /** R1-(1)：模糊风格子选项（透明模糊 / 磨砂玻璃）。 */
    val blurStyle: BlurStyle = BlurStyle.TRANSPARENT,
    /**
     * [v233] 顶栏遮罩**总开关**（默认**关**，主人 v232 验收：「顶栏模糊直接给我删掉吧，太难看」）。
     * 关 = 不渲染任何顶栏遮罩；开 = 按 [topFadeBottomAnchored] 选样式渲染。
     */
    val topFadeEnabled: Boolean = false,
    /**
     * [v229 P1-4] 顶栏遮罩形态（主人拍板：「做两版，然后选一个开关，让我自己选」）。
     *  - true（默认）= 主人描述的形态：上/中段背景可见（alpha 恒 0）+ 下段从下往上的渐变遮盖；
     *  - false = Kelivo 实测形态：上浓下淡的单一长渐变（`surface` 色，stops [0,0.48,0.78,1]）。
     * 高度/比例一律走可调常量，**不得**出现 screenHeight/6 之类写死公式。
     */
    val topFadeBottomAnchored: Boolean = true,
    val light: BubbleStyleTheme = BubbleStyleTheme(),
    val dark: BubbleStyleTheme = BubbleStyleTheme(),
) {
    fun themeFor(isDark: Boolean): BubbleStyleTheme = if (isDark) dark else light

    /** 当前样式对应的背景不透明度（默认样式不参与调节 → 返回纯色值）。 */
    fun backgroundOpacityFor(style: MessageBubbleStyle, dark: Boolean): Float {
        val t = themeFor(dark)
        return when (style) {
            MessageBubbleStyle.FROSTED -> t.frostedOpacity
            else -> t.solidOpacity
        }
    }
}

/**
 * [v222 R1-(2)] 模糊强度档位：5 档（关 / 弱 / 中 / 强 / 极强）。
 *
 * 数值是「壁纸侧一次性模糊」的 sigma（dp），也是输入栏 Haze 的 blurRadius。
 * 0 = 关闭（不产生任何 blur 开销）。
 */
object BlurStrength {
    val SIGMAS_DP: List<Float> = listOf(0f, 8f, 14f, 22f, 32f)
    const val DEFAULT_INDEX: Int = 2

    fun sigmaDp(index: Int): Float = SIGMAS_DP[index.coerceIn(0, SIGMAS_DP.lastIndex)]
    fun clampIndex(index: Int): Int = index.coerceIn(0, SIGMAS_DP.lastIndex)
    fun labelIndex(index: Int): String = when (clampIndex(index)) {
        0 -> "关"
        1 -> "弱"
        2 -> "中"
        3 -> "强"
        else -> "极强"
    }
}

/** 解算后的气泡外观（纯数据，便于单测；Compose 侧再转 `Color`）。 */
data class ResolvedBubbleStyle(
    /** 已经乘上不透明度的最终 ARGB。 */
    val backgroundArgb: Int,
    val borderArgb: Int,
    /** null = 跟随主题 onSurface。 */
    val textArgb: Int?,
    val borderWidthDp: Float,
    val cornerRadiusDp: Float,
)

/**
 * [v222 R5] 把「主题 + 样式 + 亮暗」解算成最终绘制值（**纯函数，可单测**）。
 *
 * 与 Kelivo `resolveBubbleStyle` 语义一致：
 * - 背景：override 色（无则 fallback）乘 `frostedOpacity` / `solidOpacity`
 * - 边框：override 色（无则 fallback）乘 `borderOpacity`
 * - 文字：override 色，无则 null（调用方用 colorScheme.onSurface）
 */
fun resolveBubbleStyle(
    setting: MessageStyleSetting,
    style: MessageBubbleStyle,
    dark: Boolean,
    fallbackBackgroundArgb: Int,
    fallbackBorderArgb: Int,
): ResolvedBubbleStyle {
    val t = setting.themeFor(dark)
    val opacity = when (style) {
        MessageBubbleStyle.FROSTED -> t.frostedOpacity
        else -> t.solidOpacity
    }
    val borderOpacity = t.borderOpacity
    return ResolvedBubbleStyle(
        backgroundArgb = withAlpha(t.backgroundArgb ?: fallbackBackgroundArgb, opacity),
        borderArgb = withAlpha(t.borderArgb ?: fallbackBorderArgb, borderOpacity),
        textArgb = t.textArgb,
        borderWidthDp = t.borderWidth,
        cornerRadiusDp = t.cornerRadius,
    )
}

/** 用 [alpha]（0..1）替换 ARGB 的 alpha 通道。 */
fun withAlpha(argb: Int, alpha: Float): Int {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (argb and 0x00FFFFFF) or (a shl 24)
}

/**
 * [v222 R6 / v225 P4] 助手回复按**空行**拆分为多个气泡段。
 *
 * 规则（纯函数，可单测）：
 * - 连续空行（含只含空格 / 制表符的行，兼容 CRLF）视为一个分段边界；
 * - 段内保留原有单换行与受保护块内的空行；
 * - 首尾空白段被丢弃；无空行时返回单元素列表（原样）。
 *
 * 结构保护（v225 P4：对齐 Kelivo assistant_paragraph_splitter 的单保护状态机，本轮补第 2、4 条）：
 * - 代码围栏（三反引号 / 波浪号为标记；收栏须同字符且不短于开栏、标记后仅空白；含未闭合流式态）；
 * - 行内独占的数学围栏（两个美元号为标记；含未闭合流式态）；
 * - details 折叠块（含未闭合、嵌套计数容错）；
 * - 缩进续行：空行之后第一个非空行以 4 空格 / Tab 开头时，该空行不是边界
 *   （缩进代码块内部空行、缩进块与上文之间的空行都不拆）。
 *
 * 面板消息（带 HTML 的消息）不参与拆分 —— 由调用方在数据到 UI 的边界判断。
 * 本函数只服务显示分段（唯一调用点 ChatMessage.kt），因此段内容统一按 LF 输出（CRLF 归一）。
 */
fun splitAssistantSegments(text: String): List<String> {
    if (text.isEmpty()) return listOf(text)

    // CRLF 容错：按换行切物理行后去掉行尾 CR；段内容统一输出 LF（显示层无差异）。
    val lines = text.split("\n").map { it.trimEnd(Char(13)) }
    val segments = mutableListOf<String>()
    val current = StringBuilder()

    // —— 保护状态（单状态机；开/闭都在行尾推进，保证开栏/开块行本身进入保护）——
    var fenceChar: Char? = null   // 反引号 / 波浪号
    var fenceLength = 0           // 开栏 run 长度：收栏必须同字符且不短于它
    var mathBlock = false         // 独占一行的显示数学块
    var detailsDepth = 0          // details 嵌套深度；未闭合时容错保持大于 0

    val detailsTag = Regex("</details>|<details(?:\\s[^<>]*)?>", RegexOption.IGNORE_CASE)

    // 空行之后第一个非空行是否 4 空格 / Tab 缩进（缩进续行保护）。
    // 自底向上一遍预处理，避免每个空行都向后扫描造成 O(n^2)。
    val nextNonBlankIndented = BooleanArray(lines.size + 1)
    for (j in lines.indices.reversed()) {
        nextNonBlankIndented[j] = if (lines[j].isBlank()) {
            nextNonBlankIndented[j + 1]
        } else {
            lines[j].startsWith("    ") || lines[j].startsWith("\t")
        }
    }

    fun flush() {
        if (current.isNotEmpty()) {
            segments += current.toString().trimEnd(Char(10))
            current.setLength(0)
        }
    }

    // 行首围栏标记：至少 3 个同字符；反引号围栏的 info string 不得再含反引号。
    fun fenceOpening(line: String): Pair<Char, Int>? {
        val marker = when {
            line.startsWith(FENCE_BACKTICK) -> FENCE_BACKTICK[0]
            line.startsWith(FENCE_TILDE) -> FENCE_TILDE[0]
            else -> return null
        }
        var run = 1
        while (run < line.length && line[run] == marker) run++
        if (run < 3) return null
        if (marker == FENCE_BACKTICK[0] && line.indexOf(FENCE_BACKTICK, run) >= 0) return null
        return marker to run
    }

    // 收栏：同字符、run 不短于开栏、标记之后只允许空白。
    fun closesFence(line: String, marker: Char, minRun: Int): Boolean {
        var run = 0
        while (run < line.length && line[run] == marker) run++
        if (run < minRun) return false
        for (i in run until line.length) {
            if (line[i] != Char(32) && line[i] != Char(9)) return false
        }
        return true
    }

    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trimStart()
        val insideProtected = fenceChar != null || mathBlock || detailsDepth > 0

        // 1) 分段边界 = 空行 且 不在任何保护区内 且 下一非空行不是缩进续行。
        if (line.isBlank() && !insideProtected && !nextNonBlankIndented[i + 1]) {
            flush()
            continue
        }
        // 段首空行直接丢弃（缩进续行保护不应把段首空行带进气泡）。
        if (line.isBlank() && current.isEmpty()) continue

        // 2) 内容行（含保护区内空行）进入当前段。
        current.append(line).append("\n")

        // 3) 行尾推进保护状态。
        val openMarker = fenceChar
        if (openMarker != null) {
            if (closesFence(trimmed, openMarker, fenceLength)) {
                fenceChar = null
                fenceLength = 0
            }
            continue // 围栏内不识别 details / math
        }

        if (trimmed == MATH_FENCE) {
            mathBlock = !mathBlock
            continue
        }
        if (mathBlock) continue // 显示数学块优先：内部不识别围栏 / details

        val opening = fenceOpening(trimmed)
        if (opening != null) {
            fenceChar = opening.first
            fenceLength = opening.second
            continue
        }

        // details：depth 计数。depth 为 0 时仅行首 tag 开块（避免正文里的字面量扩保护），
        // 块内允许嵌套；close 可在行内任意位置；多余的 close 忽略（容错）。
        for (match in detailsTag.findAll(trimmed)) {
            if (match.value.startsWith("</")) {
                if (detailsDepth > 0) detailsDepth--
            } else if (detailsDepth > 0 || match.range.first == 0) {
                detailsDepth++
            }
        }
    }

    flush()

    // [v226 C3] 切分后的 merge pass（对齐 Kelivo `_mergeRelatedChunks` / `_shouldMerge` 的两条规则）：
    //  ① 相邻两段**都以列表项开头** → 合并：否则「1. a」/「2. b」被空行拆成两个气泡后，
    //     每个气泡里的有序列表都会从 1 重新编号；
    //  ② 某段**只有一行且是 ATX 标题** → 与下一段合并：避免出现「光秃秃一个标题气泡」。
    // 合并用 "\n\n" 回填（= 原来的空行），合并后的文本与原文逐字一致，渲染语义不变。
    // 这是「切完之后」的归并，不改变「空行才是边界」的主语义；无匹配时行为与 v225 完全一致。
    val merged = mutableListOf<String>()
    for (segment in segments) {
        val prev = merged.lastOrNull()
        if (prev != null && shouldMergeSegments(prev, segment)) {
            merged[merged.lastIndex] = prev + "\n\n" + segment
        } else {
            merged += segment
        }
    }

    return merged.ifEmpty { listOf(text) }
}

/** [v226 C3] 段首是否是列表项：`-` / `*` / `+` 或 `1.` / `1)`；标记后必须跟空白（允许前导空白）。 */
private fun startsWithListItem(segment: String): Boolean {
    val line = segment.substringBefore(Char(10)).trimStart()
    if (line.isEmpty()) return false
    val first = line[0]
    if (first == '-' || first == '*' || first == '+') {
        return line.length > 1 && (line[1] == ' ' || line[1] == Char(9))
    }
    if (!first.isDigit()) return false
    var i = 0
    while (i < line.length && line[i].isDigit()) i++
    if (i > 9 || i >= line.length) return false // CommonMark：有序标记最多 9 位
    if (line[i] != '.' && line[i] != ')') return false
    return i + 1 < line.length && (line[i + 1] == ' ' || line[i + 1] == Char(9))
}

/** [v226 C3] 段是否「只有一行且是 ATX 标题」：1~6 个 `#` 后必须跟空白（`#hashtag` 不算）。 */
private fun isSingleLineHeading(segment: String): Boolean {
    if (segment.indexOf(Char(10)) >= 0) return false
    val line = segment.trimStart()
    var i = 0
    while (i < line.length && line[i] == '#') i++
    if (i < 1 || i > 6) return false
    return i < line.length && (line[i] == ' ' || line[i] == Char(9))
}

/** [v226 C3] 相邻两段是否该并回同一个气泡（Kelivo `_shouldMerge` 的两条规则）。 */
private fun shouldMergeSegments(prev: String, next: String): Boolean =
    isSingleLineHeading(prev) || (startsWithListItem(prev) && startsWithListItem(next))

private const val FENCE_BACKTICK = "```"
private const val FENCE_TILDE = "~~~"
private const val MATH_FENCE = "$$"
