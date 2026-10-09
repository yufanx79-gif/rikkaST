package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import hct.Hct

/*
 * Surface Ladder（v217 / A2）
 *
 * 解决的问题：我们原来的自定义主题只有「种子色 -> 一堆角色」，容器档位
 * （surfaceContainerLowest..Highest）**根本没被推导** —— me.rerere.material3 的
 * DynamicSchemeExt.toColorScheme() 只写了 primary/secondary/.../outlineVariant，
 * surfaceContainer* 全部落到 material3 ColorScheme 的默认值（固定灰紫），
 * 于是「换任何种子色，卡片/顶栏都还是 M3 默认灰」。
 *
 * 这里把 Kelivo（Flutter / AGPL-3.0，只参考机制、未复制代码）的 Surface Ladder 机制
 * 用 Compose 原生重写：全部层级只在 HCT 的 tone 轴上派生。
 *
 *   页面 = 卡片下沉 4 个 tone（亮色；纯白跳过；暗色不沉）
 *   card / fill / hairline / surfaceContainer 五档 全部从「页面色」推导
 *
 * 为什么用 HCT：tone 等价于 CIE L*（0 = 黑，100 = 白），在 tone 轴上平移等于
 * 「只改明度、保住色相与彩度」，所以换任何主题都不会出现某档对比度崩掉。
 *
 * 作用范围（v217）：只对「自定义主题」与「动态色」两条路径启用；7 套预设保持现状，
 * 因此回滚方式 = 切回任一预设。
 */

/** 页面相对卡片的下沉量（亮色）。 */
private const val PAGE_SINK_TONES = 4.0

/** tone 高于此值就当作「纯白背景」：跳过下沉（否则卡片会比页面还暗）。 */
private const val PURE_BACKGROUND_TONE = 99.5

/** 亮色下「向上还有没有足够空间放卡片」的阈值。 */
private const val LIGHT_CARD_HEADROOM = 4.5

// ---------------------------------------------------------------------------
// 四个 HCT tone 原语
// ---------------------------------------------------------------------------

/** 保持 base 的 hue/chroma，把 tone 平移 [delta]，结果 clamp 到 [0, 100]。 */
fun shiftTone(base: Color, delta: Double): Color {
    val hct = Hct.fromInt(base.toArgb())
    val tone = (hct.tone + delta).coerceIn(0.0, 100.0)
    return Color(Hct.from(hct.hue, hct.chroma, tone).toInt())
}

/** 读出颜色的 HCT tone（0..100，等价于 CIE L*）。 */
fun surfaceTone(color: Color): Double = Hct.fromInt(color.toArgb()).tone

/** 保持 base 的 hue/chroma，强制到指定 [tone]（clamp 到 [0, 100]）。 */
fun atTone(base: Color, tone: Double): Color {
    val hct = Hct.fromInt(base.toArgb())
    return Color(Hct.from(hct.hue, hct.chroma, tone.coerceIn(0.0, 100.0)).toInt())
}

/**
 * **只在 tone 轴上**从 [from] 向 [to] 线性插值，hue/chroma 始终取 [from]。
 *
 * 注意这不是颜色插值（不是 `androidx.compose.ui.graphics.lerp`）：结果是同色相的灰阶过渡，
 * 不会把 [to] 的彩度带进来。上游就是这个语义，别「顺手优化」成颜色插值。
 */
fun lerpTone(from: Color, to: Color, t: Double): Color =
    atTone(from, surfaceTone(from) + (surfaceTone(to) - surfaceTone(from)) * t)

// ---------------------------------------------------------------------------
// 页面下沉
// ---------------------------------------------------------------------------

/**
 * 把「调色板声明的 surface（= 卡片色）」换算成「页面色」。
 *
 * - 亮色 + layered + 非纯白：`shiftTone(surface, -4)`
 * - 暗色：原样返回（暗色不沉）
 * - 纯白背景（tone >= 99.5）：原样返回，让 ladder 内部的 headroom 兜底去算卡片
 * - 非 layered：原样返回
 */
fun applyPageSurface(
    surface: Color,
    isDark: Boolean,
    layered: Boolean = true,
    pureBackground: Boolean = surfaceTone(surface) >= PURE_BACKGROUND_TONE,
): Color = when {
    !layered -> surface
    isDark -> surface
    pureBackground -> surface
    else -> shiftTone(surface, -PAGE_SINK_TONES)
}

// ---------------------------------------------------------------------------
// Ladder
// ---------------------------------------------------------------------------

/** Surface Ladder 的全部派生结果。纯数据，可在 `remember {}` 里安全缓存。 */
@Immutable
data class SurfaceLadder(
    val page: Color,
    val card: Color,
    val surfaceFill: Color,
    val surfaceCardFill: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
)

/**
 * 从页面色派生整条阶梯。
 *
 * @param surface **已经下沉过的页面色**（即 [applyPageSurface] 的返回值）。
 *   不要在这里再沉一次，否则暗色/AMOLED 会双重偏移。
 * @param onSurface 用于派生 hairline（亮色 0.08 / 暗色 0.12；strong 为 0.20 / 0.26）。
 * @param outlineVariant 只在 `layered = false` 的 legacy 分支用到。
 */
fun surfaceLadderFromScheme(
    surface: Color,
    onSurface: Color,
    outlineVariant: Color,
    isDark: Boolean,
    layered: Boolean = true,
): SurfaceLadder {
    val page = surface

    if (!layered) {
        // legacy：非 layered 走「叠加半透明」而不是 tone 轴。保留以便对照，新页面不要用。
        val card = (if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.96f))
            .compositeOver(page)
        val fill = onSurface.copy(alpha = if (isDark) 0.16f else 0.05f).compositeOver(page)
        return SurfaceLadder(
            page = page,
            card = card,
            surfaceFill = fill,
            surfaceCardFill = fill,
            hairline = outlineVariant.copy(alpha = if (isDark) 0.08f else 0.06f).compositeOver(page),
            hairlineStrong = outlineVariant.copy(alpha = if (isDark) 0.26f else 0.38f).compositeOver(page),
            surfaceContainerLowest = (if (isDark) Color.Black.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.72f))
                .compositeOver(page),
            surfaceContainerLow = Color.White.copy(alpha = if (isDark) 0.03f else 0.55f).compositeOver(page),
            surfaceContainer = Color.White.copy(alpha = if (isDark) 0.045f else 0.35f).compositeOver(page),
            surfaceContainerHigh = Color.White.copy(alpha = if (isDark) 0.06f else 0.85f).compositeOver(page),
            surfaceContainerHighest = (if (isDark) Color.White.copy(alpha = 0.09f) else onSurface.copy(alpha = 0.05f))
                .compositeOver(page),
        )
    }

    if (isDark) {
        // 暗色是 5 连 shiftTone（-3 / +4 / +7 / +11 / +14），不使用 atTone / lerpTone。
        val card = shiftTone(page, 11.0)
        return SurfaceLadder(
            page = page,
            card = card,
            surfaceFill = shiftTone(page, 9.0),
            surfaceCardFill = shiftTone(card, 9.0),
            hairline = onSurface.copy(alpha = 0.12f).compositeOver(page),
            hairlineStrong = onSurface.copy(alpha = 0.26f).compositeOver(page),
            surfaceContainerLowest = shiftTone(page, -3.0),
            surfaceContainerLow = shiftTone(page, 4.0),
            surfaceContainer = shiftTone(page, 7.0),
            surfaceContainerHigh = shiftTone(page, 11.0),
            surfaceContainerHighest = shiftTone(page, 14.0),
        )
    }

    // 亮色：卡片向上 4 个 tone；若头顶空间不足（纯白/极浅页面）则向下取 3.5，
    // 得到一个浅灰卡片（上游的真实观感，不是 bug）。
    val headroom = 100.0 - surfaceTone(page)
    val card = if (headroom >= LIGHT_CARD_HEADROOM) shiftTone(page, 4.0) else shiftTone(page, -3.5)
    val fill = shiftTone(page, -2.5)
    return SurfaceLadder(
        page = page,
        card = card,
        surfaceFill = fill,
        surfaceCardFill = shiftTone(card, -3.2),
        hairline = onSurface.copy(alpha = 0.08f).compositeOver(page),
        hairlineStrong = onSurface.copy(alpha = 0.20f).compositeOver(page),
        surfaceContainerLowest = atTone(page, 100.0),
        surfaceContainerLow = lerpTone(page, card, 0.50),
        surfaceContainer = lerpTone(page, card, 0.75),
        surfaceContainerHigh = card,
        surfaceContainerHighest = fill,
    )
}

// ---------------------------------------------------------------------------
// 语义层（给 A4 的设置卡 / 分段控件用）
// ---------------------------------------------------------------------------

/**
 * 没有 M3 ColorScheme 对应角色的几个 surface 语义色。
 * `surfaceCard` 等价于 `colorScheme.surfaceContainerHigh`，单独给出来是为了让
 * 卡片组件不必知道「第几档 = 卡片」这件事。
 */
@Immutable
data class AppSurfaceColors(
    val surfaceCard: Color,
    val surfaceCardFill: Color,
    val surfaceFill: Color,
    val hairline: Color,
    val hairlineStrong: Color,
) {
    companion object {
        /**
         * 未启用 Surface Ladder（7 套预设）时的回落值：
         * 直接用 M3 自己的容器档位，保证预设主题的观感完全不变。
         */
        @Composable
        @ReadOnlyComposable
        fun fromScheme(scheme: ColorScheme): AppSurfaceColors = AppSurfaceColors(
            surfaceCard = scheme.surfaceContainerHigh,
            surfaceCardFill = scheme.surfaceContainerHighest,
            surfaceFill = scheme.surfaceContainerLow,
            hairline = scheme.outlineVariant.copy(alpha = 0.5f),
            hairlineStrong = scheme.outlineVariant,
        )
    }
}

val LocalAppSurfaceColors = staticCompositionLocalOf<AppSurfaceColors?> { null }

/** 取当前主题的 surface 语义色；没被 [RikkahubTheme] 包住时回落 M3 容器档位。 */
val MaterialTheme.appSurfaceColors: AppSurfaceColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppSurfaceColors.current ?: AppSurfaceColors.fromScheme(MaterialTheme.colorScheme)
