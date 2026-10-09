package me.rerere.rikkahub.ui.components.frosted

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * [v222 R3 / v227 D3-b] 顶部聊天栏「半透明 scrim + 长渐变」遮罩（对标 Kelivo 图一）。
 *
 * ## 约束（主人拍板 A4/A5）
 * - **常驻显示**：不随滚动淡入淡出。
 * - **不跟随**「消息样式」三选一：顶栏固定按 Kelivo 观感做。
 * - 必须是**覆盖在消息列表之上的独立装饰层**：
 *   本组件只画，不改 LazyColumn 的 clip / padding，不碰 MessageHtmlBlock / PanelScrollBridge / message key。
 *
 * ## v227 D3-b：为什么删掉「不透明实色段」
 * v222~v226 的结构是 `0..topInset` **完全不透明实色段** + 40dp 渐变段。v226 真机验收结论：
 * 主人确认「顶部遮罩栏确实是可以了」（渐变终于画出来了），但「过渡得不是很自然」。逐行取色证实
 * v226 = 「y=0..190 全宽恒定 (241,242,244)、std=0.0 的纯不透明带」+「y≈200 硬边跳到壁纸本色」；
 * 而主人给的参照图（图1）是「顶栏区壁纸可见（std 46~56，纹理完整保留）、无任何恒定色带、向下连续过渡」。
 *
 * 所以 v227 改成**单一长渐变 scrim**：
 * - 渐变从 `y = 0` 起（**不再有实色段**）-> 顶栏区壁纸从顶上就透上来；
 * - alpha 按 [chatTopFadeRampStops] 的平滑 ramp 从 [CHAT_TOP_FADE_SCRIM_ALPHA] 连续衰减到 0；
 * - 下边界 = `topInset + fadeHeight`（v227: 40dp -> 88dp，过渡更长更自然）；
 * - 顶栏与内容区之间**没有硬边**（alpha 是 y 的连续函数）。
 *
 * ⚠️ 红线（v225 冻结 P1 = A）：**不得模糊整张聊天壁纸** —— 本组件只有 `drawRect`，零 blur / 零 RenderEffect。
 *
 * ⚠️ 红线（v226 血泪，别踩第二遍）：渐变 shader 的 `startY/endY` 必须是本 DrawScope 的**绝对坐标**
 * （`drawRect(brush, topLeft, size)` 不做 `canvas.translate`，见 javap 实证）。凡「画了看不到」，
 * 先怀疑坐标系，别先怀疑颜色。
 *
 * ⚠️ 本组件不拦截触摸（没有 clickable / pointerInput）。
 *
 * @param topInset 状态栏 + 顶栏高度（调用方给 Scaffold 的 top padding）。
 * @param fadeHeight 渐变段在顶栏下缘之外再延伸的高度。
 * @param scrimTopAlpha 顶部 scrim 不透明度。**必须 < 1**：=1 就退化成 v226 那条「不透明实色带」。
 * @param color 遮罩色。**v228 A 起改用 [MaterialTheme.colorScheme.onSurface] 系**：
 *   v227 取 surfaceContainerHigh（与顶栏/卡片同色系），在**纯色底**（主人反馈的会话没有壁纸）上
 *   半透明叠加 = 看不见（v227 红线「同色叠加 = 不可见」）。onSurface 是前景色，永远与底有色差。
 *
 * ## [v228 A] 参数调整（v227 验收「还是没有过渡」的收口）
 * 三条同时改：scrim 顶部 alpha `0.66 -> 0.86`、fadeHeight `88 -> 128dp`、颜色换 onSurface 系。
 * 为什么够：纯色底会话里唯一能被眼睛捕捉的是**明度差**，同色系只差 alpha 时人眼分辨不出。
 */

/** [v227 D3-b] 顶部 scrim 起始不透明度。**必须 < 1**（=1 即 v226 的不透明实色带，主人判「不自然」）。 */
internal const val CHAT_TOP_FADE_SCRIM_ALPHA: Float = 0.86f

/** [v227 D3-b] ramp 曲线指数：`alpha(t) = topAlpha * (1 - t) ^ EXP`。1 = 线性，>1 = 顶部衰减快、尾部收得慢。 */
internal const val CHAT_TOP_FADE_RAMP_EXP: Float = 1.35f

/** [v227 D3-b] ramp 采样档数（colorStops 粒度；8 档在真机上已看不出折线）。 */
internal const val CHAT_TOP_FADE_RAMP_STOPS: Int = 8

/**
 * [v227 D3-b] 遮罩几何（纯函数，可单测）。单位 = px。
 *
 * ## 不变量
 * 1. 渐变段的 [gradientStartY] / [gradientEndY] 必须是 **DrawScope 绝对坐标**
 *    （v226 真机缺陷的根因：写成 `0f..fadeHeightPx` 会让整块渐变矩形落在渐变定义域之外，
 *    `TileMode.Clamp` 取末色标 `alpha = 0` -> 渐变段全透明）。
 * 2. [gradientStartY] **恒为 0**：v227 起顶栏区不再有不透明实色段（D3-b）。
 */
internal data class ChatTopFadeGeometry(
    /** scrim 渐变上边界（绝对坐标）。v227 恒为 0f。 */
    val gradientStartY: Float,
    /** scrim 渐变下边界（绝对坐标 = topInset + fadeHeight）。 */
    val gradientEndY: Float,
    /** 渐变段在顶栏下缘之外延伸的高度。 */
    val fadeHeightPx: Float,
)

internal fun chatTopFadeGeometry(topInsetPx: Float, fadeHeightPx: Float): ChatTopFadeGeometry =
    ChatTopFadeGeometry(
        gradientStartY = 0f,
        gradientEndY = topInsetPx + fadeHeightPx,
        fadeHeightPx = fadeHeightPx,
    )

/**
 * [v227 D3-b] 平滑 scrim ramp（纯函数，可单测）。
 *
 * 返回 `(t, alpha)` 采样表，`t` 从 0 到 1 均匀分布，`alpha` 单调递减、首档 = [topAlpha]、末档恒为 0。
 * 用多项式 ramp 而不是 2 档线性：线性 ramp 在顶部与尾部的斜率突变肉眼可见（「看得见带边」），
 * 而 `(1 - t) ^ 1.35` 在两端都更平缓。
 */
internal fun chatTopFadeRampStops(
    topAlpha: Float = CHAT_TOP_FADE_SCRIM_ALPHA,
    exp: Float = CHAT_TOP_FADE_RAMP_EXP,
    stops: Int = CHAT_TOP_FADE_RAMP_STOPS,
): List<Pair<Float, Float>> {
    require(stops >= 2) { "stops must be >= 2, got $stops" }
    val a = topAlpha.coerceIn(0f, 1f)
    val last = stops - 1
    return (0 until stops).map { i ->
        val t = i.toFloat() / last.toFloat()
        val alpha = if (i == last) 0f else a * (1f - t).pow(exp)
        t to alpha
    }
}


/**
 * [v229 P1-4] 顶栏遮罩「两版参数」（主人拍板：先读 Kelivo -> 出两版 -> 给开关让主人自己挑）。
 *
 *  - [KELIVO_TOP_DOWN]：Kelivo 实测形态 —— **上浓下淡**（顶部最浓、向下连续淡出到 0）。
 *    参考实现（只读，未抄代码）：Kelivo `lib/features/home/widgets/chat_input_overlay_layout.dart:178-264`
 *    的 stops [0.0, 0.48, 0.78, 1.0] 与 alpha [1, 1, 0.9, 0]；遮罩色一律 `colorScheme.surface`。
 *    ⚠️ v228 之所以被评「上面偏黑」，是因为用了 `onSurface`（前景色）当遮罩 = 比背景更深。
 *  - [BOTTOM_ANCHORED]：主人给的形态描述 —— 顶栏上/中段=**背景可见**（alpha 恒 0），
 *    下段=**从下往上**的渐变遮盖（底部最浓，向上淡出到 0），顶栏区内不出现对话内容
 *    （内容由列表 contentPadding 让开，见 ChatPage；本组件只负责观感）。
 */
internal enum class ChatTopFadeStyle {
    /** Kelivo 实测：上浓下淡的单一长渐变。 */
    KELIVO_TOP_DOWN,

    /** 主人描述：上中段背景可见（alpha 0）+ 下段从下往上的渐变遮盖。 */
    BOTTOM_ANCHORED,
}

// ------------------------------------------------------------------ 可调常量（真机标定用，禁止写死比例）
// ⚠️ 主人 2026-10-04 明确纠正：「屏幕分 6 等份」**只是打个比方**，不是规格。
//    因此下面全部是**绝对 dp / 无量纲比例常量**，代码里**不得**出现 screenHeight / 6 之类的公式。

/** [KELIVO_TOP_DOWN] 渐变段总高（顶栏下缘之外再延伸的高度）。真机按截图微调这一个值即可。 */
internal val CHAT_TOP_FADE_DP_TOP_DOWN: Dp = 128.dp
/** [BOTTOM_ANCHORED] 渐变段总高（v229b 收紧：28dp，只做顶栏下缘的窄软过渡，不再垂进内容区）。 */
internal val CHAT_TOP_FADE_DP_BOTTOM_ANCHORED: Dp = 28.dp

/** [KELIVO_TOP_DOWN] 顶部浓度（< 1；=1 即 v226 的不透明实色带，主人判「不自然」）。 */
internal const val CHAT_TOP_FADE_ALPHA_TOP_DOWN: Float = 0.74f
/** [BOTTOM_ANCHORED] 下段峰值浓度（v229b 0.62 -> 0.42：surface 色叠加更轻，不再「白雾一条」）。 */
internal const val CHAT_TOP_FADE_ALPHA_BOTTOM_ANCHORED: Float = 0.42f
/** [BOTTOM_ANCHORED] 上/中段占整段的比例（v229b 0.62 -> 0.30：背景可见区更大，遮盖更收敛）。 */
internal const val CHAT_TOP_FADE_BG_VISIBLE_FRACTION: Float = 0.30f

/** [BOTTOM_ANCHORED] 峰值位置（u 坐标，0..1；v229b = 0.72，藏在渐变带内部）。 */
internal const val CHAT_TOP_FADE_PEAK_U: Float = 0.72f

/**
 * [v229 P1-4] 顶栏遮罩 alpha 采样（纯函数，可单测）。
 *
 * @return `(t, alpha)` 采样表，`t` 从 0（顶栏最上）到 1（渐变段最下）均匀分布。
 *   - [ChatTopFadeStyle.KELIVO_TOP_DOWN]：alpha 单调**递减**，首档 = [topAlpha]，末档恒 0；
 *   - [ChatTopFadeStyle.BOTTOM_ANCHORED]：前 [bgVisibleFraction] 区间恒 0（背景可见），
 *     之后单调**递增**到 [bottomAlpha]，末档 = [bottomAlpha]。
 */
internal fun chatTopFadeProfileStops(
    style: ChatTopFadeStyle,
    topAlpha: Float = CHAT_TOP_FADE_ALPHA_TOP_DOWN,
    bottomAlpha: Float = CHAT_TOP_FADE_ALPHA_BOTTOM_ANCHORED,
    bgVisibleFraction: Float = CHAT_TOP_FADE_BG_VISIBLE_FRACTION,
    exp: Float = CHAT_TOP_FADE_RAMP_EXP,
    stops: Int = CHAT_TOP_FADE_RAMP_STOPS,
): List<Pair<Float, Float>> {
    require(stops >= 2) { "stops must be >= 2, got $stops" }
    val last = stops - 1
    return when (style) {
        ChatTopFadeStyle.KELIVO_TOP_DOWN -> {
            val a = topAlpha.coerceIn(0f, 1f)
            (0 until stops).map { i ->
                val t = i.toFloat() / last.toFloat()
                val alpha = if (i == last) 0f else a * (1f - t).pow(exp)
                t to alpha
            }
        }

        ChatTopFadeStyle.BOTTOM_ANCHORED -> {
            // [v229b] 主人真机反馈：旧轮廓（0 -> 峰值 0.62 且停在末档）会在顶栏下缘留一条
            // 「白色半透明长带」且**硬切**。新轮廓 = 升起 -> 峰值(t≈0.72) -> 回落到 0：
            //   两端 alpha 恒 0（顶栏上缘和渐变带下缘都无硬边），峰值藏在渐变带内部。
            val a = bottomAlpha.coerceIn(0f, 1f)
            val f = bgVisibleFraction.coerceIn(0f, 0.95f)
            (0 until stops).map { i ->
                val t = i.toFloat() / last.toFloat()
                val alpha = when {
                    t <= f -> 0f
                    else -> {
                        val u = ((t - f) / (1f - f)).coerceIn(0f, 1f)
                        // 三角峰 * 平滑指数：峰在 u = PEAK，两端归零
                        val peak = CHAT_TOP_FADE_PEAK_U
                        val tri = if (u <= peak) u / peak else (1f - u) / (1f - peak)
                        a * tri.pow(exp)
                    }
                }
                t to alpha
            }
        }
    }
}
@Composable
internal fun ChatTopFadeOverlay(
    modifier: Modifier = Modifier,
    topInset: Dp = 0.dp,
    /** [v229 P1-4] 顶栏形态（两版参数，主人自己挑；默认 = 主人描述的那版）。 */
    style: ChatTopFadeStyle = ChatTopFadeStyle.BOTTOM_ANCHORED,
    /** 渐变段高度；null = 按 [style] 取对应可调常量（真机标定只改常量即可）。 */
    fadeHeight: Dp? = null,
    scrimTopAlpha: Float = CHAT_TOP_FADE_ALPHA_TOP_DOWN,
    /** [v229 P1-4] 下段最大遮盖强度（仅 [ChatTopFadeStyle.BOTTOM_ANCHORED]）。 */
    bottomAlpha: Float = CHAT_TOP_FADE_ALPHA_BOTTOM_ANCHORED,
    /**
     * 遮罩色。**两版都用 `colorScheme.surface`**：
     *  - Kelivo 实测（`chat_input_overlay_layout.dart:178-264`）遮罩色就是 surface；
     *  - v228 用 `onSurface`（前景色）= 比背景更深 -> 真机评价「上面偏黑」的根因；
     *  - 同色系 surface 半透明叠在**纯色底**上不会有硬边（v227 的「同色叠加=不可见」风险由
     *    下段较大 alpha + 背景可见区间 alpha 恒 0 的结构规避：看得见的是**层次**不是色带）。
     */
    color: Color = MaterialTheme.colorScheme.surface,
) {
    val resolvedFadeHeight = fadeHeight ?: when (style) {
        ChatTopFadeStyle.KELIVO_TOP_DOWN -> CHAT_TOP_FADE_DP_TOP_DOWN
        ChatTopFadeStyle.BOTTOM_ANCHORED -> CHAT_TOP_FADE_DP_BOTTOM_ANCHORED
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topInset + resolvedFadeHeight)
            .drawBehind {
                // [v226 D3] 几何一律走纯函数 chatTopFadeGeometry（可单测），不要在绘制块里手写坐标。
                val g = chatTopFadeGeometry(topInset.toPx(), resolvedFadeHeight.toPx())
                if (g.gradientEndY <= g.gradientStartY) return@drawBehind
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = chatTopFadeProfileStops(
                            style = style,
                            topAlpha = scrimTopAlpha,
                            bottomAlpha = bottomAlpha,
                        )
                            .map { (t, alpha) -> t to color.copy(alpha = alpha) }
                            .toTypedArray(),
                        // [v226 D3] startY/endY 必须是 DrawScope 的**绝对坐标**（见类注释 / 单测）。
                        startY = g.gradientStartY,
                        endY = g.gradientEndY,
                    ),
                    topLeft = Offset(0f, g.gradientStartY),
                    size = Size(size.width, g.gradientEndY - g.gradientStartY),
                )
            }
    )
}
